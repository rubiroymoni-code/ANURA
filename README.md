# ANURA

Plataforma mobile-first para entrenamiento, nutrición compartida y evolución física. Nace para José y Mónica, pero su dominio y autorización son multiusuario desde el primer día.

## Primer hito

Este commit establece una base ejecutable:

- Java 21 + Spring Boot + PostgreSQL + Flyway.
- React + TypeScript + Vite.
- PWA instalable con navegación móvil.
- Docker Compose para desarrollo.
- Health checks de backend y frontend.
- Arquitectura y alcance de Fase 1 documentados.

## Arranque

Requisitos: Docker y Docker Compose.

```bash
cp .env.example .env
docker compose up --build
```

- Web: http://localhost:5173
- API: http://localhost:8080/api/v1/health
- Swagger: http://localhost:8080/swagger-ui.html

## Estructura

```text
backend/          API y dominio Spring Boot
frontend/         PWA React
contracts/csv/    contratos oficiales versionados
docs/             arquitectura, API y experiencia
```

Consulta [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) para las decisiones técnicas y el alcance cerrado de Fase 1.

## Funcionalidad disponible

- Registro e inicio de sesión con JWT.
- Perfil multiusuario y datos aislados por usuario.
- Seguimiento de entrenamientos, comidas, peso, medidas y objetivos.
- Resumen diario e historial.
- PWA mobile-first instalable.
- API documentada con Swagger.
- Unidades domésticas con invitaciones y nutrición compartida.
- Recetas, cantidades individuales, macros deterministas y planes versionados.
- Importación CSV individual, compartida y de recetas con preview.
- Lista de compra semanal consolidada.
- Ejecución real de entrenamientos: plan del día, sesiones libres, series, descanso, dolor, métricas e histórico.
- Registro offline de series con IndexedDB y sincronización idempotente.

## Railway

Consulta [docs/RAILWAY.md](docs/RAILWAY.md). El backend y el frontend se despliegan como servicios separados con directorios raíz `/backend` y `/frontend`.

> ANURA no sustituye el asesoramiento médico, nutricional o deportivo profesional.
