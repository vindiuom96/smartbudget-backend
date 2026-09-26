package com.smartbudget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.RecoveredLineItem;
import com.smartbudget.dto.RecoveredTableDto;
import com.smartbudget.dto.RecoveredTableRowDto;
import com.smartbudget.dto.TextractBlockDto;
import com.smartbudget.dto.TextractTableResultResponse;

@Service
public class TableRecoveryService {

    private static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.05");

    /*
     * Handles cases where Textract merges the UNIT and UNIT PRICE
     * into a single cell, for example:
     *
     * "Kg $5.20"
     * "Each (5-7kg) $24.20"
     *
     * The prefix must contain at least one letter so malformed numeric
     * values such as "1 1" are not interpreted as unit + price.
     */
    private static final Pattern MERGED_UNIT_PRICE_PATTERN = Pattern.compile("^(.+?)\\s+\\$?(-?\\d+(?:\\.\\d+)?)$");

    /*
     * -------------------------------------------------------
     * PART 1
     * Convert raw Textract blocks into rows and columns.
     * -------------------------------------------------------
     */
    public List<RecoveredTableDto> recoverTables(
            TextractTableResultResponse result) {

        Map<String, TextractBlockDto> blocksById = new HashMap<>();

        for (TextractBlockDto block : result.blocks()) {
            blocksById.put(block.id(), block);
        }

        List<RecoveredTableDto> recoveredTables = new ArrayList<>();

        for (TextractBlockDto block : result.blocks()) {

            if (!"TABLE".equals(block.blockType())) {
                continue;
            }

            RecoveredTableDto recoveredTable = recoverSingleTable(
                    block,
                    blocksById);

            recoveredTables.add(recoveredTable);
        }

        return recoveredTables;
    }

    private RecoveredTableDto recoverSingleTable(
            TextractBlockDto tableBlock,
            Map<String, TextractBlockDto> blocksById) {

        Map<Integer, Map<Integer, String>> rows = new TreeMap<>();

        List<String> cellIds = getRelationshipIds(
                tableBlock,
                "CHILD");

        for (String cellId : cellIds) {

            TextractBlockDto cell = blocksById.get(cellId);

            if (cell == null) {
                continue;
            }

            if (!"CELL".equals(cell.blockType())) {
                continue;
            }

            if (cell.rowIndex() == null
                    || cell.columnIndex() == null) {
                continue;
            }

            String text = extractCellText(
                    cell,
                    blocksById);

            rows.computeIfAbsent(
                    cell.rowIndex(),
                    ignored -> new TreeMap<>())
                    .put(
                            cell.columnIndex(),
                            text);
        }

        int maxColumn = rows.values()
                .stream()
                .flatMap(row -> row.keySet().stream())
                .max(Comparator.naturalOrder())
                .orElse(0);

        List<RecoveredTableRowDto> recoveredRows = new ArrayList<>();

        for (Map.Entry<Integer, Map<Integer, String>> rowEntry : rows.entrySet()) {

            List<String> cells = new ArrayList<>();

            for (int column = 1; column <= maxColumn; column++) {

                cells.add(
                        rowEntry
                                .getValue()
                                .getOrDefault(
                                        column,
                                        ""));
            }

            recoveredRows.add(
                    new RecoveredTableRowDto(
                            rowEntry.getKey(),
                            cells));
        }

        return new RecoveredTableDto(
                tableBlock.id(),
                tableBlock.page(),
                recoveredRows);
    }

    private String extractCellText(
            TextractBlockDto cell,
            Map<String, TextractBlockDto> blocksById) {

        List<String> childIds = getRelationshipIds(
                cell,
                "CHILD");

        List<String> words = new ArrayList<>();

        for (String childId : childIds) {

            TextractBlockDto child = blocksById.get(childId);

            if (child == null) {
                continue;
            }

            if ("WORD".equals(child.blockType())
                    && child.text() != null
                    && !child.text().isBlank()) {

                words.add(child.text().trim());
            }
        }

        return String.join(" ", words);
    }

    private List<String> getRelationshipIds(
            TextractBlockDto block,
            String relationshipType) {

        if (block.relationships() == null) {
            return List.of();
        }

        return block.relationships()
                .stream()
                .filter(relationship -> relationshipType.equals(
                        relationship.type()))
                .flatMap(relationship -> relationship.ids().stream())
                .toList();
    }

