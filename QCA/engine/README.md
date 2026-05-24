# QCA Engine

Plain Java worker for processing queued scan jobs.

The worker polls `qca-scan-queue`, downloads the scan input from S3, simulates processing, updates `qca-scans`, and deletes the SQS message after success.

Required environment:

```text
SCAN_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/564061926474/qca-scan-queue
```

Optional environment:

```text
AWS_REGION=us-east-1
SCAN_TABLE_NAME=qca-scans
PROCESSING_SECONDS=60
WORKER_ID=engine-local
```

Run locally:

```sh
mvn -q -DskipTests package
java -jar target/engine-0.0.1-SNAPSHOT.jar
```

Build container:

```sh
docker build -t qca-engine .
```
