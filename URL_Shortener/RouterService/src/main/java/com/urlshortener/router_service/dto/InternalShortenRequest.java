package com.urlshortener.router_service.dto;

public class InternalShortenRequest {
    private String originalUrl;

    public InternalShortenRequest() {
    }

    public InternalShortenRequest(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }
}
