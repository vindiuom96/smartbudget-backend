package com.smartbudget.dto;

import java.util.List;

public record ChefPriceInsightsResponse(
        int insightCount,
        List<PriceChangeInsightResponse> insights) {
}