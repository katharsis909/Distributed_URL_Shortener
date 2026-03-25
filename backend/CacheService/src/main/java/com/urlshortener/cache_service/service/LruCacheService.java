package com.urlshortener.cache_service.service;

import com.urlshortener.cache_service.config.CacheProperties;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LruCacheService {

    private final CacheProperties cacheProperties;
    private Map<String, String> cache;

    public LruCacheService(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    @PostConstruct
    public void initialize() {
        int capacity = Math.max(cacheProperties.getCapacity(), 1);
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized Optional<String> get(String shortCode) {
        return Optional.ofNullable(cache.get(shortCode));
    }

    public synchronized void put(String shortCode, String originalUrl) {
        cache.put(shortCode, originalUrl);
    }

    public synchronized void delete(String shortCode) {
        cache.remove(shortCode);
    }
}
