package com.smartbudget.dto;

import java.math.BigDecimal;
import java.util.List;

public record OpenAiRawInvoiceResponse(
        String supplier,
        String invoiceNumber,
        String invoiceDate,

        BigDecimal subtotalExGst,
        BigDecimal gst,
        BigDecimal total,

        List<OpenAiRawLineItem> items) {
}