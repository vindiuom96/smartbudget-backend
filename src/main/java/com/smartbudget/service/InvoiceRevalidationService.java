package com.smartbudget.service;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.InvoiceApprovalRequest;
import com.smartbudget.dto.InvoiceRevalidationResponse;

@Service
public class InvoiceRevalidationService {

    private final InvoiceValidationService invoiceValidationService;

    public InvoiceRevalidationService(
            InvoiceValidationService invoiceValidationService) {

        this.invoiceValidationService = invoiceValidationService;
    }

    public InvoiceRevalidationResponse revalidate(
            InvoiceApprovalRequest request) {

        InvoiceValidationResult result = invoiceValidationService
                .validate(request);

        return new InvoiceRevalidationResponse(
                !result.valid(),
                result.warnings(),
                result.items());
    }
}