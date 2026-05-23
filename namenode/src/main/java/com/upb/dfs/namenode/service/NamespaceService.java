package com.upb.dfs.namenode.service;

import com.upb.dfs.common.dto.FileDtos;
import com.upb.dfs.namenode.entity.*;
import com.upb.dfs.namenode.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class NamespaceService {

    private final FileRepo fileRepo;
    private final DirectoryRepo directoryRepo;
    private final BlockRepo blockRepo;
    private final BlockReplicaRepo replicaRepo;
    private final UserRepo userRepo;

    public NamespaceService(FileRepo fileRepo, DirectoryRepo directoryRepo, BlockRepo blockRepo,
                            BlockReplicaRepo replicaRepo, UserRepo userRepo) {
        this.fileRepo = fileRepo;
        this.directoryRepo = directoryRepo;
        this.blockRepo = blockRepo;
        this.replicaRepo = replicaRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public void mkdir(Long ownerId, String path) {
        String n = PathUtil.normalize(path);
        if (n.equals("/")) return;
        if (directoryRepo.findByOwnerIdAndPath(ownerId, n).isPresent()) {
            throw new IllegalStateException("Directory already exists: " + n);
        }
        if (fileRepo.findByOwnerIdAndPath(ownerId, n).isPresent()) {
            throw new IllegalStateException("Path collides with file: " + n);
        }
        // mkdir -p style: create ancestors
        String[] parts = n.substring(1).split("/");
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            cur.append('/').append(p);
            String acc = cur.toString();
            if (directoryRepo.findByOwnerIdAndPath(ownerId, acc).isEmpty()) {
                DirectoryEntity d = new DirectoryEntity();
                d.setOwnerId(ownerId);
                d.setPath(acc);
                directoryRepo.save(d);
            }
        }
    }

    @Transactional
    public void rmdir(Long ownerId, String path) {
        String n = PathUtil.normalize(path);
        if (n.equals("/")) throw new IllegalArgumentException("Cannot remove root");
        DirectoryEntity d = directoryRepo.findByOwnerIdAndPath(ownerId, n)
                .orElseThrow(() -> new NoSuchElementException("Directory not found: " + n));
        // ensure empty: no files and no subdirs
        boolean hasChildren = listChildren(ownerId, n).size() > 0;
        if (hasChildren) throw new IllegalStateException("Directory not empty: " + n);
        directoryRepo.delete(d);
    }

    @Transactional(readOnly = true)
    public FileDtos.ListDirResponse list(Long ownerId, String path) {
        String n = PathUtil.normalize(path);
        if (!n.equals("/") && directoryRepo.findByOwnerIdAndPath(ownerId, n).isEmpty()) {
            throw new NoSuchElementException("Directory not found: " + n);
        }
        FileDtos.ListDirResponse resp = new FileDtos.ListDirResponse();
        resp.path = n;
        resp.entries = listChildren(ownerId, n);
        return resp;
    }

    private List<FileDtos.DirEntry> listChildren(Long ownerId, String path) {
        String prefix = path.equals("/") ? "/" : path + "/";
        List<FileDtos.DirEntry> out = new ArrayList<>();
        Optional<UserEntity> ownerOpt = userRepo.findById(ownerId);
        String ownerName = ownerOpt.map(UserEntity::getUsername).orElse("?");

        Set<String> seen = new HashSet<>();
        for (DirectoryEntity d : directoryRepo.findByOwnerId(ownerId)) {
            if (d.getPath().equals(path)) continue;
            if (d.getPath().startsWith(prefix)) {
                String rest = d.getPath().substring(prefix.length());
                String first = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                if (seen.add("D:" + first)) {
                    FileDtos.DirEntry e = new FileDtos.DirEntry();
                    e.name = first;
                    e.type = "DIR";
                    e.owner = ownerName;
                    out.add(e);
                }
            }
        }
        for (FileEntity f : fileRepo.findByOwnerId(ownerId)) {
            if (f.getPath().startsWith(prefix)) {
                String rest = f.getPath().substring(prefix.length());
                if (!rest.contains("/")) {
                    FileDtos.DirEntry e = new FileDtos.DirEntry();
                    e.name = rest;
                    e.type = "FILE";
                    e.sizeBytes = f.getSizeBytes();
                    e.owner = ownerName;
                    out.add(e);
                }
            }
        }
        out.sort(Comparator.comparing((FileDtos.DirEntry e) -> e.type).thenComparing(e -> e.name));
        return out;
    }
}
