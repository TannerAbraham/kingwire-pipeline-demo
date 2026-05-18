package com.kingwiredemo.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "pricing", indexes = {
        @Index(name = "idx_pricing_product",    columnList = "product_id"),
        @Index(name = "idx_pricing_price_type", columnList = "price_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"product", "priceType", "effectiveDt"})
public class Pricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    @Column(name = "price_type", length = 30)
    private String priceType;        // LIST | DISTRIBUTOR | CONTRACT

    @Column(name = "unit_price", precision = 10, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "effective_dt")
    private LocalDate effectiveDt;
}
