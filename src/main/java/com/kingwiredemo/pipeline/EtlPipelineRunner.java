package com.kingwiredemo.pipeline;

import com.kingwiredemo.extractor.ErpDatabaseExtractor;
import com.kingwiredemo.extractor.PricingCsvExtractor;
import com.kingwiredemo.extractor.WarehouseApiExtractor;
import com.kingwiredemo.model.dto.PipelineResultDto;
import com.kingwiredemo.model.dto.RawProductRecord;
import com.kingwiredemo.search.ProductIndexingService;
import com.kingwiredemo.transform.DeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates the full ETL pipeline:
 *
 *   Extract  →  3 source systems (ERP DB, Pricing CSV, Warehouse API)
 *   Transform → ProductHarmonizer normalizes all records
 *   Load      → DeduplicationService reconciles into MySQL
 *   Index     → ProductIndexingService syncs MySQL → Elasticsearch
 *
 * Scheduled daily at 02:00 AM to satisfy the "24-hour update cycle"
 * requirement. Can also be triggered manually via POST /api/pipeline/run.
 *
 * Thread safety: AtomicBoolean guard prevents concurrent runs if a manual
 * trigger fires while a scheduled run is in progress.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EtlPipelineRunner {

    private final ErpDatabaseExtractor    erpExtractor;
    private final PricingCsvExtractor     csvExtractor;
    private final WarehouseApiExtractor   apiExtractor;
    private final DeduplicationService    deduplicationService;
    private final ProductIndexingService  indexingService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Scheduled trigger — runs nightly at 02:00.
     * Cron: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledRun() {
        log.info("=== Scheduled ETL pipeline triggered ===");
        run();
    }

    /**
     * Manual trigger — called by PipelineController.
     * Evicts all caches so the next API request reflects fresh data.
     */
    @CacheEvict(cacheNames = {"productSummary", "productDetail"}, allEntries = true)
    public PipelineResultDto run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Pipeline already running — skipping concurrent execution");
            return PipelineResultDto.builder()
                    .status("SKIPPED")
                    .message("Pipeline already in progress")
                    .build();
        }

        LocalDateTime startedAt = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        log.info("=== KingWire ETL Pipeline starting at {} ===", startedAt);

        try {
            // ── EXTRACT ──────────────────────────────────────────────────────
            log.info("Phase 1: Extraction");
            List<RawProductRecord> erpRecords = erpExtractor.extract();
            List<RawProductRecord> csvRecords = csvExtractor.extract();

            // Warehouse API needs known SKUs to generate/correlate inventory
            List<String> knownSkus = erpRecords.stream().map(RawProductRecord::getSku).toList();
            List<RawProductRecord> apiRecords = apiExtractor.extract(knownSkus);

            int totalExtracted = erpRecords.size() + csvRecords.size() + apiRecords.size();
            log.info("Extraction complete: {} ERP + {} CSV + {} API = {} total records",
                    erpRecords.size(), csvRecords.size(), apiRecords.size(), totalExtracted);

            // ── LOAD / RECONCILE ─────────────────────────────────────────────
            log.info("Phase 2: Harmonize & reconcile products");
            DeduplicationService.ReconciliationResult result =
                    deduplicationService.reconcileProducts(erpRecords);

            log.info("Phase 3: Reconcile inventory");
            deduplicationService.reconcileInventory(apiRecords);

            log.info("Phase 4: Reconcile pricing");
            deduplicationService.reconcilePricing(csvRecords);

            // ── INDEX → ELASTICSEARCH ────────────────────────────────────────
            log.info("Phase 5: Sync MySQL → Elasticsearch");
            int indexed = indexingService.reindex();

            long durationMs = System.currentTimeMillis() - startMs;
            log.info("=== Pipeline complete in {}ms — inserted:{} updated:{} skipped:{} conflicts:{} indexed:{} ===",
                    durationMs, result.inserted(), result.updated(), result.skipped(), result.conflicts(), indexed);

            return PipelineResultDto.builder()
                    .startedAt(startedAt)
                    .completedAt(LocalDateTime.now())
                    .durationMs(durationMs)
                    .erpRecordsExtracted(erpRecords.size())
                    .csvRecordsExtracted(csvRecords.size())
                    .apiRecordsExtracted(apiRecords.size())
                    .totalExtracted(totalExtracted)
                    .inserted(result.inserted())
                    .updated(result.updated())
                    .skipped(result.skipped())
                    .conflicts(result.conflicts())
                    .elasticsearchDocumentsIndexed(indexed)
                    .status("SUCCESS")
                    .message("Pipeline completed successfully")
                    .build();

        } catch (Exception e) {
            log.error("=== Pipeline FAILED: {} ===", e.getMessage(), e);
            return PipelineResultDto.builder()
                    .startedAt(startedAt)
                    .completedAt(LocalDateTime.now())
                    .durationMs(System.currentTimeMillis() - startMs)
                    .status("FAILED")
                    .message(e.getMessage())
                    .build();
        } finally {
            running.set(false);
        }
    }
}
