package com.smartbudget.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.InvoiceDetailsLineItemResponse;
import com.smartbudget.dto.InvoiceDetailsResponse;
import com.smartbudget.dto.PackUnitNormalizationResult;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.repository.InvoiceRepository;

@Service
public class InvoiceDetailsService {

        private final InvoiceRepository invoiceRepository;
        private final PackUnitNormalizationService packUnitNormalizationService;

        public InvoiceDetailsService(
                        InvoiceRepository invoiceRepository,
                        PackUnitNormalizationService packUnitNormalizationService) {

                this.invoiceRepository = invoiceRepository;
                this.packUnitNormalizationService = packUnitNormalizationService;
        }

        public InvoiceDetailsResponse getInvoice(
                        String userSub,
                        String invoiceId) {

                InvoiceEntity invoice = invoiceRepository
                                .findByUserAndInvoiceId(
                                                userSub,
                                                invoiceId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Invoice not found."));

                List<InvoiceDetailsLineItemResponse> items = invoice.getItems() == null
                                ? List.of()
                                : invoice.getItems()
                                                .stream()
                                                .map(this::toLineItemResponse)
                                                .toList();

                return new InvoiceDetailsResponse(
                                invoice.getInvoiceId(),
                                invoice.getSupplierId(),
                                invoice.getSupplierName(),
                                invoice.getRawSupplierName(),
                                invoice.getInvoiceNumber(),
                                invoice.getInvoiceDate(),

                                invoice.getSubtotalExGst(),
                                invoice.getGst(),
                                invoice.getTotal(),

                                items.size(),
                                invoice.getApprovedAt(),

                                invoice.getS3Keys(),
                                items);
        }

        private InvoiceDetailsLineItemResponse toLineItemResponse(
                        InvoiceLineItemEntity item) {

                var normalizedQuantity = item.getNormalizedQuantity();

                var normalizedUnit = item.getNormalizedUnit();

                var normalizedUnitPrice = item.getNormalizedUnitPrice();

                /*
                 * Legacy invoice fallback.
                 *
                 * Older invoices may have been saved before
                 * support for pack formats such as "2 Litre"
                 * was added.
                 *
                 * Recalculate normalized values when they
                 * are missing instead of requiring the
                 * invoice to be uploaded again.
                 */
                if (normalizedQuantity == null
                                || normalizedUnit == null
                                || normalizedUnitPrice == null) {

                        PackUnitNormalizationResult normalization = packUnitNormalizationService.normalize(
                                        item.getQuantity(),
                                        item.getUnit(),
                                        item.getPackSize(),
                                        item.getLineExGst());

                        normalizedQuantity = normalization.normalizedQuantity();

                        normalizedUnit = normalization.normalizedUnit();

                        normalizedUnitPrice = normalization.normalizedUnitPrice();
                }

                return new InvoiceDetailsLineItemResponse(
                                item.getProductCode(),
                                item.getDescription(),
                                item.getBrand(),
                                item.getPackSize(),
                                item.getUnit(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getLineExGst(),
                                item.getGstValue(),
                                item.getLineTotal(),

                                normalizedQuantity,
                                normalizedUnit,
                                normalizedUnitPrice,

                                item.getPageNumber());
        }
}