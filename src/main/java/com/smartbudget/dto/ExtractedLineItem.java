package com.smartbudget.dto;

import java.util.List;

public record ExtractedLineItem(
        List<ExtractedField> fields) {
}