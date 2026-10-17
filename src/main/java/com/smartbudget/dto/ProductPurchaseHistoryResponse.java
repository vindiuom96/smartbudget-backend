package com.smartbudget.dto;

import java.util.List;

public record ProductPurchaseHistoryResponse(
        String supplierId,
        String supplier,
        String productCode,
        String description,
        String brand,
        int purchaseCount,
        ProductPriceSummaryResponse priceSummary,
        List<ProductPurchaseHistoryItemResponse> purchases) {
}