    /*
     * -------------------------------------------------------
     * PART 2
     * Convert recovered rows into invoice line items.
     *
     * Supports both:
     *
     * SAJ/simple:
     * QTY | CODE | DESCRIPTION | UNIT | UNIT PRICE | AMOUNT
     *
     * Bidfood/GST-aware:
     * CODE | PRODUCT DESCRIPTION | BRAND | PACK SIZE |
     * UNIT OF MEASURE | QUANTITY SUPPLIED | UNIT PRICE |
     * PRICE | EXCL. GST VALUE | GST VALUE | TOTAL VALUE
     * -------------------------------------------------------
     */
    public List<RecoveredLineItem> extractLineItems(
            TextractTableResultResponse result) {

        List<RecoveredTableDto> tables = recoverTables(result);

        List<RecoveredLineItem> items = new ArrayList<>();

        for (RecoveredTableDto table : tables) {

            HeaderMapping mapping = findHeaderMapping(table);

            /*
             * This table does not look like an invoice line-item table.
             * This naturally ignores Bidfood's small invoice-details tables.
             */
            if (mapping == null) {
                continue;
            }

            List<RecoveredLineItem> tableItems = new ArrayList<>();

            for (RecoveredTableRowDto row : table.rows()) {

                if (row.rowIndex() <= mapping.headerRowIndex()) {
                    continue;
                }

                if (isBlankRow(row)) {
                    continue;
                }

                if (isSummaryOrFooterRow(row)) {
                    continue;
                }

                if (isSectionHeadingRow(row, mapping)) {
                    continue;
                }

                RecoveredLineItem item = parseLineItem(
                        row,
                        table.page(),
                        mapping);

                tableItems.add(item);
            }

            /*
             * Financial recovery happens during parseLineItem().
             * This pass only cleans optional metadata.
             */
            items.addAll(
                    repairMetadata(tableItems));
        }

        return items;
    }

    private HeaderMapping findHeaderMapping(
            RecoveredTableDto table) {

        for (RecoveredTableRowDto row : table.rows()) {

            int quantityColumn = -1;
            int codeColumn = -1;
            int descriptionColumn = -1;
            int brandColumn = -1;
            int packSizeColumn = -1;
            int unitColumn = -1;
            int unitPriceColumn = -1;

            /*
             * Simple invoices such as SAJ.
             */
            int amountColumn = -1;

            /*
             * GST-aware invoices such as Bidfood.
             */
            int lineExGstColumn = -1;
            int gstValueColumn = -1;
            int totalValueColumn = -1;

            List<String> cells = row.cells();

            for (int i = 0; i < cells.size(); i++) {

                String header = normalizeHeader(
                        cells.get(i));

                if (header.isBlank()) {
                    continue;
                }

                /*
                 * Quantity
                 */
                if (header.equals("qty")
                        || header.equals("quantity")
                        || header.equals("quantity supplied")
                        || header.equals("qty supplied")) {

                    quantityColumn = i;
                }

                /*
                 * Product code
                 */
                if (header.equals("code")
                        || header.equals("product code")
                        || header.equals("item code")
                        || header.equals("sku")) {

                    codeColumn = i;
                }

                /*
                 * Description
                 */
                if (header.equals("description")
                        || header.equals("item")
                        || header.equals("product")
                        || header.equals("product description")
                        || header.equals("item description")) {

                    descriptionColumn = i;
                }

                /*
                 * Optional metadata
                 */
                if (header.equals("brand")) {
                    brandColumn = i;
                }

                if (header.equals("pack size")
                        || header.equals("packsize")) {

                    packSizeColumn = i;
                }

                /*
                 * Unit price must be checked before unit.
                 */
                if (header.contains("unit price")
                        || header.contains("unit cost")) {

                    unitPriceColumn = i;
                }

                /*
                 * Unit / UOM
                 */
                if (header.equals("unit")
                        || header.equals("uom")
                        || header.equals("unit of measure")) {

                    unitColumn = i;
                }

                /*
                 * GST-aware financial columns.
                 *
                 * Exact/strong matches are intentional so Bidfood's plain
                 * "Price" column is NOT mistaken for a line total.
                 */
                if (header.equals("excl gst value")
                        || header.equals("ex gst value")
                        || header.equals("excluding gst value")
                        || header.equals("excl gst")
                        || header.equals("ex gst")) {

                    lineExGstColumn = i;
                }

                if (header.equals("gst value")
                        || header.equals("gst amount")
                        || header.equals("tax value")
                        || header.equals("tax amount")) {

                    gstValueColumn = i;
                }

                if (header.equals("total value")
                        || header.equals("line total")
                        || header.equals("total amount")) {

                    totalValueColumn = i;
                }

                /*
                 * Simple line amount used by SAJ / Conapak-style tables.
                 *
                 * Deliberately do NOT treat a generic "Price" header as
                 * amount, because Bidfood has a separate Price column.
                 */
                if (header.equals("amount")) {
                    amountColumn = i;
                }
            }

            boolean commonColumnsPresent = quantityColumn >= 0
                    && descriptionColumn >= 0
                    && unitPriceColumn >= 0;

            boolean gstAwareColumnsPresent = lineExGstColumn >= 0
                    && gstValueColumn >= 0
                    && totalValueColumn >= 0;

            boolean simpleAmountPresent = amountColumn >= 0;

            if (commonColumnsPresent
                    && (gstAwareColumnsPresent || simpleAmountPresent)) {

                return new HeaderMapping(
                        row.rowIndex(),
                        quantityColumn,
                        codeColumn,
                        descriptionColumn,
                        brandColumn,
                        packSizeColumn,
                        unitColumn,
                        unitPriceColumn,
                        amountColumn,
                        lineExGstColumn,
                        gstValueColumn,
                        totalValueColumn);
            }
        }

        return null;
    }

