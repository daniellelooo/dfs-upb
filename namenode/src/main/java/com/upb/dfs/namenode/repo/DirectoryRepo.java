package com.upb.dfs.namenode.repo;

import com.upb.dfs.namenode.entity.DirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DirectoryRepo extends JpaRepository<DirectoryEntity, Long> {
    Optional<DirectoryEntity> findByOwnerIdAndPath(Long ownerId, String path);
    List<DirectoryEntity> findByOwnerId(Long ownerId);
}
