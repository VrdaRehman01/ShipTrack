# ADR 0001: Use Spring Boot 4 instead of Spring Boot 3

**Date:** 2026-11-05 · **Status:** Accepted

## Context
The project plan says "Spring Boot 3". Spring Initializr now generates Spring Boot 4.x by default, and new
projects should start on the current major version. Boot 4 still runs on Java 21 and keeps the
programming model we need (Spring MVC, Spring Data JPA, Spring Security, Flyway).

## Decision
All Spring services use **Spring Boot 4.1.x** (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3).

## Consequences
- We stay on a supported line for longer and don't face a 3 → 4 migration later.
- Some test annotations moved to new packages (for example
  `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`), and older tutorials use the
  Boot 3 names.
- Every library has to support Boot 4. We use springdoc-openapi 3.x for Swagger. The Spring Cloud Gateway
  release train has to match when we add the gateway.
