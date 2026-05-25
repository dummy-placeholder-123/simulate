package com.devashish.qca.fes.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ScanFindingsResponse(
        String scanId,
        String status,
        String resultBucketName,
        String resultObjectKey,
        JsonNode findings
) {
}
