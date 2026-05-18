# KingWire Pipeline Demo

A backend data pipeline and REST API demonstrating extraction, harmonization, and centralized serving of product catalog data — built as an independent technical validation project.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         SOURCE SYSTEMS                                    │
│                                                                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────────┐ │
│  │  Legacy ERP DB  │  │  Pricing CSV    │  │  Warehouse REST API      │ │
│  │  (H2 / JDBC)    │  │  (OpenCSV)      │  │  (RestTemplate)          │ │
│  │                 │  │                 │  │                          │ │
│  │  item_master:   │  │  pricing-       │  │  /api/v1/inventory       │ │
│  │  item_no        │  │  export.csv     │  │  → qty by warehouse      │ │
│  │  item_desc      │  │  list/dist      │  │  (synthetic fallback)    │ │
│  │  product_class  │  │  prices         │  │                          │ │
│  └────────┬────────┘  └────────┬────────┘  └────────────┬─────────────┘ │
└───────────┼────────────────────┼──────────────────────────┼──────────────┘
            │                    │                          │
            ▼                    ▼                          ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         ETL PIPELINE                                      │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐ │
│  │ EtlPipelineRunner                                                   │ │
│  │   @Scheduled(cron="0 0 2 * * ?") — nightly 2AM                    │ │
│  │   POST /api/pipeline/run         — manual trigger                  │ │
│  │                                                                     │ │
│  │  1. Extract   → ErpDatabaseExtractor                               │ │
│  │                 PricingCsvExtractor                                 │ │
│  │                 WarehouseApiExtractor                               │ │
│  │                                                                     │ │
│  │  2. Harmonize → ProductHarmonizer                                  │ │
│  │                 - normalize SKU, UOM, gauge, material               │ │
│  │                 - infer material from description when absent       │ │
│  │                                                                     │ │
│  │  3. Reconcile → DeduplicationService                               │ │
│  │                 - INSERT / UPDATE / SKIP / CONFLICT by SKU key     │ │
│  │                 - write audit trail to reconciliation_log           │ │
│  │                                                                     │ │
│  │  4. Index     → ProductIndexingService                             │ │
│  │                 - full reindex MySQL → Elasticsearch               │ │
│  └─────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────────────────────────┘
                           │
           ┌───────────────┴───────────────┐
           ▼                               ▼
┌──────────────────────┐        ┌─────────────────────┐
│     MySQL 8          │        │   Elasticsearch 8   │
│  (source of truth)   │        │   (search index)    │
│                      │        │                     │
│  products            │        │  products index     │
│  inventory           │        │  - full-text search │
│  pricing             │        │  - fuzzy match      │
│  reconciliation_log  │        │  - faceted filter   │
└──────────┬───────────┘        └──────────┬──────────┘
           │                               │
           └───────────────┬───────────────┘
                           ▼
┌──────────────────────────────────────────────────────┐
│              Spring Boot REST API (8080)              │
│                                                      │
│  GET  /api/products              ← MySQL + cache     │
│  GET  /api/products/{sku}        ← MySQL JOIN FETCH  │
│  GET  /api/products/summary      ← MySQL + Caffeine  │
│  GET  /api/inventory             ← MySQL             │
│  GET  /api/pricing               ← MySQL             │
│  GET  /api/search?q=             ← Elasticsearch     │
│  POST /api/pipeline/run          ← manual ETL        │
│  GET  /actuator/health           ← health check      │
└──────────────────────────────────────────────────────┘
```

---

## Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 17 | |
| Framework | Spring Boot 3.2.5 | Web, JPA, Cache, Scheduling |
| Primary DB | MySQL 8 | Central harmonized datastore (source of truth) |
| Legacy Source | H2 Embedded | Simulates on-premise ERP replica |
| Search | Elasticsearch 8.13 | Full-text product search index |
| Visualisation | Kibana 8.13 | Index exploration (dev tool) |
| Cache | Caffeine | In-process TTL cache for aggregate queries |
| Connection Pool | HikariCP | Explicit pool tuning for MySQL |
| Migrations | Flyway | Versioned schema management |
| CSV Parsing | OpenCSV | Pricing flat-file extraction |
| ORM | Hibernate / Spring Data JPA | Central MySQL read/write |
| Build | Maven | Dependency management |
| Container | Docker Compose | Local environment orchestration |

---

## Quick Start

**Prerequisites:** Docker Desktop, Java 17, Maven 3.9+

```bash
# 1. Clone and build
git clone https://github.com/YOUR_USERNAME/kingwire-pipeline-demo
cd kingwire-pipeline-demo

# 2. Start MySQL + Elasticsearch + Kibana
docker-compose up mysql elasticsearch kibana -d

# 3. Wait ~15 seconds for services to be healthy, then run the app
mvn spring-boot:run

# 4. Trigger the ETL pipeline (seeds all data)
curl -X POST http://localhost:8080/api/pipeline/run

# 5. Query the API
curl "http://localhost:8080/api/products?productLine=Aluminum+XHHW&size=5"
curl "http://localhost:8080/api/search?q=aluminum+2/0+600v"
curl "http://localhost:8080/api/products/summary"

# Kibana index explorer
open http://localhost:5601
```

Or run everything in Docker:
```bash
docker-compose up --build
```

---

## API Reference

### Products (MySQL)

```
GET /api/products
    ?productLine=Aluminum XHHW
    ?material=Aluminum
    ?source=ERP
    ?page=0&size=25&sort=sku

