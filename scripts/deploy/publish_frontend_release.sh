#!/usr/bin/env bash
set -euo pipefail

SERVICE_DIR="$1"
ARTIFACT_PREFIX="$2"
STABLE_TAG="$3"
RELEASE_BUCKET="$4"

pushd "${SERVICE_DIR}" >/dev/null
npm ci
npm run build
popd >/dev/null

for TAG in "${STABLE_TAG}" "${SHA_TAG}" "${DATE_TAG}"; do
  aws s3 sync \
    "${SERVICE_DIR}/dist/" \
    "s3://${RELEASE_BUCKET}/${ARTIFACT_PREFIX}/${TAG}/" \
    --delete
done
