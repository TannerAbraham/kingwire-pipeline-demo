package com.kingwiredemo.repository;

import com.kingwiredemo.model.search.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    Page<ProductDocument> findByProductLine(String productLine, Pageable pageable);

    Page<ProductDocument> findByMaterial(String material, Pageable pageable);

    /**
     * Multi-field full-text search with AUTO fuzziness.
     * Matches across description, productLine, gauge, and material fields.
     * Fuzziness handles common spec abbreviation variations (e.g. "XHHW" vs "XHWW" typo).
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", " +
           "\"fields\": [\"description^2\", \"productLine\", \"material\", \"gauge\"], " +
           "\"fuzziness\": \"AUTO\"}}")
    Page<ProductDocument> searchByText(String query, Pageable pageable);
}
