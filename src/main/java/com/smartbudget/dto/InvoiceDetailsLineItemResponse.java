package com.smartbudget.dto;

import java.math.BigDecimal;

public record InvoiceDetailsLineItemResponse(
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
        Integer pageNumber) {
}