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

        private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

        public InvoiceReviewResponse normalize(
                        OpenAiRawInvoiceResponse raw) {

                List<String> invoiceWarnings = new ArrayList<>();

                BigDecimal subtotal = raw.subtotalExGst();
                BigDecimal gst = raw.gst();
                BigDecimal total = raw.total();

                /*
                 * Safe deterministic rule:
                 *
                 * If subtotal and total are explicitly printed
                 * and they are equal, GST must mathematically be zero.
                 */
                if (gst == null
                                && subtotal != null
                                && total != null
                                && closeEnough(subtotal, total)) {

                        gst = BigDecimal.ZERO;
                }

                /*
                 * Fallback for invoices where OpenAI reads the final total
                 * and the line values, but misses the printed subtotal/GST.
                 *
                 * We only apply this when EVERY line can be reconstructed and
                 * the reconstructed amount matches the printed invoice total.
                 *
                 * Generic amount fields do not prove by themselves that GST is
                 * zero, so this fallback intentionally leaves a review warning.
                 * The chef can confirm GST is zero and revalidate before saving.
                 */
                if (subtotal == null
                                && gst == null
                                && total != null) {

                        BigDecimal reconstructedSubtotal = reconstructPossibleZeroGstSubtotal(raw);

                        if (reconstructedSubtotal != null
                                        && closeEnough(
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
                                                                gst));
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
                        BigDecimal invoiceGst) {

                List<String> warnings = new ArrayList<>();

                BigDecimal lineExGst = raw.lineExGst();
                BigDecimal gstValue = raw.gstValue();
                BigDecimal lineTotal = raw.lineTotal();

                /*
                 * Generic AMOUNT handling.
                 *
                 * Only convert it automatically when
                 * the whole invoice has zero GST.
                 */
                if (raw.amount() != null
                                && lineExGst == null
                                && gstValue == null
                                && lineTotal == null) {

                        if (isZero(invoiceGst)) {

                                lineExGst = raw.amount();
                                gstValue = BigDecimal.ZERO;
                                lineTotal = raw.amount();

                        } else {

                                warnings.add(
                                                "Generic amount cannot be safely classified "
                                                                + "because invoice GST could not be confirmed as zero.");
                        }
                }

                /*
                 * If GST has already been established as zero and OpenAI also
                 * missed the generic amount, quantity * unitPrice can reconstruct
                 * the line amount deterministically.
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

                /*
                 * Seeing both a generic amount and explicit
                 * GST-specific values is suspicious.
                 */
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

                if (lineExGst == null) {

                        warnings.add(
                                        "Line ex-GST value is missing.");
                }

                if (gstValue == null) {

                        warnings.add(
                                        "Line GST value is missing.");
                }

                if (lineTotal == null) {

                        warnings.add(
                                        "Line total is missing.");
                }

                /*
                 * Financial validation:
                 *
                 * quantity * unitPrice ~= lineExGst
                 */
                if (raw.quantity() != null
                                && raw.unitPrice() != null
                                && lineExGst != null) {

                        BigDecimal calculated = raw.quantity()
                                        .multiply(
                                                        raw.unitPrice());

                        if (!closeEnough(
                                        calculated,
                                        lineExGst)) {

                                warnings.add(
                                                "Quantity x unit price does not "
                                                                + "match line ex-GST value.");
                        }
                }

                /*
                 * lineExGst + GST ~= lineTotal
                 */
                if (lineExGst != null
                                && gstValue != null
                                && lineTotal != null) {

                        BigDecimal calculated = lineExGst.add(
                                        gstValue);

                        if (!closeEnough(
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

                                // We deliberately do not trust an
                                // AI-generated confidence score.
                                null,

                                needsReview,
                                raw.pageNumber(),
                                warnings);
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

                /*
                 * Header arithmetic.
                 */
                if (subtotal != null
                                && gst != null
                                && total != null) {

                        BigDecimal calculated = subtotal.add(gst);

                        if (!closeEnough(
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

                        if (!closeEnough(
                                        extractedSubtotal,
                                        subtotal)) {

                                warnings.add(
                                                "Extracted line items do not add up "
                                                                + "to invoice subtotal ex-GST.");
                        }
                }

                if (allGstPresent
                                && gst != null) {

                        BigDecimal extractedGst = items.stream()
                                        .map(
                                                        InvoiceLineItemReview::gstValue)
                                        .reduce(
                                                        BigDecimal.ZERO,
                                                        BigDecimal::add);

                        if (!closeEnough(
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

                        if (!closeEnough(
                                        extractedTotal,
                                        total)) {

                                warnings.add(
                                                "Extracted line totals do not add up "
                                                                + "to invoice total.");
                        }
                }
        }

        /*
         * Reconstruct a possible zero-GST subtotal from raw line items.
         *
         * Priority:
         * 1. Use the raw generic amount when it exists.
         * 2. Otherwise use quantity * unitPrice.
         *
         * We return null unless EVERY row can be reconstructed. This avoids
         * deriving a subtotal from only a partial set of invoice items.
         */
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

        private int calculatePageCount(
                        List<InvoiceLineItemReview> items) {

                return items.stream()
                                .map(
                                                InvoiceLineItemReview::pageNumber)
                                .filter(
                                                page -> page != null
                                                                && page > 0)
                                .max(Integer::compareTo)
                                .orElse(
                                                items.isEmpty()
                                                                ? 0
                                                                : 1);
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

        private boolean isZero(
                        BigDecimal value) {

                return value != null
                                && value.compareTo(
                                                BigDecimal.ZERO) == 0;
        }

        private boolean isBlank(
                        String value) {

                return value == null
                                || value.isBlank();
        }
}
