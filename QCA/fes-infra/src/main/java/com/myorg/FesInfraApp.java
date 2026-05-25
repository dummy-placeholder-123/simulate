package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public class FesInfraApp {
    public static void main(final String[] args) {
        App app = new App();

        String stage = StageConfig.stage(app.getNode().tryGetContext("stage"));
        String stackId = "prod".equals(stage) ? "FesInfraStack" : "FesInfraStack-" + stage;

        new FesInfraStack(app, stackId, StackProps.builder().build());

        app.synth();
    }
}
