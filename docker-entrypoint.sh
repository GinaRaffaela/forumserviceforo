#!/bin/sh
set -eu

# Si se proporcionan variables genéricas DB_URL / DB_USERNAME / DB_PASSWORD desde AWS Secrets Manager,
# las mapeamos a FORUM_DB_URL / FORUM_DB_USERNAME / FORUM_DB_PASSWORD
if [ -n "${DB_URL:-}" ] && [ -z "${FORUM_DB_URL:-}" ]; then
  export FORUM_DB_URL="$DB_URL"
fi
if [ -n "${DB_USERNAME:-}" ] && [ -z "${FORUM_DB_USERNAME:-}" ]; then
  export FORUM_DB_USERNAME="$DB_USERNAME"
fi
if [ -n "${DB_PASSWORD:-}" ] && [ -z "${FORUM_DB_PASSWORD:-}" ]; then
  export FORUM_DB_PASSWORD="$DB_PASSWORD"
fi

exec java -jar /app/app.jar