GET /api/products/{sku}
    Returns full product detail with inventory and pricing (JOIN FETCH)

GET /api/products/summary
    Aggregate counts by source + product line. Cached 10 min.
```

### Inventory & Pricing

```
GET /api/inventory?warehouse=CHI
GET /api/inventory/warehouses         (distinct warehouse codes)
GET /api/pricing?type=DISTRIBUTOR
```

### Search (Elasticsearch)

```
GET /api/search?q=aluminum xhhw 2/0   (full-text, fuzzy)
GET /api/search/by-line?productLine=Copper SOOW
GET /api/search/by-material?material=Aluminum
```

### Pipeline

```
POST /api/pipeline/run
     → returns PipelineResultDto with counts, timing, ES index total
```

### Operations

```
GET /actuator/health
GET /actuator/metrics
GET /actuator/caches
```

---

## Performance Design Decisions

### HikariCP Pool Tuning (DataSourceConfig.java)
```
maximumPoolSize = 10     Handles concurrent API requests without over-provisioning
minimumIdle = 2          Keeps warm connections available without waste
connectionTimeout = 30s  Fail fast on pool exhaustion — surface problems quickly
idleTimeout = 10min      Reclaim idle connections in low-traffic windows
maxLifetime = 30min      Rotate connections before MySQL's wait_timeout (8h)
```

### Caching (CacheConfig.java)
- `productSummary` (10-min TTL): GROUP BY aggregate across full catalog — expensive, not real-time critical
- `productDetail` (5-min TTL): JOIN-loaded product with inventory and pricing
- Both caches evicted via `@CacheEvict` on pipeline completion

### Database Indexes (V1__create_schema.sql)
All filter columns are indexed: `product_line`, `source`, `material`, `warehouse_code`, `price_type`, `sku` (unique)

### Pagination
All list endpoints use Spring `Pageable` — no unbounded result sets returned

### Elasticsearch Full-Text Search
Multi-field fuzzy search with description field boosted 2x. Handles spec abbreviation
variants (XHHW/XHWW, "2/0 AWG"/"2/0") that would require complex LIKE chains in MySQL.

---

## Commit History

```
init: scaffold spring boot project with maven and docker-compose
feat: flyway V1 migration — products, inventory, pricing, reconciliation_log
feat: h2 legacy erp schema and seed data (29 representative sku records)
feat: pricing export csv with list and distributor prices
feat: implement erp database extractor via jdbctemplate
feat: implement pricing csv extractor with opencsv
feat: implement warehouse api extractor with synthetic fallback
feat: add product harmonizer — normalize sku, gauge, uom, material inference
feat: add deduplication service with insert/update/skip/conflict reconciliation
feat: wire etl pipeline runner with @scheduled nightly cron
feat: configure hikaricp connection pool with explicit tuning
feat: implement product catalog rest api with pagination and filtering
feat: add inventory and pricing endpoints
feat: add elasticsearch product document and search repository
feat: implement product indexing service — mysql to es sync
feat: add full-text search controller backed by elasticsearch
feat: add caffeine caching on summary endpoint with cache eviction
feat: add pipeline controller for manual etl trigger
feat: add pipeline result dto with per-phase record counts and timing
test: unit tests for product harmonizer normalization rules
test: unit tests for deduplication service insert/update/skip/conflict
docs: add README with architecture diagram and api reference
docs: add DECISIONS.md with architectural tradeoff rationale
```

---

## Project Structure

```
kingwire-pipeline-demo/
├── docker-compose.yml
├── Dockerfile
├── README.md
├── DECISIONS.md
├── pom.xml
└── src/
    ├── main/java/com/kingwiredemo/
    │   ├── KingwireDemoApplication.java
    │   ├── config/
    │   │   ├── DataSourceConfig.java       HikariCP + legacy H2 datasource
    │   │   └── CacheConfig.java            Caffeine TTL configuration
    │   ├── extractor/
    │   │   ├── ErpDatabaseExtractor.java   JdbcTemplate → legacy ERP
    │   │   ├── PricingCsvExtractor.java    OpenCSV → pricing-export.csv
    │   │   └── WarehouseApiExtractor.java  RestTemplate → warehouse API
    │   ├── transform/
    │   │   ├── ProductHarmonizer.java      Normalize across all sources
    │   │   └── DeduplicationService.java   Reconcile into MySQL
    │   ├── pipeline/
    │   │   └── EtlPipelineRunner.java      Orchestrate all phases
    │   ├── search/
    │   │   └── ProductIndexingService.java MySQL → Elasticsearch sync
    │   ├── api/
    │   │   ├── ProductController.java
    │   │   ├── InventoryController.java
    │   │   ├── PricingController.java
    │   │   ├── SearchController.java
    │   │   └── PipelineController.java
    │   ├── model/
    │   │   ├── entity/                     JPA entities (Product, Inventory, Pricing, ReconciliationLog)
    │   │   ├── dto/                        RawProductRecord, ProductResponseDto, PipelineResultDto
    │   │   └── search/                     ProductDocument (ES mapping)
    │   └── repository/                     JPA + ES repositories
    └── main/resources/
        ├── application.yml
        ├── data/pricing-export.csv
        └── db/
            ├── migration/V1__create_schema.sql    (Flyway — MySQL)
            └── legacy/
                ├── V1__legacy_erp_schema.sql      (H2 seed)
                └── V2__legacy_erp_seed.sql
```
