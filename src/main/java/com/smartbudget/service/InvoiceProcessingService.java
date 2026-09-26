package com.smartbudget.service;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.dto.OpenAiInvoiceExtractionRequest;
import com.smartbudget.dto.OpenAiRawInvoiceResponse;

@Service
public class InvoiceProcessingService {

    private final OpenAiInvoiceExtractionService extractionService;
    private final OpenAiInvoiceNormalizationService normalizationService;

    public InvoiceProcessingService(
            OpenAiInvoiceExtractionService extractionService,
            OpenAiInvoiceNormalizationService normalizationService) {

        this.extractionService = extractionService;
        this.normalizationService = normalizationService;
    }

    public InvoiceReviewResponse process(
            OpenAiInvoiceExtractionRequest request) {

        // 1. OpenAI only reads/transcribes the invoice.
        OpenAiRawInvoiceResponse raw = extractionService.extract(
                request.s3Keys());

        // 2. Java normalizes and validates everything.
        InvoiceReviewResponse review = normalizationService.normalize(raw);

        /*
         * For now:
         *
         * If validation passes -> return it.
         * If validation fails -> also return it with needsReview=true.
         *
         * In the next step we will insert the automatic
         * Textract fallback here.
         */

        return review;
    }
}