package com.urlshortener.router_service.controller;

import com.urlshortener.router_service.dto.DeleteResponse;
import com.urlshortener.router_service.dto.ShortenRequest;
import com.urlshortener.router_service.dto.ShortenResponse;
import com.urlshortener.router_service.service.RouterForwardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RouterController {

    private final RouterForwardingService routerForwardingService;

    public RouterController(RouterForwardingService routerForwardingService) {
        this.routerForwardingService = routerForwardingService;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
        return routerForwardingService.create(request);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> resolve(@PathVariable String shortCode) {
        return routerForwardingService.resolve(shortCode);
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<DeleteResponse> delete(@PathVariable String shortCode) {
        return routerForwardingService.delete(shortCode);
    }
}
