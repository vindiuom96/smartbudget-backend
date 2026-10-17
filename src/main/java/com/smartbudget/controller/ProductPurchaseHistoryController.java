package com.smartbudget.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartbudget.dto.ProductPurchaseHistoryResponse;
import com.smartbudget.service.ProductPurchaseHistoryService;

@RestController
@RequestMapping("/api/products")
public class ProductPurchaseHistoryController {

    private final ProductPurchaseHistoryService productPurchaseHistoryService;

    public ProductPurchaseHistoryController(
            ProductPurchaseHistoryService productPurchaseHistoryService) {

        this.productPurchaseHistoryService = productPurchaseHistoryService;
    }

    @GetMapping("/history")
    public ProductPurchaseHistoryResponse getProductHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String supplierId,
            @RequestParam String productCode) {

        return productPurchaseHistoryService
                .getProductHistory(
                        jwt.getSubject(),
                        supplierId,
                        productCode);
    }
}