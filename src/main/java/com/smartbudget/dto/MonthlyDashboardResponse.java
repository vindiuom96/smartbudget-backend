package com.smartbudget.dto;

import java.math.BigDecimal;

public record MonthlyDashboardResponse(
        String month,
        BigDecimal budget,
        BigDecimal spentExGst,
        BigDecimal remaining,
        BigDecimal usedPercentage,
        int invoiceCount) {
}