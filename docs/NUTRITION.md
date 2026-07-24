# Nutrición compartida

Una unidad doméstica (`Household`) agrupa miembros `OWNER` y `MEMBER`. Solo recetas, menús y listas de compra son compartidos; entrenamiento, peso y medidas permanecen ligados al usuario.

El plato común se modela como `Recipe` + `RecipeIngredient`. Cada `PlannedMeal` tiene una fila `UserMealPortion` por persona con multiplicador y macros calculados. No existen columnas rígidas por miembro en base de datos.

Los ingredientes son la fuente nutricional por 100 g/ml. Los totales se calculan de manera determinista. Los valores CSV sirven para construir y validar el catálogo, no como recomendación clínica.

## Importaciones

- `dieta_plan_v1.csv`: plan individual.
- `dieta_compartida_plan_v1.csv`: plan doméstico; el adaptador transforma las dos columnas cómodas del contrato en filas `UserMealPortion`.
- `recetas_v1.csv`: catálogo reutilizable.

Flujo: upload → validación → preview → confirmación transaccional → plan `DRAFT` → activación. Una nueva activación marca el plan anterior `SUPERSEDED` y conserva su histórico.

## Compra

La lista suma ingredientes × porciones para una semana. Si fue modificada manualmente, regenerarla produce conflicto salvo confirmación explícita. Permite artículos manuales, marcar comprados y copiar pendientes como texto.

No es asesoramiento médico ni nutricional.
