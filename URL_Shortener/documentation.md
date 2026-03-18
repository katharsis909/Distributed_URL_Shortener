# Distributed URL Shortener Documentation

## 1. Project Overview

This project is a distributed URL shortener built as 5 Spring Boot services:

1. `RouterService`
2. `URL_Service1`
3. `URL_Service2`
4. `URL_Service3`
5. `URL_Service4`

The system supports:

1. normal short URL creation
2. custom short URL creation
3. redirect from short code to original URL
4. deletion and recycling of short codes

The main design goal is to keep URL allocation fast while avoiding collisions across multiple services.

## 1.1 Quick Summary

At a very high level:

1. `RouterService` receives client requests
2. normal shorten requests are routed by `hash(userId) % 4`
3. custom shorten, resolve, and delete are routed by first-character ownership
4. each URL service has its own DB and its own RAM pool
5. RAM is used for fast allocation, DB is used for correctness and persistence

This means the system stays simple to understand:

1. router decides where request goes
2. URL service does the real work
3. DB stores truth
4. RAM improves speed

## 2. High-Level Architecture

The request flow is:

```text
Client
  |
RouterService
  |
-----------------------------------------
|          |          |          |
URL S1     URL S2     URL S3     URL S4
```

Responsibilities:

1. `RouterService`
   Routes incoming client requests to the correct URL service.
2. `URL_Service1..4`
   Own URL partitions, manage local database state, manage in-memory available-code pool, and execute shorten/resolve/delete logic.
3. Per-service database
   Stores active mappings and available codes for that service only.
4. In-memory RAM pool
   Makes URL allocation fast without hitting the database for every request.

## 3. Service Layout

Current directories:

1. `/Users/megha_shah/Documents/Ren_Proj/Distributed Systems/URL_Shortener/RouterService`
2. `/Users/megha_shah/Documents/Ren_Proj/Distributed Systems/URL_Shortener/URL_Service1`
3. `/Users/megha_shah/Documents/Ren_Proj/Distributed Systems/URL_Shortener/URL_Service2`
4. `/Users/megha_shah/Documents/Ren_Proj/Distributed Systems/URL_Shortener/URL_Service3`
5. `/Users/megha_shah/Documents/Ren_Proj/Distributed Systems/URL_Shortener/URL_Service4`

Each service is an independent Maven Spring Boot project.

## 4. Ports

The services run on fixed ports:

1. `RouterService` -> `8080`
2. `URL_Service1` -> `8081`
3. `URL_Service2` -> `8082`
4. `URL_Service3` -> `8083`
5. `URL_Service4` -> `8084`

## 5. Routing Rules

### 5.1 Normal Shorten Request

If the user does not request a custom short code, the router computes:

```text
hash(userId) % 4
```

This decides which URL service handles the request.

Purpose:

1. same user goes to the same service
2. load is distributed
3. router does not need central coordination

### 5.2 Custom Shorten, Resolve, and Delete

For these operations, the router uses the first character of the short code.

The Base62 alphabet is:

```text
0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ
```

Partition ranges:

1. `URL_Service1` -> `0..e`
2. `URL_Service2` -> `f..t`
3. `URL_Service3` -> `u..I`
4. `URL_Service4` -> `J..Z`

This means the router can determine ownership directly from the first character.

## 6. External API

These are exposed by `RouterService`:

1. `POST /shorten`
   Body includes `userId`, `originalUrl`, and optional `customCode`
2. `GET /{shortCode}`
   Resolves and redirects
3. `DELETE /{shortCode}`
   Deletes the mapping and recycles the code

Response style:

1. create and delete return JSON
2. resolve returns HTTP `302`

## 7. Internal URL Service API

Each URL service exposes internal endpoints used by the router:

1. `POST /internal/shorten`
2. `POST /internal/shorten/custom`
3. `GET /internal/resolve/{shortCode}`
4. `DELETE /internal/{shortCode}`

These are not meant to be called directly by external clients.

## 8. Data Model

Each URL service has its own disk-based H2 database.

### 8.1 `urls` table

Stores active mappings:

```text
short_code -> original_url
```

Columns:

1. `short_code`
2. `original_url`

### 8.2 `available_url` table

Stores currently unused codes that can still be allocated.

Columns:

1. `code`

## 9. RAM Pool Design

Each URL service keeps two in-memory structures:

1. `ConcurrentLinkedQueue<String> availableQueue`
2. `ConcurrentHashMap<String, Boolean> availableMap`

Meaning:

1. queue gives allocation order
2. map is the truth of availability

The queue may still contain stale entries after a custom allocation removes a code from the map. That is acceptable because the allocator checks the map before issuing.

