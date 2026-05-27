#!/usr/bin/env bash
set -euo pipefail

RELEASE_BUCKET="$1"
ARTIFACT_PREFIX="$2"
DEPLOY_TAG="$3"
HOSTING_BUCKET="$4"
CLOUDFRONT_DISTRIBUTION_ID="$5"
DEFAULT_ENV="$6"
DEV_API_BASE_URL="$7"
QA_API_BASE_URL="$8"
PROD_API_BASE_URL="$9"

aws s3 ls "s3://${RELEASE_BUCKET}/${ARTIFACT_PREFIX}/${DEPLOY_TAG}/" >/dev/null

aws s3 sync \
  "s3://${RELEASE_BUCKET}/${ARTIFACT_PREFIX}/${DEPLOY_TAG}/" \
  "s3://${HOSTING_BUCKET}/" \
  --delete

RUNTIME_CONFIG_FILE=$(mktemp)
cat > "${RUNTIME_CONFIG_FILE}" <<EOF
{
  "defaultEnv": "${DEFAULT_ENV}",
  "environments": {
    "dev": { "apiBaseUrl": "${DEV_API_BASE_URL}" },
    "qa": { "apiBaseUrl": "${QA_API_BASE_URL}" },
    "prod": { "apiBaseUrl": "${PROD_API_BASE_URL}" }
  }
}
EOF

aws s3 cp \
  "${RUNTIME_CONFIG_FILE}" \
  "s3://${HOSTING_BUCKET}/runtime-config.json" \
  --content-type application/json \
  --cache-control "no-cache, no-store, must-revalidate"

rm -f "${RUNTIME_CONFIG_FILE}"

aws cloudfront create-invalidation \
  --distribution-id "${CLOUDFRONT_DISTRIBUTION_ID}" \
  --paths "/*" >/dev/null
