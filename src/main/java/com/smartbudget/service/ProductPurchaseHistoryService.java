package com.smartbudget.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.smartbudget.dto.PackUnitNormalizationResult;
import com.smartbudget.dto.ProductPurchaseHistoryItemResponse;
import com.smartbudget.dto.ProductPurchaseHistoryResponse;
import com.smartbudget.dto.PriceChangeDirection;
import com.smartbudget.dto.ProductPriceSummaryResponse;
import com.smartbudget.model.InvoiceEntity;
import com.smartbudget.model.InvoiceLineItemEntity;
import com.smartbudget.repository.InvoiceRepository;

@Service
public class ProductPurchaseHistoryService {

    private final InvoiceRepository invoiceRepository;
    private final PackUnitNormalizationService packUnitNormalizationService;

    public ProductPurchaseHistoryService(
            InvoiceRepository invoiceRepository,
            PackUnitNormalizationService packUnitNormalizationService) {

        this.invoiceRepository = invoiceRepository;
        this.packUnitNormalizationService = packUnitNormalizationService;
    }

    public ProductPurchaseHistoryResponse getProductHistory(
            String userSub,
            String supplierId,
            String productCode) {

        validateRequest(
                supplierId,
                productCode);

        String normalizedSupplierId = supplierId.trim()
                .toUpperCase();

        String normalizedProductCode = productCode.trim()
                .toUpperCase();

        List<ProductPurchaseHistoryItemResponse> purchases = new ArrayList<>();

        String latestDescription = null;
        String latestBrand = null;
        String supplierName = null;

        List<InvoiceEntity> invoices = invoiceRepository.findAllByUser(
                userSub);

        for (InvoiceEntity invoice : invoices) {

            if (!matchesSupplier(
                    invoice,
                    normalizedSupplierId)) {
                continue;
            }

            if (invoice.getItems() == null) {
                continue;
            }

            for (InvoiceLineItemEntity item : invoice.getItems()) {

                if (!matchesProductCode(
                        item,
                        normalizedProductCode)) {
                    continue;
                }

                purchases.add(
                        toPurchaseResponse(
                                invoice,
                                item));

                supplierName = invoice.getSupplierName();

                /*
                 * We update these while iterating.
                 * After sorting below we will replace them
                 * with the newest purchase's values.
                 */
                latestDescription = item.getDescription();

                latestBrand = item.getBrand();
            }
        }

        if (purchases.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No purchase history found for this product.");
        }

        purchases.sort(
                Comparator.comparing(
                        this::purchaseDateForSorting)
                        .reversed());

        ProductPriceSummaryResponse priceSummary = calculatePriceSummary(purchases);

        /*
         * Use the newest purchase as the display metadata.
         * Supplier descriptions/brands can change over time.
         */
        ProductPurchaseHistoryItemResponse latest = purchases.getFirst();

        latestDescription = latest.description();

        latestBrand = latest.brand();

        return new ProductPurchaseHistoryResponse(
                normalizedSupplierId,
                supplierName,
                normalizedProductCode,
                latestDescription,
                latestBrand,
                purchases.size(),
                priceSummary,
                purchases);
    }

    private void validateRequest(
            String supplierId,
            String productCode) {

        if (supplierId == null ||
                supplierId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supplier ID is required.");
        }

        if (productCode == null ||
                productCode.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product code is required.");
        }
    }

    private boolean matchesSupplier(
            InvoiceEntity invoice,
            String supplierId) {

        if (invoice.getSupplierId() == null) {
            return false;
        }

        return supplierId.equals(
                invoice.getSupplierId()
                        .trim()
                        .toUpperCase());
    }

    private boolean matchesProductCode(
            InvoiceLineItemEntity item,
            String productCode) {

        if (item.getProductCode() == null) {
            return false;
        }

        return productCode.equals(
                item.getProductCode()
                        .trim()
                        .toUpperCase());
    }

