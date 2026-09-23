package com.smartbudget.dto;

import java.util.List;

public record TextractRecoveryRequest(
        List<String> expenseJobIds,
        List<String> tableJobIds) {
}