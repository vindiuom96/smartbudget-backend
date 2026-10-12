package com.smartbudget.dto;

import java.util.List;

public record InvoiceRevalidationResponse(
        boolean needsReview,
        List<String> warnings,
        List<InvoiceItemValidationResponse> items) {
}