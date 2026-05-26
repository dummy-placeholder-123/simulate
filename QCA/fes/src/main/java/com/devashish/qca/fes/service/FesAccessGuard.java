package com.devashish.qca.fes.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class FesAccessGuard {
    private final AppConfigFeatureFlagsService featureFlagsService;
    private final byte[] expectedApiKeyBytes;

    public FesAccessGuard(
            AppConfigFeatureFlagsService featureFlagsService,
            @Value("${qca.security.api-key}") String expectedApiKey) {
        this.featureFlagsService = featureFlagsService;
        this.expectedApiKeyBytes = expectedApiKey.getBytes(StandardCharsets.UTF_8);
    }

    public void requireCreateScanAccess(String providedApiKey) {
        requireValidApiKey(providedApiKey);
        if (!featureFlagsService.isCreateScanEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "create-scan is disabled");
        }
    }

    public void requireStartScanAccess(String providedApiKey) {
        requireValidApiKey(providedApiKey);
        if (!featureFlagsService.isStartScanEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "start-scan is disabled");
        }
    }

    private void requireValidApiKey(String providedApiKey) {
        if (providedApiKey == null || providedApiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing api key");
        }

        byte[] providedApiKeyBytes = providedApiKey.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedApiKeyBytes, providedApiKeyBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid api key");
        }
    }
}
