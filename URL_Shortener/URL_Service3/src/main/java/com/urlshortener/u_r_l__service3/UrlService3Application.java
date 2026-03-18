package com.urlshortener.u_r_l__service3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class UrlService3Application {
    public static void main(String[] args) {
        SpringApplication.run(UrlService3Application.class, args);
    }
}
