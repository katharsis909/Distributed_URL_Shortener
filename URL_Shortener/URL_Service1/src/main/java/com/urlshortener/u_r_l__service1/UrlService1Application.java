package com.urlshortener.u_r_l__service1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class UrlService1Application {
    public static void main(String[] args) {
        SpringApplication.run(UrlService1Application.class, args);
    }
}
