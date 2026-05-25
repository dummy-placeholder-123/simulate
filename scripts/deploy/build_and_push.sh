#!/usr/bin/env bash
set -euo pipefail

SERVICE_DIR="$1"
REPOSITORY="$2"
DEPLOY_TAG="$3"

IMAGE_URI="${ECR_REGISTRY}/${REPOSITORY}"

aws ecr describe-repositories --repository-names "${REPOSITORY}" >/dev/null 2>&1 || \
aws ecr create-repository --repository-name "${REPOSITORY}" >/dev/null

docker build -t "${IMAGE_URI}:${DEPLOY_TAG}" "${SERVICE_DIR}"

if [ "${DEPLOY_TAG}" != "${SHA_TAG}" ]; then
  docker tag "${IMAGE_URI}:${DEPLOY_TAG}" "${IMAGE_URI}:${SHA_TAG}"
fi

if [ "${DEPLOY_TAG}" != "${DATE_TAG}" ]; then
  docker tag "${IMAGE_URI}:${DEPLOY_TAG}" "${IMAGE_URI}:${DATE_TAG}"
fi

docker push "${IMAGE_URI}:${DEPLOY_TAG}"
docker push "${IMAGE_URI}:${SHA_TAG}"
docker push "${IMAGE_URI}:${DATE_TAG}"
