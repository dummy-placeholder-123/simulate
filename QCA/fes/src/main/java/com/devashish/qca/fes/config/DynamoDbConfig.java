package com.devashish.qca.fes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sfn.SfnClient;

@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(@Value("${qca.aws.region}") String awsRegion) {
        return DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${qca.aws.region}") String awsRegion) {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public S3Client s3Client(@Value("${qca.aws.region}") String awsRegion) {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public SfnClient sfnClient(@Value("${qca.aws.region}") String awsRegion) {
        return SfnClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }

    @Bean
    public AppConfigDataClient appConfigDataClient(@Value("${qca.aws.region}") String awsRegion) {
        return AppConfigDataClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
