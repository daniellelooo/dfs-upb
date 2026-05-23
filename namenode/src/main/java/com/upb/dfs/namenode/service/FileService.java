package com.upb.dfs.namenode.service;

import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.namenode.config.DfsProperties;
import com.upb.dfs.namenode.entity.*;
import com.upb.dfs.namenode.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class FileService {

    private final FileRepo fileRepo;
    private final BlockRepo blockRepo;
    private final BlockReplicaRepo replicaRepo;
    private final DirectoryRepo directoryRepo;
    private final UserRepo userRepo;
    private final DataNodeService dataNodeService;
    private final DfsProperties props;

    public FileService(FileRepo fileRepo, BlockRepo blockRepo, BlockReplicaRepo replicaRepo,
                       DirectoryRepo directoryRepo, UserRepo userRepo,
                       DataNodeService dataNodeService, DfsProperties props) {
        this.fileRepo = fileRepo;
        this.blockRepo = blockRepo;
        this.replicaRepo = replicaRepo;
        this.directoryRepo = directoryRepo;
        this.userRepo = userRepo;
        this.dataNodeService = dataNodeService;
        this.props = props;
    }

    @Transactional
    public FileDtos.CreateFileResponse createFile(Long ownerId, FileDtos.CreateFileRequest req) {
        String path = PathUtil.normalize(req.path);
        if (path.equals("/")) throw new IllegalArgumentException("Invalid file path");
        if (fileRepo.findByOwnerIdAndPath(ownerId, path).isPresent()) {
            throw new IllegalStateException("File already exists (WORM): " + path);
        }
        if (directoryRepo.findByOwnerIdAndPath(ownerId, path).isPresent()) {
            throw new IllegalStateException("Path is a directory: " + path);
        }
        // ensure parent dir exists
        String parent = PathUtil.parent(path);
        if (!parent.equals("/") && directoryRepo.findByOwnerIdAndPath(ownerId, parent).isEmpty()) {
            throw new IllegalStateException("Parent directory does not exist: " + parent);
        }

        long blockSize = req.blockSize > 0 ? req.blockSize : props.blockSizeBytes();
        FileEntity fe = new FileEntity();
        fe.setOwnerId(ownerId);
        fe.setPath(path);
        fe.setSizeBytes(req.sizeBytes);
        fe.setBlockSize(blockSize);
        fe.setStatus("PENDING");
        fileRepo.save(fe);

        FileDtos.CreateFileResponse resp = new FileDtos.CreateFileResponse();
        resp.fileId = String.valueOf(fe.getId());
        resp.blockSize = blockSize;

        int replication = props.getReplicationFactor();
        long remaining = req.sizeBytes;
        for (int i = 0; i < req.numBlocks; i++) {
            FileDtos.BlockPlan bp = new FileDtos.BlockPlan();
            bp.blockId = UUID.randomUUID().toString();
            bp.sequenceIndex = i;
            bp.sizeBytes = Math.min(blockSize, remaining);
            remaining -= bp.sizeBytes;

            BlockEntity be = new BlockEntity();
            be.setId(bp.blockId);
            be.setFileId(fe.getId());
            be.setSequenceIndex(i);
            be.setSizeBytes(bp.sizeBytes);
            blockRepo.save(be);

            List<DataNodeEntity> targets = dataNodeService.chooseTargets(replication);
            // Round-robin alternation: shift first DataNode for each block
            int shift = i % targets.size();
            List<DataNodeEntity> rotated = new ArrayList<>();
            for (int k = 0; k < targets.size(); k++) {
                rotated.add(targets.get((k + shift) % targets.size()));
            }
            for (DataNodeEntity dn : rotated) {
                BlockReplicaEntity r = new BlockReplicaEntity();
                r.setBlockId(bp.blockId);
                r.setDatanodeId(dn.getId());
                r.setStatus("PENDING");
                replicaRepo.save(r);
                bp.replicas.add(dataNodeService.toRef(dn));
            }
            resp.blocks.add(bp);
        }
        return resp;
    }

    @Transactional
    public void commitFile(Long ownerId, String path, FileDtos.CommitFileRequest req) {
        FileEntity f = fileRepo.findByOwnerIdAndPath(ownerId, PathUtil.normalize(path))
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        for (FileDtos.BlockCommit bc : req.blocks) {
            BlockEntity be = blockRepo.findById(bc.blockId)
                    .orElseThrow(() -> new NoSuchElementException("Unknown block " + bc.blockId));
            be.setHashSha256(bc.hashSha256);
            be.setSizeBytes(bc.sizeBytes);
            blockRepo.save(be);
            // mark replicas LIVE
            for (String dnId : bc.liveReplicas) {
                replicaRepo.findByBlockIdAndDatanodeId(bc.blockId, dnId).ifPresent(r -> {
                    r.setStatus("LIVE");
                    r.setUpdatedAt(Instant.now());
                    replicaRepo.save(r);
                });
            }
        }
        f.setStatus("COMPLETE");
        fileRepo.save(f);
    }

    @Transactional(readOnly = true)
    public FileDtos.FileMetadataResponse getFile(Long ownerId, String path) {
        FileEntity f = fileRepo.findByOwnerIdAndPath(ownerId, PathUtil.normalize(path))
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        FileDtos.FileMetadataResponse resp = new FileDtos.FileMetadataResponse();
        resp.fileId = String.valueOf(f.getId());
        resp.path = f.getPath();
        resp.sizeBytes = f.getSizeBytes();
        resp.blockSize = f.getBlockSize();
        resp.status = f.getStatus();
        resp.owner = userRepo.findById(ownerId).map(UserEntity::getUsername).orElse("?");

        Map<String, DataNodeEntity> dnCache = new HashMap<>();
        List<BlockEntity> blocks = blockRepo.findByFileIdOrderBySequenceIndexAsc(f.getId());
        for (BlockEntity be : blocks) {
            FileDtos.BlockLocation bl = new FileDtos.BlockLocation();
            bl.blockId = be.getId();
            bl.sequenceIndex = be.getSequenceIndex();
            bl.sizeBytes = be.getSizeBytes();
            bl.hashSha256 = be.getHashSha256();
            for (BlockReplicaEntity r : replicaRepo.findByBlockId(be.getId())) {
                if (!"LIVE".equals(r.getStatus())) continue;
                DataNodeEntity dn = dnCache.computeIfAbsent(r.getDatanodeId(), id ->
                        dataNodeService.liveDataNodes().stream()
                                .filter(d -> d.getId().equals(id)).findFirst().orElse(null));
                if (dn != null) bl.replicas.add(dataNodeService.toRef(dn));
            }
            resp.blocks.add(bl);
        }
        return resp;
    }

    @Transactional
    public void deleteFile(Long ownerId, String path) {
        FileEntity f = fileRepo.findByOwnerIdAndPath(ownerId, PathUtil.normalize(path))
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        // Mark replicas DELETED so heartbeat tells DataNodes to GC
        List<BlockEntity> blocks = blockRepo.findByFileIdOrderBySequenceIndexAsc(f.getId());
        for (BlockEntity be : blocks) {
            for (BlockReplicaEntity r : replicaRepo.findByBlockId(be.getId())) {
                r.setStatus("DELETED");
                r.setUpdatedAt(Instant.now());
                replicaRepo.save(r);
            }
            // we keep the block row briefly; GC happens via heartbeat instructions
        }
        fileRepo.delete(f);
    }
}
