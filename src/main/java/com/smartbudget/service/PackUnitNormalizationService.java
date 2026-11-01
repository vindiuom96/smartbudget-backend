package com.smartbudget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.PackUnitNormalizationResult;

@Service
public class PackUnitNormalizationService {

        private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");

        private static final BigDecimal TWELVE = new BigDecimal("12");

        /*
         * Examples:
         * 30x115gr
         * 36x170gr
         * 6x2L
         */
        private static final Pattern MULTI_PACK_PATTERN = Pattern.compile(
                        "^(\\d+(?:\\.\\d+)?)X" +
                                        "(\\d+(?:\\.\\d+)?)" +
                                        "(KG|G|GR|GM|L|LT|LTR|LITRE|LITRES|LITER|LITERS|ML|DOZ|BUNCH|BUNCHES)$");

        /*
         * Examples:
         * 2KG
         * 500GR
         * 4L
         * 1.5KG
         * 15DOZ
         * 3BUNCH
         */
        private static final Pattern SIMPLE_PACK_PATTERN = Pattern.compile(
                        "^(\\d+(?:\\.\\d+)?)" +
                                        "(KG|G|GR|GM|L|LT|LTR|LITRE|LITRES|LITER|LITERS|ML|DOZ|BUNCH|BUNCHES)$");

        /*
         * Handles descriptive pack sizes where the physical
         * measurement appears at the end.
         *
         * Examples:
         *
         * 1 Box (8kg)
         * 1 Box - 8kg
         * 1 Pn (250g)
         * 1 Pn x 250g
         *
         * After normalization these become:
         *
         * 1BOX8KG
         * 1BOX8KG
         * 1PN250G
         * 1PNX250G
         *
         * We then extract:
         *
         * 8KG
         * 250G
         */
        private static final Pattern TRAILING_MEASURE_PATTERN = Pattern.compile(
                        ".*?(\\d+(?:\\.\\d+)?)" +
                                        "(KG|G|GR|GM|L|LT|LTR|LITRE|LITRES|LITER|LITERS|ML|BUNCH|BUNCHES)$");

        public PackUnitNormalizationResult normalize(
                        BigDecimal quantity,
                        String unit,
                        String packSize,
                        BigDecimal lineExGst) {

                if (quantity == null
                                || quantity.compareTo(BigDecimal.ZERO) <= 0) {

                        return unresolved();
                }

                /*
                 * If quantity itself is already a physical measurement,
                 * trust it first.
                 *
                 * Example:
                 * University Meat
                 * quantity = 5.46
                 * unit = KG
                 *
                 * -> 5.46 KG
                 */
                Measure directMeasure = normalizeDirectMeasurement(
                                quantity,
                                unit);

                if (directMeasure != null) {

                        return buildResult(
                                        directMeasure.quantity(),
                                        directMeasure.unit(),
                                        lineExGst);
                }

                /*
                 * Otherwise try to understand the pack size.
                 *
                 * Example:
                 * quantity = 3
                 * unit = PKT
                 * packSize = 2KG
                 *
                 * -> 3 x 2 KG
                 * -> 6 KG
                 */
                Measure packMeasure = parsePackSize(
                                packSize,
                                unit);

                if (packMeasure == null) {

                        packMeasure = parsePackSize(
                                        unit,
                                        null);
                }

                if (packMeasure != null) {

                        BigDecimal normalizedQuantity = quantity.multiply(
                                        packMeasure.quantity());

                        return buildResult(
                                        normalizedQuantity,
                                        packMeasure.unit(),
                                        lineExGst);
                }

                /*
                 * EA is usable when there is no measurable
                 * pack size.
                 *
                 * Example:
                 * quantity = 3
                 * unit = EA
                 *
                 * -> 3 EA
                 */
                if (isEach(unit)) {

                        return buildResult(
                                        quantity,
                                        "EA",
                                        lineExGst);
                }

                /*
                 * We cannot safely determine a normalized
                 * quantity.
                 *
                 * Do not guess.
                 */
                return unresolved();
        }

        private Measure normalizeDirectMeasurement(
                        BigDecimal quantity,
                        String unit) {

                if (unit == null ||
                                unit.isBlank()) {

                        return null;
                }

                String normalizedUnit = normalizeText(unit);

                return switch (normalizedUnit) {

                        case "KG",
                                        "KGS",
                                        "KILOGRAM",
                                        "KILOGRAMS" ->
                                new Measure(
                                                quantity,
                                                "KG");

                        case "G",
                                        "GR",
                                        "GM",
                                        "GRAM",
                                        "GRAMS" ->
                                new Measure(
                                                quantity.divide(
                                                                ONE_THOUSAND),
                                                "KG");

                        case "L",
                                        "LT",
                                        "LTR",
                                        "LITRE",
                                        "LITRES",
                                        "LITER",
                                        "LITERS" ->
                                new Measure(
                                                quantity,
                                                "L");

                        case "ML" ->
                                new Measure(
                                                quantity.divide(
                                                                ONE_THOUSAND),
                                                "L");

                        case "DOZ",
                                        "DOZEN" ->
                                new Measure(
                                                quantity.multiply(
                                                                TWELVE),
                                                "EA");

                        /*
                         * A bunch cannot safely be converted to
                         * KG or EA because its physical contents
                         * are unknown.
                         *
                         * Keep it as its own comparison unit.
                         */
                        case "BUNCH",
                                        "BUNCHES" ->
                                new Measure(
                                                quantity,
                                                "BUNCH");

                        case "BAG",
                                        "BAGS" ->
                                new Measure(
                                                quantity,
                                                "BAG");

                        default -> null;
                };
        }

