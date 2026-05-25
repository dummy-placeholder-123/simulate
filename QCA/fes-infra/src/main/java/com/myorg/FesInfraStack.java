package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
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

public class FesInfraStack extends Stack {
    public FesInfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String stage = StageConfig.stage(getNode().tryGetContext("stage"));
        String resourcePrefix = StageConfig.resourcePrefix(stage);

        Table scanTable = Table.Builder.create(this, "ScanTable")
                .tableName(resourcePrefix + "-scans")
                .partitionKey(Attribute.builder()
                        .name("scanId")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.RETAIN)
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

        Table.Builder.create(this, "ScanIdempotencyTable")
                .tableName(resourcePrefix + "-scan-idempotency")
                .partitionKey(Attribute.builder()
                        .name("idempotencyKey")
                        .type(AttributeType.STRING)
                        .build())
                .timeToLiveAttribute("expiresAt")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        Bucket scanUploadBucket = Bucket.Builder.create(this, "ScanUploadBucket")
                .bucketName(resourcePrefix + "-scan-uploads-" + StageConfig.AWS_ACCOUNT_ID + "-" + StageConfig.AWS_REGION)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .cors(List.of(CorsRule.builder()
                        .allowedMethods(List.of(HttpMethods.PUT))
                        .allowedOrigins(List.of("*"))
                        .allowedHeaders(List.of("*"))
                        .build()))
                .encryption(BucketEncryption.S3_MANAGED)
                .enforceSsl(true)
                .versioned(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        Queue scanDlq = Queue.Builder.create(this, "ScanDeadLetterQueue")
                .queueName(resourcePrefix + "-scan-dlq")
                .retentionPeriod(Duration.days(14))
                .build();

        Queue scanQueue = Queue.Builder.create(this, "ScanQueue")
                .queueName(resourcePrefix + "-scan-queue")
                .visibilityTimeout(Duration.minutes(6))
                .retentionPeriod(Duration.days(4))
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

        StateMachine.Builder.create(this, "ScanStateMachine")
                .stateMachineName(resourcePrefix + "-scan-state-machine")
                .definitionBody(DefinitionBody.fromChainable(sendScanToQueue))
                .stateMachineType(StateMachineType.STANDARD)
                .build();
    }
}
