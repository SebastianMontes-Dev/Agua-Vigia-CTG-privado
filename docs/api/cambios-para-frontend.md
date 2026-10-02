# Cambios del backend que el frontend debe adaptar

El backend manda y el frontend se adapta (decisión del dueño, 2026-09-30). Cada fase añade aquí su sección: qué rompe, qué es
nuevo y qué debe cambiar el frontend. Sin periodo de compatibilidad: lo viejo se elimina.

## F0 — Base

### Rompe

| Cambio | Dónde | Qué adapta el frontend |
|---|---|---|
| `POST /api/reportes/{id}/foto` **rechaza WebP** con `400` (antes lo guardaba sin limpiar el EXIF, con la ubicación del teléfono) | `docs/api/reportes.md` | Quitar `image/webp` de `TIPOS_FOTO` (`frontend/src/api/reportes.ts`) y del texto de ayuda de `Reporte.tsx` («JPEG, PNG o WebP…» → «JPEG o PNG…») |

### Sin efecto en el contrato

- Los cortes que crea un boletín de Acuacar dejan de bloquear el retorno a «con servicio» cuando su ventana prometida vence.
  Cambia el estado que ve el mapa, no la forma de la API.

## F1 — Núcleo de estado (estado de un barrio, cierres por barrio, disputas)

El estado de un barrio lo decide **un solo resolutor** a partir de lo que afirma cada fuente (boletín de Acuacar, prensa
aprobada, corte o cierre del veedor, quórum de vecinos, sensores). Ya no hay procesos que se pisen: la forma del contrato
cambia porque ahora el barrio explica de dónde sale su estado.

### Rompe

| Cambio | Dónde | Qué adapta el frontend |
|---|---|---|
| `CorteRespuesta` **ya no trae `finReal`**. Trae `cierres[]` (`sectorId`, `hora`, `fuente`, `provisional`): un corte agrupa varios barrios y se restablece barrio por barrio | `GET/POST/PATCH /api/veedor/cortes…`, `GET /api/sectores/{id}/cortes` | Dibujar el cierre de cada barrio. Un corte `RESTABLECIDO` tiene todos sus barrios cerrados; la «hora real» del corte es la del último cierre. Un cierre `provisional: true` lo sostienen solo los vecinos (o, en teoría, sensores) y está pendiente de que el veedor lo confirme |
| `CorteRespuesta.estado` gana **`EXPIRADO`** (nadie confirmó el restablecimiento a tiempo; no cuenta en el Índice) y **`ANULADO`** (se publicó por error) | ídem | Tratarlos como cortes sin hora real. `ANULADO` trae `motivoAnulacion` |
| `GET /api/bitacora`: `tipo` gana `CORTE_EXPIRADO`, `CORTE_ANULADO` (también para un boletín anulado), `RESTABLECIMIENTO_POR_VECINOS`, `ESTADO_EN_DISPUTA`, `CONSENSO_REVERTIDO` | `docs/api/bitacora-estadisticas-cumplimiento.md` | Textos e iconos por tipo nuevo |
| `OrigenEstado` (en `origen` de sectores, `fuente` de eventos y de cierres) tiene 5 valores: `ACUACAR`, `PRENSA`, `VEEDOR`, `VECINOS`, `SENSOR`. **`SENSOR` existe en el contrato pero no se produce**: el proyecto no usa sensores físicos | varios | No asumir un conjunto cerrado de tres; no hace falta diseñar nada para `SENSOR` |
| El umbral de vecinos por barrio pasa a `clamp(ceil(población × 0,001), 3, 15)` | `respaldo.umbral` | Ninguno: es el denominador de «11 de 12»; ya no hay umbrales de 48 |

### Nuevo

- **`GET /api/sectores` y `/{id}`** ganan, siempre presentes: `origen`, `ventanaPrometida {inicio, fin}`,
  `restablecimientoPorConfirmar`, `enDisputa`, `reportesEnContra`, `respaldo {vecinos, umbral}`. Son nulos o falsos
  cuando el barrio no tiene estado. Insignias sugeridas: «Acuacar prometió hasta…», «por confirmar», «en disputa»,
  «11 de 12 vecinos». **El color no cambia por una disputa**: solo se marca.
- **`GET /api/bitacora`**: cada evento gana `fuente` (quién lo sostiene) y `respaldo {vecinos, umbral}` cuando nace de un quórum.
- **Panel del veedor** (`VER_PANEL` para leer, `GESTIONAR_CORTES` / `REVISAR_INGESTA` para actuar):
  - `GET /api/veedor/disputas`: barrios en disputa, los más contradichos primero (misma forma que `SectorRespuesta`).
  - `GET /api/veedor/cortes/vencidos`: cortes con la promesa vencida sin cierre, y los que tienen un cierre provisional.
  - `PATCH /api/veedor/cortes/{id}/sectores/{sectorId}/cierre` `{horaReal}`: cierra **un** barrio.
  - `PATCH /api/veedor/cortes/{id}/sectores/{sectorId}/confirmacion` `{horaReal}`: confirma —o corrige la hora de— un cierre provisional.
  - `PATCH /api/veedor/cortes/{id}/anulacion` `{motivo}` y `PATCH /api/veedor/ingesta/propuestas/{id}/anulacion` `{motivo}`:
    deshacen un corte o un boletín publicados por error. Quedan con su motivo, en la bitácora (como corrección) y en la auditoría.
  - `PATCH /api/veedor/cortes/{id}/cierre` **se conserva** como atajo: cierra de una vez todos los barrios pendientes.
  - `POST /api/veedor/cortes` acepta `caducaEn` (opcional): el corte del veedor es el override y deja de afirmar nada a esa hora.
- Los sensores de presión (`POST /api/iot/presion`) quedan construidos pero **inactivos**: el proyecto no los usa en físico y la ruta responde `503` mientras no se configure `X-IoT-Key`. No hay nada que mostrar ni que adaptar.

### Errores

`409` al cerrar o anular lo que ya estaba cerrado o anulado; `400` si falta `motivo` o `horaReal`; `404` si el corte o la propuesta no existen.
