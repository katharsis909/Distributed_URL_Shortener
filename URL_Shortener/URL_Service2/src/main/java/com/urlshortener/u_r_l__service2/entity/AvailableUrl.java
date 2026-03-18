package com.urlshortener.u_r_l__service2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "available_url")
public class AvailableUrl {
    @Id
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    public AvailableUrl() {
    }

    public AvailableUrl(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
