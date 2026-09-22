# Sprint 5 — Calidad del backend

**Abierto:** no registrado (mismo motivo que `sprint-3.md` y `sprint-4.md`) · **Cerrado:** 2026-09-22 —
reconstruido retroactivamente. Ver la nota de `sprint-3.md` sobre por qué.

> **Este sprint no es el que originaba `docs/gestion/README.md`.** Su foco original era "Calidad,
> accesibilidad y PWA", con tres entregables: cobertura, auditoría WCAG AA y E2E en verde. Las dos
> últimas son de interfaz y `ADR-048` retiró el frontend del repositorio — no hay nada que auditar ni
> qué correr en Playwright. `docs/gestion/README.md` ya quedó redefinido a solo la parte de backend
> (`REC-017`). Cuando exista el frontend nuevo, su propia auditoría de accesibilidad y su propia suite
> E2E son trabajo de ese momento, no de este sprint reabierto.

> **Corrección del 2026-09-22, tarde:** este sprint se cerró citando `jacoco:check` como lo que
> exige y protege el umbral. Al revisar el commit de un compañero se verificó con la build real que
> **no era cierto**: `<include>com.aguavigia.ctg.domain.*</include>` no incluye el paquete
> `domain` mismo, solo subpaquetes de un nivel — la regla no evaluaba nada real (`BUG-096`). Se
> corrigió el mismo día. El cierre de este sprint **sigue siendo válido**: la cobertura real
> siempre superó el 70% que pide `RNF017` (medida a mano, no por la build), solo que la build no
> lo hacía cumplir hasta la corrección.

---

## 1. Objetivo del sprint

**El backend mantiene una cobertura de pruebas que hace confiable cualquier cambio: `domain/` y
`application/` por encima del umbral que exige `RNF017`, verificado en cada build, no de forma
manual.**

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| RNF017 | ✅ Entregado — cobertura real de `domain/` y `application/` ≥85% (90,2% / 97,6% el 2026-09-22); `jacoco:check` en `pom.xml` la exige desde `BUG-096` (antes no evaluaba nada) | 825 pruebas construidas a lo largo de los Sprints 0–4 |

La columna **Depende de** es la importante: es donde se ve qué tiene que existir antes.

**Fuera de este sprint, retirado por alcance (`ADR-048`):** RNF012–RNF016 (contraste, teclado,
objetivos táctiles, responsive, no-solo-color) y el E2E de `RF001`–`RF028` — los seis dependen de una
interfaz que hoy no existe en el repositorio.

---

## 3. Obstáculos del sprint — resumen

No registrados, mismo motivo que `sprint-3.md` §3.

---

## 4. Review — qué se demostró funcionando

**Verificación del 2026-09-22:**

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|
| RNF017 | `backend/pom.xml`: regla `jacoco:check` con `<minimum>0.85</minimum>`, corregida el 2026-09-22 (`BUG-096`) para que evalúe `domain/` y `application/` de verdad; medición real 90,2% / 97,6%, build completa con Docker: 825 pruebas, 0 fallos | ✅ |

**Comprometido:** 1 · **Entregado:** 1 · **Arrastrado al siguiente sprint:** —

---

## 5. Métricas del sprint

| Métrica | Valor (medido 2026-09-22) |
|---|---|
| Cobertura `domain/` + `application/` | 90,2% / 97,6%; ≥85% exigido en cada build desde `BUG-096` |
| Build en verde al cierre | Sí — `main` en verde en sus tres workflows |

---

## 6. Retrospectiva

No se llena — mismo motivo que `sprint-3.md` §6.
