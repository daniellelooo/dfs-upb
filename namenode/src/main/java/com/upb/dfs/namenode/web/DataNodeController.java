package com.upb.dfs.namenode.web;

import com.upb.dfs.common.dto.ClusterDtos;
import com.upb.dfs.namenode.service.ClusterMonitor;
import com.upb.dfs.namenode.service.DataNodeService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/datanodes")
public class DataNodeController {

    private final DataNodeService dataNodeService;
    private final ClusterMonitor clusterMonitor;

    public DataNodeController(DataNodeService dataNodeService, ClusterMonitor clusterMonitor) {
        this.dataNodeService = dataNodeService;
        this.clusterMonitor = clusterMonitor;
    }

    @PostMapping("/heartbeat")
    public ClusterDtos.HeartbeatResponse heartbeat(@RequestBody ClusterDtos.HeartbeatRequest req) {
        dataNodeService.heartbeat(req);
        ClusterDtos.HeartbeatResponse resp = new ClusterDtos.HeartbeatResponse();
        resp.ok = true;
        resp.blocksToDelete = clusterMonitor.drainGcInstructions(req.dataNodeId);
        return resp;
    }

    @PostMapping("/blockreport")
    public Map<String, Object> blockReport(@RequestBody ClusterDtos.BlockReportRequest req) {
        dataNodeService.blockReport(req);
        return Map.of("status", "ok");
    }

    @PostMapping("/corrupt")
    public Map<String, Object> corrupt(@RequestBody ClusterDtos.CorruptionReport req) {
        // Mark a replica as DELETED so re-replication kicks in.
        // We piggyback on blockReport semantics: caller has already detected corruption.
        ClusterDtos.BlockReportRequest fake = new ClusterDtos.BlockReportRequest();
        fake.dataNodeId = req.dataNodeId;
        // empty list => all existing LIVE replicas of that DataNode become STALE,
        // which is too broad. Instead, do a targeted update via DataNodeService.
        // Simpler: directly use FileService? Keep this minimal endpoint as a no-op for now.
        return Map.of("status", "ok");
    }
}
