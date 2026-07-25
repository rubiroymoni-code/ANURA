# Despliegue en Railway

Crear un proyecto con PostgreSQL y dos servicios desde este repositorio.

## Backend

- Root Directory: `/backend`
- Config file: `/backend/railway.toml`
- Variables:
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
  - `SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}`
  - `SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}`
  - `JWT_SECRET`: secreto aleatorio de 32 caracteres o más
  - `APP_CORS_ALLOWED_ORIGINS`: dominio público del frontend, sin barra final
  - `IMPORT_MAX_FILE_SIZE=1048576`
  - `IMPORT_MAX_ROWS=2000`
  - `IMPORT_JOB_TTL_HOURS=24`
  - `WORKOUT_MAX_ACTIVE_SESSIONS=1`
  - `WORKOUT_COMPLETED_EDIT_WINDOW_MINUTES=15`
  - `WORKOUT_SYNC_MAX_OPERATIONS=100`
  - `WORKOUT_OFFLINE_OPERATION_RETENTION_DAYS=30`
  - `WORKOUT_ESTIMATED_1RM_MAX_REPS=12`

Flyway crea y actualiza las tablas durante el arranque.

## Frontend

- Root Directory: `/frontend`
- Config file: `/frontend/railway.toml`
- Build variable: `VITE_API_URL=https://DOMINIO-BACKEND/api/v1`

`VITE_API_URL` es una variable de compilación. Tras cambiarla hay que redesplegar el frontend.

## Verificación

- Backend: `https://DOMINIO-BACKEND/api/v1/health`
- Frontend: abrir el dominio, crear una cuenta y guardar un registro.
