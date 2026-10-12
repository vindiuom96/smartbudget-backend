package com.smartbudget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.smartbudget.dto.PackUnitNormalizationResult;

class PackUnitNormalizationServiceTest {

    private PackUnitNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new PackUnitNormalizationService();
    }

    @Test
    void shouldNormalizeMjChickenTwoKgPackets() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("3"),
                "PKT",
                "2KG",
                new BigDecimal("41.70"));

        assertEquals(
                0,
                new BigDecimal("6")
                        .compareTo(result.normalizedQuantity()));

        assertEquals(
                "KG",
                result.normalizedUnit());

        assertEquals(
                0,
                new BigDecimal("6.9500")
                        .compareTo(result.normalizedUnitPrice()));
    }

    @Test
    void shouldNormalizeBidfoodThirtyBy115GramCarton() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("1"),
                "CTN",
                "30x115gr",
                new BigDecimal("50.63"));

        assertEquals(
                0,
                new BigDecimal("3.45")
                        .compareTo(result.normalizedQuantity()));

        assertEquals(
                "KG",
                result.normalizedUnit());

        assertEquals(
                0,
                new BigDecimal("14.6754")
                        .compareTo(result.normalizedUnitPrice()));
    }

    @Test
    void shouldNormalizeBidfoodFifteenDozenCartons() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("2"),
                "CTN",
                "15doz",
                new BigDecimal("106.52"));

        assertEquals(
                0,
                new BigDecimal("360")
                        .compareTo(result.normalizedQuantity()));

        assertEquals(
                "EA",
                result.normalizedUnit());

        assertEquals(
                0,
                new BigDecimal("0.2959")
                        .compareTo(result.normalizedUnitPrice()));
    }

    @Test
    void shouldNormalizeProcalFourLitrePacks() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("5"),
                "EA",
                "4L",
                new BigDecimal("143.00"));

        assertEquals(
                0,
                new BigDecimal("20")
                        .compareTo(result.normalizedQuantity()));

        assertEquals(
                "L",
                result.normalizedUnit());

        assertEquals(
                0,
                new BigDecimal("7.1500")
                        .compareTo(result.normalizedUnitPrice()));
    }

    @Test
    void shouldKeepUniversityMeatKgQuantityAsAlreadyNormalized() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("5.46"),
                "KG",
                null,
                new BigDecimal("96.59"));

        assertEquals(
                0,
                new BigDecimal("5.46")
                        .compareTo(result.normalizedQuantity()));

        assertEquals(
                "KG",
                result.normalizedUnit());

        assertEquals(
                0,
                new BigDecimal("17.6905")
                        .compareTo(result.normalizedUnitPrice()));
    }

    @Test
    void shouldConvertFiveHundredGramPacksToKg() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("5"),
                "PAT",
                "500gr",
                new BigDecimal("41.90"));

        assertEquals(
                0,
                new BigDecimal("2.5")
                        .compareTo(result.normalizedQuantity()));

        assertEquals(
                "KG",
                result.normalizedUnit());

        assertEquals(
                0,
                new BigDecimal("16.7600")
                        .compareTo(result.normalizedUnitPrice()));
    }

    @Test
    void shouldNormalizeEachWhenNoPackSizeExists() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("3"),
                "EA",
                null,
                new BigDecimal("40.65"));

        assertEquals(
                0,
                new BigDecimal("3")
                        .compareTo(result.normalizedQuantity()));

        assertEquals(
                "EA",
                result.normalizedUnit());

        assertEquals(
                0,
                new BigDecimal("13.5500")
                        .compareTo(result.normalizedUnitPrice()));
    }

    @Test
    void shouldReturnUnresolvedForUnknownPack() {

        PackUnitNormalizationResult result = service.normalize(
                new BigDecimal("3"),
                "PKT",
                "ASSORTED",
                new BigDecimal("30.00"));

        assertNull(
                result.normalizedQuantity());

        assertNull(
                result.normalizedUnit());

        assertNull(
                result.normalizedUnitPrice());
    }

    @Test
    void shouldReturnUnresolvedWhenQuantityIsMissing() {

        PackUnitNormalizationResult result = service.normalize(
                null,
                "PKT",
                "2KG",
                new BigDecimal("41.70"));

        assertNull(
                result.normalizedQuantity());

        assertNull(
                result.normalizedUnit());

        assertNull(
                result.normalizedUnitPrice());
    }
}