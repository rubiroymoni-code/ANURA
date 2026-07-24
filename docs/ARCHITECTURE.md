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
