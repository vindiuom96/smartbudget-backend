package com.smartbudget.dto;

import java.math.BigDecimal;

public record PriceChangeInsightResponse(
        String supplierId,
        String supplier,
        String productCode,
        String description,
        String brand,

        String currentInvoiceId,
        String currentInvoiceNumber,
        String currentInvoiceDate,

        String previousInvoiceId,
        String previousInvoiceNumber,
        String previousInvoiceDate,

        BigDecimal currentUnitPrice,
        BigDecimal previousUnitPrice,
        String normalizedUnit,

        BigDecimal absoluteChange,
        BigDecimal percentageChange,
        PriceChangeDirection direction) {
}