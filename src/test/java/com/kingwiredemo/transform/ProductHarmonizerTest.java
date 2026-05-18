package com.kingwiredemo.transform;

import com.kingwiredemo.model.dto.RawProductRecord;
import com.kingwiredemo.model.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductHarmonizer")
class ProductHarmonizerTest {

    private ProductHarmonizer harmonizer;

    @BeforeEach
    void setUp() {
        harmonizer = new ProductHarmonizer();
    }

    @Nested
    @DisplayName("SKU normalization")
    class SkuNormalization {

        @Test
        void uppercasesAndTrims() {
            assertThat(harmonizer.normalizeSku("  al-xhhw2-20  ")).isEqualTo("AL-XHHW2-20");
        }

        @Test
        void replacesSpacesWithHyphens() {
            assertThat(harmonizer.normalizeSku("AL XHHW2 20")).isEqualTo("AL-XHHW2-20");
        }

        @Test
        void returnsNullForNullInput() {
            assertThat(harmonizer.normalizeSku(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Gauge normalization")
    class GaugeNormalization {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "'2/0 AWG',    '2/0'",
            "'350MCM',     '350 kcmil'",
            "'500KCMIL',   '500 kcmil'",
            "'4 AWG',      '4'",
            "'12 AWG',     '12'",
            "'1/0 AWG',    '1/0'"
        })
        void normalizesGaugeVariants(String input, String expected) {
            assertThat(harmonizer.normalizeGauge(input)).isEqualTo(expected);
        }

        @Test
        void handlesNullGauge() {
            assertThat(harmonizer.normalizeGauge(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Material inference")
    class MaterialInference {

        @Test
        void resolvesExplicitAluminumAbbreviation() {
            RawProductRecord raw = RawProductRecord.builder()
                    .material("AL").description("").productLine("").build();
            assertThat(harmonizer.resolveMaterial(raw)).isEqualTo("Aluminum");
        }

        @Test
        void resolvesExplicitCopperAbbreviation() {
            RawProductRecord raw = RawProductRecord.builder()
                    .material("CU").description("").productLine("").build();
            assertThat(harmonizer.resolveMaterial(raw)).isEqualTo("Copper");
        }

        @Test
        void infersAluminumFromDescription() {
            RawProductRecord raw = RawProductRecord.builder()
                    .material(null)
                    .description("2/0 AWG Aluminum XHHW-2 Stranded")
                    .productLine("").build();
            assertThat(harmonizer.resolveMaterial(raw)).isEqualTo("Aluminum");
        }

        @Test
        void infersCopperFromProductLine() {
            RawProductRecord raw = RawProductRecord.builder()
                    .material(null)
                    .description("")
                    .productLine("Copper SOOW").build();
            assertThat(harmonizer.resolveMaterial(raw)).isEqualTo("Copper");
        }

        @Test
        void defaultsToUnknownWhenNotInferable() {
            RawProductRecord raw = RawProductRecord.builder()
                    .material(null).description("Extension Cord").productLine("General").build();
            assertThat(harmonizer.resolveMaterial(raw)).isEqualTo("Unknown");
        }
    }

    @Nested
    @DisplayName("UOM normalization")
    class UomNormalization {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "FEET, FT",
            "FOOT, FT",
            "FT, FT",
            "REEL, REEL",
            "RL, REEL",
            "CUT, CUT",
            "CT, CUT"
        })
        void normalizesUomVariants(String input, String expected) {
            assertThat(harmonizer.normalizeUom(input)).isEqualTo(expected);
        }

        @Test
        void defaultsToFtForNull() {
            assertThat(harmonizer.normalizeUom(null)).isEqualTo("FT");
        }
    }

    @Test
    @DisplayName("Full harmonization produces correct Product entity")
    void fullHarmonizationProducesCorrectProduct() {
        RawProductRecord raw = RawProductRecord.builder()
                .sku("  al-xhhw2-20  ")
                .description("  2/0 AWG  Aluminum  XHHW-2  ")
                .productLine("Aluminum XHHW")
                .material("AL")
                .gauge("2/0 AWG")
                .voltageRating("600v")
                .uom("FEET")
                .sourceSystem("ERP")
                .build();

        Product product = harmonizer.toProduct(raw);

        assertThat(product.getSku()).isEqualTo("AL-XHHW2-20");
        assertThat(product.getDescription()).isEqualTo("2/0 AWG Aluminum XHHW-2");
        assertThat(product.getMaterial()).isEqualTo("Aluminum");
        assertThat(product.getGauge()).isEqualTo("2/0");
        assertThat(product.getVoltageRating()).isEqualTo("600V");
        assertThat(product.getUom()).isEqualTo("FT");
        assertThat(product.getSource()).isEqualTo("ERP");
    }
}
