package com.smartbudget.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.dto.OpenAiInvoiceExtractionRequest;
import com.smartbudget.service.InvoiceProcessingService;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceProcessingController {

    private final InvoiceProcessingService processingService;

    public InvoiceProcessingController(
            InvoiceProcessingService processingService) {

        this.processingService = processingService;
    }

    @PostMapping("/process")
    public InvoiceReviewResponse process(
            @RequestBody OpenAiInvoiceExtractionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        validateRequestOwnership(
                request,
                jwt);

        return processingService.process(
                request);
    }

    private void validateRequestOwnership(
            OpenAiInvoiceExtractionRequest request,
            Jwt jwt) {

        if (request.invoiceDraftId() == null
                || request.invoiceDraftId().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "invoiceDraftId is required.");
        }

        if (request.s3Keys() == null
                || request.s3Keys().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one s3Key is required.");
        }

        String expectedPrefix = "invoices/"
                + jwt.getSubject()
                + "/"
                + request.invoiceDraftId()
                + "/";

        boolean invalidKey = request.s3Keys()
                .stream()
                .anyMatch(
                        key -> key == null
                                || !key.startsWith(
                                        expectedPrefix));

        if (invalidKey) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invoice S3 key does not belong "
                            + "to this user/draft.");
        }
    }
}