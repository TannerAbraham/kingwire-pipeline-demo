package com.kingwiredemo.api;

import com.kingwiredemo.model.search.ProductDocument;
import com.kingwiredemo.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * Full-text product search backed by Elasticsearch.
 *
 * Why ES rather than MySQL LIKE queries:
 *  - Distributors search by partial specs: "600V aluminum 2/0" — multi-field
 *  - Fuzziness handles typos and abbreviation variations (XHHW vs XHWW)
 *  - Sub-50ms response on large catalogs vs MySQL's sequential scan on TEXT
 *  - Faceted filtering (material, gauge) can be added without schema changes
 *
 * The /api/products endpoint serves structured CRUD from MySQL.
 * This endpoint serves unstructured search from the ES index.
 * The two endpoints serve different front-end use cases.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProductSearchRepository searchRepository;

    /**
     * Full-text search across description, productLine, material, and gauge.
     * Fuzzy matching enabled (AUTO) to handle spec abbreviation variations.
     *
     * Examples:
     *   ?q=aluminum xhhw 2/0      — natural language spec search
     *   ?q=copper soow             — product line search
     *   ?q=350 kcmil 600v          — gauge + voltage filter
     */
    @GetMapping
    public Page<ProductDocument> search(
            @RequestParam String q,
            @PageableDefault(size = 25) Pageable pageable) {
        return searchRepository.searchByText(q, pageable);
    }

    /** Browse by product line (exact match on Keyword field). */
    @GetMapping("/by-line")
    public Page<ProductDocument> byProductLine(
            @RequestParam String productLine,
            @PageableDefault(size = 25) Pageable pageable) {
        return searchRepository.findByProductLine(productLine, pageable);
    }

    /** Browse by material — "Aluminum" or "Copper". */
    @GetMapping("/by-material")
    public Page<ProductDocument> byMaterial(
            @RequestParam String material,
            @PageableDefault(size = 25) Pageable pageable) {
        return searchRepository.findByMaterial(material, pageable);
    }
}
