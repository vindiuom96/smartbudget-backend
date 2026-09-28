package com.smartbudget.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.InvoiceApprovalLineItem;
import com.smartbudget.dto.InvoiceApprovalRequest;
import com.smartbudget.dto.InvoiceApprovalResponse;
import com.smartbudget.dto.SupplierResolution;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.repository.InvoiceRepository;

import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Service
public class InvoiceApprovalService {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

    private final InvoiceRepository invoiceRepository;
    private final SupplierNormalizationService supplierNormalizationService;

    public InvoiceApprovalService(
            InvoiceRepository invoiceRepository,
            SupplierNormalizationService supplierNormalizationService) {

        this.invoiceRepository = invoiceRepository;
        this.supplierNormalizationService = supplierNormalizationService;
    }

    public InvoiceApprovalResponse approve(
            String userSub,
            InvoiceApprovalRequest request) {

        /*
         * 1. Validate basic request data.
         */
        validateRequiredFields(request);

        /*
         * 2. Make sure the S3 objects really belong
         * to this authenticated user/draft.
         */
        validateS3Ownership(
                userSub,
                request);

        /*
         * 3. Resolve the AI supplier name to one
         * canonical supplier identity.
         */
        SupplierResolution supplier = supplierNormalizationService.resolve(
                request.supplier());

        if (!supplier.recognized()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supplier could not be confidently identified. "
                            + "Supplier confirmation is required.");
        }

        /*
         * 4. Validate all invoice arithmetic again.
         *
         * We never trust the React approval request
         * without backend validation.
         */
        validateFinancials(request);

        /*
         * 5. Generate internal invoice ID.
         */
        String invoiceId = UUID.randomUUID().toString();

        /*
         * 6. Build deterministic duplicate key.
         *
         * Same supplier + same invoice number
         * produces the same DynamoDB key.
         */
        String sortKey = buildInvoiceSortKey(
                supplier.supplierId(),
                request.invoiceNumber());

        /*
         * 7. Convert approved DTO into DynamoDB entity.
         */
        InvoiceEntity invoice = new InvoiceEntity();

        invoice.setUserSub(userSub);
        invoice.setSortKey(sortKey);

        invoice.setInvoiceId(invoiceId);
        invoice.setInvoiceDraftId(
                request.invoiceDraftId());

        invoice.setSupplierId(
                supplier.supplierId());

        invoice.setSupplierName(
                supplier.supplierName());

        invoice.setRawSupplierName(
                supplier.rawSupplierName());

        invoice.setInvoiceNumber(
                request.invoiceNumber().trim());

        invoice.setInvoiceDate(
                request.invoiceDate());

        invoice.setSubtotalExGst(
                request.subtotalExGst());

        invoice.setGst(
                request.gst());

        invoice.setTotal(
                request.total());

        invoice.setS3Keys(
                List.copyOf(request.s3Keys()));

        invoice.setItems(
                mapLineItems(request.items()));

        String approvedAt = Instant.now().toString();

        invoice.setApprovedAt(
                approvedAt);

        /*
         * 8. Conditional DynamoDB save.
         *
         * If the same supplier + invoice number
         * already exists, DynamoDB rejects it.
         */
        try {

            invoiceRepository.save(invoice);

        } catch (ConditionalCheckFailedException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This invoice has already been saved.");
        }

