package com.urlshortener.u_r_l__service2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class UrlService2Application {
    public static void main(String[] args) {
        SpringApplication.run(UrlService2Application.class, args);
    }
}
