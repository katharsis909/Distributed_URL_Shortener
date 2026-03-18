package com.urlshortener.u_r_l__service4.service;

import com.urlshortener.u_r_l__service4.config.PoolProperties;
import com.urlshortener.u_r_l__service4.dto.DeleteResponse;
import com.urlshortener.u_r_l__service4.dto.ShortenResponse;
import com.urlshortener.u_r_l__service4.entity.AvailableUrl;
import com.urlshortener.u_r_l__service4.entity.UrlMapping;
import com.urlshortener.u_r_l__service4.repository.AvailableUrlRepository;
import com.urlshortener.u_r_l__service4.repository.UrlMappingRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlServiceManager {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final UrlMappingRepository urlMappingRepository;
    private final AvailableUrlRepository availableUrlRepository;
    private final PoolProperties poolProperties;
    private final UrlServiceManager selfProxy;
    //check below for doubt

    private final ConcurrentLinkedQueue<String> availableQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<String, Boolean> availableMap = new ConcurrentHashMap<>();
    private final ReentrantLock queueLock = new ReentrantLock();
    private final AtomicBoolean refillInProgress = new AtomicBoolean(false);

    public UrlServiceManager(
            UrlMappingRepository urlMappingRepository,
            AvailableUrlRepository availableUrlRepository,
            PoolProperties poolProperties,
            @Lazy UrlServiceManager selfProxy) {
        //lazy allows the springboot to use proxy design pattern to allow @transactional & @async
        this.urlMappingRepository = urlMappingRepository;
        this.availableUrlRepository = availableUrlRepository;
        this.poolProperties = poolProperties;
        this.selfProxy = selfProxy;
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
            //why enclosing in try block?
                //below is an exception of dataintegrity which is thrown by the primary key constraint of dbms
            urlMappingRepository.save(new UrlMapping(code, originalUrl));
            triggerAsyncRefillIfLow();
            //no other option at all, but i dont like this - that i check it every save time
            return new ShortenResponse(true, code, "Short URL created");
        } catch (DataIntegrityViolationException ex) {
            recycleCodeToAvailableDb(code);
            //purpose is that if the saving leads to exception but you have already allocated it from cache, now time to put it back to db
            return new ShortenResponse(false, null, "Code collision, retry request");
        }
    }

    public ShortenResponse shortenCustom(String originalUrl, String customCode) {
        if (originalUrl == null || originalUrl.isBlank()) {
            return new ShortenResponse(false, null, "originalUrl is required");
        }
        if (!isValidCode(customCode)) {
            //checks if customcode has valid characters & not random ascii codes
            return new ShortenResponse(false, null, "customCode is invalid");
        }

        if (removeFromAvailabilityMapWithQueueLock(customCode)) {
            //we are removing from hashmap
            //we also using a lock inside this metgod which also locks the cache of URLs in RAM
                //preventing it to be allocated until this method runs!
            return persistMapping(customCode, originalUrl);
            //saving it in db
                //for some reason there is n extra method for this operation because for some reason the sub-code is big
        }

        Optional<String> fromDb = selfProxy.consumeCustomCodeFromDb(customCode, originalUrl);
        if (fromDb.isPresent()) {
            return new ShortenResponse(true, customCode, "Custom short URL created");
        }

        return new ShortenResponse(false, null, "Requested custom code is unavailable");
    }

    public Optional<String> resolve(String shortCode) {
        return urlMappingRepository.findById(shortCode).map(UrlMapping::getOriginalUrl);
    }

    @Transactional
    //I made it transactional so as to delete & recycle both & avoid inconsitency!
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
            //I just do not think there is a chance of queue every getting close to 0 so what is the purpose?
            //Well it may happne thet rate of poll is more than loading!

            queueLock.lock();
            //we could have used synchronised method instead of lock, but there is a need of lock
            //It is so that when the user demands a custom url ,
            //      it may exist in ram & for that time, the url is in hashmap
//                        it must be removed from there & allocating a url should be temporarily stopped
//            What are we doing here is verifying that allocating random & custom shorten must not run at the same time!
//            You should not worry about time complexity because it would be rare to demand a custom url because of higher price!
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
            //finally ka use apt hai
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
            //no need of retrying using a loop, just check once & that is enough
            //why the redndant recursive flow ? ignore the future me!
            selfProxy.refillAsync();
        }
    }

    @Async
    public void refillAsync() {
        try {
            refillSynchronously();
        } finally {
            refillInProgress.set(false);
        }
    }

    private void refillSynchronously() {
        List<String> freshCodes = selfProxy.takeBatchFromAvailableDb(poolProperties.getRefillBatchSize());
        if (freshCodes.isEmpty()) {
            return;
            //Is it a case of all urls being issued?????????
            //why not try to handle it with a user defined exception
                //nope; notify users when they encounter the problem!
        }

        queueLock.lock();
        //I am locking because in the mean time better not concurrently demand for custom url while it is deleted from DB, but it is not even in hashmap
        //am i right? do we really need to care about this? isnt the probability too low? why not not use lock & just tell user the custom url is not available if its not present i both db & queue & in urlmapped table too
        try {
            for (String code : freshCodes) {
                availableQueue.offer(code);
                availableMap.put(code, Boolean.TRUE);
            }
        } finally {
            queueLock.unlock();
        }
    }

    @Transactional
    //Why?
    //because - first selct is done before delete
    //it means later when you use this select for adding it to cache, dont encounter inconsitency!
    public List<String> takeBatchFromAvailableDb(int batchSize) {
        List<String> candidates = availableUrlRepository.fetchBatch(batchSize);
        if (candidates.isEmpty()) {
            return candidates;
        }

        availableUrlRepository.deleteCodesInBatch(candidates);
        return new ArrayList<>(candidates);
    }

    @Transactional
    //needs to be transactional because what happens if power failure/exception occurs & you somehow delete it from db but not add it to urlmap
    //this will cause url to be permanantly deleted
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
            //put it back to db because we could not allocate it to user demand then & there
            return Optional.empty();
        }
    }

    private void recycleCodeToAvailableDb(String code) {
        availableUrlRepository.save(new AvailableUrl(code));
    }

    private void seedInitialCodes() {
        //i mean server should not start for actual while this function is in progress
        //This is handled by the post construct annotation
            //It means to not start using the beans until the post construct methods are done!
        int target = poolProperties.getSeedBatchSize();
        int length = Math.max(poolProperties.getCodeLength(), 2);
        List<Character> allowedFirstChars = allowedPartitionChars();

        List<AvailableUrl> generated = new ArrayList<>();
        long counter = 0L;
        //we are using the counter to generate url except first char

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
