# syntax=docker/dockerfile:1.7
# Build from the repo root:  docker build -f frontend/Dockerfile .

# ---- Build the Angular app ----
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ .
RUN npx ng build

# ---- Serve it with nginx; API calls are forwarded to the gateway ----
FROM nginx:1.27-alpine
ENV API_GATEWAY=api-gateway:8080
COPY frontend/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/shiptrack-web/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --start-interval=2s --retries=5 \
  CMD wget -qO- http://127.0.0.1/healthz || exit 1
