package com.kingwiredemo.repository;

import com.kingwiredemo.model.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findByProductLine(String productLine, Pageable pageable);

    Page<Product> findBySource(String source, Pageable pageable);

    Page<Product> findByMaterial(String material, Pageable pageable);

    Page<Product> findByProductLineAndMaterial(String productLine, String material, Pageable pageable);

    /**
     * Eagerly fetch inventory and pricing in one query to avoid N+1 on detail endpoint.
     * Safe with Set collections — Hibernate can JOIN FETCH multiple Sets simultaneously
     * because they have no bag-ordering ambiguity.
     */
    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.inventories " +
            "LEFT JOIN FETCH p.pricings " +
            "WHERE p.sku = :sku")
    Optional<Product> findBySkuWithDetails(@Param("sku") String sku);

    /** Summary projection used by the cached aggregate endpoint */
    @Query("SELECT p.source AS source, p.productLine AS productLine, COUNT(p) AS count " +
            "FROM Product p GROUP BY p.source, p.productLine ORDER BY p.source, p.productLine")
    List<ProductSummaryProjection> findSummaryGrouped();

    /**
     * Used by ProductIndexingService — loads all products with their inventory collections.
     * Split into two queries (one for inventories, one for pricings) to avoid a cartesian
     * product in the SQL result set when both collections are large. Hibernate's first-level
     * cache merges both results into the same Product instances within the same session,
     * so after both queries run, every product has both collections fully populated.
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.inventories")
    List<Product> findAllWithInventories();

    /**
     * Second half of the split fetch — loads pricings into the already-resident Product
     * instances in the current Hibernate session. Return value is intentionally discarded
     * by the caller; the side effect of populating the pricings collection is what matters.
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.pricings")
    List<Product> findAllWithPricings();
}