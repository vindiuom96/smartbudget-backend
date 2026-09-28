package com.smartbudget.dto;

public record InvoiceApprovalResponse(
        String invoiceId,
        String supplier,
        String invoiceNumber,
        String approvedAt) {
}