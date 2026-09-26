package com.smartbudget.dto;

import java.util.List;

public record TextractTableResultResponse(
        String status,
        String statusMessage,
        List<TextractBlockDto> blocks) {
}