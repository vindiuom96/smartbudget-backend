package com.smartbudget.dto;

import java.math.BigDecimal;

public record ProductPriceSummaryResponse(
        BigDecimal currentUnitPrice,
        BigDecimal previousUnitPrice,
        String normalizedUnit,

        BigDecimal absoluteChange,
        BigDecimal percentageChange,
        PriceChangeDirection direction,

        BigDecimal lowestUnitPrice,
        BigDecimal highestUnitPrice,
        BigDecimal averageUnitPrice,

        BigDecimal currentVsAveragePercentage,
        int comparablePurchaseCount) {
}