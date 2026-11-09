# ADR 0008: Angular web app served by nginx on the same origin as the API

**Date:** 2026-11-06 · **Status:** Accepted

## Context
v1 was API-only. The plan's next step is an Angular frontend for the three roles: customers sending and following
parcels, drivers updating deliveries from their phones, and admins assigning drivers and recording payments.
It also needs a public tracking page.

## Decision
- **Angular 21**, standalone components and signals, lazy-loaded pages, Vitest unit tests. (Angular 22 requires
  Node 24.15+, and the team machine has 24.13, so we use 21, which is fully supported.)
- **Same origin:** an nginx container serves the app and reverse-proxies `/api`, Swagger and `/actuator/health` to
  the gateway. Browsers never make cross-origin calls, so CORS stays a fallback only. In production the website
  takes port 80 and the gateway is no longer exposed directly.
- **Security:** strict CSP (`script-src 'self'`, no inline scripts; Angular's inline critical-CSS loader is
  disabled for this), `X-Frame-Options: DENY`, and geolocation allowed only for this site. The JWT is kept in
  sessionStorage, so it is gone when the tab closes. It expires after 15 minutes without activity; while the app
  is in use it is renewed, for up to 8 hours after logging in.
- **Idempotency:** each "Send a parcel" form gets one `Idempotency-Key`, so double-clicks and retries never create
  duplicates (FR-10).
- **Drivers on phones:** the browser's geolocation posts the driver's location, once or every minute while the page is open.

## Consequences
- The frontend is one more image in ECR and one more container (about 64 MB RAM).
- The lifecycle rules are mirrored in `core/status.ts` so the UI only offers allowed moves. The backend still
  enforces them; the UI is only a convenience.
- Map tiles come from OpenStreetMap's public servers, which is fine for a demo. A busy production site would need
  its own tile provider.
