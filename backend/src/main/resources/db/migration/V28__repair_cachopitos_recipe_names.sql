-- Repair recipe labels imported before recipes were snapshotted per plan.
WITH expected(code,name) AS (VALUES
  ('D1M1','Bol rápido de avena y plátano'),
  ('D1M2','Arroz con pollo y brócoli'),
  ('D1M3','Tostadas con pavo y fruta'),
  ('D1M4','Merluza con patata y calabacín'),
  ('D2M1','Tostadas con huevo y manzana'),
  ('D2M2','Pasta con pollo, tomate y calabacín'),
  ('D2M3','Yogur con frutos rojos y nueces'),
  ('D2M4','Salmón con patata y brócoli'),
  ('D3M1','Bol de yogur, avena y manzana'),
  ('D3M2','Garbanzos con arroz, huevo y brócoli'),
  ('D3M3','Tostadas con pavo y plátano'),
  ('D3M4','Pollo con patata y calabacín'),
  ('D4M1','Tostadas de pavo, aguacate y fruta'),
  ('D4M2','Arroz con pollo y brócoli'),
  ('D4M3','Yogur con avena y frutos rojos'),
  ('D4M4','Ensalada de garbanzos y atún'),
  ('D5M1','Avena con yogur y plátano'),
  ('D5M2','Pollo con patata y brócoli'),
  ('D5M3','Tostadas con pavo y manzana'),
  ('D5M4','Merluza con arroz y calabacín'),
  ('D6M1','Yogur con avena, frutos rojos y nueces'),
  ('D6M2','Pasta con atún, tomate y brócoli'),
  ('D6M3','Tostadas con crema de cacahuete y plátano'),
  ('D6M4','Salmón con patata y brócoli'),
  ('D7M1','Huevos, tostadas y manzana'),
  ('D7M2','Garbanzos con pollo y calabacín'),
  ('D7M3','Yogur con manzana y nueces'),
  ('D7M4','Pollo con patata y brócoli')
)
UPDATE recipe r SET name=expected.name,updated_at=CURRENT_TIMESTAMP
FROM expected
WHERE (r.code=expected.code OR r.code LIKE expected.code||'__PLAN_%')
  AND EXISTS (
    SELECT 1 FROM planned_meal pm
    JOIN nutrition_plan_day d ON d.id=pm.nutrition_plan_day_id
    JOIN nutrition_plan p ON p.id=d.nutrition_plan_id
    WHERE pm.recipe_id=r.id
      AND p.external_id='CACHOPITOS-CANONICA-20260804'
      AND p.version=4
  );
