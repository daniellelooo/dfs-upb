package com.upb.dfs.namenode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dfs")
public class DfsProperties {
    private int blockSizeMb = 64;
    private int replicationFactor = 2;
    private String jwtSecret = "change-me";
    private long jwtExpirationHours = 24;
    private long heartbeatSuspectSeconds = 10;
    private long heartbeatDeadSeconds = 30;
    private long rereplicationIntervalSeconds = 15;

    public long blockSizeBytes() { return ((long) blockSizeMb) * 1024L * 1024L; }

    public int getBlockSizeMb() { return blockSizeMb; }
    public void setBlockSizeMb(int blockSizeMb) { this.blockSizeMb = blockSizeMb; }
    public int getReplicationFactor() { return replicationFactor; }
    public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }
    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public long getJwtExpirationHours() { return jwtExpirationHours; }
    public void setJwtExpirationHours(long jwtExpirationHours) { this.jwtExpirationHours = jwtExpirationHours; }
    public long getHeartbeatSuspectSeconds() { return heartbeatSuspectSeconds; }
    public void setHeartbeatSuspectSeconds(long heartbeatSuspectSeconds) { this.heartbeatSuspectSeconds = heartbeatSuspectSeconds; }
    public long getHeartbeatDeadSeconds() { return heartbeatDeadSeconds; }
    public void setHeartbeatDeadSeconds(long heartbeatDeadSeconds) { this.heartbeatDeadSeconds = heartbeatDeadSeconds; }
    public long getRereplicationIntervalSeconds() { return rereplicationIntervalSeconds; }
    public void setRereplicationIntervalSeconds(long s) { this.rereplicationIntervalSeconds = s; }
}
