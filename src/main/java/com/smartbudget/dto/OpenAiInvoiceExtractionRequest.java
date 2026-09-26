package com.smartbudget.dto;

import java.util.List;

public record OpenAiInvoiceExtractionRequest(
        String invoiceDraftId,
        List<String> s3Keys) {
}