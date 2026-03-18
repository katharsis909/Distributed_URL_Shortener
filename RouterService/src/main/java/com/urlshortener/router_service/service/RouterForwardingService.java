package com.urlshortener.router_service.service;

import com.urlshortener.router_service.config.RouterProperties;
import com.urlshortener.router_service.dto.DeleteResponse;
import com.urlshortener.router_service.dto.InternalCustomShortenRequest;
import com.urlshortener.router_service.dto.InternalShortenRequest;
import com.urlshortener.router_service.dto.ShortenRequest;
import com.urlshortener.router_service.dto.ShortenResponse;
import java.net.URI;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class RouterForwardingService {

    private final RouterProperties routerProperties;
    private final RestTemplate restTemplate;

    public RouterForwardingService(RouterProperties routerProperties, RestTemplate restTemplate) {
        this.routerProperties = routerProperties;
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<ShortenResponse> create(ShortenRequest request) {
        String target;

        if (request.getCustomCode() != null && !request.getCustomCode().isBlank()) {
            target = routerProperties.routeForCode(request.getCustomCode());
            InternalCustomShortenRequest customRequest = new InternalCustomShortenRequest(
                    request.getOriginalUrl(), request.getCustomCode());
            return forwardPost(target + "/internal/shorten/custom", customRequest);
        }

        target = routerProperties.routeForUser(request.getUserId());
        InternalShortenRequest normalRequest = new InternalShortenRequest(request.getOriginalUrl());
        return forwardPost(target + "/internal/shorten", normalRequest);
    }

    public ResponseEntity<Void> resolve(String shortCode) {
        String target = routerProperties.routeForCode(shortCode);
        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    target + "/internal/resolve/{shortCode}",
                    HttpMethod.GET,
                    null,
                    Void.class,
                    shortCode);

            HttpHeaders headers = new HttpHeaders();
            URI location = response.getHeaders().getLocation();
            if (location != null) {
                headers.setLocation(location);
            }
            return new ResponseEntity<>(headers, response.getStatusCode());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        }
    }

    public ResponseEntity<DeleteResponse> delete(String shortCode) {
        String target = routerProperties.routeForCode(shortCode);
        try {
            return restTemplate.exchange(
                    target + "/internal/{shortCode}",
                    HttpMethod.DELETE,
                    null,
                    DeleteResponse.class,
                    shortCode);
        } catch (HttpStatusCodeException ex) {
            DeleteResponse response = new DeleteResponse();
            response.setSuccess(false);
            response.setMessage("Delete failed");
            return ResponseEntity.status(ex.getStatusCode()).body(response);
        }
    }

    private ResponseEntity<ShortenResponse> forwardPost(String url, Object body) {
        try {
            return restTemplate.postForEntity(url, new HttpEntity<>(body), ShortenResponse.class);
        } catch (HttpStatusCodeException ex) {
            ShortenResponse response = new ShortenResponse();
            response.setSuccess(false);
            response.setMessage("Request failed");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }
}
