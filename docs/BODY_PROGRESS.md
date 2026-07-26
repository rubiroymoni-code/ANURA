# Evolución corporal y check-in semanal

El módulo específico utiliza `body_checkin` y `progress_photo`; los registros anteriores de peso y medidas en `tracker_entry` se conservan y aparecen como históricos legacy. Todas las consultas incorporan el usuario autenticado y la fecha es única dentro de cada usuario.

## Evolución

`GET /api/v1/body-checkins/evolution` calcula en cada petición peso mínimo/máximo, cambios total y anterior, tendencia, racha semanal y series de todas las medidas. `STABLE` representa una variación absoluta inferior a 0,2 kg. La media móvil usa registros dentro de los siete días naturales anteriores y se omite cuando no hay suficientes puntos.

Los rangos del cliente son 1, 3, 6 y 12 meses o todo el histórico. Las gráficas son SVG responsive sin dependencia adicional.

## Fotografías

PostgreSQL solo conserva metadatos y URLs HTTPS. `ProgressPhotoStorage` separa el dominio del futuro proveedor de objetos. El proveedor externo está deshabilitado por defecto; se activa con `PROGRESS_PHOTO_STORAGE_ENABLED=true` después de configurar un almacenamiento permanente externo. Nunca se utiliza el disco local de Railway ni Base64.

Limitación actual: ANURA valida y registra URLs ya cargadas; la subida firmada a un proveedor concreto queda pendiente de elegir dicho proveedor.
