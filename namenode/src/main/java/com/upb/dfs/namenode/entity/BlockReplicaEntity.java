package com.upb.dfs.namenode.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "block_replicas",
       uniqueConstraints = @UniqueConstraint(columnNames = {"block_id", "datanode_id"}),
       indexes = {
           @Index(name = "idx_replica_block", columnList = "block_id"),
           @Index(name = "idx_replica_datanode", columnList = "datanode_id")
       })
public class BlockReplicaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_id", nullable = false, length = 64)
    private String blockId;

    @Column(name = "datanode_id", nullable = false, length = 64)
    private String datanodeId;

    @Column(nullable = false, length = 16)
    private String status; // PENDING, LIVE, STALE, DELETED

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBlockId() { return blockId; }
    public void setBlockId(String blockId) { this.blockId = blockId; }
    public String getDatanodeId() { return datanodeId; }
    public void setDatanodeId(String datanodeId) { this.datanodeId = datanodeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
