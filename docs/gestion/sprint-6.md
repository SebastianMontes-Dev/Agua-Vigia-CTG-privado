# Sprint 6 — Entrega final

**Abierto:** 2026-09-24 · **Cerrado:** 2026-09-24 — el entregable (demo local con `docker compose` y dataset histórico) se demostró funcionando; `RNF027` queda parcial y arrastrado

> **La definición de «demo» de abajo fue aceptada por el dueño el 2026-09-24** (`REC-018` resuelta).

---

## 1. Objetivo del sprint

**Cualquier persona puede clonar el repositorio, levantar todo con un solo `docker compose up` y ver el
sistema funcionando con el dataset histórico cargado, siguiendo un guion de demo que declara su límite
(backend sin frontend propio, 50 000 usuarios no demostrables en local).**

Propuesta para `REC-018`: la demo **no espera al frontend**. Se hace contra el backend con el guion de
`docs/ingenieria/entorno-local.md` (Swagger y `scripts/verificar-flujos.mjs`). Si el frontend nuevo ya está
fusionado cuando se presente, se añade encima; no es requisito para cerrar el sprint (`ADR-048`, `ADR-057`).

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| — | ✅ Entorno desde cero: `scripts/verificar-flujos.mjs` dio **21 pasos correctos, 0 con fallo el 2026-09-24 a la primera** sobre una copia limpia de `main` (`488bb6c`, sin `.env` ni datos, imagen y volúmenes nuevos), y otra vez 21/0 sobre el backend reconstruido con la base de la demo (211 sectores, histórico, 40 001 cuentas). Detalle y trampas en `plan-de-pruebas.md` §8. Al principio fallaban 3 pasos por una imagen anterior a los PR #48 y #49; `--build` los resolvió | `BUG-101` corregido (PR #49) |
| — | ✅ Dataset histórico cargado — **sembrado y verificado el 2026-09-24**: `sembrar-historico-cortes.mjs` cargó 120 cortes y 600 reportes (mayo–julio 2026); `GET /api/cumplimiento` pasó de 33,33 % (1 corte de prueba) a 99,62 % y coincide con el cálculo independiente hecho en `mongosh` sobre los mismos documentos (11 228 400 s prometidos / 11 270 826 s reales, 121 cortes con fin real); el sector `ciudadela-11-de-noviembre` da 252 000 / 251 277 s y `serie` y `serie.csv` responden con los tres meses. Corrida anotada en el guion de demo | Entorno levantado |
| RNF027 | 🟡 Mediciones locales — **repetidas el 2026-09-24** (`escalabilidad.md`): lectura pública a 500 y 1 000 req/s contra el backend directo (p95 5,2 / 7,9 ms, 0,03 % de errores, todos `dial: i/o timeout` del banco), `RNF002` (p95 32,6 ms, 0 % errores) y 2 000 conexiones SSE (1 995 abiertas, primer evento p95 205 ms). **Hallazgo sin aislar:** con los SSE abiertos, la API por el puerto publicado rechaza conexiones desde el host aunque responde por dentro del contenedor. **Sin repetir:** nginx con micro-caché, escritura en pico y 10 000 SSE; de esas tres siguen valiendo las cifras de la medición anterior | Entorno levantado |
| — | ✅ Guion de demo escrito: [`docs/ingenieria/guion-de-demo.md`](../ingenieria/guion-de-demo.md) (2026-09-24). Los comandos de mapa, consenso (con SSE y bitácora) y cumplimiento se ejecutaron contra el entorno real; **la suscripción por correo se ensayó a mano el 2026-09-24** (confirmación y aviso «Se fue el agua» en MailHog); **el panel del veedor se recorrió el 2026-09-24** con `verificar-flujos.mjs` (login del ADMIN con segundo factor, cortes, moderación, ingesta, invitaciones, auditoría y cierre de sesión). **El guion se ensayó completo el 2026-09-24** (secciones 0 a 6): salieron dos errores míos, `size` en vez de `tamano` en la bitácora y una lista de meses desactualizada, ya corregidos. Falta solo repetirlo el día previo a la presentación | Los tres anteriores |
| RF041 | Fuera del sprint salvo que lleguen credenciales de WhatsApp/Telegram; se declara como no cubierto | Credenciales de terceros |

---

## 3. Obstáculos del sprint — resumen

