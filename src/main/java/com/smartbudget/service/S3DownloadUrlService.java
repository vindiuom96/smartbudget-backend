package com.smartbudget.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3DownloadUrlService {

    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3DownloadUrlService(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucketName) {

        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    public String createPresignedGetUrl(String s3Key) {

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner
                .presignGetObject(presignRequest)
                .url()
                .toString();
    }
}