## 10. Concurrency Rule

The project uses a shared `ReentrantLock` called `queueLock`.

This lock is used because the system is not protecting only a queue or only a map. It is protecting a multi-step operation across both of them.

Required RAM allocation logic:

1. check code in hashmap
2. remove code from hashmap
3. allocate code

The lock ensures that random allocation, custom allocation, and refill do not interfere in the middle of those state transitions.

## 11. Normal Shorten Flow

Step-by-step:

1. Client sends `POST /shorten` to router
2. Router hashes `userId`
3. Router forwards request to the selected URL service
4. URL service polls candidate from RAM queue
5. URL service checks candidate in RAM map
6. If valid, URL service removes it from map and allocates it
7. URL service saves `short_code -> original_url` in `urls`
8. Response is returned as JSON

If RAM has no candidate available, the service returns failure for now and triggers refill.

Important implementation notes:

1. the DB save is wrapped in `try/catch` because the database can still reject the insert with an integrity violation
2. if that happens after the code was already removed from RAM, the code is recycled back into `available_url` so it is not lost
3. queue exhaustion is possible if requests consume RAM faster than refill loads it

## 12. Custom Shorten Flow

Step-by-step:

1. Client sends `POST /shorten` with `customCode`
2. Router examines the first character of `customCode`
3. Router forwards request to the owning URL service
4. URL service first checks RAM map under lock
5. If custom code is present in RAM map, it is removed and saved to `urls`
6. If not present in RAM, the service checks `available_url` in the database
7. Database path runs in one transaction:
   1. verify code exists in `available_url`
   2. delete it from `available_url`
   3. insert into `urls`
8. If that succeeds, the custom code is issued
9. If not found, request fails as unavailable

Current implementation choice:

1. custom flow does not trigger refill
2. refill is tied only to the normal RAM-issued path

## 13. Resolve Flow

Step-by-step:

1. Client requests `GET /{shortCode}` on router
2. Router uses first character to determine owner service
3. Router forwards request to `/internal/resolve/{shortCode}`
4. URL service looks up `short_code` in `urls`
5. If found, it returns HTTP `302` with `Location` header
6. Browser or client follows redirect

## 14. Delete Flow

Step-by-step:

1. Client calls `DELETE /{shortCode}` on router
2. Router routes to owning URL service
3. URL service finds mapping in `urls`
4. URL service deletes that mapping
5. URL service inserts the same code back into `available_url`

Current implementation choice:

1. deleted codes are recycled to database only
2. deleted codes are not pushed directly back into RAM
3. delete-and-recycle runs inside one transaction so the code is not lost between delete and recycle

## 15. Refill Flow

Purpose:

1. keep RAM pool stocked
2. avoid database hit for every normal shorten request

Rule:

1. if `availableQueue.size() < lowWatermark`, refill is triggered

Default low watermark:

1. `500`

Refill process:

1. fetch batch of codes from `available_url`
2. delete the same batch from `available_url`
3. push those codes into RAM queue and RAM map

Current implementation:

1. refill runs through `@Async`
2. database fetch/delete batch runs through `@Transactional`
3. bulk delete is used with `DELETE ... WHERE code IN (:codes)`

Why transaction matters here:

1. this method first reads a batch, then deletes that same batch
2. without one transaction, another flow could observe or claim overlapping data between those two steps
3. the transaction makes the batch claim behave like one logical ownership transfer from DB pool to RAM pool

Why refill uses the RAM lock while inserting:

1. refill is updating both queue and hashmap together
2. custom allocation and normal allocation also depend on those same two structures being in sync
3. the lock prevents a half-updated RAM state from being visible to another thread

What happens if the batch is empty:

1. refill simply stops
2. this usually means the service currently has no more available codes to load from its DB pool
3. the user-facing failure is handled later during allocation, not by throwing a refill-specific exception

## 16. Startup Seeding

On startup:

1. service checks whether `available_url` is empty
2. if empty, it generates an initial batch of codes
3. generated codes are saved in the service’s own database
4. then the first refill loads RAM from DB

Startup behavior:

1. this seeding work happens during bean initialization
2. the service does not begin normal work until this startup preparation finishes

Code generation uses:

1. first character from that service’s allowed partition
2. suffix generated in Base62
3. fixed total length from config

## 17. Why `selfProxy` Exists

This project uses Spring annotations like `@Async` and `@Transactional`.

Spring applies these through a proxy object, not by directly changing the method itself.

If a method inside the same class directly calls another annotated method, Spring may not apply the annotation behavior.

That is why the service injects:

1. `private final UrlServiceManager selfProxy;`

