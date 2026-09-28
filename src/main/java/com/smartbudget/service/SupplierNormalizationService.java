package com.smartbudget.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.smartbudget.dto.SupplierResolution;

@Service
public class SupplierNormalizationService {

    private record SupplierDefinition(
            String supplierId,
            String canonicalName) {
    }

    private final Map<String, SupplierDefinition> aliases = new HashMap<>();

    public SupplierNormalizationService() {

        /*
         * CONAPAK
         */
        register(
                "CONAPAK",
                "CONAPAK Produce",
                "CONAPAK Produce",
                "CONAPAK PRODUCE");

        /*
         * SAJ
         */
        register(
                "SAJ",
                "SAJ Fruit Supply (Vic) Pty Ltd",
                "SAJ Fruit Supply (Vic) Pty Ltd",
                "SAJ Fruit Supply VIC Pty Ltd",
                "SAJ Fruit Supply");

        /*
         * BIDFOOD
         */
        register(
                "BIDFOOD",
                "Bidfood",
                "Bidfood",
                "BIDFOOD",
                "Bidfood Australia");
    }

    public SupplierResolution resolve(
            String rawSupplierName) {

        if (rawSupplierName == null
                || rawSupplierName.isBlank()) {

            return new SupplierResolution(
                    null,
                    null,
                    rawSupplierName,
                    false);
        }

        String lookupKey = normalizeForLookup(rawSupplierName);

        SupplierDefinition supplier = aliases.get(lookupKey);

        if (supplier == null) {

            /*
             * IMPORTANT:
             *
             * Do not fuzzy-match or guess.
             *
             * An unknown supplier must be confirmed
             * by the chef later.
             */
            return new SupplierResolution(
                    null,
                    null,
                    rawSupplierName,
                    false);
        }

        return new SupplierResolution(
                supplier.supplierId(),
                supplier.canonicalName(),
                rawSupplierName,
                true);
    }

    private void register(
            String supplierId,
            String canonicalName,
            String... supplierAliases) {

        SupplierDefinition supplier = new SupplierDefinition(
                supplierId,
                canonicalName);

        for (String alias : supplierAliases) {

            aliases.put(
                    normalizeForLookup(alias),
                    supplier);
        }
    }

    private String normalizeForLookup(
            String value) {

        return value
                .trim()
                .toUpperCase(Locale.ROOT)

                // punctuation differences should not
                // create different suppliers
                .replaceAll("[^A-Z0-9]", "");
    }
}