package com.kingwiredemo.transform;

import com.kingwiredemo.model.dto.RawProductRecord;
import com.kingwiredemo.model.entity.Inventory;
import com.kingwiredemo.model.entity.Pricing;
import com.kingwiredemo.model.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Normalizes raw records from disparate source systems into the canonical
 * Product/Inventory/Pricing entity model.
 *
 * Key normalizations performed:
 *   - SKU:         uppercase, whitespace stripped, spaces replaced with hyphens
 *   - Description: collapsed whitespace, trimmed
 *   - Material:    inferred from description/product_line when not explicit
 *   - Gauge:       strips "AWG" suffix, normalizes "MCM" → "kcmil"
 *   - UOM:         maps variations (FEET, FOOT, RL) to canonical values (FT, REEL)
 *   - Voltage:     strips trailing spaces, uppercase
 */
@Component
@Slf4j
public class ProductHarmonizer {

    private static final Map<String, String> UOM_MAP = Map.of(
            "FEET", "FT", "FOOT", "FT", "FT", "FT",
            "REEL", "REEL", "RL", "REEL",
            "CUT", "CUT", "CT", "CUT"
    );

    public Product toProduct(RawProductRecord raw) {
        return Product.builder()
                .sku(normalizeSku(raw.getSku()))
                .description(normalizeDescription(raw.getDescription()))
                .productLine(raw.getProductLine() != null ? raw.getProductLine().trim() : null)
                .material(resolveMaterial(raw))
                .gauge(normalizeGauge(raw.getGauge()))
                .voltageRating(raw.getVoltageRating() != null ? raw.getVoltageRating().trim().toUpperCase() : null)
                .uom(normalizeUom(raw.getUom()))
                .source(raw.getSourceSystem())
                .build();
    }

    public Inventory toInventory(RawProductRecord raw, Product product) {
        return Inventory.builder()
                .product(product)
                .warehouseCode(raw.getWarehouseCode())
                .qtyOnHand(raw.getQtyOnHand())
                .qtyAvailable(raw.getQtyAvailable())
                .lastSyncedAt(LocalDateTime.now())
                .build();
    }

    public Pricing toListPricing(RawProductRecord raw, Product product) {
        return Pricing.builder()
                .product(product)
                .priceType("LIST")
                .unitPrice(raw.getListPrice())
                .effectiveDt(raw.getEffectiveDate())
                .build();
    }

    public Pricing toDistributorPricing(RawProductRecord raw, Product product) {
        return Pricing.builder()
                .product(product)
                .priceType("DISTRIBUTOR")
                .unitPrice(raw.getDistributorPrice())
                .effectiveDt(raw.getEffectiveDate())
                .build();
    }

    // --- Normalization helpers ---

    public String normalizeSku(String sku) {
        if (sku == null) return null;
        return sku.trim().toUpperCase().replaceAll("\\s+", "-");
    }

    private String normalizeDescription(String desc) {
        if (desc == null) return null;
        return desc.trim().replaceAll("\\s{2,}", " ");
    }

    /**
     * Material inference hierarchy:
     *  1. Explicit material field from source
     *  2. Keyword scan of description + product line
     *  3. Default: "Unknown" (flagged for manual review)
     */
    String resolveMaterial(RawProductRecord raw) {
        if (raw.getMaterial() != null && !raw.getMaterial().isBlank()) {
            String m = raw.getMaterial().trim();
            if (m.equalsIgnoreCase("AL") || m.equalsIgnoreCase("ALUM")) return "Aluminum";
            if (m.equalsIgnoreCase("CU") || m.equalsIgnoreCase("COPP")) return "Copper";
            return capitalize(m);
        }

        String combined = ((raw.getDescription() != null ? raw.getDescription() : "") + " "
                + (raw.getProductLine() != null ? raw.getProductLine() : "")).toUpperCase();

        if (combined.contains("ALUMINUM") || combined.contains("ALUM") || combined.contains(" AL ") || combined.contains("AL-")) {
            return "Aluminum";
        }
        if (combined.contains("COPPER") || combined.contains(" CU ") || combined.contains("CU-")) {
            return "Copper";
        }

        log.debug("Could not infer material for SKU: {} — defaulting to Unknown", raw.getSku());
        return "Unknown";
    }

    String normalizeGauge(String gauge) {
        if (gauge == null) return null;
        return gauge.trim()
                .replaceAll("(?i)\\s*AWG$", "")      // "2/0 AWG" → "2/0"
                .replaceAll("(?i)MCM$", " kcmil")    // "350MCM" → "350 kcmil"
                .replaceAll("(?i)\\s*KCMIL$", " kcmil")
                .trim();
    }

    String normalizeUom(String uom) {
        if (uom == null || uom.isBlank()) return "FT";
        return UOM_MAP.getOrDefault(uom.trim().toUpperCase(), uom.trim().toUpperCase());
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
