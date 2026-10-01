package com.smartbudget.service;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredMonthlyBudgetProvider
        implements MonthlyBudgetProvider {

    private final BigDecimal monthlyBudget;

    public ConfiguredMonthlyBudgetProvider(
            @Value("${smartbudget.monthly-budget}") BigDecimal monthlyBudget) {

        this.monthlyBudget = monthlyBudget;
    }

    @Override
    public BigDecimal getBudget(
            YearMonth month) {

        return monthlyBudget;
    }
}