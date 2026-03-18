## Beginner-Friendly Plan: Distributed URL Shortener

### 1. Goal
Build 5 Spring Boot services that work together:
1. `RouterService` (receives client requests)
2. `URL_Service1`
3. `URL_Service2`
4. `URL_Service3`
5. `URL_Service4`

The system should support:
1. Create short URL
2. Create custom short URL
3. Redirect from short URL to original URL
4. Delete short URL and recycle code

---

### 2. Service Ports
Set fixed ports:

1. `RouterService` -> `8080`
2. `URL_Service1` -> `8081`
3. `URL_Service2` -> `8082`
4. `URL_Service3` -> `8083`
5. `URL_Service4` -> `8084`

---

### 3. What Each Service Does
1. **RouterService**
- Accepts all client requests
- Decides which URL service should handle each request
- Forwards request to that URL service

2. **URL_Service1..4**
- Own short-code partitions
- Store URL data in their own DB
- Manage RAM pool for fast code allocation
- Handle create, custom create, resolve, delete

---

### 4. Routing Rules (Simple)
1. **Normal create** (`customCode` not given):
- Router uses: `hash(userId) % 4`
- Sends request to one URL service

2. **Custom create / resolve / delete**:
- Router looks at first character of short code
- Uses fixed Base62 partitions
- Sends request to owner URL service

---

### 5. Database (Per URL Service)
Each URL service has its own disk-based H2 DB.

Create 2 tables:

1. `urls`
- `short_code` (unique)
- `original_url`

2. `available_url`
- `code` (unique)

---

### 6. RAM Pool (Per URL Service)
Use two in-memory structures:

1. `ConcurrentLinkedQueue<String>` (queue of candidate codes)
2. `ConcurrentHashMap<String, Boolean>` (availability map)

Important rule:
- Queue gives candidates
- HashMap is source of truth

---

### 7. Concurrency Rule You Requested
When checking if a code is available in RAM, do exactly 3 steps:

1. Check in hashmap
2. Delete from hashmap
3. Allocate code

During step 1 and step 2:
- lock the concurrent linked list (shared lock) so check+delete stays safe under concurrency.

---

### 8. API Endpoints
#### Router (external)
1. `POST /shorten`
   Inputs: `userId`, `originalUrl`, optional `customCode`
2. `GET /{shortCode}`
3. `DELETE /{shortCode}`

#### URL Services (internal)
1. `POST /internal/shorten`
2. `POST /internal/shorten/custom`
3. `GET /internal/resolve/{shortCode}`
4. `DELETE /internal/{shortCode}`

---

### 9. Main Flows
1. **Normal shorten flow**
- Router picks service using user hash
- URL service allocates from RAM
- Save mapping in `urls`
- Return short code JSON

2. **Custom shorten flow**
- Router picks owner service by short code first character
- URL service first tries RAM (with lock + check/delete/allocate)
- If RAM fails, do one transactional DB method:
1. check code in `available_url`
2. delete code from `available_url`
3. insert into `urls`
- If code not found in DB pool, return unavailable

3. **Resolve flow**
- Router picks owner service
- URL service reads `urls`
- Return HTTP 302 with `Location`

4. **Delete flow**
- Router picks owner service
- URL service deletes from `urls`
- Inserts code back into `available_url`
- Do not push deleted code directly into RAM

---

### 10. Refill and Seeding
1. **Refill**
- If RAM queue size drops below threshold (default 500), start async refill
- Refill method is `@Async` + `@Transactional`
- Fetch batch from `available_url`, delete same batch, put in RAM queue+map

2. **Startup seed**
- On startup, if `available_url` is empty, generate initial batch
- Code length comes from config property

---

### 11. Response Format
1. Create/Delete responses: JSON
2. Resolve response: HTTP `302` redirect

---

### 12. Testing Checklist (Beginner Order)
1. All 5 services start on correct ports
2. Router forwards normal create by user hash
3. Router forwards custom/resolve/delete by first character
4. Parallel requests do not allocate duplicate codes
5. Custom code works from RAM path
6. Custom code works from DB transactional fallback path
7. Same custom code requested concurrently: only one success
8. Resolve returns correct 302 redirect
9. Delete recycles only to DB pool
10. Refill and custom DB consume do not overlap incorrectly

---

### 13. Assumptions
1. This phase covers core flow only
2. No availability-check endpoint
3. No replication/failover in this phase
4. DB uniqueness + transaction rules are final safety layer

---

### 14. Commit Naming Rule
1. All trivial commits must be named exactly `trivial`.
2. All non-trivial commits must use a minimalistic name and include date, month, and year.
