package com.kingwiredemo.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory", indexes = {
        @Index(name = "idx_inventory_warehouse", columnList = "warehouse_code"),
        @Index(name = "idx_inventory_product",   columnList = "product_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"product", "warehouseCode"})
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    private Product product;

    @Column(name = "warehouse_code", length = 10)
    private String warehouseCode;     // CHI | ATL | DAL | DEN | LAX

    @Column(name = "qty_on_hand")
    private Integer qtyOnHand;

    @Column(name = "qty_available")
    private Integer qtyAvailable;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
