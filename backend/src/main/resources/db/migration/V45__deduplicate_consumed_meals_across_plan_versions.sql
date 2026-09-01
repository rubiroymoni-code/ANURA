-- A new plan version creates new planned_meal ids. Keep only the most recent
-- tracking record for the same user, date and logical planned meal.
WITH version_duplicates AS (
  SELECT cm.id,
         row_number() OVER (
           PARTITION BY cm.user_id,cm.meal_date,pm.meal_order
           ORDER BY cm.completed_at DESC NULLS LAST,cm.updated_at DESC,cm.created_at DESC,cm.id DESC
         ) row_number
    FROM consumed_meal cm
    JOIN planned_meal pm ON pm.id=cm.planned_meal_id
)
DELETE FROM consumed_meal
 WHERE id IN (SELECT id FROM version_duplicates WHERE row_number>1);
