package com.urlshortener.u_r_l__service3.service;

import com.urlshortener.u_r_l__service3.config.PoolProperties;
import com.urlshortener.u_r_l__service3.dto.DeleteResponse;
import com.urlshortener.u_r_l__service3.dto.ShortenResponse;
import com.urlshortener.u_r_l__service3.entity.AvailableUrl;
import com.urlshortener.u_r_l__service3.entity.UrlMapping;
import com.urlshortener.u_r_l__service3.repository.AvailableUrlRepository;
import com.urlshortener.u_r_l__service3.repository.UrlMappingRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlServiceManager {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final UrlMappingRepository urlMappingRepository;
    private final AvailableUrlRepository availableUrlRepository;
    private final PoolProperties poolProperties;
    private final UrlPoolDbService urlPoolDbService;
    private final UrlPoolAsyncService urlPoolAsyncService;

    private final ConcurrentLinkedQueue<String> availableQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Boolean> availableMap = new ConcurrentHashMap<>();
    // This lock protects queue+map updates that must behave like one RAM-pool state change.
    private final ReentrantLock queueLock = new ReentrantLock();
    private final AtomicBoolean refillInProgress = new AtomicBoolean(false);

    public UrlServiceManager(
            UrlMappingRepository urlMappingRepository,
            AvailableUrlRepository availableUrlRepository,
            PoolProperties poolProperties,
            UrlPoolDbService urlPoolDbService,
            UrlPoolAsyncService urlPoolAsyncService) {
        this.urlMappingRepository = urlMappingRepository;
        this.availableUrlRepository = availableUrlRepository;
        this.poolProperties = poolProperties;
        this.urlPoolDbService = urlPoolDbService;
        this.urlPoolAsyncService = urlPoolAsyncService;
    }

    @PostConstruct
    public void initializePool() {
        if (availableUrlRepository.count() == 0) {
            seedInitialCodes();
        }
        refillSynchronously();
    }

    public ShortenResponse shorten(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            return new ShortenResponse(false, null, "originalUrl is required");
        }

        Optional<String> allocated = allocateFromRam();
        if (allocated.isEmpty()) {
            triggerAsyncRefill();
            return new ShortenResponse(false, null, "No short code available right now");
        }

        String code = allocated.get();
        try {
            urlMappingRepository.save(new UrlMapping(code, originalUrl));
            triggerAsyncRefillIfLow();
            return new ShortenResponse(true, code, "Short URL created");
        } catch (DataIntegrityViolationException ex) {
            recycleCodeToAvailableDb(code);
            return new ShortenResponse(false, null, "Code collision, retry request");
        }
    }

    public ShortenResponse shortenCustom(String originalUrl, String customCode) {
        if (originalUrl == null || originalUrl.isBlank()) {
            return new ShortenResponse(false, null, "originalUrl is required");
        }
        if (!isValidCode(customCode)) {
            return new ShortenResponse(false, null, "customCode is invalid");
        }

        if (removeFromAvailabilityMapWithQueueLock(customCode)) {
            return persistMapping(customCode, originalUrl);
        }

        Optional<String> fromDb = urlPoolDbService.consumeCustomCodeFromDb(customCode, originalUrl);
        if (fromDb.isPresent()) {
            return new ShortenResponse(true, customCode, "Custom short URL created");
        }

        return new ShortenResponse(false, null, "Requested custom code is unavailable");
    }

    public Optional<String> resolve(String shortCode) {
        return urlMappingRepository.findById(shortCode).map(UrlMapping::getOriginalUrl);
    }

    @Transactional
    public DeleteResponse deleteAndRecycle(String shortCode) {
        Optional<UrlMapping> existing = urlMappingRepository.findById(shortCode);
        if (existing.isEmpty()) {
            return new DeleteResponse(false, "Short code not found");
        }

        urlMappingRepository.delete(existing.get());
        availableUrlRepository.save(new AvailableUrl(shortCode));
        return new DeleteResponse(true, "Short code deleted and recycled");
    }

    private ShortenResponse persistMapping(String code, String originalUrl) {
        try {
            urlMappingRepository.save(new UrlMapping(code, originalUrl));
            return new ShortenResponse(true, code, "Short URL created");
        } catch (DataIntegrityViolationException ex) {
            recycleCodeToAvailableDb(code);
            return new ShortenResponse(false, null, "Requested custom code is unavailable");
        }
    }

    private Optional<String> allocateFromRam() {
        while (true) {
            String candidate = availableQueue.poll();
            if (candidate == null) {
                return Optional.empty();
            }

            queueLock.lock();
            try {
                if (availableMap.containsKey(candidate)) {
                    availableMap.remove(candidate);
                    return Optional.of(candidate);
                }
            } finally {
                queueLock.unlock();
            }
        }
    }

    private boolean removeFromAvailabilityMapWithQueueLock(String code) {
        queueLock.lock();
        try {
            if (availableMap.containsKey(code)) {
                availableMap.remove(code);
                return true;
            }
            return false;
        } finally {
            queueLock.unlock();
        }
    }

    private void triggerAsyncRefillIfLow() {
        if (availableQueue.size() < poolProperties.getLowWatermark()) {
            triggerAsyncRefill();
        }
    }

    private void triggerAsyncRefill() {
        if (refillInProgress.compareAndSet(false, true)) {
            urlPoolAsyncService.refillAsync(this, poolProperties.getRefillBatchSize());
        }
    }

    void refillSynchronously() {
        List<String> freshCodes = urlPoolDbService.takeBatchFromAvailableDb(poolProperties.getRefillBatchSize());
        if (freshCodes.isEmpty()) {
            return;
        }

        addCodesToRam(freshCodes);
    }

    void addCodesToRam(List<String> freshCodes) {
        queueLock.lock();
        try {
            for (String code : freshCodes) {
                availableQueue.offer(code);
                availableMap.put(code, Boolean.TRUE);
            }
        } finally {
            queueLock.unlock();
        }
    }

    void finishRefill() {
        refillInProgress.set(false);
    }

    private void recycleCodeToAvailableDb(String code) {
        availableUrlRepository.save(new AvailableUrl(code));
    }

    private void seedInitialCodes() {
        int target = poolProperties.getSeedBatchSize();
        int length = Math.max(poolProperties.getCodeLength(), 2);
        List<Character> allowedFirstChars = allowedPartitionChars();

        List<AvailableUrl> generated = new ArrayList<>();
        long counter = 0L;

        while (generated.size() < target) {
            for (Character first : allowedFirstChars) {
                if (generated.size() >= target) {
                    break;
                }
                String suffix = toBase62Padded(counter, length - 1);
                generated.add(new AvailableUrl(first + suffix));
            }
            counter++;
        }

        availableUrlRepository.saveAll(generated);
    }

    private List<Character> allowedPartitionChars() {
        String start = Objects.requireNonNull(poolProperties.getPartitionStart(), "partitionStart is required");
        String end = Objects.requireNonNull(poolProperties.getPartitionEnd(), "partitionEnd is required");

        int startIdx = BASE62.indexOf(start.charAt(0));
        int endIdx = BASE62.indexOf(end.charAt(0));
        if (startIdx < 0 || endIdx < 0 || startIdx > endIdx) {
            throw new IllegalArgumentException("Invalid partition range");
        }

        List<Character> chars = new ArrayList<>();
        for (int i = startIdx; i <= endIdx; i++) {
            chars.add(BASE62.charAt(i));
        }
        return chars;
    }

    private String toBase62Padded(long value, int length) {
        StringBuilder builder = new StringBuilder();
        long current = value;

        do {
            int idx = (int) (current % 62);
            builder.append(BASE62.charAt(idx));
            current = current / 62;
        } while (current > 0);

        while (builder.length() < length) {
            builder.append('0');
        }

        String encoded = builder.reverse().toString();
        if (encoded.length() > length) {
            return encoded.substring(encoded.length() - length);
        }
        return encoded;
    }

    private boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        for (char c : code.toCharArray()) {
            if (BASE62.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }
}
