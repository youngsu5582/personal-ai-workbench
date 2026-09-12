# Personal AI Workbench Initial T2I Vertical Slice Implementation Plan

> **For Hermes:** Use strict TDD and verify every slice with the Dockerized Gradle toolchain.

**Goal:** Build a usable T2I generation flow with user ownership, an async Job, one Provider adapter, a Worker, result storage, and Job status lookup.

**Architecture:** The public API creates an internal `GenerationJob` and returns immediately. The Worker submits the job to one Provider adapter and tracks completion asynchronously. `User` ownership is resolved from the authenticated principal (or `default-user` in P0), never from a client-controlled `owner_user_id` field.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, PostgreSQL, Docker Compose, Gradle.

---

## Phase 1 — Ownership and Job persistence

1. Add a pure owner-resolution test.
2. Add `AuthenticatedPrincipal` and P0 `default-user` resolution.
3. Add `User` entity/repository and seed `default-user`.
4. Add `GenerationJob` entity/repository with `owner_user_id`.
5. Add ownership-scoped Job query tests.

## Phase 2 — Job creation API

1. Write request/response and ownership tests.
2. Add `POST /generation-jobs` with `202 Accepted`.
3. Add idempotency behavior.
4. Add `GET /generation-jobs/{jobId}` with owner scope.

## Phase 3 — Provider adapter and Worker

1. Define the Provider port and mock Provider.
2. Add Worker claim and async submission.
3. Add Provider polling with bounded backoff.
4. Add result download and private local storage.
5. Add retryable failure states.

## Phase 4 — Operational verification

1. Add Docker Compose for API and PostgreSQL.
2. Add a mock-provider end-to-end smoke test.
3. Run the service and exercise create → process → query.
4. Record initial runtime output and known limitations.

## Deferred target architecture

- Provider Webhook primary with polling reconciliation where supported.
- SSE for browser status updates.
- I2I asset lineage.
- Usage and cost aggregation.
- MCP read tools, then cost-bearing write tools.
- Gradle multi-module and Kubernetes.
