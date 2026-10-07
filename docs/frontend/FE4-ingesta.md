# FE4 · Ingesta

**Objetivo:** que el veedor revise lo que detectó la ingesta automática (boletines de Acuacar y prensa): aprobar, descartar
o anular cada propuesta, ver los documentos que no se pudieron procesar y saber si cada colector está sano.

Rama: `feat/fe4-ingesta`. Depende de FE2. Esfuerzo: 1–2 sesiones.

Contrato: [`panel-veedor.md` §Revisión de la ingesta](../api/panel-veedor.md#revisión-de-la-ingesta), F3 de
[`cambios-para-frontend.md`](../api/cambios-para-frontend.md) (`motivoDeRevision`, varias zonas, histórico).

## Pantalla `/panel/ingesta`

La fila ya está en [`guia-frontend.md` §5.4](../diseno/guia-frontend.md#54-panel-del-veedor).

| Sección | Permiso | Qué |
|---|---|---|
| Propuestas | `VER_PANEL` | `GET /api/veedor/ingesta/propuestas`, paginado. Cada una muestra:<br>- barrio y estado propuesto<br>- **cita textual del boletín y fuente con su enlace**<br>- ventana declarada<br>- `motivoDeRevision`: por qué no se publicó sola<br><br>Una cita o una fuente ausente se dice explícitamente. **Nunca se inventa evidencia**: es la regla 4 de ética de datos |
| Acciones | `REVISAR_INGESTA` | Aprobar, descartar, anular: `PATCH …/propuestas/{id}/aprobar`, `…/descartar`, `…/anulacion`. Las resueltas no tienen botones activos. Después de aprobar se refrescan los datos, porque aprobar no implica que el estado cambie |
| Documentos fallidos | `VER_PANEL` | `GET /api/veedor/ingesta/fallidos`. Distingue lista vacía, fallo del colector y metadatos disponibles. Solo se muestra lo que la respuesta trae |
| Salud | `VER_PANEL` | `GET /api/veedor/ingesta/salud`: estado de cada colector, último éxito y último error, en texto y glifo, no solo en color |

## Pruebas

- e2e simulado:
  - propuesta con y sin cita o fuente
  - aprobar, descartar y anular
  - resuelta sin acciones
  - fallidos vacío y con datos
  - salud con un colector caído
- e2e real:
  - con el backend de simulación (`backend-sim`), `POST /api/sim/boletines` inyecta un boletín poco fiable que espera en la cola. Se aprueba desde la interfaz y el corte aparece en la ficha pública
  - si no hay `backend-sim`, se usa la ingesta local de `docker compose up`

## Terminado cuando

- Pasa la puerta completa.
- Ninguna propuesta muestra una cita que no venga en la respuesta.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md, docs/frontend/FE4-ingesta.md y docs/api/panel-veedor.md.
En feat/fe4-ingesta desde main construye /panel/ingesta con propuestas, acciones por permiso, fallidos y salud.
Nunca muestres evidencia que la API no devuelva. Corre la puerta completa y muéstrame el resultado.
Abre el PR, no lo fusiones. Si usas subagentes, usa model sonnet.
```
