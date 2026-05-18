package com.kingwiredemo.transform;

import com.kingwiredemo.model.entity.*;
import com.kingwiredemo.model.dto.RawProductRecord;
import com.kingwiredemo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reconciles incoming harmonized records against the existing MySQL datastore.
 *
 * Deduplication strategy: natural key on SKU (unique per product).
 *
 * Actions:
 *   INSERT   — SKU not found in database, create new Product
 *   UPDATE   — SKU exists, fields differ → update and log what changed
 *   SKIP     — SKU exists, all fields identical → no-op
 *   CONFLICT — Data integrity issue (e.g. null SKU) → log and skip
 *
 * All reconciliation events are written to reconciliation_log for audit
 * and operational visibility. This log replaces the need for manual
 * comparison spreadsheets — a direct analog to the role requirements.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeduplicationService {

    private final ProductRepository           productRepository;
    private final InventoryRepository         inventoryRepository;
    private final PricingRepository           pricingRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;
    private final ProductHarmonizer           harmonizer;

    public record ReconciliationResult(int inserted, int updated, int skipped, int conflicts) {
        public static ReconciliationResult empty() { return new ReconciliationResult(0, 0, 0, 0); }
    }

    @Transactional
    public ReconciliationResult reconcileProducts(List<RawProductRecord> rawRecords) {
        int inserted = 0, updated = 0, skipped = 0, conflicts = 0;

        for (RawProductRecord raw : rawRecords) {
            if (raw.getSku() == null || raw.getSku().isBlank()) {
                log.warn("[DEDUP] Skipping record with null/blank SKU from source: {}", raw.getSourceSystem());
                logReconciliation(null, raw.getSourceSystem(), "CONFLICT", "Null or blank SKU");
                conflicts++;
                continue;
            }

            String normalizedSku = harmonizer.normalizeSku(raw.getSku());
            Optional<Product> existing = productRepository.findBySku(normalizedSku);

            if (existing.isEmpty()) {
                // New product — insert
                Product product = harmonizer.toProduct(raw);
                productRepository.save(product);
                logReconciliation(normalizedSku, raw.getSourceSystem(), "INSERT", "New product from " + raw.getSourceSystem());
                inserted++;
            } else {
                // Existing product — diff and update if needed
                Product product = existing.get();
                List<String> diffs = computeDiff(product, raw);

                if (!diffs.isEmpty()) {
                    applyUpdates(product, raw);
                    productRepository.save(product);
                    logReconciliation(normalizedSku, raw.getSourceSystem(), "UPDATE", String.join("; ", diffs));
                    updated++;
                } else {
                    skipped++;
                }
            }
        }

        return new ReconciliationResult(inserted, updated, skipped, conflicts);
    }

    @Transactional
    public void reconcileInventory(List<RawProductRecord> apiRecords) {
        for (RawProductRecord raw : apiRecords) {
            if (raw.getSku() == null || raw.getWarehouseCode() == null) continue;

            String sku = harmonizer.normalizeSku(raw.getSku());
            productRepository.findBySku(sku).ifPresent(product -> {
                Optional<Inventory> existing = inventoryRepository
                        .findByProductIdAndWarehouseCode(product.getId(), raw.getWarehouseCode());

                if (existing.isPresent()) {
                    Inventory inv = existing.get();
                    inv.setQtyOnHand(raw.getQtyOnHand());
                    inv.setQtyAvailable(raw.getQtyAvailable());
                    inv.setLastSyncedAt(java.time.LocalDateTime.now());
                    inventoryRepository.save(inv);
                } else {
                    inventoryRepository.save(harmonizer.toInventory(raw, product));
                }
            });
        }
    }

    @Transactional
    public void reconcilePricing(List<RawProductRecord> csvRecords) {
        for (RawProductRecord raw : csvRecords) {
            if (raw.getSku() == null) continue;

            String sku = harmonizer.normalizeSku(raw.getSku());
            productRepository.findBySku(sku).ifPresent(product -> {
                if (raw.getListPrice() != null) {
                    upsertPricing(product, "LIST", raw);
                }
                if (raw.getDistributorPrice() != null) {
                    upsertPricing(product, "DISTRIBUTOR", raw);
                }
            });
        }
    }

    private void upsertPricing(Product product, String priceType, RawProductRecord raw) {
        Optional<Pricing> existing = pricingRepository.findByProductIdAndPriceType(product.getId(), priceType);
        if (existing.isPresent()) {
            Pricing p = existing.get();
            p.setUnitPrice("LIST".equals(priceType) ? raw.getListPrice() : raw.getDistributorPrice());
            p.setEffectiveDt(raw.getEffectiveDate());
            pricingRepository.save(p);
        } else {
            pricingRepository.save("LIST".equals(priceType)
                    ? harmonizer.toListPricing(raw, product)
                    : harmonizer.toDistributorPricing(raw, product));
        }
    }

    private List<String> computeDiff(Product existing, RawProductRecord incoming) {
        List<String> diffs = new ArrayList<>();
        Product harmonized = harmonizer.toProduct(incoming);

        if (changed(existing.getDescription(), harmonized.getDescription()))
            diffs.add("description: '" + existing.getDescription() + "' → '" + harmonized.getDescription() + "'");
        if (changed(existing.getProductLine(), harmonized.getProductLine()))
            diffs.add("productLine: '" + existing.getProductLine() + "' → '" + harmonized.getProductLine() + "'");
        if (changed(existing.getMaterial(), harmonized.getMaterial()))
            diffs.add("material: '" + existing.getMaterial() + "' → '" + harmonized.getMaterial() + "'");
        if (changed(existing.getGauge(), harmonized.getGauge()))
            diffs.add("gauge: '" + existing.getGauge() + "' → '" + harmonized.getGauge() + "'");
        if (changed(existing.getVoltageRating(), harmonized.getVoltageRating()))
            diffs.add("voltageRating: '" + existing.getVoltageRating() + "' → '" + harmonized.getVoltageRating() + "'");

        return diffs;
    }

    private void applyUpdates(Product product, RawProductRecord raw) {
        Product harmonized = harmonizer.toProduct(raw);
        product.setDescription(harmonized.getDescription());
        product.setProductLine(harmonized.getProductLine());
        product.setMaterial(harmonized.getMaterial());
        product.setGauge(harmonized.getGauge());
        product.setVoltageRating(harmonized.getVoltageRating());
        product.setUom(harmonized.getUom());
    }

    private boolean changed(String a, String b) {
        if (a == null && b == null) return false;
        if (a == null || b == null) return true;
        return !a.equals(b);
    }

    private void logReconciliation(String sku, String source, String action, String notes) {
        reconciliationLogRepository.save(ReconciliationLog.builder()
                .sku(sku)
                .source(source)
                .action(action)
                .diffNotes(notes)
                .build());
    }
}
