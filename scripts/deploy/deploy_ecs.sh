#!/usr/bin/env bash
set -euo pipefail

CLUSTER="$1"
SERVICE="$2"
TASK_FAMILY="$3"
CONTAINER_NAME="$4"
IMAGE_URI="$5"
DEPLOY_STAGE="$6"
ARTIFACT_PREFIX="$7"

# Infra owns steady-state scaling. For non-prod, keep a lightweight bootstrap so
# a service created at 0 can come up when an image is first deployed.
if [ "${DEPLOY_STAGE}" != "prod" ]; then
  aws ecs update-service \
    --cluster "${CLUSTER}" \
    --service "${SERVICE}" \
    --desired-count 1 >/dev/null
fi

aws ecs describe-task-definition \
  --task-definition "${TASK_FAMILY}" \
  --query taskDefinition > "${ARTIFACT_PREFIX}-taskdef.json"

python3 - "${ARTIFACT_PREFIX}-taskdef.json" "${ARTIFACT_PREFIX}-taskdef-rendered.json" "${CONTAINER_NAME}" "${IMAGE_URI}" <<'PY'
import json
import sys

source_path, output_path, container_name, image_uri = sys.argv[1:]

with open(source_path, "r", encoding="utf-8") as f:
    task_definition = json.load(f)

for container in task_definition["containerDefinitions"]:
    if container["name"] == container_name:
        container["image"] = image_uri
        break
else:
    raise SystemExit(f"container '{container_name}' not found in task definition")

allowed_fields = {
    "family",
    "taskRoleArn",
    "executionRoleArn",
    "networkMode",
    "containerDefinitions",
    "volumes",
    "placementConstraints",
    "requiresCompatibilities",
    "cpu",
    "memory",
    "tags",
    "pidMode",
    "ipcMode",
    "proxyConfiguration",
    "inferenceAccelerators",
    "ephemeralStorage",
    "runtimePlatform",
}

register_payload = {k: v for k, v in task_definition.items() if k in allowed_fields}

with open(output_path, "w", encoding="utf-8") as f:
    json.dump(register_payload, f)
PY

NEW_TASK_DEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json "file://${ARTIFACT_PREFIX}-taskdef-rendered.json" \
  --query 'taskDefinition.taskDefinitionArn' \
  --output text)

aws ecs update-service \
  --cluster "${CLUSTER}" \
  --service "${SERVICE}" \
  --task-definition "${NEW_TASK_DEF_ARN}" >/dev/null

WAIT_TIMEOUT_SECONDS="${ECS_WAIT_TIMEOUT_SECONDS:-1800}"
WAIT_POLL_SECONDS="${ECS_WAIT_POLL_SECONDS:-30}"
WAIT_DEADLINE=$((SECONDS + WAIT_TIMEOUT_SECONDS))

while true; do
  SERVICE_STATE=$(aws ecs describe-services \
    --cluster "${CLUSTER}" \
    --services "${SERVICE}" \
    --query 'services[0].[desiredCount,runningCount,pendingCount,length(deployments),deployments[0].rolloutState]' \
    --output text)

  read -r DESIRED_COUNT RUNNING_COUNT PENDING_COUNT DEPLOYMENT_COUNT ROLLOUT_STATE <<< "${SERVICE_STATE}"
  echo "ECS service state: desired=${DESIRED_COUNT} running=${RUNNING_COUNT} pending=${PENDING_COUNT} deployments=${DEPLOYMENT_COUNT} rollout=${ROLLOUT_STATE}"

  if [ "${DEPLOYMENT_COUNT}" = "1" ] \
    && [ "${RUNNING_COUNT}" = "${DESIRED_COUNT}" ] \
    && [ "${PENDING_COUNT}" = "0" ] \
    && { [ "${ROLLOUT_STATE}" = "COMPLETED" ] || [ "${ROLLOUT_STATE}" = "None" ]; }; then
    break
  fi

  if [ "${ROLLOUT_STATE}" = "FAILED" ]; then
    aws ecs describe-services \
      --cluster "${CLUSTER}" \
      --services "${SERVICE}" \
      --query 'services[0].events[0:10].[createdAt,message]' \
      --output table
    exit 1
  fi

  if [ "${SECONDS}" -ge "${WAIT_DEADLINE}" ]; then
    echo "Timed out waiting for ECS service ${SERVICE} to stabilize after ${WAIT_TIMEOUT_SECONDS}s" >&2
    aws ecs describe-services \
      --cluster "${CLUSTER}" \
      --services "${SERVICE}" \
      --query 'services[0].events[0:10].[createdAt,message]' \
      --output table
    exit 1
  fi

  sleep "${WAIT_POLL_SECONDS}"
done
