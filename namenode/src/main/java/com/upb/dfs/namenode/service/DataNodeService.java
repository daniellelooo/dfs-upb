package com.upb.dfs.namenode.service;

import com.upb.dfs.common.dto.ClusterDtos;
import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.namenode.config.DfsProperties;
import com.upb.dfs.namenode.entity.BlockReplicaEntity;
import com.upb.dfs.namenode.entity.DataNodeEntity;
import com.upb.dfs.namenode.repo.BlockReplicaRepo;
import com.upb.dfs.namenode.repo.DataNodeRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class DataNodeService {

    private final DataNodeRepo dataNodeRepo;
    private final BlockReplicaRepo replicaRepo;
    private final DfsProperties props;

    private final Map<String, Integer> recentAssignments = new HashMap<>();

    public DataNodeService(DataNodeRepo dataNodeRepo, BlockReplicaRepo replicaRepo, DfsProperties props) {
        this.dataNodeRepo = dataNodeRepo;
        this.replicaRepo = replicaRepo;
        this.props = props;
    }

    @Transactional
    public void heartbeat(ClusterDtos.HeartbeatRequest req) {
        DataNodeEntity dn = dataNodeRepo.findById(req.dataNodeId).orElseGet(DataNodeEntity::new);
        dn.setId(req.dataNodeId);
        dn.setHost(req.host);
        dn.setPort(req.port);
        dn.setCapacityBytes(req.capacityBytes);
        dn.setUsedBytes(req.usedBytes);
        dn.setLastHeartbeat(Instant.now());
        dn.setStatus("LIVE");
        dataNodeRepo.save(dn);
    }

    @Transactional
    public void blockReport(ClusterDtos.BlockReportRequest req) {
        Set<String> reported = new HashSet<>();
        for (ClusterDtos.BlockReportEntry e : req.blocks) reported.add(e.blockId);

        // Mark replicas LIVE if reported, mark missing as STALE.
        List<BlockReplicaEntity> existing = replicaRepo.findByDatanodeId(req.dataNodeId);
        for (BlockReplicaEntity r : existing) {
            if (reported.contains(r.getBlockId())) {
                if (!"LIVE".equals(r.getStatus())) {
                    r.setStatus("LIVE");
                    r.setUpdatedAt(Instant.now());
                    replicaRepo.save(r);
                }
                reported.remove(r.getBlockId());
            } else if ("LIVE".equals(r.getStatus()) || "PENDING".equals(r.getStatus())) {
                r.setStatus("STALE");
                r.setUpdatedAt(Instant.now());
                replicaRepo.save(r);
            }
        }
        // Any reported blocks not yet tracked: NameNode is source of truth, so these are
        // orphan replicas (e.g., file deleted). They will be GC'd via heartbeat instructions.
    }

    @Transactional(readOnly = true)
    public List<DataNodeEntity> liveDataNodes() {
        Instant cutoff = Instant.now().minusSeconds(props.getHeartbeatSuspectSeconds());
        return dataNodeRepo.findByStatus("LIVE").stream()
                .filter(dn -> dn.getLastHeartbeat().isAfter(cutoff))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** Score-based selection of N distinct DataNodes for a new block. */
    public List<DataNodeEntity> chooseTargets(int count) {
        List<DataNodeEntity> alive = liveDataNodes();
        if (alive.size() < count) {
            throw new IllegalStateException("Not enough live DataNodes (need "
                    + count + ", have " + alive.size() + ")");
        }
        alive.sort(Comparator.comparingDouble((DataNodeEntity d) -> {
            double freeRatio = d.getCapacityBytes() <= 0 ? 0.0
                    : 1.0 - ((double) d.getUsedBytes() / (double) d.getCapacityBytes());
            int recent = recentAssignments.getOrDefault(d.getId(), 0);
            return -(1.0 * freeRatio - 0.1 * recent); // descending by score
        }));
        List<DataNodeEntity> out = new ArrayList<>(alive.subList(0, count));
        for (DataNodeEntity d : out) {
            recentAssignments.merge(d.getId(), 1, Integer::sum);
        }
        return out;
    }

    public void resetRecent() { recentAssignments.clear(); }

    public FileDtos.DataNodeRef toRef(DataNodeEntity dn) {
        return new FileDtos.DataNodeRef(dn.getId(), dn.getHost(), dn.getPort());
    }
}
