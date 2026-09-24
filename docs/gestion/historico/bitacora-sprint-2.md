# Bitácora de sesiones — Sprint 2 y trabajo adelantado (rotada)

Entradas rotadas desde `docs/gestion/bitacora-sesiones.md` el 2026-09-24 (la bitácora superó las 30 entradas al cerrar el Sprint 6). Son las 5 más antiguas del bloque «Preparación del backend»: el Sprint 2 y trabajo que se adelantó de los Sprints 3 y 4.

### 2026-08-09 · `feature/crud-cortes-veedor` (PR #119, no #116)
**Qué:** `RegistrarEventoBitacoraService` (RF026) y `GestionarCorteOficialService`
actualizado para anexar un evento de bitácora por cada sector afectado al registrar
(`CORTE_ANUNCIADO`) y al cerrar (`CORTE_RESTABLECIDO`) un corte — antes solo el consenso ciudadano
anexaba. 176/176 pruebas en verde. Fusionado a `develop`.
**Hallazgo de proceso:** el PR #116 se fusionó *antes* de que este commit llegara al remoto —
quedó huérfano en la misma rama con el PR ya cerrado, así que abrí el PR #119 sobre el mismo commit.
Pasa cuando se empuja a una rama cuyo PR ya fue aprobado y fusionado en paralelo;
vale la pena revisar el estado del PR (`gh pr view <N> --json state`) antes de empujar, no solo al
abrirlo.
**Sigue:** RF018 (moderación de reportes) sigue fuera — sin puerto de dominio todavía.

### 2026-08-09 · `feature/indice-cumplimiento`
**Qué:** `CalcularCumplimientoService` (RF020-RF022, M6 — el diferencial del proyecto).
`ADR-022`: agrega por suma de duraciones, no promedio de porcentajes. `IndiceCumplimientoController`
público en `/api/cumplimiento` (porCorte, porSector, global). Agregado
`CorteAguaRepository.listarTodos()`. 178/178 pruebas en verde. Fusionado a `develop` en el PR #118.
**Sigue:** —

### 2026-08-09 · `feature/crud-cortes-veedor`
**Qué:** `GestionarCorteOficialService` (RF016-RF017) y `CorteController` en
`/api/veedor/cortes` (registrar, cerrar, consultar, listar por sector), protegido por el JWT ya
existente sin tocar `SecurityConfig`. Cerrar un corte ya cerrado responde 409 (nuevo
`IllegalStateException` en `ManejadorGlobalDeErrores`). 175/175 pruebas en verde. Fusionado a
`develop` en el PR #116.
**Sigue:** RF018 (moderación de reportes) queda fuera — sin puerto de dominio todavía.

### 2026-08-09 · `feature/corteagua-mongo-adapter`
**Qué:** Construido `CorteAguaMongoAdapter` (RF016-RF017) — el dominio de `CorteAgua` existía sin
adaptador que lo persistiera. Índice de `sectoresAfectados` agregado a `IndicesMongo`. 154/154
pruebas en verde. Trabajo adelantado de Sprint 3.
**Sigue:** Fusionado a `develop` en el PR #113.

### 2026-08-09 · `feature/cache-sectores-y-rate-limit`
**Qué:** Activados los dos pendientes de infraestructura (`sprint-2.md` §2): `@Cacheable` en `GET /api/sectores`
con invalidación al confirmar consenso, y reglas de rate limiting para `/api/veedor/sesion` y
`/api/reportes`. 155/155 pruebas en verde. `REC-006` registrada (trampa de `RateLimitConfig` en
`@WebMvcTest`). Fusionado a `develop` en el PR #112, implementación registrada.
**Sigue:** —

---
