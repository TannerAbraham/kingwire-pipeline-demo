# KingWire Pipeline Demo

![Java](https://img.shields.io/badge/Java-17-blue) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-blue) ![MySQL](https://img.shields.io/badge/MySQL-8.0-blue) ![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.13-blue) ![Docker](https://img.shields.io/badge/Docker-Compose-blue)

A backend data pipeline and REST API demonstrating extraction, harmonization, and centralized serving of product catalog data - built as an independent technical validation project.

---

## Table of Contents

- [Assessment Response](#assessment-response)
- [Live Deployment](#live-deployment)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [ETL Pipeline Flow](#etl-pipeline-flow)
- [Spring Application Architecture](#spring-application-architecture)
- [Data Model](#data-model)
- [Reconciliation Log](#reconciliation-log)
- [UML Class Diagram](#uml-class-diagram)
- [API Routes](#api-routes)
- [Sample API Responses](#sample-api-responses)
- [Environment Variables](#environment-variables)
- [Stack](#stack)
- [Java Spring vs Python](#java-spring-vs-python)
- [Performance Design Decisions](#performance-design-decisions)
- [Production Next Steps](#production-next-steps)
- [CI/CD Pipeline](#cicd-pipeline)
- [Commit History](#commit-history)
- [Project Structure](#project-structure)
- [Thank You](#thank-you)

---

## Assessment Response

This project directly addresses KingWire's two-part technical assessment:

### Requirement 1 - Data Extraction & Harmonization

> *"Evidence of a pipeline extracting, transforming, and reconciling data from disparate databases into a centralized datastore."*

KingWire's operational reality is a common enterprise problem: product, pricing, and inventory data living in separate systems that were never designed to talk to each other. This project models that exact environment:

| Source | Simulates | Technology | Key Challenge |
|---|---|---|---|
| Legacy ERP (H2) | On-premise Epicor/SAP item master | JdbcTemplate | Inconsistent column names, abbreviated material codes (`AL`, `CU`), non-normalized UOM values (`FEET`, `RL`) |
| Pricing CSV | Weekly Excel export from pricing team | OpenCSV | Manual file drops, no schema enforcement, malformed rows |
| Warehouse API | WMS REST inventory feed | RestTemplate | External dependency, unreachable in some environments |

Rather than writing one-off scripts per source, the pipeline establishes a repeatable, auditable pattern:

1. **Each source has a dedicated extractor** - isolated, independently testable, swappable without touching the rest of the pipeline
2. **All sources funnel into a single `RawProductRecord`** - a canonical intermediate format that absorbs schema differences before transformation
3. **`ProductHarmonizer` normalizes every inconsistency** - SKU casing, gauge suffixes (`2/0 AWG` → `2/0`), UOM variants, material inference from description when the field is absent
4. **`DeduplicationService` reconciles by natural key** - every run produces an INSERT / UPDATE / SKIP / CONFLICT decision per record, written to `reconciliation_log` for full auditability
5. **Flyway manages schema versioning** - the central MySQL datastore evolves through versioned migrations, not manual DDL

This directly replaces the spreadsheet-based reconciliation workflow described in KingWire's job description - the pipeline runs nightly at 2AM and can be triggered manually via `POST /api/pipeline/run`.

---

### Requirement 2 - Dynamic Data Serving

> *"Evidence of a middleware or API construction designed to serve that centralized data to a front-end database, with the associated performance considerations."*

The Spring Boot REST API sits between the centralized MySQL datastore and any front-end consumer. It is not a thin CRUD wrapper - it makes deliberate performance decisions at every layer:

| Consideration | Implementation | Why |
|---|---|---|
| **Connection pooling** | HikariCP with explicit `maximumPoolSize`, `connectionTimeout`, `idleTimeout`, `maxLifetime` | Prevent pool exhaustion under concurrent load; rotate connections before MySQL server timeout |
| **Query optimization** | `JOIN FETCH` on detail endpoints; indexed columns on all filter fields | Eliminate N+1 queries; push filtering to the DB layer where indexes apply |
| **Caching** | Caffeine in-process cache on `/api/products/summary` (10-min TTL) | Aggregate GROUP BY across a full product catalog is expensive; result doesn't need real-time freshness |
| **Pagination** | Spring `Pageable` on every list endpoint | No unbounded result sets; front end controls page size |
| **Full-text search** | Elasticsearch with multi-field fuzzy query | Product specs like "600V aluminum 2/0" span multiple fields; MySQL `LIKE` on TEXT columns is a sequential scan at scale |
| **Cache invalidation** | `@CacheEvict` fires after every pipeline run | Ensures the API reflects fresh data immediately after a sync without requiring a restart |

The Elasticsearch layer specifically addresses KingWire's product catalog use case - distributors and contractors search by partial specs across voltage, gauge, and material simultaneously. That query pattern is impractical in SQL and native to Elasticsearch.

---

### Requirement 3 - Solo Execution

> *"Demonstrate your capacity to build a backend data pipeline and a data-serving application layer without reliance on a supporting engineering team."*

The commit history and `DECISIONS.md` document the architectural tradeoffs and implementation decisions made independently, without a supporting team.

---

---

## Live Deployment

The API is live on Railway at:

**https://kingwire-pipeline-demo.up.railway.app**

No setup required. Use the endpoints below to interact with it directly.

**Trigger the ETL pipeline** (seeds all product, inventory, and pricing data):
```bash
curl -X POST https://kingwire-pipeline-demo.up.railway.app/api/pipeline/run
```

**Browse the product catalog:**
```bash
curl "https://kingwire-pipeline-demo.up.railway.app/api/products?size=5"
```

**Filter by product line:**
```bash
curl "https://kingwire-pipeline-demo.up.railway.app/api/products?productLine=Aluminum%20XHHW"
```

**Full-text search:**
```bash
curl "https://kingwire-pipeline-demo.up.railway.app/api/search?q=aluminum+xhhw+2/0"
```

**Inventory by warehouse:**
```bash
curl "https://kingwire-pipeline-demo.up.railway.app/api/inventory?warehouse=CHI"
```

**Pricing by type:**
```bash
curl "https://kingwire-pipeline-demo.up.railway.app/api/pricing?type=DISTRIBUTOR"
```

**Aggregate summary (cached):**
```bash
curl "https://kingwire-pipeline-demo.up.railway.app/api/products/summary"
```

**Health check:**
```bash
curl "https://kingwire-pipeline-demo.up.railway.app/actuator/health"
```

All endpoints can also be pasted directly into a browser or imported into Postman using the base URL above.

---

## Quick Start

**Prerequisites:** Docker Desktop, Java 17, Maven 3.9+

```bash
# 1. Clone and build
git clone https://github.com/TannerAbraham/kingwire-pipeline-demo
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

## Architecture

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#162B4A", "primaryTextColor": "#FFFFFF", "primaryBorderColor": "#2A5298", "lineColor": "#3D6BB5", "secondaryColor": "#0C1929", "tertiaryColor": "#0C1929", "clusterBkg": "#0C1929", "clusterBorder": "#2A5298", "titleColor": "#FFFFFF", "edgeLabelBackground": "#162B4A", "nodeTextColor": "#FFFFFF"}}}%%
flowchart TD
    subgraph SOURCES["Source Systems"]
        ERP["Legacy ERP DB\nH2 / JDBC\nitem_no - item_desc - product_class"]
        CSV["Pricing CSV\nOpenCSV\nlist_price - dist_price - effective_date"]
        API["Warehouse REST API\nRestTemplate\n/api/v1/inventory - synthetic fallback"]
    end

    subgraph PIPELINE["ETL Pipeline - EtlPipelineRunner"]
        direction TB
        E1["1. Extract\nErpDatabaseExtractor\nPricingCsvExtractor\nWarehouseApiExtractor"]
        E2["2. Harmonize\nProductHarmonizer\nnormalize SKU - UOM - gauge - material"]
        E3["3. Reconcile\nDeduplicationService\nINSERT - UPDATE - SKIP - CONFLICT"]
        E4["4. Index\nProductIndexingService\nMySQL to Elasticsearch"]
        E1 --> E2 --> E3 --> E4
    end

    subgraph STORES["Data Stores"]
        MYSQL["MySQL 8\nsource of truth\nproducts - inventory - pricing - reconciliation_log"]
        ES["Elasticsearch 8\nsearch index\nfull-text - fuzzy - faceted"]
    end

    subgraph API_LAYER["Spring Boot REST API :8080"]
        direction LR
        A1["GET /api/products\nGET /api/products/{sku}\nGET /api/products/summary"]
        A2["GET /api/inventory\nGET /api/pricing"]
        A3["GET /api/search?q="]
        A4["POST /api/pipeline/run\nGET /actuator/health"]
    end

    classDef source fill:#2A5298,stroke:#0C1929,color:#FFFFFF
    classDef pipeline fill:#162B4A,stroke:#2A5298,color:#FFFFFF
    classDef store fill:#0C1929,stroke:#2A5298,color:#FFFFFF
    classDef apilayer fill:#2A5298,stroke:#0C1929,color:#FFFFFF

    class ERP,CSV,API source
    class E1,E2,E3,E4 pipeline
    class MYSQL,ES store
    class A1,A2,A3,A4 apilayer

    ERP --> PIPELINE
    CSV --> PIPELINE
    API --> PIPELINE
    PIPELINE --> MYSQL
    E4 --> ES
    MYSQL --> A1
    MYSQL --> A2
    ES --> A3
    MYSQL --> A4
```

---

## ETL Pipeline Flow

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#162B4A", "primaryTextColor": "#FFFFFF", "primaryBorderColor": "#2A5298", "lineColor": "#3D6BB5", "secondaryColor": "#0C1929", "actorBkg": "#162B4A", "actorBorder": "#2A5298", "actorTextColor": "#FFFFFF", "actorLineColor": "#3D6BB5", "signalColor": "#3D6BB5", "signalTextColor": "#FFFFFF", "labelBoxBkgColor": "#0C1929", "labelBoxBorderColor": "#2A5298", "labelTextColor": "#FFFFFF", "loopTextColor": "#FFFFFF", "noteBkgColor": "#2A5298", "noteTextColor": "#0C1929", "noteBorderColor": "#0C1929", "activationBkgColor": "#2A5298", "activationBorderColor": "#0C1929", "sequenceNumberColor": "#FFFFFF"}}}%%
sequenceDiagram
    participant Trigger as Trigger (Cron / API)
    participant Runner as EtlPipelineRunner
    participant ERP as ErpDatabaseExtractor
    participant CSV as PricingCsvExtractor
    participant WH as WarehouseApiExtractor
    participant H as ProductHarmonizer
    participant D as DeduplicationService
    participant MySQL as MySQL
    participant ES as Elasticsearch

    Trigger->>Runner: run()
    Runner->>ERP: extract()
    ERP-->>Runner: List RawProductRecord
    Runner->>CSV: extract()
    CSV-->>Runner: List RawProductRecord
    Runner->>WH: extract(knownSkus)
    WH-->>Runner: List RawProductRecord

    Runner->>D: reconcileProducts(erpRecords)
    D->>H: toProduct(raw)
    H-->>D: Product
    D->>MySQL: INSERT / UPDATE / SKIP
    D->>MySQL: write reconciliation_log

    Runner->>D: reconcileInventory(apiRecords)
    D->>MySQL: upsert inventory

    Runner->>D: reconcilePricing(csvRecords)
    D->>MySQL: upsert pricing

    Runner->>ES: reindex()
    ES-->>Runner: docsIndexed
    Runner-->>Trigger: PipelineResultDto
```

---

## Spring Application Architecture

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#162B4A", "primaryTextColor": "#FFFFFF", "primaryBorderColor": "#2A5298", "lineColor": "#3D6BB5", "secondaryColor": "#0C1929", "tertiaryColor": "#0C1929", "clusterBkg": "#0C1929", "clusterBorder": "#2A5298", "titleColor": "#FFFFFF", "edgeLabelBackground": "#162B4A", "nodeTextColor": "#FFFFFF"}}}%%
flowchart TB
    subgraph CLIENT["Client / Front End"]
        FE["HTTP Requests"]
    end

    subgraph CONTROLLER["Controller Layer - com.kingwiredemo.api"]
        PC["ProductController\nPagination - Filtering - Caching"]
        IC["InventoryController\nWarehouse filtering"]
        PRC["PricingController\nPrice type filtering"]
        SC["SearchController\nFull-text - Fuzzy - Faceted"]
        PLC["PipelineController\nManual ETL trigger"]
    end

    subgraph SERVICE["Service Layer - com.kingwiredemo.pipeline - transform - search"]
        RUNNER["EtlPipelineRunner\nOrchestrates all ETL phases\n@Scheduled nightly 2AM\nAtomicBoolean concurrency guard"]
        HARM["ProductHarmonizer\nNormalizes SKU - UOM - gauge\nInfers material from description"]
        DEDUP["DeduplicationService\nINSERT - UPDATE - SKIP - CONFLICT\nWrites reconciliation_log audit trail"]
        INDEX["ProductIndexingService\nFull reindex MySQL to Elasticsearch\nRuns after every pipeline completion"]
    end

    subgraph EXTRACTOR["Extraction Layer - com.kingwiredemo.extractor"]
        ERP["ErpDatabaseExtractor\nJdbcTemplate to H2 legacy ERP\nMaps ERP item_master columns"]
        CSV["PricingCsvExtractor\nOpenCSV to pricing-export.csv\nLenient row-level error handling"]
        WH["WarehouseApiExtractor\nRestTemplate to WMS REST API\nSynthetic fallback if unreachable"]
    end

    subgraph REPO["Repository Layer - com.kingwiredemo.repository"]
        JPA["Spring Data JPA\nProductRepository - InventoryRepository\nPricingRepository - ReconciliationLogRepository\nJOIN FETCH - Projections - Pageable"]
        ESR["ElasticsearchRepository\nProductSearchRepository\nMulti-field query with AUTO fuzziness"]
    end

    subgraph CONFIG["Configuration - com.kingwiredemo.config"]
        DS["DataSourceConfig\nHikariCP pool tuning\nPrimary MySQL + Legacy H2 datasources"]
        CC["CacheConfig\nCaffeine - 10min summary TTL\n5min product detail TTL"]
    end

    subgraph DATA["Data Layer"]
        MYSQL[("MySQL 8\nSource of truth\nproducts - inventory\npricing - recon_log")]
        ES[("Elasticsearch 8\nSearch index\nProductDocument")]
        H2[("H2 Embedded\nLegacy ERP replica\nerp_item_master")]
    end

    classDef client fill:#2A5298,stroke:#0C1929,color:#FFFFFF
    classDef controller fill:#162B4A,stroke:#2A5298,color:#FFFFFF
    classDef service fill:#1A3560,stroke:#2A5298,color:#FFFFFF
    classDef extractor fill:#162B4A,stroke:#2A5298,color:#FFFFFF
    classDef repo fill:#0C1929,stroke:#2A5298,color:#FFFFFF
    classDef config fill:#243D6B,stroke:#2A5298,color:#FFFFFF
    classDef data fill:#2A5298,stroke:#0C1929,color:#FFFFFF

    class FE client
    class PC,IC,PRC,SC,PLC controller
    class RUNNER,HARM,DEDUP,INDEX service
    class ERP,CSV,WH extractor
    class JPA,ESR repo
    class DS,CC config
    class MYSQL,ES,H2 data

    FE --> CONTROLLER
    CONTROLLER --> SERVICE
    CONTROLLER --> REPO
    SERVICE --> EXTRACTOR
    SERVICE --> REPO
    EXTRACTOR --> H2
    REPO --> MYSQL
    REPO --> ES
    CONFIG -.->|configures| REPO
    CONFIG -.->|configures| SERVICE
    CONFIG -.->|configures| EXTRACTOR
```

---

## Data Model

```mermaid
%%{init: {"theme": "dark", "themeVariables": {"primaryColor": "#162B4A", "primaryTextColor": "#FFFFFF", "primaryBorderColor": "#2A5298", "lineColor": "#3D6BB5", "secondaryColor": "#0C1929", "tertiaryColor": "#0C1929", "background": "#0C1929", "mainBkg": "#162B4A", "nodeBorder": "#2A5298", "clusterBkg": "#0C1929", "titleColor": "#FFFFFF", "edgeLabelBackground": "#0C1929", "attributeBackgroundColorEven": "#162B4A", "attributeBackgroundColorOdd": "#1E3D6B", "attributeColor": "#FFFFFF", "attributeTextColor": "#FFFFFF", "entityTextColor": "#FFFFFF"}}}%%
erDiagram
    PRODUCTS {
        bigint id PK
        varchar sku UK
        varchar description
        varchar product_line
        varchar material
        varchar gauge
        varchar voltage_rating
        varchar uom
        varchar source
        timestamp created_at
        timestamp updated_at
    }
    INVENTORY {
        bigint id PK
        bigint product_id FK
        varchar warehouse_code
        int qty_on_hand
        int qty_available
        timestamp last_synced_at
    }
    PRICING {
        bigint id PK
        bigint product_id FK
        varchar price_type
        decimal unit_price
        date effective_dt
    }
    RECONCILIATION_LOG {
        bigint id PK
        varchar sku
        varchar source
        varchar action
        text diff_notes
        timestamp run_at
    }

    PRODUCTS ||--o{ INVENTORY : "has"
    PRODUCTS ||--o{ PRICING : "has"
```

---

## Reconciliation Log

Every pipeline run writes a full audit trail to `reconciliation_log`. Each record captures the SKU, source system, action taken, and a human-readable diff of what changed.

| Action | When it fires |
|---|---|
| `INSERT` | SKU not previously seen - new product created |
| `UPDATE` | SKU exists but one or more fields differ - diff recorded |
| `SKIP` | SKU exists and all fields are identical - no write needed |
| `CONFLICT` | Record could not be processed - null SKU, malformed data |

This table directly replaces the manual spreadsheet comparison process - every data change is timestamped, attributed to a source system, and queryable via `GET /api/reconciliation` without opening a file.


## UML Class Diagram

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#162B4A", "primaryTextColor": "#FFFFFF", "primaryBorderColor": "#2A5298", "lineColor": "#3D6BB5", "secondaryColor": "#0C1929", "tertiaryColor": "#0C1929", "clusterBkg": "#0C1929", "clusterBorder": "#2A5298", "titleColor": "#FFFFFF", "edgeLabelBackground": "#162B4A", "nodeTextColor": "#FFFFFF", "classText": "#FFFFFF", "background": "#0C1929"}}}%%
classDiagram
    direction TB

    %% ── Entity Model ──────────────────────────────────────────────────────

    class Product {
        +Long id
        +String sku
        +String description
        +String productLine
        +String material
        +String gauge
        +String voltageRating
        +String uom
        +String source
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
        +List~Inventory~ inventories
        +List~Pricing~ pricings
    }

    class Inventory {
        +Long id
        +Product product
        +String warehouseCode
        +Integer qtyOnHand
        +Integer qtyAvailable
        +LocalDateTime lastSyncedAt
    }

    class Pricing {
        +Long id
        +Product product
        +String priceType
        +BigDecimal unitPrice
        +LocalDate effectiveDt
    }

    class ReconciliationLog {
        +Long id
        +String sku
        +String source
        +String action
        +String diffNotes
        +LocalDateTime runAt
    }

    %% ── DTOs ──────────────────────────────────────────────────────────────

    class RawProductRecord {
        +String sku
        +String description
        +String productLine
        +String material
        +String gauge
        +String voltageRating
        +String uom
        +String sourceSystem
        +String warehouseCode
        +Integer qtyOnHand
        +Integer qtyAvailable
        +BigDecimal listPrice
        +BigDecimal distributorPrice
        +LocalDate effectiveDate
    }

    class ProductResponseDto {
        +Long id
        +String sku
        +String description
        +String productLine
        +String material
        +String gauge
        +Map~String,Integer~ stockByWarehouse
        +int totalStock
        +BigDecimal listPrice
        +BigDecimal distributorPrice
        +from(Product)$ ProductResponseDto
    }

    class PipelineResultDto {
        +LocalDateTime startedAt
        +LocalDateTime completedAt
        +long durationMs
        +int erpRecordsExtracted
        +int csvRecordsExtracted
        +int apiRecordsExtracted
        +int inserted
        +int updated
        +int skipped
        +int conflicts
        +int elasticsearchDocumentsIndexed
        +String status
        +String message
    }

    class ProductDocument {
        +String sku
        +String description
        +String productLine
        +String material
        +String gauge
        +String voltageRating
        +BigDecimal listPrice
        +BigDecimal distributorPrice
        +Integer totalQtyAvailable
        +LocalDateTime lastIndexed
    }

    %% ── Extractors ────────────────────────────────────────────────────────

    class ErpDatabaseExtractor {
        -JdbcTemplate legacyJdbcTemplate
        +extract() List~RawProductRecord~
    }

    class PricingCsvExtractor {
        +extract() List~RawProductRecord~
        -parseRow(String[]) RawProductRecord
        -parseBigDecimal(String) BigDecimal
    }

    class WarehouseApiExtractor {
        -String warehouseApiUrl
        -RestTemplate restTemplate
        +extract(List~String~) List~RawProductRecord~
        -extractFromApi() List~RawProductRecord~
        -generateSyntheticInventory(List~String~) List~RawProductRecord~
    }

    %% ── Transform ─────────────────────────────────────────────────────────

    class ProductHarmonizer {
        +toProduct(RawProductRecord) Product
        +toInventory(RawProductRecord, Product) Inventory
        +toListPricing(RawProductRecord, Product) Pricing
        +toDistributorPricing(RawProductRecord, Product) Pricing
        +normalizeSku(String) String
        +normalizeGauge(String) String
        +normalizeUom(String) String
        +resolveMaterial(RawProductRecord) String
    }

    class DeduplicationService {
        -ProductRepository productRepository
        -InventoryRepository inventoryRepository
        -PricingRepository pricingRepository
        -ReconciliationLogRepository reconciliationLogRepository
        -ProductHarmonizer harmonizer
        +reconcileProducts(List~RawProductRecord~) ReconciliationResult
        +reconcileInventory(List~RawProductRecord~) void
        +reconcilePricing(List~RawProductRecord~) void
        -computeDiff(Product, RawProductRecord) List~String~
        -logReconciliation(String, String, String, String) void
    }

    %% ── Pipeline and Search ───────────────────────────────────────────────

    class EtlPipelineRunner {
        -ErpDatabaseExtractor erpExtractor
        -PricingCsvExtractor csvExtractor
        -WarehouseApiExtractor apiExtractor
        -DeduplicationService deduplicationService
        -ProductIndexingService indexingService
        -AtomicBoolean running
        +scheduledRun() void
        +run() PipelineResultDto
    }

    class ProductIndexingService {
        -ProductRepository productRepository
        -ProductSearchRepository searchRepository
        +reindex() int
        -toDocument(Product) ProductDocument
    }

    %% ── Controllers ───────────────────────────────────────────────────────

    class ProductController {
        -ProductRepository productRepository
        +getProducts(Pageable) Page~ProductResponseDto~
        +getProduct(String) ResponseEntity~ProductResponseDto~
        +getSummary() List~ProductSummaryProjection~
    }

    class InventoryController {
        -InventoryRepository inventoryRepository
        +getInventory(String, Pageable) Page~Inventory~
        +getWarehouses() List~String~
    }

    class PricingController {
        -PricingRepository pricingRepository
        +getPricing(String, Pageable) Page~Pricing~
    }

    class SearchController {
        -ProductSearchRepository searchRepository
        +search(String, Pageable) Page~ProductDocument~
        +byProductLine(String, Pageable) Page~ProductDocument~
        +byMaterial(String, Pageable) Page~ProductDocument~
    }

    class PipelineController {
        -EtlPipelineRunner pipelineRunner
        +runPipeline() ResponseEntity~PipelineResultDto~
    }

    %% ── Relationships ─────────────────────────────────────────────────────

    Product "1" *-- "many" Inventory : contains
    Product "1" *-- "many" Pricing : contains

    ErpDatabaseExtractor ..> RawProductRecord : produces
    PricingCsvExtractor ..> RawProductRecord : produces
    WarehouseApiExtractor ..> RawProductRecord : produces

    ProductHarmonizer ..> RawProductRecord : consumes
    ProductHarmonizer ..> Product : produces
    ProductHarmonizer ..> Inventory : produces
    ProductHarmonizer ..> Pricing : produces

    DeduplicationService --> ProductHarmonizer : uses
    DeduplicationService ..> ReconciliationLog : writes
    DeduplicationService ..> Product : reconciles

    EtlPipelineRunner --> ErpDatabaseExtractor : orchestrates
    EtlPipelineRunner --> PricingCsvExtractor : orchestrates
    EtlPipelineRunner --> WarehouseApiExtractor : orchestrates
    EtlPipelineRunner --> DeduplicationService : orchestrates
    EtlPipelineRunner --> ProductIndexingService : orchestrates
    EtlPipelineRunner ..> PipelineResultDto : returns

    ProductIndexingService ..> ProductDocument : produces

    ProductController ..> ProductResponseDto : returns
    PipelineController --> EtlPipelineRunner : triggers

    ProductResponseDto ..> Product : maps from
```

---

## API Routes

| Method | Endpoint | Description | Data Source | Cached |
|---|---|---|---|---|
| `GET` | `/api/products` | Paginated product catalog. Filterable by `productLine`, `material`, `source` | MySQL | No |
| `GET` | `/api/products/{sku}` | Single product with full inventory and pricing detail (JOIN FETCH) | MySQL | No |
| `GET` | `/api/products/summary` | Aggregate product counts grouped by source and product line | MySQL | 10 min |
| `GET` | `/api/inventory` | Inventory levels across all warehouses. Filterable by `warehouse` | MySQL | No |
| `GET` | `/api/inventory/warehouses` | List of distinct warehouse codes (CHI, ATL, DAL, DEN, LAX) | MySQL | No |
| `GET` | `/api/pricing` | Pricing records. Filterable by `type` (LIST, DISTRIBUTOR) | MySQL | No |
| `GET` | `/api/search?q=` | Full-text fuzzy search across description, gauge, product line, material | Elasticsearch | No |
| `GET` | `/api/search/by-line` | Browse by exact product line | Elasticsearch | No |
| `GET` | `/api/search/by-material` | Browse by material (Aluminum, Copper) | Elasticsearch | No |
| `POST` | `/api/pipeline/run` | Manually trigger a full ETL pipeline run | - | Evicts all |
| `GET` | `/actuator/health` | Application and dependency health check | - | No |
| `GET` | `/actuator/metrics` | JVM, HikariCP pool, and cache metrics | - | No |
| `GET` | `/actuator/caches` | Inspect active Caffeine cache entries | - | No |

---

## Sample API Responses

**GET /api/products/AL-XHHW2-20**
```json
{
  "id": 6,
  "sku": "AL-XHHW2-20",
  "description": "2/0 AWG Aluminum XHHW-2 Stranded 600V",
  "productLine": "Aluminum XHHW",
  "material": "Aluminum",
  "gauge": "2/0",
  "voltageRating": "600V",
  "uom": "FT",
  "source": "ERP",
  "updatedAt": "2025-01-15T02:00:43",
  "stockByWarehouse": {
    "ATL": 38142,
    "CHI": 61204,
    "DAL": 44871,
    "DEN": 29503,
    "LAX": 51930
  },
  "totalStock": 225650,
  "listPrice": 0.9870,
  "distributorPrice": 0.7896
}
```

**GET /api/search?q=aluminum+xhhw+2/0**
```json
{
  "content": [
    {
      "sku": "AL-XHHW2-20",
      "description": "2/0 AWG Aluminum XHHW-2 Stranded 600V",
      "productLine": "Aluminum XHHW",
      "material": "Aluminum",
      "gauge": "2/0",
      "voltageRating": "600V",
      "listPrice": 0.9870,
      "distributorPrice": 0.7896,
      "totalQtyAvailable": 225650,
      "lastIndexed": "2025-01-15T02:01:12"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 25
}
```

**POST /api/pipeline/run**
```json
{
  "startedAt": "2025-01-15T02:00:00",
  "completedAt": "2025-01-15T02:00:43",
  "durationMs": 43210,
  "erpRecordsExtracted": 29,
  "csvRecordsExtracted": 29,
  "apiRecordsExtracted": 145,
  "totalExtracted": 203,
  "inserted": 0,
  "updated": 3,
  "skipped": 26,
  "conflicts": 0,
  "elasticsearchDocumentsIndexed": 29,
  "status": "SUCCESS",
  "message": "Pipeline completed successfully"
}
```


## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `MYSQL_HOST` | `localhost` | MySQL server hostname |
| `MYSQL_DB` | `kingwire_db` | Target database name |
| `MYSQL_USER` | `root` | MySQL username |
| `MYSQL_PASSWORD` | `secret` | MySQL password |
| `ES_URI` | `http://localhost:9200` | Elasticsearch connection URI |
| `WAREHOUSE_API_URL` | _(empty)_ | Warehouse REST API base URL - synthetic data generated if blank |

All variables are passed via `docker-compose.yml` in local development and should be sourced from AWS Secrets Manager in production.


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

## Java Spring vs Python

### Type Safety at the Data Boundary

The harmonizer is doing the most dangerous work in the system - taking loosely structured data from three sources with inconsistent field types and coercing it into a strict schema. Java's compile-time type system catches mismatches before runtime. A Python dict can silently carry the wrong type all the way to the database insert. A Java `RawProductRecord` with a `BigDecimal unitPrice` field will not compile if you try to assign a string to it.

### Spring Data JPA vs Python ORMs

The JOIN FETCH queries, `Pageable` pagination, and projection interfaces (`ProductSummaryProjection`) are built into Spring Data JPA. The equivalent in Python's SQLAlchemy requires significantly more manual wiring, and Django ORM's pagination is tied to the Django request cycle - awkward to use in a standalone pipeline context.

### HikariCP

HikariCP is the fastest JDBC connection pool available and is Spring Boot's default. Python's database connection pooling (SQLAlchemy's pool, psycopg2's connection pool) is functional but HikariCP has had years of production hardening specifically for high-throughput JVM workloads. For a system serving concurrent API requests against MySQL, that matters.

### Spring Boot Auto-Configuration

The entire application - two datasources, Flyway migrations, Elasticsearch client, Caffeine cache, scheduled cron, actuator health endpoints - starts up from `application.yml` with virtually no boilerplate. Replicating the same in Python requires manually wiring together Flask/FastAPI, SQLAlchemy, Celery or APScheduler, and separate health check logic. Each of those has its own configuration model and failure mode.

### Dependency Injection

The pipeline architecture relies heavily on DI - `EtlPipelineRunner` receives its extractors, harmonizer, dedup service, and indexing service as constructor-injected beans. Spring's container manages their lifecycle. In Python, this pattern is achievable with frameworks like `dependency-injector`, but it is a third-party add-on with limited adoption compared to Spring's mature, deeply integrated DI container.

### Enterprise Credibility

KingWire is an industrial manufacturer with an existing ERP, on-premise systems, and a Microsoft Azure environment. That stack implies an IT organization that expects Java or .NET for backend services - not because Python is wrong, but because Java's tooling around monitoring (Actuator, JMX), deployment (JAR packaging, Docker), and long-term maintainability aligns with what enterprise operations teams already know how to support.

### Where Python Would Actually Win

To be direct about the tradeoffs: Python would be faster to prototype, easier to write quick data transformations with pandas, and far simpler if this were a pure ETL script rather than a full application. If KingWire had asked for a one-off migration script, Python would be the better choice. The reason Java wins here is that this is a running service - it has an API layer, a cache, a scheduler, connection pooling, and health checks. That is Spring's native territory.

---

## Performance Design Decisions

### HikariCP Pool Tuning (DataSourceConfig.java)
```
maximumPoolSize = 10     Handles concurrent API requests without over-provisioning
minimumIdle = 2          Keeps warm connections available without waste
connectionTimeout = 30s  Fail fast on pool exhaustion - surface problems quickly
idleTimeout = 10min      Reclaim idle connections in low-traffic windows
maxLifetime = 30min      Rotate connections before MySQL's wait_timeout (8h)
```

### Caching (CacheConfig.java)
- `productSummary` (10-min TTL): GROUP BY aggregate across full catalog - expensive, not real-time critical
- `productDetail` (5-min TTL): JOIN-loaded product with inventory and pricing
- Both caches evicted via `@CacheEvict` on pipeline completion

### Database Indexes (V1__create_schema.sql)
All filter columns are indexed: `product_line`, `source`, `material`, `warehouse_code`, `price_type`, `sku` (unique)

### Pagination
All list endpoints use Spring `Pageable` - no unbounded result sets returned

### Elasticsearch Full-Text Search
Multi-field fuzzy search with description field boosted 2x. Handles spec abbreviation
variants (XHHW/XHWW, "2/0 AWG"/"2/0") that would require complex LIKE chains in MySQL.

---

## Production Next Steps

The architecture as built is a fully functional, well-structured data pipeline and API layer. The following represents the optimal path for scaling it to a production-grade enterprise system.

**Kafka + AWS: S3 as the event source + MSK as the backbone + RDS + OpenSearch**

The pricing team uploads to S3, Lambda publishes the event to MSK, the Spring consumer harmonizes and writes to RDS, a separate ES consumer updates OpenSearch. This architecture fully eliminates the nightly cron, makes data updates near real-time, and lays the foundation for the Power BI reporting layer described in the job description - all without changing the core harmonization and deduplication logic already built.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#162B4A", "primaryTextColor": "#FFFFFF", "primaryBorderColor": "#2A5298", "lineColor": "#3D6BB5", "secondaryColor": "#0C1929", "tertiaryColor": "#0C1929", "clusterBkg": "#0C1929", "clusterBorder": "#2A5298", "titleColor": "#FFFFFF", "edgeLabelBackground": "#162B4A", "nodeTextColor": "#FFFFFF"}}}%%
flowchart TD
    subgraph SOURCES["Source Systems"]
        ERP["ERP System\nPublishes change events"]
        PT["Pricing Team\nUploads CSV to S3"]
        WMS["WMS\nPublishes inventory events"]
    end

    subgraph AWS_ENTRY["AWS Entry Points"]
        MSK1["MSK Topic\nerp-product-events"]
        S3["AWS S3\npricing-exports bucket"]
        LAMBDA["AWS Lambda\nS3 event trigger"]
        MSK3["MSK Topic\ninventory-events"]
    end

    subgraph BACKBONE["MSK Backbone - Apache Kafka"]
        KAFKA["Durable, replayable event stream\nDecouples producers from consumers"]
    end

    subgraph CONSUMERS["Spring Consumers"]
        SC["Spring Consumer\nHarmonize and write to RDS"]
        EC["ES Consumer\nUpdate OpenSearch index"]
    end

    subgraph STORES["Data Stores"]
        RDS["AWS RDS\nMySQL - source of truth"]
        OS["AWS OpenSearch\nFull-text search index"]
    end

    PBI["Power BI reporting layer"]
    CW["CloudWatch\nMonitor all layers"]

    classDef source fill:#162B4A,stroke:#2A5298,color:#FFFFFF
    classDef entry fill:#1A3560,stroke:#2A5298,color:#FFFFFF
    classDef kafka fill:#0C1929,stroke:#3D6BB5,color:#FFFFFF
    classDef consumer fill:#162B4A,stroke:#2A5298,color:#FFFFFF
    classDef store fill:#0C1929,stroke:#2A5298,color:#FFFFFF
    classDef reporting fill:#1A3560,stroke:#2A5298,color:#FFFFFF
    classDef monitor fill:#0C1929,stroke:#2A5298,color:#FFFFFF

    class ERP,PT,WMS source
    class MSK1,S3,LAMBDA,MSK3 entry
    class KAFKA kafka
    class SC,EC consumer
    class RDS,OS store
    class PBI reporting
    class CW monitor

    ERP --> MSK1
    PT --> S3
    S3 --> LAMBDA
    WMS --> MSK3

    MSK1 --> KAFKA
    LAMBDA --> KAFKA
    MSK3 --> KAFKA

    KAFKA --> SC
    KAFKA --> EC

    SC --> RDS
    EC --> OS

    RDS -.->|product-harmonized topic| KAFKA

    RDS --> PBI
    OS --> PBI

    CW -.->|monitors| CONSUMERS
```

**Top layer** - Three source systems: ERP publishing directly, pricing team uploading to S3, WMS publishing inventory events.

**AWS entry layer** - S3 bucket catches the CSV upload, Lambda fires immediately on the S3 event, MSK topics receive events from ERP and WMS directly.

**MSK backbone** - All three sources converge here. This is the decoupling point - producers don't know about consumers, and each step can fail and retry independently.

**Consumer layer** - Two separate Spring consumers reading from the backbone: one harmonizes and writes to RDS, another updates OpenSearch. They're independent so an ES failure doesn't block the MySQL write.

**Feedback loop** - After RDS writes, a `product-harmonized` topic fires so any future consumers (Power BI, alerts, etc.) can subscribe without changing existing code.

**Bottom** - Both stores feed Power BI. CloudWatch monitors the consumer layer.

| Current | Production Replacement | Benefit |
|---|---|---|
| Docker MySQL | AWS RDS Multi-AZ | Automatic failover, automated backups, no manual patching |
| Local Elasticsearch | AWS OpenSearch Service | Managed cluster, snapshots, IAM access control |
| `pricing-export.csv` in resources | S3 bucket upload | Pricing team self-serves, no redeploy required |
| `@Scheduled` nightly cron | AWS Lambda + S3 event trigger | Pipeline fires the moment new data lands, no polling delay |
| Console/log monitoring | AWS CloudWatch | Pipeline result metrics, alerting on conflict spikes or failures |
| `application.yml` credentials | AWS Secrets Manager | Automatic credential rotation, no secrets in config files |
| Sequential MySQL write + ES sync | Kafka (MSK) decoupled consumers | Each step fails and retries independently, no ambiguous pipeline state |

---

## CI/CD Pipeline

```mermaid
%%{init: {"theme": "base", "themeVariables": {"primaryColor": "#162B4A", "primaryTextColor": "#FFFFFF", "primaryBorderColor": "#2A5298", "lineColor": "#3D6BB5", "secondaryColor": "#0C1929", "tertiaryColor": "#0C1929", "clusterBkg": "#0C1929", "clusterBorder": "#2A5298", "titleColor": "#FFFFFF", "edgeLabelBackground": "#162B4A", "nodeTextColor": "#FFFFFF"}}}%%
flowchart TD
    subgraph DEVELOPER["Developer"]
        PUSH["git push / PR merge"]
    end

    subgraph CI["GitHub Actions"]
        CO["Checkout\nactions/checkout"]
        TEST["Build and Test\nmvn verify"]
        DOCKER["Docker Build\nmulti-stage Dockerfile"]
        PUSH_IMG["Push Image\ntagged with commit SHA"]
        FAIL["Tests fail\nbranch blocked from merge"]

        CO --> TEST --> DOCKER --> PUSH_IMG
        TEST -.->|failure| FAIL
    end

    subgraph REGISTRY["Registry"]
        GHCR["GitHub Container Registry\nghcr.io/TannerAbraham/kingwire-pipeline-demo"]
    end

    subgraph RAILWAY["Railway"]
        ENV["Env Vars injected\nMYSQL_URL - MYSQL_USER\nMYSQL_PASSWORD - ES_URI"]
        DEPLOY["Railway Deploy\npulls new image"]
        SB["Spring Boot\nHikariCP + Flyway startup"]
        MYSQL["MySQL"]
        ES["Elasticsearch"]

        ENV -.->|injected| DEPLOY
        DEPLOY --> SB
        DEPLOY --> MYSQL
        DEPLOY --> ES
    end

    classDef trigger fill:#2A5298,stroke:#0C1929,color:#FFFFFF
    classDef step fill:#162B4A,stroke:#2A5298,color:#FFFFFF
    classDef registry fill:#0C1929,stroke:#2A5298,color:#FFFFFF
    classDef deploy fill:#1A3560,stroke:#2A5298,color:#FFFFFF
    classDef service fill:#0C1929,stroke:#2A5298,color:#FFFFFF
    classDef failure fill:#162B4A,stroke:#2A5298,color:#FFFFFF

    class PUSH trigger
    class CO,TEST,DOCKER,PUSH_IMG step
    class GHCR registry
    class ENV,DEPLOY deploy
    class SB,MYSQL,ES service
    class FAIL failure

    PUSH --> CO
    PUSH_IMG --> GHCR
    GHCR --> DEPLOY
```

The pipeline covers the full deployment lifecycle across four lanes.

**Trigger**
A `git push` to `main` - or a merged PR - fires the GitHub Actions workflow. Nothing deploys without passing through the pipeline first.

**CI (GitHub Actions)**
Three sequential steps: checkout - `mvn verify` (compiles, runs unit tests including `ProductHarmonizerTest` and `DeduplicationServiceTest`) - multi-stage Docker build using the existing `Dockerfile`. The Maven stage is the gate: a test failure blocks the image from being built and the PR cannot merge.

**Registry**
On success, the image is tagged with the commit SHA and pushed to GitHub Container Registry (`ghcr.io`). Using the SHA tag rather than `latest` means every deployed version is traceable back to an exact commit.

**Railway Deploy**
Railway pulls the new image, injects the environment variables (`MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, `ES_URI`) from its secrets panel, and starts the container. On startup, HikariCP initializes with `initializationFailTimeout=-1` and Flyway retries up to 10 times before the app is marked healthy.

**Failure Path**
If Maven or Docker fails, no image is pushed, Railway sees no new image, and the running deployment is untouched. The branch is blocked from merging until the build is green.

**What to Wire Up**
One GitHub Actions workflow file (`.github/workflows/deploy.yml`) with three jobs - `test`, `docker-build-push`, and a Railway deploy step using the `railwayapp/railway-github-action`.

---

## Commit History

```
31ba6bc feat: implement product catalog rest api with pagination and filtering
43b476e feat: implement product indexing service - mysql to elasticsearch sync
4f6b9cb feat: wire etl pipeline runner with @scheduled nightly cron
b05b4f3 feat: add deduplication service with insert/update/skip/conflict reconciliation
9374385 feat: add product harmonizer - normalize sku, gauge, uom, material inference
a5b6e6b feat: implement warehouse api extractor with synthetic fallback
2aa713a feat: implement pricing csv extractor with opencsv
4abfeaa feat: implement erp database extractor via jdbctemplate
25395d1 feat: add caffeine caching configuration
ce10f0e feat: configure hikaricp connection pool with explicit tuning
b3192f2 feat: add spring data jpa and elasticsearch repositories
08af7a2 feat: define elasticsearch product document mapping
215626d feat: define dto layer - raw record, api response, pipeline result
a68d593 feat: define jpa entities - product, inventory, pricing, reconciliation_log
ef2948c feat: pricing export csv with list and distributor prices
80fd29f feat: legacy erp h2 schema and seed data (29 representative sku records)
7c6b7e2 feat: flyway V1 migration - products, inventory, pricing, reconciliation_log
09c8046 init: scaffold spring boot project with maven and docker-compose
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
            ├── migration/V1__create_schema.sql    (Flyway - MySQL)
            └── legacy/
                ├── V1__legacy_erp_schema.sql      (H2 seed)
                └── V2__legacy_erp_seed.sql
```

---

## Author

**Tanner Abraham**
[GitHub](https://github.com/TannerAbraham)

---

## Thank You

Thank you to **Zach Haden** and **Ryan King** at KingWire for the time and opportunity to interview for this position.
