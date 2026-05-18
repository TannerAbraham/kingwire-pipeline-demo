package com.kingwiredemo.model.search;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Elasticsearch index document — a flattened, search-optimized projection
 * of the MySQL product + inventory + pricing data.
 *
 * Responsibility split:
 *   MySQL  = source of truth (transactional writes, pricing, inventory)
 *   ES     = read-optimized search index (full-text, faceted, fuzzy)
 *
 * The document is always derived from MySQL — never written directly.
 * This avoids dual-write consistency issues. ES is repopulated after
 * each ETL pipeline run via ProductIndexingService.
 *
 * Index settings: 1 shard, 0 replicas — appropriate for a single-node
 * development/demo environment. Increase replicas before production.
 */
@Document(indexName = "products")
@Setting(shards = 1, replicas = 0)
@Data
@Builder
public class ProductDocument {

    @Id
    private String sku;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String productLine;

    @Field(type = FieldType.Keyword)
    private String material;

    @Field(type = FieldType.Keyword)
    private String gauge;

    @Field(type = FieldType.Keyword)
    private String voltageRating;

    @Field(type = FieldType.Keyword)
    private String uom;

    @Field(type = FieldType.Keyword)
    private String source;

    @Field(type = FieldType.Double)
    private BigDecimal listPrice;

    @Field(type = FieldType.Double)
    private BigDecimal distributorPrice;

    @Field(type = FieldType.Integer)
    private Integer totalQtyAvailable;    // rolled up across all warehouses

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime lastIndexed;
}