    private ProductPurchaseHistoryItemResponse toPurchaseResponse(
            InvoiceEntity invoice,
            InvoiceLineItemEntity item) {

        var normalizedQuantity = item.getNormalizedQuantity();

        var normalizedUnit = item.getNormalizedUnit();

        var normalizedUnitPrice = item.getNormalizedUnitPrice();

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

        return new ProductPurchaseHistoryItemResponse(
                invoice.getInvoiceId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),

                item.getDescription(),
                item.getBrand(),

                item.getPackSize(),
                item.getUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineExGst(),

                normalizedQuantity,
                normalizedUnit,
                normalizedUnitPrice);
    }

    private LocalDate purchaseDateForSorting(
            ProductPurchaseHistoryItemResponse purchase) {

        if (purchase.invoiceDate() == null ||
                purchase.invoiceDate().isBlank()) {

            return LocalDate.MIN;
        }

        return LocalDate.parse(
                purchase.invoiceDate());
    }

    private ProductPriceSummaryResponse calculatePriceSummary(
            List<ProductPurchaseHistoryItemResponse> purchases) {

        if (purchases.isEmpty()) {
            return null;
        }

        ProductPurchaseHistoryItemResponse current = purchases.get(0);

        if (current.normalizedUnitPrice() == null ||
                current.normalizedUnit() == null) {

            return null;
        }

        String unit = current.normalizedUnit();

        List<ProductPurchaseHistoryItemResponse> comparable = purchases.stream()
                .filter(purchase -> purchase.normalizedUnitPrice() != null)
                .filter(purchase -> purchase.normalizedUnit() != null)
                .filter(purchase -> unit.equalsIgnoreCase(
                        purchase.normalizedUnit()))
                .toList();

        if (comparable.isEmpty()) {
            return null;
        }

        BigDecimal currentPrice = current.normalizedUnitPrice();

        BigDecimal previousPrice = comparable.size() >= 2
                ? comparable.get(1)
                        .normalizedUnitPrice()
                : null;

        BigDecimal absoluteChange = null;
        BigDecimal percentageChange = null;
        PriceChangeDirection direction = null;

        if (previousPrice != null) {

            absoluteChange = currentPrice
                    .subtract(previousPrice)
                    .setScale(
                            4,
                            RoundingMode.HALF_UP);

            int comparison = currentPrice.compareTo(
                    previousPrice);

            if (comparison > 0) {
                direction = PriceChangeDirection.INCREASE;
            } else if (comparison < 0) {
                direction = PriceChangeDirection.DECREASE;
            } else {
                direction = PriceChangeDirection.UNCHANGED;
            }

            if (previousPrice.compareTo(
                    BigDecimal.ZERO) != 0) {

                percentageChange = absoluteChange
                        .divide(
                                previousPrice,
                                6,
                                RoundingMode.HALF_UP)
                        .multiply(
                                new BigDecimal("100"))
                        .setScale(
                                1,
                                RoundingMode.HALF_UP);
            }
        }

        BigDecimal lowestPrice = comparable.stream()
                .map(
                        ProductPurchaseHistoryItemResponse::normalizedUnitPrice)
                .min(BigDecimal::compareTo)
                .orElse(currentPrice);

        BigDecimal highestPrice = comparable.stream()
                .map(
                        ProductPurchaseHistoryItemResponse::normalizedUnitPrice)
                .max(BigDecimal::compareTo)
                .orElse(currentPrice);

        BigDecimal totalPrice = comparable.stream()
                .map(
                        ProductPurchaseHistoryItemResponse::normalizedUnitPrice)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);

        BigDecimal averagePrice = totalPrice.divide(
                BigDecimal.valueOf(
                        comparable.size()),
                4,
                RoundingMode.HALF_UP);

        BigDecimal currentVsAveragePercentage = null;

        if (averagePrice.compareTo(
                BigDecimal.ZERO) != 0) {

            currentVsAveragePercentage = currentPrice
                    .subtract(averagePrice)
                    .divide(
                            averagePrice,
                            6,
                            RoundingMode.HALF_UP)
                    .multiply(
                            new BigDecimal("100"))
                    .setScale(
                            1,
                            RoundingMode.HALF_UP);
        }

        return new ProductPriceSummaryResponse(
                currentPrice,
                previousPrice,
                unit,
                absoluteChange,
                percentageChange,
                direction,
                lowestPrice,
                highestPrice,
                averagePrice,
                currentVsAveragePercentage,
                comparable.size());
    }
}