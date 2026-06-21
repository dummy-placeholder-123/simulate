package com.myorg;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.appconfig.Application;
import software.amazon.awscdk.services.appconfig.ApplicationProps;
import software.amazon.awscdk.services.appconfig.ConfigurationContent;
import software.amazon.awscdk.services.appconfig.ConfigurationType;
import software.amazon.awscdk.services.appconfig.DeploymentStrategyId;
import software.amazon.awscdk.services.appconfig.Environment;
import software.amazon.awscdk.services.appconfig.EnvironmentProps;
import software.amazon.awscdk.services.appconfig.HostedConfiguration;
import software.amazon.awscdk.services.appconfig.HostedConfigurationProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.cloudwatch.Alarm;
import software.amazon.awscdk.services.cloudwatch.AlarmProps;
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.cloudwatch.TreatMissingData;
import software.amazon.awscdk.services.cloudfront.AddBehaviorOptions;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.CachePolicy;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.DistributionProps;
import software.amazon.awscdk.services.cloudfront.OriginProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.OriginRequestPolicy;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.PasswordPolicy;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolClientProps;
import software.amazon.awscdk.services.cognito.UserPoolProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.CpuUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.CpuArchitecture;
import software.amazon.awscdk.services.ecs.CfnService;
import software.amazon.awscdk.services.ecs.DeploymentCircuitBreaker;
import software.amazon.awscdk.services.ecs.DeploymentStrategy;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.LoadBalancerTargetOptions;
import software.amazon.awscdk.services.ecs.MemoryUtilizationScalingProps;
import software.amazon.awscdk.services.ecs.OperatingSystemFamily;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.RuntimePlatform;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRule;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListenerRuleProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroupProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerAction;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCondition;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.cloudfront.origins.LoadBalancerV2Origin;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.stepfunctions.DefinitionBody;
import software.amazon.awscdk.services.stepfunctions.IntegrationPattern;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.CatchProps;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.StateMachineType;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.Timeout;
import software.amazon.awscdk.services.stepfunctions.Parallel;
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke;
import software.amazon.awscdk.services.stepfunctions.tasks.SqsSendMessage;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class InfraStack extends Stack {
    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String stage = StageConfig.stage(getNode().tryGetContext("stage"));
        String resourcePrefix = StageConfig.resourcePrefix(stage);
        String engineLogGroupName = "prod".equals(stage) ? "/qca/engine" : "/qca/" + stage + "/engine";
        String llmEngineLogGroupName = "prod".equals(stage) ? "/qca/llm-engine" : "/qca/" + stage + "/llm-engine";
        String fesLogGroupName = "prod".equals(stage) ? "/qca/fes" : "/qca/" + stage + "/fes";
        String scanQueueName = resourcePrefix + "-scan-queue";
        String llmScanQueueName = resourcePrefix + "-llm-scan-queue";
        String scanUploadBucketName = resourcePrefix + "-scan-uploads-" + StageConfig.AWS_ACCOUNT_ID + "-" + StageConfig.AWS_REGION;
        String scanTableName = resourcePrefix + "-scans";
        String fesConfigPathPrefix = "/qca/" + stage + "/fes";
        String fesAuthProvider = contextString("fesAuthProvider", "cognito");
        String fesCognitoIssuerUri = contextString("fesCognitoIssuerUri", "");
        String fesCognitoClientId = contextString("fesCognitoClientId", "");
        boolean isProd = "prod".equals(stage);
        boolean isDev = "dev".equals(stage);
        int engineDesiredCount = isProd ? 1 : 0;
        int engineMinCapacity = isProd ? 1 : 0;
        int llmEngineDesiredCount = isProd ? 1 : 0;
        int llmEngineMinCapacity = isProd ? 1 : 0;
        int fesDesiredCount = isProd ? 2 : 0;
        int fesMinCapacity = isProd ? 2 : 0;
        Distribution fesUiDistribution = null;

        Table scanTable = Table.Builder.create(this, "ScanTable")
                .tableName(scanTableName)
                .partitionKey(Attribute.builder()
                        .name("scanId")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        scanTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("accountScansIndex")
                .partitionKey(Attribute.builder()
                        .name("accountId")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("createdAtScanId")
                        .type(AttributeType.STRING)
                        .build())
                .projectionType(ProjectionType.ALL)
                .build());

        Table scanIdempotencyTable = Table.Builder.create(this, "ScanIdempotencyTable")
                .tableName(resourcePrefix + "-scan-idempotency")
                .partitionKey(Attribute.builder()
                        .name("idempotencyKey")
                        .type(AttributeType.STRING)
                        .build())
                .timeToLiveAttribute("expiresAt")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Bucket scanUploadBucket = Bucket.Builder.create(this, "ScanUploadBucket")
                .bucketName(scanUploadBucketName)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .cors(List.of(CorsRule.builder()
                        .allowedMethods(List.of(HttpMethods.PUT))
                        .allowedOrigins(List.of("*"))
                        .allowedHeaders(List.of("*"))
                        .build()))
                .encryption(BucketEncryption.S3_MANAGED)
                .enforceSsl(true)
                .versioned(false)
                .autoDeleteObjects(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        if (isDev) {
            String uiHostingBucketName = resourcePrefix + "-fes-ui-host-" + StageConfig.AWS_ACCOUNT_ID + "-" + StageConfig.AWS_REGION;
            String uiReleaseBucketName = resourcePrefix + "-fes-ui-releases-" + StageConfig.AWS_ACCOUNT_ID + "-" + StageConfig.AWS_REGION;

            Bucket fesUiHostingBucket = Bucket.Builder.create(this, "FesUiHostingBucket")
                    .bucketName(uiHostingBucketName)
                    .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                    .encryption(BucketEncryption.S3_MANAGED)
                    .enforceSsl(true)
                    .versioned(false)
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .autoDeleteObjects(true)
                    .build();

            Bucket fesUiReleaseBucket = Bucket.Builder.create(this, "FesUiReleaseBucket")
                    .bucketName(uiReleaseBucketName)
                    .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                    .encryption(BucketEncryption.S3_MANAGED)
                    .enforceSsl(true)
                    .versioned(false)
                    .removalPolicy(RemovalPolicy.DESTROY)
                    .autoDeleteObjects(true)
                    .build();

            fesUiDistribution = new Distribution(this, "FesUiDistribution",
                    DistributionProps.builder()
                            .defaultRootObject("index.html")
                            .defaultBehavior(software.amazon.awscdk.services.cloudfront.BehaviorOptions.builder()
                                    .origin(S3BucketOrigin.withOriginAccessControl(fesUiHostingBucket))
                                    .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                    .build())
                            .build());

            CfnOutput.Builder.create(this, "FesUiReleaseBucketName")
                    .value(fesUiReleaseBucket.getBucketName())
                    .build();

            CfnOutput.Builder.create(this, "FesUiHostingBucketName")
                    .value(fesUiHostingBucket.getBucketName())
                    .build();

            CfnOutput.Builder.create(this, "FesUiDistributionId")
                    .value(fesUiDistribution.getDistributionId())
                    .build();

            CfnOutput.Builder.create(this, "FesUiDistributionUrl")
                    .value("https://" + fesUiDistribution.getDistributionDomainName())
                    .build();
        }

        Queue scanDlq = Queue.Builder.create(this, "ScanDeadLetterQueue")
                .queueName(resourcePrefix + "-scan-dlq")
                .retentionPeriod(Duration.days(1))
                .build();

        Queue scanQueue = Queue.Builder.create(this, "ScanQueue")
                .queueName(scanQueueName)
                .visibilityTimeout(Duration.minutes(6))
                .retentionPeriod(Duration.days(1))
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(scanDlq)
                        .maxReceiveCount(3)
                        .build())
                .build();

        Queue llmScanDlq = Queue.Builder.create(this, "LlmScanDeadLetterQueue")
                .queueName(resourcePrefix + "-llm-scan-dlq")
                .retentionPeriod(Duration.days(1))
                .build();

        Queue llmScanQueue = Queue.Builder.create(this, "LlmScanQueue")
                .queueName(llmScanQueueName)
                .visibilityTimeout(Duration.minutes(6))
                .retentionPeriod(Duration.days(1))
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(llmScanDlq)
                        .maxReceiveCount(3)
                        .build())
                .build();

        SqsSendMessage sendStandardScanToQueue = SqsSendMessage.Builder.create(this, "SendStandardScanToQueue")
                .queue(scanQueue)
                .integrationPattern(IntegrationPattern.WAIT_FOR_TASK_TOKEN)
                .taskTimeout(Timeout.duration(Duration.minutes(3)))
                .resultPath("$.standardWorkerResult")
                .messageBody(TaskInput.fromObject(Map.ofEntries(
                        Map.entry("taskToken", JsonPath.getTaskToken()),
                        Map.entry("eventType", JsonPath.stringAt("$.eventType")),
                        Map.entry("scanId", JsonPath.stringAt("$.scanId")),
                        Map.entry("accountId", JsonPath.stringAt("$.accountId")),
                        Map.entry("repoFullName", JsonPath.stringAt("$.repoFullName")),
                        Map.entry("prNumber", JsonPath.numberAt("$.prNumber")),
                        Map.entry("headSha", JsonPath.stringAt("$.headSha")),
                        Map.entry("useCase", JsonPath.stringAt("$.useCase")),
                        Map.entry("service", JsonPath.stringAt("$.service")),
                        Map.entry("uploadBucketName", JsonPath.stringAt("$.uploadBucketName")),
                        Map.entry("uploadObjectKey", JsonPath.stringAt("$.uploadObjectKey")),
                        Map.entry("resultBucketName", JsonPath.stringAt("$.resultBucketName")),
                        Map.entry("resultObjectKey", JsonPath.stringAt("$.standardResultObjectKey")),
                        Map.entry("queuedAt", JsonPath.stringAt("$.queuedAt")))))
                .build();

        SqsSendMessage sendLlmScanToQueue = SqsSendMessage.Builder.create(this, "SendLlmScanToQueue")
                .queue(llmScanQueue)
                .integrationPattern(IntegrationPattern.WAIT_FOR_TASK_TOKEN)
                .taskTimeout(Timeout.duration(Duration.minutes(3)))
                .resultPath("$.llmWorkerResult")
                .messageBody(TaskInput.fromObject(Map.ofEntries(
                        Map.entry("taskToken", JsonPath.getTaskToken()),
                        Map.entry("eventType", JsonPath.stringAt("$.eventType")),
                        Map.entry("scanId", JsonPath.stringAt("$.scanId")),
                        Map.entry("accountId", JsonPath.stringAt("$.accountId")),
                        Map.entry("repoFullName", JsonPath.stringAt("$.repoFullName")),
                        Map.entry("prNumber", JsonPath.numberAt("$.prNumber")),
                        Map.entry("headSha", JsonPath.stringAt("$.headSha")),
                        Map.entry("useCase", JsonPath.stringAt("$.useCase")),
                        Map.entry("service", JsonPath.stringAt("$.service")),
                        Map.entry("uploadBucketName", JsonPath.stringAt("$.uploadBucketName")),
                        Map.entry("uploadObjectKey", JsonPath.stringAt("$.uploadObjectKey")),
                        Map.entry("resultBucketName", JsonPath.stringAt("$.resultBucketName")),
                        Map.entry("resultObjectKey", JsonPath.stringAt("$.llmResultObjectKey")),
                        Map.entry("queuedAt", JsonPath.stringAt("$.queuedAt")))))
                .build();

        Function findingsAggregator = Function.Builder.create(this, "FindingsAggregatorFunction")
                .functionName(resourcePrefix + "-findings-aggregator")
                .runtime(Runtime.PYTHON_3_12)
                .handler("index.handler")
                .code(Code.fromAsset("lambda/aggregate-findings"))
                .timeout(Duration.minutes(1))
                .environment(Map.of(
                        "SCAN_TABLE_NAME", scanTableName))
                .build();

        scanUploadBucket.grantReadWrite(findingsAggregator);
        scanTable.grantReadWriteData(findingsAggregator);

        Parallel runScanWorkers = Parallel.Builder.create(this, "RunScanWorkers")
                .resultPath("$.engineResults")
                .build();
        runScanWorkers.branch(sendStandardScanToQueue);
        runScanWorkers.branch(sendLlmScanToQueue);

        LambdaInvoke finalizeFailedScan = LambdaInvoke.Builder.create(this, "FinalizeFailedScan")
                .lambdaFunction(findingsAggregator)
                .payload(TaskInput.fromObject(Map.ofEntries(
                        Map.entry("scanId", JsonPath.stringAt("$.scanId")),
                        Map.entry("accountId", JsonPath.stringAt("$.accountId")),
                        Map.entry("resultBucketName", JsonPath.stringAt("$.resultBucketName")),
                        Map.entry("resultObjectKey", JsonPath.stringAt("$.resultObjectKey")),
                        Map.entry("standardResultObjectKey", JsonPath.stringAt("$.standardResultObjectKey")),
                        Map.entry("llmResultObjectKey", JsonPath.stringAt("$.llmResultObjectKey")),
                        Map.entry("finalStatus", "FAILED"),
                        Map.entry("failureError", JsonPath.stringAt("$.workerFailure.Error")),
                        Map.entry("failureCause", JsonPath.stringAt("$.workerFailure.Cause")))))
                .resultPath("$.finalizationResult")
                .build();

        LambdaInvoke aggregateFindings = LambdaInvoke.Builder.create(this, "AggregateFindings")
                .lambdaFunction(findingsAggregator)
                .payload(TaskInput.fromObject(Map.ofEntries(
                        Map.entry("scanId", JsonPath.stringAt("$.scanId")),
                        Map.entry("accountId", JsonPath.stringAt("$.accountId")),
                        Map.entry("resultBucketName", JsonPath.stringAt("$.resultBucketName")),
                        Map.entry("resultObjectKey", JsonPath.stringAt("$.resultObjectKey")),
                        Map.entry("standardResultObjectKey", JsonPath.stringAt("$.standardResultObjectKey")),
                        Map.entry("llmResultObjectKey", JsonPath.stringAt("$.llmResultObjectKey")))))
                .resultPath("$.aggregationResult")
                .build();

        runScanWorkers.addCatch(finalizeFailedScan, CatchProps.builder()
                .resultPath("$.workerFailure")
                .build());

        aggregateFindings.addCatch(finalizeFailedScan, CatchProps.builder()
                .resultPath("$.workerFailure")
                .build());

        StateMachine scanStateMachine = StateMachine.Builder.create(this, "ScanStateMachine")
                .stateMachineName(resourcePrefix + "-scan-state-machine")
                .definitionBody(DefinitionBody.fromChainable(runScanWorkers.next(aggregateFindings)))
                .stateMachineType(StateMachineType.STANDARD)
                .build();

        UserPool fesUserPool = new UserPool(this, "FesUserPool",
                UserPoolProps.builder()
                        .userPoolName(resourcePrefix + "-fes-users")
                        .selfSignUpEnabled(false)
                        .signInAliases(SignInAliases.builder()
                                .username(true)
                                .build())
                        .signInCaseSensitive(false)
                        .passwordPolicy(PasswordPolicy.builder()
                                .minLength(8)
                                .requireDigits(false)
                                .requireLowercase(false)
                                .requireSymbols(false)
                                .requireUppercase(false)
                                .build())
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .build());

        UserPoolClient fesUserPoolClient = new UserPoolClient(this, "FesUserPoolClient",
                UserPoolClientProps.builder()
                        .userPool(fesUserPool)
                        .userPoolClientName(resourcePrefix + "-fes-ui")
                        .generateSecret(false)
                        .authFlows(AuthFlow.builder()
                                .userPassword(true)
                                .userSrp(true)
                                .build())
                        .accessTokenValidity(Duration.minutes(15))
                        .idTokenValidity(Duration.minutes(15))
                        .refreshTokenValidity(Duration.hours(6))
                        .build());

        String createdFesCognitoIssuerUri = "https://cognito-idp."
                + StageConfig.AWS_REGION
                + ".amazonaws.com/"
                + fesUserPool.getUserPoolId();
        String effectiveFesCognitoIssuerUri = fesCognitoIssuerUri.isBlank()
                ? createdFesCognitoIssuerUri
                : fesCognitoIssuerUri;
        String effectiveFesCognitoClientId = fesCognitoClientId.isBlank()
                ? fesUserPoolClient.getUserPoolClientId()
                : fesCognitoClientId;

        CfnOutput.Builder.create(this, "FesCognitoUserPoolId")
                .value(fesUserPool.getUserPoolId())
                .build();

        CfnOutput.Builder.create(this, "FesCognitoClientId")
                .value(fesUserPoolClient.getUserPoolClientId())
                .build();

        CfnOutput.Builder.create(this, "FesCognitoIssuerUri")
                .value(createdFesCognitoIssuerUri)
                .build();

        Secret fesDemoPasswordSecret = Secret.Builder.create(this, "FesDemoPasswordSecret")
                .secretName(fesConfigPathPrefix + "/demo-user-password")
                .description("Password for the demo FES operator account")
                .generateSecretString(SecretStringGenerator.builder()
                        .excludePunctuation(true)
                        .passwordLength(24)
                        .build())
                .build();

        Secret fesJwtSigningSecret = Secret.Builder.create(this, "FesJwtSigningSecret")
                .secretName(fesConfigPathPrefix + "/jwt-signing-key")
                .description("JWT signing key used by FES for access and refresh tokens")
                .generateSecretString(SecretStringGenerator.builder()
                        .excludePunctuation(true)
                        .passwordLength(64)
                        .build())
                .build();

        StringParameter fesPresignedUrlDurationParameter = StringParameter.Builder.create(this, "FesPresignedUrlDurationParameter")
                .parameterName(fesConfigPathPrefix + "/presigned-url-duration-minutes")
                .description("Presigned upload URL duration for FES, in minutes")
                .stringValue("5")
                .build();

        Application fesAppConfigApplication = new Application(this, "FesAppConfigApplication",
                ApplicationProps.builder()
                        .applicationName(resourcePrefix + "-fes-config")
                        .description("Runtime feature flags for FES")
                        .build());

        Environment fesAppConfigEnvironment = new Environment(this, "FesAppConfigEnvironment",
                EnvironmentProps.builder()
                        .application(fesAppConfigApplication)
                        .environmentName(stage)
                        .description("FES runtime flags for " + stage)
                        .build());

        HostedConfiguration fesAppConfigHostedConfiguration = new HostedConfiguration(this, "FesAppConfigHostedConfiguration",
                HostedConfigurationProps.builder()
                        .application(fesAppConfigApplication)
                        .deployTo(List.of(fesAppConfigEnvironment))
                        .deploymentStrategy(software.amazon.awscdk.services.appconfig.DeploymentStrategy
                                .fromDeploymentStrategyId(this, "FesAppConfigAllAtOnce", DeploymentStrategyId.ALL_AT_ONCE))
                        .name(resourcePrefix + "-fes-flags")
                        .description("Feature flags for create-scan and start-scan")
                        .type(ConfigurationType.FREEFORM)
                        .content(ConfigurationContent.fromInlineJson("""
                                {
                                  "enableCreateScan": true,
                                  "enableStartScan": true
                                }
                                """))
                        .versionLabel("v1")
                        .build());

        Vpc vpc = Vpc.Builder.create(this, "WorkerVpc")
                .vpcName(resourcePrefix + "-worker-vpc")
                .maxAzs(2)
                .natGateways(0)
                .subnetConfiguration(List.of(SubnetConfiguration.builder()
                        .name("Public")
                        .subnetType(SubnetType.PUBLIC)
                        .cidrMask(24)
                        .build()))
                .build();

        Cluster cluster = Cluster.Builder.create(this, "WorkerCluster")
                .clusterName(resourcePrefix + "-worker-cluster")
                .vpc(vpc)
                .build();

        LogGroup engineLogGroup = LogGroup.Builder.create(this, "EngineLogGroup")
                .logGroupName(engineLogGroupName)
                .retention(RetentionDays.ONE_DAY)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        LogGroup llmEngineLogGroup = LogGroup.Builder.create(this, "LlmEngineLogGroup")
                .logGroupName(llmEngineLogGroupName)
                .retention(RetentionDays.ONE_DAY)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        LogGroup fesLogGroup = LogGroup.Builder.create(this, "FesLogGroup")
                .logGroupName(fesLogGroupName)
                .retention(RetentionDays.ONE_DAY)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Role engineExecutionRole = Role.Builder.create(this, "EngineTaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(ManagedPolicy.fromAwsManagedPolicyName(
                        "service-role/AmazonECSTaskExecutionRolePolicy")))
                .build();

        Role fesExecutionRole = Role.Builder.create(this, "FesTaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(ManagedPolicy.fromAwsManagedPolicyName(
                        "service-role/AmazonECSTaskExecutionRolePolicy")))
                .build();

        fesDemoPasswordSecret.grantRead(fesExecutionRole);
        fesJwtSigningSecret.grantRead(fesExecutionRole);
        fesPresignedUrlDurationParameter.grantRead(fesExecutionRole);

        Role fesBlueGreenInfrastructureRole = Role.Builder.create(this, "FesBlueGreenInfrastructureRole")
                .assumedBy(new ServicePrincipal("ecs.amazonaws.com"))
                .managedPolicies(List.of(ManagedPolicy.fromAwsManagedPolicyName(
                        "AmazonECSInfrastructureRolePolicyForLoadBalancers")))
                .build();

        Role llmEngineExecutionRole = Role.Builder.create(this, "LlmEngineTaskExecutionRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .managedPolicies(List.of(ManagedPolicy.fromAwsManagedPolicyName(
                        "service-role/AmazonECSTaskExecutionRolePolicy")))
                .build();

        FargateTaskDefinition engineTaskDefinition = FargateTaskDefinition.Builder.create(this, "EngineTaskDefinition")
                .family(resourcePrefix + "-engine")
                .cpu(256)
                .memoryLimitMiB(512)
                .executionRole(engineExecutionRole)
                .runtimePlatform(RuntimePlatform.builder()
                        .cpuArchitecture(CpuArchitecture.X86_64)
                        .operatingSystemFamily(OperatingSystemFamily.LINUX)
                        .build())
                .build();

        FargateTaskDefinition fesTaskDefinition = FargateTaskDefinition.Builder.create(this, "FesTaskDefinition")
                .family(resourcePrefix + "-fes")
                .cpu(512)
                .memoryLimitMiB(1024)
                .executionRole(fesExecutionRole)
                .runtimePlatform(RuntimePlatform.builder()
                        .cpuArchitecture(CpuArchitecture.X86_64)
                        .operatingSystemFamily(OperatingSystemFamily.LINUX)
                        .build())
                .build();

        FargateTaskDefinition llmEngineTaskDefinition = FargateTaskDefinition.Builder.create(this, "LlmEngineTaskDefinition")
                .family(resourcePrefix + "-llm-engine")
                .cpu(256)
                .memoryLimitMiB(512)
                .executionRole(llmEngineExecutionRole)
                .runtimePlatform(RuntimePlatform.builder()
                        .cpuArchitecture(CpuArchitecture.X86_64)
                        .operatingSystemFamily(OperatingSystemFamily.LINUX)
                        .build())
                .build();

        scanQueue.grantConsumeMessages(engineTaskDefinition.getTaskRole());
        scanUploadBucket.grantReadWrite(engineTaskDefinition.getTaskRole());
        scanTable.grantWriteData(engineTaskDefinition.getTaskRole());
        engineTaskDefinition.getTaskRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "states:SendTaskSuccess",
                        "states:SendTaskFailure",
                        "states:SendTaskHeartbeat"))
                .resources(List.of("*"))
                .build());

        engineTaskDefinition.addContainer("EngineContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("public.ecr.aws/amazonlinux/amazonlinux:latest"))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(engineLogGroup)
                        .streamPrefix("engine")
                        .build()))
                .environment(Map.of(
                        "AWS_REGION", StageConfig.AWS_REGION,
                        "SCAN_QUEUE_URL", scanQueue.getQueueUrl(),
                        "SCAN_TABLE_NAME", scanTableName,
                        "SCAN_UPLOAD_BUCKET_NAME", scanUploadBucketName,
                        "SCAN_RESULT_BUCKET_NAME", scanUploadBucketName,
                        "WORKER_KIND", "STANDARD",
                        "PROCESSING_SECONDS", "60"))
                .build());

        llmScanQueue.grantConsumeMessages(llmEngineTaskDefinition.getTaskRole());
        scanUploadBucket.grantReadWrite(llmEngineTaskDefinition.getTaskRole());
        scanTable.grantWriteData(llmEngineTaskDefinition.getTaskRole());
        llmEngineTaskDefinition.getTaskRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "states:SendTaskSuccess",
                        "states:SendTaskFailure",
                        "states:SendTaskHeartbeat"))
                .resources(List.of("*"))
                .build());

        llmEngineTaskDefinition.addContainer("EngineContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("public.ecr.aws/amazonlinux/amazonlinux:latest"))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(llmEngineLogGroup)
                        .streamPrefix("llm-engine")
                        .build()))
                .environment(Map.of(
                        "AWS_REGION", StageConfig.AWS_REGION,
                        "SCAN_QUEUE_URL", llmScanQueue.getQueueUrl(),
                        "SCAN_TABLE_NAME", scanTableName,
                        "SCAN_UPLOAD_BUCKET_NAME", scanUploadBucketName,
                        "SCAN_RESULT_BUCKET_NAME", scanUploadBucketName,
                        "WORKER_KIND", "LLM",
                        "PROCESSING_SECONDS", "60"))
                .build());

        scanTable.grantReadWriteData(fesTaskDefinition.getTaskRole());
        scanIdempotencyTable.grantReadWriteData(fesTaskDefinition.getTaskRole());
        scanUploadBucket.grantReadWrite(fesTaskDefinition.getTaskRole());
        scanStateMachine.grantStartExecution(fesTaskDefinition.getTaskRole());
        fesTaskDefinition.getTaskRole().addToPrincipalPolicy(PolicyStatement.Builder.create()
                .actions(List.of(
                        "appconfigdata:StartConfigurationSession",
                        "appconfigdata:GetLatestConfiguration"))
                .resources(List.of("*"))
                .build());

        fesTaskDefinition.addContainer("FesContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("public.ecr.aws/amazonlinux/amazonlinux:latest"))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(fesLogGroup)
                        .streamPrefix("fes")
                        .build()))
                .portMappings(List.of(PortMapping.builder()
                        .containerPort(8080)
                        .build()))
                .environment(Map.ofEntries(
                        Map.entry("AWS_REGION", StageConfig.AWS_REGION),
                        Map.entry("QCA_DYNAMODB_SCAN_TABLE_NAME", scanTableName),
                        Map.entry("QCA_DYNAMODB_IDEMPOTENCY_TABLE_NAME", scanIdempotencyTable.getTableName()),
                        Map.entry("QCA_S3_SCAN_UPLOAD_BUCKET_NAME", scanUploadBucketName),
                        Map.entry("QCA_STEP_FUNCTIONS_SCAN_STATE_MACHINE_ARN", scanStateMachine.getStateMachineArn()),
                        Map.entry("QCA_IDEMPOTENCY_TTL_HOURS", "24"),
                        Map.entry("QCA_AUTH_PROVIDER", fesAuthProvider),
                        Map.entry("QCA_COGNITO_ISSUER_URI", effectiveFesCognitoIssuerUri),
                        Map.entry("QCA_COGNITO_CLIENT_ID", effectiveFesCognitoClientId),
                        Map.entry("QCA_FES_DEMO_USERNAME", "qca-admin"),
                        Map.entry("QCA_JWT_ACCESS_TTL_SECONDS", "900"),
                        Map.entry("QCA_JWT_REFRESH_TTL_SECONDS", "604800"),
                        Map.entry("QCA_APPCONFIG_APPLICATION_ID", fesAppConfigApplication.getApplicationId()),
                        Map.entry("QCA_APPCONFIG_ENVIRONMENT_ID", fesAppConfigEnvironment.getEnvironmentId()),
                        Map.entry("QCA_APPCONFIG_PROFILE_ID", fesAppConfigHostedConfiguration.getConfigurationProfileId()),
                        Map.entry("QCA_APPCONFIG_POLL_SECONDS", "30")))
                .secrets(Map.of(
                        "QCA_FES_DEMO_PASSWORD", software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(fesDemoPasswordSecret),
                        "QCA_JWT_SIGNING_KEY", software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(fesJwtSigningSecret),
                        "QCA_S3_PRESIGNED_URL_DURATION_MINUTES", software.amazon.awscdk.services.ecs.Secret.fromSsmParameter(fesPresignedUrlDurationParameter)))
                .build());

        FargateService engineService = FargateService.Builder.create(this, "EngineService")
                .serviceName(resourcePrefix + "-engine-service")
                .cluster(cluster)
                .taskDefinition(engineTaskDefinition)
                .desiredCount(engineDesiredCount)
                .assignPublicIp(true)
                .circuitBreaker(DeploymentCircuitBreaker.builder()
                        .rollback(true)
                        .build())
                .minHealthyPercent(100)
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build();

        FargateService llmEngineService = FargateService.Builder.create(this, "LlmEngineService")
                .serviceName(resourcePrefix + "-llm-engine-service")
                .cluster(cluster)
                .taskDefinition(llmEngineTaskDefinition)
                .desiredCount(llmEngineDesiredCount)
                .assignPublicIp(true)
                .circuitBreaker(DeploymentCircuitBreaker.builder()
                        .rollback(true)
                        .build())
                .minHealthyPercent(100)
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build();

        SecurityGroup fesAlbSecurityGroup = SecurityGroup.Builder.create(this, "FesAlbSecurityGroup")
                .vpc(vpc)
                .allowAllOutbound(true)
                .description("Security group for the public FES load balancer")
                .build();
        fesAlbSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "Allow HTTP");
        fesAlbSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(9000), "Allow FES green test traffic");

        SecurityGroup fesServiceSecurityGroup = SecurityGroup.Builder.create(this, "FesServiceSecurityGroup")
                .vpc(vpc)
                .allowAllOutbound(true)
                .description("Security group for the FES ECS service")
                .build();
        fesServiceSecurityGroup.addIngressRule(fesAlbSecurityGroup, Port.tcp(8080), "Allow ALB to reach FES");

        FargateService fesService = FargateService.Builder.create(this, "FesService")
                .serviceName(resourcePrefix + "-fes-service")
                .cluster(cluster)
                .taskDefinition(fesTaskDefinition)
                .deploymentStrategy(DeploymentStrategy.BLUE_GREEN)
                .bakeTime(Duration.minutes(5))
                .desiredCount(fesDesiredCount)
                .assignPublicIp(true)
                .securityGroups(List.of(fesServiceSecurityGroup))
                .minHealthyPercent(100)
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build();

        ApplicationLoadBalancer fesLoadBalancer = ApplicationLoadBalancer.Builder.create(this, "FesLoadBalancer")
                .loadBalancerName(resourcePrefix + "-fes-alb")
                .vpc(vpc)
                .internetFacing(true)
                .securityGroup(fesAlbSecurityGroup)
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build();

        if (isDev && fesUiDistribution != null) {
            fesUiDistribution.addBehavior("/api/*",
                    LoadBalancerV2Origin.Builder.create(fesLoadBalancer)
                            .protocolPolicy(OriginProtocolPolicy.HTTP_ONLY)
                            .httpPort(80)
                            .build(),
                    AddBehaviorOptions.builder()
                            .allowedMethods(AllowedMethods.ALLOW_ALL)
                            .cachePolicy(CachePolicy.CACHING_DISABLED)
                            .originRequestPolicy(OriginRequestPolicy.ALL_VIEWER)
                            .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                            .build());
        }

        ApplicationListener fesHttpListener = fesLoadBalancer.addListener("FesHttpListener",
                BaseApplicationListenerProps.builder()
                        .port(80)
                        .defaultAction(ListenerAction.fixedResponse(404))
                        .protocol(ApplicationProtocol.HTTP)
                        .open(false)
                        .build());

        ApplicationListener fesTestListener = fesLoadBalancer.addListener("FesTestListener",
                BaseApplicationListenerProps.builder()
                        .port(9000)
                        .defaultAction(ListenerAction.fixedResponse(404))
                        .protocol(ApplicationProtocol.HTTP)
                        .open(false)
                        .build());

        ApplicationTargetGroup fesBlueTargetGroup = new ApplicationTargetGroup(this, "FesBlueTargetGroup",
                ApplicationTargetGroupProps.builder()
                        .vpc(vpc)
                        .port(8080)
                        .protocol(ApplicationProtocol.HTTP)
                        .targetType(TargetType.IP)
                        .healthCheck(HealthCheck.builder()
                                .path("/actuator/health")
                                .healthyHttpCodes("200")
                                .build())
                        .build());

        ApplicationTargetGroup fesGreenTargetGroup = new ApplicationTargetGroup(this, "FesGreenTargetGroup",
                ApplicationTargetGroupProps.builder()
                        .vpc(vpc)
                        .port(8080)
                        .protocol(ApplicationProtocol.HTTP)
                        .targetType(TargetType.IP)
                        .healthCheck(HealthCheck.builder()
                                .path("/actuator/health")
                                .healthyHttpCodes("200")
                                .build())
                        .build());

        ApplicationListenerRule fesProductionRule = new ApplicationListenerRule(this, "FesProductionRule",
                ApplicationListenerRuleProps.builder()
                        .listener(fesHttpListener)
                        .priority(100)
                        .conditions(List.of(ListenerCondition.pathPatterns(List.of("/*"))))
                        .action(ListenerAction.forward(List.of(fesBlueTargetGroup)))
                        .build());

        ApplicationListenerRule fesTestRule = new ApplicationListenerRule(this, "FesTestRule",
                ApplicationListenerRuleProps.builder()
                        .listener(fesTestListener)
                        .priority(100)
                        .conditions(List.of(ListenerCondition.pathPatterns(List.of("/*"))))
                        .action(ListenerAction.forward(List.of(fesGreenTargetGroup)))
                        .build());

        CfnService fesServiceResource = (CfnService) fesService.getNode().getDefaultChild();
        fesServiceResource.setLoadBalancers(List.of(CfnService.LoadBalancerProperty.builder()
                .containerName("FesContainer")
                .containerPort(8080)
                .targetGroupArn(fesBlueTargetGroup.getTargetGroupArn())
                .advancedConfiguration(CfnService.AdvancedConfigurationProperty.builder()
                        .alternateTargetGroupArn(fesGreenTargetGroup.getTargetGroupArn())
                        .productionListenerRule(fesProductionRule.getListenerRuleArn())
                        .testListenerRule(fesTestRule.getListenerRuleArn())
                        .roleArn(fesBlueGreenInfrastructureRole.getRoleArn())
                        .build())
                .build()));

        CfnOutput.Builder.create(this, "FesAlbDnsName")
                .value(fesLoadBalancer.getLoadBalancerDnsName())
                .build();

        CfnOutput.Builder.create(this, "FesAlbGreenTestUrl")
                .value("http://" + fesLoadBalancer.getLoadBalancerDnsName() + ":9000")
                .build();

        CfnOutput.Builder.create(this, "FesDemoUsername")
                .value("qca-admin")
                .build();

        CfnOutput.Builder.create(this, "FesDemoPasswordSecretName")
                .value(fesDemoPasswordSecret.getSecretName())
                .build();

        CfnOutput.Builder.create(this, "FesJwtSigningSecretName")
                .value(fesJwtSigningSecret.getSecretName())
                .build();

        CfnOutput.Builder.create(this, "FesPresignedUrlDurationParameterName")
                .value(fesPresignedUrlDurationParameter.getParameterName())
                .build();

        CfnOutput.Builder.create(this, "FesAppConfigApplicationId")
                .value(fesAppConfigApplication.getApplicationId())
                .build();

        CfnOutput.Builder.create(this, "FesAppConfigEnvironmentId")
                .value(fesAppConfigEnvironment.getEnvironmentId())
                .build();

        CfnOutput.Builder.create(this, "FesAppConfigProfileId")
                .value(fesAppConfigHostedConfiguration.getConfigurationProfileId())
                .build();

        ScalableTaskCount engineScaling = engineService.autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(engineMinCapacity)
                .maxCapacity(5)
                .build());

        engineScaling.scaleOnMetric("QueueDepthScaling",
                software.amazon.awscdk.services.applicationautoscaling.BasicStepScalingPolicyProps.builder()
                        .metric(Metric.Builder.create()
                                .namespace("AWS/SQS")
                                .metricName("ApproximateNumberOfMessagesVisible")
                                .dimensionsMap(Map.of("QueueName", scanQueueName))
                                .statistic("Average")
                                .period(Duration.minutes(1))
                                .build())
                        .scalingSteps(List.of(
                                software.amazon.awscdk.services.applicationautoscaling.ScalingInterval.builder()
                                        .upper(0)
                                        .change(-1)
                                        .build(),
                                software.amazon.awscdk.services.applicationautoscaling.ScalingInterval.builder()
                                        .lower(1)
                                        .change(+1)
                                        .build(),
                                software.amazon.awscdk.services.applicationautoscaling.ScalingInterval.builder()
                                        .lower(10)
                                        .change(+2)
                                        .build()))
                        .adjustmentType(software.amazon.awscdk.services.applicationautoscaling.AdjustmentType.CHANGE_IN_CAPACITY)
                        .cooldown(Duration.minutes(1))
                        .build());

        ScalableTaskCount fesScaling = fesService.autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(fesMinCapacity)
                .maxCapacity(5)
                .build());

        ScalableTaskCount llmEngineScaling = llmEngineService.autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(llmEngineMinCapacity)
                .maxCapacity(5)
                .build());

        llmEngineScaling.scaleOnMetric("LlmQueueDepthScaling",
                software.amazon.awscdk.services.applicationautoscaling.BasicStepScalingPolicyProps.builder()
                        .metric(Metric.Builder.create()
                                .namespace("AWS/SQS")
                                .metricName("ApproximateNumberOfMessagesVisible")
                                .dimensionsMap(Map.of("QueueName", llmScanQueueName))
                                .statistic("Average")
                                .period(Duration.minutes(1))
                                .build())
                        .scalingSteps(List.of(
                                software.amazon.awscdk.services.applicationautoscaling.ScalingInterval.builder()
                                        .upper(0)
                                        .change(-1)
                                        .build(),
                                software.amazon.awscdk.services.applicationautoscaling.ScalingInterval.builder()
                                        .lower(1)
                                        .change(+1)
                                        .build(),
                                software.amazon.awscdk.services.applicationautoscaling.ScalingInterval.builder()
                                        .lower(10)
                                        .change(+2)
                                        .build()))
                        .adjustmentType(software.amazon.awscdk.services.applicationautoscaling.AdjustmentType.CHANGE_IN_CAPACITY)
                        .cooldown(Duration.minutes(1))
                        .build());

        fesScaling.scaleOnCpuUtilization("FesCpuScaling",
                CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(60)
                        .scaleInCooldown(Duration.minutes(2))
                        .scaleOutCooldown(Duration.minutes(1))
                        .build());

        fesScaling.scaleOnMemoryUtilization("FesMemoryScaling",
                MemoryUtilizationScalingProps.builder()
                        .targetUtilizationPercent(70)
                        .scaleInCooldown(Duration.minutes(2))
                        .scaleOutCooldown(Duration.minutes(1))
                        .build());

        Alarm fesAlb5xxAlarm = new Alarm(this, "FesAlb5xxAlarm", AlarmProps.builder()
                .alarmName(resourcePrefix + "-fes-alb-5xx")
                .alarmDescription("FES ALB 5xx responses detected")
                .metric(Metric.Builder.create()
                        .namespace("AWS/ApplicationELB")
                        .metricName("HTTPCode_ELB_5XX_Count")
                        .dimensionsMap(Map.of("LoadBalancer", fesLoadBalancer.getLoadBalancerFullName()))
                        .statistic("Sum")
                        .period(Duration.minutes(1))
                        .build())
                .threshold(1)
                .evaluationPeriods(1)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build());

        Alarm fesTargetResponseTimeAlarm = new Alarm(this, "FesTargetResponseTimeAlarm", AlarmProps.builder()
                .alarmName(resourcePrefix + "-fes-target-response-time")
                .alarmDescription("FES target response time is elevated")
                .metric(Metric.Builder.create()
                        .namespace("AWS/ApplicationELB")
                        .metricName("TargetResponseTime")
                        .dimensionsMap(Map.of(
                                "LoadBalancer", fesLoadBalancer.getLoadBalancerFullName(),
                                "TargetGroup", fesBlueTargetGroup.getTargetGroupFullName()))
                        .statistic("Average")
                        .period(Duration.minutes(1))
                        .build())
                .threshold(2)
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build());

        Alarm fesRunningTaskCountAboveTwoAlarm = new Alarm(this, "FesRunningTaskCountAboveTwoAlarm", AlarmProps.builder()
                .alarmName(resourcePrefix + "-fes-running-task-count-above-two")
                .alarmDescription("FES service is running more than two tasks")
                .metric(Metric.Builder.create()
                        .namespace("AWS/ECS")
                        .metricName("RunningTaskCount")
                        .dimensionsMap(Map.of(
                                "ClusterName", cluster.getClusterName(),
                                "ServiceName", resourcePrefix + "-fes-service"))
                        .statistic("Average")
                        .period(Duration.minutes(1))
                        .build())
                .threshold(2)
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .treatMissingData(TreatMissingData.NOT_BREACHING)
                .build());

        CfnOutput.Builder.create(this, "FesAlb5xxAlarmName")
                .value(fesAlb5xxAlarm.getAlarmName())
                .build();

        CfnOutput.Builder.create(this, "FesTargetResponseTimeAlarmName")
                .value(fesTargetResponseTimeAlarm.getAlarmName())
                .build();

        CfnOutput.Builder.create(this, "FesRunningTaskCountAboveTwoAlarmName")
                .value(fesRunningTaskCountAboveTwoAlarm.getAlarmName())
                .build();
    }

    private String contextString(String key, String defaultValue) {
        Object value = getNode().tryGetContext(key);
        if (value == null) {
            return defaultValue;
        }
        String stringValue = value.toString();
        return stringValue.isBlank() ? defaultValue : stringValue;
    }
}
