package com.smartbudget.dto;

public record InvoiceUploadRequest(
        String fileName,
        String contentType,
        String invoiceDraftId,
        Integer pageNumber,
        Long fileSize) {
}