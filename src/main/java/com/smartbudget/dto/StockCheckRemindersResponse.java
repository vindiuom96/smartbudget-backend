package com.smartbudget.dto;

import java.util.List;

public record StockCheckRemindersResponse(
        int reminderCount,
        int overdueCount,
        int dueCount,
        int upcomingCount,
        List<StockCheckReminderResponse> reminders) {
}