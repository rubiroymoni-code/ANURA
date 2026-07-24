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