    private RecoveredLineItem parseLineItem(
            RecoveredTableRowDto row,
            Integer pageNumber,
            HeaderMapping mapping) {

        String quantityRaw = getCell(
                row,
                mapping.quantityColumn());

        String productCode = cleanText(
                getCell(
                        row,
                        mapping.codeColumn()));

        String description = cleanText(
                getCell(
                        row,
                        mapping.descriptionColumn()));

        String brand = cleanText(
                getCell(
                        row,
                        mapping.brandColumn()));

        String packSize = cleanText(
                getCell(
                        row,
                        mapping.packSizeColumn()));

        String unit = cleanText(
                getCell(
                        row,
                        mapping.unitColumn()));

        String unitPriceRaw = getCell(
                row,
                mapping.unitPriceColumn());

        String simpleAmountRaw = getCell(
                row,
                mapping.amountColumn());

        String lineExGstRaw = getCell(
                row,
                mapping.lineExGstColumn());

        String gstValueRaw = getCell(
                row,
                mapping.gstValueColumn());

        String totalValueRaw = getCell(
                row,
                mapping.totalValueColumn());

        BigDecimal quantity = parseStrictDecimal(quantityRaw);
        BigDecimal unitPrice = parseStrictDecimal(unitPriceRaw);

        boolean gstAware = mapping.isGstAware();

        BigDecimal lineExGst;
        BigDecimal gstValue;
        BigDecimal lineTotal;

        if (gstAware) {

            lineExGst = parseStrictDecimal(lineExGstRaw);
            gstValue = parseStrictDecimal(gstValueRaw);
            lineTotal = parseStrictDecimal(totalValueRaw);

        } else {

            BigDecimal amount = parseStrictDecimal(simpleAmountRaw);

            lineExGst = amount;
            gstValue = amount == null
                    ? null
                    : BigDecimal.ZERO;
            lineTotal = amount;
        }

        List<String> warnings = new ArrayList<>();
        List<String> recoveryNotes = new ArrayList<>();

        /*
         * Recover merged unit / unit-price values such as "Kg $5.20".
         */
        if (unitPrice == null
                && unitPriceRaw != null
                && !unitPriceRaw.isBlank()) {

            ParsedUnitPrice parsedUnitPrice = parseMergedUnitAndPrice(
                    unitPriceRaw);

            if (parsedUnitPrice != null) {

                unitPrice = parsedUnitPrice.price();

                if (unit == null || unit.isBlank()) {
                    unit = parsedUnitPrice.unit();
                }

                recoveryNotes.add(
                        "Unit price recovered from merged unit/price cell.");
            }
        }

        /*
         * Quantity should reconcile against the ex-GST line value, not the
         * GST-inclusive total. This is essential for taxable Bidfood rows.
         */
        if (unitPrice != null
                && lineExGst != null
                && unitPrice.compareTo(BigDecimal.ZERO) > 0) {

            boolean quantityMissing = quantity == null;

            boolean quantityMathFails = false;

            if (quantity != null) {

                BigDecimal currentExpected = quantity.multiply(unitPrice);

                quantityMathFails = currentExpected
                        .subtract(lineExGst)
                        .abs()
                        .compareTo(MONEY_TOLERANCE) > 0;
            }

            if (quantityMissing || quantityMathFails) {

                BigDecimal inferredQuantity = lineExGst
                        .divide(
                                unitPrice,
                                4,
                                RoundingMode.HALF_UP)
                        .stripTrailingZeros();

                BigDecimal inferredExpected = inferredQuantity.multiply(
                        unitPrice);

                BigDecimal difference = inferredExpected
                        .subtract(lineExGst)
                        .abs();

                int decimalPlaces = Math.max(
                        inferredQuantity.scale(),
                        0);

                if (inferredQuantity.compareTo(BigDecimal.ZERO) > 0
                        && inferredQuantity.compareTo(
                                new BigDecimal("10000")) <= 0
                        && decimalPlaces <= 3
                        && difference.compareTo(
                                MONEY_TOLERANCE) <= 0) {

                    BigDecimal oldQuantity = quantity;

                    quantity = inferredQuantity;

                    if (oldQuantity == null) {

                        recoveryNotes.add(
                                "Quantity recovered from ex-GST line value / unit price.");

                    } else {

                        recoveryNotes.add(
                                "Quantity corrected from "
                                        + oldQuantity.toPlainString()
                                        + " to "
                                        + quantity.toPlainString()
                                        + " using ex-GST line value / unit price.");
                    }
                }
            }
        }

        /*
         * Required field validation.
         */
        if (quantity == null) {

            if (quantityRaw == null
                    || quantityRaw.isBlank()) {

                warnings.add(
                        "Quantity is missing.");

            } else {

                warnings.add(
                        "Quantity could not be parsed from: "
                                + quantityRaw);
            }
        }

        if (description == null
                || description.isBlank()) {

            warnings.add(
                    "Description is missing.");
        }

        if (unitPriceRaw == null
                || unitPriceRaw.isBlank()) {

            warnings.add(
                    "Unit price is missing.");

        } else if (unitPrice == null) {

            warnings.add(
                    "Unit price could not be parsed from: "
                            + unitPriceRaw);
        }

        if (gstAware) {

            if (lineExGstRaw == null
                    || lineExGstRaw.isBlank()) {

                warnings.add(
                        "Ex-GST line value is missing.");

            } else if (lineExGst == null) {

                warnings.add(
                        "Ex-GST line value could not be parsed from: "
                                + lineExGstRaw);
            }

            if (gstValueRaw == null
                    || gstValueRaw.isBlank()) {

                warnings.add(
                        "GST value is missing.");

            } else if (gstValue == null) {

                warnings.add(
                        "GST value could not be parsed from: "
                                + gstValueRaw);
            }

            if (totalValueRaw == null
                    || totalValueRaw.isBlank()) {

                warnings.add(
                        "Total line value is missing.");

            } else if (lineTotal == null) {

                warnings.add(
                        "Total line value could not be parsed from: "
                                + totalValueRaw);
            }

        } else {

            if (simpleAmountRaw == null
                    || simpleAmountRaw.isBlank()) {

                warnings.add(
                        "Line total is missing.");

            } else if (lineTotal == null) {

                warnings.add(
                        "Line total could not be parsed from: "
                                + simpleAmountRaw);
            }
        }

        /*
         * Validation 1:
         * quantity * unit price = ex-GST line value
         */
        if (quantity != null
                && unitPrice != null
                && lineExGst != null) {

            BigDecimal expectedExGst = quantity.multiply(
                    unitPrice);

            BigDecimal difference = expectedExGst
                    .subtract(lineExGst)
                    .abs();

            if (difference.compareTo(
                    MONEY_TOLERANCE) > 0) {

                warnings.add(
                        "Quantity * unit price does not match the ex-GST line value.");
            }
        }

        /*
         * Validation 2:
         * ex-GST line value + GST = total line value
         */
        if (lineExGst != null
                && gstValue != null
                && lineTotal != null) {

            BigDecimal expectedTotal = lineExGst.add(
                    gstValue);

            BigDecimal difference = expectedTotal
                    .subtract(lineTotal)
                    .abs();

            if (difference.compareTo(
                    MONEY_TOLERANCE) > 0) {

                warnings.add(
                        "Ex-GST line value + GST does not match the total line value.");
            }
        }

        return new RecoveredLineItem(
                row.rowIndex(),
                pageNumber,
                quantity,
                productCode,
                description,
                brand,
                packSize,
                unit,
                unitPrice,
                lineExGst,
                gstValue,
                lineTotal,
                warnings.isEmpty(),
                warnings,
                recoveryNotes);
    }

