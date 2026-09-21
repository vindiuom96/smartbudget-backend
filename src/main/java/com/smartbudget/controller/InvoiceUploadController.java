package com.smartbudget.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.InvoiceUploadRequest;
import com.smartbudget.dto.InvoiceUploadResponse;
import com.smartbudget.service.InvoiceUploadService;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceUploadController {

    private final InvoiceUploadService invoiceUploadService;

    public InvoiceUploadController(
            InvoiceUploadService invoiceUploadService) {
        this.invoiceUploadService = invoiceUploadService;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<InvoiceUploadResponse> createUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody InvoiceUploadRequest request) {

        String userSub = jwt.getSubject();

        InvoiceUploadResponse response = invoiceUploadService.createUploadUrl(
                userSub,
                request);

        return ResponseEntity.ok(response);
    }
}