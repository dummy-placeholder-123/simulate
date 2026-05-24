package com.devashish.qca.fes.service;

import com.devashish.qca.fes.dto.ScanListResponse;
import com.devashish.qca.fes.dto.ScanRequest;
import com.devashish.qca.fes.dto.ScanResponse;
import com.devashish.qca.fes.dto.StartScanRequest;
import com.devashish.qca.fes.dto.StartScanResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ScanService {
    private static final String WAITING_FOR_UPLOAD = "WAITING_FOR_UPLOAD";
    private static final String QUEUED = "QUEUED";
    private static final String DUPLICATE = "DUPLICATE";

    private final DynamoDbClient dynamoDbClient;
    private final S3Presigner s3Presigner;
    private final SfnClient sfnClient;
    private final ObjectMapper objectMapper;
    private final String scanTableName;
    private final String idempotencyTableName;
    private final String scanUploadBucketName;
    private final String scanStateMachineArn;
    private final long idempotencyTtlHours;
    private final long presignedUrlDurationMinutes;

    public ScanService(
            DynamoDbClient dynamoDbClient,
            S3Presigner s3Presigner,
            SfnClient sfnClient,
            ObjectMapper objectMapper,
            @Value("${qca.dynamodb.scan-table-name}") String scanTableName,
            @Value("${qca.dynamodb.idempotency-table-name}") String idempotencyTableName,
            @Value("${qca.s3.scan-upload-bucket-name}") String scanUploadBucketName,
            @Value("${qca.step-functions.scan-state-machine-arn}") String scanStateMachineArn,
            @Value("${qca.idempotency.ttl-hours}") long idempotencyTtlHours,
            @Value("${qca.s3.presigned-url-duration-minutes}") long presignedUrlDurationMinutes) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Presigner = s3Presigner;
        this.sfnClient = sfnClient;
        this.objectMapper = objectMapper;
        this.scanTableName = scanTableName;
        this.idempotencyTableName = idempotencyTableName;
        this.scanUploadBucketName = scanUploadBucketName;
        this.scanStateMachineArn = scanStateMachineArn;
        this.idempotencyTtlHours = idempotencyTtlHours;
        this.presignedUrlDurationMinutes = presignedUrlDurationMinutes;
    }

    public ScanResponse createScan(ScanRequest request) {
        validate(request);

        String scanId = hasText(request.scanId()) ? request.scanId() : UUID.randomUUID().toString();
        String idempotencyKey = buildIdempotencyKey(request);
        Instant now = Instant.now();
        String createdAt = now.toString();
        long nowEpochSecond = now.getEpochSecond();
        long expiresAt = now.plus(Duration.ofHours(idempotencyTtlHours)).getEpochSecond();

        try {
            putIdempotencyRecord(request, scanId, idempotencyKey, createdAt, nowEpochSecond, expiresAt);
        } catch (ConditionalCheckFailedException e) {
            return duplicateResponse(request, scanId, idempotencyKey, createdAt);
        }

        putScanRecord(request, scanId, idempotencyKey, createdAt, expiresAt, WAITING_FOR_UPLOAD);

        return response(request, scanId, WAITING_FOR_UPLOAD, idempotencyKey, createdAt);
    }

    public StartScanResponse startScan(StartScanRequest request) {
        if (request == null || !hasText(request.scanId())) {
            throw new IllegalArgumentException("scanId is required");
        }

        Map<String, AttributeValue> scanItem = getScanItem(request.scanId());
        if (scanItem.isEmpty()) {
            throw new IllegalArgumentException("scan not found");
        }

        String currentStatus = stringAttribute(scanItem, "status", null);
        if (QUEUED.equals(currentStatus)) {
            return new StartScanResponse(request.scanId(), QUEUED, stringAttribute(scanItem, "queuedAt", null));
        }

        String queuedAt = Instant.now().toString();
        startScanStateMachine(scanItem, queuedAt);
        updateScanQueued(request.scanId(), queuedAt);

        return new StartScanResponse(request.scanId(), QUEUED, queuedAt);
    }

    public List<ScanListResponse> listScansByAccountId(String accountId, Integer limit) {
        if (!hasText(accountId)) {
            throw new IllegalArgumentException("accountId is required");
        }

        int queryLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 100));

        return dynamoDbClient.query(QueryRequest.builder()
                        .tableName(scanTableName)
                        .indexName("accountScansIndex")
                        .keyConditionExpression("accountId = :accountId")
                        .expressionAttributeValues(Map.of(":accountId", stringValue(accountId)))
                        .scanIndexForward(false)
                        .limit(queryLimit)
                        .build())
                .items()
                .stream()
                .map(this::scanResponseFromItem)
                .toList();
    }

    private ScanResponse duplicateResponse(
            ScanRequest request,
            String fallbackScanId,
            String idempotencyKey,
            String fallbackCreatedAt) {
        Map<String, AttributeValue> item = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(idempotencyTableName)
                .key(Map.of("idempotencyKey", stringValue(idempotencyKey)))
                .build()).item();

        String scanId = stringAttribute(item, "scanId", fallbackScanId);
        String createdAt = stringAttribute(item, "createdAt", fallbackCreatedAt);
        return response(request, scanId, DUPLICATE, idempotencyKey, createdAt);
    }

    private Map<String, AttributeValue> getScanItem(String scanId) {
        return dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(scanTableName)
                .key(Map.of("scanId", stringValue(scanId)))
                .build()).item();
    }

    private void putIdempotencyRecord(
            ScanRequest request,
            String scanId,
            String idempotencyKey,
            String createdAt,
            long nowEpochSecond,
            long expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("idempotencyKey", stringValue(idempotencyKey));
        item.put("scanId", stringValue(scanId));
        item.put("accountId", stringValue(request.accountId()));
        item.put("repoFullName", stringValue(request.repoFullName()));
        item.put("prNumber", numberValue(request.prNumber()));
        item.put("headSha", stringValue(request.headSha()));
        item.put("createdAt", stringValue(createdAt));
        item.put("expiresAt", numberValue(expiresAt));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(idempotencyTableName)
                .item(item)
                .conditionExpression("attribute_not_exists(idempotencyKey) OR expiresAt < :now")
                .expressionAttributeValues(Map.of(":now", numberValue(nowEpochSecond)))
                .build());
    }

    private void putScanRecord(
            ScanRequest request,
            String scanId,
            String idempotencyKey,
            String createdAt,
            long expiresAt,
            String status) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("scanId", stringValue(scanId));
        item.put("accountId", stringValue(request.accountId()));
        item.put("repoFullName", stringValue(request.repoFullName()));
        item.put("prNumber", numberValue(request.prNumber()));
        item.put("headSha", stringValue(request.headSha()));
        item.put("useCase", stringValue(request.useCase()));
        item.put("service", stringValue(request.service()));
        item.put("status", stringValue(status));
        item.put("idempotencyKey", stringValue(idempotencyKey));
        item.put("createdAt", stringValue(createdAt));
        item.put("updatedAt", stringValue(createdAt));
        item.put("createdAtScanId", stringValue(createdAt + "#" + scanId));
        item.put("idempotencyExpiresAt", numberValue(expiresAt));
        item.put("uploadBucketName", stringValue(scanUploadBucketName));
        item.put("uploadObjectKey", stringValue(uploadObjectKey(request, scanId)));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(scanTableName)
                .item(item)
                .build());
    }

    private void updateScanQueued(String scanId, String queuedAt) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(scanTableName)
                .key(Map.of("scanId", stringValue(scanId)))
                .updateExpression("SET #status = :status, queuedAt = :queuedAt, updatedAt = :queuedAt")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", stringValue(QUEUED),
                        ":queuedAt", stringValue(queuedAt)))
                .build());
    }

    private void startScanStateMachine(Map<String, AttributeValue> scanItem, String queuedAt) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", "SCAN_QUEUED");
        message.put("scanId", stringAttribute(scanItem, "scanId", null));
        message.put("accountId", stringAttribute(scanItem, "accountId", null));
        message.put("repoFullName", stringAttribute(scanItem, "repoFullName", null));
        message.put("prNumber", integerAttribute(scanItem, "prNumber"));
        message.put("headSha", stringAttribute(scanItem, "headSha", null));
        message.put("useCase", stringAttribute(scanItem, "useCase", null));
        message.put("service", stringAttribute(scanItem, "service", null));
        message.put("uploadBucketName", stringAttribute(scanItem, "uploadBucketName", scanUploadBucketName));
        message.put("uploadObjectKey", stringAttribute(scanItem, "uploadObjectKey", null));
        message.put("queuedAt", queuedAt);

        sfnClient.startExecution(StartExecutionRequest.builder()
                .stateMachineArn(scanStateMachineArn)
                .input(toJson(message))
                .build());
    }

    private String toJson(Map<String, Object> message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize scan queue message", e);
        }
    }

    private String buildIdempotencyKey(ScanRequest request) {
        return "github-scan:%s:repo:%s:pr:%d:sha:%s".formatted(
                request.accountId(),
                request.repoFullName(),
                request.prNumber(),
                request.headSha());
    }

    private ScanResponse response(
            ScanRequest request,
            String scanId,
            String status,
            String idempotencyKey,
            String createdAt) {
        String uploadObjectKey = uploadObjectKey(request, scanId);
        PresignedUpload presignedUpload = presignUpload(uploadObjectKey);

        return new ScanResponse(
                scanId,
                request.accountId(),
                request.repoFullName(),
                request.prNumber(),
                request.headSha(),
                request.useCase(),
                request.service(),
                status,
                createdAt,
                presignedUpload.url(),
                uploadObjectKey,
                presignedUpload.expiresAt());
    }

    private PresignedUpload presignUpload(String uploadObjectKey) {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(presignedUrlDurationMinutes));
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(scanUploadBucketName)
                .key(uploadObjectKey)
                .contentType("application/octet-stream")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlDurationMinutes))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presignedRequest.url().toString(), expiresAt.toString());
    }

    private String uploadObjectKey(ScanRequest request, String scanId) {
        return "scans/%s/%s/input".formatted(request.accountId(), scanId);
    }

    private record PresignedUpload(String url, String expiresAt) {
    }

    private ScanListResponse scanResponseFromItem(Map<String, AttributeValue> item) {
        return new ScanListResponse(
                stringAttribute(item, "scanId", null),
                stringAttribute(item, "accountId", null),
                stringAttribute(item, "repoFullName", null),
                integerAttribute(item, "prNumber"),
                stringAttribute(item, "headSha", null),
                stringAttribute(item, "useCase", null),
                stringAttribute(item, "service", null),
                stringAttribute(item, "status", null),
                stringAttribute(item, "createdAt", null));
    }

    private void validate(ScanRequest request) {
        if (request == null
                || !hasText(request.accountId())
                || !hasText(request.repoFullName())
                || request.prNumber() == null
                || !hasText(request.headSha())
                || !hasText(request.useCase())
                || !hasText(request.service())) {
            throw new IllegalArgumentException(
                    "accountId, repoFullName, prNumber, headSha, useCase, and service are required");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue numberValue(Number value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }

    private String stringAttribute(Map<String, AttributeValue> item, String name, String fallback) {
        AttributeValue value = item.get(name);
        return value == null || value.s() == null ? fallback : value.s();
    }

    private Long numberAttribute(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null || value.n() == null ? null : Long.valueOf(value.n());
    }

    private Integer integerAttribute(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null || value.n() == null ? null : Integer.valueOf(value.n());
    }
}
