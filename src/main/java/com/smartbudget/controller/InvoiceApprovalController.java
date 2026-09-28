package com.smartbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.InvoiceApprovalRequest;
import com.smartbudget.dto.InvoiceApprovalResponse;
import com.smartbudget.service.InvoiceApprovalService;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceApprovalController {

    private final InvoiceApprovalService invoiceApprovalService;

    public InvoiceApprovalController(
            InvoiceApprovalService invoiceApprovalService) {

        this.invoiceApprovalService = invoiceApprovalService;
    }

    @PostMapping("/approve")
    public InvoiceApprovalResponse approve(
            @RequestBody InvoiceApprovalRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        return invoiceApprovalService.approve(
                jwt.getSubject(),
                request);
    }
}