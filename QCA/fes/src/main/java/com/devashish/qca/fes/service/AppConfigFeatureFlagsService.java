package com.devashish.qca.fes.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AppConfigFeatureFlagsService implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(AppConfigFeatureFlagsService.class);

    private final AppConfigDataClient appConfigDataClient;
    private final ObjectMapper objectMapper;
    private final String applicationId;
    private final String environmentId;
    private final String profileId;
    private final int pollSeconds;
    private final AtomicReference<FeatureFlags> currentFlags = new AtomicReference<>(FeatureFlags.defaults());
    private final AtomicReference<String> configurationToken = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "qca-appconfig-feature-flags");
        thread.setDaemon(true);
        return thread;
    });

    public AppConfigFeatureFlagsService(
            AppConfigDataClient appConfigDataClient,
            ObjectMapper objectMapper,
            @Value("${qca.appconfig.application-id}") String applicationId,
            @Value("${qca.appconfig.environment-id}") String environmentId,
            @Value("${qca.appconfig.profile-id}") String profileId,
            @Value("${qca.appconfig.poll-seconds}") int pollSeconds) {
        this.appConfigDataClient = appConfigDataClient;
        this.objectMapper = objectMapper;
        this.applicationId = applicationId;
        this.environmentId = environmentId;
        this.profileId = profileId;
        this.pollSeconds = Math.max(15, pollSeconds);
    }

    @PostConstruct
    void startPolling() {
        if (!isConfigured()) {
            log.info("AppConfig identifiers are not configured. Using default feature flags.");
            return;
        }

        refreshSafely();
        scheduler.scheduleWithFixedDelay(this::refreshSafely, pollSeconds, pollSeconds, TimeUnit.SECONDS);
    }

    public boolean isCreateScanEnabled() {
        return currentFlags.get().enableCreateScan();
    }

    public boolean isStartScanEnabled() {
        return currentFlags.get().enableStartScan();
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }

    private boolean isConfigured() {
        return hasText(applicationId) && hasText(environmentId) && hasText(profileId);
    }

    private void refreshSafely() {
        try {
            refreshFlags();
        } catch (Exception exception) {
            log.warn("Failed to refresh AppConfig feature flags. Keeping last known values.", exception);
        }
    }

    private void refreshFlags() throws Exception {
        String token = configurationToken.updateAndGet(existingToken -> {
            if (hasText(existingToken)) {
                return existingToken;
            }

            return appConfigDataClient.startConfigurationSession(StartConfigurationSessionRequest.builder()
                            .applicationIdentifier(applicationId)
                            .environmentIdentifier(environmentId)
                            .configurationProfileIdentifier(profileId)
                            .requiredMinimumPollIntervalInSeconds(pollSeconds)
                            .build())
                    .initialConfigurationToken();
        });

        var response = appConfigDataClient.getLatestConfiguration(GetLatestConfigurationRequest.builder()
                .configurationToken(token)
                .build());

        configurationToken.set(response.nextPollConfigurationToken());
        applyConfiguration(response.configuration());
    }

    private void applyConfiguration(SdkBytes configurationBytes) throws Exception {
        if (configurationBytes == null) {
            return;
        }

        byte[] payload = configurationBytes.asByteArray();
        if (payload.length == 0) {
            return;
        }

        JsonNode document = objectMapper.readTree(payload);
        currentFlags.set(new FeatureFlags(
                document.path("enableCreateScan").asBoolean(true),
                document.path("enableStartScan").asBoolean(true)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FeatureFlags(boolean enableCreateScan, boolean enableStartScan) {
        private static FeatureFlags defaults() {
            return new FeatureFlags(true, true);
        }
    }
}
