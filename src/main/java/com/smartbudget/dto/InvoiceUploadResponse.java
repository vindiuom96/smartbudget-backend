package com.smartbudget.dto;

public record InvoiceUploadResponse(
        String invoiceDraftId,
        String uploadUrl,
        String s3Key,
        long expiresInSeconds) {
}