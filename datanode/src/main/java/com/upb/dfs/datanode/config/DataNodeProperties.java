package com.upb.dfs.datanode.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "dfs")
public class DataNodeProperties {
    private String datanodeId;
    private String datanodeHost;
    private int datanodePort;
    private String blocksDir;
    private long capacityMb = 10_240;
    private String namenodeUrl;
    private String jwtSecret;
    private long heartbeatIntervalSeconds = 3;
    private long blockreportIntervalSeconds = 60;

    public long capacityBytes() { return capacityMb * 1024L * 1024L; }

    public String getDatanodeId() { return datanodeId; }
    public void setDatanodeId(String v) { this.datanodeId = v; }
    public String getDatanodeHost() { return datanodeHost; }
    public void setDatanodeHost(String v) { this.datanodeHost = v; }
    public int getDatanodePort() { return datanodePort; }
    public void setDatanodePort(int v) { this.datanodePort = v; }
    public String getBlocksDir() { return blocksDir; }
    public void setBlocksDir(String v) { this.blocksDir = v; }
    public long getCapacityMb() { return capacityMb; }
    public void setCapacityMb(long v) { this.capacityMb = v; }
    public String getNamenodeUrl() { return namenodeUrl; }
    public void setNamenodeUrl(String v) { this.namenodeUrl = v; }
    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String v) { this.jwtSecret = v; }
    public long getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public void setHeartbeatIntervalSeconds(long v) { this.heartbeatIntervalSeconds = v; }
    public long getBlockreportIntervalSeconds() { return blockreportIntervalSeconds; }
    public void setBlockreportIntervalSeconds(long v) { this.blockreportIntervalSeconds = v; }
}
