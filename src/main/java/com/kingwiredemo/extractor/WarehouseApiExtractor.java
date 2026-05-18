package com.kingwiredemo.extractor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kingwiredemo.model.dto.RawProductRecord;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Extracts real-time warehouse inventory levels from an external REST API.
 *
 * In production this would call a WMS (Warehouse Management System) or ERP
 * inventory endpoint. The URL is externalised to application.yml so it can
 * be swapped between environments without code changes.
 *
 * Resilience: if the warehouse API is unreachable (network error, timeout,
 * or not configured), the extractor falls back to generating synthetic
 * inventory data seeded from the ERP product list. This ensures the pipeline
 * completes with partial data rather than failing entirely — acceptable for
 * an async batch process where inventory will be refreshed on the next run.
 */
@Component
@Slf4j
public class WarehouseApiExtractor {

    private static final List<String> WAREHOUSES = Arrays.asList("CHI", "ATL", "DAL", "DEN", "LAX");

    @Value("${warehouse.api.url:}")
    private String warehouseApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<RawProductRecord> extract(List<String> knownSkus) {
        if (!warehouseApiUrl.isBlank()) {
            try {
                return extractFromApi();
            } catch (RestClientException e) {
                log.warn("[API] Warehouse API unreachable at {}: {}. Falling back to synthetic data.",
                        warehouseApiUrl, e.getMessage());
            }
        } else {
            log.info("[API] warehouse.api.url not configured — generating synthetic inventory data");
        }
        return generateSyntheticInventory(knownSkus);
    }

    private List<RawProductRecord> extractFromApi() {
        String url = warehouseApiUrl + "/api/v1/inventory";
        log.info("[API] Fetching inventory from {}", url);

        List<WarehouseInventoryResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<WarehouseInventoryResponse>>() {}
        ).getBody();

        if (response == null) return List.of();

        return response.stream()
                .map(r -> RawProductRecord.builder()
                        .sku(r.getSku())
                        .warehouseCode(r.getWarehouse())
                        .qtyOnHand(r.getQtyOnHand())
                        .qtyAvailable(r.getQtyAvailable())
                        .sourceSystem("API")
                        .build())
                .toList();
    }

    private List<RawProductRecord> generateSyntheticInventory(List<String> skus) {
        log.info("[API] Generating synthetic inventory for {} SKUs across {} warehouses",
                skus.size(), WAREHOUSES.size());
        List<RawProductRecord> records = new ArrayList<>();
        Random rng = new Random(42); // fixed seed for reproducibility

        for (String sku : skus) {
            for (String warehouse : WAREHOUSES) {
                int onHand    = rng.nextInt(100_000);
                int available = (int) (onHand * (0.8 + rng.nextDouble() * 0.15));
                records.add(RawProductRecord.builder()
                        .sku(sku)
                        .warehouseCode(warehouse)
                        .qtyOnHand(onHand)
                        .qtyAvailable(available)
                        .sourceSystem("API")
                        .build());
            }
        }
        return records;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WarehouseInventoryResponse {
        @JsonProperty("sku")         private String sku;
        @JsonProperty("warehouse")   private String warehouse;
        @JsonProperty("qty_on_hand") private Integer qtyOnHand;
        @JsonProperty("qty_available") private Integer qtyAvailable;
    }
}
