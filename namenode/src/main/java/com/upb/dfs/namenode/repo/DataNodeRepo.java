package com.upb.dfs.namenode.repo;

import com.upb.dfs.namenode.entity.DataNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DataNodeRepo extends JpaRepository<DataNodeEntity, String> {
    List<DataNodeEntity> findByStatus(String status);
    List<DataNodeEntity> findByLastHeartbeatBefore(Instant cutoff);
}
