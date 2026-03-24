# Distributed URL Shortener

This repository is now organized as a simple monorepo:

1. `backend/`
   Spring Boot microservices for routing, shortening, resolving, recycling, and RAM-pool management
2. `frontend/`
   streamlined browser UI for create, resolve, and delete flows

## Structure

Backend services:

1. `backend/RouterService`
2. `backend/URL_Service1`
3. `backend/URL_Service2`
4. `backend/URL_Service3`
5. `backend/URL_Service4`

Frontend:

1. `frontend/index.html`
2. `frontend/styles.css`
3. `frontend/app.js`

## Documentation

Backend architecture and implementation details live in:

1. [documentation.md](/Users/megha_shah/Documents/Ren_Proj/Distributed%20Systems/backend/documentation.md)

## Ports

Current backend ports:

1. `RouterService` -> `8080`
2. `URL_Service1` -> `8081`
3. `URL_Service2` -> `8082`
4. `URL_Service3` -> `8083`
5. `URL_Service4` -> `8084`

## Frontend Direction

The frontend stays framework-free for now, but the UI has been trimmed down to the core actions so it is easier to test in the browser without extra dashboard noise.

## Running Everything

You do not need to start all backend services manually anymore.

Use:

1. `./start-backend.sh`
2. open `frontend/index.html` in Chrome
3. when done, run `./stop-backend.sh`
