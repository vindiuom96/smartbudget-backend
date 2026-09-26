package com.smartbudget.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class AwsConfig {

    @Bean
    S3Presigner s3Presigner(
            @Value("${aws.region}") String region) {

        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    TextractClient textractClient(
            @Value("${aws.region}") String region) {

        return TextractClient.builder()
                .region(Region.of(region))
                .build();
    }
}