package com.smartbudget.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartbudget.dto.PackUnitNormalizationResult;
import com.smartbudget.dto.StockCheckReminderResponse;
import com.smartbudget.dto.StockCheckRemindersResponse;
import com.smartbudget.dto.StockCheckReminderStatus;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.model.StockReminderActionEntity;
import com.smartbudget.repository.StockReminderActionRepository;
import com.smartbudget.repository.InvoiceRepository;

@Service
public class StockCheckReminderService {

    private static final int MINIMUM_PURCHASE_DATES = 3;

    private static final int UPCOMING_WINDOW_DAYS = 2;

    private static final double INTERVAL_TOLERANCE_PERCENT = 0.35;

    private static final int MINIMUM_INTERVAL_TOLERANCE_DAYS = 2;

    private final InvoiceRepository invoiceRepository;

    private final PackUnitNormalizationService packUnitNormalizationService;

    private final ZoneId businessZoneId;

    private final StockReminderActionRepository stockReminderActionRepository;

    public StockCheckReminderService(
            InvoiceRepository invoiceRepository,
            PackUnitNormalizationService packUnitNormalizationService,
            StockReminderActionRepository stockReminderActionRepository,
            @Value("${smartbudget.time-zone:Australia/Melbourne}") String timeZone) {

        this.invoiceRepository = invoiceRepository;

        this.packUnitNormalizationService = packUnitNormalizationService;

        this.stockReminderActionRepository = stockReminderActionRepository;

        this.businessZoneId = ZoneId.of(timeZone);
    }

    public StockCheckRemindersResponse getStockCheckReminders(
            String userSub) {

        LocalDate today = LocalDate.now(
                businessZoneId);

        return getStockCheckReminders(
                userSub,
                today);
    }

    /*
     * Package-private overload so our unit tests
     * can supply a fixed "today" date.
     */
    StockCheckRemindersResponse getStockCheckReminders(
            String userSub,
            LocalDate today) {

        List<InvoiceEntity> invoices = invoiceRepository
                .findAllByUser(
                        userSub);

        List<StockReminderActionEntity> savedActions = stockReminderActionRepository
                .findAllByUser(
                        userSub);

        Map<ProductKey, List<PurchasePoint>> purchasesByProduct = new HashMap<>();
        Map<String, StockReminderActionEntity> actionsBySortKey = new HashMap<>();

        for (StockReminderActionEntity action : savedActions) {

            if (action.getSortKey() != null) {
                actionsBySortKey.put(
                        action.getSortKey(),
                        action);
            }
        }
        for (InvoiceEntity invoice : invoices) {

            if (invoice.getSupplierId() == null ||
                    invoice.getSupplierId()
                            .isBlank()
                    ||
                    invoice.getItems() == null) {

                continue;
            }

            LocalDate invoiceDate = parseInvoiceDate(
                    invoice.getInvoiceDate());

            if (invoiceDate == null) {
                continue;
            }

            for (InvoiceLineItemEntity item : invoice.getItems()) {

                if (item.getProductCode() == null ||
                        item.getProductCode()
                                .isBlank()) {

                    continue;
                }

                ProductKey key = new ProductKey(
                        invoice.getSupplierId()
                                .trim()
                                .toUpperCase(),
                        item.getProductCode()
                                .trim()
                                .toUpperCase());

                PurchasePoint purchase = toPurchasePoint(
                        invoice,
                        item,
                        invoiceDate);

                purchasesByProduct
                        .computeIfAbsent(
                                key,
                                ignored -> new ArrayList<>())
                        .add(purchase);
            }
        }

        List<StockCheckReminderResponse> reminders = new ArrayList<>();

        for (Map.Entry<ProductKey, List<PurchasePoint>> entry : purchasesByProduct.entrySet()) {

            StockCheckReminderResponse reminder = createReminder(
                    entry.getKey(),
                    entry.getValue(),
                    today);

            if (reminder == null) {
                continue;
            }

            String reminderSortKey = buildReminderSortKey(
                    reminder.supplierId(),
                    reminder.productCode(),
                    reminder.expectedNextDate());

            if (actionsBySortKey.containsKey(
                    reminderSortKey)) {

                continue;
            }

            reminders.add(reminder);
        }

        reminders.sort(
                Comparator
                        .comparingInt(
                                (StockCheckReminderResponse reminder) -> statusPriority(
                                        reminder.status()))
                        .thenComparing(
                                Comparator.comparingInt(
                                        StockCheckReminderResponse::daysFromExpectedDate)
                                        .reversed()));

        int overdueCount = (int) reminders.stream()
                .filter(reminder -> reminder.status() == StockCheckReminderStatus.OVERDUE)
                .count();

        int dueCount = (int) reminders.stream()
                .filter(reminder -> reminder.status() == StockCheckReminderStatus.DUE)
                .count();

        int upcomingCount = (int) reminders.stream()
                .filter(reminder -> reminder.status() == StockCheckReminderStatus.UPCOMING)
                .count();

        return new StockCheckRemindersResponse(
                reminders.size(),
                overdueCount,
                dueCount,
                upcomingCount,
                reminders);
    }

