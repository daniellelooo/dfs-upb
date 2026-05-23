package com.upb.dfs.namenode.repo;

import com.upb.dfs.namenode.entity.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepo extends JpaRepository<FileEntity, Long> {
    Optional<FileEntity> findByOwnerIdAndPath(Long ownerId, String path);
    List<FileEntity> findByOwnerId(Long ownerId);
}
