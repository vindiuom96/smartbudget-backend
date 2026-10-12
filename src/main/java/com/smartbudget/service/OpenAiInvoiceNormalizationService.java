package com.smartbudget.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.InvoiceLineItemReview;
import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.dto.OpenAiRawInvoiceResponse;
import com.smartbudget.dto.OpenAiRawLineItem;

@Service
public class OpenAiInvoiceNormalizationService {

        private static final BigDecimal MONEY_TOTAL_TOLERANCE = new BigDecimal("0.001");

        private static final BigDecimal MIN_LINE_CALCULATION_TOLERANCE = new BigDecimal("0.02");

        private static final BigDecimal HALF_CENT = new BigDecimal("0.005");

        private enum GenericAmountMeaning {
                NONE,
                ZERO_GST,
                EX_GST,
                UNKNOWN
        }

        public InvoiceReviewResponse normalize(
                        OpenAiRawInvoiceResponse raw) {

                List<String> invoiceWarnings = new ArrayList<>();

                BigDecimal subtotal = raw.subtotalExGst();

                BigDecimal gst = raw.gst();

                BigDecimal total = raw.total();

                /*
                 * If subtotal and total are explicitly printed
                 * and equal, GST is mathematically zero.
                 */
                if (gst == null
                                && subtotal != null
                                && total != null
                                && moneyMatches(
                                                subtotal,
                                                total)) {

                        gst = BigDecimal.ZERO;
                }
                /*
                 * If GST is explicitly zero and total is present,
                 * a missing subtotal can be recovered safely.
                 *
                 * subtotal + 0 = total
                 * therefore subtotal = total.
                 */
                if (subtotal == null
                                && isZero(gst)
                                && total != null) {

                        subtotal = total;
                }

                /*
                 * Fallback for invoices where OpenAI reads the final total
                 * and every row can be reconstructed, but misses the
                 * printed subtotal/GST.
                 */
                if (subtotal == null
                                && gst == null
                                && total != null) {

                        BigDecimal reconstructedSubtotal = reconstructPossibleZeroGstSubtotal(
                                        raw);

                        if (reconstructedSubtotal != null
                                        && moneyMatches(
                                                        reconstructedSubtotal,
                                                        total)) {

                                subtotal = reconstructedSubtotal;

                                gst = BigDecimal.ZERO;

                                invoiceWarnings.add(
                                                "Invoice subtotal and GST were reconstructed from "
                                                                + "line-item arithmetic because the printed "
                                                                + "subtotal/GST were not extracted. "
                                                                + "Please confirm GST is zero.");
                        }
                }

                GenericAmountMeaning genericAmountMeaning = determineGenericAmountMeaning(
                                raw,
                                subtotal,
                                gst);

                validateHeader(
                                raw,
                                subtotal,
                                gst,
                                total,
                                invoiceWarnings);

                List<InvoiceLineItemReview> items = new ArrayList<>();

                if (raw.items() != null) {

                        for (OpenAiRawLineItem rawItem : raw.items()) {

                                items.add(
                                                normalizeLineItem(
                                                                rawItem,
                                                                gst,
                                                                genericAmountMeaning));
                        }
                }

                validateInvoiceMath(
                                subtotal,
                                gst,
                                total,
                                items,
                                invoiceWarnings);

                boolean itemNeedsReview = items.stream()
                                .anyMatch(
                                                InvoiceLineItemReview::needsReview);

                boolean needsReview = !invoiceWarnings.isEmpty()
                                || itemNeedsReview;

                int pageCount = calculatePageCount(items);

                return new InvoiceReviewResponse(
                                raw.supplier(),
                                raw.invoiceNumber(),
                                raw.invoiceDate(),
                                subtotal,
                                gst,
                                total,
                                needsReview,
                                pageCount,
                                invoiceWarnings,
                                items);
        }

