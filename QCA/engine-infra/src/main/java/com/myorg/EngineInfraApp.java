package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public class EngineInfraApp {
    public static void main(final String[] args) {
        App app = new App();

        String stage = StageConfig.stage(app.getNode().tryGetContext("stage"));
        String stackId = "prod".equals(stage) ? "EngineInfraStack" : "EngineInfraStack-" + stage;

        new EngineInfraStack(app, stackId, StackProps.builder().build());

        app.synth();
    }
}
