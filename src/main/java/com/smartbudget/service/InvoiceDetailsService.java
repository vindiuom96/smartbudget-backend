package com.smartbudget.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.InvoiceDetailsLineItemResponse;
import com.smartbudget.dto.InvoiceDetailsResponse;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.repository.InvoiceRepository;

@Service
public class InvoiceDetailsService {

    private final InvoiceRepository invoiceRepository;

    public InvoiceDetailsService(
            InvoiceRepository invoiceRepository) {

        this.invoiceRepository = invoiceRepository;
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
                item.getPageNumber());
    }
}