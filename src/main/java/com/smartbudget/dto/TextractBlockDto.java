package com.smartbudget.dto;

import java.util.List;

public record TextractBlockDto(
        String id,
        String blockType,
        String text,
        Integer page,
        Integer rowIndex,
        Integer columnIndex,
        Integer rowSpan,
        Integer columnSpan,
        Float confidence,
        List<String> entityTypes,
        List<TextractRelationshipDto> relationships) {
}