package com.smartbudget.dto;

public record SupplierResolution(
        String supplierId,
        String supplierName,
        String rawSupplierName,
        boolean recognized) {
}