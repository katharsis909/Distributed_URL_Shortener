package com.urlshortener.u_r_l__service4.service;

import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UrlPoolAsyncService {
    private final UrlPoolDbService urlPoolDbService;

    public UrlPoolAsyncService(UrlPoolDbService urlPoolDbService) {
        this.urlPoolDbService = urlPoolDbService;
    }

    @Async
    public void refillAsync(UrlServiceManager manager, int batchSize) {
        try {
            List<String> freshCodes = urlPoolDbService.takeBatchFromAvailableDb(batchSize);
            if (!freshCodes.isEmpty()) {
                manager.addCodesToRam(freshCodes);
            }
        } finally {
            manager.finishRefill();
        }
    }
}
