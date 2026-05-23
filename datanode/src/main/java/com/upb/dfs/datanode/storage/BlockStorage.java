package com.upb.dfs.datanode.storage;

import com.upb.dfs.common.util.HashUtil;
import com.upb.dfs.datanode.config.DataNodeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class BlockStorage {

    private static final Logger log = LoggerFactory.getLogger(BlockStorage.class);
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final DataNodeProperties props;
    private Path root;

    public BlockStorage(DataNodeProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() throws IOException {
        root = Paths.get(props.getBlocksDir());
        Files.createDirectories(root);
        log.info("BlockStorage initialized at {}", root.toAbsolutePath());
    }

    private Path resolve(String blockId) {
        if (!UUID_PATTERN.matcher(blockId).matches()) {
            throw new IllegalArgumentException("Invalid blockId");
        }
        return root.resolve(blockId);
    }

    public boolean exists(String blockId) {
        return Files.exists(resolve(blockId));
    }

    public long size(String blockId) throws IOException {
        return Files.size(resolve(blockId));
    }

    public String writeBlock(String blockId, InputStream in, String expectedHash) throws IOException {
        Path target = resolve(blockId);
        Path tmp = target.resolveSibling(blockId + ".tmp");
        MessageDigest sha = HashUtil.newSha256();
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                sha.update(buf, 0, r);
                out.write(buf, 0, r);
            }
        }
        String hex = HashUtil.toHex(sha.digest());
        if (expectedHash != null && !expectedHash.isBlank()
                && !hex.equalsIgnoreCase(expectedHash)) {
            Files.deleteIfExists(tmp);
            throw new IOException("Hash mismatch: expected " + expectedHash + " got " + hex);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return hex;
    }

    public InputStream readBlock(String blockId) throws IOException {
        return Files.newInputStream(resolve(blockId));
    }

    public boolean delete(String blockId) throws IOException {
        return Files.deleteIfExists(resolve(blockId));
    }

    public long usedBytes() {
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0L; }
            }).sum();
        } catch (IOException e) { return 0L; }
    }

    public List<BlockInfo> listBlocks() {
        List<BlockInfo> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                if (!UUID_PATTERN.matcher(name).matches()) return;
                try {
                    BlockInfo bi = new BlockInfo();
                    bi.blockId = name;
                    bi.sizeBytes = Files.size(p);
                    out.add(bi);
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            log.warn("listBlocks error: {}", e.getMessage());
        }
        return out;
    }

    public String hashOf(String blockId) throws IOException {
        try (InputStream in = readBlock(blockId)) {
            MessageDigest sha = HashUtil.newSha256();
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) sha.update(buf, 0, r);
            return HashUtil.toHex(sha.digest());
        }
    }

    public static class BlockInfo {
        public String blockId;
        public long sizeBytes;
    }
}
