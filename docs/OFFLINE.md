# Entrenamiento offline

Durante una sesión, la PWA guarda operaciones pendientes en IndexedDB con UUID de operación y entidad. Al volver la conexión envía el lote en orden; el servidor registra cada `operationId` y repetirlo devuelve el mismo resultado sin duplicar series.

```mermaid
sequenceDiagram
  participant PWA
  participant IndexedDB
  participant API
  PWA->>IndexedDB: guardar operación y UUID
  PWA-->>PWA: mostrar guardado en dispositivo
  PWA->>API: lote ordenado al recuperar conexión
  API->>API: validar usuario, sesión y operationId
  API-->>PWA: APPLIED / CONFLICT / ERROR
  PWA->>IndexedDB: borrar solo APPLIED confirmado
```

El service worker solo cachea app shell y recursos estáticos del mismo origen. Nunca cachea `/api`, autenticación ni respuestas privadas. Al cerrar sesión se elimina IndexedDB de entrenamiento. El lote está limitado por `WORKOUT_SYNC_MAX_OPERATIONS`.
