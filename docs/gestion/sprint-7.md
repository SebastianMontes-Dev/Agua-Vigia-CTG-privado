# Sprint 7 — Frontend nuevo

**Abierto:** 2026-09-25 · **Cerrado:** — *(cuando la SPA se levanta con el mismo `docker compose` que el backend y una persona responde «¿tengo agua o no, y hasta cuándo?» en menos de 5 s desde un celular en 3G, sin registrarse: F6 del plan)*

> Abierto por decisión del dueño el 2026-09-25 (`plan-frontend.md` §13, punto 1). Las fases, sus criterios de
> terminado y su verificación viven en [`plan-frontend.md`](../ingenieria/plan-frontend.md) §10; aquí solo se marca
> el avance.

---

## 1. Objetivo del sprint

**Un vecino de Cartagena abre la app en su celular, sin registrarse, y sabe en menos de 5 segundos si tiene agua y
hasta cuándo; puede reportar en dos toques, consultar la historia pública y suscribirse, y el veedor trabaja desde
su panel.**

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| — | ✅ F0 — Decisión y andamiaje: `ADR-067`, `frontend/`, cliente tipado con `api:check`, `frontend-ci.yml` en verde (PR #54, fusionado el 2026-09-25) | Plan aprobado por el dueño |
| — | 🟡 F1 — Prototipos y lenguaje visual, sin código de producción. Hecho el 2026-09-25: prototipos de 4 pantallas, glifos de estado en SVG, contraste AA medido (`REC-019`), glifos locales (`ADR-068`) y guía integral aprobada como especificación documental (`ADR-069`) y su paso 1, fundamentos visuales: acento claro `#06747f` aplicado y verificado en ambos temas (`REC-019` resuelta); paso 2, prototipos adaptados a la guía, rechazados por genéricos; rediseño total con identidad formal (`ADR-070`, `docs/diseno/identidad.md`, skills `disenar-frontend` y `revisar-diseno`). Falta: la aprobación visual del rumbo formal, la migración de tokens y fuentes y el extracto PMTiles | Red del entorno con acceso a `build.protomaps.com` para el extracto |
| RF001–RF008, RF037, RF038 | F2 — Núcleo ciudadano: mapa, tarjeta, ficha, lista, buscador, SSE y reporte en 2 toques | F1 aprobado |
| RF011, RF020–RF028 | F3 — Historia pública: bitácora, cumplimiento y estadísticas | F2 |
| RF012–RF015, RF041 | F4 — Avisos, con los enlaces de los correos apuntando a la SPA | F2 · URL de los correos decidida (`plan-frontend.md` §13.3) |
| RF016–RF019, RF042–RF046 | F5 — Cuentas y panel del veedor | F2 |
| RNF001, RNF012–RNF016, RNF020 | F6 — Integración: nginx sirve la SPA, un solo `docker compose`, Lighthouse y axe | F2–F5 |

---

## 3. Obstáculos del sprint — resumen

| Qué detuvo el avance | De qué dependía | Días | Cómo se resolvió |
|---|---|---|---|
| El extracto PMTiles no se puede descargar desde la sesión en la nube: la política de red deniega `build.protomaps.com` | Ampliar la red del entorno o correr `scripts/preparar-mapa-base.sh pmtiles` en la máquina del dueño | — | Abierto |

---

## 4. Review — qué se demostró funcionando

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|

**Comprometido:** — · **Entregado:** — · **Arrastrado al siguiente sprint:** —

---

## 5. Métricas del sprint

| Métrica | Valor |
|---|---|
| Requisitos entregados / comprometidos | |
| PRs fusionados | |
| Bugs abiertos / cerrados | |
| Cobertura `domain/` + `application/` | |
| Build en verde al cierre | |

---

## 6. Retrospectiva

*(Se llena al cerrar.)*