        private InvoiceLineItemReview normalizeLineItem(
                        OpenAiRawLineItem raw,
                        BigDecimal invoiceGst,
                        GenericAmountMeaning genericAmountMeaning) {

                List<String> warnings = new ArrayList<>();

                BigDecimal lineExGst = raw.lineExGst();

                BigDecimal gstValue = raw.gstValue();

                BigDecimal lineTotal = raw.lineTotal();

                /*
                 * Interpret an ambiguous row Amount/Value/Total only when
                 * invoice-level arithmetic makes the meaning deterministic.
                 */
                if (raw.amount() != null
                                && lineExGst == null
                                && gstValue == null
                                && lineTotal == null) {

                        if (genericAmountMeaning == GenericAmountMeaning.ZERO_GST) {

                                lineExGst = raw.amount();

                                gstValue = BigDecimal.ZERO;

                                lineTotal = raw.amount();

                        } else if (genericAmountMeaning == GenericAmountMeaning.EX_GST) {

                                /*
                                 * Example: Peters prints GST only at invoice level.
                                 * The row totals sum exactly to the printed ex-GST
                                 * subtotal, so they are safe to use as lineExGst.
                                 */
                                lineExGst = raw.amount();

                        } else {

                                warnings.add(
                                                "Generic amount cannot be safely classified "
                                                                + "from the invoice totals.");
                        }
                }

                /*
                 * Zero-GST fallback if there is no generic amount.
                 */
                if (isZero(invoiceGst)
                                && raw.amount() == null
                                && lineExGst == null
                                && gstValue == null
                                && lineTotal == null
                                && raw.quantity() != null
                                && raw.unitPrice() != null) {

                        BigDecimal calculatedLineAmount = raw.quantity()
                                        .multiply(
                                                        raw.unitPrice());

                        lineExGst = calculatedLineAmount;

                        gstValue = BigDecimal.ZERO;

                        lineTotal = calculatedLineAmount;
                }

                if (raw.amount() != null
                                && (raw.lineExGst() != null
                                                || raw.gstValue() != null
                                                || raw.lineTotal() != null)) {

                        warnings.add(
                                        "Both generic amount and GST-specific "
                                                        + "line values were extracted.");
                }

                if (isBlank(raw.description())) {

                        warnings.add(
                                        "Line item description is missing.");
                }

                if (raw.quantity() == null) {

                        warnings.add(
                                        "Line item quantity is missing.");
                }

                if (raw.unitPrice() == null) {

                        warnings.add(
                                        "Line item unit price is missing.");
                }

                /*
                 * SmartBudget requires a line ex-GST amount for
                 * subtotal reconciliation and future price analytics.
                 *
                 * Line GST and line total are optional because some
                 * suppliers print GST only at invoice level.
                 */
                if (lineExGst == null) {

                        warnings.add(
                                        "Line ex-GST value is missing.");
                }

                if (raw.quantity() != null
                                && raw.unitPrice() != null
                                && lineExGst != null
                                && !lineCalculationMatches(
                                                raw.quantity(),
                                                raw.unitPrice(),
                                                lineExGst)) {

                        warnings.add(
                                        "Quantity x unit price does not match line ex-GST value.");
                }

                if (lineExGst != null
                                && gstValue != null
                                && lineTotal != null) {

                        BigDecimal calculated = lineExGst.add(
                                        gstValue);

                        if (!moneyMatches(
                                        calculated,
                                        lineTotal)) {

                                warnings.add(
                                                "Line ex-GST value + GST does not "
                                                                + "match line total.");
                        }
                }

                boolean needsReview = !warnings.isEmpty();

                return new InvoiceLineItemReview(
                                raw.productCode(),
                                raw.description(),
                                raw.brand(),
                                raw.packSize(),
                                raw.unit(),
                                raw.quantity(),
                                raw.unitPrice(),
                                lineExGst,
                                gstValue,
                                lineTotal,
                                null,
                                needsReview,
                                raw.pageNumber(),
                                warnings);
        }

