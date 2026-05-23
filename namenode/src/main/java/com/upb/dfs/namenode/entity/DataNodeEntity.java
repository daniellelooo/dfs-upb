package com.upb.dfs.namenode.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "datanodes")
public class DataNodeEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private long capacityBytes;

    @Column(nullable = false)
    private long usedBytes;

    @Column(nullable = false)
    private Instant lastHeartbeat = Instant.now();

    @Column(nullable = false, length = 16)
    private String status; // LIVE, SUSPECT, DEAD

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public long getCapacityBytes() { return capacityBytes; }
    public void setCapacityBytes(long capacityBytes) { this.capacityBytes = capacityBytes; }
    public long getUsedBytes() { return usedBytes; }
    public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
