package com.smartbudget.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.TextractResultResponse;
import com.smartbudget.dto.TextractStartRequest;
import com.smartbudget.dto.TextractStartResponse;
import com.smartbudget.dto.TextractTableResultResponse;
import com.smartbudget.service.TextractService;
import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.dto.TextractReviewRequest;
import com.smartbudget.service.InvoiceNormalizationService;
import com.smartbudget.service.InvoiceRecoveryService;
import com.smartbudget.service.TableRecoveryService;
import com.smartbudget.dto.RecoveredTableDto;
import com.smartbudget.dto.RecoveredLineItem;
import com.smartbudget.dto.TextractRecoveryRequest;

@RestController
@RequestMapping("/api/invoices/textract")
public class TextractController {

    private final TextractService textractService;
    private final InvoiceNormalizationService invoiceNormalizationService;
    private final TableRecoveryService tableRecoveryService;
    private final InvoiceRecoveryService invoiceRecoveryService;

    public TextractController(
            TextractService textractService,
            InvoiceNormalizationService invoiceNormalizationService,
            TableRecoveryService tableRecoveryService,
            InvoiceRecoveryService invoiceRecoveryService) {

        this.textractService = textractService;
        this.invoiceNormalizationService = invoiceNormalizationService;

        this.tableRecoveryService = tableRecoveryService;

        this.invoiceRecoveryService = invoiceRecoveryService;
    }

    @PostMapping("/start")
    public ResponseEntity<TextractStartResponse> start(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TextractStartRequest request) {

        TextractStartResponse response = textractService.startAnalysis(
                jwt.getSubject(),
                request.invoiceDraftId(),
                request.s3Key());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<TextractResultResponse> result(
            @PathVariable String jobId) {

        return ResponseEntity.ok(
                textractService.getAnalysis(jobId));
    }

    @PostMapping("/review")
    public ResponseEntity<InvoiceReviewResponse> review(
            @RequestBody TextractReviewRequest request) {

        if (request.jobIds() == null
                || request.jobIds().isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one Textract job ID is required");
        }

        List<TextractResultResponse> results = request.jobIds()
                .stream()
                .map(
                        textractService::getAnalysis)
                .toList();

        InvoiceReviewResponse response = invoiceNormalizationService
                .normalize(results);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tables/{jobId}")
    public ResponseEntity<TextractTableResultResponse> getTableAnalysis(
            @PathVariable String jobId) {

        return ResponseEntity.ok(
                textractService.getTableAnalysis(
                        jobId));
    }

    @GetMapping("/tables/{jobId}/line-items")
    public ResponseEntity<List<RecoveredLineItem>> recoverLineItems(
            @PathVariable String jobId) {

        TextractTableResultResponse result = textractService.getTableAnalysis(
                jobId);

        if (!"SUCCEEDED".equals(result.status())) {

            throw new IllegalStateException(
                    "Textract table analysis is not complete. Status: "
                            + result.status());
        }

        return ResponseEntity.ok(
                tableRecoveryService.extractLineItems(
                        result));
    }

    @GetMapping("/tables/{jobId}/recovered")
    public ResponseEntity<List<RecoveredTableDto>> recoverTable(
            @PathVariable String jobId) {

        TextractTableResultResponse result = textractService.getTableAnalysis(
                jobId);

        if (!"SUCCEEDED".equals(result.status())) {
            throw new IllegalStateException(
                    "Textract table analysis is not complete. Status: "
                            + result.status());
        }

        return ResponseEntity.ok(
                tableRecoveryService.recoverTables(
                        result));
    }

    @PostMapping("/review-with-recovery")
    public ResponseEntity<InvoiceReviewResponse> reviewWithRecovery(
            @RequestBody TextractRecoveryRequest request) {

        if (request.expenseJobIds() == null
                || request.expenseJobIds().isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one expense Textract job ID is required.");
        }

        if (request.tableJobIds() == null
                || request.tableJobIds().isEmpty()) {

            throw new IllegalArgumentException(
                    "At least one TABLES Textract job ID is required.");
        }

        /*
         * PASS 1:
         * Normal AnalyzeExpense result.
         */
        List<TextractResultResponse> expenseResults = request.expenseJobIds()
                .stream()
                .map(
                        textractService::getAnalysis)
                .toList();

        InvoiceReviewResponse originalReview = invoiceNormalizationService.normalize(
                expenseResults);

        /*
         * If AnalyzeExpense was already perfect,
         * don't do anything else.
         */
        if (!originalReview.needsReview()) {

            return ResponseEntity.ok(
                    originalReview);
        }

        /*
         * PASS 2:
         * Read TABLES jobs.
         */
        List<RecoveredLineItem> recoveredItems = new ArrayList<>();

        for (String tableJobId : request.tableJobIds()) {

            TextractTableResultResponse tableResult = textractService.getTableAnalysis(
                    tableJobId);

            if (!"SUCCEEDED".equals(
                    tableResult.status())) {

                throw new IllegalStateException(
                        "TABLES analysis is not complete for job "
                                + tableJobId
                                + ". Status: "
                                + tableResult.status());
            }

            recoveredItems.addAll(
                    tableRecoveryService.extractLineItems(
                            tableResult));
        }

        InvoiceReviewResponse recoveredReview = invoiceRecoveryService
                .applyTableRecovery(
                        originalReview,
                        recoveredItems);

        return ResponseEntity.ok(
                recoveredReview);
    }

    @PostMapping("/tables/start")
    public ResponseEntity<Map<String, String>> startTableAnalysis(
            @RequestBody TextractStartRequest request) {

        /*
         * Keep using the same validation you already use
         * before starting expense analysis.
         *
         * If that validation currently lives inside
         * TextractService.startAnalysis(), we'll move it
         * into a reusable helper afterward.
         */

        String jobId = textractService.startTableAnalysis(
                request.s3Key());

        return ResponseEntity.ok(
                Map.of(
                        "invoiceDraftId",
                        request.invoiceDraftId(),
                        "jobId",
                        jobId));
    }
}