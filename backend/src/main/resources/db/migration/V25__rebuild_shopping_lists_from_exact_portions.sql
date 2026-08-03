-- Lists generated before exact per-user portions were used can contain ingredients
-- retained by reusable recipes from older plan versions. Keep pantry stock and force
-- every household to regenerate its list with the corrected calculation.
DELETE FROM shopping_list;
