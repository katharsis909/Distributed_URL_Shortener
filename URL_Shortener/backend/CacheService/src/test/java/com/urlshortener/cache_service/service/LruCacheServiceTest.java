package com.urlshortener.cache_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.urlshortener.cache_service.config.CacheProperties;
import org.junit.jupiter.api.Test;

class LruCacheServiceTest {

    @Test
    void getReturnsStoredValue() {
        LruCacheService cacheService = newCacheService(2);

        cacheService.put("a00000", "https://chatgpt.com");

        assertEquals("https://chatgpt.com", cacheService.get("a00000").orElseThrow());
    }

    @Test
    void leastRecentlyUsedEntryIsEvictedWhenCapacityIsExceeded() {
        LruCacheService cacheService = newCacheService(2);

        cacheService.put("a00000", "https://one.example");
        cacheService.put("b00000", "https://two.example");
        cacheService.put("c00000", "https://three.example");

        assertFalse(cacheService.get("a00000").isPresent());
        assertTrue(cacheService.get("b00000").isPresent());
        assertTrue(cacheService.get("c00000").isPresent());
    }

    @Test
    void readMarksEntryAsRecentlyUsed() {
        LruCacheService cacheService = newCacheService(2);

        cacheService.put("a00000", "https://one.example");
        cacheService.put("b00000", "https://two.example");
        cacheService.get("a00000");
        cacheService.put("c00000", "https://three.example");

        assertTrue(cacheService.get("a00000").isPresent());
        assertFalse(cacheService.get("b00000").isPresent());
        assertTrue(cacheService.get("c00000").isPresent());
    }

    @Test
    void deleteRemovesEntry() {
        LruCacheService cacheService = newCacheService(2);

        cacheService.put("a00000", "https://chatgpt.com");
        cacheService.delete("a00000");

        assertFalse(cacheService.get("a00000").isPresent());
    }

    private LruCacheService newCacheService(int capacity) {
        CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setCapacity(capacity);

        LruCacheService cacheService = new LruCacheService(cacheProperties);
        cacheService.initialize();
        return cacheService;
    }
}
