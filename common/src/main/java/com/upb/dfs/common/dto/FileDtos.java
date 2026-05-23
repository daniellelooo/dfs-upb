package com.upb.dfs.common.dto;

import java.util.ArrayList;
import java.util.List;

public class FileDtos {

    public static class DataNodeRef {
        public String id;
        public String host;
        public int port;

        public DataNodeRef() {}
        public DataNodeRef(String id, String host, int port) {
            this.id = id; this.host = host; this.port = port;
        }
    }

    public static class BlockPlan {
        public String blockId;
        public int sequenceIndex;
        public long sizeBytes;
        public List<DataNodeRef> replicas = new ArrayList<>();
    }

    public static class CreateFileRequest {
        public String path;
        public long sizeBytes;
        public long blockSize;
        public int numBlocks;
    }

    public static class CreateFileResponse {
        public String fileId;
        public long blockSize;
        public List<BlockPlan> blocks = new ArrayList<>();
    }

    public static class CommitFileRequest {
        public List<BlockCommit> blocks = new ArrayList<>();
    }

    public static class BlockCommit {
        public String blockId;
        public String hashSha256;
        public long sizeBytes;
        public List<String> liveReplicas = new ArrayList<>();
    }

    public static class FileMetadataResponse {
        public String fileId;
        public String path;
        public long sizeBytes;
        public long blockSize;
        public String status;
        public String owner;
        public List<BlockLocation> blocks = new ArrayList<>();
    }

    public static class BlockLocation {
        public String blockId;
        public int sequenceIndex;
        public long sizeBytes;
        public String hashSha256;
        public List<DataNodeRef> replicas = new ArrayList<>();
    }

    public static class DirEntry {
        public String name;
        public String type; // FILE or DIR
        public long sizeBytes;
        public String owner;
    }

    public static class ListDirResponse {
        public String path;
        public List<DirEntry> entries = new ArrayList<>();
    }

    public static class CreateDirRequest {
        public String path;
    }
}
