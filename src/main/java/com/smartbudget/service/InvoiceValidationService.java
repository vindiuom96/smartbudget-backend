package com.smartbudget.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.InvoiceApprovalLineItem;
import com.smartbudget.dto.InvoiceApprovalRequest;
import com.smartbudget.dto.InvoiceItemValidationResponse;
import com.smartbudget.dto.SupplierResolution;

@Service
public class InvoiceValidationService {

    /*
     * Quantity x displayed unit price may differ slightly
     * from the supplier's printed line amount because the
     * supplier may use more decimal precision internally.
     */
    private static final BigDecimal LINE_CALCULATION_TOLERANCE = new BigDecimal("0.02");

    /*
     * Header totals and aggregate totals should agree
     * to the cent.
     *
     * 0.001 means a $0.01 difference will fail.
     */
    private static final BigDecimal MONEY_TOTAL_TOLERANCE = new BigDecimal("0.001");

    private final SupplierNormalizationService supplierNormalizationService;

    public InvoiceValidationService(
            SupplierNormalizationService supplierNormalizationService) {

        this.supplierNormalizationService = supplierNormalizationService;
    }

    public InvoiceValidationResult validate(
            InvoiceApprovalRequest request) {

        List<String> warnings = new ArrayList<>();

        List<InvoiceItemValidationResponse> itemValidations = new ArrayList<>();

        if (request == null) {

            warnings.add(
                    "Invoice data is required.");

            return new InvoiceValidationResult(
                    false,
                    warnings,
                    itemValidations);
        }

        validateHeader(
                request,
                warnings);

        validateItems(
                request,
                warnings,
                itemValidations);

        return new InvoiceValidationResult(
                warnings.isEmpty()
                        && itemValidations.isEmpty(),
                warnings,
                itemValidations);
    }

    private void validateHeader(
            InvoiceApprovalRequest request,
            List<String> warnings) {

        if (isBlank(request.supplier())) {

            warnings.add(
                    "Supplier is required.");

        } else {

            SupplierResolution resolution = supplierNormalizationService.resolve(
                    request.supplier());

            if (!resolution.recognized()) {

                warnings.add(
                        "Supplier is not recognised.");
            }
        }

        if (isBlank(request.invoiceNumber())) {

            warnings.add(
                    "Invoice number is required.");
        }

        if (isBlank(request.invoiceDate())) {

            warnings.add(
                    "Invoice date is required.");

        } else {

            try {

                LocalDate.parse(
                        request.invoiceDate());

            } catch (DateTimeParseException exception) {

                warnings.add(
                        "Invoice date must use YYYY-MM-DD format.");
            }
        }

        if (request.subtotalExGst() == null) {

            warnings.add(
                    "Invoice ex GST subtotal is required.");

        } else if (isNegative(
                request.subtotalExGst())) {

            warnings.add(
                    "Invoice ex GST subtotal cannot be negative.");
        }

        if (request.gst() == null) {

            warnings.add(
                    "Invoice GST is required.");

        } else if (isNegative(
                request.gst())) {

            warnings.add(
                    "Invoice GST cannot be negative.");
        }

        if (request.total() == null) {

            warnings.add(
                    "Invoice total is required.");

        } else if (isNegative(
                request.total())) {

            warnings.add(
                    "Invoice total cannot be negative.");
        }

        /*
         * Header financial relationship:
         *
         * subtotal + GST must match total
         * to the cent.
         */
        if (request.subtotalExGst() != null
                && request.gst() != null
                && request.total() != null) {

            BigDecimal calculatedTotal = request.subtotalExGst()
                    .add(
                            request.gst());

            if (!moneyMatches(
                    calculatedTotal,
                    request.total())) {

                warnings.add(
                        "Invoice subtotal plus GST does not match the invoice total.");
            }
        }
    }

