package com.smartbudget.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.dto.OpenAiInvoiceExtractionRequest;
import com.smartbudget.dto.OpenAiRawInvoiceResponse;
import com.smartbudget.dto.SupplierResolution;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.repository.InvoiceRepository;

@Service
public class InvoiceProcessingService {

    private final OpenAiInvoiceExtractionService extractionService;
    private final OpenAiInvoiceNormalizationService normalizationService;
    private final SupplierNormalizationService supplierNormalizationService;
    private final InvoiceRepository invoiceRepository;

    public InvoiceProcessingService(
            OpenAiInvoiceExtractionService extractionService,
            OpenAiInvoiceNormalizationService normalizationService,
            SupplierNormalizationService supplierNormalizationService,
            InvoiceRepository invoiceRepository) {

        this.extractionService = extractionService;
        this.normalizationService = normalizationService;
        this.supplierNormalizationService = supplierNormalizationService;
        this.invoiceRepository = invoiceRepository;
    }

    public InvoiceReviewResponse process(
            String userSub,
            OpenAiInvoiceExtractionRequest request) {

        /*
         * 1. OpenAI only reads/transcribes the invoice.
         */
        OpenAiRawInvoiceResponse raw = extractionService.extract(
                request.s3Keys());

        /*
         * 2. Java normalizes and validates the invoice.
         */
        InvoiceReviewResponse review = normalizationService.normalize(raw);

        /*
         * 3. Check whether this invoice has already
         * been saved.
         *
         * We can only do this safely if both:
         *
         * - supplier is recognised
         * - invoice number was extracted
         */
        checkForDuplicate(
                userSub,
                review);

        /*
         * 4. Only new invoices continue to the
         * review/edit/approval workflow.
         */
        return review;
    }

    private void checkForDuplicate(
            String userSub,
            InvoiceReviewResponse review) {

        if (review == null) {
            return;
        }

        if (isBlank(review.supplier())
                || isBlank(review.invoiceNumber())) {

            /*
             * We cannot safely determine duplicate
             * identity without these fields.
             *
             * The normal review workflow will deal
             * with the missing values.
             */
            return;
        }

        SupplierResolution supplier = supplierNormalizationService.resolve(
                review.supplier());

        if (!supplier.recognized()) {

            /*
             * Do not guess supplier identity.
             *
             * Unknown suppliers must first be
             * confirmed by the chef.
             */
            return;
        }

        String sortKey = buildInvoiceSortKey(
                supplier.supplierId(),
                review.invoiceNumber());

        InvoiceEntity existingInvoice = invoiceRepository.findByUserAndSortKey(
                userSub,
                sortKey);

        if (existingInvoice != null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This invoice has already been saved.");
        }
    }

    private String buildInvoiceSortKey(
            String supplierId,
            String invoiceNumber) {

        String normalizedInvoiceNumber = invoiceNumber
                .trim()
                .toUpperCase(Locale.ROOT);

        return "INVOICE#"
                + supplierId
                + "#"
                + normalizedInvoiceNumber;
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.isBlank();
    }
}