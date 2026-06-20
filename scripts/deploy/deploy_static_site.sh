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
AUTH_PROVIDER="${FES_UI_AUTH_PROVIDER:-cognito}"
SESSION_TTL_SECONDS="${FES_UI_SESSION_TTL_SECONDS:-21600}"
COGNITO_REGION="${FES_UI_COGNITO_REGION:-}"
COGNITO_USER_POOL_ID="${FES_UI_COGNITO_USER_POOL_ID:-}"
COGNITO_CLIENT_ID="${FES_UI_COGNITO_CLIENT_ID:-}"

if ! [[ "${SESSION_TTL_SECONDS}" =~ ^[0-9]+$ ]]; then
  echo "FES_UI_SESSION_TTL_SECONDS must be a positive integer" >&2
  exit 1
fi

if [ "${AUTH_PROVIDER}" = "cognito" ]; then
  if [ -z "${COGNITO_REGION}" ] || [ -z "${COGNITO_USER_POOL_ID}" ] || [ -z "${COGNITO_CLIENT_ID}" ]; then
    echo "Cognito UI auth requires FES_UI_COGNITO_REGION, FES_UI_COGNITO_USER_POOL_ID, and FES_UI_COGNITO_CLIENT_ID" >&2
    exit 1
  fi
fi

aws s3 ls "s3://${RELEASE_BUCKET}/${ARTIFACT_PREFIX}/${DEPLOY_TAG}/" >/dev/null

aws s3 sync \
  "s3://${RELEASE_BUCKET}/${ARTIFACT_PREFIX}/${DEPLOY_TAG}/" \
  "s3://${HOSTING_BUCKET}/" \
  --delete

aws s3 cp \
  "s3://${RELEASE_BUCKET}/${ARTIFACT_PREFIX}/${DEPLOY_TAG}/index.html" \
  "s3://${HOSTING_BUCKET}/index.html" \
  --content-type text/html \
  --cache-control "no-cache, no-store, must-revalidate"

RUNTIME_CONFIG_FILE=$(mktemp)
cat > "${RUNTIME_CONFIG_FILE}" <<EOF
{
  "auth": {
    "provider": "${AUTH_PROVIDER}",
    "sessionTtlSeconds": ${SESSION_TTL_SECONDS},
    "cognito": {
      "region": "${COGNITO_REGION}",
      "userPoolId": "${COGNITO_USER_POOL_ID}",
      "clientId": "${COGNITO_CLIENT_ID}"
    }
  },
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
