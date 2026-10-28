package com.smartbudget.dto;

import com.smartbudget.model.StockReminderActionType;

public record StockReminderActionResponse(
        String supplierId,
        String productCode,
        String expectedNextDate,
        StockReminderActionType actionType,
        String actionAt) {
}