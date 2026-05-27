# QCA FES API

Spring Boot API for creating scans, starting scan execution, and reading scan status and findings.

## API Explorer UI

FES serves the UI locally at:

```text
GET /
```

The same React app is also deployable as a shared static site on S3 + CloudFront.

The UI includes:

- login and refresh token actions
- create-scan form with random scan ID generation
- archive upload using the presigned URL from `create-scan`
- start-scan, list, status, and findings actions
- request preview and response panel
- response timing in milliseconds

Source:

```text
QCA/fes-ui
```

Frontend commands:

```sh
cd QCA/fes-ui
npm install
npm run dev
npm run build
npm run build:spring
```

Build targets:

```text
npm run build         -> QCA/fes-ui/dist
npm run build:spring  -> QCA/fes/src/main/resources/static
```

Release/deploy config:

```text
frontend-releases/fes-ui.yml
environments/dev/fes-ui.yml
```

GitHub Actions:

```text
.github/workflows/release-ui.yml
.github/workflows/deploy-ui.yml
```

The hosted UI does not publish a Docker image. It publishes versioned static build artifacts and deploys them to a shared S3 + CloudFront site.

## Authentication

Mutating APIs require a bearer access token.

### Login

`POST /api/auth/login`

Request:

```json
{
  "username": "qca-admin",
  "password": "replace-with-demo-password"
}
```

Response:

```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "tokenType": "Bearer",
  "accessTokenExpiresInSeconds": 900,
  "refreshTokenExpiresInSeconds": 604800
}
```

### Refresh tokens

`POST /api/auth/refresh`

Request:

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

## Scan APIs

### Create scan

`POST /api/create-scan`

Headers:

```text
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request:

```json
{
  "scanId": "scan-local-001",
  "accountId": "564061926474",
  "repoFullName": "devashishrane/qca",
  "prNumber": 42,
  "headSha": "abc123def4567890",
  "useCase": "github-pr-scan",
  "service": "fes"
}
```

Response:

```json
{
  "scanId": "scan-local-001",
  "accountId": "564061926474",
  "repoFullName": "devashishrane/qca",
  "prNumber": 42,
  "headSha": "abc123def4567890",
  "useCase": "github-pr-scan",
  "service": "fes",
  "status": "WAITING_FOR_UPLOAD",
  "createdAt": "2026-05-27T10:00:00Z",
  "uploadUrl": "https://...",
  "uploadObjectKey": "scans/564061926474/scan-local-001/input",
  "uploadUrlExpiresAt": "2026-05-27T10:05:00Z"
}
```

After `create-scan`, upload the archive to the returned `uploadUrl` with `Content-Type: application/octet-stream`.

### Start scan

`POST /api/start-scan`

Headers:

```text
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request:

```json
{
  "scanId": "scan-local-001"
}
```

Response:

```json
{
  "scanId": "scan-local-001",
  "status": "QUEUED",
  "queuedAt": "2026-05-27T10:01:00Z"
}
```

This starts the Step Functions workflow, which fans out to:

- standard engine
- llm engine

Each worker has a `3` minute Step Functions timeout.

## Read APIs

### List scans

`GET /api/accounts/{accountId}/scans?limit=50`

Example:

```text
GET /api/accounts/564061926474/scans?limit=50
```

### Get scan status

`GET /api/scans/{scanId}/status`

Response:

```json
{
  "scanId": "scan-local-001",
  "status": "QUEUED",
  "createdAt": "2026-05-27T10:00:00Z",
  "updatedAt": "2026-05-27T10:01:00Z",
  "queuedAt": "2026-05-27T10:01:00Z",
  "findingsAvailable": false
}
```

### Get scan findings

`GET /api/scans/{scanId}/findings`

Response shape:

```json
{
  "scanId": "scan-local-001",
  "status": "COMPLETED",
  "resultBucketName": "qca-dev-scan-uploads-564061926474-us-east-1",
  "resultObjectKey": "scans/564061926474/scan-local-001/findings.json",
  "findings": {
    "scanId": "scan-local-001",
    "status": "COMPLETED",
    "resultBucketName": "qca-dev-scan-uploads-564061926474-us-east-1",
    "resultObjectKey": "scans/564061926474/scan-local-001/findings.json",
    "engines": {
      "standard": {},
      "llm": {}
    },
    "findings": []
  }
}
```

Important behavior:

- if both engines succeed, the response contains merged findings from both
- if one engine succeeds and one engine fails or times out, the scan status is `FAILED`
- in that partial-failure case, `GET /findings` still returns findings from the successful engine only
- if neither engine output exists yet, the API returns `400 scan findings are not available yet`

## Health and observability

### Health

`GET /actuator/health`

### Trace headers

Every request includes:

```text
X-Trace-Id
X-Span-Id
```

These values are also written to FES logs.

## Local request files

Use:

- [requests.http](/Users/devashishrane/Documents/sde2/QCA/test/requests.http) for local FES
- [alb-requests.http](/Users/devashishrane/Documents/sde2/QCA/test/alb-requests.http) for ALB-based testing
