# Sprint 6 — Entrega final

**Abierto:** 2026-09-24 · **Cerrado:** — *(se llena el día que el entregable se demuestra funcionando)*

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
| — | 🟡 Entorno local: `scripts/verificar-flujos.mjs` dio **21 pasos correctos, 0 con fallo el 2026-09-24** sobre el backend reconstruido desde `main` (`488bb6c`) con la base de la demo (211 sectores, histórico y 40 001 cuentas), incluido el panel del veedor completo con segundo factor. Antes, el backend que corría era una imagen anterior a los PR #48 y #49 y fallaban CORS, la foto y «cerrar sesión revoca el token» (3 pasos); reconstruir con `--build` los resolvió. **Falta** repetirlo desde una copia sin `.env` ni datos sobre el `main` final de la entrega | `BUG-101` corregido (PR #49) |
| — | 🟡 Dataset histórico cargado — **sembrado y verificado el 2026-09-24**: `sembrar-historico-cortes.mjs` cargó 120 cortes y 600 reportes (mayo–julio 2026); `GET /api/cumplimiento` pasó de 33,33 % (1 corte de prueba) a 99,62 % y coincide con el cálculo independiente hecho en `mongosh` sobre los mismos documentos (11 228 400 s prometidos / 11 270 826 s reales, 121 cortes con fin real); el sector `ciudadela-11-de-noviembre` da 252 000 / 251 277 s y `serie` y `serie.csv` responden con los tres meses. Corrida anotada en el guion de demo | Entorno levantado |
| RNF027 | 🟡 Mediciones locales — **repetidas el 2026-09-24** (`escalabilidad.md`): lectura pública a 500 y 1 000 req/s contra el backend directo (p95 5,2 / 7,9 ms, 0,03 % de errores, todos `dial: i/o timeout` del banco), `RNF002` (p95 32,6 ms, 0 % errores) y 2 000 conexiones SSE (1 995 abiertas, primer evento p95 205 ms). **Hallazgo sin aislar:** con los SSE abiertos, la API por el puerto publicado rechaza conexiones desde el host aunque responde por dentro del contenedor. **Sin repetir:** nginx con micro-caché, escritura en pico y 10 000 SSE; de esas tres siguen valiendo las cifras de la medición anterior | Entorno levantado |
| — | 🟡 Guion de demo escrito: [`docs/ingenieria/guion-de-demo.md`](../ingenieria/guion-de-demo.md) (2026-09-24). Los comandos de mapa, consenso (con SSE y bitácora) y cumplimiento se ejecutaron contra el entorno real; **la suscripción por correo se ensayó a mano el 2026-09-24** (confirmación y aviso «Se fue el agua» en MailHog); **el panel del veedor se recorrió el 2026-09-24** con `verificar-flujos.mjs` (login del ADMIN con segundo factor, cortes, moderación, ingesta, invitaciones, auditoría y cierre de sesión). **El guion se ensayó completo el 2026-09-24** (secciones 0 a 6): salieron dos errores míos, `size` en vez de `tamano` en la bitácora y una lista de meses desactualizada, ya corregidos. Falta solo repetirlo el día previo a la presentación | Los tres anteriores |
| RF041 | Fuera del sprint salvo que lleguen credenciales de WhatsApp/Telegram; se declara como no cubierto | Credenciales de terceros |

---

## 3. Obstáculos del sprint — resumen

| Qué detuvo el avance | De qué dependía | Días | Cómo se resolvió |
|---|---|---|---|
| Definición de «demo» sin frontend propio | Decisión del dueño (`REC-018`) | — | Abierto: propuesta en este archivo |

---

## 4. Review — qué se demostró funcionando

*Se llena al cerrar.* Solo cuenta lo que se muestre corriendo.

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|

**Comprometido:** 4 · **Entregado:** 0 · **Arrastrado al siguiente sprint:** —

---

## 5. Métricas del sprint

| Métrica | Valor |
|---|---|
| Requisitos entregados / comprometidos | — |
| PRs fusionados | — |
| Bugs abiertos / cerrados | — |
| Cobertura `domain/` + `application/` | — |
| Build en verde al cierre | — |

---

## 6. Retrospectiva

*Se llena al cerrar.*