    /*
     * A section heading such as FROZEN / CHILLER / DRY has a description
     * but no actual product financial values. Skip it instead of marking it
     * as an invalid invoice item.
     */
    private boolean isSectionHeadingRow(
            RecoveredTableRowDto row,
            HeaderMapping mapping) {

        String code = cleanText(
                getCell(
                        row,
                        mapping.codeColumn()));

        String description = cleanText(
                getCell(
                        row,
                        mapping.descriptionColumn()));

        String quantity = cleanText(
                getCell(
                        row,
                        mapping.quantityColumn()));

        String unitPrice = cleanText(
                getCell(
                        row,
                        mapping.unitPriceColumn()));

        String simpleAmount = cleanText(
                getCell(
                        row,
                        mapping.amountColumn()));

        String lineExGst = cleanText(
                getCell(
                        row,
                        mapping.lineExGstColumn()));

        String totalValue = cleanText(
                getCell(
                        row,
                        mapping.totalValueColumn()));

        return (code == null || code.isBlank())
                && description != null
                && !description.isBlank()
                && (quantity == null || quantity.isBlank())
                && (unitPrice == null || unitPrice.isBlank())
                && (simpleAmount == null || simpleAmount.isBlank())
                && (lineExGst == null || lineExGst.isBlank())
                && (totalValue == null || totalValue.isBlank());
    }

