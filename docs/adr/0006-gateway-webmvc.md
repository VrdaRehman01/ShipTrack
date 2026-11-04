# ADR 0006: Servlet-based Spring Cloud Gateway with a shared common module

**Date:** 2026-11-05 · **Status:** Accepted

## Context
FR-9 needs a single entry point that checks JWTs, adds correlation IDs and routes to the services. Spring Cloud
Gateway comes in two flavours: reactive (WebFlux) and servlet (WebMVC). Every other Spring service already uses
`shiptrack-common`, which is servlet-based, for JWT checks, error handling and correlation IDs.

## Decision
- Use **spring-cloud-starter-gateway-server-webmvc** so the gateway reuses `shiptrack-common`. That gives it the
  same JWT verifier, the same error shape and the same correlation-ID filter. Virtual threads keep it efficient under load.
- The gateway rejects missing or invalid tokens at the edge, and the services check the token again (defence in depth).
- `/internal/**` has no route. CORS is configured once, at the gateway.
- The gateway's Swagger UI combines every service's OpenAPI spec, proxied under `/docs/*`.
- Spring Cloud **2025.1.x** is certified for Boot 4.0 and we run Boot 4.1. We turn off the compatibility check
  (`spring.cloud.compatibility-verifier.enabled=false`) and cover the gateway with integration tests. When Spring Cloud
  2026.0 is released, upgrade and turn the check back on.

## Consequences
- A single place to change routing, CORS and edge security.
- Running with the compatibility check off means a Spring Cloud upgrade must be re-tested with `ApiGatewayTest`
  before it is merged.