        private GenericAmountMeaning determineGenericAmountMeaning(
                        OpenAiRawInvoiceResponse raw,
                        BigDecimal subtotal,
                        BigDecimal gst) {

                if (raw.items() == null
                                || raw.items().isEmpty()) {

                        return GenericAmountMeaning.NONE;
                }

                boolean hasGenericAmount = false;

                BigDecimal possibleExGstSum = BigDecimal.ZERO;

                for (OpenAiRawLineItem item : raw.items()) {

                        if (item.lineExGst() != null) {

                                possibleExGstSum = possibleExGstSum.add(
                                                item.lineExGst());

                                continue;
                        }

                        if (item.amount() != null) {

                                hasGenericAmount = true;

                                possibleExGstSum = possibleExGstSum.add(
                                                item.amount());

                                continue;
                        }

                        return hasGenericAmount
                                        ? GenericAmountMeaning.UNKNOWN
                                        : GenericAmountMeaning.NONE;
                }

                if (!hasGenericAmount) {

                        return GenericAmountMeaning.NONE;
                }

                if (isZero(gst)) {

                        return GenericAmountMeaning.ZERO_GST;
                }

                if (subtotal != null
                                && moneyMatches(
                                                possibleExGstSum,
                                                subtotal)) {

                        return GenericAmountMeaning.EX_GST;
                }

                return GenericAmountMeaning.UNKNOWN;
        }

        private void validateHeader(
                        OpenAiRawInvoiceResponse raw,
                        BigDecimal subtotal,
                        BigDecimal gst,
                        BigDecimal total,
                        List<String> warnings) {

                if (isBlank(raw.supplier())) {

                        warnings.add(
                                        "Supplier is missing.");
                }

                if (isBlank(raw.invoiceNumber())) {

                        warnings.add(
                                        "Invoice number is missing.");
                }

                if (isBlank(raw.invoiceDate())) {

                        warnings.add(
                                        "Invoice date is missing.");

                } else {

                        try {

                                LocalDate.parse(
                                                raw.invoiceDate());

                        } catch (DateTimeParseException e) {

                                warnings.add(
                                                "Invoice date is not in YYYY-MM-DD format.");
                        }
                }

                if (subtotal == null) {

                        warnings.add(
                                        "Invoice subtotal ex-GST is missing.");
                }

                if (gst == null) {

                        warnings.add(
                                        "Invoice GST is missing.");
                }

                if (total == null) {

                        warnings.add(
                                        "Invoice total is missing.");
                }
        }

