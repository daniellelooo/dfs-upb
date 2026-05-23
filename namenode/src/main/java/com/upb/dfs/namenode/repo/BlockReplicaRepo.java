package com.upb.dfs.namenode.repo;

import com.upb.dfs.namenode.entity.BlockReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockReplicaRepo extends JpaRepository<BlockReplicaEntity, Long> {
    List<BlockReplicaEntity> findByBlockId(String blockId);
    List<BlockReplicaEntity> findByDatanodeId(String datanodeId);
    List<BlockReplicaEntity> findByDatanodeIdAndStatus(String datanodeId, String status);
    Optional<BlockReplicaEntity> findByBlockIdAndDatanodeId(String blockId, String datanodeId);

    @Query("SELECT r.blockId FROM BlockReplicaEntity r WHERE r.status = 'LIVE' " +
           "GROUP BY r.blockId HAVING COUNT(r.id) < :minReplicas")
    List<String> findUnderReplicated(@Param("minReplicas") int minReplicas);
}
