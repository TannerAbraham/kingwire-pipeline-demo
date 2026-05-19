# Architectural Decision Record - KingWire Pipeline Demo

## 1. MySQL over PostgreSQL

**Decision:** MySQL 8 is the central datastore.

**Rationale:** KingWire's job description and existing infrastructure references MySQL/MariaDB explicitly. Matching the target environment is a practical engineering choice - it avoids migration risk and ensures the schema, index hints, and connection configuration are directly applicable to the production environment.

---

## 2. H2 Embedded Database for Legacy ERP Source

**Decision:** The "legacy ERP" source is simulated via an H2 in-memory database, seeded with representative item master data on application startup.

**Rationale:** In production, this datasource would be a JDBC/ODBC connection to a replicated Epicor or SAP database - a read-only replica of the on-premise ERP to avoid placing any load on the transactional system. H2 allows the demo to run fully self-contained without an actual ERP instance. The extraction code (`ErpDatabaseExtractor`) uses raw `JdbcTemplate` rather than JPA entities because legacy ERP schemas rarely conform to clean ORM mapping - they use vendor-specific column conventions, compound keys, and type mismatches that are better handled with explicit SQL.

---

## 3. JdbcTemplate for ERP Extraction, JPA for Central Store

**Decision:** `JdbcTemplate` for reading the legacy source; Spring Data JPA + Hibernate for writing to and reading from MySQL.

**Rationale:** The legacy ERP schema is read-only and has irregular column names (`item_no`, `item_desc`, `product_class`). JdbcTemplate gives explicit SQL control with no magic. The central MySQL schema is designed by us - it maps cleanly to JPA entities, and Spring Data repositories provide pagination, sorting, and derived queries with minimal boilerplate.

---

## 4. Flyway for Schema Migration

**Decision:** Flyway manages all DDL against the primary MySQL datasource.

**Rationale:** Versioned migrations provide a reproducible, auditable schema history. `ddl-auto: validate` ensures Hibernate only validates that entities match the schema - it never modifies it. This is the correct production posture: Flyway owns schema evolution, Hibernate does not.

---

## 5. Caffeine over Redis for Caching

**Decision:** Caffeine in-process cache for the summary and detail endpoints.

**Rationale:** The API is a single-instance service. A distributed cache (Redis) would add network latency (1–5ms per hit) and operational overhead (another service to deploy, monitor, and fail) without benefit. Caffeine provides sub-microsecond in-process reads. The correct time to introduce Redis is when the API scales to multiple instances - at that point, cache consistency across instances becomes necessary. Adding it prematurely is over-engineering.

Cache TTLs:
- `productSummary` - 10 minutes. Aggregate GROUP BY queries are expensive and don't need real-time freshness for a reporting dashboard.
- `productDetail` - 5 minutes. JOIN-loaded product detail is moderately expensive and changes only after pipeline runs.

Both caches are evicted via `@CacheEvict` when the pipeline completes.

---

## 6. Elasticsearch as Read-Optimized Search Layer (not source of truth)

**Decision:** MySQL is the source of truth. Elasticsearch is populated as a derived read index after each ETL run.

**Rationale:** Dual-write patterns (writing to both MySQL and ES simultaneously) introduce consistency risks - if one write fails, the stores diverge. By treating ES as a downstream projection of verified MySQL data, we guarantee that every document in the search index has passed through harmonization and deduplication. The full-reindex-after-pipeline approach is appropriate for this catalog scale (thousands of SKUs). For millions of documents, a delta-sync strategy using `updated_at` timestamps would be preferred.

ES is justified over MySQL `LIKE` queries because:
- Multi-field search across description, gauge, product line, and material in a single query
- Fuzzy matching handles spec abbreviation variants (XHHW vs XHWW, 2/0 AWG vs 2/0)
- The `Keyword` field type enables exact-match faceted filtering with zero performance penalty

---

## 7. AtomicBoolean Pipeline Guard

**Decision:** `EtlPipelineRunner` uses an `AtomicBoolean` to prevent concurrent pipeline executions.

**Rationale:** The nightly `@Scheduled` cron and the manual `/api/pipeline/run` endpoint both call the same `run()` method. Without a concurrency guard, a slow pipeline triggered by cron could overlap with a manual trigger, causing duplicate inserts and reconciliation log spam. `AtomicBoolean.compareAndSet` provides thread-safe mutual exclusion without introducing a lock or a distributed coordination dependency.

---

## 8. Warehouse API Fallback to Synthetic Data

**Decision:** If `warehouse.api.url` is not configured or the endpoint is unreachable, `WarehouseApiExtractor` generates synthetic inventory data using a seeded random.

**Rationale:** The pipeline is an async batch process. Partial completion (products loaded, inventory unavailable) is more useful than total failure. The synthetic fallback uses a fixed seed (`Random(42)`) to produce reproducible results across runs, which aids testing and debugging. A production implementation would replace the synthetic fallback with a dead-letter queue and alerting.

---

## 9. Pipeline Returns Detailed Result DTO

**Decision:** `EtlPipelineRunner.run()` returns a `PipelineResultDto` with record counts, timing, and per-action breakdowns.

**Rationale:** Operational visibility without a log scraper. A DevOps team or stakeholder can `POST /api/pipeline/run` and immediately see how many records were inserted vs updated vs skipped, how long it took, and whether ES indexing succeeded - all in a single API response. This directly replaces the "how did last night's ETL go?" question that otherwise requires reading server logs.
