package com.smartbudget.dto;

import java.util.List;

public record RecoveredTableDto(
        String tableId,
        Integer page,
        List<RecoveredTableRowDto> rows) {
}