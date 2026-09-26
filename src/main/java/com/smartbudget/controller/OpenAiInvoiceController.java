package com.smartbudget.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.OpenAiInvoiceExtractionRequest;
import com.smartbudget.dto.OpenAiRawInvoiceResponse;
import com.smartbudget.service.OpenAiInvoiceExtractionService;
import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.service.OpenAiInvoiceNormalizationService;

@RestController
@RequestMapping("/api/invoices/openai")
public class OpenAiInvoiceController {

    private final OpenAiInvoiceExtractionService extractionService;
    private final OpenAiInvoiceNormalizationService normalizationService;

    public OpenAiInvoiceController(
            OpenAiInvoiceExtractionService extractionService,
            OpenAiInvoiceNormalizationService normalizationService) {

        this.extractionService = extractionService;
        this.normalizationService = normalizationService;
    }

    @PostMapping("/review")
    public InvoiceReviewResponse review(
            @RequestBody OpenAiInvoiceExtractionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        validateRequestOwnership(
                request,
                jwt);

        OpenAiRawInvoiceResponse raw = extractionService.extract(
                request.s3Keys());

        return normalizationService.normalize(
                raw);
    }

    @PostMapping("/extract")
    public OpenAiRawInvoiceResponse extract(
            @RequestBody OpenAiInvoiceExtractionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        validateRequestOwnership(
                request,
                jwt);

        return extractionService.extract(
                request.s3Keys());
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