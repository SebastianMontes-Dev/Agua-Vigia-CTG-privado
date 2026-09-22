# Bitácora de sesiones de trabajo

> Registro **append-only** de cada sesión de trabajo con IA que produjo un cambio en el repositorio.
> Existe para que la sesión siguiente arranque sabiendo dónde quedó todo,
> sin reconstruir una conversación de horas.
>
> **Para agregar una entrada: usa la skill `cerrar-sesion`.**
> **Formato: 3 líneas máximo.** Una entrada larga es una entrada que nadie lee.

---

## Cómo se lee esto

| Campo | Qué significa |
|---|---|
| **Fecha** | AAAA-MM-DD |
| **Rama** | Dónde quedó el trabajo |
| **Qué** | Una frase, en pasado, con el resultado — no la intención |
| **Sigue** | El siguiente paso concreto. Sin esto, la próxima sesión empieza decidiendo |

Referencias cruzadas: `ADR-NNN` · `BUG-NNN` · `RF0NN` · `archivo:línea`.
**Nunca se pega código aquí.**

---

## Sprint 2

### 2026-09-21 · `chore/registrar-dependabot-26-a-29`
**Qué:** Se fusionó el #25 y el #30 (quitan el pendiente de `frontend/`, ya borrado del disco). Se cerraron #7 y #11 por `ADR-059`. De cuatro PR nuevos de Dependabot (#26–#29: resilience4j, jacoco, Maven wrapper, springdoc 2.9.1), todos menores, se actualizaron sus ramas y se fusionaron con `Backend CI` en verde; `main` quedó en verde en sus tres workflows (`2c6d6f7`). El #2 (Testcontainers 2.0) se reverificó y sigue roto de verdad: queda abierto.
**Sigue:** Del dueño: añadir `permissions` a `secret-scan.yml` (`BUG-089`, `REC-016`), decidir el #2 y la hoja de ruta de los Sprints 3–6 (`REC-017`).

### 2026-09-21 · `chore/ordenar-dependabot-y-documentos`
**Qué:** Se fusionó el #23 y, de Dependabot, #1, #4, #24, #8, #5 y #9 con el CI verde sobre el `main` nuevo (`Backend CI` completo en los dos de `pom.xml`); `main` quedó en verde en sus cuatro workflows. Se decidió no migrar a Spring Boot 4 (`ADR-059`) y Dependabot ya no propone ese salto ni el de springdoc. Se corrigieron contradicciones entre documentos: línea 1 corrupta de la matriz, RNF012–016 sin marcar como retirados, cobertura del registro (28/36 → 40/46 RF, 17/27 RNF), tabla de recomendaciones, plan de pruebas con staging inexistente y sprint 2 con filas 🟡 ya entregadas. Se registró `BUG-089` (`gitleaks` por PR falla con 403) y las `REC-016`/`REC-017`.
**Sigue:** Del dueño: añadir `permissions` a `secret-scan.yml` (bloqueado por la regla `Read(**/*secret*)`, `REC-016`), decidir el #2 (Testcontainers 2.0) y la hoja de ruta de los Sprints 3–6 (`REC-017`). `frontend/` ya se borró del disco (2026-09-21).

