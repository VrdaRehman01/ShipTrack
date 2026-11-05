#!/bin/bash
# Runs once, the first time the MySQL volume is created.
# One MySQL server, one database per service (see docs/adr/0002-one-mysql-many-databases.md).
#
# The MySQL entrypoint SOURCES this file when it isn't executable (e.g. on a Linux checkout, or when the
# deploy writes it). Shell options set here would then leak into the entrypoint and crash it, so all
# strict-mode work happens inside a subshell ( ... ).
(
  set -euo pipefail
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS auth_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE DATABASE IF NOT EXISTS shipment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE DATABASE IF NOT EXISTS billing_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
    CREATE DATABASE IF NOT EXISTS tracking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

    GRANT ALL PRIVILEGES ON auth_db.*     TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON shipment_db.* TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON billing_db.*  TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON tracking_db.* TO '${MYSQL_USER}'@'%';
    FLUSH PRIVILEGES;
EOSQL
)
