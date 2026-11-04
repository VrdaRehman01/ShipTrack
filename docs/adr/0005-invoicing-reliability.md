# ADR 0005: Reliable invoicing without a message broker

**Date:** 2026-11-05 · **Status:** Accepted

## Context
FR-7 says an invoice must be created when a shipment is DELIVERED. The reliability NFR says a status update
must succeed even if billing is down. Messaging (SQS or Kafka) is out of scope for v1. The invoice currency was an
open question.

## Decision
- shipment-service marks the shipment DELIVERED and commits **first**. After the commit, a background
  (`@Async`) listener calls `POST /internal/invoices` on billing-service.
- The call goes through a **Resilience4j circuit breaker**. After repeated failures it opens and fails fast for
  30 s instead of piling up slow calls.
- The shipment keeps `invoiced = false` until billing confirms. A **scheduled job** (every 60 s) retries
  delivered shipments that are still uninvoiced. This works like a small outbox stored on the shipment row.
- billing-service is **idempotent on shipmentId** (a unique index). Retries, timeouts and duplicate calls can never
  bill twice.
- `/internal/**` is protected by a shared `INTERNAL_API_KEY` and is never routed by the gateway.
- **Currency: PKR only in v1.** Price = base fee + per-kg rate × weight (both configurable), rounded half-up to 2
  decimals. The API returns money as a decimal string plus a currency code.

## Consequences
- A driver's DELIVERED request never waits on billing and never fails because of it.
- An invoice may appear up to one retry interval after billing recovers (eventual consistency). Clients get
  404 "no invoice yet" in the meantime.
- Moving to SQS later is a small change: publish an event where the listener runs today.
- Supporting more currencies means adding a currency per shipment or customer, which is a v2 decision.
