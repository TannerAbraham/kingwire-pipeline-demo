CREATE TABLE erp_item_master (
    item_no         VARCHAR(50)  NOT NULL PRIMARY KEY,
    item_desc       VARCHAR(255),
    product_class   VARCHAR(100),
    unit_of_measure VARCHAR(20),
    voltage_rating  VARCHAR(20),
    gauge_size      VARCHAR(30),
    material_type   VARCHAR(20),
    inactive_flag   CHAR(1) DEFAULT 'N',
    last_modified   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
