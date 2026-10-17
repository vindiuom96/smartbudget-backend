package com.smartbudget.dto;

import java.math.BigDecimal;

public record ProductPurchaseHistoryItemResponse(
        String invoiceId,
        String invoiceNumber,
        String invoiceDate,

        String description,
        String brand,

        String packSize,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineExGst,

        BigDecimal normalizedQuantity,
        String normalizedUnit,
        BigDecimal normalizedUnitPrice) {
}