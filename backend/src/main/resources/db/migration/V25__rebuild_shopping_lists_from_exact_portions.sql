-- Lists generated before exact per-user portions were used can contain ingredients
-- retained by reusable recipes from older plan versions. Keep pantry stock and force
-- every household to regenerate its list with the corrected calculation.
INSERT INTO household_pantry_stock(household_id,ingredient_id,unit,quantity)
SELECT s.household_id,i.ingredient_id,i.unit,SUM(i.pantry_used)
FROM shopping_list_item i
JOIN shopping_list s ON s.id=i.shopping_list_id
WHERE i.ingredient_id IS NOT NULL AND i.pantry_used>0
GROUP BY s.household_id,i.ingredient_id,i.unit
ON CONFLICT(household_id,ingredient_id,unit) DO UPDATE SET
 quantity=household_pantry_stock.quantity+EXCLUDED.quantity,
 updated_at=CURRENT_TIMESTAMP;

DELETE FROM shopping_list;
