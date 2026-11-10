#!/bin/bash
# Runs ON the EC2 instance (sent by GitHub Actions through SSM). Usage: deploy-on-host.sh <image-tag>
# 1. reads the shiptrack/prod secret (JSON) from Secrets Manager; on the first deploy it generates the
#    passwords CloudFormation didn't and stores them back, so they stay the same on every later deploy
# 2. writes them to a root-only .env, pulls the new images from ECR
# 3. restarts the stack and waits until every container is healthy
set -euo pipefail

IMAGE_TAG="${1:?usage: deploy-on-host.sh <image-tag>}"
APP_DIR=/opt/shiptrack
SECRET_ID=shiptrack/prod
cd "$APP_DIR"
chmod 700 "$APP_DIR/backup-database.sh"
systemctl daemon-reload
systemctl enable --now shiptrack-backup.timer

# Region, registry, log group and admin email were written by the instance's UserData.
set -a
# shellcheck disable=SC1091
. "$APP_DIR/stack.env"
set +a

secrets=$(aws secretsmanager get-secret-value --region "$AWS_REGION" --secret-id "$SECRET_ID" \
  --query SecretString --output text)

changed=false
for key in dbPassword mysqlRootPassword internalApiKey adminPassword; do
  if [ -z "$(jq -r --arg k "$key" '.[$k] // empty' <<<"$secrets")" ]; then
    secrets=$(jq --arg k "$key" --arg v "$(openssl rand -hex 24)" '. + {($k): $v}' <<<"$secrets")
    changed=true
  fi
done
if [ "$changed" = true ]; then
  aws secretsmanager put-secret-value --region "$AWS_REGION" --secret-id "$SECRET_ID" \
    --secret-string "$secrets" > /dev/null
  echo "Generated missing secrets and stored them in $SECRET_ID"
fi

secret() {
  local value
  value=$(jq -r --arg k "$1" '.[$k] // empty' <<<"$secrets")
  [ -n "$value" ] || { echo "Secret key $1 is missing in $SECRET_ID" >&2; exit 1; }
  printf '%s' "$value"
}

umask 077
cat > .env <<EOF
AWS_REGION=$AWS_REGION
ECR_REGISTRY=$ECR_REGISTRY
LOG_GROUP=$LOG_GROUP
ADMIN_EMAIL=$ADMIN_EMAIL
IMAGE_TAG=$IMAGE_TAG
DB_PASSWORD=$(secret dbPassword)
MYSQL_ROOT_PASSWORD=$(secret mysqlRootPassword)
JWT_SECRET=$(secret jwtSecret)
INTERNAL_API_KEY=$(secret internalApiKey)
ADMIN_PASSWORD=$(secret adminPassword)
EOF

aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

docker compose -f docker-compose.prod.yml pull --quiet
docker compose -f docker-compose.prod.yml up -d --remove-orphans --wait --wait-timeout 420
"$APP_DIR/backup-database.sh"
docker image prune -f > /dev/null

echo "Deployed $IMAGE_TAG"
docker compose -f docker-compose.prod.yml ps --format '{{.Service}}: {{.Status}}'
