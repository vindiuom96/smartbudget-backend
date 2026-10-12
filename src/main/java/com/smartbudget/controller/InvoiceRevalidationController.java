package com.smartbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.InvoiceApprovalRequest;
import com.smartbudget.dto.InvoiceRevalidationResponse;
import com.smartbudget.service.InvoiceRevalidationService;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceRevalidationController {

    private final InvoiceRevalidationService invoiceRevalidationService;

    public InvoiceRevalidationController(
            InvoiceRevalidationService invoiceRevalidationService) {

        this.invoiceRevalidationService = invoiceRevalidationService;
    }

    @PostMapping("/revalidate")
    public InvoiceRevalidationResponse revalidate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody InvoiceApprovalRequest request) {

        /*
         * Jwt is intentionally part of this endpoint
         * because revalidation is an authenticated
         * invoice workflow.
         *
         * The final /approve endpoint still performs
         * S3 ownership and persistence checks.
         */
        jwt.getSubject();

        return invoiceRevalidationService
                .revalidate(request);
    }
}