# ADR 0004: Micronaut for tracking-service, with access checks delegated to shipment-service

**Date:** 2026-11-05 · **Status:** Accepted

## Context
Tracking is the write-heavy service: every driver posts a location every few seconds. The plan calls for a
second framework where fast startup and a small memory footprint matter. Tracking also needs FR-6
permissions: only the owner, the assigned driver or an admin may read a shipment's location.

## Decision
- tracking-service uses **Micronaut 4** with Micronaut Data JDBC (queries are checked at compile time), Flyway and
  micronaut-security-jwt. It verifies the same HS256 JWT as every other service.
- It does **not** copy the shipment permission rules. For each request it calls
  `GET /api/v1/shipments/{id}` on shipment-service **with the caller's own token**. A 200 response means the caller may proceed,
  and a 403 or 404 response is passed back to the client unchanged. Posting also requires that the caller is the assigned driver and that the shipment is
  PICKED_UP or IN_TRANSIT.
- It returns the same error shape and correlation IDs as the Spring services. Built-in Micronaut errors are
  rewritten by `StandardErrorResponseProcessor`.

## Consequences
- The permission rules live in one place, so the two services can't drift apart.
- Every tracking request costs one extra call to shipment-service. That is acceptable for v1. If it becomes a problem,
  cache "may user U see shipment S" for a few seconds.
- When shipment-service is down, tracking returns 503 rather than guessing.
- Micronaut 4.10 manages Testcontainers 1.x, which can't talk to Docker Engine 29+, so we pin Testcontainers 2.x
  in `tracking-service/pom.xml`.
