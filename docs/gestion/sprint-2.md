# Sprint 2 — Reporte ciudadano y consenso

**Abierto:** 2026-08-09 · **Cerrado:** — *(sin Review formal — `docs/gestion/` se retiró el
2026-08-12, ver §4. El objetivo sí se cumplió, verificable contra el código y contra
`matriz-trazabilidad.md`)*

> **Este sprint también abre con parte de su alcance ya entregada**, igual que pasó con el Sprint 1.
> La hoja de ruta original le asignaba tres tareas de calidad — Testcontainers, JaCoCo en CI, y que
> ArchUnit tumbe la build — **las tres ya están construidas** desde el Sprint 0/1 (`backend-ci.yml`
> ya corre `./mvnw verify` con Testcontainers real y publica el reporte JaCoCo; ArchUnit es parte de
> esa misma verificación). No hay compromiso nuevo por ese frente.

---

## 1. Objetivo del sprint

**Que un vecino de Cartagena reporte "no tengo agua" desde el mapa en dos toques, sin crear cuenta,
y que el sistema confirme automáticamente el corte de su sector cuando varios reportes
independientes coincidan en una ventana de tiempo.**

El Sprint 1 dejó `RegistrarReporteService` construido y probado en `application/` (RF005–RF008,
incluido RF006 real — límite de reportes por dispositivo), pero **sin `POST /api/reportes`**: la API
queda cerrada a propósito hasta este sprint (el contrato OpenAPI solo cubría `/api/sectores`). Ese es
el primer entregable. El segundo es `EvaluarConsensoService` (RF009–RF011): hoy `ContadorReportesPort`
ya acumula reportes por sector en Redis, pero nada los lee para decidir un cambio de estado.

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| RF005–RF008 | ✅ Entregado — `POST /api/reportes` expone `RegistrarReporteService`. `@Cacheable` sobre `GET /api/sectores` y las reglas de rate limiting (`/api/veedor/sesion`, `/api/reportes`) 🟡 en revisión — PR #112 | Dominio y puertos ✅ · `RegistrarReporteService` ✅ (Sprint 1) · PR #104 |
| RF009–RF011 | ✅ Entregado — `EvaluarConsensoService` con patrón Strategy (`UmbralFijoEstrategiaConsenso`, `UmbralProporcionalEstrategiaConsenso`), leyendo `ContadorReportesPort.contarRecientes`. Anexa el evento a `eventos_bitacora` en vez de publicar un evento de dominio aparte (más simple, mismo resultado observable) | Dominio y puertos ✅ · `ContadorReportesPort` ✅ (Sprint 1, PR #57) · PR #106 |
| RF013 (completo) · RF015 | ✅ Entregado — `GET /api/suscripciones/confirmar` y `GET /api/suscripciones/cancelar`, probados extremo a extremo (confirmar dos veces no falla, token inválido → 400 real) | Dominio y puertos ✅ · `SuscribirseService` ✅ (Sprint 1, PR #78) · PR #107 |
| RF008 (frontend) | 🟡 Retirado por alcance el 2026-09-21 (`ADR-048`: el frontend salió del repositorio y lo rehará otra persona; el contrato `POST /api/reportes` sí está entregado y documentado en `docs/api/reportes.md`). Antes: ✅ Implementado en `fix/integrar-formulario-reportes`, pendiente de revisión y fusión — `FormularioReporte` consume `POST /api/reportes`, genera una huella anónima SHA-256 estable, permite coordenada opcional y solo confirma éxito después del `201` real. Sin fallback ni datos simulados | `POST /api/reportes` ✅ (este sprint) |

La columna **Depende de** es la importante: es donde se ve qué tiene que existir antes. Se escribe
con el artefacto concreto que falta, no con una intención.

### Ya entregado antes de abrir el sprint (adelantado desde Sprint 0/1)

| Frente | Dónde | Estado |
|---|---|---|
| Testcontainers real en pruebas de integración | `backend-ci.yml`, 8 clases de test | ✅ |
| Cobertura JaCoCo publicada en CI | `backend-ci.yml` | ✅ |
| Build falla si ArchUnit falla | `backend-ci.yml` (`./mvnw verify`) | ✅ |
| `RegistrarReporteService` + RF006 real | PR #84, #89 | ✅ — falta solo el endpoint |
| Ventana deslizante de consenso en Redis (`ContadorReportesPort`) | PR #57 (Sprint 1) | ✅ — falta quien la lea |
| Rate limiting Redis (`RateLimitingInterceptor`) | PR #60 (Sprint 1) | 🟡 reglas activas desde PR #112, pendiente de fusionar |
| Caché sobre Redis (`@EnableCaching`) | PR #61 (Sprint 1) | 🟡 en uso desde PR #112, pendiente de fusionar |
| Colectores de ingesta M9 (`AcuacarApiCollector`, `RssCollector`) | PR #98 (Sprint 1, adelantado de Sprint 4) | ✅ — la capa de IA se descartó (`ADR-025`) |

**Lo que no estaba adelantado, y es el corazón de este sprint:** la API de reportes y la lógica de
consenso. Son los dos entregables que de verdad cierran M2 y abren M3.

---

## 3. Obstáculos del sprint — resumen

Ninguno registrado en este sprint.

---

## 4. Review — qué se demostró funcionando

*(Se llena el último día del sprint. Solo lo que se pudo mostrar corriendo.)*

**Este sprint queda sin Review, Métricas ni Retrospectiva formales — constancia explícita, no un
cierre inventado.** `docs/ingenieria/matriz-trazabilidad.md` (líneas 6-9) registra que
`docs/gestion/` —el registro de implementaciones por sprint que alimentaba estas ceremonias— **se
retiró del proyecto el 2026-08-12**, al fusionar el rediseño de frontend a `main`: la trazabilidad
pasó a vivir solo en esa matriz desde entonces. Este sprint se abrió el 2026-08-09, tres días antes
del retiro, y quedó a medio llenar cuando el mecanismo que lo hubiera cerrado dejó de usarse. Los
compromisos de la tabla §2 sí se entregaron — verificable contra el código y contra
`matriz-trazabilidad.md`, que documenta ese trabajo con fecha hasta 2026-08-12 y en adelante bajo
"Sprint 4/5/6" — pero nadie volvió a esta tabla para marcarlo aquí. Fabricar ahora un Review, unas
métricas o una retrospectiva de una ceremonia que no ocurrió sería inventar evidencia, exactamente
lo que este proyecto existe para no hacer.

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|

**Comprometido:** — · **Entregado:** — · **Arrastrado al siguiente sprint:** —

---

## 5. Métricas del sprint

| Métrica | Valor |
|---|---|
| Requisitos entregados / comprometidos | |
| PRs fusionados | |
| Bugs abiertos / cerrados | *(al abrir: 11 abiertos / 21 cerrados — `registro-de-bugs.md`)* |
| Cobertura `domain/` + `application/` | *(al abrir: `domain/` 74,3% · `application/` 100% — ambas ya superan `RNF017`)* |
| Build en verde al cierre | |

---

## 6. Retrospectiva

*(Se llena después del review. Máximo 3 por bloque, concretos, sobre el proceso.)*

**Qué funcionó**

**Qué no funcionó**

**Acciones para el próximo sprint**

| Acción | Para cuándo |
|---|---|
