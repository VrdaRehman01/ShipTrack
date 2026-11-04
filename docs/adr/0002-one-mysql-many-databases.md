# ADR 0002: One MySQL server with a separate database per service

**Date:** 2026-11-05 · **Status:** Accepted (for local and demo; revisit for production)

## Context
Each service must own its data (database-per-service). We could run one MySQL container per service or one
MySQL server holding several databases. The machine is a laptop, and the AWS budget is $0–10.

## Decision
Run **one MySQL 8.4 container** with **separate databases**: `auth_db`, `shipment_db`, `billing_db` and
`tracking_db`. They are created by `docker/mysql/init/01-create-databases.sh`. Each service connects only to its own
database. Flyway manages each schema from inside that service.

## Consequences
- Low RAM use locally and one container to manage. This also maps to a single free-tier RDS instance.
- Services never join across databases. They call each other's APIs instead.
- A MySQL outage takes down every service at once. That's acceptable for a demo, but not for real production.
- Moving a service to its own server later only means changing its `DB_HOST`.
