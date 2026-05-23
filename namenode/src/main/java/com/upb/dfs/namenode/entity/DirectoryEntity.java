package com.upb.dfs.namenode.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "directories",
       uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "path"}))
public class DirectoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 1024)
    private String path;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