And calls:

1. `selfProxy.refillAsync()`
2. `selfProxy.takeBatchFromAvailableDb(...)`
3. `selfProxy.consumeCustomCodeFromDb(...)`

This ensures Spring proxy behavior is preserved.

## 18. Why `RestTemplate` Exists in Router

`RouterService` needs to call the internal endpoints of the URL services.

For that, it uses a `RestTemplate` bean.

Special behavior:

1. router disables automatic redirect following

Reason:

1. when URL service returns `302`
2. router should forward that `302` back to the client
3. router should not follow the redirect itself

## 19. Error Handling and Recovery

### 19.1 DB save failure after RAM allocation

If a code is removed from RAM but saving into `urls` fails:

1. the code is recycled back into `available_url`

This avoids permanently losing the code.

### 19.2 Custom DB allocation failure

If custom allocation deletes code from `available_url` but saving to `urls` fails:

1. the code is inserted back into `available_url`

### 19.3 Empty refill batch

If refill finds no codes in DB:

1. refill ends quietly
2. later requests may fail with “No short code available right now”

### 19.4 Custom DB claim race with refill

Two flows may target the same available code:

1. refill may fetch a batch that includes a code
2. a custom request may try to claim that exact code from DB

Correct behavior is:

1. whichever transaction successfully deletes the row first wins
2. the other flow treats the code as unavailable
3. this is why custom DB consume and refill batch claim both rely on transactional delete confirmation

## 20. Project Configuration

Each URL service has config values such as:

1. `server.port`
2. H2 datasource URL
3. `url.pool.low-watermark`
4. `url.pool.refill-batch-size`
5. `url.pool.seed-batch-size`
6. `url.pool.code-length`
7. `url.pool.partition-start`
8. `url.pool.partition-end`

Router config includes:

1. normal targets
2. partition ranges
3. partition targets

## 21. Important Implementation Decisions

Current code intentionally does the following:

1. normal shorten can trigger refill if RAM goes low
2. custom shorten does not trigger refill
3. delete recycles to DB only, not directly to RAM
4. queue plus map state changes are protected by explicit lock
5. per-service H2 DBs are file-based, not in-memory

## 22. Current Limitations

This project currently does not include:

1. replication
2. failover
3. consistent hashing
4. central observability
5. cache for hot redirects
6. production-grade load balancer in front of multiple routers

It is a solid distributed systems learning implementation, but not yet a production-hardened distributed platform.

## 23. Running the Project

General sequence:

1. start `URL_Service1`
2. start `URL_Service2`
3. start `URL_Service3`
4. start `URL_Service4`
5. start `RouterService`

Then call router endpoints on port `8080`.

## 24. Build and Tooling Notes

This project uses Maven-based Spring Boot modules.

In this environment, network restrictions may block Maven dependency download from Maven Central. If that happens, compilation and test execution cannot complete until network access is available.

## 25. Commit Naming Rule

Project rule:

1. trivial commits must be named exactly `trivial`
2. non-trivial commits must use a minimalistic name and include date, month, and year

## 26. Beginner Checklist

If you want to understand the project from scratch, read in this order:

1. this `documentation.md`
2. router config and forwarding flow
3. one URL service manager
4. one repository interface
5. one controller

If you want to verify features manually, check in this order:

1. all 5 services start on the configured ports
2. normal create routes by user hash
3. custom create routes by first character
4. resolve returns `302`
5. delete recycles code to DB
6. custom DB consume and refill do not corrupt the pool

## 27. File Guide

Useful starting points:

1. [RouterServiceApplication.java](/Users/megha_shah/Documents/Ren_Proj/Distributed%20Systems/URL_Shortener/RouterService/src/main/java/com/urlshortener/router_service/RouterServiceApplication.java)
2. [RouterForwardingService.java](/Users/megha_shah/Documents/Ren_Proj/Distributed%20Systems/URL_Shortener/RouterService/src/main/java/com/urlshortener/router_service/service/RouterForwardingService.java)
3. [UrlServiceManager.java](/Users/megha_shah/Documents/Ren_Proj/Distributed%20Systems/URL_Shortener/URL_Service1/src/main/java/com/urlshortener/u_r_l__service1/service/UrlServiceManager.java)
4. [AvailableUrlRepository.java](/Users/megha_shah/Documents/Ren_Proj/Distributed%20Systems/URL_Shortener/URL_Service1/src/main/java/com/urlshortener/u_r_l__service1/repository/AvailableUrlRepository.java)
5. [documentation.md](/Users/megha_shah/Documents/Ren_Proj/Distributed%20Systems/URL_Shortener/documentation.md)
