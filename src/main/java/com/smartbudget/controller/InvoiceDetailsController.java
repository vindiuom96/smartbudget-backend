package com.smartbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.InvoiceDetailsResponse;
import com.smartbudget.service.InvoiceDetailsService;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceDetailsController {

    private final InvoiceDetailsService invoiceDetailsService;

    public InvoiceDetailsController(
            InvoiceDetailsService invoiceDetailsService) {

        this.invoiceDetailsService = invoiceDetailsService;
    }

    @GetMapping("/{invoiceId}")
    public InvoiceDetailsResponse getInvoice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String invoiceId) {

        return invoiceDetailsService.getInvoice(
                jwt.getSubject(),
                invoiceId);
    }
}