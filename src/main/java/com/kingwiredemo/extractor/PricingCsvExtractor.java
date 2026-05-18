package com.kingwiredemo.extractor;

import com.kingwiredemo.model.dto.RawProductRecord;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts pricing data from a flat CSV file.
 *
 * This simulates the common enterprise pattern where a pricing team exports
 * a weekly Excel/CSV pricing sheet from an ERP or pricing system. The file
 * is typically dropped to a shared network location or SFTP folder on a
 * schedule and picked up by this extractor.
 *
 * The extractor is deliberately lenient — rows with missing or malformed
 * prices are skipped with a warning log rather than failing the pipeline,
 * because partial pricing updates are preferable to no update at all.
 */
@Component
@Slf4j
public class PricingCsvExtractor {

    private static final String CSV_PATH = "data/pricing-export.csv";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<RawProductRecord> extract() {
        log.info("[CSV] Starting extraction from pricing flat file: {}", CSV_PATH);
        List<RawProductRecord> records = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new ClassPathResource(CSV_PATH).getInputStream()))) {

            List<String[]> rows = reader.readAll();
            // Skip header row
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                try {
                    records.add(parseRow(row));
                } catch (Exception e) {
                    log.warn("[CSV] Skipping malformed row {}: {} — {}", i + 1, String.join(",", row), e.getMessage());
                }
            }
        } catch (IOException | CsvException e) {
            log.error("[CSV] Failed to read pricing CSV: {}", e.getMessage());
        }

        log.info("[CSV] Extracted {} pricing records from flat file", records.size());
        return records;
    }

    private RawProductRecord parseRow(String[] row) {
        // columns: sku, description, product_line, list_price, distributor_price, effective_date, uom
        return RawProductRecord.builder()
                .sku(row[0].trim())
                .description(row[1].trim())
                .productLine(row[2].trim())
                .listPrice(parseBigDecimal(row[3]))
                .distributorPrice(parseBigDecimal(row[4]))
                .effectiveDate(LocalDate.parse(row[5].trim(), DATE_FMT))
                .uom(row[6].trim())
                .sourceSystem("CSV")
                .build();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        return new BigDecimal(value.trim().replace("$", "").replace(",", ""));
    }
}
