#!/bin/sh
set -eu

# Mapear variables de AWS Secrets Manager (DB_URL, DB_USERNAME, DB_PASSWORD)
if [ -n "${DB_URL:-}" ]; then
  export FORUM_DB_URL="$DB_URL"
fi
if [ -n "${DB_USERNAME:-}" ]; then
  export FORUM_DB_USERNAME="$DB_USERNAME"
fi
if [ -n "${DB_PASSWORD:-}" ]; then
  export FORUM_DB_PASSWORD="$DB_PASSWORD"
fi

# Si corre en ECS/EC2 con Wallet en S3 y /app/wallet aún no está montado, descargarla automáticamente
if [ -n "${ORACLE_WALLET_S3_BUCKET:-}" ] && [ -n "${ORACLE_WALLET_S3_KEY:-}" ] && [ ! -f "/app/wallet/tnsnames.ora" ]; then
  mkdir -p /app/wallet
  aws s3 cp "s3://${ORACLE_WALLET_S3_BUCKET}/${ORACLE_WALLET_S3_KEY}" /tmp/wallet.zip --region "${AWS_REGION:-us-east-1}"
  unzip -q -j -o /tmp/wallet.zip -d /app/wallet
  rm -f /tmp/wallet.zip
fi

exec java -jar /app/app.jar
