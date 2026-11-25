ALTER TABLE product_presentations
    ADD COLUMN price_tiers_json TEXT NOT NULL DEFAULT '[]';