    private void validateItems(
            InvoiceApprovalRequest request,
            List<String> warnings,
            List<InvoiceItemValidationResponse> itemValidations) {

        if (request.items() == null
                || request.items().isEmpty()) {

            warnings.add(
                    "Invoice must contain at least one line item.");

            return;
        }

        BigDecimal exGstSum = BigDecimal.ZERO;

        BigDecimal gstSum = BigDecimal.ZERO;

        BigDecimal totalSum = BigDecimal.ZERO;

        boolean canValidateExGstSum = true;
        boolean canValidateGstSum = true;
        boolean canValidateTotalSum = true;

        for (int index = 0; index < request.items().size(); index++) {

            InvoiceApprovalLineItem item = request.items().get(index);

            List<String> itemWarnings = validateItem(item);

            if (!itemWarnings.isEmpty()) {

                itemValidations.add(
                        new InvoiceItemValidationResponse(
                                index,
                                itemWarnings));
            }

            /*
             * Avoid dereferencing a missing item.
             */
            if (item == null) {

                canValidateExGstSum = false;
                canValidateGstSum = false;
                canValidateTotalSum = false;

                continue;
            }

            if (item.lineExGst() != null) {

                exGstSum = exGstSum.add(
                        item.lineExGst());

            } else {

                canValidateExGstSum = false;
            }

            if (item.gstValue() != null) {

                gstSum = gstSum.add(
                        item.gstValue());

            } else {

                canValidateGstSum = false;
            }

            if (item.lineTotal() != null) {

                totalSum = totalSum.add(
                        item.lineTotal());

            } else {

                canValidateTotalSum = false;
            }
        }

        /*
         * Aggregate amounts must match the invoice
         * totals to the cent.
         */
        if (canValidateExGstSum
                && request.subtotalExGst() != null
                && !moneyMatches(
                        exGstSum,
                        request.subtotalExGst())) {

            warnings.add(
                    "Sum of line item ex GST amounts does not match the invoice subtotal.");
        }

        if (canValidateGstSum
                && request.gst() != null
                && !moneyMatches(
                        gstSum,
                        request.gst())) {

            warnings.add(
                    "Sum of line item GST amounts does not match the invoice GST.");
        }

        if (canValidateTotalSum
                && request.total() != null
                && !moneyMatches(
                        totalSum,
                        request.total())) {

            warnings.add(
                    "Sum of line item totals does not match the invoice total.");
        }
    }

    private List<String> validateItem(
            InvoiceApprovalLineItem item) {

        List<String> warnings = new ArrayList<>();

        if (item == null) {

            warnings.add(
                    "Line item is missing.");

            return warnings;
        }

        if (isBlank(item.description())) {

            warnings.add(
                    "Description is required.");
        }

        if (item.quantity() == null) {

            warnings.add(
                    "Quantity is required.");

        } else if (isNegative(
                item.quantity())) {

            warnings.add(
                    "Quantity cannot be negative.");
        }

        if (item.unitPrice() == null) {

            warnings.add(
                    "Unit price is required.");

        } else if (isNegative(
                item.unitPrice())) {

            warnings.add(
                    "Unit price cannot be negative.");
        }

        if (item.lineExGst() == null) {

            warnings.add(
                    "Ex GST amount is required.");

        } else if (isNegative(
                item.lineExGst())) {

            warnings.add(
                    "Ex GST amount cannot be negative.");
        }

        if (item.gstValue() == null) {

            warnings.add(
                    "GST amount is required.");

        } else if (isNegative(
                item.gstValue())) {

            warnings.add(
                    "GST amount cannot be negative.");
        }

        if (item.lineTotal() == null) {

            warnings.add(
                    "Line total is required.");

        } else if (isNegative(
                item.lineTotal())) {

            warnings.add(
                    "Line total cannot be negative.");
        }

        /*
         * quantity x unitPrice ~= lineExGst
         *
         * This is the one calculation where we allow
         * a small rounding tolerance.
         */
        if (item.quantity() != null
                && item.unitPrice() != null
                && item.lineExGst() != null) {

            BigDecimal expectedExGst = item.quantity()
                    .multiply(
                            item.unitPrice());

            if (!lineCalculationMatches(
                    expectedExGst,
                    item.lineExGst())) {

                warnings.add(
                        "Quantity x unit price does not match ex GST amount.");
            }
        }

        /*
         * Ex GST + GST must match line total
         * to the cent.
         */
        if (item.lineExGst() != null
                && item.gstValue() != null
                && item.lineTotal() != null) {

            BigDecimal expectedTotal = item.lineExGst()
                    .add(
                            item.gstValue());

            if (!moneyMatches(
                    expectedTotal,
                    item.lineTotal())) {

                warnings.add(
                        "Ex GST plus GST does not match line total.");
            }
        }

        return warnings;
    }

    private boolean lineCalculationMatches(
            BigDecimal first,
            BigDecimal second) {

        return difference(
                first,
                second)
                .compareTo(
                        LINE_CALCULATION_TOLERANCE) <= 0;
    }

    private boolean moneyMatches(
            BigDecimal first,
            BigDecimal second) {

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

    private boolean isNegative(
            BigDecimal value) {

        return value.compareTo(
                BigDecimal.ZERO) < 0;
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.isBlank();
    }
}