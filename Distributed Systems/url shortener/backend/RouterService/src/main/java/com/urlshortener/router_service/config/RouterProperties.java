package com.urlshortener.router_service.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "router")
public class RouterProperties {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private List<String> normalTargets = new ArrayList<>();
    private List<String> partitionRanges = new ArrayList<>();
    private List<String> partitionTargets = new ArrayList<>();

    @PostConstruct
    public void validate() {
        if (normalTargets.size() != 4) {
            throw new IllegalArgumentException("router.normal-targets must have exactly 4 values");
        }
        if (partitionRanges.size() != 4 || partitionTargets.size() != 4) {
            throw new IllegalArgumentException("router.partition-ranges and router.partition-targets must each have exactly 4 values");
        }
    }

    public String routeForUser(Long userId) {
        int idx = Math.floorMod(userId == null ? 0 : userId.hashCode(), normalTargets.size());
        return normalTargets.get(idx);
    }

    public String routeForCode(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new IllegalArgumentException("shortCode is required");
        }

        char first = shortCode.charAt(0);
        int codeIdx = BASE62.indexOf(first);
        if (codeIdx < 0) {
            throw new IllegalArgumentException("shortCode contains non-Base62 characters");
        }

        for (int i = 0; i < partitionRanges.size(); i++) {
            String[] bounds = partitionRanges.get(i).split("-");
            if (bounds.length != 2) {
                continue;
            }
            int start = BASE62.indexOf(bounds[0].charAt(0));
            int end = BASE62.indexOf(bounds[1].charAt(0));
            if (start <= codeIdx && codeIdx <= end) {
                return partitionTargets.get(i);
            }
        }

        throw new IllegalArgumentException("No partition mapping found for code");
    }

    public List<String> getNormalTargets() {
        return normalTargets;
    }

    public void setNormalTargets(List<String> normalTargets) {
        this.normalTargets = normalTargets;
    }

    public List<String> getPartitionRanges() {
        return partitionRanges;
    }

    public void setPartitionRanges(List<String> partitionRanges) {
        this.partitionRanges = partitionRanges;
    }

    public List<String> getPartitionTargets() {
        return partitionTargets;
    }

    public void setPartitionTargets(List<String> partitionTargets) {
        this.partitionTargets = partitionTargets;
    }
}
