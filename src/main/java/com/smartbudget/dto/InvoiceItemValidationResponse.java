package com.smartbudget.dto;

import java.util.List;

public record InvoiceItemValidationResponse(
        int itemIndex,
        List<String> warnings) {
}