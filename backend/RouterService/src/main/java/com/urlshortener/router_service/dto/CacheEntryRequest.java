package com.urlshortener.router_service.dto;

public class CacheEntryRequest {
    private String shortCode;
    private String originalUrl;

    public CacheEntryRequest() {
    }

    public CacheEntryRequest(String shortCode, String originalUrl) {
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
