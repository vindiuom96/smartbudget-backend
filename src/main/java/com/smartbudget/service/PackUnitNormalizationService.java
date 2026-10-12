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
            "^(\\d+(?:\\.\\d+)?)X(\\d+(?:\\.\\d+)?)(KG|G|GR|GM|L|LT|LTR|ML|DOZ)$");

    /*
     * Examples:
     * 2KG
     * 500GR
     * 4L
     * 1.5KG
     * 15DOZ
     */
    private static final Pattern SIMPLE_PACK_PATTERN = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)(KG|G|GR|GM|L|LT|LTR|ML|DOZ)$");

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
         * That already means 5.46 KG.
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
         * 3 x 2 KG = 6 KG.
         */
        Measure packMeasure = parsePackSize(packSize);

        if (packMeasure != null) {

            BigDecimal normalizedQuantity = quantity.multiply(
                    packMeasure.quantity());

            return buildResult(
                    normalizedQuantity,
                    packMeasure.unit(),
                    lineExGst);
        }

        /*
         * EA is usable when there is no measurable pack size.
         *
         * Example:
         * quantity = 3
         * unit = EA
         * packSize = null
         *
         * -> 3 EA.
         */
        if (isEach(unit)) {

            return buildResult(
                    quantity,
                    "EA",
                    lineExGst);
        }

        /*
         * We cannot safely determine a normalized quantity.
         * Do not guess.
         */
        return unresolved();
    }

    private Measure normalizeDirectMeasurement(
            BigDecimal quantity,
            String unit) {

        if (unit == null || unit.isBlank()) {
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

            default -> null;
        };
    }

    private Measure parsePackSize(
            String packSize) {

        if (packSize == null
                || packSize.isBlank()) {

            return null;
        }

        String normalized = normalizeText(packSize)
                .replace("×", "X");

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
                    "LTR" ->
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
                .replaceAll("\\s+", "");
    }

    private record Measure(
            BigDecimal quantity,
            String unit) {
    }
}