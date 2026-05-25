#!/usr/bin/env bash
set -euo pipefail

CLUSTER="$1"
SERVICE="$2"
TASK_FAMILY="$3"
CONTAINER_NAME="$4"
IMAGE_URI="$5"
MIN_CAPACITY="$6"
MAX_CAPACITY="$7"
ARTIFACT_PREFIX="$8"

aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id "service/${CLUSTER}/${SERVICE}" \
  --min-capacity "${MIN_CAPACITY}" \
  --max-capacity "${MAX_CAPACITY}"

aws ecs update-service \
  --cluster "${CLUSTER}" \
  --service "${SERVICE}" \
  --desired-count "${MIN_CAPACITY}" >/dev/null

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

aws ecs wait services-stable \
  --cluster "${CLUSTER}" \
  --services "${SERVICE}"
