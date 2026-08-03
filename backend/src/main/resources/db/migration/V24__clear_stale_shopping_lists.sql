-- Limpieza solicitada: los artículos se eliminan por ON DELETE CASCADE.
-- Se conserva household_pantry_stock para no perder compras reales acumuladas.
DELETE FROM shopping_list;
