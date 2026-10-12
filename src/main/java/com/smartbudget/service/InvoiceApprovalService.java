package com.smartbudget.service;

import java.time.Instant;
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

        private final InvoiceRepository invoiceRepository;
        private final SupplierNormalizationService supplierNormalizationService;
        private final InvoiceValidationService invoiceValidationService;

        public InvoiceApprovalService(
                        InvoiceRepository invoiceRepository,
                        SupplierNormalizationService supplierNormalizationService,
                        InvoiceValidationService invoiceValidationService) {

                this.invoiceRepository = invoiceRepository;
                this.supplierNormalizationService = supplierNormalizationService;
                this.invoiceValidationService = invoiceValidationService;
        }

        public InvoiceApprovalResponse approve(
                        String userSub,
                        InvoiceApprovalRequest request) {

                /*
                 * 1. Validate approval-specific request data.
                 *
                 * Generic invoice correctness is handled by
                 * InvoiceValidationService below.
                 */
                validateApprovalContext(request);

                /*
                 * 2. Run the shared deterministic invoice validation.
                 *
                 * This same validator is also used by /revalidate,
                 * so the financial rules have one source of truth.
                 */
                InvoiceValidationResult validation = invoiceValidationService.validate(request);

                if (!validation.valid()) {
                        throw badRequest(
                                        buildValidationMessage(validation));
                }

                /*
                 * 3. Make sure the S3 objects really belong
                 * to this authenticated user/draft.
                 */
                validateS3Ownership(
                                userSub,
                                request);

                /*
                 * 4. Resolve the supplier name to one
                 * canonical supplier identity.
                 */
                SupplierResolution supplier = supplierNormalizationService.resolve(
                                request.supplier());

                /*
                 * This is also checked by InvoiceValidationService,
                 * but approval resolves the canonical supplier again
                 * because the resolved identity is required for
                 * persistence and the duplicate key.
                 */
                if (!supplier.recognized()) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Supplier could not be confidently identified. "
                                                        + "Supplier confirmation is required.");
                }

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

        /*
         * Approval-specific fields that are not part of
         * the generic financial/business validation.
         */
        private void validateApprovalContext(
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

        /*
         * Convert all shared validation warnings into the
         * single message used by the approval endpoint.
         */
        private String buildValidationMessage(
                        InvoiceValidationResult validation) {

                List<String> messages = new ArrayList<>();

                messages.addAll(
                                validation.warnings());

                validation.items()
                                .forEach(item -> item.warnings()
                                                .forEach(warning -> messages.add(
                                                                "Item "
                                                                                + (item.itemIndex() + 1)
                                                                                + ": "
                                                                                + warning)));

                if (messages.isEmpty()) {
                        return "Invoice validation failed.";
                }

                return String.join(
                                " ",
                                messages);
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
