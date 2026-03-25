package com.urlshortener.router_service.service;

import com.urlshortener.router_service.config.RouterProperties;
import com.urlshortener.router_service.dto.CacheEntryRequest;
import com.urlshortener.router_service.dto.CacheEntryResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
public class RouterForwardingService {

    private final RouterProperties routerProperties;
    private final RestTemplate restTemplate;
    private final String cacheServiceUrl;

    public RouterForwardingService(
            RouterProperties routerProperties,
            RestTemplate restTemplate,
            @Value("${cache.service-url}") String cacheServiceUrl) {
        this.routerProperties = routerProperties;
        this.restTemplate = restTemplate;
        this.cacheServiceUrl = cacheServiceUrl;
    }

    public ResponseEntity<ShortenResponse> create(ShortenRequest request) {
        String target;

        if (request.getCustomCode() != null && !request.getCustomCode().isBlank()) {
            target = routerProperties.routeForCode(request.getCustomCode());
            InternalCustomShortenRequest customRequest = new InternalCustomShortenRequest(
                    request.getOriginalUrl(), request.getCustomCode());
            return cacheOnCreate(
                    forwardPost(target + "/internal/shorten/custom", customRequest),
                    request.getOriginalUrl());
        }

        target = routerProperties.routeForUser(request.getUserId());
        InternalShortenRequest normalRequest = new InternalShortenRequest(request.getOriginalUrl());
        return cacheOnCreate(
                forwardPost(target + "/internal/shorten", normalRequest),
                request.getOriginalUrl());
    }

    public ResponseEntity<Void> resolve(String shortCode) {
        ResponseEntity<Void> cachedResponse = resolveFromCache(shortCode);
        if (cachedResponse != null) {
            return cachedResponse;
        }

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
                cachePut(shortCode, location.toString());
            }
            return new ResponseEntity<>(headers, response.getStatusCode());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        }
    }

    public ResponseEntity<DeleteResponse> delete(String shortCode) {
        String target = routerProperties.routeForCode(shortCode);
        try {
            ResponseEntity<DeleteResponse> response = restTemplate.exchange(
                    target + "/internal/{shortCode}",
                    HttpMethod.DELETE,
                    null,
                    DeleteResponse.class,
                    shortCode);
            if (response.getStatusCode().is2xxSuccessful()
                    && response.getBody() != null
                    && response.getBody().isSuccess()) {
                cacheDelete(shortCode);
            }
            return response;
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

    private ResponseEntity<ShortenResponse> cacheOnCreate(
            ResponseEntity<ShortenResponse> response, String originalUrl) {
        if (response.getStatusCode().is2xxSuccessful()
                && response.getBody() != null
                && response.getBody().isSuccess()
                && response.getBody().getShortCode() != null
                && originalUrl != null
                && !originalUrl.isBlank()) {
            cachePut(response.getBody().getShortCode(), originalUrl);
        }
        return response;
    }

    private ResponseEntity<Void> resolveFromCache(String shortCode) {
        try {
            ResponseEntity<CacheEntryResponse> response = restTemplate.getForEntity(
                    cacheServiceUrl + "/internal/cache/{shortCode}",
                    CacheEntryResponse.class,
                    shortCode);

            CacheEntryResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful()
                    || body == null
                    || body.getOriginalUrl() == null
                    || body.getOriginalUrl().isBlank()) {
                return null;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(body.getOriginalUrl()));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (HttpStatusCodeException ex) {
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void cachePut(String shortCode, String originalUrl) {
        try {
            CacheEntryRequest request = new CacheEntryRequest(shortCode, originalUrl);
            restTemplate.exchange(
                    cacheServiceUrl + "/internal/cache",
                    HttpMethod.PUT,
                    new HttpEntity<>(request),
                    Void.class);
        } catch (RuntimeException ex) {
            // Cache failures must not block the main request path.
        }
    }

    private void cacheDelete(String shortCode) {
        try {
            restTemplate.exchange(
                    cacheServiceUrl + "/internal/cache/{shortCode}",
                    HttpMethod.DELETE,
                    null,
                    Void.class,
                    shortCode);
        } catch (RuntimeException ex) {
            // Cache failures must not block the main request path.
        }
    }
}
