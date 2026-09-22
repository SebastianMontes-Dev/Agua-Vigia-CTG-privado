# Sprint 3 — Administración y alertas

**Abierto:** no registrado (el trabajo ocurrió entre el retiro de `docs/gestion/` el 2026-08-12 y su
reanudación el 2026-09-17; nadie llevó la fecha) · **Cerrado:** 2026-09-22 — reconstruido
retroactivamente contra el código y `docs/ingenieria/matriz-trazabilidad.md`, no contra una ceremonia
que ocurrió en su momento. Mismo motivo que dejó a `sprint-2.md` sin Review formal (ver su §4).

> **Por qué se cierra así.** El código ya entrega el objetivo de este sprint — verificado el
> 2026-09-22, con pruebas nombradas — pero nadie escribió `sprint-3.md` cuando ocurrió. Fabricar una
> fecha de apertura o una retrospectiva de una ceremonia que no ocurrió sería inventar evidencia,
> exactamente lo que este proyecto existe para no hacer (mismo criterio que `sprint-2.md` §4).

---

## 1. Objetivo del sprint

**El veedor registra un corte oficial y lo cierra con la hora real de restablecimiento; el suscriptor
de ese sector recibe un correo cuando el estado cambia, venga de un corte oficial o del consenso
ciudadano.**

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| RF016, RF017 | ✅ Entregado — `GestionarCorteOficialService` + `CorteController` en `/api/veedor/cortes` (registrar, cerrar con hora real, consultar, listar por sector); cerrar un corte ya cerrado responde 409 | `CorteAguaMongoAdapter` (Sprint 1) · JWT del panel (RF019) |
| RF018 | ✅ Entregado — `ModerarReporteService` + `ModeracionReporteController` en `/api/veedor/reportes` (listar pendientes, aprobar, descartar); `ADR-023` define "dudoso" como "todo reporte sin moderar" | Reportes ciudadanos (Sprint 2) |
| RF019, RNF011 | ✅ Entregado — `POST /api/veedor/sesion` (credencial BCrypt), `SecurityConfig` protege `/api/veedor/**`, token JWT expira a las 8h exactas | — |
| RF014 | ✅ Entregado — `SectorMongoAdapter` publica `SectorActualizadoEvent` al guardar un sector; `NotificarSuscripcionesService` lo escucha y avisa a los suscriptores confirmados de ese sector. Cubre tanto el corte oficial (este sprint) como el consenso ciudadano (Sprint 2) | `SuscribirseService` (Sprint 1) |
| RF026, RF028 | ✅ Entregado — `RegistrarEventoBitacoraService` anexa `CORTE_ANUNCIADO`/`CORTE_RESTABLECIDO` por cada sector afectado; el puerto no expone edición ni borrado (inmutable por diseño, no por regla aparte) | `EvaluarConsensoService` ya anexaba desde el consenso (Sprint 2) |
| RF030 | ✅ Entregado — `RssCollector` (Google News RSS, Zona Cero RSS), adelantado de Sprint 4/M9 | `AcuacarApiCollector` (Sprint 1) |

La columna **Depende de** es la importante: es donde se ve qué tiene que existir antes. Se escribe
con el artefacto concreto que falta, no con una intención.

---

## 3. Obstáculos del sprint — resumen

No registrados: `docs/gestion/` estaba retirado del proyecto mientras este trabajo ocurrió (ver la nota
de apertura). No es evidencia de que no hubiera obstáculos, es ausencia de registro — se dice así en
vez de inventar que no hubo ninguno.

---

## 4. Review — qué se demostró funcionando

**Verificación del 2026-09-22** (contra el código real de `main`, no contra la descripción de un PR):

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|
| RF016, RF017 | `GestionarCorteOficialServiceTest` cubre registrar, cerrar con hora real y el 409 al cerrar dos veces | ✅ |
| RF018 | `ModerarReporteService` + su controlador, con `ADR-023` documentando el criterio de "dudoso" | ✅ |
| RF019, RNF011 | `JwtProviderTest`, `VeedorAuthControllerTest`: login, 401/404 según corresponda, expiración exacta de 8h | ✅ |
| RF014 | Publicación del evento verificada en `SectorMongoAdapter.java:119,143`; consumo en `NotificarSuscripcionesServiceTest` (avisa una sola vez por suscriptor confirmado, pasa el sector del evento, no mezcla sectores) y en `GestionarCorteOficialServiceTest` | ✅ |
| RF026, RF028 | Eventos anexados en la bitácora por todo el ciclo de vida del corte; sin ruta de edición ni borrado en `BitacoraPort` | ✅ |
| RF030 | `RssCollectorTest` | ✅ |

**Comprometido:** 6 · **Entregado:** 6 · **Arrastrado al siguiente sprint:** —

---

## 5. Métricas del sprint

No se tomaron al cierre real porque no se llevó constancia entonces. Lo de abajo es el estado del
proyecto el 2026-09-22, no el del cierre de este sprint — se dice así explícitamente para no
confundir una foto de hoy con una métrica histórica.

| Métrica | Valor (medido 2026-09-22, no al cierre real) |
|---|---|
| Cobertura `domain/` + `application/` | 92,4% / 99,2% (medido 2026-09-05, `RNF017`; hoy la build exige ≥85% con `jacoco:check`) |
| Build en verde al cierre | Sí — `main` en verde en sus tres workflows |

---

## 6. Retrospectiva

No se llena: no hubo una ceremonia de cierre real de la que reconstruir "qué funcionó" o "qué no" sin
inventarlo. Ver la nota de apertura.
