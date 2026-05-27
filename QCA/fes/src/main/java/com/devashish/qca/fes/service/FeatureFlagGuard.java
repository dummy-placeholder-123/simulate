package com.devashish.qca.fes.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeatureFlagGuard {
    private final AppConfigFeatureFlagsService featureFlagsService;

    public FeatureFlagGuard(AppConfigFeatureFlagsService featureFlagsService) {
        this.featureFlagsService = featureFlagsService;
    }

    public void requireCreateScanEnabled() {
        if (!featureFlagsService.isCreateScanEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "create-scan is disabled");
        }
    }

    public void requireStartScanEnabled() {
        if (!featureFlagsService.isStartScanEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "start-scan is disabled");
        }
    }
}
