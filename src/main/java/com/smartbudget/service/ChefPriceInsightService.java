package com.smartbudget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartbudget.dto.ChefPriceInsightsResponse;
import com.smartbudget.dto.PriceChangeDirection;
import com.smartbudget.dto.PriceChangeInsightResponse;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.repository.InvoiceRepository;
import com.smartbudget.dto.PackUnitNormalizationResult;

@Service
public class ChefPriceInsightService {

    private final InvoiceRepository invoiceRepository;
    private final PackUnitNormalizationService packUnitNormalizationService;

    private final BigDecimal minimumPercentageChange;

    public ChefPriceInsightService(
            InvoiceRepository invoiceRepository,
            PackUnitNormalizationService packUnitNormalizationService,
            @Value("${smartbudget.insights.minimum-price-change-percent:5}") BigDecimal minimumPercentageChange) {

        this.invoiceRepository = invoiceRepository;

        this.packUnitNormalizationService = packUnitNormalizationService;

        this.minimumPercentageChange = minimumPercentageChange;
    }

    public ChefPriceInsightsResponse getPriceInsights(
            String userSub) {

        List<InvoiceEntity> invoices = invoiceRepository.findAllByUser(
                userSub);

        Map<ProductKey, List<PurchasePoint>> purchasesByProduct = new HashMap<>();

        for (InvoiceEntity invoice : invoices) {

            if (invoice.getSupplierId() == null ||
                    invoice.getItems() == null) {
                continue;
            }

            for (InvoiceLineItemEntity item : invoice.getItems()) {

                if (item.getProductCode() == null ||
                        item.getProductCode().isBlank()) {
                    continue;
                }

                PurchasePoint purchase = toPurchasePoint(
                        invoice,
                        item);

                if (purchase == null) {
                    continue;
                }

                ProductKey key = new ProductKey(
                        invoice.getSupplierId()
                                .trim()
                                .toUpperCase(),
                        item.getProductCode()
                                .trim()
                                .toUpperCase());

                purchasesByProduct
                        .computeIfAbsent(
                                key,
                                ignored -> new ArrayList<>())
                        .add(purchase);
            }
        }

        List<PriceChangeInsightResponse> insights = new ArrayList<>();

        for (Map.Entry<ProductKey, List<PurchasePoint>> entry : purchasesByProduct.entrySet()) {

            PriceChangeInsightResponse insight = createInsight(
                    entry.getKey(),
                    entry.getValue());

            if (insight != null) {
                insights.add(insight);
            }
        }

        insights.sort(
                Comparator
                        .comparing(
                                (PriceChangeInsightResponse insight) -> insight.percentageChange()
                                        .abs())
                        .reversed()
                        .thenComparing(
                                this::currentDateForSorting,
                                Comparator.reverseOrder()));

        return new ChefPriceInsightsResponse(
                insights.size(),
                insights);
    }

    private PurchasePoint toPurchasePoint(
            InvoiceEntity invoice,
            InvoiceLineItemEntity item) {

        BigDecimal normalizedQuantity = item.getNormalizedQuantity();

        String normalizedUnit = item.getNormalizedUnit();

        BigDecimal normalizedUnitPrice = item.getNormalizedUnitPrice();

        /*
         * Legacy invoices may not have stored
         * normalized values.
         */
        if (normalizedQuantity == null ||
                normalizedUnit == null ||
                normalizedUnitPrice == null) {

            PackUnitNormalizationResult normalization = packUnitNormalizationService.normalize(
                    item.getQuantity(),
                    item.getUnit(),
                    item.getPackSize(),
                    item.getLineExGst());

            normalizedQuantity = normalization.normalizedQuantity();

            normalizedUnit = normalization.normalizedUnit();

            normalizedUnitPrice = normalization.normalizedUnitPrice();
        }

        if (normalizedUnit == null ||
                normalizedUnitPrice == null) {

            return null;
        }

        if (invoice.getInvoiceDate() == null ||
                invoice.getInvoiceDate().isBlank()) {

            return null;
        }

        return new PurchasePoint(
                invoice.getSupplierName(),

                invoice.getInvoiceId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),

                item.getDescription(),
                item.getBrand(),

                normalizedUnit,
                normalizedUnitPrice);
    }

    private PriceChangeInsightResponse createInsight(
            ProductKey key,
            List<PurchasePoint> purchases) {

        if (purchases.size() < 2) {
            return null;
        }

        purchases.sort(
                Comparator.comparing(
                        this::purchaseDateForSorting)
                        .reversed());

        PurchasePoint current = purchases.get(0);

        PurchasePoint previous = purchases.get(1);

        /*
         * Never compare incompatible units.
         */
        if (!current.normalizedUnit()
                .equalsIgnoreCase(
                        previous.normalizedUnit())) {

            return null;
        }

        BigDecimal previousPrice = previous.normalizedUnitPrice();

        BigDecimal currentPrice = current.normalizedUnitPrice();

        if (previousPrice.compareTo(
                BigDecimal.ZERO) == 0) {

            return null;
        }

        BigDecimal absoluteChange = currentPrice
                .subtract(previousPrice)
                .setScale(
                        4,
                        RoundingMode.HALF_UP);

        BigDecimal percentageChange = absoluteChange
                .divide(
                        previousPrice,
                        6,
                        RoundingMode.HALF_UP)
                .multiply(
                        new BigDecimal("100"))
                .setScale(
                        1,
                        RoundingMode.HALF_UP);

        /*
         * Ignore tiny movements.
         *
         * Default threshold = 5%.
         */
        if (percentageChange
                .abs()
                .compareTo(
                        minimumPercentageChange) < 0) {

            return null;
        }

        PriceChangeDirection direction;

        int comparison = percentageChange.compareTo(
                BigDecimal.ZERO);

        if (comparison > 0) {
            direction = PriceChangeDirection.INCREASE;
        } else if (comparison < 0) {
            direction = PriceChangeDirection.DECREASE;
        } else {
            direction = PriceChangeDirection.UNCHANGED;
        }

        return new PriceChangeInsightResponse(
                key.supplierId(),
                current.supplier(),
                key.productCode(),

                current.description(),
                current.brand(),

                current.invoiceId(),
                current.invoiceNumber(),
                current.invoiceDate(),

                previous.invoiceId(),
                previous.invoiceNumber(),
                previous.invoiceDate(),

                currentPrice,
                previousPrice,
                current.normalizedUnit(),

                absoluteChange,
                percentageChange,
                direction);
    }

    private LocalDate purchaseDateForSorting(
            PurchasePoint purchase) {

        return LocalDate.parse(
                purchase.invoiceDate());
    }

    private LocalDate currentDateForSorting(
            PriceChangeInsightResponse insight) {

        return LocalDate.parse(
                insight.currentInvoiceDate());
    }

    private record ProductKey(
            String supplierId,
            String productCode) {
    }

    private record PurchasePoint(
            String supplier,

            String invoiceId,
            String invoiceNumber,
            String invoiceDate,

            String description,
            String brand,

            String normalizedUnit,
            BigDecimal normalizedUnitPrice) {
    }
}