package com.urlshortener.cache_service.controller;

import com.urlshortener.cache_service.dto.CacheEntryRequest;
import com.urlshortener.cache_service.dto.CacheEntryResponse;
import com.urlshortener.cache_service.service.LruCacheService;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/cache")
public class CacheController {

    private final LruCacheService lruCacheService;

    public CacheController(LruCacheService lruCacheService) {
        this.lruCacheService = lruCacheService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<CacheEntryResponse> get(@PathVariable String shortCode) {
        Optional<String> originalUrl = lruCacheService.get(shortCode);
        if (originalUrl.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new CacheEntryResponse(shortCode, originalUrl.get()));
    }

    @PutMapping
    public ResponseEntity<Void> put(@RequestBody CacheEntryRequest request) {
        if (request.getShortCode() == null || request.getShortCode().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getOriginalUrl() == null || request.getOriginalUrl().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        lruCacheService.put(request.getShortCode(), request.getOriginalUrl());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> delete(@PathVariable String shortCode) {
        lruCacheService.delete(shortCode);
        return ResponseEntity.noContent().build();
    }
}
