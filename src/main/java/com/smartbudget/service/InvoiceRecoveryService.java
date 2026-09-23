package com.smartbudget.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.InvoiceLineItemReview;
import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.dto.RecoveredLineItem;

@Service
public class InvoiceRecoveryService {

    private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.05");

    public InvoiceReviewResponse applyTableRecovery(
            InvoiceReviewResponse original,
            List<RecoveredLineItem> recoveredItems) {

        if (recoveredItems == null || recoveredItems.isEmpty()) {
            return original;
        }

        /*
         * Only replace the original AnalyzeExpense line items when every
         * recovered TABLES row has passed row-level validation.
         */
        boolean allRowsValid = recoveredItems.stream()
                .allMatch(item -> item.valid()
                        && item.lineExGst() != null
                        && item.gstValue() != null
                        && item.lineTotal() != null);

        if (!allRowsValid) {
            return original;
        }

        BigDecimal recoveredSubtotal = recoveredItems.stream()
                .map(RecoveredLineItem::lineExGst)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recoveredGst = recoveredItems.stream()
                .map(RecoveredLineItem::gstValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recoveredTotal = recoveredItems.stream()
                .map(RecoveredLineItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        /*
         * TABLES recovery is trusted only when it reconciles with the
         * invoice-level financial values already extracted from the document.
         */
        if (original.subtotalExGst() == null
                || !moneyMatches(recoveredSubtotal, original.subtotalExGst())) {
            return original;
        }

        if (original.gst() != null
                && !moneyMatches(recoveredGst, original.gst())) {
            return original;
        }

        if (original.total() != null
                && !moneyMatches(recoveredTotal, original.total())) {
            return original;
        }

        /*
         * Internal consistency check as an additional guard:
         * subtotal + GST must equal total.
         */
        if (!moneyMatches(
                recoveredSubtotal.add(recoveredGst),
                recoveredTotal)) {
            return original;
        }

        List<InvoiceLineItemReview> recoveredReviewItems = recoveredItems.stream()
                .map(this::toInvoiceLineItem)
                .toList();

        /*
         * Remove warnings that the successful TABLES recovery has now
         * independently resolved. Keep unrelated warnings.
         */
        List<String> remainingWarnings = original.warnings() == null
                ? new ArrayList<>()
                : original.warnings()
                        .stream()
                        .filter(warning -> !isResolvedByTableRecovery(warning))
                        .toList();

        boolean needsReview = !remainingWarnings.isEmpty()
                || recoveredReviewItems.stream()
                        .anyMatch(InvoiceLineItemReview::needsReview);

        return new InvoiceReviewResponse(
                original.supplier(),
                original.invoiceNumber(),
                original.invoiceDate(),
                original.subtotalExGst(),
                original.gst(),
                original.total(),
                needsReview,
                original.pageCount(),
                remainingWarnings,
                recoveredReviewItems);
    }

    private InvoiceLineItemReview toInvoiceLineItem(
            RecoveredLineItem recovered) {

        return new InvoiceLineItemReview(
                recovered.productCode(),
                recovered.description(),
                recovered.brand(),
                recovered.packSize(),
                recovered.unit(),
                recovered.quantity(),
                recovered.unitPrice(),
                recovered.lineExGst(),
                recovered.gstValue(),
                recovered.lineTotal(),
                null,
                false,
                recovered.pageNumber(),
                List.of());
    }

    private boolean moneyMatches(
            BigDecimal first,
            BigDecimal second) {

        if (first == null || second == null) {
            return false;
        }

        return first
                .subtract(second)
                .abs()
                .compareTo(MONEY_TOLERANCE) <= 0;
    }

    private boolean isResolvedByTableRecovery(
            String warning) {

        if (warning == null) {
            return false;
        }

        String normalized = warning
                .toLowerCase(Locale.ROOT);

        return normalized.contains("extracted line items total")
                || normalized.contains("subtotal was derived from total minus gst");
    }
}
