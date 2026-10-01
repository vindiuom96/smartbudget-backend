package com.smartbudget.dto;

import java.math.BigDecimal;

public record InvoiceHistoryItemResponse(
        String invoiceId,
        String supplier,
        String invoiceNumber,
        String invoiceDate,
        BigDecimal subtotalExGst,
        BigDecimal gst,
        BigDecimal total,
        int itemCount,
        String approvedAt) {
}