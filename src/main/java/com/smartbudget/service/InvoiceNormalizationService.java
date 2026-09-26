package com.smartbudget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.ExtractedField;
import com.smartbudget.dto.ExtractedLineItem;
import com.smartbudget.dto.InvoiceLineItemReview;
import com.smartbudget.dto.InvoiceReviewResponse;
import com.smartbudget.dto.TextractResultResponse;

@Service
public class InvoiceNormalizationService {

    private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.05");

    /*
     * We deliberately do NOT use something like 90% here.
     *
     * Real SAJ invoices showed some correct numeric values
     * with lower confidence.
     *
     * Math validation is more important than confidence alone.
     */
    private static final float VERY_LOW_CONFIDENCE = 60.0f;

    public InvoiceReviewResponse normalize(
            List<TextractResultResponse> results) {

        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one Textract result is required");
        }

        validateResultsSucceeded(results);

        List<ExtractedField> summaryFields = results.stream()
                .flatMap(result -> result.summaryFields().stream())
                .toList();

        Supplier supplier = detectSupplier(summaryFields);

        String invoiceNumber = extractInvoiceNumber(summaryFields);

        String invoiceDate = extractInvoiceDate(summaryFields);

        BigDecimal subtotal = extractMoneyByType(
                summaryFields,
                "SUBTOTAL");

        BigDecimal gst = firstMoneyByTypes(
                summaryFields,
                "TAX",
                "TOTAL_TAX");

        BigDecimal total = extractBestTotal(summaryFields);

        List<InvoiceLineItemReview> items = normalizeLineItems(
                results,
                supplier);

        List<String> invoiceWarnings = new ArrayList<>();

        /*
         * If GST wasn't extracted at summary level,
         * try summing GST values from line items.
         *
         * This is particularly useful for Bidfood.
         */
        if (gst == null) {

            BigDecimal itemGst = items.stream()
                    .map(InvoiceLineItemReview::gstValue)
                    .filter(Objects::nonNull)
                    .reduce(
                            BigDecimal.ZERO,
                            BigDecimal::add);

            if (itemGst.compareTo(BigDecimal.ZERO) > 0) {
                gst = money(itemGst);
            }
        }

        /*
         * Example:
         *
         * total = 370.76
         * GST = 11.77
         *
         * therefore:
         * subtotal = 358.99
         *
         * Useful when Bidfood doesn't give Textract
         * a clean SUBTOTAL field.
         */
        if (subtotal == null
                && total != null
                && gst != null) {

            subtotal = money(total.subtract(gst));

            invoiceWarnings.add(
                    "Subtotal was derived from total minus GST.");
        }

