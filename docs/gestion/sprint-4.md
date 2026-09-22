# Sprint 4 — Ingesta y Cumplimiento ⭐

**Abierto:** no registrado (mismo motivo que `sprint-3.md`: el trabajo ocurrió mientras `docs/gestion/`
estaba retirado del proyecto, entre el 2026-08-12 y el 2026-09-17) · **Cerrado:** 2026-09-22 —
reconstruido retroactivamente contra el código y `docs/ingenieria/matriz-trazabilidad.md`. Ver la nota
de `sprint-3.md` sobre por qué se cierra así y no con una ceremonia inventada.

---

## 1. Objetivo del sprint

**Un boletín real de Acuacar entra solo al sistema, y el Índice de Cumplimiento de cada sector se
calcula comparando la duración prometida contra la real, sin intervención manual.**

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| RF020, RF021, RF022 | ✅ Entregado — `CalcularCumplimientoService` + `IndiceCumplimientoController` público en `/api/cumplimiento`; `ADR-022` decide agregar por suma de duraciones, no promedio de porcentajes; se presenta como comparación, no como puntaje | Dominio de M6 (Sprint 1, PR #21) |
| RF023, RF024 | ✅ Entregado — sectores más afectados con duración y frecuencia (`EstadisticasMongoAdapterTest`); evolución del índice en el tiempo (`GET /api/cumplimiento/serie`, `SerieMensualCumplimientoTest`) | `CalcularCumplimientoService` |
| RF027 | ✅ Entregado — `GET /api/bitacora` público, directo al puerto de salida sin caso de uso (`ADR-015`) | `RegistrarEventoBitacoraService` (Sprint 3) |
| RNF004, RNF005 | ✅ Entregado — un colector caído no tumba el ciclo (`PipelineOrquestadorTest`); `@Retry`/`@CircuitBreaker` abre tras 3 fallos consecutivos (`ResilienciaDeColectoresTest`) | `AcuacarApiCollector`, `RssCollector` (Sprints 1 y 3) |
| RNF007 | ✅ Entregado — salud por colector expuesta (`ColectorHealthIndicatorTest`, detalle autenticado en `GET /api/veedor/ingesta/salud`) | — |
| RF032–RF035 (clasificación IA) | ❌ Descartado — `ADR-025` elimina la dependencia del SDK de Anthropic; el pipeline queda sin capa de IA, extracción manual por el veedor | — |

La columna **Depende de** es la importante: es donde se ve qué tiene que existir antes. Se escribe
con el artefacto concreto que falta, no con una intención.

**Nota sobre `RNF006` (cola muerta de la ingesta):** aparece en `product-requirements.md` junto a
RNF004/RNF005, pero la matriz la clasifica en el Sprint 2, no en este — y al reverificarla el
2026-09-22 resultó **parcial**, no entregada (`BUG-091`): existe el reintento de un documento fallido,
no la cola muerta consultable con su motivo. No es compromiso de este sprint, pero queda anotado aquí
porque cualquiera que lea "ingesta resiliente" esperaría encontrarla.

---

## 3. Obstáculos del sprint — resumen

No registrados, mismo motivo que `sprint-3.md` §3.

---

## 4. Review — qué se demostró funcionando

**Verificación del 2026-09-22** (contra el código real de `main`):

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|
| RF020–RF022 | `CalcularCumplimientoService` agrega por suma de duraciones (`ADR-022`); `IndiceCumplimientoController` responde en `/api/cumplimiento` | ✅ |
| RF023, RF024 | `EstadisticasMongoAdapterTest`; `SerieMensualCumplimientoTest` para la evolución en el tiempo | ✅ |
| RF027 | `BitacoraController` público, sin autenticación | ✅ |
| RNF004, RNF005 | `PipelineOrquestadorTest.unColectorCaidoNoDebeImpedirQueSeLeaElOtro`; `ResilienciaDeColectoresTest.debeAbrirElCortacircuitosAlTercerFalloConsecutivo` | ✅ |
| RNF007 | `ColectorHealthIndicatorTest` | ✅ |
| RF032–RF035 | Descartado por decisión (`ADR-025`), no por incumplimiento | Descartado |

**Comprometido:** 6 (5 entregados + 1 descartado por decisión) · **Entregado:** 5 · **Descartado:** 1
· **Arrastrado al siguiente sprint:** —

---

## 5. Métricas del sprint

Mismo aviso que `sprint-3.md` §5: es el estado del proyecto el 2026-09-22, no una métrica tomada al
cierre real.

| Métrica | Valor (medido 2026-09-22, no al cierre real) |
|---|---|
| Cobertura `domain/` + `application/` | 92,4% / 99,2% (medido 2026-09-05, `RNF017`) |
| Build en verde al cierre | Sí — `main` en verde en sus tres workflows |
| Hallazgo de esta verificación | `BUG-091`: `RNF006` estaba sobreestimado en la matriz |

---

## 6. Retrospectiva

No se llena — mismo motivo que `sprint-3.md` §6.
