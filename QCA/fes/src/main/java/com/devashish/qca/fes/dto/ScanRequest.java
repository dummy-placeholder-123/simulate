package com.devashish.qca.fes.dto;

public record ScanRequest(
        String scanId,
        String accountId,
        String repoFullName,
        Integer prNumber,
        String headSha,
        String useCase,
        String service
) {
}
