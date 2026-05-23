package com.devashish.qca.fes.service;

import com.devashish.qca.fes.dto.ScanRequest;
import com.devashish.qca.fes.dto.ScanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ScanService {
    private static final String ACCEPTED = "ACCEPTED";
    private static final String DUPLICATE = "DUPLICATE";

    private final DynamoDbClient dynamoDbClient;
    private final String scanTableName;
    private final String idempotencyTableName;
    private final long idempotencyTtlHours;

    public ScanService(
            DynamoDbClient dynamoDbClient,
            @Value("${qca.dynamodb.scan-table-name}") String scanTableName,
            @Value("${qca.dynamodb.idempotency-table-name}") String idempotencyTableName,
            @Value("${qca.idempotency.ttl-hours}") long idempotencyTtlHours) {
        this.dynamoDbClient = dynamoDbClient;
        this.scanTableName = scanTableName;
        this.idempotencyTableName = idempotencyTableName;
        this.idempotencyTtlHours = idempotencyTtlHours;
    }

    public ScanResponse startScan(ScanRequest request) {
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

        putScanRecord(request, scanId, idempotencyKey, createdAt, expiresAt);

        return response(request, scanId, ACCEPTED, idempotencyKey, createdAt, expiresAt);
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
        Long expiresAt = numberAttribute(item, "expiresAt");

        return response(request, scanId, DUPLICATE, idempotencyKey, createdAt, expiresAt);
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
            long expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("scanId", stringValue(scanId));
        item.put("accountId", stringValue(request.accountId()));
        item.put("repoFullName", stringValue(request.repoFullName()));
        item.put("prNumber", numberValue(request.prNumber()));
        item.put("headSha", stringValue(request.headSha()));
        item.put("useCase", stringValue(request.useCase()));
        item.put("service", stringValue(request.service()));
        item.put("status", stringValue(ACCEPTED));
        item.put("idempotencyKey", stringValue(idempotencyKey));
        item.put("createdAt", stringValue(createdAt));
        item.put("updatedAt", stringValue(createdAt));
        item.put("createdAtScanId", stringValue(createdAt + "#" + scanId));
        item.put("idempotencyExpiresAt", numberValue(expiresAt));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(scanTableName)
                .item(item)
                .build());
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
            String createdAt,
            Long expiresAt) {
        return new ScanResponse(
                scanId,
                request.accountId(),
                request.repoFullName(),
                request.prNumber(),
                request.headSha(),
                request.useCase(),
                request.service(),
                status,
                idempotencyKey,
                createdAt,
                expiresAt);
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
}
