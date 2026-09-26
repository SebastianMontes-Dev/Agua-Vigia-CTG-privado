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
| — | 🟡 F1 — Prototipos y lenguaje visual, sin código de producción. Hecho el 2026-09-25: prototipos de 4 pantallas, glifos de estado en SVG, contraste AA medido (`REC-019`), glifos locales (`ADR-068`) y guía integral aprobada como especificación documental (`ADR-069`) y su paso 1, fundamentos visuales: acento claro `#06747f` aplicado y verificado en ambos temas (`REC-019` resuelta); paso 2, prototipos adaptados a la guía, rechazados por genéricos; rediseño total con identidad formal (`ADR-070`, `docs/diseno/identidad.md`, skills `disenar-frontend` y `revisar-diseno`); plan «identidad propia y respuestas claras» del dueño: identidad contenida (`ADR-071`), PMTiles con Git LFS (`ADR-072`) y API ampliada con filtros de la bitácora y `verificadoEn` (`ADR-073`, con pruebas, fusionado en PR #57). tokens y Newsreader migrados (`DESIGN.md`, `tokens.css`, pruebas y muestrario). prototipos de las ocho pantallas con la identidad contenida, a 390–1440 px y en ambos temas, publicados en un Artifact (`plan-frontend.md` §10). Extracto local listo, 5 132 380 bytes, SHA-256 registrado, estilo neutro y LFS. Falta: la revisión visual del dueño en un teléfono real | Red del entorno con acceso a `build.protomaps.com` para el extracto |
| RF001–RF008, RF037, RF038 | 🟡 F2 — Núcleo ciudadano: mapa, tarjeta, ficha, lista, buscador, SSE y reporte en 2 toques. Canal, mapa local y núcleo construido; geometría en IndexedDB, ficha, reportes, foto posterior y confirmación (ADR-074–076), E2E contra Docker, axe y capturas en ambos temas. Evidencia en docs/qa/f2/README.md. El dueño autorizó empezar F2 antes de revisar en teléfono; aprobación visual en el PR del núcleo ciudadano, antes de fusionarlo | F1 visual pendiente; decisión explícita del dueño |
| RF011, RF020–RF028 | F3 — Historia pública: bitácora, cumplimiento y estadísticas | F2 |
| RF012–RF015, RF041 | F4 — Avisos, con los enlaces de los correos apuntando a la SPA | F2 · URL de los correos decidida (`plan-frontend.md` §13.3) |
| RF016–RF019, RF042–RF046 | F5 — Cuentas y panel del veedor | F2 |
| RNF001, RNF012–RNF016, RNF020 | F6 — Integración: nginx sirve la SPA, un solo `docker compose`, Lighthouse y axe | F2–F5 |

---

## 3. Obstáculos del sprint — resumen

| Qué detuvo el avance | De qué dependía | Días | Cómo se resolvió |
|---|---|---|---|
| El extracto PMTiles no se puede descargar desde la sesión en la nube: la política de red deniega `build.protomaps.com` | Ampliar la red del entorno o correr `scripts/preparar-mapa-base.sh pmtiles` en la máquina del dueño | — | Resuelto en local el 2026-09-25; fuente oficial accesible |

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
