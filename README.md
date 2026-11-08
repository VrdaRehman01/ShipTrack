# ShipTrack

A logistics backend: customers create shipments, drivers move them through delivery and post live locations,
and the system bills each delivered shipment automatically.
It's built as Java 21 microservices (Spring Boot 4 + Micronaut 4) behind one API gateway.

```
Browser ──► Website (nginx) :4200 ──► API Gateway :8080 ──┬─► auth-service :8081 ...   (/api is forwarded)

Client ──► API Gateway :8080 ──┬─► auth-service      :8081 ─► auth_db      users, roles, JWT
 (JWT check at the edge,       ├─► shipment-service  :8082 ─► shipment_db  lifecycle, drivers, history
  correlation ID, CORS,        │        │ on DELIVERED (async, circuit breaker, retried)
  one Swagger UI)              │        ▼
                               ├─► billing-service   :8083 ─► billing_db   invoices, pricing
                               └─► tracking-service  :8084 ─► tracking_db  driver locations (Micronaut)
```

## Setup in 5 steps

You need **Docker Desktop**. To run services from your IDE you also need **JDK 21+** (Maven comes with `./mvnw`).

1. **Clone the repo**
   ```bash
   git clone https://github.com/VrdaRehman01/ShipTrack.git && cd ShipTrack
   ```
2. **Create `.env`.** Copy the example, then change every password and secret:
   ```bash
   cp .env.example .env
   ```
3. **Start everything** (the first build takes a few minutes)
   ```bash
   docker compose up -d --build --wait
   ```
4. **Open the website** at http://localhost:4200. Track a parcel, create an account, or log in as the admin
   from `.env`. Swagger for the API is at http://localhost:8080/swagger-ui.html; use the dropdown in the top right to switch services.
5. **Run the full flow.** Import `postman/ShipTrack.postman_collection.json` into Postman and run it top to bottom,
   or use Newman:
   ```bash
   docker run --rm --network host -v "$PWD/postman:/etc/newman" postman/newman:6-alpine \
     run ShipTrack.postman_collection.json --env-var adminPassword=<ADMIN_PASSWORD from .env>
   ```

To stop everything, run `docker compose down`. Add `-v` to also delete the database.

## What it does

| Requirement | How |
|---|---|
| FR-1/2 Register, login, JWT | BCrypt passwords; HS256 JWT, 15-minute expiry; every service verifies the token itself |
| FR-3 Roles | CUSTOMER, DRIVER, ADMIN. Public sign-up creates customers only; admins create drivers; the first admin comes from `.env` |
| FR-4 Shipments | Create, list (paginated, newest first), view; tracking codes like `ST-2026-000042` |
| FR-5 Lifecycle | `CREATED → PICKED_UP → IN_TRANSIT → DELIVERED`, no skipping (400 `INVALID_STATUS_TRANSITION`). Customers may cancel before pickup |
| FR-6 Privacy | Owner, assigned driver or admin only; anyone else gets 403. Applies to shipments, locations and invoices |
| FR-7 Invoices | Created automatically on delivery. Billing being down never fails a delivery (circuit breaker + retry job) |
| FR-8 Tracking | Assigned driver posts locations while the parcel is moving; owner and admin read the latest location or the route |
| FR-9 Gateway | All traffic goes through `:8080`; `/internal/**` is never exposed |
| FR-10 Idempotency | `Idempotency-Key` header: retrying the same request returns the first shipment; reusing a key for a different body gives 422 |
| Extras | Status history timeline, public tracking page with no personal data, optimistic locking (409 on concurrent edits), admin user list |

