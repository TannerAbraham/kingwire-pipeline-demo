package com.kingwiredemo.repository;

import com.kingwiredemo.model.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Page<Inventory> findByWarehouseCode(String warehouseCode, Pageable pageable);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.warehouseCode = :warehouse")
    List<Inventory> findByWarehouseCodeWithProduct(String warehouse);

    Optional<Inventory> findByProductIdAndWarehouseCode(Long productId, String warehouseCode);

    @Query("SELECT DISTINCT i.warehouseCode FROM Inventory i ORDER BY i.warehouseCode")
    List<String> findDistinctWarehouseCodes();
}
