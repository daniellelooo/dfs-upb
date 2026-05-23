package com.upb.dfs.common.dto;

import java.util.ArrayList;
import java.util.List;

public class ClusterDtos {

    public static class HeartbeatRequest {
        public String dataNodeId;
        public String host;
        public int port;
        public long capacityBytes;
        public long usedBytes;
        public int blockCount;
    }

    public static class HeartbeatResponse {
        public boolean ok;
        public List<String> blocksToDelete = new ArrayList<>();
    }

    public static class BlockReportEntry {
        public String blockId;
        public long sizeBytes;
        public String hashSha256;
    }

    public static class BlockReportRequest {
        public String dataNodeId;
        public List<BlockReportEntry> blocks = new ArrayList<>();
    }

    public static class CorruptionReport {
        public String blockId;
        public String dataNodeId;
    }

    public static class ReplicateOrder {
        public String blockId;
        public FileDtos.DataNodeRef target;
    }
}