    private ParsedUnitPrice parseMergedUnitAndPrice(
            String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        Matcher matcher = MERGED_UNIT_PRICE_PATTERN.matcher(
                raw.trim());

        if (!matcher.matches()) {
            return null;
        }

        String unitPart = cleanText(
                matcher.group(1));

        String pricePart = matcher.group(2);

        if (unitPart == null
                || !unitPart.matches(".*[A-Za-z].*")) {

            return null;
        }

        BigDecimal price = parseStrictDecimal(
                pricePart);

        if (price == null) {
            return null;
        }

        return new ParsedUnitPrice(
                unitPart,
                price);
    }

    /*
     * -------------------------------------------------------
     * PART 3
     * Conservative metadata cleanup.
     * -------------------------------------------------------
     */
    private List<RecoveredLineItem> repairMetadata(
            List<RecoveredLineItem> tableItems) {

        List<RecoveredLineItem> repaired = new ArrayList<>();

        for (int i = 0; i < tableItems.size(); i++) {

            RecoveredLineItem item = tableItems.get(i);

            RecoveredLineItem next = i + 1 < tableItems.size()
                    ? tableItems.get(i + 1)
                    : null;

            List<String> recoveryNotes = new ArrayList<>();

            if (item.recoveryNotes() != null) {
                recoveryNotes.addAll(
                        item.recoveryNotes());
            }

            String description = removeDuplicatedUnitSuffix(
                    item.description(),
                    item.unit());

            if (!sameText(
                    description,
                    item.description())) {

                recoveryNotes.add(
                        "Duplicated unit text removed from description.");
            }

            String productCode = repairProductCode(
                    item.productCode(),
                    description,
                    next != null
                            ? next.productCode()
                            : null,
                    recoveryNotes);

            repaired.add(
                    new RecoveredLineItem(
                            item.rowIndex(),
                            item.pageNumber(),
                            item.quantity(),
                            productCode,
                            description,
                            item.brand(),
                            item.packSize(),
                            item.unit(),
                            item.unitPrice(),
                            item.lineExGst(),
                            item.gstValue(),
                            item.lineTotal(),
                            item.valid(),
                            item.warnings(),
                            recoveryNotes));
        }

        return repaired;
    }

