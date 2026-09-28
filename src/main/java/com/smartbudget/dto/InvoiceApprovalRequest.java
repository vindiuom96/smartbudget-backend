package com.smartbudget.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceApprovalRequest(
        String invoiceDraftId,
        List<String> s3Keys,

        String supplier,
        String invoiceNumber,
        String invoiceDate,

        BigDecimal subtotalExGst,
        BigDecimal gst,
        BigDecimal total,

        List<InvoiceApprovalLineItem> items) {
}