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
import software.amazon.awscdk.services.cloudwatch.Metric;
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
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.stepfunctions.DefinitionBody;
import software.amazon.awscdk.services.stepfunctions.IntegrationPattern;
import software.amazon.awscdk.services.stepfunctions.JsonPath;
import software.amazon.awscdk.services.stepfunctions.StateMachine;
import software.amazon.awscdk.services.stepfunctions.StateMachineType;
import software.amazon.awscdk.services.stepfunctions.TaskInput;
import software.amazon.awscdk.services.stepfunctions.Timeout;
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
        String fesLogGroupName = "prod".equals(stage) ? "/qca/fes" : "/qca/" + stage + "/fes";
        String scanQueueName = resourcePrefix + "-scan-queue";
        String scanUploadBucketName = resourcePrefix + "-scan-uploads-" + StageConfig.AWS_ACCOUNT_ID + "-" + StageConfig.AWS_REGION;
        String scanTableName = resourcePrefix + "-scans";
        String fesConfigPathPrefix = "/qca/" + stage + "/fes";
        boolean isProd = "prod".equals(stage);
        int engineDesiredCount = isProd ? 1 : 0;
        int engineMinCapacity = isProd ? 1 : 0;
        int fesDesiredCount = isProd ? 2 : 0;
        int fesMinCapacity = isProd ? 2 : 0;

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

        SqsSendMessage sendScanToQueue = SqsSendMessage.Builder.create(this, "SendScanToQueue")
                .queue(scanQueue)
                .integrationPattern(IntegrationPattern.WAIT_FOR_TASK_TOKEN)
                .taskTimeout(Timeout.duration(Duration.minutes(6)))
                .resultPath("$.workerResult")
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
                        Map.entry("resultObjectKey", JsonPath.stringAt("$.resultObjectKey")),
                        Map.entry("queuedAt", JsonPath.stringAt("$.queuedAt")))))
                .build();

        StateMachine scanStateMachine = StateMachine.Builder.create(this, "ScanStateMachine")
                .stateMachineName(resourcePrefix + "-scan-state-machine")
                .definitionBody(DefinitionBody.fromChainable(sendScanToQueue))
                .stateMachineType(StateMachineType.STANDARD)
                .build();

        Secret fesApiKeySecret = Secret.Builder.create(this, "FesApiKeySecret")
                .secretName(fesConfigPathPrefix + "/api-key")
                .description("Shared API key required for mutating FES endpoints")
                .generateSecretString(SecretStringGenerator.builder()
                        .excludePunctuation(true)
                        .passwordLength(32)
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

        fesApiKeySecret.grantRead(fesExecutionRole);
        fesPresignedUrlDurationParameter.grantRead(fesExecutionRole);

        Role fesBlueGreenInfrastructureRole = Role.Builder.create(this, "FesBlueGreenInfrastructureRole")
                .assumedBy(new ServicePrincipal("ecs.amazonaws.com"))
                .managedPolicies(List.of(ManagedPolicy.fromAwsManagedPolicyName(
                        "AmazonECSInfrastructureRolePolicyForLoadBalancers")))
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
                .environment(Map.of(
                        "AWS_REGION", StageConfig.AWS_REGION,
                        "QCA_DYNAMODB_SCAN_TABLE_NAME", scanTableName,
                        "QCA_DYNAMODB_IDEMPOTENCY_TABLE_NAME", scanIdempotencyTable.getTableName(),
                        "QCA_S3_SCAN_UPLOAD_BUCKET_NAME", scanUploadBucketName,
                        "QCA_STEP_FUNCTIONS_SCAN_STATE_MACHINE_ARN", scanStateMachine.getStateMachineArn(),
                        "QCA_IDEMPOTENCY_TTL_HOURS", "24",
                        "QCA_APPCONFIG_APPLICATION_ID", fesAppConfigApplication.getApplicationId(),
                        "QCA_APPCONFIG_ENVIRONMENT_ID", fesAppConfigEnvironment.getEnvironmentId(),
                        "QCA_APPCONFIG_PROFILE_ID", fesAppConfigHostedConfiguration.getConfigurationProfileId(),
                        "QCA_APPCONFIG_POLL_SECONDS", "30"))
                .secrets(Map.of(
                        "QCA_FES_API_KEY", software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(fesApiKeySecret),
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

        CfnOutput.Builder.create(this, "FesApiKeySecretName")
                .value(fesApiKeySecret.getSecretName())
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
    }
}
