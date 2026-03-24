package com.urlshortener.u_r_l__service2.service;

import com.urlshortener.u_r_l__service2.entity.AvailableUrl;
import com.urlshortener.u_r_l__service2.entity.UrlMapping;
import com.urlshortener.u_r_l__service2.repository.AvailableUrlRepository;
import com.urlshortener.u_r_l__service2.repository.UrlMappingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlPoolDbService {
    private final AvailableUrlRepository availableUrlRepository;
    private final UrlMappingRepository urlMappingRepository;

    public UrlPoolDbService(
            AvailableUrlRepository availableUrlRepository,
            UrlMappingRepository urlMappingRepository) {
        this.availableUrlRepository = availableUrlRepository;
        this.urlMappingRepository = urlMappingRepository;
    }

    @Transactional
    public List<String> takeBatchFromAvailableDb(int batchSize) {
        List<String> candidates = availableUrlRepository.fetchBatch(batchSize);
        if (candidates.isEmpty()) {
            return candidates;
        }

        // The read and delete stay in one transaction so refill only returns codes it really claimed.
        availableUrlRepository.deleteCodesInBatch(candidates);
        return new ArrayList<>(candidates);
    }

    @Transactional
    public Optional<String> consumeCustomCodeFromDb(String customCode, String originalUrl) {
        if (!availableUrlRepository.existsById(customCode)) {
            return Optional.empty();
        }

        int deleted = availableUrlRepository.deleteCodeIfPresent(customCode);
        if (deleted != 1) {
            return Optional.empty();
        }

        try {
            urlMappingRepository.save(new UrlMapping(customCode, originalUrl));
            return Optional.of(customCode);
        } catch (DataIntegrityViolationException ex) {
            availableUrlRepository.save(new AvailableUrl(customCode));
            return Optional.empty();
        }
    }
}
