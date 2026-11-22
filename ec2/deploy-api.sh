#!/usr/bin/env bash
set -Eeuo pipefail

image="${1:?image URI is required}"
region="${2:?AWS region is required}"
secret_arn="${3:?Oracle credentials secret ARN is required}"
wallet_bucket="${4:?Oracle wallet bucket is required}"
wallet_key="${5:?Oracle wallet object key is required}"

if [[ ! "$image" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/foro-forum-service:[0-9a-f]{40}$ ]]; then
  echo "Invalid forum-service ECR image URI." >&2
  exit 2
fi
if [[ ! "$region" =~ ^[a-z0-9-]+$ \
  || ! "$secret_arn" =~ ^arn:aws:secretsmanager:${region}:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$ \
  || ! "$wallet_bucket" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ \
  || ! "$wallet_key" =~ ^[A-Za-z0-9._/-]{1,1024}$ ]]; then
  echo "Invalid Oracle resource identifier." >&2
  exit 2
fi

registry="${image%%/*}"
docker_config="$(mktemp -d /tmp/foro-forum-ecr-docker.XXXXXX)"
env_file="$(mktemp /tmp/foro-forum-env.XXXXXX)"
export DOCKER_CONFIG="$docker_config"
cleanup() {
  if [[ -n "$docker_config" ]]; then
    rm -rf -- "$docker_config"
    docker_config=""
  fi
  if [[ -n "$env_file" ]]; then
    rm -f -- "$env_file"
    env_file=""
  fi
}
trap cleanup EXIT

aws ecr get-login-password --region "$region" \
  | docker login --username AWS --password-stdin "$registry" >/dev/null
docker pull "$image"
docker logout "$registry" >/dev/null 2>&1 || true
rm -f -- "$docker_config/config.json"

aws secretsmanager get-secret-value \
  --secret-id "$secret_arn" \
  --query SecretString \
  --output text \
  --region "$region" \
  | python3 -c '
import json
import sys

secret = json.load(sys.stdin)
with open(sys.argv[1], "w", encoding="utf-8") as output:
    db_url = secret.get("FORUM_DB_URL") or secret.get("DB_URL")
    db_user = secret.get("FORUM_DB_USERNAME") or secret.get("DB_USERNAME")
    db_pass = secret.get("FORUM_DB_PASSWORD") or secret.get("DB_PASSWORD")
    for key, value in (("FORUM_DB_URL", db_url), ("FORUM_DB_USERNAME", db_user), ("FORUM_DB_PASSWORD", db_pass)):
        if not isinstance(value, str) or "\n" in value or "\r" in value:
            raise SystemExit(f"Secret is missing a valid {key} value.")
        output.write(f"{key}={value}\n")
' "$env_file"
printf 'AWS_REGION=%s\nORACLE_WALLET_S3_BUCKET=%s\nORACLE_WALLET_S3_KEY=%s\nTNS_ADMIN=/app/wallet\nSPRING_PROFILES_ACTIVE=demo\nDEMO_USERS_URL=http://foro-users-service:8081\n' \
  "$region" "$wallet_bucket" "$wallet_key" >> "$env_file"
chmod 600 "$env_file"

if [[ -d /etc/nginx/conf.d ]]; then
  rm -f /etc/nginx/conf.d/pet-app.conf /etc/nginx/conf.d/default.conf
  cat > /etc/nginx/conf.d/foro-app.conf <<'NGINX'
server {
    listen 80 default_server;
    server_name _;

    location /api/auth {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/users {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/categories {
        proxy_pass http://127.0.0.1:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /api/topics {
        proxy_pass http://127.0.0.1:8082;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:4000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX
  systemctl reload nginx 2>/dev/null || true
fi

wallet_host_dir="/var/lib/foro-oracle-wallet"
mkdir -p "$wallet_host_dir"
wallet_zip="$(mktemp /tmp/foro-wallet.XXXXXX.zip)"
aws s3 cp "s3://${wallet_bucket}/${wallet_key}" "$wallet_zip" --region "$region" >/dev/null
python3 -m zipfile -e "$wallet_zip" "$wallet_host_dir"
find "$wallet_host_dir" -mindepth 2 -type f -exec cp -f {} "$wallet_host_dir/" \;
rm -f -- "$wallet_zip"
chmod -R a+rX "$wallet_host_dir"

backup=""
new_container_attempted=false
rollback() {
  status=$?
  trap - EXIT
  if (( status != 0 )); then
    if [[ "$new_container_attempted" == true ]] && docker inspect foro-forum-service >/dev/null 2>&1; then
      docker rm -f foro-forum-service >/dev/null
    fi
    if [[ -n "$backup" ]] && docker inspect "$backup" >/dev/null 2>&1; then
      docker rename "$backup" foro-forum-service
      docker start foro-forum-service
      echo "forum-service deployment failed; previous container restored." >&2
    fi
  fi
  cleanup
  exit "$status"
}
trap rollback EXIT

if docker inspect foro-forum-service >/dev/null 2>&1; then
  backup="foro-forum-rollback-$(date +%s%N)"
  docker rename foro-forum-service "$backup"
  docker stop "$backup" >/dev/null || true
fi

new_container_attempted=true
docker network create foro-net >/dev/null 2>&1 || true
docker run -d \
  --name foro-forum-service \
  --network foro-net \
  --restart unless-stopped \
  --publish 127.0.0.1:8082:8082 \
  --volume "${wallet_host_dir}:/app/wallet:ro" \
  --env-file "$env_file" \
  "$image" >/dev/null
rm -f -- "$env_file"
env_file=""

healthy=false
for attempt in $(seq 1 60); do
  if curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8082/api/categories \
    | python3 -c 'import json,sys; cats=json.load(sys.stdin); assert isinstance(cats,list) and len(cats) >= 1'; then
    healthy=true
    break
  fi
  sleep 5
done
if [[ "$healthy" != true ]]; then
  echo "forum-service health check failed on port 8082 (/api/categories)." >&2
  exit 1
fi

if [[ -n "$backup" ]]; then
  docker rm "$backup" >/dev/null
fi
backup=""
cleanup
trap - EXIT
echo "forum-service deployed successfully: $image"