    private String repairProductCode(
            String currentCode,
            String description,
            String nextRowCode,
            List<String> recoveryNotes) {

        List<String> currentCandidates = splitProductCodeCandidates(
                currentCode);

        List<String> nextCandidates = splitProductCodeCandidates(
                nextRowCode);

        CodeCandidate currentBest = bestMatchingCode(
                currentCandidates,
                description);

        CodeCandidate nextBest = bestMatchingCode(
                nextCandidates,
                description);

        if (currentCandidates.size() > 1) {

            if (currentBest != null
                    && currentBest.score() >= 3) {

                recoveryNotes.add(
                        "Product code cleaned from merged code cell: "
                                + currentCode
                                + " -> "
                                + currentBest.code());

                return currentBest.code();
            }

            if (nextBest != null
                    && nextBest.score() >= 4) {

                recoveryNotes.add(
                        "Product code recovered from adjacent row after table shift: "
                                + nextBest.code());

                return nextBest.code();
            }

            recoveryNotes.add(
                    "Suspicious merged product code removed: "
                            + currentCode);

            return null;
        }

        if (currentCandidates.isEmpty()) {

            if (nextBest != null
                    && nextBest.score() >= 4) {

                recoveryNotes.add(
                        "Product code recovered from adjacent row after table shift: "
                                + nextBest.code());

                return nextBest.code();
            }

            return null;
        }

        String current = currentCandidates.get(0);

        int currentScore = currentBest != null
                ? currentBest.score()
                : 0;

        if (currentScore >= 3) {
            return current;
        }

        if (nextBest != null
                && nextBest.score() >= 4
                && nextBest.score() > currentScore) {

            recoveryNotes.add(
                    "Product code corrected after adjacent-row shift: "
                            + current
                            + " -> "
                            + nextBest.code());

            return nextBest.code();
        }

        /*
         * Numeric and opaque supplier codes are valid too. If the current
         * value is one clean token and there is no strong evidence of a
         * shift, preserve it.
         */
        return current;
    }

