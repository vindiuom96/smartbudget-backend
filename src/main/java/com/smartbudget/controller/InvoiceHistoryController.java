package com.smartbudget.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.service.InvoiceHistoryService;
import com.smartbudget.dto.InvoiceHistoryItemResponse;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceHistoryController {

    private final InvoiceHistoryService invoiceHistoryService;

    public InvoiceHistoryController(
            InvoiceHistoryService invoiceHistoryService) {
        this.invoiceHistoryService = invoiceHistoryService;
    }

    @GetMapping
    public List<InvoiceHistoryItemResponse> getInvoices(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String month) {
        return invoiceHistoryService.getInvoices(
                jwt.getSubject(),
                month);
    }
}