package com.urlshortener.u_r_l__service4.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "url.pool")
public class PoolProperties {
    private int lowWatermark = 500;
    private int refillBatchSize = 500;
    private int seedBatchSize = 5000;
    private int codeLength = 6;
    private String partitionStart;
    private String partitionEnd;

    public int getLowWatermark() {
        return lowWatermark;
    }

    public void setLowWatermark(int lowWatermark) {
        this.lowWatermark = lowWatermark;
    }

    public int getRefillBatchSize() {
        return refillBatchSize;
    }

    public void setRefillBatchSize(int refillBatchSize) {
        this.refillBatchSize = refillBatchSize;
    }

    public int getSeedBatchSize() {
        return seedBatchSize;
    }

    public void setSeedBatchSize(int seedBatchSize) {
        this.seedBatchSize = seedBatchSize;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public String getPartitionStart() {
        return partitionStart;
    }

    public void setPartitionStart(String partitionStart) {
        this.partitionStart = partitionStart;
    }

    public String getPartitionEnd() {
        return partitionEnd;
    }

    public void setPartitionEnd(String partitionEnd) {
        this.partitionEnd = partitionEnd;
    }
}
