package com.smartbudget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartbudget.dto.InvoiceApprovalLineItem;
import com.smartbudget.dto.InvoiceApprovalRequest;
import com.smartbudget.dto.SupplierResolution;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.repository.InvoiceRepository;

@ExtendWith(MockitoExtension.class)
class InvoiceApprovalServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private SupplierNormalizationService supplierNormalizationService;

    @Mock
    private InvoiceValidationService invoiceValidationService;

    @Mock
    private InvoiceApprovalRequest request;

    @Mock
    private InvoiceApprovalLineItem lineItem;

    @Mock
    private SupplierResolution supplierResolution;

    @Mock
    private InvoiceValidationResult validationResult;

    private InvoiceApprovalService service;

    @BeforeEach
    void setUp() {

        PackUnitNormalizationService packUnitNormalizationService = new PackUnitNormalizationService();

        service = new InvoiceApprovalService(
                invoiceRepository,
                supplierNormalizationService,
                invoiceValidationService,
                packUnitNormalizationService);
    }

    @Test
    void shouldPersistRawAndNormalizedLineItemValues() {

        String userSub = "test-user";
        String draftId = "draft-123";

        /*
         * Approval request
         */
        when(request.invoiceDraftId())
                .thenReturn(draftId);

        when(request.s3Keys())
                .thenReturn(
                        List.of(
                                "invoices/test-user/draft-123/document.pdf"));

        when(request.supplier())
                .thenReturn("M & J CHICKENS");

        when(request.invoiceNumber())
                .thenReturn("VI-3617093");

        when(request.invoiceDate())
                .thenReturn("2026-09-28");

        when(request.subtotalExGst())
                .thenReturn(
                        new BigDecimal("82.35"));

        when(request.gst())
                .thenReturn(
                        BigDecimal.ZERO);

        when(request.total())
                .thenReturn(
                        new BigDecimal("82.35"));

        when(request.items())
                .thenReturn(
                        List.of(lineItem));

        /*
         * M&J raw invoice line:
         *
         * 3 PKT
         * each packet = 2KG
         * line ex GST = $41.70
         */
        when(lineItem.productCode())
                .thenReturn("FD222K");

        when(lineItem.description())
                .thenReturn(
                        "FSH NO.22 DRUMSTICKS 2KG PKT");

        when(lineItem.brand())
                .thenReturn(null);

        when(lineItem.packSize())
                .thenReturn("2KG");

        when(lineItem.unit())
                .thenReturn("PKT");

        when(lineItem.quantity())
                .thenReturn(
                        new BigDecimal("3"));

        when(lineItem.unitPrice())
                .thenReturn(
                        new BigDecimal("13.90"));

        when(lineItem.lineExGst())
                .thenReturn(
                        new BigDecimal("41.70"));

        when(lineItem.gstValue())
                .thenReturn(
                        BigDecimal.ZERO);

        when(lineItem.lineTotal())
                .thenReturn(
                        new BigDecimal("41.70"));

        when(lineItem.pageNumber())
                .thenReturn(1);

        /*
         * Shared validation already passed.
         */
        when(
                invoiceValidationService
                        .validate(request))
                .thenReturn(validationResult);

        when(validationResult.valid())
                .thenReturn(true);

        /*
         * Supplier resolution.
         */
        when(
                supplierNormalizationService
                        .resolve("M & J CHICKENS"))
                .thenReturn(supplierResolution);

        when(supplierResolution.recognized())
                .thenReturn(true);

        when(supplierResolution.supplierId())
                .thenReturn("MJCHICKENS");

        when(supplierResolution.supplierName())
                .thenReturn("M & J Chickens");

        when(supplierResolution.rawSupplierName())
                .thenReturn("M & J CHICKENS");

        /*
         * Execute approval.
         */
        service.approve(
                userSub,
                request);

        /*
         * Capture what would have been sent
         * to DynamoDB.
         */
        ArgumentCaptor<InvoiceEntity> invoiceCaptor = ArgumentCaptor.forClass(
                InvoiceEntity.class);

        verify(invoiceRepository)
                .save(
                        invoiceCaptor.capture());

        InvoiceEntity savedInvoice = invoiceCaptor.getValue();

        assertEquals(
                1,
                savedInvoice
                        .getItems()
                        .size());

        InvoiceLineItemEntity savedItem = savedInvoice
                .getItems()
                .get(0);

        /*
         * RAW DATA MUST REMAIN UNCHANGED
         */
        assertEquals(
                "FD222K",
                savedItem.getProductCode());

        assertEquals(
                "FSH NO.22 DRUMSTICKS 2KG PKT",
                savedItem.getDescription());

        assertEquals(
                "PKT",
                savedItem.getUnit());

        assertEquals(
                "2KG",
                savedItem.getPackSize());

        assertBigDecimalEquals(
                "3",
                savedItem.getQuantity());

        assertBigDecimalEquals(
                "13.90",
                savedItem.getUnitPrice());

        assertBigDecimalEquals(
                "41.70",
                savedItem.getLineExGst());

        /*
         * DERIVED NORMALIZED DATA
         *
         * 3 packets x 2KG
         * = 6KG
         *
         * $41.70 / 6KG
         * = $6.95/KG
         */
        assertBigDecimalEquals(
                "6",
                savedItem.getNormalizedQuantity());

        assertEquals(
                "KG",
                savedItem.getNormalizedUnit());

        assertBigDecimalEquals(
                "6.9500",
                savedItem.getNormalizedUnitPrice());
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