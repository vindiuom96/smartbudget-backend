package com.smartbudget.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceReviewResponse(
        String supplier,
        String invoiceNumber,
        String invoiceDate,
        BigDecimal subtotalExGst,
        BigDecimal gst,
        BigDecimal total,
        boolean needsReview,
        int pageCount,
        List<String> warnings,
        List<InvoiceLineItemReview> items) {
}