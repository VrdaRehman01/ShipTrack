# syntax=docker/dockerfile:1.7
# Build from the repo root:  docker build -f billing-service/Dockerfile .

# ---- Build stage: compile this service and the shared common module ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
# The cache mount keeps ~/.m2 between builds, so dependencies download only once.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl billing-service -am package -DskipTests -Djacoco.skip -Dcheckstyle.skip

# ---- Run stage: slim JRE 21, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S shiptrack && adduser -S shiptrack -G shiptrack
COPY --from=build /src/billing-service/target/billing-service-*.jar app.jar
USER shiptrack

EXPOSE 8083
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
