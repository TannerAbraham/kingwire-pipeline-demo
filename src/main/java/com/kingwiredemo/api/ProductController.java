package com.kingwiredemo.api;

import com.kingwiredemo.config.CacheConfig;
import com.kingwiredemo.model.dto.ProductResponseDto;
import com.kingwiredemo.model.entity.Product;
import com.kingwiredemo.repository.ProductRepository;
import com.kingwiredemo.repository.ProductSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product catalog REST API.
 *
 * Performance notes:
 *  - All list endpoints use Spring Pageable — no unbounded result sets
 *  - /summary uses @Cacheable with Caffeine TTL (see CacheConfig)
 *  - /products/{sku} uses a JOIN FETCH query to avoid N+1 on detail view
 *  - Filtering is pushed to the DB layer (indexed columns) not in-memory
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    /**
     * Paginated product catalog.
     * Optional filters: productLine, material, source.
     * All filter columns are indexed in the Flyway migration.
     */
    @GetMapping
    public Page<ProductResponseDto> getProducts(
            @RequestParam(required = false) String productLine,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String source,
            @PageableDefault(size = 25, sort = "sku") Pageable pageable) {

        Page<Product> products;

        if (productLine != null && material != null) {
            products = productRepository.findByProductLineAndMaterial(productLine, material, pageable);
        } else if (productLine != null) {
            products = productRepository.findByProductLine(productLine, pageable);
        } else if (material != null) {
            products = productRepository.findByMaterial(material, pageable);
        } else if (source != null) {
            products = productRepository.findBySource(source, pageable);
        } else {
            products = productRepository.findAll(pageable);
        }

        return products.map(ProductResponseDto::from);
    }

    /**
     * Single product with full inventory and pricing detail.
     * Uses JOIN FETCH to load relations in one query.
     */
    @GetMapping("/{sku}")
    public ResponseEntity<ProductResponseDto> getProduct(@PathVariable String sku) {
        return productRepository.findBySkuWithDetails(sku.toUpperCase())
                .map(ProductResponseDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Aggregate summary grouped by source system and product line.
     * Cached with a 10-minute TTL — expensive GROUP BY, doesn't need real-time freshness.
     * Cache is evicted after each pipeline run.
     */
    @GetMapping("/summary")
    @Cacheable(CacheConfig.CACHE_PRODUCT_SUMMARY)
    public List<ProductSummaryProjection> getSummary() {
        return productRepository.findSummaryGrouped();
    }
}
