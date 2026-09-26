package com.smartbudget.dto;

import java.util.List;

public record TextractResultResponse(
        String status,
        String statusMessage,
        List<ExtractedField> summaryFields,
        List<ExtractedLineItem> lineItems) {
}