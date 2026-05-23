package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;

public class InfraStack extends Stack {
    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Table scanTable = Table.Builder.create(this, "ScanTable")
                .tableName("qca-scans")
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
                .tableName("qca-scan-idempotency")
                .partitionKey(Attribute.builder()
                        .name("idempotencyKey")
                        .type(AttributeType.STRING)
                        .build())
                .timeToLiveAttribute("expiresAt")
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();
    }
}
