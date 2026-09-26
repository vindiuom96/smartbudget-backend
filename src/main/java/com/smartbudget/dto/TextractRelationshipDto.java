package com.smartbudget.dto;

import java.util.List;

public record TextractRelationshipDto(
        String type,
        List<String> ids) {
}