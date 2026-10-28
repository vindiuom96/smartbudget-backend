package com.smartbudget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartbudget.dto.StockCheckReminderStatus;
import com.smartbudget.dto.StockCheckRemindersResponse;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.model.StockReminderActionEntity;
import com.smartbudget.repository.InvoiceRepository;
import com.smartbudget.repository.StockReminderActionRepository;

@ExtendWith(MockitoExtension.class)
class StockCheckReminderServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private StockReminderActionRepository stockReminderActionRepository;

    private StockCheckReminderService service;

    private static final String USER_SUB = "user-123";

    @BeforeEach
    void setUp() {

        PackUnitNormalizationService packUnitNormalizationService = new PackUnitNormalizationService();

        service = new StockCheckReminderService(
                invoiceRepository,
                packUnitNormalizationService,
                stockReminderActionRepository,
                "Australia/Melbourne");

        when(
                stockReminderActionRepository
                        .findAllByUser(USER_SUB))
                .thenReturn(
                        List.of());
    }

    @Test
    void shouldCreateDueReminderForExactWeeklyPattern() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        List.of(
                                createInvoice(
                                        "invoice-1",
                                        "F1001",
                                        "2026-10-01",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-2",
                                        "F1002",
                                        "2026-10-08",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-3",
                                        "F1003",
                                        "2026-10-15",
                                        createStoredNormalizedMilkItem())));

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        22));

        assertEquals(
                1,
                response.reminderCount());

        assertEquals(
                1,
                response.dueCount());

        assertEquals(
                0,
                response.overdueCount());

        assertEquals(
                0,
                response.upcomingCount());

        var reminder = response.reminders().get(0);

        assertEquals(
                StockCheckReminderStatus.DUE,
                reminder.status());

        assertEquals(
                7,
                reminder.typicalIntervalDays());

        assertEquals(
                "2026-10-15",
                reminder.lastPurchaseDate());

        assertEquals(
                "2026-10-22",
                reminder.expectedNextDate());

        assertEquals(
                7,
                reminder.daysSinceLastPurchase());

        assertEquals(
                0,
                reminder.daysFromExpectedDate());

        assertBigDecimalEquals(
                "12",
                reminder.lastNormalizedQuantity());

        assertEquals(
                "L",
                reminder.normalizedUnit());

        assertBigDecimalEquals(
                "1.95",
                reminder.lastNormalizedUnitPrice());
    }

    @Test
    void shouldAcceptSlightlyVariableRecurringPattern() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        List.of(
                                createInvoice(
                                        "invoice-1",
                                        "F2001",
                                        "2026-10-01",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-2",
                                        "F2002",
                                        "2026-10-08",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-3",
                                        "F2003",
                                        "2026-10-16",
                                        createStoredNormalizedMilkItem())));

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        23));

        assertEquals(
                1,
                response.reminderCount());

        var reminder = response.reminders().get(0);

        /*
         * Intervals:
         * 7 days
         * 8 days
         *
         * Median for two values:
         * (7 + 8) / 2 = 7.5
         * rounded = 8
         */
        assertEquals(
                8,
                reminder.typicalIntervalDays());

        assertEquals(
                "2026-10-24",
                reminder.expectedNextDate());

        assertEquals(
                StockCheckReminderStatus.UPCOMING,
                reminder.status());

        assertEquals(
                -1,
                reminder.daysFromExpectedDate());
    }

    @Test
    void shouldRejectIrregularPurchasePattern() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        List.of(
                                createInvoice(
                                        "invoice-1",
                                        "F3001",
                                        "2026-10-01",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-2",
                                        "F3002",
                                        "2026-10-03",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-3",
                                        "F3003",
                                        "2026-10-20",
                                        createStoredNormalizedMilkItem())));

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        22));

        assertEquals(
                0,
                response.reminderCount());

        assertTrue(
                response.reminders()
                        .isEmpty());
    }

    @Test
    void shouldRejectProductWithInsufficientHistory() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        List.of(
                                createInvoice(
                                        "invoice-1",
                                        "F4001",
                                        "2026-10-01",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-2",
                                        "F4002",
                                        "2026-10-08",
                                        createStoredNormalizedMilkItem())));

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        15));

        assertEquals(
                0,
                response.reminderCount());
    }

    @Test
    void shouldCreateUpcomingReminderTwoDaysBeforeExpectedDate() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        weeklyInvoices());

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        20));

        assertEquals(
                1,
                response.upcomingCount());

        assertEquals(
                StockCheckReminderStatus.UPCOMING,
                response.reminders()
                        .get(0)
                        .status());

        assertEquals(
                -2,
                response.reminders()
                        .get(0)
                        .daysFromExpectedDate());
    }

    @Test
    void shouldCreateOverdueReminderWhenPastUsualInterval() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        weeklyInvoices());

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        24));

        assertEquals(
                1,
                response.overdueCount());

        var reminder = response.reminders().get(0);

        assertEquals(
                StockCheckReminderStatus.OVERDUE,
                reminder.status());

        assertEquals(
                2,
                reminder.daysFromExpectedDate());

        assertEquals(
                9,
                reminder.daysSinceLastPurchase());
    }

    @Test
    void shouldNormalizeLegacyLatestPurchaseForReminderDisplay() {

        InvoiceLineItemEntity legacyItem = createRawMilkItem();

        /*
         * Deliberately leave:
         *
         * normalizedQuantity
         * normalizedUnit
         * normalizedUnitPrice
         *
         * as null.
         */

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        List.of(
                                createInvoice(
                                        "invoice-1",
                                        "F5001",
                                        "2026-10-01",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-2",
                                        "F5002",
                                        "2026-10-08",
                                        createStoredNormalizedMilkItem()),
                                createInvoice(
                                        "invoice-3",
                                        "F5003",
                                        "2026-10-15",
                                        legacyItem)));

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        22));

        assertEquals(
                1,
                response.reminderCount());

        var reminder = response.reminders().get(0);

        assertBigDecimalEquals(
                "12",
                reminder.lastNormalizedQuantity());

        assertEquals(
                "L",
                reminder.normalizedUnit());

        assertBigDecimalEquals(
                "1.95",
                reminder.lastNormalizedUnitPrice());
    }

    @Test
    void shouldRecalculatePatternWhenLateInvoiceIsAdded() {

        List<InvoiceEntity> historyWithMissingInvoice = List.of(
                createInvoice(
                        "invoice-1",
                        "F6001",
                        "2026-10-01",
                        createStoredNormalizedMilkItem()),
                createInvoice(
                        "invoice-2",
                        "F6002",
                        "2026-10-08",
                        createStoredNormalizedMilkItem()),
                createInvoice(
                        "invoice-4",
                        "F6004",
                        "2026-10-22",
                        createStoredNormalizedMilkItem()));

        List<InvoiceEntity> correctedHistory = List.of(
                createInvoice(
                        "invoice-1",
                        "F6001",
                        "2026-10-01",
                        createStoredNormalizedMilkItem()),
                createInvoice(
                        "invoice-2",
                        "F6002",
                        "2026-10-08",
                        createStoredNormalizedMilkItem()),

                /*
                 * This invoice was entered late,
                 * but its invoice date places it
                 * correctly between 08 and 22 Oct.
                 */
                createInvoice(
                        "invoice-3",
                        "F6003",
                        "2026-10-15",
                        createStoredNormalizedMilkItem()),

                createInvoice(
                        "invoice-4",
                        "F6004",
                        "2026-10-22",
                        createStoredNormalizedMilkItem()));

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        historyWithMissingInvoice,
                        correctedHistory);

        StockCheckRemindersResponse beforeLateInvoice = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        29));

        /*
         * Gaps are 7 and 14 days.
         * With limited history this isn't
         * consistent enough.
         */
        assertEquals(
                0,
                beforeLateInvoice.reminderCount());

        StockCheckRemindersResponse afterLateInvoice = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        29));

        /*
         * Corrected history:
         *
         * 01 -> 08 = 7
         * 08 -> 15 = 7
         * 15 -> 22 = 7
         *
         * Expected next = 29 Oct.
         */
        assertEquals(
                1,
                afterLateInvoice.reminderCount());

        var reminder = afterLateInvoice.reminders()
                .get(0);

        assertEquals(
                7,
                reminder.typicalIntervalDays());

        assertEquals(
                "2026-10-29",
                reminder.expectedNextDate());

        assertEquals(
                StockCheckReminderStatus.DUE,
                reminder.status());
    }

    private List<InvoiceEntity> weeklyInvoices() {

        return List.of(
                createInvoice(
                        "invoice-1",
                        "F7001",
                        "2026-10-01",
                        createStoredNormalizedMilkItem()),
                createInvoice(
                        "invoice-2",
                        "F7002",
                        "2026-10-08",
                        createStoredNormalizedMilkItem()),
                createInvoice(
                        "invoice-3",
                        "F7003",
                        "2026-10-15",
                        createStoredNormalizedMilkItem()));
    }

    @Test
    void shouldHideIgnoredReminderOccurrence() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        weeklyInvoices());

        StockReminderActionEntity action = new StockReminderActionEntity();

        action.setUserSub(
                USER_SUB);

        action.setSortKey(
                "REMINDER#BIDFOOD#MILK001#2026-10-22");

        action.setSupplierId(
                "BIDFOOD");

        action.setProductCode(
                "MILK001");

        action.setExpectedNextDate(
                "2026-10-22");

        action.setActionType(
                "IGNORED");

        when(
                stockReminderActionRepository
                        .findAllByUser(USER_SUB))
                .thenReturn(
                        List.of(action));

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        22));

        assertEquals(
                0,
                response.reminderCount());

        assertTrue(
                response.reminders()
                        .isEmpty());
    }

    @Test
    void shouldHideOrderedReminderOccurrence() {

        when(invoiceRepository.findAllByUser(USER_SUB))
                .thenReturn(
                        weeklyInvoices());

        StockReminderActionEntity action = new StockReminderActionEntity();

        action.setUserSub(
                USER_SUB);

        action.setSortKey(
                "REMINDER#BIDFOOD#MILK001#2026-10-22");

        action.setSupplierId(
                "BIDFOOD");

        action.setProductCode(
                "MILK001");

        action.setExpectedNextDate(
                "2026-10-22");

        action.setActionType(
                "ORDERED");

        when(
                stockReminderActionRepository
                        .findAllByUser(USER_SUB))
                .thenReturn(
                        List.of(action));

        StockCheckRemindersResponse response = service.getStockCheckReminders(
                USER_SUB,
                LocalDate.of(
                        2026,
                        10,
                        22));

        assertEquals(
                0,
                response.reminderCount());
    }

    private InvoiceEntity createInvoice(
            String invoiceId,
            String invoiceNumber,
            String invoiceDate,
            InvoiceLineItemEntity item) {

        InvoiceEntity invoice = new InvoiceEntity();

        invoice.setInvoiceId(
                invoiceId);

        invoice.setSupplierId(
                "BIDFOOD");

        invoice.setSupplierName(
                "Bidfood");

        invoice.setInvoiceNumber(
                invoiceNumber);

        invoice.setInvoiceDate(
                invoiceDate);

        invoice.setItems(
                List.of(item));

        return invoice;
    }

    private InvoiceLineItemEntity createStoredNormalizedMilkItem() {

        InvoiceLineItemEntity item = createRawMilkItem();

        item.setNormalizedQuantity(
                new BigDecimal("12"));

        item.setNormalizedUnit(
                "L");

        item.setNormalizedUnitPrice(
                new BigDecimal("1.9500"));

        return item;
    }

    private InvoiceLineItemEntity createRawMilkItem() {

        InvoiceLineItemEntity item = new InvoiceLineItemEntity();

        item.setProductCode(
                "MILK001");

        item.setDescription(
                "FULL CREAM MILK");

        item.setBrand(
                "DEVONDALE");

        item.setPackSize(
                "2lt");

        item.setUnit(
                "BTL");

        item.setQuantity(
                new BigDecimal("6"));

        item.setUnitPrice(
                new BigDecimal("3.90"));

        item.setLineExGst(
                new BigDecimal("23.40"));

        return item;
    }

    private void assertBigDecimalEquals(
            String expected,
            BigDecimal actual) {

        assertEquals(
                0,
                new BigDecimal(expected)
                        .compareTo(actual));
    }
}