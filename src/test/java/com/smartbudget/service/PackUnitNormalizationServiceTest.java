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

        @Test
        void shouldNormalizePackSizeWhenPackagingUnitIsRepeated() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("4"),
                                "PKT",
                                "2KG PKT",
                                new BigDecimal("93.60"));

                assertEquals(
                                0,
                                new BigDecimal("8")
                                                .compareTo(
                                                                result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("11.7000")
                                                .compareTo(
                                                                result.normalizedUnitPrice()));
        }

        @Test
        void shouldNormalizeBoxWithBracketedKgPackSize() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "BOX",
                                "1 Box (8kg)",
                                new BigDecimal("24.00"));

                assertEquals(
                                0,
                                new BigDecimal("8")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("3.0000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldNormalizeBoxWithHyphenatedKgPackSize() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "BOX",
                                "1 Box - 8kg",
                                new BigDecimal("24.00"));

                assertEquals(
                                0,
                                new BigDecimal("8")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("3.0000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldNormalizePunnetWithBracketedGramPackSize() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "PN",
                                "1 Pn (250g)",
                                new BigDecimal("2.50"));

                assertEquals(
                                0,
                                new BigDecimal("0.25")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("10.0000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldNormalizePunnetWithXGramPackSize() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "PN",
                                "1 Pn x 250g",
                                new BigDecimal("2.50"));

                assertEquals(
                                0,
                                new BigDecimal("0.25")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("10.0000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldKeepBunchAsItsOwnNormalizedUnit() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("3"),
                                "BUNCH",
                                null,
                                new BigDecimal("6.00"));

                assertEquals(
                                0,
                                new BigDecimal("3")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "BUNCH",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("2.0000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldNormalizePackDescriptionStoredInUnitField() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "Box (1.5kg)",
                                null,
                                new BigDecimal("16.80"));

                assertEquals(
                                0,
                                new BigDecimal("1.5")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("11.2000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldNormalizePunnetStoredInUnitField() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "Pn (250g)",
                                null,
                                new BigDecimal("6.15"));

                assertEquals(
                                0,
                                new BigDecimal("0.25")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("24.6000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldNormalizeBoxTenKgStoredInUnitField() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "Box (10kg)",
                                null,
                                new BigDecimal("42.00"));

                assertEquals(
                                0,
                                new BigDecimal("10")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "KG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("4.2000")
                                                .compareTo(result.normalizedUnitPrice()));
        }

        @Test
        void shouldKeepBagAsItsOwnNormalizedUnit() {

                PackUnitNormalizationResult result = service.normalize(
                                new BigDecimal("1"),
                                "Bag",
                                null,
                                new BigDecimal("33.60"));

                assertEquals(
                                0,
                                new BigDecimal("1")
                                                .compareTo(result.normalizedQuantity()));

                assertEquals(
                                "BAG",
                                result.normalizedUnit());

                assertEquals(
                                0,
                                new BigDecimal("33.6000")
                                                .compareTo(result.normalizedUnitPrice()));
        }
}