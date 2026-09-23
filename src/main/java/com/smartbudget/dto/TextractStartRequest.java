package com.smartbudget.dto;

public record TextractStartRequest(
        String invoiceDraftId,
        String s3Key) {
}