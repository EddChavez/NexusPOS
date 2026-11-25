CREATE TABLE price_lists (
    level_id INTEGER PRIMARY KEY CHECK (level_id BETWEEN 2 AND 5),
    name VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO price_lists (level_id, name) VALUES (2, 'Precio 2'), (3, 'Precio 3'), (4, 'Precio 4'), (5, 'Precio 5');

ALTER TABLE product_presentations ADD COLUMN price_list_prices TEXT NOT NULL DEFAULT '{}';
ALTER TABLE sale_lines ADD COLUMN price_list_level INTEGER;
