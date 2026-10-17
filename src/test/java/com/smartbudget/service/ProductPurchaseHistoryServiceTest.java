package com.smartbudget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartbudget.dto.ProductPurchaseHistoryResponse;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.repository.InvoiceRepository;

@ExtendWith(MockitoExtension.class)
class ProductPurchaseHistoryServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private ProductPurchaseHistoryService service;

    @BeforeEach
    void setUp() {

        PackUnitNormalizationService packUnitNormalizationService = new PackUnitNormalizationService();

        service = new ProductPurchaseHistoryService(
                invoiceRepository,
                packUnitNormalizationService);
    }

    @Test
    void shouldReturnStoredAndLegacyNormalizedPurchaseHistory() {

        String userSub = "user-123";

        InvoiceEntity newerInvoice = createInvoice(
                "invoice-new",
                "I71712245.TRU",
                "2026-09-27",
                createStoredNormalizedItem());

        InvoiceEntity legacyInvoice = createInvoice(
                "invoice-old",
                "I71682481.TRU",
                "2026-09-24",
                createLegacyItem());

        when(invoiceRepository.findAllByUser(userSub))
                .thenReturn(
                        List.of(
                                legacyInvoice,
                                newerInvoice));

        ProductPurchaseHistoryResponse response = service.getProductHistory(
                userSub,
                "BIDFOOD",
                "225452");

        assertEquals(
                "BIDFOOD",
                response.supplierId());

        assertEquals(
                "Bidfood",
                response.supplier());

        assertEquals(
                "225452",
                response.productCode());

        assertEquals(
                "JUICE CLEAR APPLE LONG LIFE 100% PET",
                response.description());

        assertEquals(
                "DEWFRESH",
                response.brand());

        assertEquals(
                2,
                response.purchaseCount());

        assertEquals(
                2,
                response.purchases().size());

        /*
         * Newest purchase must be first.
         */
        var newest = response.purchases().get(0);

        assertEquals(
                "invoice-new",
                newest.invoiceId());

        assertEquals(
                "I71712245.TRU",
                newest.invoiceNumber());

        assertEquals(
                "2026-09-27",
                newest.invoiceDate());

        assertBigDecimalEquals(
                "12",
                newest.normalizedQuantity());

        assertEquals(
                "L",
                newest.normalizedUnit());

        assertBigDecimalEquals(
                "1.95",
                newest.normalizedUnitPrice());

        /*
         * Legacy invoice has no normalized values
         * stored in DynamoDB.
         *
         * History service should derive them from:
         *
         * 6 BTL × 2lt
         * = 12 L
         *
         * $23.40 / 12
         * = $1.95/L
         */
        var legacy = response.purchases().get(1);

        assertEquals(
                "invoice-old",
                legacy.invoiceId());

        assertEquals(
                "I71682481.TRU",
                legacy.invoiceNumber());

        assertEquals(
                "2026-09-24",
                legacy.invoiceDate());

        assertBigDecimalEquals(
                "12",
                legacy.normalizedQuantity());

        assertEquals(
                "L",
                legacy.normalizedUnit());

        assertBigDecimalEquals(
                "1.95",
                legacy.normalizedUnitPrice());
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

    private InvoiceLineItemEntity createStoredNormalizedItem() {

        InvoiceLineItemEntity item = createRawAppleJuiceItem();

        item.setNormalizedQuantity(
                new BigDecimal("12"));

        item.setNormalizedUnit(
                "L");

        item.setNormalizedUnitPrice(
                new BigDecimal("1.9500"));

        return item;
    }

    private InvoiceLineItemEntity createLegacyItem() {

        /*
         * Deliberately leave all three
         * normalized fields null.
         */
        return createRawAppleJuiceItem();
    }

    private InvoiceLineItemEntity createRawAppleJuiceItem() {

        InvoiceLineItemEntity item = new InvoiceLineItemEntity();

        item.setProductCode(
                "225452");

        item.setDescription(
                "JUICE CLEAR APPLE LONG LIFE 100% PET");

        item.setBrand(
                "DEWFRESH");

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