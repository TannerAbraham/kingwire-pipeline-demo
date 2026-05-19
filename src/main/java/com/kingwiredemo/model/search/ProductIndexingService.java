package com.kingwiredemo.model.search;

import com.kingwiredemo.model.entity.Pricing;
import com.kingwiredemo.model.entity.Product;
import com.kingwiredemo.repository.ProductRepository;
import com.kingwiredemo.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductIndexingService {

    private final ProductRepository       productRepository;
    private final ProductSearchRepository searchRepository;

    @Transactional(readOnly = true)
    public int reindex() {
        log.info("[ES] Starting full product reindex...");

        // Query 1: load all products with their inventory collections.
        // Hibernate caches these Product instances in the session's first-level cache.
        List<Product> products = productRepository.findAllWithInventories();

        // Query 2: load pricings into the same session. Hibernate merges the results
        // into the same Product instances already cached above — no second list needed.
        productRepository.findAllWithPricings();

        // At this point every Product in `products` has both collections populated.
        List<ProductDocument> documents = products.stream()
                .map(this::toDocument)
                .toList();

        searchRepository.deleteAll();
        searchRepository.saveAll(documents);

        log.info("[ES] Reindex complete — {} documents indexed into 'products' index", documents.size());
        return documents.size();
    }

    private ProductDocument toDocument(Product product) {
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