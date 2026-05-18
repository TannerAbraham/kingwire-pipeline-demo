package com.kingwiredemo.model.dto;

import com.kingwiredemo.model.entity.Inventory;
import com.kingwiredemo.model.entity.Pricing;
import com.kingwiredemo.model.entity.Product;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API response DTO — flattens product + inventory + pricing into a
 * single document suitable for front-end consumption without exposing
 * internal JPA entity relationships.
 */
@Data
@Builder
public class ProductResponseDto {

    private Long id;
    private String sku;
    private String description;
    private String productLine;
    private String material;
    private String gauge;
    private String voltageRating;
    private String uom;
    private String source;
    private LocalDateTime updatedAt;

    private Map<String, Integer> stockByWarehouse;  // warehouse -> qty_available
    private int totalStock;

    private BigDecimal listPrice;
    private BigDecimal distributorPrice;

    public static ProductResponseDto from(Product product) {
        Map<String, Integer> stock = product.getInventories().stream()
                .collect(Collectors.toMap(
                        Inventory::getWarehouseCode,
                        i -> i.getQtyAvailable() != null ? i.getQtyAvailable() : 0
                ));

        int total = stock.values().stream().mapToInt(Integer::intValue).sum();

        BigDecimal list = product.getPricings().stream()
                .filter(p -> "LIST".equals(p.getPriceType()))
                .map(Pricing::getUnitPrice)
                .findFirst().orElse(null);

        BigDecimal dist = product.getPricings().stream()
                .filter(p -> "DISTRIBUTOR".equals(p.getPriceType()))
                .map(Pricing::getUnitPrice)
                .findFirst().orElse(null);

        return ProductResponseDto.builder()
                .id(product.getId())
                .sku(product.getSku())
                .description(product.getDescription())
                .productLine(product.getProductLine())
                .material(product.getMaterial())
                .gauge(product.getGauge())
                .voltageRating(product.getVoltageRating())
                .uom(product.getUom())
                .source(product.getSource())
                .updatedAt(product.getUpdatedAt())
                .stockByWarehouse(stock)
                .totalStock(total)
                .listPrice(list)
                .distributorPrice(dist)
                .build();
    }
}
