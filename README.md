# ShipTrack

A logistics backend where businesses create shipments, track them live and get invoices automatically.
It is built as Java 21 microservices (Spring Boot + Micronaut) behind one API gateway and deployed on AWS.

| Service | Port | Status |
|---|---|---|
| auth-service: users, roles, login, JWT | 8081 | ✅ Sprint 0 / Day 1 |
| shipment-service: shipments, status lifecycle | 8082 | ⏳ Day 2 |
| billing-service: invoices | 8083 | ⏳ after MVP |
| tracking-service (Micronaut): driver locations | 8084 | ⏳ after MVP |
| api-gateway: routing, JWT check, correlation ID | 8080 | ⏳ after MVP |

## Setup in 5 steps

You need **Docker Desktop**. To run services from an IDE, you also need **JDK 21+** (Maven comes with the `mvnw` wrapper).

1. **Clone the repo**
   ```bash
   git clone <repo-url> shiptrack && cd shiptrack
   ```
2. **Create your `.env`** from the example, then change the passwords and `JWT_SECRET` (at least 32 characters):
   ```bash
   cp .env.example .env
   ```
3. **Start everything**
   ```bash
   docker compose up -d --build
   ```
4. **Check it's up.** This should return `{"status":"UP"...}`:
   ```bash
   curl http://localhost:8081/actuator/health
   ```
5. **Try the API.** Open Swagger at http://localhost:8081/swagger-ui.html, or import
   `postman/ShipTrack.postman_collection.json` into Postman and run *Register* and then *Login*.

### Run a service from your IDE instead
```bash
docker compose up -d mysql          # just the database
cd auth-service && ./mvnw spring-boot:run
```
The service reads the repo-root `.env` automatically, so you don't need to set any variables by hand.

### Run the tests
```bash
cd auth-service && ./mvnw test      # unit tests + Testcontainers integration tests (Docker must be running)
```

## Auth API (v1)

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Create a CUSTOMER account |
| POST | `/api/v1/auth/login` | Public | Get a JWT (expires in 15 min) |
| GET | `/api/v1/users/me` | Any logged-in user | Who am I |
| GET | `/api/v1/users/{id}` | Admin | Get a user |
| POST | `/api/v1/users` | Admin | Create a user with any role (e.g. a DRIVER) |

Send the token as `Authorization: Bearer <accessToken>`.

**The first admin** is created when auth-service starts, if `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set in `.env`.
Public sign-up can only create customers. Drivers are created by an admin.

Every error uses the same shape:
```json
{
  "timestamp": "2026-11-04T09:30:00Z",
  "status": 409,
  "error": "EMAIL_ALREADY_REGISTERED",
  "message": "Email is already registered: ali.khan@example.com",
  "path": "/api/v1/auth/register",
  "correlationId": "a1b2c3d4"
}
```

## Repo layout
```
shiptrack/
├── auth-service/          # Spring Boot 4, Java 21
├── docker/mysql/init/     # creates one database per service on first start
├── docs/adr/              # Architecture Decision Records
├── docs/TROUBLESHOOTING.md
├── postman/               # API collection (run in CI via Newman later)
├── docker-compose.yml
└── .env.example
```

## Docs
- Architecture decisions: [docs/adr](docs/adr)
- Something broke? [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
