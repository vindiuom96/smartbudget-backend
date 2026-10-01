package com.smartbudget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.MonthlyDashboardResponse;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.repository.InvoiceRepository;

@Service
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final MonthlyBudgetProvider monthlyBudgetProvider;

    public DashboardService(
            InvoiceRepository invoiceRepository,
            MonthlyBudgetProvider monthlyBudgetProvider) {

        this.invoiceRepository = invoiceRepository;

        this.monthlyBudgetProvider = monthlyBudgetProvider;
    }

    public MonthlyDashboardResponse getMonthlyDashboard(
            String userSub,
            String month) {

        YearMonth requestedMonth = parseMonth(month);

        List<InvoiceEntity> monthlyInvoices = invoiceRepository
                .findAllByUser(userSub)
                .stream()
                .filter(invoice -> belongsToMonth(
                        invoice,
                        requestedMonth))
                .toList();

        BigDecimal spentExGst = monthlyInvoices
                .stream()
                .map(InvoiceEntity::getSubtotalExGst)
                .filter(value -> value != null)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);

        BigDecimal budget = monthlyBudgetProvider
                .getBudget(requestedMonth);

        BigDecimal remaining = budget.subtract(spentExGst);

        BigDecimal usedPercentage = calculateUsedPercentage(
                spentExGst,
                budget);

        return new MonthlyDashboardResponse(
                requestedMonth.toString(),
                budget,
                spentExGst,
                remaining,
                usedPercentage,
                monthlyInvoices.size());
    }

    private YearMonth parseMonth(
            String month) {

        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }

        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Month must use YYYY-MM format.");
        }
    }

    private boolean belongsToMonth(
            InvoiceEntity invoice,
            YearMonth requestedMonth) {

        if (invoice.getInvoiceDate() == null) {
            return false;
        }

        try {
            LocalDate invoiceDate = LocalDate.parse(
                    invoice.getInvoiceDate());

            return YearMonth
                    .from(invoiceDate)
                    .equals(requestedMonth);

        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private BigDecimal calculateUsedPercentage(
            BigDecimal spent,
            BigDecimal budget) {

        if (budget == null ||
                budget.compareTo(BigDecimal.ZERO) <= 0) {

            return BigDecimal.ZERO;
        }

        return spent
                .multiply(
                        BigDecimal.valueOf(100))
                .divide(
                        budget,
                        1,
                        RoundingMode.HALF_UP);
    }
}