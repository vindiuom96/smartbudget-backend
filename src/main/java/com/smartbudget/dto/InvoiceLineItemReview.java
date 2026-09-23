package com.smartbudget.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceLineItemReview(
        String productCode,
        String description,
        String brand,
        String packSize,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineExGst,
        BigDecimal gstValue,
        BigDecimal lineTotal,
        Float confidence,
        boolean needsReview,
        Integer pageNumber,
        List<String> warnings) {
}