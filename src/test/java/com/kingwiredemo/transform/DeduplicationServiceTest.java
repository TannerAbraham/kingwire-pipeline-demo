package com.kingwiredemo.transform;

import com.kingwiredemo.model.dto.RawProductRecord;
import com.kingwiredemo.model.entity.Product;
import com.kingwiredemo.model.entity.ReconciliationLog;
import com.kingwiredemo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeduplicationService")
class DeduplicationServiceTest {

    @Mock private ProductRepository           productRepository;
    @Mock private InventoryRepository         inventoryRepository;
    @Mock private PricingRepository           pricingRepository;
    @Mock private ReconciliationLogRepository reconciliationLogRepository;

    private ProductHarmonizer    harmonizer;
    private DeduplicationService service;

    @BeforeEach
    void setUp() {
        harmonizer = new ProductHarmonizer();
        service = new DeduplicationService(
                productRepository, inventoryRepository,
                pricingRepository, reconciliationLogRepository, harmonizer);
    }

    @Test
    @DisplayName("Inserts new product when SKU not found")
    void insertsNewProduct() {
        RawProductRecord raw = buildRaw("AL-XHHW2-20", "ERP");
        when(productRepository.findBySku("AL-XHHW2-20")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DeduplicationService.ReconciliationResult result = service.reconcileProducts(List.of(raw));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();

        verify(productRepository).save(any(Product.class));

        ArgumentCaptor<ReconciliationLog> logCaptor = ArgumentCaptor.forClass(ReconciliationLog.class);
        verify(reconciliationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("INSERT");
    }

    @Test
    @DisplayName("Skips record when product unchanged")
    void skipsUnchangedProduct() {
        RawProductRecord raw = buildRaw("AL-XHHW2-20", "ERP");

        Product existing = Product.builder()
                .sku("AL-XHHW2-20")
                .description("2/0 AWG Aluminum XHHW-2 Stranded 600V")
                .productLine("Aluminum XHHW")
                .material("Aluminum")
                .gauge("2/0")
                .voltageRating("600V")
                .uom("FT")
                .source("ERP")
                .build();

        when(productRepository.findBySku("AL-XHHW2-20")).thenReturn(Optional.of(existing));

        DeduplicationService.ReconciliationResult result = service.reconcileProducts(List.of(raw));

        assertThat(result.skipped()).isEqualTo(1);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Logs CONFLICT for null SKU record")
    void logsConflictForNullSku() {
        RawProductRecord raw = RawProductRecord.builder().sku(null).sourceSystem("CSV").build();

        DeduplicationService.ReconciliationResult result = service.reconcileProducts(List.of(raw));

        assertThat(result.conflicts()).isEqualTo(1);
        ArgumentCaptor<ReconciliationLog> logCaptor = ArgumentCaptor.forClass(ReconciliationLog.class);
        verify(reconciliationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("CONFLICT");
    }

    private RawProductRecord buildRaw(String sku, String source) {
        return RawProductRecord.builder()
                .sku(sku)
                .description("2/0 AWG Aluminum XHHW-2 Stranded 600V")
                .productLine("Aluminum XHHW")
                .material("AL")
                .gauge("2/0 AWG")
                .voltageRating("600V")
                .uom("FEET")
                .sourceSystem(source)
                .build();
    }
}