    private StockCheckReminderResponse createReminder(
            ProductKey key,
            List<PurchasePoint> purchases,
            LocalDate today) {

        /*
         * Recurrence is calculated using distinct
         * purchase dates.
         *
         * Multiple invoices on the same day must
         * not create a fake zero-day interval.
         */
        List<LocalDate> purchaseDates = purchases.stream()
                .map(
                        PurchasePoint::invoiceDate)
                .distinct()
                .sorted()
                .toList();

        if (purchaseDates.size() < MINIMUM_PURCHASE_DATES) {

            return null;
        }

        List<Integer> intervals = calculateIntervals(
                purchaseDates);

        if (intervals.size() < 2) {
            return null;
        }

        int typicalIntervalDays = calculateMedianInterval(
                intervals);

        if (typicalIntervalDays <= 0) {
            return null;
        }

        if (!isRecurringPattern(
                intervals,
                typicalIntervalDays)) {

            return null;
        }

        PurchasePoint latest = purchases.stream()
                .max(
                        Comparator.comparing(
                                PurchasePoint::invoiceDate))
                .orElse(null);

        if (latest == null) {
            return null;
        }

        /*
         * Ignore future-dated invoices.
         */
        if (latest.invoiceDate()
                .isAfter(today)) {

            return null;
        }

        LocalDate expectedNextDate = latest.invoiceDate()
                .plusDays(
                        typicalIntervalDays);

        int daysSinceLastPurchase = Math.toIntExact(
                ChronoUnit.DAYS.between(
                        latest.invoiceDate(),
                        today));

        /*
         * Positive = past expected date.
         * Negative = expected date is still ahead.
         *
         * Example:
         *
         * expected = 20 Oct
         * today = 22 Oct
         *
         * daysFromExpectedDate = 2
         */
        int daysFromExpectedDate = Math.toIntExact(
                ChronoUnit.DAYS.between(
                        expectedNextDate,
                        today));

        StockCheckReminderStatus status = determineStatus(
                daysFromExpectedDate);

        /*
         * Product is recurring, but it is not close
         * enough to its next expected date yet.
         */
        if (status == null) {
            return null;
        }

        return new StockCheckReminderResponse(
                key.supplierId(),
                latest.supplier(),
                key.productCode(),
                latest.description(),
                latest.brand(),

                latest.invoiceId(),
                latest.invoiceNumber(),
                latest.invoiceDate()
                        .toString(),

                purchases.size(),
                typicalIntervalDays,

                expectedNextDate.toString(),
                daysSinceLastPurchase,
                daysFromExpectedDate,

                status,

                latest.quantity(),
                latest.unit(),
                latest.packSize(),

                latest.normalizedQuantity(),
                latest.normalizedUnit(),
                latest.normalizedUnitPrice());
    }

    private List<Integer> calculateIntervals(
            List<LocalDate> purchaseDates) {

        List<Integer> intervals = new ArrayList<>();

        for (int index = 1; index < purchaseDates.size(); index++) {

            LocalDate previous = purchaseDates.get(
                    index - 1);

            LocalDate current = purchaseDates.get(
                    index);

            long days = ChronoUnit.DAYS.between(
                    previous,
                    current);

            if (days > 0) {
                intervals.add(
                        Math.toIntExact(days));
            }
        }

        return intervals;
    }

