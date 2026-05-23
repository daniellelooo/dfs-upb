package com.upb.dfs.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upb.dfs.common.dto.AuthDtos;
import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.common.util.HashUtil;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

public class DfsClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String namenodeUrl;
    private final String token;

    public DfsClient(String namenodeUrl, String token) {
        this.namenodeUrl = namenodeUrl;
        this.token = token;
    }

    public static AuthDtos.LoginResponse login(String namenodeUrl, String username, String password)
            throws Exception {
        AuthDtos.LoginRequest req = new AuthDtos.LoginRequest();
        req.username = username; req.password = password;
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(namenodeUrl + "/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(req)))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Login failed: " + resp.statusCode() + " " + resp.body());
        }
        return MAPPER.readValue(resp.body(), AuthDtos.LoginResponse.class);
    }

    public static void register(String namenodeUrl, String username, String password) throws Exception {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest();
        req.username = username; req.password = password;
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(namenodeUrl + "/auth/register"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(req)))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Register failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private HttpRequest.Builder authReq(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token);
    }

    public FileDtos.ListDirResponse ls(String path) throws Exception {
        URI uri = URI.create(namenodeUrl + "/dirs?path="
                + URLEncoder.encode(path, StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(authReq(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        ensureOk(r, "ls");
        return MAPPER.readValue(r.body(), FileDtos.ListDirResponse.class);
    }

    public void mkdir(String path) throws Exception {
        FileDtos.CreateDirRequest req = new FileDtos.CreateDirRequest();
        req.path = path;
        URI uri = URI.create(namenodeUrl + "/dirs");
        HttpResponse<String> r = http.send(authReq(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(req))).build(),
                HttpResponse.BodyHandlers.ofString());
        ensureOk(r, "mkdir");
    }

    public void rmdir(String path) throws Exception {
        URI uri = URI.create(namenodeUrl + "/dirs?path="
                + URLEncoder.encode(path, StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(authReq(uri).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        ensureOk(r, "rmdir");
    }

    public void rm(String path) throws Exception {
        URI uri = URI.create(namenodeUrl + "/files?path="
                + URLEncoder.encode(path, StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(authReq(uri).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        ensureOk(r, "rm");
    }

    public FileDtos.FileMetadataResponse stat(String path) throws Exception {
        URI uri = URI.create(namenodeUrl + "/files/meta?path="
                + URLEncoder.encode(path, StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(authReq(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        ensureOk(r, "stat");
        return MAPPER.readValue(r.body(), FileDtos.FileMetadataResponse.class);
    }

    public void put(Path local, String remote, long blockSize) throws Exception {
        if (!Files.isRegularFile(local)) throw new IOException("Local file not found: " + local);
        long size = Files.size(local);
        int numBlocks = size == 0 ? 1 : (int) ((size + blockSize - 1) / blockSize);

        FileDtos.CreateFileRequest createReq = new FileDtos.CreateFileRequest();
        createReq.path = remote;
        createReq.sizeBytes = size;
        createReq.blockSize = blockSize;
        createReq.numBlocks = numBlocks;

        URI createUri = URI.create(namenodeUrl + "/files");
        HttpResponse<String> cresp = http.send(authReq(createUri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(createReq)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        ensureOk(cresp, "create");
        FileDtos.CreateFileResponse plan = MAPPER.readValue(cresp.body(), FileDtos.CreateFileResponse.class);

        FileDtos.CommitFileRequest commit = new FileDtos.CommitFileRequest();

        try (InputStream in = new BufferedInputStream(Files.newInputStream(local))) {
            for (FileDtos.BlockPlan bp : plan.blocks) {
                byte[] buf = new byte[(int) bp.sizeBytes];
                int read = 0;
                while (read < buf.length) {
                    int r = in.read(buf, read, buf.length - read);
                    if (r == -1) break;
                    read += r;
                }
                if (read != buf.length) {
                    throw new IOException("Short read on block " + bp.sequenceIndex);
                }
                String hash = HashUtil.sha256Hex(buf);

                FileDtos.DataNodeRef primary = bp.replicas.get(0);
                StringBuilder pipe = new StringBuilder();
                for (int i = 1; i < bp.replicas.size(); i++) {
                    if (pipe.length() > 0) pipe.append(',');
                    FileDtos.DataNodeRef dn = bp.replicas.get(i);
                    pipe.append(dn.host).append(':').append(dn.port);
                }

                URI uri = URI.create("http://" + primary.host + ":" + primary.port + "/blocks/" + bp.blockId);
                HttpRequest.Builder b = authReq(uri)
                        .timeout(Duration.ofMinutes(15))
                        .header("X-DFS-Hash", hash);
                if (pipe.length() > 0) b.header("X-DFS-Pipeline", pipe.toString());
                HttpResponse<String> resp = http.send(
                        b.PUT(HttpRequest.BodyPublishers.ofByteArray(buf)).build(),
                        HttpResponse.BodyHandlers.ofString());
                ensureOk(resp, "PUT block " + bp.sequenceIndex);

                FileDtos.BlockCommit bc = new FileDtos.BlockCommit();
                bc.blockId = bp.blockId;
                bc.hashSha256 = hash;
                bc.sizeBytes = bp.sizeBytes;
                for (FileDtos.DataNodeRef dn : bp.replicas) bc.liveReplicas.add(dn.id);
                commit.blocks.add(bc);
                System.out.println("  block " + (bp.sequenceIndex + 1) + "/" + plan.blocks.size()
                        + " uploaded (" + bp.sizeBytes + " bytes, sha256=" + hash.substring(0, 12)
                        + "..., replicas=" + bp.replicas.size() + ")");
            }
        }

        URI commitUri = URI.create(namenodeUrl + "/files/commit?path="
                + URLEncoder.encode(remote, StandardCharsets.UTF_8));
        HttpResponse<String> cr = http.send(authReq(commitUri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(commit))).build(),
                HttpResponse.BodyHandlers.ofString());
        ensureOk(cr, "commit");
    }

    public void get(String remote, Path local) throws Exception {
        FileDtos.FileMetadataResponse meta = stat(remote);
        if (!"COMPLETE".equals(meta.status)) {
            throw new IOException("File not yet committed: " + remote);
        }
        Files.createDirectories(local.toAbsolutePath().getParent());
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(local,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (FileDtos.BlockLocation bl : meta.blocks) {
                byte[] data = downloadBlockWithFailover(bl);
                out.write(data);
                System.out.println("  block " + (bl.sequenceIndex + 1) + "/" + meta.blocks.size()
                        + " downloaded (" + data.length + " bytes)");
            }
        }
    }

    private byte[] downloadBlockWithFailover(FileDtos.BlockLocation bl) throws Exception {
        if (bl.replicas == null || bl.replicas.isEmpty()) {
            throw new IOException("Block " + bl.blockId + " has no live replicas");
        }
        List<FileDtos.DataNodeRef> shuffled = new ArrayList<>(bl.replicas);
        Collections.shuffle(shuffled);
        Exception last = null;
        for (FileDtos.DataNodeRef dn : shuffled) {
            try {
                URI uri = URI.create("http://" + dn.host + ":" + dn.port + "/blocks/" + bl.blockId);
                HttpResponse<byte[]> r = http.send(authReq(uri).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() / 100 != 2) {
                    last = new IOException("DataNode " + dn.id + " responded " + r.statusCode());
                    continue;
                }
                byte[] body = r.body();
                MessageDigest sha = HashUtil.newSha256();
                sha.update(body);
                String hex = HashUtil.toHex(sha.digest());
                if (bl.hashSha256 != null && !bl.hashSha256.equalsIgnoreCase(hex)) {
                    last = new IOException("Hash mismatch on block " + bl.blockId
                            + " from " + dn.id + " (got " + hex + ")");
                    continue;
                }
                return body;
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new IOException("All replicas failed for block " + bl.blockId, last);
    }

    private void ensureOk(HttpResponse<String> r, String op) throws IOException {
        if (r.statusCode() / 100 != 2) {
            throw new IOException(op + " failed: " + r.statusCode() + " " + r.body());
        }
    }
}
