# API de planificación

Todos los endpoints requieren JWT salvo lectura de esquema y plantilla.

- `GET /api/v1/import-schemas`
- `GET /api/v1/import-schemas/training-plan/v1`
- `GET /api/v1/import-schemas/training-plan/v1/template`
- `POST /api/v1/imports/training-plans/preview` multipart, campo `file`
- `GET /api/v1/imports/{id}`
- `GET /api/v1/imports/{id}/errors`
- `POST /api/v1/imports/{id}/confirm`
- `DELETE /api/v1/imports/{id}`
- `GET /api/v1/workout-plans`
- `GET /api/v1/workout-plans/active`
- `GET /api/v1/workout-plans/{id}`
- `GET /api/v1/workout-plans/{id}/days`
- `POST /api/v1/workout-plans/{id}/activate`
- `POST /api/v1/workout-plans/{id}/archive`

Los errores incluyen `status`, `code`, `message`, `correlationId` y violaciones de campo.

## Ejecución de entrenamientos

- `GET /api/v1/workouts/today`
- `GET /api/v1/workout-sessions/active`
- `GET|POST /api/v1/workout-sessions`
- `GET /api/v1/workout-sessions/{id}`
- `POST /api/v1/workout-sessions/{id}/pause|resume|complete|abandon`
- `POST /api/v1/workout-sessions/{id}/exercises`
- `POST /api/v1/workout-sessions/{id}/exercises/{exerciseId}/substitute|complete`
- `PATCH /api/v1/workout-sessions/{id}/exercises/{exerciseId}/pain`
- `POST|PATCH|DELETE /api/v1/workout-sessions/{id}/exercises/{exerciseId}/sets[/{setId}]`
- `GET /api/v1/exercises/{id}/history|last-performance`
- `GET /api/v1/workout-sessions/{id}/metrics`
- `GET /api/v1/training/summary`
- `POST /api/v1/workout-sessions/{id}/sync`

Todos requieren JWT. Los recursos se filtran por el usuario del contexto; una sesión ajena responde como inexistente.

## Nutrición compartida

- `GET /api/v1/nutrition/today`
- `POST /api/v1/nutrition/today/{plannedMealId}/complete`
- `GET|POST /api/v1/households`
- `GET /api/v1/households/{id}/members`
- `POST /api/v1/households/{id}/invitations`
- `POST /api/v1/households/invitations/accept`
- `GET /api/v1/nutrition/recipes`
- `GET /api/v1/nutrition/plans`
- `GET|PUT /api/v1/nutrition/targets`
- `GET /api/v1/nutrition/plans/{id}/week`
- `POST /api/v1/nutrition/plans/{id}/activate`
- `POST /api/v1/nutrition/plans/{id}/shopping-list`
- `GET /api/v1/nutrition/shopping-lists`
- `GET|POST /api/v1/nutrition/shopping-lists/{id}/items`
- `PATCH /api/v1/nutrition/shopping-items/{id}/toggle`
- `GET /api/v1/nutrition-import-schemas`
- `GET /api/v1/nutrition-import-schemas/{type}/template`
- `POST /api/v1/imports/nutrition/{type}/preview`
- `POST /api/v1/imports/nutrition/{id}/confirm`