        private Measure parsePackSize(
                        String packSize,
                        String invoiceUnit) {

                if (packSize == null
                                || packSize.isBlank()) {

                        return null;
                }

                /*
                 * normalizeText:
                 *
                 * "1 Box (8kg)"
                 * -> "1BOX(8KG)"
                 *
                 * Then remove formatting characters:
                 *
                 * -> "1BOX8KG"
                 *
                 * This allows different supplier formatting
                 * styles to be treated identically.
                 */
                String normalized = normalizeText(packSize)
                                .replace("×", "X")
                                .replaceAll(
                                                "[^A-Z0-9.X]",
                                                "");

                /*
                 * Some suppliers repeat the packaging unit
                 * inside the pack-size field.
                 *
                 * Example:
                 *
                 * packSize = "2KG PKT"
                 * unit = "PKT"
                 *
                 * -> 2KGPKT
                 *
                 * Strip the trailing PKT so the physical
                 * measurement can be parsed as 2KG.
                 */
                if (invoiceUnit != null
                                && !invoiceUnit.isBlank()) {

                        String normalizedInvoiceUnit = normalizeText(
                                        invoiceUnit);

                        if (!normalizedInvoiceUnit.isBlank()
                                        && normalized.endsWith(
                                                        normalizedInvoiceUnit)
                                        && normalized.length() > normalizedInvoiceUnit.length()) {

                                normalized = normalized.substring(
                                                0,
                                                normalized.length()
                                                                - normalizedInvoiceUnit.length());
                        }
                }

                /*
                 * First try a normal multi-pack.
                 *
                 * Examples:
                 *
                 * 30X115GR
                 * 6X2L
                 */
                Matcher multiMatcher = MULTI_PACK_PATTERN.matcher(
                                normalized);

                if (multiMatcher.matches()) {

                        BigDecimal count = new BigDecimal(
                                        multiMatcher.group(1));

                        BigDecimal size = new BigDecimal(
                                        multiMatcher.group(2));

                        String unit = multiMatcher.group(3);

                        Measure oneItem = convertToBaseUnit(
                                        size,
                                        unit);

                        if (oneItem == null) {
                                return null;
                        }

                        return new Measure(
                                        count.multiply(
                                                        oneItem.quantity()),
                                        oneItem.unit());
                }

                /*
                 * Then try a simple physical pack.
                 *
                 * Examples:
                 *
                 * 2KG
                 * 500G
                 * 4L
                 */
                Matcher simpleMatcher = SIMPLE_PACK_PATTERN.matcher(
                                normalized);

                if (simpleMatcher.matches()) {

                        BigDecimal size = new BigDecimal(
                                        simpleMatcher.group(1));

                        String unit = simpleMatcher.group(2);

                        return convertToBaseUnit(
                                        size,
                                        unit);
                }

                /*
                 * Finally support descriptive supplier formats.
                 *
                 * Examples:
                 *
                 * 1BOX8KG
                 * 1PN250G
                 * 1PNX250G
                 *
                 * We intentionally use the final physical
                 * measurement rather than trying to understand
                 * supplier-specific words such as BOX or PN.
                 */
                Matcher trailingMatcher = TRAILING_MEASURE_PATTERN.matcher(
                                normalized);

                if (trailingMatcher.matches()) {

                        BigDecimal size = new BigDecimal(
                                        trailingMatcher.group(1));

                        String unit = trailingMatcher.group(2);

                        return convertToBaseUnit(
                                        size,
                                        unit);
                }

                return null;
        }

        private Measure convertToBaseUnit(
                        BigDecimal value,
                        String unit) {

                return switch (unit) {

                        case "KG" ->
                                new Measure(
                                                value,
                                                "KG");

                        case "G",
                                        "GR",
                                        "GM" ->
                                new Measure(
                                                value.divide(
                                                                ONE_THOUSAND),
                                                "KG");

                        case "L",
                                        "LT",
                                        "LTR",
                                        "LITRE",
                                        "LITRES",
                                        "LITER",
                                        "LITERS" ->
                                new Measure(
                                                value,
                                                "L");

                        case "ML" ->
                                new Measure(
                                                value.divide(
                                                                ONE_THOUSAND),
                                                "L");

                        case "DOZ" ->
                                new Measure(
                                                value.multiply(
                                                                TWELVE),
                                                "EA");

                        case "BUNCH",
                                        "BUNCHES" ->
                                new Measure(
                                                value,
                                                "BUNCH");

                        default -> null;
                };
        }

        private PackUnitNormalizationResult buildResult(
                        BigDecimal normalizedQuantity,
                        String normalizedUnit,
                        BigDecimal lineExGst) {

                BigDecimal normalizedUnitPrice = null;

                if (lineExGst != null
                                && normalizedQuantity != null
                                && normalizedQuantity.compareTo(
                                                BigDecimal.ZERO) > 0) {

                        normalizedUnitPrice = lineExGst.divide(
                                        normalizedQuantity,
                                        4,
                                        RoundingMode.HALF_UP);
                }

                return new PackUnitNormalizationResult(
                                normalizedQuantity,
                                normalizedUnit,
                                normalizedUnitPrice);
        }

        private PackUnitNormalizationResult unresolved() {

                return new PackUnitNormalizationResult(
                                null,
                                null,
                                null);
        }

        private boolean isEach(
                        String unit) {

                if (unit == null) {
                        return false;
                }

                String normalized = normalizeText(unit);

                return normalized.equals("EA")
                                || normalized.equals("EACH");
        }

        private String normalizeText(
                        String value) {

                return value
                                .trim()
                                .toUpperCase(Locale.ROOT)
                                .replaceAll(
                                                "\\s+",
                                                "");
        }

        private record Measure(
                        BigDecimal quantity,
                        String unit) {
        }
}