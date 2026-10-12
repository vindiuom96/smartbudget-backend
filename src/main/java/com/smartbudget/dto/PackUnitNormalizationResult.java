package com.smartbudget.dto;

import java.math.BigDecimal;

public record PackUnitNormalizationResult(
        BigDecimal normalizedQuantity,
        String normalizedUnit,
        BigDecimal normalizedUnitPrice) {

    public boolean normalized() {
        return normalizedQuantity != null
                && normalizedUnit != null
                && normalizedUnitPrice != null;
    }
}