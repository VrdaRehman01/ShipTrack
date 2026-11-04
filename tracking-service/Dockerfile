# syntax=docker/dockerfile:1.7
# Build from the repo root:  docker build -f tracking-service/Dockerfile .

# ---- Build stage: Micronaut builds a single runnable (shaded) jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl tracking-service package -DskipTests -Djacoco.skip -Dcheckstyle.skip

# ---- Run stage: slim JRE 21, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S shiptrack && adduser -S shiptrack -G shiptrack
COPY --from=build /src/tracking-service/target/tracking-service-*.jar app.jar
USER shiptrack

EXPOSE 8084
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:8084/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
