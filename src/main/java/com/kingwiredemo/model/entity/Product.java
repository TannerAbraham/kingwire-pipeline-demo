package com.kingwiredemo.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_product_line", columnList = "product_line"),
        @Index(name = "idx_products_source",       columnList = "source"),
        @Index(name = "idx_products_material",     columnList = "material")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "sku")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(length = 255)
    private String description;

    @Column(name = "product_line", length = 100)
    private String productLine;

    @Column(length = 50)
    private String material;

    @Column(length = 30)
    private String gauge;

    @Column(name = "voltage_rating", length = 20)
    private String voltageRating;

    @Column(length = 20)
    private String uom;

    @Column(length = 50)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Changed from List to Set — fixes MultipleBagFetchException when
    // JOIN FETCHing two collections in the same JPQL query.
    // Hibernate can simultaneously fetch multiple Set associations but
    // not multiple List (bag) associations.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Inventory> inventories = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Pricing> pricings = new HashSet<>();
}