# Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `docker compose up` says `set JWT_SECRET in .env` | There's no `.env` file | `cp .env.example .env` and fill in the values |
| auth-service won't start: `shiptrack.jwt.secret ... must be at least 32 characters` | `JWT_SECRET` is missing or too short | Use a long random value, e.g. `openssl rand -base64 48` |
| `Access denied for user 'shiptrack'` | You changed `DB_USERNAME`/`DB_PASSWORD` after MySQL was first created (the init script only runs once) | `docker compose down -v` (**deletes local DB data**), then `docker compose up -d` |
| Port 3306 is already in use | MySQL is already installed on your machine | Set `MYSQL_HOST_PORT=3307` and `DB_PORT=3307` in `.env` |
| Init script fails with `$'\r': command not found` | The `.sh` file has Windows CRLF line endings | `.gitattributes` forces LF. If you edited the file, convert it with `dos2unix docker/mysql/init/*.sh` |
| Hibernate says `Schema-validation: wrong column type ... found [varchar], but expecting [enum ...]` | Hibernate maps Java enums to a MySQL `ENUM` by default | Annotate the field with `@JdbcTypeCode(SqlTypes.VARCHAR)` (see `User.role`) |
| Integration tests fail with `Could not find a valid Docker environment` | Docker Desktop isn't running | Start Docker Desktop and run the tests again |
| Integration tests hang for many minutes on "Waiting for database connection" | A half-open connection through Docker Desktop's port proxy; the MySQL driver has no read timeout by default | Test containers set `connectTimeout`/`socketTimeout` URL params so a stalled probe fails fast and retries (`TestcontainersConfiguration`) |
| Micronaut tests: `Could not find a valid Docker environment` | Testcontainers 1.x (managed by Micronaut) can't negotiate with Docker Engine 29+ | `tracking-service/pom.xml` pins Testcontainers 2.x, including the core `testcontainers` artifact |
| Gateway fails to start: `expected single matching bean ... CorsConfigurationSource` | Spring MVC's introspector is also a `CorsConfigurationSource` | `shiptrack-common` looks the gateway's bean up by name (`corsConfigurationSource`) |
| Invoice missing right after delivery (404 "No invoice yet") | Invoicing runs asynchronously after the commit; if billing was down, the retry job picks it up | Wait a moment, or up to `BILLING_RETRY_INTERVAL` after billing recovers |
| `401` from every endpoint through the gateway | Different `JWT_SECRET` in the gateway and auth-service, or the token is older than 15 minutes | Use the same `.env` for everything; log in again |
| MySQL container exits on first start on Linux/CI: `MYSQL_ONETIME_PASSWORD: unbound variable` | The entrypoint *sources* non-executable init scripts, so `set -u` in our script leaked into it (Windows hid this: every file looks executable there) | Strict mode now runs inside a subshell in `01-create-databases.sh`, and the file is executable in git |
| Deploy fails: `Instance i-... is terminated` | A stack update replaced the server; older deploy workflows looked for the old instance ID | The deploy now finds the server by its `shiptrack-prod` tag; the template no longer re-resolves the AMI on every update (which caused the replacement) |
