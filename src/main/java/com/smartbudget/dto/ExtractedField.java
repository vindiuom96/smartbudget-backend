package com.smartbudget.dto;

public record ExtractedField(
        String type,
        String label,
        String value,
        Float confidence,
        Integer pageNumber) {
}