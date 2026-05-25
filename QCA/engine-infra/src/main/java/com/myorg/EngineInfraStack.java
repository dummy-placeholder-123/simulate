package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.cloudwatch.Metric;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.CpuArchitecture;
import software.amazon.awscdk.services.ecs.DeploymentCircuitBreaker;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.OperatingSystemFamily;
import software.amazon.awscdk.services.ecs.RuntimePlatform;
import software.amazon.awscdk.services.ecs.ScalableTaskCount;
import software.amazon.awscdk.services.ecr.CfnRepository;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.sqs.IQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

public class EngineInfraStack extends Stack {
    public EngineInfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String stage = StageConfig.stage(getNode().tryGetContext("stage"));
        String resourcePrefix = StageConfig.resourcePrefix(stage);
        String engineLogGroupName = "prod".equals(stage) ? "/qca/engine" : "/qca/" + stage + "/engine";
        String scanQueueName = resourcePrefix + "-scan-queue";
        String scanUploadBucketName = resourcePrefix + "-scan-uploads-" + StageConfig.AWS_ACCOUNT_ID + "-" + StageConfig.AWS_REGION;
        String scanTableName = resourcePrefix + "-scans";

        IQueue scanQueue = Queue.fromQueueArn(this, "ImportedScanQueue",
                "arn:aws:sqs:" + StageConfig.AWS_REGION + ":" + StageConfig.AWS_ACCOUNT_ID + ":" + scanQueueName);
        IBucket scanUploadBucket = Bucket.fromBucketName(this, "ImportedScanUploadBucket", scanUploadBucketName);
        ITable scanTable = Table.fromTableName(this, "ImportedScanTable", scanTableName);

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
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        CfnRepository engineRepository = CfnRepository.Builder.create(this, "EngineImageRepository")
                .repositoryName(resourcePrefix + "-engine")
                .imageScanningConfiguration(CfnRepository.ImageScanningConfigurationProperty.builder()
                        .scanOnPush(true)
                        .build())
                .lifecyclePolicy(CfnRepository.LifecyclePolicyProperty.builder()
                        .lifecyclePolicyText("""
                                {
                                  "rules": [
                                    {
                                      "rulePriority": 1,
                                      "selection": {
                                        "tagStatus": "any",
                                        "countType": "imageCountMoreThan",
                                        "countNumber": 20
                                      },
                                      "action": {
                                        "type": "expire"
                                      }
                                    }
                                  ]
                                }
                                """)
                        .build())
                .build();
        engineRepository.applyRemovalPolicy(RemovalPolicy.RETAIN);

        Role engineExecutionRole = Role.Builder.create(this, "EngineTaskExecutionRole")
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

        FargateService engineService = FargateService.Builder.create(this, "EngineService")
                .serviceName(resourcePrefix + "-engine-service")
                .cluster(cluster)
                .taskDefinition(engineTaskDefinition)
                .desiredCount(0)
                .assignPublicIp(true)
                .circuitBreaker(DeploymentCircuitBreaker.builder()
                        .rollback(true)
                        .build())
                .minHealthyPercent(100)
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PUBLIC)
                        .build())
                .build();

        ScalableTaskCount scaling = engineService.autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(0)
                .maxCapacity(5)
                .build());

        scaling.scaleOnMetric("QueueDepthScaling",
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
    }
}
