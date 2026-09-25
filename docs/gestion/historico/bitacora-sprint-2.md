# Bitácora de sesiones — Sprint 2 y trabajo adelantado (rotada)

Entradas rotadas desde `docs/gestion/bitacora-sesiones.md` el 2026-09-24 (la bitácora superó las 30 entradas al cerrar el Sprint 6). Son las 5 más antiguas del bloque «Preparación del backend»: el Sprint 2 y trabajo que se adelantó de los Sprints 3 y 4.

> La entrada siguiente se rotó el 2026-09-25, en la sesión de adaptación de prototipos del Sprint 7.

### 2026-09-19 · `main`
**Qué:** El proyecto pasó a ser individual (`ADR-045`): se retiraron los roles D1–D5, las compuertas, el registro de bloqueos y Pages; registros y Javadoc quedaron sin actores y la Sala de control se genera solo en local desde `docs/`. `verify` del backend sin fallos (660 casos).
**Sigue:** Contrastar `sprint-2.md` contra el código (M10–M15 ya están entregados) y subir el commit a `origin`.

> Las dos entradas siguientes se rotaron el 2026-09-25, en la sesión de fundamentos visuales del Sprint 7, al volver a superar la bitácora las 30 entradas.

### 2026-09-08 · `main`
**Qué:** Refactor costero aplicado sin alterar Leaflet/GeoJSON/endpoints; feed público limitado a Acuacar, mapa rotulado y estable ante clics rápidos, y veeduría Stitch con olas dobles animadas. Build, lint, 107 pruebas unitarias y 12 E2E en verde.
**Sigue:** Revisar los cambios en la rama de trabajo y abrir un PR.

### 2026-08-09 · `fix/integrar-formulario-reportes`
**Qué:** RF008 conectado a `POST /api/reportes`: formulario real en dos pasos, huella anónima SHA-256, ubicación opcional, errores RFC 7807 y contrato OpenAPI regenerado; 26 pruebas, lint, build y `npm audit` en verde.
**Sigue:** Fusionar el PR a `main`; después registrar la entrega en `registro-de-implementaciones.md`.

> La entrada siguiente se rotó el 2026-09-25, al superar la bitácora las 30 entradas otra vez (Sprint 7).

### 2026-08-09 · `develop` (cierre de sesión)
**Qué:** Sesión larga sobre todo el backend — 8 PRs fusionados (#112, #113,
#116, #118, #119, #120, #121, #124). Con esto **M1–M6 y M8 quedan completos**: los 9 puertos de
entrada y 8 de salida del dominio tienen implementación real. Regenerado `backend/openapi.yaml` (de 7
a 17 rutas — faltaban los cuatro módulos nuevos, PR #124). Puesta al día `registro-de-implementaciones.md`
(7 PRs sin registrar) y su tabla de cobertura, que seguía en el estado del Sprint 1 (36 RF: 28% → 78%
funcional real). `/security-review` sobre las cuatro superficies nuevas: sin hallazgos que superaran
el umbral de confianza.
**Hallazgo real:** RF014 (avisar al suscriptor cuando su sector cambia de estado) sigue sin conectar
— `NotificacionPort` solo se dispara al suscribirse (`SuscribirseService`), ni `EvaluarConsensoService`
ni `GestionarCorteOficialService` lo llaman. M4 queda en 75%, no 100%, por esto.
**Sigue:** M9 (etapa IA) descartada (`ADR-025`). RF014 es el hueco funcional real más concreto
que queda en lo ya construido.

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

### 2026-08-09 · `feature/bitacora-publica`
**Qué:** `BitacoraController` público en `GET /api/bitacora` (RF027), directo a
`EventoBitacoraRepository` sin caso de uso (ADR-015). 178/178 pruebas en verde. Fusionado a
`develop` en el PR #120 — **M8 completo**.
**Sigue:** —

<!-- Rotada el 2026-09-25, al abrir la sección «Frontend nuevo» de la bitácora. -->

### 2026-08-09 · `feature/moderacion-reportes`
**Qué:** `ModerarReporteService` (RF018, M5) — el veedor aprueba o descarta reportes ciudadanos.
`ADR-023`: nadie había definido qué hace "dudoso" a un reporte, así que se interpreta como "todo
reporte sin moderar" (nace `PENDIENTE`) en vez de inventar una heurística de fraude no pedida.
`ReporteCiudadano` gana `EstadoModeracion`; `ModeracionReporteController` en `/api/veedor/reportes`.
209/209 pruebas en verde.
**Sigue:** Alcance acotado a propósito: descartar no recalcula consenso ni
el conteo de RF006 (ver el propio ADR).

> La entrada siguiente se rotó el 2026-09-25, en la sesión del plan «identidad propia y respuestas claras».

### 2026-09-19 · `main`
**Qué:** Se investigó el E2E en rojo del frontend: la bitácora pública no recibía los boletines (`BUG-071`) y no se cargaba el CSS de escritorio (`BUG-072`), ambos corregidos; quedan abiertos el rotulado del mapa (`BUG-073`) y los props sin cablear que rompen `npm run build` (`BUG-074`). Netty subió a 4.1.137 por CVE-2026-75595 y el CI del backend volvió a verde.
**Sigue:** Decidir el cálculo de «% operativa» y si se construye el rotulado de barrios; hasta entonces el Frontend CI seguirá en rojo.

> La entrada siguiente se rotó el 2026-09-25, en la sesión de los prototipos de F1 con la identidad contenida.

### 2026-09-20 · `main`
**Qué:** Se cerró el `BUG-074` (`resumirServicio`, `ADR-046`), se registró el CVE de Netty (`BUG-075`) y `openspec/` recogió el resumen del servicio, el reporte desde el llamado a veedores y la vista pública de Acuacar. Las reglas globales quedaron en un solo `CLAUDE.md` y se retiró Notion.
**Sigue:** Aprobar el diseño del rotulado de barrios (`BUG-073`: principales por área, N = 12, zoom 14) y luego confirmar con el commit de todo lo pendiente.
