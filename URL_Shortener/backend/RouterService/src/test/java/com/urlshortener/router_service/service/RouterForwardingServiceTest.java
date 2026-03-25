package com.urlshortener.router_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.urlshortener.router_service.config.RouterProperties;
import com.urlshortener.router_service.dto.CacheEntryRequest;
import com.urlshortener.router_service.dto.CacheEntryResponse;
import com.urlshortener.router_service.dto.DeleteResponse;
import com.urlshortener.router_service.dto.ShortenRequest;
import com.urlshortener.router_service.dto.ShortenResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

class RouterForwardingServiceTest {

    private RouterProperties routerProperties;
    private StubRestTemplate restTemplate;
    private RouterForwardingService routerForwardingService;

    @BeforeEach
    void setUp() {
        routerProperties = new RouterProperties();
        routerProperties.setNormalTargets(List.of(
                "http://localhost:8081",
                "http://localhost:8082",
                "http://localhost:8083",
                "http://localhost:8084"));
        routerProperties.setPartitionRanges(List.of("0-e", "f-t", "u-I", "J-Z"));
        routerProperties.setPartitionTargets(List.of(
                "http://localhost:8081",
                "http://localhost:8082",
                "http://localhost:8083",
                "http://localhost:8084"));

        restTemplate = new StubRestTemplate();
        routerForwardingService =
                new RouterForwardingService(routerProperties, restTemplate, "http://localhost:8085");
    }

    @Test
    void resolveReturnsCachedLocationWithoutCallingOwningService() {
        CacheEntryResponse cacheEntryResponse = new CacheEntryResponse();
        cacheEntryResponse.setShortCode("a00000");
        cacheEntryResponse.setOriginalUrl("https://chatgpt.com");
        restTemplate.cacheGetResponse = ResponseEntity.ok(cacheEntryResponse);

        ResponseEntity<Void> response = routerForwardingService.resolve("a00000");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create("https://chatgpt.com"), response.getHeaders().getLocation());
        assertNull(restTemplate.resolveExchangeUrl);
    }

    @Test
    void resolveFallsBackToOwningServiceAndBackfillsCacheOnMiss() {
        restTemplate.cacheGetException = HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("https://chatgpt.com"));
        restTemplate.resolveExchangeResponse = new ResponseEntity<>(headers, HttpStatus.FOUND);
        restTemplate.cachePutResponse = ResponseEntity.ok().build();

        ResponseEntity<Void> response = routerForwardingService.resolve("a00000");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create("https://chatgpt.com"), response.getHeaders().getLocation());
        assertEquals("http://localhost:8081/internal/resolve/{shortCode}", restTemplate.resolveExchangeUrl);
        assertNotNull(restTemplate.lastCachePutBody);
        assertInstanceOf(CacheEntryRequest.class, restTemplate.lastCachePutBody);
        CacheEntryRequest request = (CacheEntryRequest) restTemplate.lastCachePutBody;
        assertEquals("a00000", request.getShortCode());
        assertEquals("https://chatgpt.com", request.getOriginalUrl());
    }

    @Test
    void createWarmsCacheAfterSuccessfulCreate() {
        ShortenResponse shortenResponse = new ShortenResponse();
        shortenResponse.setSuccess(true);
        shortenResponse.setShortCode("f00000");
        shortenResponse.setMessage("Short URL created");
        restTemplate.postResponses.put("http://localhost:8083/internal/shorten", ResponseEntity.ok(shortenResponse));
        restTemplate.cachePutResponse = ResponseEntity.ok().build();

        ShortenRequest request = new ShortenRequest();
        request.setUserId(42L);
        request.setOriginalUrl("https://chatgpt.com");

        ResponseEntity<ShortenResponse> response = routerForwardingService.create(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("f00000", response.getBody().getShortCode());
        assertNotNull(restTemplate.lastCachePutBody);
        CacheEntryRequest cacheRequest = (CacheEntryRequest) restTemplate.lastCachePutBody;
        assertEquals("f00000", cacheRequest.getShortCode());
        assertEquals("https://chatgpt.com", cacheRequest.getOriginalUrl());
    }

    @Test
    void deleteInvalidatesCacheAfterSuccessfulDelete() {
        DeleteResponse deleteResponse = new DeleteResponse();
        deleteResponse.setSuccess(true);
        deleteResponse.setMessage("deleted");
        restTemplate.deleteResponses.put(
                "http://localhost:8081/internal/{shortCode}",
                ResponseEntity.ok(deleteResponse));
        restTemplate.cacheDeleteResponse = ResponseEntity.noContent().build();

        ResponseEntity<DeleteResponse> response = routerForwardingService.delete("a00000");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("http://localhost:8085/internal/cache/{shortCode}", restTemplate.cacheDeleteUrl);
        assertEquals("a00000", restTemplate.cacheDeleteShortCode);
    }

    @Test
    void deleteDoesNotInvalidateCacheWhenDeleteFails() {
        restTemplate.deleteException = HttpClientErrorException.NotFound.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);

        ResponseEntity<DeleteResponse> response = routerForwardingService.delete("a00000");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertNull(restTemplate.cacheDeleteUrl);
    }

    private static class StubRestTemplate extends RestTemplate {
        private ResponseEntity<CacheEntryResponse> cacheGetResponse;
        private HttpClientErrorException cacheGetException;
        private ResponseEntity<Void> resolveExchangeResponse;
        private ResponseEntity<Void> cachePutResponse;
        private ResponseEntity<Void> cacheDeleteResponse;
        private HttpClientErrorException deleteException;

        private final Map<String, ResponseEntity<ShortenResponse>> postResponses = new HashMap<>();
        private final Map<String, ResponseEntity<DeleteResponse>> deleteResponses = new HashMap<>();

        private Object lastCachePutBody;
        private String resolveExchangeUrl;
        private String cacheDeleteUrl;
        private String cacheDeleteShortCode;

        @Override
        public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            if (cacheGetException != null) {
                throw cacheGetException;
            }
            return cast(cacheGetResponse);
        }

        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
            return cast(postResponses.get(url));
        }

        @Override
        public <T> ResponseEntity<T> exchange(
                String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType, Object... uriVariables) {
            if (url.contains("/internal/resolve/")) {
                resolveExchangeUrl = url;
                return cast(resolveExchangeResponse);
            }
            if (url.endsWith("/internal/cache") && method == HttpMethod.PUT) {
                lastCachePutBody = requestEntity == null ? null : requestEntity.getBody();
                return cast(cachePutResponse);
            }
            if (url.contains("/internal/cache/") && method == HttpMethod.DELETE) {
                cacheDeleteUrl = url;
                cacheDeleteShortCode = uriVariables.length > 0 ? String.valueOf(uriVariables[0]) : null;
                return cast(cacheDeleteResponse);
            }
            if (method == HttpMethod.DELETE) {
                if (deleteException != null) {
                    throw deleteException;
                }
                return cast(deleteResponses.get(url));
            }
            throw new UnsupportedOperationException("Unexpected call: " + method + " " + url);
        }

        @SuppressWarnings("unchecked")
        private <T> ResponseEntity<T> cast(ResponseEntity<?> response) {
            return (ResponseEntity<T>) response;
        }
    }
}
