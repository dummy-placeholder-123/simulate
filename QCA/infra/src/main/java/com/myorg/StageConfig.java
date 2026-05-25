package com.myorg;

import java.util.List;

public final class StageConfig {
    public static final String AWS_ACCOUNT_ID = "564061926474";
    public static final String AWS_REGION = "us-east-1";

    private StageConfig() {
    }

    public static String stage(Object contextValue) {
        String stage = contextValue == null ? "prod" : contextValue.toString();
        if (stage.isBlank()) {
            stage = "prod";
        }
        if (!List.of("dev", "qa", "prod").contains(stage)) {
            throw new IllegalArgumentException("stage must be one of: dev, qa, prod");
        }
        return stage;
    }

    public static String resourcePrefix(String stage) {
        return "prod".equals(stage) ? "qca" : "qca-" + stage;
    }
}
