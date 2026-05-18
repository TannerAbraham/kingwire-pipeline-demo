package com.kingwiredemo.search;

import com.kingwiredemo.model.entity.Inventory;
import com.kingwiredemo.model.entity.Pricing;
import com.kingwiredemo.model.entity.Product;
import com.kingwiredemo.model.search.ProductDocument;
import com.kingwiredemo.repository.ProductRepository;
import com.kingwiredemo.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Synchronizes the MySQL product catalog into the Elasticsearch search index.
 *
 * Called after each ETL pipeline run. The full index is rebuilt from MySQL
 * rather than incrementally patched, which:
 *  - Guarantees ES is always a consistent projection of verified MySQL data
 *  - Avoids dual-write consistency bugs where ES and MySQL diverge
 *  - Is acceptable because product catalog size (thousands of SKUs) is small
 *    relative to the cost of managing partial-update complexity
 *
 * For catalogs in the millions of documents, a delta-sync strategy
 * (index only changed products by updated_at timestamp) would be preferred.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductIndexingService {

    private final ProductRepository       productRepository;
    private final ProductSearchRepository searchRepository;

    /**
     * Full reindex: loads all products with relations from MySQL,
     * maps to ES documents, clears the old index, and bulk-inserts.
     *
     * @return number of documents indexed
     */
    public int reindex() {
        log.info("[ES] Starting full product reindex...");

        List<Product> products = productRepository.findAllWithDetails();

        List<ProductDocument> documents = products.stream()
                .map(this::toDocument)
                .toList();

        searchRepository.deleteAll();
        searchRepository.saveAll(documents);

        log.info("[ES] Reindex complete — {} documents indexed into 'products' index", documents.size());
        return documents.size();
    }

    private ProductDocument toDocument(Product product) {
        // Roll up total available qty across all warehouses
        int totalAvailable = product.getInventories().stream()
                .mapToInt(i -> i.getQtyAvailable() != null ? i.getQtyAvailable() : 0)
                .sum();

        BigDecimal listPrice = product.getPricings().stream()
                .filter(p -> "LIST".equals(p.getPriceType()))
                .map(Pricing::getUnitPrice)
                .findFirst().orElse(null);

        BigDecimal distPrice = product.getPricings().stream()
                .filter(p -> "DISTRIBUTOR".equals(p.getPriceType()))
                .map(Pricing::getUnitPrice)
                .findFirst().orElse(null);

        return ProductDocument.builder()
                .sku(product.getSku())
                .description(product.getDescription())
                .productLine(product.getProductLine())
                .material(product.getMaterial())
                .gauge(product.getGauge())
                .voltageRating(product.getVoltageRating())
                .uom(product.getUom())
                .source(product.getSource())
                .listPrice(listPrice)
                .distributorPrice(distPrice)
                .totalQtyAvailable(totalAvailable)
                .lastIndexed(LocalDateTime.now())
                .build();
    }
}
