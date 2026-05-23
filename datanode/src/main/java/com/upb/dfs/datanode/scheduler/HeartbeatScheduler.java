package com.upb.dfs.datanode.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upb.dfs.common.dto.ClusterDtos;
import com.upb.dfs.common.util.JwtUtil;
import com.upb.dfs.datanode.config.DataNodeProperties;
import com.upb.dfs.datanode.storage.BlockStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final DataNodeProperties props;
    private final BlockStorage storage;
    private final JwtUtil jwtUtil;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public HeartbeatScheduler(DataNodeProperties props, BlockStorage storage, JwtUtil jwtUtil) {
        this.props = props;
        this.storage = storage;
        this.jwtUtil = jwtUtil;
    }

    @Scheduled(fixedDelayString = "${dfs.heartbeat-interval-seconds:3}000",
               initialDelay = 2000)
    public void heartbeat() {
        try {
            ClusterDtos.HeartbeatRequest req = new ClusterDtos.HeartbeatRequest();
            req.dataNodeId = props.getDatanodeId();
            req.host = props.getDatanodeHost();
            req.port = props.getDatanodePort();
            req.capacityBytes = props.capacityBytes();
            req.usedBytes = storage.usedBytes();
            req.blockCount = storage.listBlocks().size();
            String body = mapper.writeValueAsString(req);
            HttpResponse<String> resp = post("/datanodes/heartbeat", body);
            if (resp.statusCode() / 100 == 2) {
                ClusterDtos.HeartbeatResponse hr = mapper.readValue(resp.body(),
                        ClusterDtos.HeartbeatResponse.class);
                if (hr.blocksToDelete != null) {
                    for (String b : hr.blocksToDelete) {
                        try {
                            if (storage.delete(b)) log.info("GC'd block {}", b);
                        } catch (Exception ignored) {}
                    }
                }
            } else {
                log.warn("Heartbeat returned {} body={}", resp.statusCode(), resp.body());
            }
        } catch (Exception ex) {
            log.warn("Heartbeat failed: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${dfs.blockreport-interval-seconds:60}000",
               initialDelay = 5000)
    public void blockReport() {
        try {
            ClusterDtos.BlockReportRequest req = new ClusterDtos.BlockReportRequest();
            req.dataNodeId = props.getDatanodeId();
            List<BlockStorage.BlockInfo> blocks = storage.listBlocks();
            for (BlockStorage.BlockInfo b : blocks) {
                ClusterDtos.BlockReportEntry e = new ClusterDtos.BlockReportEntry();
                e.blockId = b.blockId;
                e.sizeBytes = b.sizeBytes;
                req.blocks.add(e);
            }
            HttpResponse<String> resp = post("/datanodes/blockreport", mapper.writeValueAsString(req));
            if (resp.statusCode() / 100 != 2) {
                log.warn("BlockReport returned {}", resp.statusCode());
            }
        } catch (Exception ex) {
            log.warn("BlockReport failed: {}", ex.getMessage());
        }
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        String token = jwtUtil.generate("__datanode__:" + props.getDatanodeId(), -1L);
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.getNamenodeUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
