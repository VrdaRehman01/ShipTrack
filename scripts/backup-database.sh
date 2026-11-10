#!/bin/bash
# Daily logical backup of every ShipTrack database to the private S3 backup bucket.
set -euo pipefail

APP_DIR=/opt/shiptrack
cd "$APP_DIR"
set -a
# shellcheck disable=SC1091
. "$APP_DIR/stack.env"
set +a

MYSQL_ROOT_PASSWORD=$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' "$APP_DIR/.env")
[ -n "$MYSQL_ROOT_PASSWORD" ] || { echo "MYSQL_ROOT_PASSWORD is missing" >&2; exit 1; }

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_key="s3://${BACKUP_BUCKET}/mysql/${timestamp}.sql.gz"
docker compose -f docker-compose.prod.yml exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
  mysqldump --user=root --all-databases \
    --single-transaction --routines --events --triggers --hex-blob \
  | gzip -1 \
  | aws s3 cp - "$backup_key" --region "$AWS_REGION" --sse AES256

echo "Database backup uploaded: $backup_key"