### 2026-09-21 · `feat/datos-de-demostracion`
**Qué:** Se publicaron 6 PR encadenados (#16 a #21) y se respondieron las decisiones del dueño: proyecto académico local sin hosting ni CDN (`ADR-057`), reportes 12 meses y eventos permanentes con índice TTL (`ADR-058`), Sprint 2 cerrado (`REC-014`), `aviso-corte.html` borrado, CORS abierto solo en `dev` para el frontend. Sembrador de 20 000 cuentas de demostración (nombres únicos, seis estados, dos roles), comprobado por la API: `X-Total-Count: 20001`, páginas y filtros en 17–58 ms. El CI encontró dos fallos de la rama 1 (`MAIL_USERNAME` sin definir en la validación del compose) y del workflow de secretos.
**Sigue:** Fusionar los PR en orden con squash, `@dependabot rebase` y fusionar #1, #4, #5, #8, #9. Pendiente del dueño: añadir `permissions: contents: read, pull-requests: read` a `secret-scan.yml` (no puedo abrirlo por tu regla sobre archivos «secret»), el token de Telegram (RF041) y borrar `frontend/` del disco.

### 2026-09-21 · `feat/ajustes-de-contrato-frontend`
**Qué:** Se tomaron las decisiones delegadas: confirmar/cancelar suscripción por POST (`ADR-054`), bitácora con conteo de sustento y detalle aparte (`ADR-055`), histórico público de cortes, población, cambio de clave con sesión y reenvío de verificación/invitación (`ADR-056`); 406 en vez de 500 (`BUG-087`); respaldo y restauración de Mongo corregidos y probados con autenticación (`BUG-088`). Contrato en 65 rutas; `./mvnw verify`: 823 pruebas en verde. Dependabot revisado, sin tocar.
**Sigue:** Commitear esta rama (nada commiteado) y publicar las 6 ramas. Sin hacer y solo del dueño: programar el respaldo, hosting/dominio/CDN, HA de Mongo y Redis, política de retención, cerrar el Sprint 2 (`REC-014`), `@dependabot rebase` y fusión de #1/#4/#5/#8/#9, RF041 y los 20 000 datos (falta saber de qué).

### 2026-09-21 · `chore/retirar-frontend`
**Qué:** Se retiró el frontend (`ADR-048`) y se pulió el backend para RNF027 (50 000 usuarios): `ADR-049`–`ADR-053`, `BUG-076`–`BUG-086`, `docs/api/` + `openapi.yaml` para el frontend nuevo y `docs/ingenieria/escalabilidad.md`. `./mvnw verify`: 782 pruebas en verde. Medido a escala reducida (nginx ~3 700 req/s con el backend al ~6 % de un núcleo, 10 000 SSE, escritura 63 001 peticiones a p95 36 ms); la medición destapó `BUG-085` y `BUG-086`. **50 000 NO está demostrado.**
**Sigue:** Hacer los commits (nada está commiteado; ramas sugeridas en el plan), borrar `frontend/` del disco a mano (`rm -rf` está denegado en `settings.json`), decidir cerrar el Sprint 2 (`REC-014`) y luego la prueba distribuida con ≥ 3 réplicas. Quedaron sin hacer: confirmar/cancelar suscripción sigue por GET, reenvío de verificación/invitación, cambio de clave con sesión, adaptador de fotos S3, Mongo/Redis con alta disponibilidad, RF041.

### 2026-09-20 · `main`
**Qué:** Se retiró OpenSpec (`ADR-047`): las 13 capacidades pasaron a `docs/ingenieria/comportamiento-del-sistema.md` (54 requisitos, 93 escenarios) y se borraron `openspec/`, las 12 copias de skills y los comandos `opsx`.
**Sigue:** Aprobar el diseño del rotulado de barrios (`BUG-073`) y hacer el commit de todo lo pendiente.

### 2026-09-20 · `main`
**Qué:** Se cerró el `BUG-074` (`resumirServicio`, `ADR-046`), se registró el CVE de Netty (`BUG-075`) y `openspec/` recogió el resumen del servicio, el reporte desde el llamado a veedores y la vista pública de Acuacar. Las reglas globales quedaron en un solo `CLAUDE.md` y se retiró Notion.
**Sigue:** Aprobar el diseño del rotulado de barrios (`BUG-073`: principales por área, N = 12, zoom 14) y luego confirmar con el commit de todo lo pendiente.

### 2026-09-19 · `main`
**Qué:** Se investigó el E2E en rojo del frontend: la bitácora pública no recibía los boletines (`BUG-071`) y no se cargaba el CSS de escritorio (`BUG-072`), ambos corregidos; quedan abiertos el rotulado del mapa (`BUG-073`) y los props sin cablear que rompen `npm run build` (`BUG-074`). Netty subió a 4.1.137 por CVE-2026-75595 y el CI del backend volvió a verde.
**Sigue:** Decidir el cálculo de «% operativa» y si se construye el rotulado de barrios; hasta entonces el Frontend CI seguirá en rojo.

### 2026-09-19 · `main`
**Qué:** El proyecto pasó a ser individual (`ADR-045`): se retiraron los roles D1–D5, las compuertas, el registro de bloqueos y Pages; registros y Javadoc quedaron sin actores y la Sala de control se genera solo en local desde `docs/`. `verify` del backend sin fallos (660 casos).
**Sigue:** Contrastar `sprint-2.md` contra el código (M10–M15 ya están entregados) y subir el commit a `origin`.

### 2026-09-08 · `main`
**Qué:** Refactor costero aplicado sin alterar Leaflet/GeoJSON/endpoints; feed público limitado a Acuacar, mapa rotulado y estable ante clics rápidos, y veeduría Stitch con olas dobles animadas. Build, lint, 107 pruebas unitarias y 12 E2E en verde.
**Sigue:** Revisar los cambios en la rama de trabajo y abrir un PR.

### 2026-08-09 · `fix/integrar-formulario-reportes`
**Qué:** RF008 conectado a `POST /api/reportes`: formulario real en dos pasos, huella anónima SHA-256, ubicación opcional, errores RFC 7807 y contrato OpenAPI regenerado; 26 pruebas, lint, build y `npm audit` en verde.
**Sigue:** Fusionar el PR a `main`; después registrar la entrega en `registro-de-implementaciones.md`.

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

### 2026-08-09 · `feature/moderacion-reportes`
**Qué:** `ModerarReporteService` (RF018, M5) — el veedor aprueba o descarta reportes ciudadanos.
`ADR-023`: nadie había definido qué hace "dudoso" a un reporte, así que se interpreta como "todo
reporte sin moderar" (nace `PENDIENTE`) en vez de inventar una heurística de fraude no pedida.
`ReporteCiudadano` gana `EstadoModeracion`; `ModeracionReporteController` en `/api/veedor/reportes`.
209/209 pruebas en verde.
**Sigue:** Alcance acotado a propósito: descartar no recalcula consenso ni
el conteo de RF006 (ver el propio ADR).

### 2026-08-09 · `feature/bitacora-publica`
**Qué:** `BitacoraController` público en `GET /api/bitacora` (RF027), directo a
`EventoBitacoraRepository` sin caso de uso (ADR-015). 178/178 pruebas en verde. Fusionado a
`develop` en el PR #120 — **M8 completo**.
**Sigue:** —

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

## Sprints 0 y 1

Rotados a [`historico/bitacora-sprint-1.md`](historico/bitacora-sprint-1.md) y [`historico/bitacora-sprint-0.md`](historico/bitacora-sprint-0.md).

<!--
Plantilla — copiar, rellenar, pegar ARRIBA de la entrada más reciente del sprint en curso.

### AAAA-MM-DD · `rama`
**Qué:** <resultado en pasado, máx. 2 líneas, con referencias ADR/BUG/RF>
**Sigue:** <siguiente paso concreto, una línea>

Rotación: al superar 30 entradas, las más viejas pasan a
docs/gestion/historico/bitacora-sprint-<N>.md. Se hace al cerrar el sprint.
-->
