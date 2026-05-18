package com.kingwiredemo.api;

import com.kingwiredemo.model.entity.Inventory;
import com.kingwiredemo.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryRepository inventoryRepository;

    /** Inventory levels with optional warehouse filter. Paginated. */
    @GetMapping
    public Page<Inventory> getInventory(
            @RequestParam(required = false) String warehouse,
            @PageableDefault(size = 50) Pageable pageable) {

        return warehouse != null
                ? inventoryRepository.findByWarehouseCode(warehouse, pageable)
                : inventoryRepository.findAll(pageable);
    }

    /** Distinct warehouse codes — useful for populating filter dropdowns. */
    @GetMapping("/warehouses")
    public List<String> getWarehouses() {
        return inventoryRepository.findDistinctWarehouseCodes();
    }
}