        private void validateInvoiceMath(
                        BigDecimal subtotal,
                        BigDecimal gst,
                        BigDecimal total,
                        List<InvoiceLineItemReview> items,
                        List<String> warnings) {

                if (subtotal != null
                                && gst != null
                                && total != null) {

                        BigDecimal calculated = subtotal.add(gst);

                        if (!moneyMatches(
                                        calculated,
                                        total)) {

                                warnings.add(
                                                "Invoice subtotal + GST does not "
                                                                + "match invoice total.");
                        }
                }

                if (items.isEmpty()) {

                        warnings.add(
                                        "No invoice line items were extracted.");

                        return;
                }

                boolean allExGstPresent = items.stream()
                                .allMatch(
                                                item -> item.lineExGst() != null);

                boolean allGstPresent = items.stream()
                                .allMatch(
                                                item -> item.gstValue() != null);

                boolean allTotalsPresent = items.stream()
                                .allMatch(
                                                item -> item.lineTotal() != null);

                if (allExGstPresent
                                && subtotal != null) {

                        BigDecimal extractedSubtotal = items.stream()
                                        .map(
                                                        InvoiceLineItemReview::lineExGst)
                                        .reduce(
                                                        BigDecimal.ZERO,
                                                        BigDecimal::add);

                        if (!moneyMatches(
                                        extractedSubtotal,
                                        subtotal)) {

                                warnings.add(
                                                "Extracted line items do not add up "
                                                                + "to invoice subtotal ex-GST.");
                        }
                }

                /*
                 * These two checks are intentionally conditional.
                 * Header-only GST invoices are valid even when each row
                 * does not expose GST or a GST-inclusive line total.
                 */
                if (allGstPresent
                                && gst != null) {

                        BigDecimal extractedGst = items.stream()
                                        .map(
                                                        InvoiceLineItemReview::gstValue)
                                        .reduce(
                                                        BigDecimal.ZERO,
                                                        BigDecimal::add);

                        if (!moneyMatches(
                                        extractedGst,
                                        gst)) {

                                warnings.add(
                                                "Extracted line item GST does not "
                                                                + "add up to invoice GST.");
                        }
                }

                if (allTotalsPresent
                                && total != null) {

                        BigDecimal extractedTotal = items.stream()
                                        .map(
                                                        InvoiceLineItemReview::lineTotal)
                                        .reduce(
                                                        BigDecimal.ZERO,
                                                        BigDecimal::add);

                        if (!moneyMatches(
                                        extractedTotal,
                                        total)) {

                                warnings.add(
                                                "Extracted line totals do not add up "
                                                                + "to invoice total.");
                        }
                }
        }

        private BigDecimal reconstructPossibleZeroGstSubtotal(
                        OpenAiRawInvoiceResponse raw) {

                if (raw.items() == null
                                || raw.items().isEmpty()) {

                        return null;
                }

                BigDecimal reconstructedSubtotal = BigDecimal.ZERO;

                for (OpenAiRawLineItem item : raw.items()) {

                        BigDecimal lineAmount = item.amount();

                        if (lineAmount == null
                                        && item.quantity() != null
                                        && item.unitPrice() != null) {

                                lineAmount = item.quantity()
                                                .multiply(
                                                                item.unitPrice());
                        }

                        if (lineAmount == null) {

                                return null;
                        }

                        reconstructedSubtotal = reconstructedSubtotal.add(
                                        lineAmount);
                }

                return reconstructedSubtotal;
        }

        private boolean lineCalculationMatches(
                        BigDecimal quantity,
                        BigDecimal unitPrice,
                        BigDecimal lineAmount) {

                BigDecimal calculated = quantity.multiply(
                                unitPrice);

                BigDecimal roundingAllowance = quantity.abs()
                                .multiply(
                                                HALF_CENT)
                                .add(
                                                HALF_CENT);

                BigDecimal allowedTolerance = roundingAllowance.max(
                                MIN_LINE_CALCULATION_TOLERANCE);

                return difference(
                                calculated,
                                lineAmount)
                                .compareTo(
                                                allowedTolerance) <= 0;
        }

        private boolean moneyMatches(
                        BigDecimal first,
                        BigDecimal second) {

                if (first == null
                                || second == null) {

                        return false;
                }

                return difference(
                                first,
                                second)
                                .compareTo(
                                                MONEY_TOTAL_TOLERANCE) <= 0;
        }

        private BigDecimal difference(
                        BigDecimal first,
                        BigDecimal second) {

                return first
                                .subtract(second)
                                .abs();
        }

        private boolean isZero(
                        BigDecimal value) {

                return value != null
                                && value.compareTo(
                                                BigDecimal.ZERO) == 0;
        }

        private int calculatePageCount(
                        List<InvoiceLineItemReview> items) {

                return items.stream()
                                .map(
                                                InvoiceLineItemReview::pageNumber)
                                .filter(
                                                page -> page != null
                                                                && page > 0)
                                .max(
                                                Integer::compareTo)
                                .orElse(
                                                items.isEmpty()
                                                                ? 0
                                                                : 1);
        }

        private boolean isBlank(
                        String value) {

                return value == null
                                || value.isBlank();
        }
}
