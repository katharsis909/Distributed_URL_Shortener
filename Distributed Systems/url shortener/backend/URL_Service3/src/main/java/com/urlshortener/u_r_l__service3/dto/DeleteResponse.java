package com.urlshortener.u_r_l__service3.dto;

public class DeleteResponse {
    private boolean success;
    private String message;

    public DeleteResponse() {
    }

    public DeleteResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
