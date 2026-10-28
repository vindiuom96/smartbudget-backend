package com.smartbudget.dto;

import java.math.BigDecimal;

public record StockCheckReminderResponse(
        String supplierId,
        String supplier,
        String productCode,
        String description,
        String brand,

        String lastInvoiceId,
        String lastInvoiceNumber,
        String lastPurchaseDate,

        int purchaseCount,
        int typicalIntervalDays,

        String expectedNextDate,
        int daysSinceLastPurchase,
        int daysFromExpectedDate,

        StockCheckReminderStatus status,

        BigDecimal lastQuantity,
        String lastUnit,
        String lastPackSize,

        BigDecimal lastNormalizedQuantity,
        String normalizedUnit,
        BigDecimal lastNormalizedUnitPrice) {
}