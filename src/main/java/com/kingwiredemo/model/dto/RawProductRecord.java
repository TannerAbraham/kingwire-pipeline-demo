package com.kingwiredemo.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Canonical intermediate transfer object produced by all three extractors.
 * Each extractor maps its native format (SQL row, CSV row, JSON object)
 * into this common structure before passing to the harmonizer.
 */
@Data
@Builder
public class RawProductRecord {
    private String sku;
    private String description;
    private String productLine;
    private String material;          // may be null — harmonizer infers from description
    private String gauge;             // raw value, may contain "AWG" suffix or "MCM"
    private String voltageRating;
    private String uom;               // raw value, may be "FEET", "FT", "RL", etc.
    private String sourceSystem;      // "ERP" | "CSV" | "API"

    // Inventory fields (populated by API extractor)
    private String warehouseCode;
    private Integer qtyOnHand;
    private Integer qtyAvailable;

    // Pricing fields (populated by CSV extractor)
    private BigDecimal listPrice;
    private BigDecimal distributorPrice;
    private LocalDate effectiveDate;
}
