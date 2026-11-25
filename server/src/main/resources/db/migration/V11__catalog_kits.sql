CREATE TABLE product_kit_components (
    kit_product_id VARCHAR(64) NOT NULL REFERENCES products(id),
    component_product_id VARCHAR(64) NOT NULL REFERENCES products(id),
    quantity_millis BIGINT NOT NULL CHECK (quantity_millis > 0),
    PRIMARY KEY (kit_product_id, component_product_id),
    CHECK (kit_product_id <> component_product_id)
);

CREATE INDEX idx_kit_component_product ON product_kit_components(component_product_id);

ALTER TABLE sale_lines ADD COLUMN kit_stock_json TEXT;

CREATE INDEX idx_stock_movements_reference_type ON stock_movements(reference, type);
