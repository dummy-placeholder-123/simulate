package com.devashish.qca.fes.dto;

public record ScanListResponse(
        String scanId,
        String accountId,
        String repoFullName,
        Integer prNumber,
        String headSha,
        String useCase,
        String service,
        String status,
        String createdAt
) {
}
