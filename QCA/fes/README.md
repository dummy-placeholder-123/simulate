# QCA FES API

Spring Boot API for creating scans, starting scan execution, and reading scan status and findings.

## API Explorer UI

FES serves the UI locally at:

```text
GET /
```

The same React app is also deployable as a shared static site on S3 + CloudFront.

The UI includes:

- create-scan form with random scan ID generation
- archive upload using the presigned URL from `create-scan`
- start-scan, list, status, and findings actions
- per-request request/response panels
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

## Scan APIs

### Create scan

`POST /api/create-scan`

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

If one engine succeeds and the other fails or times out:

- overall status can still be `FAILED`
- findings can still return the successful engine output
