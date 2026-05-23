package com.upb.dfs.namenode.repo;

import com.upb.dfs.namenode.entity.BlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockRepo extends JpaRepository<BlockEntity, String> {
    List<BlockEntity> findByFileIdOrderBySequenceIndexAsc(Long fileId);
}
