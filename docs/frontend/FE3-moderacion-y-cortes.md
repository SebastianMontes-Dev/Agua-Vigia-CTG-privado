# FE3 · Moderación y cortes

**Objetivo:** el trabajo diario del veedor. Moderar los reportes con su foto, registrar y cerrar cortes barrio por barrio,
anular lo publicado por error, atender los cortes vencidos y ver las disputas.

Rama: `feat/fe3-moderacion-y-cortes`. Depende de FE2. Esfuerzo: 2–3 sesiones.

Contrato: [`panel-veedor.md`](../api/panel-veedor.md) (rutas y permisos, cortes, moderación) y las secciones F1–F4 de
[`cambios-para-frontend.md`](../api/cambios-para-frontend.md) (`verificacion`, `senalRed`, cierres, disputas).

## Pantallas

**`/panel/disputas` y la sección «Vencidos» se añaden primero a [`guia-frontend.md` §5.4](../diseno/guia-frontend.md#54-panel-del-veedor)**; las demás ya están.

### `/panel`: cola de moderación

| Permiso | Qué |
|---|---|
| `VER_PANEL` | `GET /api/veedor/reportes/pendientes`, los más antiguos primero, paginado por cabeceras. Cada reporte muestra:<br>- barrio, tipo y hora<br>- `verificacion` (ubicación verificada, cuenta verificada…)<br>- `senalRed`: «viene de una red con ráfaga de reportes; míralo primero». **Es una señal, no una acusación**<br>- la foto, desde `GET /api/veedor/fotos/{nombre}` con el token, porque las fotos sin aprobar no son públicas |
| `MODERAR_REPORTES` | Aprobar, descartar y **descartar solo la foto**: `PATCH …/aprobar`, `…/descartar`, `…/foto/descartar`. Con confirmación, la acción bloqueada durante el envío y sin doble envío |

### `/panel/cortes`

| Permiso | Qué |
|---|---|
| `VER_PANEL` | El selector de barrio es obligatorio: `GET /api/veedor/cortes?sectorId=`. El detalle es `GET …/cortes/{id}`, con sus `cierres[]` por barrio |
| `GESTIONAR_CORTES` | Las acciones del corte:<br>- **Alta:** `POST /api/veedor/cortes` con barrios, inicio, fin prometido y causa. Los campos usan `datetime-local` con la etiqueta «Hora de Cartagena» y conversión explícita<br>- **Cerrar todo:** `PATCH …/{id}/cierre`<br>- **Cerrar un barrio:** `PATCH …/{id}/sectores/{s}/cierre`<br>- **Confirmar el cierre provisional de un barrio** (lo pusieron los vecinos): `PATCH …/{id}/sectores/{s}/confirmacion`<br>- **Anular** con motivo: `PATCH …/{id}/anulacion` |
| `VER_PANEL` | **Sección «Vencidos»** (`GET …/cortes/vencidos`): cortes cuya promesa venció y que nadie cerró. Es la cola de trabajo que evita que expiren |

Los errores `409` (corte ya cerrado, ya anulado) se explican en la pantalla. No se ofrece cerrar cortes que el contrato no
admite.

### `/panel/disputas` *

Con `VER_PANEL`: `GET /api/veedor/disputas`, los barrios donde los vecinos contradicen a la fuente oficial. Cada uno lleva a
su ficha y a sus cortes.

## Pruebas

- e2e simulado:
  - cola vacía y con `senalRed`
  - aprobar, descartar y descartar foto
  - observador sin botones de acción
  - alta de un corte con validación de fechas
  - cierre parcial, confirmación y anulación
  - vencidos y disputas
- e2e real:
  - un reporte con foto creado por la parte pública aparece en la cola, se aprueba y su foto pasa a ser pública
  - un corte creado, cerrado por barrio y visible en la ficha pública

## Terminado cuando

- Pasa la puerta completa.
- Cada acción está oculta para quien no tiene el permiso. Se comprueba con un OBSERVADOR invitado en FE5, o sembrado si FE5 no está.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md, docs/frontend/FE3-moderacion-y-cortes.md y docs/api/panel-veedor.md.
En feat/fe3-moderacion-y-cortes desde main: añade a guia-frontend.md §5.4 las filas de disputas y vencidos, y construye
/panel (moderación), /panel/cortes (con vencidos) y /panel/disputas según la guía. Acciones solo con su permiso, sin doble envío,
fechas en hora de Cartagena. Corre la puerta completa y muéstrame el resultado. Abre el PR, no lo fusiones.
Si usas subagentes, usa model sonnet.
```
