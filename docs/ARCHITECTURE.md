# Arquitectura ANURA

## Componentes

- `frontend`: PWA React, TypeScript y Vite. Conserva la sesión en el dispositivo y consume la API REST.
- `backend`: API Spring Boot 3 sobre Java 21. Seguridad stateless mediante JWT.
- `PostgreSQL`: persistencia multiusuario. Flyway controla el esquema.

## Dominio inicial

Cada usuario mantiene registros privados de cinco tipos:

- `WORKOUT`: entrenamientos, duración y detalles de series.
- `MEAL`: comidas, calorías, macros o receta.
- `WEIGHT`: evolución del peso.
- `MEASUREMENT`: perímetros y otras medidas.
- `GOAL`: objetivos y porcentaje de progreso.

La columna `user_id` y las consultas con usuario autenticado garantizan el aislamiento de datos.

## API

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET|PATCH /api/v1/profile`
- `GET|POST /api/v1/entries`
- `GET /api/v1/entries/today`
- `PUT|DELETE /api/v1/entries/{id}`
- `GET /api/v1/health`

Swagger está disponible en `/swagger-ui.html`.

## Nutrición compartida

`Household` comparte exclusivamente recetas, planes nutricionales y compra. Los recursos físicos y deportivos conservan propietario individual. `RecipeIngredient` usa valores por 100 g/ml y `UserMealPortion` normaliza cualquier número de miembros. Los CSV de dos personas son solo adaptadores de entrada.

Las confirmaciones nutricionales son transaccionales. Los planes nacen `DRAFT`; al activar una versión, la anterior pasa a `SUPERSEDED`. El histórico nunca se sobrescribe.

## Ejecución de entrenamientos

`WorkoutSession → ExercisePerformance → SetPerformance` modela la ejecución real. La sesión pertenece siempre al usuario autenticado y conserva versión del plan y objetivos planificados como snapshot. PostgreSQL impide más de una sesión activa por usuario. `WorkoutPersonalRecord` almacena hitos con referencia a la serie fuente; el resto de métricas se deriva.

La PWA mantiene una cola específica en IndexedDB. `WorkoutSyncOperation` aporta idempotencia por usuario y `operationId`; los conflictos son explícitos. Véanse [TRAINING.md](TRAINING.md) y [OFFLINE.md](OFFLINE.md).

## Evolución corporal

`BodyCheckin` es el agregado semanal de peso y perímetros. `ProgressPhoto` solo conserva referencias HTTPS y depende de `ProgressPhotoStorage`; no persiste binarios. El módulo convive con `tracker_entry`, que permanece como histórico legacy. Véase [BODY_PROGRESS.md](BODY_PROGRESS.md).
