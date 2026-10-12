package com.smartbudget.service;

import java.util.List;

import com.smartbudget.dto.InvoiceItemValidationResponse;

public record InvoiceValidationResult(
        boolean valid,
        List<String> warnings,
        List<InvoiceItemValidationResponse> items) {
}