# Bitácora, estadísticas y cumplimiento

La parte de **rendición de cuentas**: qué pasó, con qué frecuencia y, sobre todo, **si Acuacar cumple lo
que promete**. Todo público, sin sesión.

## Rutas

| Método y ruta | Qué devuelve | Caché |
|---|---|---|
| `GET /api/bitacora?pagina&tamano` | Eventos, más recientes primero. Paginado. | 5 s |
| `GET /api/estadisticas` | Sectores más afectados, cortes por día de la semana, duración media. | 5 s |
| `GET /api/estadisticas/exportar.csv` | Lo mismo en CSV. | — |
| `GET /api/cumplimiento` | Índice de Cumplimiento **global**. | 5 s |
| `GET /api/cumplimiento/sectores/{id}` | El de un sector. | 5 s |
| `GET /api/cumplimiento/cortes/{id}` | El de **un corte** concreto. | 5 s |
| `GET /api/cumplimiento/serie?sectorId&desde&hasta` | Evolución **mensual**. | 5 s |
| `GET /api/cumplimiento/serie.csv` | La serie en CSV. | — |

Esquemas en [`referencia-de-rutas.md`](referencia-de-rutas.md).

## El Índice de Cumplimiento

**Es el diferencial del proyecto.** Compara la duración *prometida* de un corte con la *real*.

```
porcentajeCumplimiento = duración prometida × 100 / duración real       (con tope 100)
desviacionSegundos      = duración real − duración prometida
```

```json
{ "sectorId": "manga", "duracionPrometidaSegundos": 28800, "duracionRealSegundos": 43200,
  "desviacionSegundos": 14400, "porcentajeCumplimiento": 66.67 }
```

- Un corte que dura **lo prometido o menos** da 100 % (el tope). Uno que dura el doble, 50 %.
- **Solo cuentan los cortes cerrados** (con hora real de fin). Un corte abierto todavía no se puede medir.
- El agregado global suma **duraciones**, no promedia porcentajes: un corte largo pesa más que uno corto.
- `sectorId` es nulo en el índice global.

### Errores

| Código | Cuándo |
|---|---|
| `400` | No hay cortes cerrados (globalmente o para ese sector): **no hay nada que medir**. Muéstralo como «aún sin datos», no como error. |
| `404` | `GET /cumplimiento/cortes/{id}` con un corte inexistente. |
| `409` | `GET /cumplimiento/cortes/{id}` de un corte **todavía abierto**. |

### La serie mensual

`GET /api/cumplimiento/serie?sectorId=manga&desde=2026-01-01T00:00:00Z&hasta=2026-08-31T23:59:59Z`

```json
[ { "periodo": "2026-07", "duracionPrometidaSegundos": 86400, "duracionRealSegundos": 108000,
    "desviacionSegundos": 21600, "porcentajeCumplimiento": 80.0, "cantidadCortes": 3 } ]
```

- `periodo` es `AAAA-MM` **en hora de Cartagena**, y ordena cronológicamente como texto.
- **`cantidadCortes` importa**: un 40 % sobre un solo corte y uno sobre veinte no significan lo mismo.
  Muéstralo (por ejemplo, con un punto más grande o un texto «sobre 3 cortes»).
- `sectorId`, `desde` y `hasta` son opcionales; sin ellos, es la serie global.

## La bitácora

Un registro **de solo anexado** (RF026–RF028): nada se edita ni se borra. Es la memoria pública del
sistema.

`GET /api/bitacora?pagina=0&tamano=20` devuelve un **arreglo** de eventos. La paginación viaja en
cabeceras (ver [Errores y límites §Paginación](errores-y-limites.md#paginación)).

```json
{
  "id": "…", "tipo": "CORTE_CONFIRMADO_POR_CIUDADANOS", "sectorId": "manga", "corteId": null,
  "timestamp": "2026-08-08T15:30:00Z",
  "descripcion": "3 reportes ciudadanos independientes confirmaron SIN_SERVICIO en 'manga'",
  "estado": "SIN_SERVICIO", "urlOriginal": null, "imagenUrl": null,
  "cantidadReportesSustento": 3
}
```

| `tipo` | Origen |
|---|---|
| `CORTE_ANUNCIADO` | Un veedor registró un corte oficial. |
| `CORTE_RESTABLECIDO` | Se cerró un corte. |
| `CORTE_CONFIRMADO_POR_CIUDADANOS` | El consenso de reportes cambió el estado. Trae `cantidadReportesSustento`. |
| `CORTE_DETECTADO_POR_INGESTA` | Un boletín de Acuacar detectado y aprobado. Trae `urlOriginal`. |

Campos que **pueden ser nulos** y que la interfaz debe tolerar: `sectorId`, `corteId`, `estado`,
`urlOriginal`, `imagenUrl`. `cantidadReportesSustento` nunca es nulo (0 si el evento no es de consenso).

- **`estado`** permite darle color y filtro al evento. Un evento con `estado: null` es **informativo**:
  píntalo neutro, sin color de estado.
- **`urlOriginal` e `imagenUrl`**: cuando el evento nace de un boletín de Acuacar, enlazar la fuente es
  parte de la credibilidad del proyecto (`ADR-006`). **Muéstrala.**
- **`imagenUrl` apunta a `acuacar.com`**, que bloquea el uso de sus imágenes desde otros dominios
  (*hotlinking*): la misma imagen responde `200` sin `Referer` y `403` con uno ajeno. En producción, el
  proxy del proyecto las sirve como propias: sustituye `https://www.acuacar.com/wp-content/uploads/` por
  **`/acuacar-media/`** en la URL. Sin ese proxy, las imágenes no cargarán en el navegador.
- **`cantidadReportesSustento`** dice cuántos reportes sostuvieron el cambio. **Los ids no vienen en el listado**
  (en una avería grande pueden ser miles y una página llegó a pesar 205 KB): se piden con
  `GET /api/bitacora/{id}/sustento`, ver abajo.

### Los reportes que sustentan un evento (RF011)

`GET /api/bitacora/{id}/sustento?pagina=0&tamano=50` devuelve un **arreglo de ids de reportes** (los que
sostuvieron el cambio de estado de ese evento), con las mismas cabeceras de paginación que el listado
(por defecto 50, máximo 200). Pídelo **solo si el usuario abre el detalle** del evento. Una página fuera de
rango devuelve `[]`; un `id` de evento inexistente, `404`. Los ids sirven para que un veedor los cruce con
el panel: no hay ruta pública que devuelva el contenido de un reporte.

## Estadísticas

```json
{ "sectoresMasAfectados": [ { "…": "ver referencia-de-rutas.md" } ],
  "cortesPorDiaDeSemana": { "Lunes": 4, "Martes": 7, "Miércoles": 0, "Jueves": 2, "Viernes": 1, "Sábado": 0, "Domingo": 3 },
  "duracionPromedioHoras": 9.4 }
```

`cortesPorDiaDeSemana` trae **siempre los 7 días** (con 0 si no hubo cortes), con el nombre **en español y
con inicial mayúscula** como clave. El día se decide en hora de Cartagena. El orden de las claves del JSON no
está garantizado: ordénalas tú.

`exportar.csv` usa `;` como separador y va con BOM UTF-8 (para que Excel lo abra bien). Es una descarga
directa: enlázala con un `<a href download>`, no la pidas por `fetch`.