        /*
         * Common for GST-free invoices such as some
         * produce invoices:
         *
         * subtotal == total
         *
         * therefore GST = 0.
         */
        if (gst == null
                && subtotal != null
                && total != null
                && closeEnough(subtotal, total)) {

            gst = BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP);
        }

        validateInvoiceTotals(
                supplier,
                items,
                subtotal,
                gst,
                total,
                invoiceWarnings);

        if (supplier == Supplier.UNKNOWN) {
            invoiceWarnings.add(
                    "Supplier could not be confidently normalized.");
        }

        if (invoiceNumber == null) {
            invoiceWarnings.add(
                    "Invoice number was not detected.");
        }

        if (invoiceDate == null) {
            invoiceWarnings.add(
                    "Invoice date was not detected.");
        }

        if (total == null) {
            invoiceWarnings.add(
                    "Invoice total was not detected.");
        }

        boolean itemNeedsReview = items.stream()
                .anyMatch(
                        InvoiceLineItemReview::needsReview);

        boolean invoiceNeedsReview = itemNeedsReview
                || !invoiceWarnings.isEmpty();

        int pageCount = calculatePageCount(results);

        return new InvoiceReviewResponse(
                supplier.displayName,
                invoiceNumber,
                invoiceDate,
                subtotal,
                gst,
                total,
                invoiceNeedsReview,
                pageCount,
                invoiceWarnings,
                items);
    }

    /*
     * ------------------------------------------------
     * SUPPLIER NORMALIZATION
     * ------------------------------------------------
     */

    private Supplier detectSupplier(
            List<ExtractedField> fields) {

        List<String> vendorNames = fields.stream()
                .filter(field -> "VENDOR_NAME".equals(field.type()))
                .map(ExtractedField::value)
                .filter(Objects::nonNull)
                .map(this::cleanText)
                .toList();

        for (String vendorName : vendorNames) {

            String value = vendorName.toLowerCase(Locale.ENGLISH);

            if (value.contains("bidfood")) {
                return Supplier.BIDFOOD;
            }

            if (value.contains("s.a.j")
                    || value.contains("saj")
                    || value.contains("fruit supply")) {

                return Supplier.SAJ;
            }

            if (value.contains("conapak")) {
                return Supplier.CONAPAK;
            }
        }

        /*
         * Conapak's stylised logo wasn't always OCR'd
         * correctly, but its ABN was consistently extracted.
         */
        boolean conapakAbn = fields.stream()
                .filter(field -> "VENDOR_ABN_NUMBER"
                        .equals(field.type()))
                .map(ExtractedField::value)
                .filter(Objects::nonNull)
                .map(this::digitsOnly)
                .anyMatch(
                        "54165574112"::equals);

        if (conapakAbn) {
            return Supplier.CONAPAK;
        }

        return Supplier.UNKNOWN;
    }

    /*
     * ------------------------------------------------
     * SUMMARY FIELDS
     * ------------------------------------------------
     */

    private String extractInvoiceNumber(
            List<ExtractedField> fields) {

        return fields.stream()
                .filter(field -> "INVOICE_RECEIPT_ID"
                        .equals(field.type()))
                .filter(field -> hasValue(field.value()))
                .max(
                        Comparator.comparingDouble(
                                this::invoiceNumberScore))
                .map(ExtractedField::value)
                .map(this::cleanText)
                .orElse(null);
    }

    private double invoiceNumberScore(
            ExtractedField field) {

        double score = field.confidence() != null
                ? field.confidence()
                : 0;

        String label = normalizeLabel(field.label());

        if (label.contains("invoice")) {
            score += 20;
        }

        if (label.contains("document")) {
            score += 5;
        }

        return score;
    }

    private String extractInvoiceDate(
            List<ExtractedField> fields) {

        return fields.stream()
                .filter(field -> "INVOICE_RECEIPT_DATE"
                        .equals(field.type()))
                .filter(field -> hasValue(field.value()))
                .max(
                        Comparator.comparingDouble(
                                field -> field.confidence() != null
                                        ? field.confidence()
                                        : 0))
                .map(ExtractedField::value)
                .map(this::normalizeDate)
                .orElse(null);
    }

    private BigDecimal extractBestTotal(
            List<ExtractedField> fields) {

        return fields.stream()
                .filter(field -> "TOTAL".equals(field.type()))
                .filter(field -> hasValue(field.value()))
                .max(
                        Comparator.comparingDouble(
                                this::totalScore))
                .map(ExtractedField::value)
                .map(this::parseMoney)
                .orElse(null);
    }

    private double totalScore(
            ExtractedField field) {

        double score = field.confidence() != null
                ? field.confidence()
                : 0;

        String label = normalizeLabel(field.label());

        if ("total".equals(label)
                || "totals".equals(label)) {

            score += 25;
        }

        if (label.contains("amount due")) {
            score += 10;
        }

        return score;
    }

    private BigDecimal extractMoneyByType(
            List<ExtractedField> fields,
            String type) {

        return fields.stream()
                .filter(field -> type.equals(field.type()))
                .filter(field -> hasValue(field.value()))
                .max(
                        Comparator.comparingDouble(
                                field -> field.confidence() != null
                                        ? field.confidence()
                                        : 0))
                .map(ExtractedField::value)
                .map(this::parseMoney)
                .orElse(null);
    }

    private BigDecimal firstMoneyByTypes(
            List<ExtractedField> fields,
            String... types) {

        for (String type : types) {

            BigDecimal value = extractMoneyByType(
                    fields,
                    type);

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    /*
     * ------------------------------------------------
     * LINE ITEMS
     * ------------------------------------------------
     */

    private List<InvoiceLineItemReview> normalizeLineItems(
            List<TextractResultResponse> results,
            Supplier supplier) {

        List<InvoiceLineItemReview> normalized = new ArrayList<>();

        int pageOffset = 0;

        for (TextractResultResponse result : results) {

            for (ExtractedLineItem lineItem : result.lineItems()) {

                InvoiceLineItemReview item = normalizeLineItem(
                        lineItem,
                        supplier,
                        pageOffset);

                /*
                 * Ignore rows that don't actually contain
                 * a product description.
                 *
                 * This removes many duplicated numeric-only
                 * rows Textract can occasionally create.
                 */
                if (item != null
                        && hasValue(item.description())) {

                    normalized.add(item);
                }
            }

            pageOffset += maxPageNumber(result);
        }

        return normalized;
    }

    private InvoiceLineItemReview normalizeLineItem(
            ExtractedLineItem lineItem,
            Supplier supplier,
            int pageOffset) {

        List<ExtractedField> fields = lineItem.fields();

        ExtractedField itemField = findByType(fields, "ITEM");

        if (itemField == null
                || !hasValue(itemField.value())) {

            return null;
        }

        ExtractedField productCodeField = findByType(
                fields,
                "PRODUCT_CODE");

        ExtractedField quantityField = findByType(
                fields,
                "QUANTITY");

        ExtractedField unitPriceField = findBestUnitPrice(fields);

        ExtractedField totalField = findBestLineTotal(fields);

        ExtractedField brandField = findByLabel(
                fields,
                "brand");

        ExtractedField packSizeField = findByLabelContaining(
                fields,
                "pack size");

        ExtractedField unitField = findUnitField(fields);

        ExtractedField lineExGstField = findByLabelContaining(
                fields,
                "excl gst");

        if (lineExGstField == null) {
            lineExGstField = findByLabelContaining(
                    fields,
                    "ex gst");
        }

        ExtractedField gstValueField = findByLabel(
                fields,
                "gst value");

        String productCode = cleanValue(productCodeField);

        String description = cleanValue(itemField);

        String brand = cleanValue(brandField);

        String packSize = cleanValue(packSizeField);

        String unit = cleanValue(unitField);

        BigDecimal quantity = parseMoneyField(quantityField);

        BigDecimal unitPrice = parseMoneyField(unitPriceField);

        BigDecimal lineExGst = parseMoneyField(lineExGstField);

        BigDecimal gstValue = parseMoneyField(gstValueField);

        BigDecimal lineTotal = parseMoneyField(totalField);

        /*
         * If Bidfood gives total + GST but Textract
         * misses ex-GST, derive it.
         */
        if (lineExGst == null
                && lineTotal != null
                && gstValue != null) {

            lineExGst = money(
                    lineTotal.subtract(gstValue));
        }

        List<String> warnings = new ArrayList<>();

        validateLineItem(
                supplier,
                description,
                quantity,
                unitPrice,
                lineExGst,
                gstValue,
                lineTotal,
                itemField,
                quantityField,
                unitPriceField,
                totalField,
                warnings);

        boolean needsReview = !warnings.isEmpty();

        Float confidence = minimumConfidence(
                itemField,
                quantityField,
                unitPriceField,
                totalField);

        int localPage = itemField.pageNumber() != null
                ? itemField.pageNumber()
                : 1;

        int overallPage = pageOffset + localPage;

        return new InvoiceLineItemReview(
                productCode,
                description,
                brand,
                packSize,
                unit,
                quantity,
                unitPrice,
                lineExGst,
                gstValue,
                lineTotal,
                confidence,
                needsReview,
                overallPage,
                warnings);
    }

    /*
     * ------------------------------------------------
     * ROW VALIDATION
     * ------------------------------------------------
     */

    private void validateLineItem(
            Supplier supplier,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineExGst,
            BigDecimal gstValue,
            BigDecimal lineTotal,
            ExtractedField itemField,
            ExtractedField quantityField,
            ExtractedField unitPriceField,
            ExtractedField totalField,
            List<String> warnings) {

        if (!hasValue(description)) {
            warnings.add(
                    "Product description is missing.");
        }

        if (quantity == null) {
            warnings.add(
                    "Quantity was not confidently extracted.");
        }

        if (unitPrice == null) {
            warnings.add(
                    "Unit price was not confidently extracted.");
        }

        if (lineTotal == null) {
            warnings.add(
                    "Line total was not confidently extracted.");
        }

        if (veryLowConfidence(itemField)
                || veryLowConfidence(quantityField)
                || veryLowConfidence(unitPriceField)
                || veryLowConfidence(totalField)) {

            warnings.add(
                    "One or more important fields have very low OCR confidence.");
        }

        if (quantity == null
                || unitPrice == null) {

            return;
        }

        BigDecimal expected = money(
                quantity.multiply(unitPrice));

        /*
         * Bidfood:
         *
         * qty × unitPrice = ex-GST line value
         *
         * ex-GST + GST = total line value
         */
        if (supplier == Supplier.BIDFOOD) {

            if (lineExGst != null
                    && !closeEnough(
                            expected,
                            lineExGst)) {

                warnings.add(
                        "Quantity × unit price does not match the ex-GST line value.");
            }

            if (lineExGst != null
                    && gstValue != null
                    && lineTotal != null) {

                BigDecimal calculatedTotal = money(
                        lineExGst.add(
                                gstValue));

                if (!closeEnough(
                        calculatedTotal,
                        lineTotal)) {

                    warnings.add(
                            "Ex-GST value + GST does not match the line total.");
                }
            }

            return;
        }

        /*
         * SAJ + Conapak:
         *
         * qty × unitPrice ≈ amount
         */
        if (lineTotal != null
                && !closeEnough(
                        expected,
                        lineTotal)) {

            warnings.add(
                    "Quantity × unit price does not match the line total.");
        }
    }

    /*
     * ------------------------------------------------
     * INVOICE VALIDATION
     * ------------------------------------------------
     */

    private void validateInvoiceTotals(
            Supplier supplier,
            List<InvoiceLineItemReview> items,
            BigDecimal subtotal,
            BigDecimal gst,
            BigDecimal total,
            List<String> warnings) {

        if (subtotal != null) {

            BigDecimal itemSubtotal = items.stream()
                    .map(item -> {

                        if (supplier == Supplier.BIDFOOD) {

                            return item.lineExGst();
                        }

                        return item.lineTotal();
                    })
                    .filter(Objects::nonNull)
                    .reduce(
                            BigDecimal.ZERO,
                            BigDecimal::add);

            itemSubtotal = money(itemSubtotal);

            if (!closeEnough(
                    itemSubtotal,
                    subtotal)) {

                warnings.add(
                        "Extracted line items total "
                                + itemSubtotal
                                + " but invoice subtotal is "
                                + subtotal
                                + ". One or more rows may be missing or incorrect.");
            }
        }

        if (subtotal != null
                && gst != null
                && total != null) {

            BigDecimal expectedTotal = money(
                    subtotal.add(gst));

            if (!closeEnough(
                    expectedTotal,
                    total)) {

                warnings.add(
                        "Subtotal + GST does not match invoice total.");
            }
        }
    }

    /*
     * ------------------------------------------------
     * FIELD HELPERS
     * ------------------------------------------------
     */

    private ExtractedField findByType(
            List<ExtractedField> fields,
            String type) {

        return fields.stream()
                .filter(field -> type.equals(field.type()))
                .filter(field -> hasValue(field.value()))
                .max(
                        Comparator.comparingDouble(
                                field -> field.confidence() != null
                                        ? field.confidence()
                                        : 0))
                .orElse(null);
    }

    private ExtractedField findBestUnitPrice(
            List<ExtractedField> fields) {

        Optional<ExtractedField> preferred = fields.stream()
                .filter(field -> "UNIT_PRICE"
                        .equals(field.type()))
                .filter(field -> normalizeLabel(
                        field.label())
                        .contains(
                                "unit price"))
                .max(
                        Comparator.comparingDouble(
                                field -> field.confidence() != null
                                        ? field.confidence()
                                        : 0));

        return preferred.orElseGet(
                () -> findByType(
                        fields,
                        "UNIT_PRICE"));
    }

    private ExtractedField findBestLineTotal(
            List<ExtractedField> fields) {

        return fields.stream()
                .filter(field -> "PRICE".equals(
                        field.type()))
                .filter(field -> hasValue(field.value()))
                .max(
                        Comparator.comparingDouble(
                                field -> lineTotalScore(field)))
                .orElse(null);
    }

    private double lineTotalScore(
            ExtractedField field) {

        double score = field.confidence() != null
                ? field.confidence()
                : 0;

        String label = normalizeLabel(field.label());

        if (label.contains("total value")) {
            score += 20;
        }

        if (label.equals("amount")) {
            score += 15;
        }

        return score;
    }

    private ExtractedField findUnitField(
            List<ExtractedField> fields) {

        return fields.stream()
                .filter(field -> "OTHER".equals(field.type()))
                .filter(field -> {

                    String label = normalizeLabel(
                            field.label());

                    return label.equals("unit")
                            || label.contains(
                                    "unit of measure");
                })
                .max(
                        Comparator.comparingDouble(
                                field -> field.confidence() != null
                                        ? field.confidence()
                                        : 0))
                .orElse(null);
    }

    private ExtractedField findByLabel(
            List<ExtractedField> fields,
            String targetLabel) {

        String normalizedTarget = normalizeLabel(targetLabel);

        return fields.stream()
                .filter(field -> normalizeLabel(
                        field.label())
                        .equals(
                                normalizedTarget))
                .max(
                        Comparator.comparingDouble(
                                field -> field.confidence() != null
                                        ? field.confidence()
                                        : 0))
                .orElse(null);
    }

    private ExtractedField findByLabelContaining(
            List<ExtractedField> fields,
            String text) {

        String normalizedText = normalizeLabel(text);

        return fields.stream()
                .filter(field -> normalizeLabel(
                        field.label())
                        .contains(
                                normalizedText))
                .max(
                        Comparator.comparingDouble(
                                field -> field.confidence() != null
                                        ? field.confidence()
                                        : 0))
                .orElse(null);
    }

    /*
     * ------------------------------------------------
     * MULTI-PAGE HELPERS
     * ------------------------------------------------
     */

    private int calculatePageCount(
            List<TextractResultResponse> results) {

        int count = 0;

        for (TextractResultResponse result : results) {
            count += maxPageNumber(result);
        }

        return Math.max(count, 1);
    }

    private int maxPageNumber(
            TextractResultResponse result) {

        int summaryMax = result.summaryFields().stream()
                .map(ExtractedField::pageNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(1);

        int itemMax = result.lineItems().stream()
                .flatMap(item -> item.fields().stream())
                .map(ExtractedField::pageNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(1);

        return Math.max(
                summaryMax,
                itemMax);
    }

    /*
     * ------------------------------------------------
     * GENERAL HELPERS
     * ------------------------------------------------
     */

    private void validateResultsSucceeded(
            List<TextractResultResponse> results) {

        for (TextractResultResponse result : results) {

            if (!"SUCCEEDED".equals(
                    result.status())) {

                throw new IllegalStateException(
                        "Textract analysis is not complete. Status: "
                                + result.status());
            }
        }
    }

    private BigDecimal parseMoneyField(
            ExtractedField field) {

        if (field == null
                || !hasValue(field.value())) {

            return null;
        }

        return parseMoney(
                field.value());
    }

    private BigDecimal parseMoney(
            String value) {

        if (!hasValue(value)) {
            return null;
        }

        String cleaned = value
                .replace("$", "")
                .replace(",", "")
                .replaceAll(
                        "[^0-9.\\-]",
                        "");

        if (cleaned.isBlank()
                || ".".equals(cleaned)
                || "-".equals(cleaned)) {

            return null;
        }

        try {

            return money(
                    new BigDecimal(cleaned));

        } catch (NumberFormatException ex) {

            return null;
        }
    }

    private BigDecimal money(
            BigDecimal value) {

        if (value == null) {
            return null;
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP);
    }

    private boolean closeEnough(
            BigDecimal first,
            BigDecimal second) {

        if (first == null
                || second == null) {

            return false;
        }

        return first
                .subtract(second)
                .abs()
                .compareTo(
                        MONEY_TOLERANCE) <= 0;
    }

    private String cleanValue(
            ExtractedField field) {

        if (field == null) {
            return null;
        }

        return cleanText(
                field.value());
    }

    private String cleanText(
            String value) {

        if (!hasValue(value)) {
            return null;
        }

        String cleaned = value
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll(
                        "\\s+",
                        " ")
                .replaceAll(
                        "-\\s+-",
                        "-")
                .trim();

        cleaned = cleaned.replaceAll(
                "\\s+-\\s*$",
                "");

        return cleaned.trim();
    }

    private String normalizeLabel(
            String label) {

        if (label == null) {
            return "";
        }

        return label
                .toLowerCase(Locale.ENGLISH)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace(".", "")
                .replaceAll(
                        "\\s+",
                        " ")
                .trim();
    }

    private String normalizeDate(
            String value) {

        String cleaned = cleanText(value);

        if (cleaned == null) {
            return null;
        }

        cleaned = cleaned.replace(
                " Sept ",
                " Sep ");

        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ofPattern(
                        "dd/MM/uuuu"),
                DateTimeFormatter.ofPattern(
                        "d/M/uuuu"),
                DateTimeFormatter.ofPattern(
                        "d MMM uuuu",
                        Locale.ENGLISH),
                DateTimeFormatter.ofPattern(
                        "dd MMM uuuu",
                        Locale.ENGLISH));

        for (DateTimeFormatter format : formats) {

            try {

                return LocalDate.parse(
                        cleaned,
                        format)
                        .toString();

            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }

        /*
         * If we cannot normalize it,
         * preserve Textract's value instead of
         * inventing a date.
         */
        return cleaned;
    }

    private Float minimumConfidence(
            ExtractedField... fields) {

        Float minimum = null;

        for (ExtractedField field : fields) {

            if (field == null
                    || field.confidence() == null) {

                continue;
            }

            if (minimum == null
                    || field.confidence() < minimum) {

                minimum = field.confidence();
            }
        }

        return minimum;
    }

    private boolean veryLowConfidence(
            ExtractedField field) {

        return field != null
                && field.confidence() != null
                && field.confidence() < VERY_LOW_CONFIDENCE;
    }

    private boolean hasValue(
            String value) {

        return value != null
                && !value.isBlank();
    }

    private String digitsOnly(
            String value) {

        if (value == null) {
            return "";
        }

        return value.replaceAll(
                "\\D",
                "");
    }

    private enum Supplier {

        BIDFOOD("Bidfood"),
        SAJ("SAJ Fruit Supply"),
        CONAPAK("Conapak"),
        UNKNOWN("Unknown supplier");

        private final String displayName;

        Supplier(
                String displayName) {

            this.displayName = displayName;
        }
    }
}