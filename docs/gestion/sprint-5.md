# Sprint 5 — Calidad del backend

**Abierto:** no registrado (mismo motivo que `sprint-3.md` y `sprint-4.md`) · **Cerrado:** 2026-09-22 —
reconstruido retroactivamente. Ver la nota de `sprint-3.md` sobre por qué.

> **Este sprint no es el que originaba `docs/gestion/README.md`.** Su foco original era "Calidad,
> accesibilidad y PWA", con tres entregables: cobertura, auditoría WCAG AA y E2E en verde. Las dos
> últimas son de interfaz y `ADR-048` retiró el frontend del repositorio — no hay nada que auditar ni
> qué correr en Playwright. `docs/gestion/README.md` ya quedó redefinido a solo la parte de backend
> (`REC-017`). Cuando exista el frontend nuevo, su propia auditoría de accesibilidad y su propia suite
> E2E son trabajo de ese momento, no de este sprint reabierto.

---

## 1. Objetivo del sprint

**El backend mantiene una cobertura de pruebas que hace confiable cualquier cambio: `domain/` y
`application/` por encima del umbral que exige `RNF017`, verificado en cada build, no de forma
manual.**

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| RNF017 | ✅ Entregado — cobertura de `domain/` y `application/` ≥85%, exigida por `jacoco:check` en `pom.xml`: la build falla si baja del umbral | 823 pruebas construidas a lo largo de los Sprints 0–4 |

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
| RNF017 | `backend/pom.xml`: regla `jacoco:check` con `<minimum>0.85</minimum>`; la última medición puntual real fue 92,4% en `domain/` y 99,2% en `application/` (2026-09-05, sobre 406 pruebas; hoy son 823) | ✅ |

**Comprometido:** 1 · **Entregado:** 1 · **Arrastrado al siguiente sprint:** —

---

## 5. Métricas del sprint

| Métrica | Valor (medido 2026-09-22) |
|---|---|
| Cobertura `domain/` + `application/` | ≥85% exigido en cada build (`jacoco:check`); última medición puntual 92,4% / 99,2% |
| Build en verde al cierre | Sí — `main` en verde en sus tres workflows |

---

## 6. Retrospectiva

No se llena — mismo motivo que `sprint-3.md` §6.
