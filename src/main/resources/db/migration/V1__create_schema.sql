-- V1: KingWire Central Harmonized Data Schema

CREATE TABLE products (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku            VARCHAR(80)  NOT NULL UNIQUE,
    description    VARCHAR(255),
    product_line   VARCHAR(100),
    material       VARCHAR(50),
    gauge          VARCHAR(30),
    voltage_rating VARCHAR(20),
    uom            VARCHAR(20),
    source         VARCHAR(50),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_products_product_line (product_line),
    INDEX idx_products_source       (source),
    INDEX idx_products_material     (material),
    INDEX idx_products_updated_at   (updated_at)
);

CREATE TABLE inventory (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id     BIGINT NOT NULL,
    warehouse_code VARCHAR(10),
    qty_on_hand    INT,
    qty_available  INT,
    last_synced_at TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_inventory_product_id     (product_id),
    INDEX idx_inventory_warehouse_code (warehouse_code)
);

CREATE TABLE pricing (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT NOT NULL,
    price_type   VARCHAR(30),
    unit_price   DECIMAL(10,4),
    effective_dt DATE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_pricing_product_id (product_id),
    INDEX idx_pricing_price_type (price_type)
);

CREATE TABLE reconciliation_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku        VARCHAR(80),
    source     VARCHAR(50),
    action     VARCHAR(20),
    diff_notes TEXT,
    run_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recon_run_at (run_at),
    INDEX idx_recon_sku    (sku),
    INDEX idx_recon_action (action)
);
