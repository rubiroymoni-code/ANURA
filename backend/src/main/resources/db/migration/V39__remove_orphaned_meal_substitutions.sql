-- Broken legacy substitutions have no planned meal to replace. Normal custom meals
-- use COMPLETED, so this deliberately leaves every valid historical meal untouched.
DELETE FROM consumed_meal
WHERE status = 'SUBSTITUTED'
  AND planned_meal_id IS NULL;
