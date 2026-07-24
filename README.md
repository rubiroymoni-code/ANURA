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

> ANURA no sustituye el asesoramiento médico, nutricional o deportivo profesional.
