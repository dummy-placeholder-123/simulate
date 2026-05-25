package com.devashish.qca.fes.dto;

public record ScanStatusResponse(
        String scanId,
        String status,
        String createdAt,
        String updatedAt,
        String queuedAt,
        boolean findingsAvailable
) {
}
