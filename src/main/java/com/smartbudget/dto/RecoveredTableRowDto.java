package com.smartbudget.dto;

import java.util.List;

public record RecoveredTableRowDto(
        Integer rowIndex,
        List<String> cells) {
}