    private List<String> splitProductCodeCandidates(
            String rawCode) {

        if (rawCode == null
                || rawCode.isBlank()) {
            return List.of();
        }

        return List.of(
                rawCode.trim()
                        .split("\\s+"))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private CodeCandidate bestMatchingCode(
            List<String> candidates,
            String description) {

        CodeCandidate best = null;

        for (String candidate : candidates) {

            int score = scoreCodeAgainstDescription(
                    candidate,
                    description);

            if (best == null
                    || score > best.score()) {

                best = new CodeCandidate(
                        candidate,
                        score);
            }
        }

        return best;
    }

    private int scoreCodeAgainstDescription(
            String code,
            String description) {

        if (code == null
                || code.isBlank()
                || description == null
                || description.isBlank()) {

            return 0;
        }

        String normalizedCode = code
                .toUpperCase(Locale.ROOT)
                .replaceAll(
                        "[^A-Z0-9]",
                        "");

        String[] words = description
                .toUpperCase(Locale.ROOT)
                .replaceAll(
                        "[^A-Z0-9]+",
                        " ")
                .trim()
                .split("\\s+");

        int score = 0;

        for (String word : words) {

            if (word.length() < 3) {
                continue;
            }

            int longestMatch = 0;

            for (int length = Math.min(
                    5,
                    word.length()); length >= 3; length--) {

                String prefix = word.substring(
                        0,
                        length);

                if (normalizedCode.contains(prefix)) {
                    longestMatch = length;
                    break;
                }
            }

            score += longestMatch;
        }

        return score;
    }

    private String removeDuplicatedUnitSuffix(
            String description,
            String unit) {

        if (description == null
                || unit == null
                || unit.isBlank()) {

            return description;
        }

        String descriptionTrimmed = description.trim();

        String unitTrimmed = unit.trim();

        String descriptionLower = descriptionTrimmed
                .toLowerCase(
                        Locale.ROOT);

        String unitLower = unitTrimmed
                .toLowerCase(
                        Locale.ROOT);

        if (!descriptionLower.endsWith(
                unitLower)) {

            return description;
        }

        if (descriptionTrimmed.length() <= unitTrimmed.length()) {

            return description;
        }

        String cleaned = descriptionTrimmed
                .substring(
                        0,
                        descriptionTrimmed.length()
                                - unitTrimmed.length())
                .trim()
                .replaceAll(
                        "[\\s,;:-]+$",
                        "")
                .trim();

        return cleaned.isBlank()
                ? description
                : cleaned;
    }

    private boolean sameText(
            String first,
            String second) {

        if (first == null
                && second == null) {
            return true;
        }

        if (first == null
                || second == null) {
            return false;
        }

        return first.equals(second);
    }

    private record CodeCandidate(
            String code,
            int score) {
    }

    /*
     * Strict parsing is intentional.
     *
     * "$5.20" -> 5.20
     * "1" -> 1
     * "1.5" -> 1.5
     *
     * But:
     *
     * "1 1" -> invalid
     * "Kg $5.20" -> invalid until the explicit merged-cell recovery handles it
     */
    private BigDecimal parseStrictDecimal(
            String raw) {

        if (raw == null
                || raw.isBlank()) {
            return null;
        }

        String cleaned = raw.trim()
                .replace("$", "")
                .replace(",", "");

        if (!cleaned.matches(
                "-?\\d+(\\.\\d+)?")) {

            return null;
        }

        try {

            return new BigDecimal(
                    cleaned);

        } catch (NumberFormatException ex) {

            return null;
        }
    }

    private String getCell(
            RecoveredTableRowDto row,
            int columnIndex) {

        if (columnIndex < 0) {
            return "";
        }

        if (columnIndex >= row.cells().size()) {
            return "";
        }

        return row.cells()
                .get(columnIndex);
    }

    private boolean isBlankRow(
            RecoveredTableRowDto row) {

        return row.cells()
                .stream()
                .allMatch(cell -> cell == null
                        || cell.isBlank());
    }

    private boolean isSummaryOrFooterRow(
            RecoveredTableRowDto row) {

        String text = String.join(
                " ",
                row.cells())
                .toLowerCase(
                        Locale.ROOT);

        boolean containsTotalsCell = row.cells()
                .stream()
                .map(this::normalizeHeader)
                .anyMatch(cell -> cell.equals("totals")
                        || cell.equals("subtotal")
                        || cell.equals("grand total"));

        return containsTotalsCell
                || text.contains("subtotal")
                || text.contains("total (incl")
                || text.contains("total incl")
                || text.startsWith("sign:")
                || text.startsWith("signature ")
                || text.contains("signature payment")
                || text.contains("boxes:");
    }

    private String normalizeHeader(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(
                        Locale.ROOT)
                .replaceAll(
                        "[^a-z0-9]+",
                        " ")
                .trim()
                .replaceAll(
                        "\\s+",
                        " ");
    }

    private String cleanText(
            String text) {

        if (text == null) {
            return null;
        }

        String cleaned = text.trim()
                .replaceAll(
                        "\\s+",
                        " ");

        return cleaned.isBlank()
                ? null
                : cleaned;
    }

    private record ParsedUnitPrice(
            String unit,
            BigDecimal price) {
    }

    /*
     * All column indexes are zero-based because row.cells() is a Java List.
     */
    private record HeaderMapping(
            Integer headerRowIndex,
            int quantityColumn,
            int codeColumn,
            int descriptionColumn,
            int brandColumn,
            int packSizeColumn,
            int unitColumn,
            int unitPriceColumn,
            int amountColumn,
            int lineExGstColumn,
            int gstValueColumn,
            int totalValueColumn) {

        boolean isGstAware() {
            return lineExGstColumn >= 0
                    && gstValueColumn >= 0
                    && totalValueColumn >= 0;
        }
    }
}
