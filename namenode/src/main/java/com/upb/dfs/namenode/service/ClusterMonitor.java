package com.upb.dfs.namenode.service;

import com.upb.dfs.common.dto.ClusterDtos;
import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.common.util.JwtUtil;
import com.upb.dfs.namenode.config.DfsProperties;
import com.upb.dfs.namenode.entity.BlockReplicaEntity;
import com.upb.dfs.namenode.entity.DataNodeEntity;
import com.upb.dfs.namenode.repo.BlockReplicaRepo;
import com.upb.dfs.namenode.repo.DataNodeRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ClusterMonitor {

    private static final Logger log = LoggerFactory.getLogger(ClusterMonitor.class);

    private final DataNodeRepo dataNodeRepo;
    private final BlockReplicaRepo replicaRepo;
    private final DataNodeService dataNodeService;
    private final DfsProperties props;
    private final JwtUtil jwtUtil;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public ClusterMonitor(DataNodeRepo dataNodeRepo, BlockReplicaRepo replicaRepo,
                          DataNodeService dataNodeService, DfsProperties props, JwtUtil jwtUtil) {
        this.dataNodeRepo = dataNodeRepo;
        this.replicaRepo = replicaRepo;
        this.dataNodeService = dataNodeService;
        this.props = props;
        this.jwtUtil = jwtUtil;
    }

    @Scheduled(fixedDelayString = "${dfs.heartbeat-suspect-seconds:10}000")
    @Transactional
    public void detectFailures() {
        Instant suspectCutoff = Instant.now().minusSeconds(props.getHeartbeatSuspectSeconds());
        Instant deadCutoff = Instant.now().minusSeconds(props.getHeartbeatDeadSeconds());
        for (DataNodeEntity dn : dataNodeRepo.findAll()) {
            if (dn.getLastHeartbeat().isBefore(deadCutoff) && !"DEAD".equals(dn.getStatus())) {
                log.warn("DataNode {} marked DEAD (last heartbeat {})", dn.getId(), dn.getLastHeartbeat());
                dn.setStatus("DEAD");
                dataNodeRepo.save(dn);
                // Mark its replicas as DELETED to drop them from LIVE counts
                for (BlockReplicaEntity r : replicaRepo.findByDatanodeId(dn.getId())) {
                    if ("LIVE".equals(r.getStatus()) || "PENDING".equals(r.getStatus())) {
                        r.setStatus("DELETED");
                        r.setUpdatedAt(Instant.now());
                        replicaRepo.save(r);
                    }
                }
            } else if (dn.getLastHeartbeat().isBefore(suspectCutoff) && "LIVE".equals(dn.getStatus())) {
                log.warn("DataNode {} marked SUSPECT", dn.getId());
                dn.setStatus("SUSPECT");
                dataNodeRepo.save(dn);
            }
        }
    }

    @Scheduled(fixedDelayString = "${dfs.rereplication-interval-seconds:15}000")
    @Transactional
    public void reReplicate() {
        int factor = props.getReplicationFactor();
        List<String> under = replicaRepo.findUnderReplicated(factor);
        if (under.isEmpty()) return;
        log.info("Re-replication scan: {} blocks under-replicated", under.size());
        for (String blockId : under) {
            try {
                rereplicateOne(blockId);
            } catch (Exception ex) {
                log.warn("Failed re-replicating block {}: {}", blockId, ex.getMessage());
            }
        }
    }

    private void rereplicateOne(String blockId) throws Exception {
        List<BlockReplicaEntity> replicas = replicaRepo.findByBlockId(blockId);
        Set<String> liveDnIds = new HashSet<>();
        BlockReplicaEntity sourceReplica = null;
        for (BlockReplicaEntity r : replicas) {
            if ("LIVE".equals(r.getStatus())) {
                liveDnIds.add(r.getDatanodeId());
                if (sourceReplica == null) sourceReplica = r;
            }
        }
        if (sourceReplica == null) {
            log.error("Block {} has no LIVE replica, cannot re-replicate", blockId);
            return;
        }
        // pick a target distinct from current LIVE set
        List<DataNodeEntity> alive = dataNodeService.liveDataNodes();
        DataNodeEntity target = alive.stream()
                .filter(d -> !liveDnIds.contains(d.getId()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            log.warn("No available DataNode to host re-replication of {}", blockId);
            return;
        }
        DataNodeEntity source = dataNodeRepo.findById(sourceReplica.getDatanodeId()).orElseThrow();

        // Create PENDING replica row for target
        BlockReplicaEntity newRep = new BlockReplicaEntity();
        newRep.setBlockId(blockId);
        newRep.setDatanodeId(target.getId());
        newRep.setStatus("PENDING");
        replicaRepo.save(newRep);

        // Order source DN to copy block to target
        ClusterDtos.ReplicateOrder order = new ClusterDtos.ReplicateOrder();
        order.blockId = blockId;
        order.target = dataNodeService.toRef(target);
        String body = mapper.writeValueAsString(order);
        String token = jwtUtil.generate("__namenode__", -1L);
        URI uri = URI.create("http://" + source.getHost() + ":" + source.getPort()
                + "/blocks/" + blockId + "/replicate");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 == 2) {
            newRep.setStatus("LIVE");
            newRep.setUpdatedAt(Instant.now());
            replicaRepo.save(newRep);
            log.info("Re-replicated block {} from {} -> {}", blockId, source.getId(), target.getId());
        } else {
            log.warn("Re-replication failed: status {} body {}", resp.statusCode(), resp.body());
            replicaRepo.delete(newRep);
        }
    }

    /** Returns blocks that the DataNode should delete (replicas marked DELETED). */
    @Transactional
    public List<String> drainGcInstructions(String dataNodeId) {
        List<String> out = new ArrayList<>();
        List<BlockReplicaEntity> deleted = replicaRepo.findByDatanodeIdAndStatus(dataNodeId, "DELETED");
        for (BlockReplicaEntity r : deleted) {
            out.add(r.getBlockId());
            replicaRepo.delete(r);
        }
        return out;
    }
}
