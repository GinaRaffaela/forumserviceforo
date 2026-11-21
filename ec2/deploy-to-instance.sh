#!/usr/bin/env bash
set -Eeuo pipefail

instance_id="${1:?EC2 instance ID is required}"
region="${2:?AWS region is required}"
component="${3:?deployment component is required}"
host_script="${4:?host deployment script is required}"
shift 4

if [[ ! "$instance_id" =~ ^i-[0-9a-f]{8,17}$ || ! "$region" =~ ^[a-z0-9-]+$ \
  || ! "$component" =~ ^(frontend|api)$ ]]; then
  echo "Invalid EC2 instance ID, region or deployment component." >&2
  exit 2
fi
if [[ ! -f "$host_script" ]]; then
  echo "Deployment script not found: $host_script" >&2
  exit 2
fi

quote_argument() {
  local value="$1"
  value="${value//\'/\'\\\'\'}"
  printf "'%s'" "$value"
}

script_b64="$(gzip -c "$host_script" | base64 -w0)"
remote_script="/tmp/$(basename "$host_script").${GITHUB_RUN_ID:-local}.sh"
remote_command="set -eu; trap 'rm -f -- $remote_script' EXIT; printf '%s' '$script_b64' | base64 --decode | gzip --decompress > '$remote_script'; chmod 700 '$remote_script'; if sudo bash '$remote_script'"
for argument in "$@"; do
  remote_command+=" $(quote_argument "$argument")"
done
remote_command+="; then deployment_status=0; else deployment_status=\$?; fi; exit \"\$deployment_status\""

parameters_file="$(mktemp)"
trap 'rm -f -- "$parameters_file"' EXIT
jq -n --arg command "$remote_command" '{commands: [$command]}' > "$parameters_file"
command_id="$(aws ssm send-command \
  --instance-ids "$instance_id" \
  --document-name AWS-RunShellScript \
  --comment "Deploy Pet application from GitHub Actions" \
  --timeout-seconds 900 \
  --parameters "file://$parameters_file" \
  --region "$region" \
  --query 'Command.CommandId' \
  --output text)"

invocation=""
for attempt in $(seq 1 90); do
  if invocation="$(aws ssm get-command-invocation \
    --command-id "$command_id" \
    --instance-id "$instance_id" \
    --region "$region" \
    --output json 2>/dev/null)"; then
    status="$(jq -r '.Status' <<< "$invocation")"
    case "$status" in
      Success)
        jq -r '.StandardOutputContent | select(length > 0)' <<< "$invocation"
        break
        ;;
      Failed|Cancelled|TimedOut|Cancelling)
        jq -r '.StandardOutputContent, .StandardErrorContent | select(length > 0)' <<< "$invocation" >&2
        echo "EC2 deployment command ended with status $status." >&2
        exit 1
        ;;
    esac
  fi
  if (( attempt == 90 )); then
    echo "Timed out waiting for the EC2 deployment command." >&2
    exit 1
  fi
  sleep 5
done

public_ip="$(aws ec2 describe-instances \
  --instance-ids "$instance_id" \
  --region "$region" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)"
if [[ ! "$public_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  echo "EC2 instance does not have a public IPv4 address." >&2
  exit 1
fi

if [[ "$component" == frontend ]]; then
  curl --fail --silent --show-error --retry 12 --retry-all-errors --retry-delay 5 \
    --max-time 10 "http://${public_ip}/" --output /dev/null
  echo "EC2 smoke test passed: frontend HTTP 200."
else
  curl --fail --silent --show-error --retry 12 --retry-all-errors --retry-delay 5 \
    --max-time 10 "http://${public_ip}/api/categories" --output "$RUNNER_TEMP/categories-response.json"
  python3 - "$RUNNER_TEMP/categories-response.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response:
    categories = json.load(response)
if not isinstance(categories, list) or len(categories) < 1:
    raise SystemExit("Public GET /api/categories did not return demo categories.")
print(f"EC2 smoke test passed: forum-service HTTP 200 with {len(categories)} categories.")
PY
fi
