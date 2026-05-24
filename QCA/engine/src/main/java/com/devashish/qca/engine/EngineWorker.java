package com.devashish.qca.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EngineWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(EngineWorker.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SqsClient sqsClient;
    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final WorkerConfig config;

    public EngineWorker(SqsClient sqsClient, S3Client s3Client, DynamoDbClient dynamoDbClient, WorkerConfig config) {
        this.sqsClient = sqsClient;
        this.s3Client = s3Client;
        this.dynamoDbClient = dynamoDbClient;
        this.config = config;
    }

    public static void main(String[] args) {
        WorkerConfig config = WorkerConfig.fromEnvironment();
        Region region = Region.of(config.awsRegion());

        try (SqsClient sqsClient = SqsClient.builder().region(region).build();
             S3Client s3Client = S3Client.builder().region(region).build();
             DynamoDbClient dynamoDbClient = DynamoDbClient.builder().region(region).build()) {
            new EngineWorker(sqsClient, s3Client, dynamoDbClient, config).run();
        }
    }

    public void run() {
        LOGGER.info("Starting engine worker id={} queue={}", config.workerId(), config.scanQueueUrl());

        while (!Thread.currentThread().isInterrupted()) {
            List<Message> messages = receiveMessages();
            if (messages.isEmpty()) {
                continue;
            }

            for (Message message : messages) {
                processMessage(message);
            }
        }
    }

    private List<Message> receiveMessages() {
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(config.scanQueueUrl())
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(20)
                        .build())
                .messages();
    }

    private void processMessage(Message message) {
        ScanMessage scanMessage;
        try {
            scanMessage = OBJECT_MAPPER.readValue(message.body(), ScanMessage.class);
        } catch (IOException e) {
            LOGGER.error("Deleting malformed SQS message messageId={}", message.messageId(), e);
            deleteMessage(message);
            return;
        }

        try {
            LOGGER.info("Processing scan scanId={} accountId={}", scanMessage.scanId(), scanMessage.accountId());
            updateStatus(scanMessage.scanId(), "RUNNING", "startedAt", Instant.now().toString(), null);

            long objectSize = downloadInput(scanMessage);
            Thread.sleep(config.processingDuration().toMillis());

            updateStatus(scanMessage.scanId(), "COMPLETED", "completedAt", Instant.now().toString(), objectSize);
            deleteMessage(message);
            LOGGER.info("Completed scan scanId={} bytes={}", scanMessage.scanId(), objectSize);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Worker interrupted while processing scanId={}", scanMessage.scanId());
        } catch (Exception e) {
            updateStatus(scanMessage.scanId(), "FAILED", "failedAt", Instant.now().toString(), null);
            LOGGER.error("Failed scan scanId={}; message will be retried or moved to DLQ", scanMessage.scanId(), e);
        }
    }

    private long downloadInput(ScanMessage scanMessage) throws IOException {
        Path tempFile = Files.createTempFile("qca-scan-" + scanMessage.scanId(), ".zip");
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(scanMessage.uploadBucketName())
                    .key(scanMessage.uploadObjectKey())
                    .build();

            try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(request)) {
                Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            return Files.size(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void updateStatus(String scanId, String status, String timestampAttribute, String timestamp, Long objectSize) {
        Map<String, AttributeValue> values = new java.util.HashMap<>();
        values.put(":status", stringValue(status));
        values.put(":timestamp", stringValue(timestamp));
        values.put(":workerId", stringValue(config.workerId()));

        String updateExpression = "SET #status = :status, updatedAt = :timestamp, "
                + timestampAttribute + " = :timestamp, workerId = :workerId";
        if (objectSize != null) {
            values.put(":objectSize", numberValue(objectSize));
            updateExpression += ", inputObjectSizeBytes = :objectSize";
        }

        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(config.scanTableName())
                .key(Map.of("scanId", stringValue(scanId)))
                .updateExpression(updateExpression)
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(values)
                .build());
    }

    private void deleteMessage(Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(config.scanQueueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
    }

    private AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue numberValue(Number value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScanMessage(
            String eventType,
            String scanId,
            String accountId,
            String repoFullName,
            Integer prNumber,
            String headSha,
            String useCase,
            String service,
            String uploadBucketName,
            String uploadObjectKey,
            String queuedAt
    ) {
    }

    private record WorkerConfig(
            String awsRegion,
            String scanQueueUrl,
            String scanTableName,
            String workerId,
            Duration processingDuration
    ) {
        private static WorkerConfig fromEnvironment() {
            return new WorkerConfig(
                    env("AWS_REGION", "us-east-1"),
                    requiredEnv("SCAN_QUEUE_URL"),
                    env("SCAN_TABLE_NAME", "qca-scans"),
                    env("WORKER_ID", defaultWorkerId()),
                    Duration.ofSeconds(Long.parseLong(env("PROCESSING_SECONDS", "60"))));
        }

        private static String requiredEnv(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " environment variable is required");
            }
            return value;
        }

        private static String env(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }

        private static String defaultWorkerId() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                return "engine-" + UUID.randomUUID();
            }
        }
    }
}
