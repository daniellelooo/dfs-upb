package com.upb.dfs.namenode.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "blocks", indexes = {
        @Index(name = "idx_block_file", columnList = "file_id")
})
public class BlockEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(nullable = false)
    private int sequenceIndex;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(length = 80)
    private String hashSha256;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public int getSequenceIndex() { return sequenceIndex; }
    public void setSequenceIndex(int sequenceIndex) { this.sequenceIndex = sequenceIndex; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getHashSha256() { return hashSha256; }
    public void setHashSha256(String hashSha256) { this.hashSha256 = hashSha256; }
}