        return new InvoiceApprovalResponse(
                invoiceId,
                supplier.supplierName(),
                request.invoiceNumber(),
                approvedAt);
    }

    private void validateRequiredFields(
            InvoiceApprovalRequest request) {

        if (request == null) {

            throw badRequest(
                    "Invoice approval request is required.");
        }

        if (isBlank(request.invoiceDraftId())) {

            throw badRequest(
                    "invoiceDraftId is required.");
        }

        if (request.s3Keys() == null
                || request.s3Keys().isEmpty()) {

            throw badRequest(
                    "At least one s3Key is required.");
        }

        if (isBlank(request.supplier())) {

            throw badRequest(
                    "Supplier is required.");
        }

        if (isBlank(request.invoiceNumber())) {

            throw badRequest(
                    "Invoice number is required.");
        }

        if (isBlank(request.invoiceDate())) {

            throw badRequest(
                    "Invoice date is required.");
        }

        try {

            LocalDate.parse(
                    request.invoiceDate());

        } catch (DateTimeParseException e) {

            throw badRequest(
                    "Invoice date must be in YYYY-MM-DD format.");
        }

        if (request.subtotalExGst() == null) {

            throw badRequest(
                    "Invoice subtotal ex-GST is required.");
        }

        if (request.gst() == null) {

            throw badRequest(
                    "Invoice GST is required.");
        }

        if (request.total() == null) {

            throw badRequest(
                    "Invoice total is required.");
        }

        if (request.items() == null
                || request.items().isEmpty()) {

            throw badRequest(
                    "Invoice must contain at least one line item.");
        }
    }

    private void validateS3Ownership(
            String userSub,
            InvoiceApprovalRequest request) {

        if (isBlank(userSub)) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required.");
        }

        String expectedPrefix = "invoices/"
                + userSub
                + "/"
                + request.invoiceDraftId()
                + "/";

        boolean invalidKey = request.s3Keys()
                .stream()
                .anyMatch(
                        key -> key == null
                                || !key.startsWith(
                                        expectedPrefix));

        if (invalidKey) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invoice S3 key does not belong "
                            + "to this user/draft.");
        }
    }

    private void validateFinancials(
            InvoiceApprovalRequest request) {

        BigDecimal expectedTotal = request.subtotalExGst()
                .add(request.gst());

        if (!closeEnough(
                expectedTotal,
                request.total())) {

            throw badRequest(
                    "Invoice subtotal + GST does not "
                            + "match invoice total.");
        }

        BigDecimal lineSubtotal = BigDecimal.ZERO;

        BigDecimal lineGst = BigDecimal.ZERO;

        BigDecimal lineTotal = BigDecimal.ZERO;

        for (int index = 0; index < request.items().size(); index++) {

            InvoiceApprovalLineItem item = request.items().get(index);

            validateLineItem(
                    item,
                    index + 1);

            lineSubtotal = lineSubtotal.add(
                    item.lineExGst());

            lineGst = lineGst.add(
                    item.gstValue());

            lineTotal = lineTotal.add(
                    item.lineTotal());
        }

        if (!closeEnough(
                lineSubtotal,
                request.subtotalExGst())) {

            throw badRequest(
                    "Line-item ex-GST values do not "
                            + "match invoice subtotal.");
        }

        if (!closeEnough(
                lineGst,
                request.gst())) {

            throw badRequest(
                    "Line-item GST values do not "
                            + "match invoice GST.");
        }

        if (!closeEnough(
                lineTotal,
                request.total())) {

            throw badRequest(
                    "Line-item totals do not "
                            + "match invoice total.");
        }
    }

    private void validateLineItem(
            InvoiceApprovalLineItem item,
            int lineNumber) {

        if (item == null) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " is missing.");
        }

        if (isBlank(item.description())) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " description is required.");
        }

        if (item.quantity() == null) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " quantity is required.");
        }

        if (item.unitPrice() == null) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " unit price is required.");
        }

        if (item.lineExGst() == null) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " ex-GST value is required.");
        }

        if (item.gstValue() == null) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " GST value is required.");
        }

        if (item.lineTotal() == null) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " total is required.");
        }

        /*
         * quantity × unitPrice ≈ lineExGst
         */
        BigDecimal calculatedExGst = item.quantity()
                .multiply(
                        item.unitPrice());

        if (!closeEnough(
                calculatedExGst,
                item.lineExGst())) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " quantity × unit price does not "
                            + "match ex-GST value.");
        }

        /*
         * lineExGst + GST ≈ lineTotal
         */
        BigDecimal calculatedTotal = item.lineExGst()
                .add(
                        item.gstValue());

        if (!closeEnough(
                calculatedTotal,
                item.lineTotal())) {

            throw badRequest(
                    "Line item "
                            + lineNumber
                            + " ex-GST + GST does not "
                            + "match line total.");
        }
    }

    private List<InvoiceLineItemEntity> mapLineItems(
            List<InvoiceApprovalLineItem> items) {

        List<InvoiceLineItemEntity> entities = new ArrayList<>();

        for (InvoiceApprovalLineItem item : items) {

            InvoiceLineItemEntity entity = new InvoiceLineItemEntity();

            entity.setProductCode(
                    item.productCode());

            entity.setDescription(
                    item.description());

            entity.setBrand(
                    item.brand());

            entity.setPackSize(
                    item.packSize());

            entity.setUnit(
                    item.unit());

            entity.setQuantity(
                    item.quantity());

            entity.setUnitPrice(
                    item.unitPrice());

            entity.setLineExGst(
                    item.lineExGst());

            entity.setGstValue(
                    item.gstValue());

            entity.setLineTotal(
                    item.lineTotal());

            entity.setPageNumber(
                    item.pageNumber());

            entities.add(entity);
        }

        return entities;
    }

    private String buildInvoiceSortKey(
            String supplierId,
            String invoiceNumber) {

        String normalizedInvoiceNumber = invoiceNumber
                .trim()
                .toUpperCase(Locale.ROOT);

        return "INVOICE#"
                + supplierId
                + "#"
                + normalizedInvoiceNumber;
    }

    private boolean closeEnough(
            BigDecimal first,
            BigDecimal second) {

        if (first == null
                || second == null) {

            return false;
        }

        return first.subtract(second)
                .abs()
                .compareTo(TOLERANCE) <= 0;
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.isBlank();
    }

    private ResponseStatusException badRequest(
            String message) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message);
    }
}