| Qué detuvo el avance | De qué dependía | Días | Cómo se resolvió |
|---|---|---|---|
| Definición de «demo» sin frontend propio | Decisión del dueño (`REC-018`) | 0 (mismo día) | Aceptada: demo contra el backend, sin esperar al frontend |
| Backend en ejecución con una imagen anterior a los PR #48 y #49 | Reconstruir con `--build` | 0 | 3 pasos de `verificar-flujos.mjs` fallaban (CORS, foto, cierre de sesión); reconstruir los resolvió |
| Clave del ADMIN de la base de la demo perdida (el `.env` solo guardaba su hash) | Vaciar `usuarios`, hash nuevo, resembrar | 0 (unas horas) | ADMIN nuevo y 40 001 cuentas. El sistema de permisos bloqueó al agente escribir credenciales y el borrado de usuarios: el dueño editó el `.env` a mano y lo ejecutó |
| Límites por IP al repetir `verificar-flujos.mjs` | Esperar 10 minutos entre corridas | 0 | Documentado en el guion |
| Rechazo de conexiones del puerto publicado con 2 000 SSE abiertos | Docker Desktop (reenvío de puertos) | — | **Sin aislar**; anotado en `escalabilidad.md` |

---

## 4. Review — qué se demostró funcionando

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|
| — | Entorno desde cero: `verificar-flujos.mjs` 21/0 a la primera sobre una copia limpia de `main` (`488bb6c`) y 21/0 sobre la base de la demo | ✅ |
| — | Histórico sembrado (120 cortes, 600 reportes) y Índice de Cumplimiento de la API igual a un cálculo independiente en `mongosh` | ✅ |
| — | Guion de demo ensayado de principio a fin (secciones 0–6), con dos correcciones de comandos | ✅ |
| RNF027 | Lectura pública a 500 y 1 000 req/s, `RNF002` y 2 000 SSE repetidos; **sin repetir** nginx con micro-caché, escritura en pico y 10 000 SSE; hallazgo del puerto publicado sin aislar | 🟡 Parcial |
| RF041 | No construido: depende de credenciales de WhatsApp/Telegram | Fuera de alcance |

**Comprometido:** 5 · **Entregado:** 3 · **Parcial:** 1 (`RNF027`) · **Arrastrado / fuera:** `RNF027` (lo no repetido) y `RF041`

---

## 5. Métricas del sprint

| Métrica | Valor (2026-09-24) |
|---|---|
| Requisitos entregados / comprometidos | 3 / 5 (1 parcial, 1 fuera de alcance) |
| PRs fusionados | 0 en el sprint; el #50 (documentación) está abierto con el CI en verde |
| Bugs abiertos / cerrados | Sin bugs nuevos registrados; `BUG-101` se cerró antes (PR #49). Un fallo intermitente de `verificar-flujos.mjs` explicado por el margen de 1 s del filtro JWT (`plan-de-pruebas.md` §8) |
| Cobertura `domain/` + `application/` | 90,2 % / 97,6 % medida el 2026-09-22; **no se re-midió** en este sprint |
| Build en verde al cierre | El CI del PR #50 pasa; las pruebas del backend **no se re-ejecutaron** hoy (no hubo cambios de código) |

---

## 6. Retrospectiva

**Qué funcionó**
- Verificar con el sistema real, no de memoria: aparecieron dos errores del propio guion (`size` por `tamano`, meses de la serie) y la imagen vieja del backend.
- El precedente de `ADR-031`/`ADR-043` resolvió sin discusión cómo documentar credenciales de la demo.

**Qué no funcionó**
- La clave del ADMIN se perdió porque solo existía su hash y nadie la anotó: costó vaciar y resembrar los usuarios.
- Repetir `verificar-flujos.mjs` gasta límites por IP y no se puede encadenar.
- Varias tareas quedaron bloqueadas por permisos del entorno y necesitaron trabajo manual del dueño.

**Acciones para el próximo sprint** *(fechas por confirmar por el dueño)*

| Acción | Para cuándo |
|---|---|
| Que `verificar-flujos.mjs` espere algo más de 1 s antes del cierre de sesión | Antes de la presentación |
| Repetir el guion completo y comprobar `GET /api/sectores` el día previo | El día previo a la presentación |
| Terminar `RNF027`: nginx con micro-caché, escritura en pico, 10 000 SSE y aislar el rechazo del puerto publicado | Antes de la presentación, si hay tiempo |
