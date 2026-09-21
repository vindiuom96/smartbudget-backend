package com.smartbudget.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartbudget.dto.InvoiceUploadRequest;
import com.smartbudget.dto.InvoiceUploadResponse;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
public class InvoiceUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png");

    private static final Duration URL_EXPIRATION = Duration.ofMinutes(10);

    private static final int MAX_PAGE_NUMBER = 10;

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB

    private final S3Presigner s3Presigner;
    private final String bucketName;

    public InvoiceUploadService(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucketName) {

        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    public InvoiceUploadResponse createUploadUrl(
            String userSub,
            InvoiceUploadRequest request) {

        validateContentType(request.contentType());
        validateFileSize(request.fileSize());

        String invoiceDraftId = getInvoiceDraftId(request.invoiceDraftId());

        String fileName = buildFileName(request);

        String s3Key = String.format(
                "invoices/%s/%s/%s",
                userSub,
                invoiceDraftId,
                fileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new InvoiceUploadResponse(
                invoiceDraftId,
                presignedRequest.url().toString(),
                s3Key,
                URL_EXPIRATION.toSeconds());
    }

    private void validateContentType(String contentType) {

        if (contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType)) {

            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType);
        }
    }

    private void validateFileSize(Long fileSize) {

        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException(
                    "File size is required");
        }

        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File must be 10 MB or smaller");
        }
    }

    private String getInvoiceDraftId(
            String invoiceDraftId) {

        // First page/file creates a new invoice draft.
        if (invoiceDraftId == null
                || invoiceDraftId.isBlank()) {

            return UUID.randomUUID().toString();
        }

        // Additional pages must send back a valid UUID.
        try {
            return UUID.fromString(invoiceDraftId).toString();

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    "Invalid invoice draft ID");
        }
    }

    private String buildFileName(
            InvoiceUploadRequest request) {

        // A PDF remains one file,
        // even if it contains multiple pages.
        if ("application/pdf".equals(
                request.contentType())) {

            return "document.pdf";
        }

        validatePageNumber(request.pageNumber());

        int pageNumber = request.pageNumber() != null
                ? request.pageNumber()
                : 1;

        String extension = "image/png".equals(
                request.contentType())
                        ? "png"
                        : "jpg";

        return "page-" + pageNumber + "." + extension;
    }

    private void validatePageNumber(
            Integer pageNumber) {

        if (pageNumber != null
                && (pageNumber < 1
                        || pageNumber > MAX_PAGE_NUMBER)) {

            throw new IllegalArgumentException(
                    "Page number must be between 1 and 10");
        }
    }
}