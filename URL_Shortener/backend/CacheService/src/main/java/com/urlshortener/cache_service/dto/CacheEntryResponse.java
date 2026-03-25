package com.urlshortener.cache_service.dto;

public class CacheEntryResponse {
    private String shortCode;
    private String originalUrl;

    public CacheEntryResponse() {
    }

    public CacheEntryResponse(String shortCode, String originalUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }
}