    private int calculateMedianInterval(
            List<Integer> intervals) {

        List<Integer> sorted = intervals.stream()
                .sorted()
                .toList();

        int size = sorted.size();

        int middle = size / 2;

        if (size % 2 == 1) {
            return sorted.get(
                    middle);
        }

        int lower = sorted.get(
                middle - 1);

        int upper = sorted.get(
                middle);

        return (int) Math.round(
                (lower + upper) /
                        2.0);
    }

    private boolean isRecurringPattern(
            List<Integer> intervals,
            int typicalIntervalDays) {

        double tolerance = Math.max(
                MINIMUM_INTERVAL_TOLERANCE_DAYS,
                typicalIntervalDays *
                        INTERVAL_TOLERANCE_PERCENT);

        int consistentIntervals = 0;

        for (int interval : intervals) {

            double difference = Math.abs(
                    interval -
                            typicalIntervalDays);

            if (difference <= tolerance) {
                consistentIntervals++;
            }
        }

        /*
         * With limited history, every interval must
         * look consistent.
         *
         * Once we have more history, allow one
         * unusual interval. This protects against
         * holidays, missed invoices, etc.
         */
        int allowedOutliers = intervals.size() >= 4
                ? 1
                : 0;

        int requiredConsistentIntervals = intervals.size() -
                allowedOutliers;

        return consistentIntervals >= requiredConsistentIntervals;
    }

    private StockCheckReminderStatus determineStatus(
            int daysFromExpectedDate) {

        if (daysFromExpectedDate > 1) {
            return StockCheckReminderStatus.OVERDUE;
        }

        if (daysFromExpectedDate >= 0) {
            return StockCheckReminderStatus.DUE;
        }

        if (daysFromExpectedDate >= -UPCOMING_WINDOW_DAYS) {

            return StockCheckReminderStatus.UPCOMING;
        }

        return null;
    }

    private PurchasePoint toPurchasePoint(
            InvoiceEntity invoice,
            InvoiceLineItemEntity item,
            LocalDate invoiceDate) {

        BigDecimal normalizedQuantity = item.getNormalizedQuantity();

        String normalizedUnit = item.getNormalizedUnit();

        BigDecimal normalizedUnitPrice = item.getNormalizedUnitPrice();

        /*
         * Legacy invoice fallback.
         *
         * The reminder itself does not depend on
         * normalization, but showing the chef the
         * most recent quantity and price is useful.
         */
        if (normalizedQuantity == null ||
                normalizedUnit == null ||
                normalizedUnitPrice == null) {

            PackUnitNormalizationResult normalization = packUnitNormalizationService
                    .normalize(
                            item.getQuantity(),
                            item.getUnit(),
                            item.getPackSize(),
                            item.getLineExGst());

            normalizedQuantity = normalization
                    .normalizedQuantity();

            normalizedUnit = normalization
                    .normalizedUnit();

            normalizedUnitPrice = normalization
                    .normalizedUnitPrice();
        }

        return new PurchasePoint(
                invoice.getSupplierName(),

                invoice.getInvoiceId(),
                invoice.getInvoiceNumber(),
                invoiceDate,

                item.getDescription(),
                item.getBrand(),

                item.getQuantity(),
                item.getUnit(),
                item.getPackSize(),

                normalizedQuantity,
                normalizedUnit,
                normalizedUnitPrice);
    }

    private LocalDate parseInvoiceDate(
            String invoiceDate) {

        if (invoiceDate == null ||
                invoiceDate.isBlank()) {

            return null;
        }

        try {
            return LocalDate.parse(
                    invoiceDate);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private int statusPriority(
            StockCheckReminderStatus status) {

        return switch (status) {
            case OVERDUE -> 0;
            case DUE -> 1;
            case UPCOMING -> 2;
        };
    }

    private record ProductKey(
            String supplierId,
            String productCode) {
    }

    private record PurchasePoint(
            String supplier,

            String invoiceId,
            String invoiceNumber,
            LocalDate invoiceDate,

            String description,
            String brand,

            BigDecimal quantity,
            String unit,
            String packSize,

            BigDecimal normalizedQuantity,
            String normalizedUnit,
            BigDecimal normalizedUnitPrice) {
    }

    private String buildReminderSortKey(
            String supplierId,
            String productCode,
            String expectedNextDate) {

        return "REMINDER#" +
                supplierId.trim().toUpperCase() +
                "#" +
                productCode.trim().toUpperCase() +
                "#" +
                expectedNextDate;
    }
}