## API (through the gateway)

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Create a customer account |
| POST | `/api/v1/auth/login` | Public | Get a JWT |
| GET | `/api/v1/users/me` | Anyone logged in | Who am I |
| GET/POST | `/api/v1/users` | Admin | List users (`?role=DRIVER`), create drivers and admins |
| POST | `/api/v1/shipments` | Customer | Create (send `Idempotency-Key`) |
| GET | `/api/v1/shipments` | Anyone logged in | Customers: own; drivers: assigned; admins: all. `?status=&page=&size=` |
| GET | `/api/v1/shipments/{id}` | Owner, driver, admin | Details |
| GET | `/api/v1/shipments/{id}/history` | Owner, driver, admin | Status timeline |
| PATCH | `/api/v1/shipments/{id}/status` | Driver, admin (customer: cancel) | Next status |
| PATCH | `/api/v1/shipments/{id}/driver` | Admin | Assign driver |
| GET | `/api/v1/public/shipments/{trackingCode}` | Public | Status and timeline only |
| POST | `/api/v1/tracking/{shipmentId}/locations` | Assigned driver | Post location |
| GET | `/api/v1/tracking/{shipmentId}/latest` | Owner, driver, admin | Latest location |
| GET | `/api/v1/tracking/{shipmentId}/locations` | Owner, driver, admin | Route, newest first |
| GET | `/api/v1/invoices?shipmentId=` | Owner, admin | Invoice for a shipment |
| GET | `/api/v1/invoices` | Customer (own), admin | List invoices |
| POST | `/api/v1/invoices/{id}/payments` | Admin | Mark paid |

Every error has the same shape and carries the request's correlation ID:
```json
{ "timestamp": "2026-11-04T09:30:00Z", "status": 400, "error": "INVALID_STATUS_TRANSITION",
  "message": "Cannot move shipment from CREATED to DELIVERED (next allowed: CANCELLED, PICKED_UP)",
  "path": "/api/v1/shipments/42/status", "correlationId": "a1b2c3d4" }
```

## Development

```bash
./mvnw verify                          # all modules: unit + Testcontainers tests, Checkstyle, 70% coverage gate
./mvnw verify -pl shipment-service -am # one service (plus the common module it needs)
docker compose up -d mysql             # just the database, then run a service from the IDE
```
The Spring services read the repo-root `.env` automatically. To run tracking-service from the IDE, export the
`.env` variables first.

Coverage reports are written to `<module>/target/site/jacoco/index.html`.
CI (`.github/workflows/ci.yml`) runs the same build, then starts the whole stack and runs the Postman flow on every push and PR.

## Performance

The success metric is **p95 < 300 ms with 100 concurrent users** (local). Measured with `k6/load-test.js`:
**p95 = 157 ms, 0 errors in 25,132 requests, about 260 requests/s.** Each of the 100 users creates, lists, reads and publicly
tracks shipments through the gateway.
```bash
docker run --rm -i --network shiptrack_default -v "$PWD/k6:/scripts" grafana/k6:1.3.0   run /scripts/load-test.js -e BASE_URL=http://api-gateway:8080
```
Run k6 inside the Compose network (as above). Going through `localhost` on Windows/macOS adds Docker Desktop's
port proxy, which becomes the bottleneck and gives misleading numbers.

## Deploying to AWS

One EC2 instance with the same Compose stack. Images go to ECR, secrets to Secrets Manager, logs to CloudWatch, and a
$5 budget alarm is included. Every merge to `main` deploys automatically. One-time setup (about 15 minutes, browser only):
**[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

## Repo layout
```
├── common/            # shared: error shape, JWT verification, correlation IDs, paging, Swagger (auto-configured)
├── auth-service/      # Spring Boot 4
├── shipment-service/  # Spring Boot 4 + Resilience4j
├── billing-service/   # Spring Boot 4
├── tracking-service/  # Micronaut 4
├── api-gateway/       # Spring Cloud Gateway (WebMVC)
├── frontend/          # Angular 21 website (customers, drivers, admins, public tracking)
├── config/            # Checkstyle rules
├── docker/mysql/init/ # creates one database per service
├── docs/              # ADRs, deployment and troubleshooting guides
├── infra/aws/         # CloudFormation template + production Compose file for the EC2 demo
├── scripts/           # deploy-on-host.sh, run on EC2 by the deploy workflow
├── k6/                # load test behind the p95 numbers above
├── postman/           # end-to-end collection (generated by build_collection.py)
├── .github/workflows/ # ci.yml (build, test, Postman flow) and deploy.yml (merge to main → AWS)
└── docker-compose.yml
```

## Docs
- Architecture decisions: [docs/adr](docs/adr)
- Something broke? See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
