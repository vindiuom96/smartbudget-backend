package com.smartbudget.service;

import java.math.BigDecimal;
import java.time.YearMonth;

public interface MonthlyBudgetProvider {

    BigDecimal getBudget(YearMonth month);
}