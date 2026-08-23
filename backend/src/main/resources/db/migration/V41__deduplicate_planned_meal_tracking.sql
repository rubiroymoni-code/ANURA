-- Keep a single current record for each planned meal and day. This also repairs
-- installations created before the unique index was consistently enforced.
WITH duplicate_rows AS (
  SELECT id,
         row_number() OVER (
           PARTITION BY user_id, planned_meal_id, meal_date
           ORDER BY completed_at DESC NULLS LAST, updated_at DESC, created_at DESC, id DESC
         ) AS row_number
  FROM consumed_meal
  WHERE planned_meal_id IS NOT NULL
)
DELETE FROM consumed_meal
WHERE id IN (SELECT id FROM duplicate_rows WHERE row_number > 1);

DROP INDEX IF EXISTS uk_consumed_planned_meal;
CREATE UNIQUE INDEX uk_consumed_planned_meal
  ON consumed_meal(user_id, planned_meal_id, meal_date)
  WHERE planned_meal_id IS NOT NULL;
