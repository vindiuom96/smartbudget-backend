package com.smartbudget.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecoveredLineItem(
        Integer rowIndex,
        Integer pageNumber,
        BigDecimal quantity,
        String productCode,
        String description,
        String brand,
        String packSize,
        String unit,
        BigDecimal unitPrice,
        BigDecimal lineExGst,
        BigDecimal gstValue,
        BigDecimal lineTotal,
        boolean valid,
        List<String> warnings,
        List<String> recoveryNotes) {
}
