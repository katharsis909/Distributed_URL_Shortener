package com.urlshortener.router_service.dto;

public class InternalCustomShortenRequest {
    private String originalUrl;
    private String customCode;

    public InternalCustomShortenRequest() {
    }

    public InternalCustomShortenRequest(String originalUrl, String customCode) {
        this.originalUrl = originalUrl;
        this.customCode = customCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getCustomCode() {
        return customCode;
    }

    public void setCustomCode(String customCode) {
        this.customCode = customCode;
    }
}
