ALTER TABLE shopping_list_item ADD COLUMN required_quantity NUMERIC(12,3);
ALTER TABLE shopping_list_item ADD COLUMN pantry_used NUMERIC(12,3) NOT NULL DEFAULT 0;
UPDATE shopping_list_item SET required_quantity=quantity WHERE required_quantity IS NULL;

CREATE TABLE household_pantry_stock (
 household_id UUID NOT NULL REFERENCES household(id) ON DELETE CASCADE,
 ingredient_id UUID NOT NULL REFERENCES ingredient(id) ON DELETE CASCADE,
 unit VARCHAR(20) NOT NULL,
 quantity NUMERIC(12,3) NOT NULL DEFAULT 0,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(household_id,ingredient_id,unit),
 CONSTRAINT ck_pantry_quantity CHECK(quantity>=0)
);
