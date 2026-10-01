package com.smartbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.MonthlyDashboardResponse;
import com.smartbudget.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(
            DashboardService dashboardService) {

        this.dashboardService = dashboardService;
    }

    @GetMapping
    public MonthlyDashboardResponse getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String month) {

        return dashboardService
                .getMonthlyDashboard(
                        jwt.getSubject(),
                        month);
    }
}