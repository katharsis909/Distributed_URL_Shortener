package com.urlshortener.router_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class RouterServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RouterServiceApplication.class, args);
    }
}
