package com.smartbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.smartbudget.dto.StockReminderActionRequest;
import com.smartbudget.dto.StockReminderActionResponse;
import com.smartbudget.service.StockReminderActionService;
import com.smartbudget.dto.StockCheckRemindersResponse;
import com.smartbudget.service.StockCheckReminderService;

@RestController
@RequestMapping("/api/reminders")
public class StockCheckReminderController {

    private final StockCheckReminderService stockCheckReminderService;
    private final StockReminderActionService stockReminderActionService;

    public StockCheckReminderController(
            StockCheckReminderService stockCheckReminderService,
            StockReminderActionService stockReminderActionService) {

        this.stockCheckReminderService = stockCheckReminderService;
        this.stockReminderActionService = stockReminderActionService;
    }

    @GetMapping("/stock-checks")
    public StockCheckRemindersResponse getStockCheckReminders(
            @AuthenticationPrincipal Jwt jwt) {

        return stockCheckReminderService
                .getStockCheckReminders(
                        jwt.getSubject());
    }

    @PostMapping("/stock-checks/action")
    public StockReminderActionResponse recordStockCheckAction(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody StockReminderActionRequest request) {

        return stockReminderActionService
                .recordAction(
                        jwt.getSubject(),
                        request);
    }
}