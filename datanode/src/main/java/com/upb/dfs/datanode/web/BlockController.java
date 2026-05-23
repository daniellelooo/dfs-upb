package com.upb.dfs.datanode.web;

import com.upb.dfs.common.dto.ClusterDtos;
import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.datanode.config.DataNodeProperties;
import com.upb.dfs.datanode.storage.BlockStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blocks")
public class BlockController {

    private static final Logger log = LoggerFactory.getLogger(BlockController.class);

    private final BlockStorage storage;
    private final DataNodeProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public BlockController(BlockStorage storage, DataNodeProperties props) {
        this.storage = storage;
        this.props = props;
    }

    /** Receive bytes for a block. Header `X-DFS-Hash` carries the expected SHA-256.
     *  Header `X-DFS-Pipeline` (CSV `host:port,host:port`) lists downstream peers. */
    @PutMapping("/{blockId}")
    public ResponseEntity<?> receive(@PathVariable String blockId,
                                     @RequestHeader(value = "X-DFS-Hash", required = false) String expectedHash,
                                     @RequestHeader(value = "X-DFS-Pipeline", required = false) String pipeline,
                                     HttpServletRequest request) {
        try (InputStream in = request.getInputStream()) {
            String storedHash = storage.writeBlock(blockId, in, expectedHash);
            forwardPipeline(blockId, storedHash, pipeline);
            return ResponseEntity.ok(Map.of("status", "ok", "hashSha256", storedHash,
                    "sizeBytes", storage.size(blockId)));
        } catch (Exception ex) {
            log.warn("PUT /blocks/{} failed: {}", blockId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }

    private void forwardPipeline(String blockId, String hash, String pipeline) throws Exception {
        if (pipeline == null || pipeline.isBlank()) return;
        String[] hops = pipeline.split(",");
        if (hops.length == 0) return;
        String next = hops[0].trim();
        String rest = String.join(",", java.util.Arrays.copyOfRange(hops, 1, hops.length));

        // Forward block bytes to next peer using PUT /blocks/{id}
        String authzHeader = currentAuthorization();
        URI uri = URI.create("http://" + next + "/blocks/" + blockId);
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("Authorization", authzHeader)
                .header("X-DFS-Hash", hash);
        if (!rest.isBlank()) b.header("X-DFS-Pipeline", rest);
        try (InputStream in = storage.readBlock(blockId)) {
            HttpRequest req = b.PUT(HttpRequest.BodyPublishers.ofInputStream(() -> in)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("Pipeline next-hop " + next + " failed: "
                        + resp.statusCode() + " " + resp.body());
            }
        }
    }

    private String currentAuthorization() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            String h = sra.getRequest().getHeader("Authorization");
            if (h != null) return h;
        }
        return "";
    }

    @GetMapping("/{blockId}")
    public void serve(@PathVariable String blockId, HttpServletResponse resp) throws Exception {
        if (!storage.exists(blockId)) {
            resp.sendError(404, "block not found");
            return;
        }
        long size = storage.size(blockId);
        String hash = storage.hashOf(blockId);
        resp.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        resp.setContentLengthLong(size);
        resp.setHeader("X-DFS-Hash", hash);
        resp.setHeader("X-DFS-DataNode", props.getDatanodeId());
        try (InputStream in = storage.readBlock(blockId);
             OutputStream out = resp.getOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
    }

    @DeleteMapping("/{blockId}")
    public Map<String, Object> remove(@PathVariable String blockId) throws Exception {
        boolean removed = storage.delete(blockId);
        return Map.of("status", "ok", "deleted", removed);
    }

    /** NameNode-driven re-replication: copy a local block to a target DataNode. */
    @PostMapping("/{blockId}/replicate")
    public ResponseEntity<?> replicate(@PathVariable String blockId,
                                       @RequestBody ClusterDtos.ReplicateOrder order) {
        try {
            if (!storage.exists(blockId)) {
                return ResponseEntity.status(404).body(Map.of("error", "block missing"));
            }
            FileDtos.DataNodeRef target = order.target;
            String hash = storage.hashOf(blockId);
            URI uri = URI.create("http://" + target.host + ":" + target.port + "/blocks/" + blockId);
            String authz = currentAuthorization();
            try (InputStream in = storage.readBlock(blockId)) {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofMinutes(10))
                        .header("Authorization", authz)
                        .header("X-DFS-Hash", hash)
                        .PUT(HttpRequest.BodyPublishers.ofInputStream(() -> in))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) {
                    return ResponseEntity.status(502).body(Map.of("error",
                            "target " + target.id + " responded " + resp.statusCode()));
                }
            }
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception ex) {
            log.warn("/replicate failed for {}: {}", blockId, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", ex.getMessage()));
        }
    }
}
