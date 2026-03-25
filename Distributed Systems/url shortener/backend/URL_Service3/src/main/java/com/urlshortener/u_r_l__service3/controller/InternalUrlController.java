package com.urlshortener.u_r_l__service3.controller;

import com.urlshortener.u_r_l__service3.dto.CustomShortenRequest;
import com.urlshortener.u_r_l__service3.dto.DeleteResponse;
import com.urlshortener.u_r_l__service3.dto.ShortenRequest;
import com.urlshortener.u_r_l__service3.dto.ShortenResponse;
import com.urlshortener.u_r_l__service3.service.UrlServiceManager;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalUrlController {

    private final UrlServiceManager urlServiceManager;

    public InternalUrlController(UrlServiceManager urlServiceManager) {
        this.urlServiceManager = urlServiceManager;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@RequestBody ShortenRequest request) {
        ShortenResponse response = urlServiceManager.shorten(request.getOriginalUrl());
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @PostMapping("/shorten/custom")
    public ResponseEntity<ShortenResponse> shortenCustom(@RequestBody CustomShortenRequest request) {
        ShortenResponse response = urlServiceManager.shortenCustom(request.getOriginalUrl(), request.getCustomCode());
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @GetMapping("/resolve/{shortCode}")
    public ResponseEntity<Void> resolve(@PathVariable String shortCode) {
        Optional<String> original = urlServiceManager.resolve(shortCode);
        if (original.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(original.get()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<DeleteResponse> delete(@PathVariable String shortCode) {
        DeleteResponse response = urlServiceManager.deleteAndRecycle(shortCode);
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}
