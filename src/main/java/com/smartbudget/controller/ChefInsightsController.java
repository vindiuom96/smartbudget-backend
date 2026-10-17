package com.smartbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.ChefPriceInsightsResponse;
import com.smartbudget.service.ChefPriceInsightService;

@RestController
@RequestMapping("/api/insights")
public class ChefInsightsController {

    private final ChefPriceInsightService chefPriceInsightService;

    public ChefInsightsController(
            ChefPriceInsightService chefPriceInsightService) {

        this.chefPriceInsightService = chefPriceInsightService;
    }

    @GetMapping("/price-changes")
    public ChefPriceInsightsResponse getPriceChangeInsights(
            @AuthenticationPrincipal Jwt jwt) {

        return chefPriceInsightService
                .getPriceInsights(
                        jwt.getSubject());
    }
}