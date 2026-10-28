package com.smartbudget.dto;

import com.smartbudget.model.StockReminderActionType;

public record StockReminderActionRequest(
        String supplierId,
        String productCode,
        String expectedNextDate,
        StockReminderActionType actionType) {
}