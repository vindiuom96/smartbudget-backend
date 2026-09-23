package com.smartbudget.dto;

import java.util.List;

public record TextractReviewRequest(
        List<String> jobIds) {
}