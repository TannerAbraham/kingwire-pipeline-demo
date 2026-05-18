package com.kingwiredemo.extractor;

import com.kingwiredemo.model.dto.RawProductRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extracts product master data from the legacy ERP replicated database.
 *
 * In production this would connect to a read replica of the on-premise ERP
 * (Epicor, SAP, or equivalent) via ODBC/JDBC. Here it connects to an H2
 * in-memory database seeded with representative item master data.
 *
 * Uses raw JdbcTemplate (not JPA) because legacy ERP schemas rarely map
 * cleanly to JPA entities — column names and types are often ERP-vendor
 * specific. JdbcTemplate gives explicit, readable SQL control.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ErpDatabaseExtractor {

    private static final String EXTRACT_SQL =
            "SELECT item_no, item_desc, product_class, unit_of_measure, " +
            "       voltage_rating, gauge_size, material_type " +
            "FROM erp_item_master " +
            "WHERE inactive_flag = 'N'";

    @Qualifier("legacyJdbcTemplate")
    private final JdbcTemplate legacyJdbcTemplate;

    public List<RawProductRecord> extract() {
        log.info("[ERP] Starting extraction from legacy ERP item master...");

        List<RawProductRecord> records = legacyJdbcTemplate.query(EXTRACT_SQL, (rs, rowNum) ->
                RawProductRecord.builder()
                        .sku(rs.getString("item_no"))
                        .description(rs.getString("item_desc"))
                        .productLine(rs.getString("product_class"))
                        .uom(rs.getString("unit_of_measure"))
                        .voltageRating(rs.getString("voltage_rating"))
                        .gauge(rs.getString("gauge_size"))
                        .material(rs.getString("material_type"))
                        .sourceSystem("ERP")
                        .build()
        );

        log.info("[ERP] Extracted {} records from legacy ERP", records.size());
        return records;
    }
}
