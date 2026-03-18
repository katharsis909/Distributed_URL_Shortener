package com.urlshortener.u_r_l__service4.dto;

public class ShortenResponse {
    private boolean success;
    private String shortCode;
    private String message;

    public ShortenResponse() {
    }

    public ShortenResponse(boolean success, String shortCode, String message) {
        this.success = success;
        this.shortCode = shortCode;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
