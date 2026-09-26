package com.smartbudget.dto;

import java.math.BigDecimal;

public record OpenAiRawLineItem(
        String productCode,
        String description,
        String brand,
        String packSize,
        String unit,

        BigDecimal quantity,
        BigDecimal unitPrice,

        // Used only when the invoice literally contains a generic
        // Amount / Value column.
        BigDecimal amount,

        // Used only when these specific financial columns
        // actually appear on the invoice.
        BigDecimal lineExGst,
        BigDecimal gstValue,
        BigDecimal lineTotal,

        Integer pageNumber) {
}