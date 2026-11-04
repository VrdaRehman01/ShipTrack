# ADR 0003: Stateless HS256 JWTs with a shared secret

**Date:** 2026-11-05 · **Status:** Accepted

## Context
Every protected endpoint has to check who is calling (FR-2) and what role they have (FR-3). Calling auth-service
on every request would add latency and make it a single point of failure.

## Decision
- auth-service issues **HS256 JWTs** with `sub` = user id, plus `email`, `role`, `iss=shiptrack-auth`, and a **15-minute** expiry.
- Other services and the gateway verify tokens themselves using the same `JWT_SECRET`. It is stored in `.env`
  locally and in AWS Secrets Manager in the cloud.
- Public sign-up always creates a `CUSTOMER`. The first `ADMIN` comes from `ADMIN_EMAIL`/`ADMIN_PASSWORD` at
  startup. Admins create drivers through `POST /api/v1/users`.

## Consequences
- Checking a token needs no network call, so it's fast.
- Anyone holding the secret can mint tokens, so the secret must never reach Git or the logs.
- Tokens can't be revoked before they expire. The short 15-minute expiry limits the damage. Refresh tokens come in v2.
- If we need stronger isolation, the upgrade path is RS256: auth-service keeps the private key and the others get only the public key.
