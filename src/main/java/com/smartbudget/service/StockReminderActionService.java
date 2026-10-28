package com.smartbudget.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.StockReminderActionRequest;
import com.smartbudget.dto.StockReminderActionResponse;
import com.smartbudget.model.StockReminderActionEntity;
import com.smartbudget.repository.StockReminderActionRepository;

@Service
public class StockReminderActionService {

    private final StockReminderActionRepository stockReminderActionRepository;

    public StockReminderActionService(
            StockReminderActionRepository stockReminderActionRepository) {

        this.stockReminderActionRepository = stockReminderActionRepository;
    }

    public StockReminderActionResponse recordAction(
            String userSub,
            StockReminderActionRequest request) {

        if (request == null) {
            throw badRequest(
                    "Reminder action is required.");
        }

        String supplierId = normalizeRequired(
                request.supplierId(),
                "supplierId");

        String productCode = normalizeRequired(
                request.productCode(),
                "productCode");

        String expectedNextDate = validateDate(
                request.expectedNextDate());

        if (request.actionType() == null) {
            throw badRequest(
                    "actionType is required.");
        }

        String actionAt = Instant.now().toString();

        String sortKey = buildSortKey(
                supplierId,
                productCode,
                expectedNextDate);

        StockReminderActionEntity entity = new StockReminderActionEntity();

        entity.setUserSub(
                userSub);

        entity.setSortKey(
                sortKey);

        entity.setSupplierId(
                supplierId);

        entity.setProductCode(
                productCode);

        entity.setExpectedNextDate(
                expectedNextDate);

        entity.setActionType(
                request.actionType()
                        .name());

        entity.setActionAt(
                actionAt);

        stockReminderActionRepository
                .save(entity);

        return new StockReminderActionResponse(
                supplierId,
                productCode,
                expectedNextDate,
                request.actionType(),
                actionAt);
    }

    private String buildSortKey(
            String supplierId,
            String productCode,
            String expectedNextDate) {

        return "REMINDER#" +
                supplierId +
                "#" +
                productCode +
                "#" +
                expectedNextDate;
    }

    private String normalizeRequired(
            String value,
            String fieldName) {

        if (value == null ||
                value.isBlank()) {

            throw badRequest(
                    fieldName +
                            " is required.");
        }

        return value
                .trim()
                .toUpperCase();
    }

    private String validateDate(
            String value) {

        if (value == null ||
                value.isBlank()) {

            throw badRequest(
                    "expectedNextDate is required.");
        }

        try {
            return LocalDate.parse(
                    value.trim())
                    .toString();

        } catch (DateTimeParseException exception) {

            throw badRequest(
                    "expectedNextDate must use YYYY-MM-DD.");
        }
    }

    private ResponseStatusException badRequest(
            String message) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message);
    }
}