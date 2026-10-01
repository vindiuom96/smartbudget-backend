package com.smartbudget.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceDetailsResponse(
        String invoiceId,
        String supplier,
        String rawSupplierName,
        String invoiceNumber,
        String invoiceDate,

        BigDecimal subtotalExGst,
        BigDecimal gst,
        BigDecimal total,

        int itemCount,
        String approvedAt,

        List<String> s3Keys,
        List<InvoiceDetailsLineItemResponse> items) {
}