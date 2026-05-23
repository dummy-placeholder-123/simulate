package com.devashish.qca.fes.dto;

public record ScanResponse(
        String scanId,
        String accountId,
        String repoFullName,
        Integer prNumber,
        String headSha,
        String useCase,
        String service,
        String status,
        String idempotencyKey,
        String createdAt,
        Long expiresAt
) {
}
