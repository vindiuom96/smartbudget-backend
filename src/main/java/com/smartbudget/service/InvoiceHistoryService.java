package com.smartbudget.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.repository.InvoiceRepository;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.dto.InvoiceHistoryItemResponse;

@Service
public class InvoiceHistoryService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceHistoryService(
            InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public List<InvoiceHistoryItemResponse> getInvoices(
            String userSub,
            String month) {
        YearMonth requestedMonth = parseMonth(month);

        return invoiceRepository
                .findAllByUser(userSub)
                .stream()

                .filter(invoice -> requestedMonth == null ||
                        belongsToMonth(invoice, requestedMonth))

                .sorted(
                        Comparator.comparing(
                                this::invoiceDateForSorting).reversed())

                .map(this::toResponse)

                .toList();
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
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

        LocalDate invoiceDate = LocalDate.parse(invoice.getInvoiceDate());

        return YearMonth
                .from(invoiceDate)
                .equals(requestedMonth);
    }

    private LocalDate invoiceDateForSorting(
            InvoiceEntity invoice) {
        if (invoice.getInvoiceDate() == null) {
            return LocalDate.MIN;
        }

        return LocalDate.parse(
                invoice.getInvoiceDate());
    }

    private InvoiceHistoryItemResponse toResponse(
            InvoiceEntity invoice) {
        int itemCount = invoice.getItems() == null
                ? 0
                : invoice.getItems().size();

        return new InvoiceHistoryItemResponse(
                invoice.getInvoiceId(),
                invoice.getSupplierName(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getSubtotalExGst(),
                invoice.getGst(),
                invoice.getTotal(),
                itemCount,
                invoice.getApprovedAt());
    }
}