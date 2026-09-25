# Sectores y tiempo real

El mapa. Cartagena está dividida en **211 sectores** (barrios), sembrados desde el GeoJSON oficial. Cada
sector tiene un estado del servicio de agua, que puede ser desconocido.

## Rutas

| Método y ruta | Qué devuelve | Caché |
|---|---|---|
| `GET /api/sectores` | Los 211 sectores con su estado. | 5 s |
| `GET /api/sectores/{id}` | Un sector. `404` si no existe. | 5 s |
| `GET /api/sectores/geometria` | Los polígonos (GeoJSON `FeatureCollection`). | 1 día |
| `GET /api/sectores/stream` | Aviso en vivo de que algo cambió (SSE). | — |

Esquemas exactos en [`referencia-de-rutas.md`](referencia-de-rutas.md).

## `GET /api/sectores`

```json
{
  "sectores": [
    { "id": "bocagrande", "nombre": "BOCAGRANDE", "estado": "SIN_SERVICIO",
      "actualizadoEn": "2026-08-08T15:30:00Z", "verificadoEn": "2026-08-08T18:05:00Z" },
    { "id": "manga", "nombre": "MANGA", "estado": null, "actualizadoEn": null, "verificadoEn": null }
  ],
  "generadoEn": "2026-08-08T15:31:02Z"
}
```

- **`estado: null` es «sin datos»**, no «con servicio». Mientras nadie verifique nada de un sector, su
  estado es nulo, y con él `actualizadoEn` y `verificadoEn`. Pintarlo como normal sería afirmar algo que no sabemos.
- **`actualizadoEn` y `verificadoEn` son dos fechas distintas** (`ADR-073`). La primera es cuándo *cambió* el
  estado; la segunda, la última vez que el consenso de vecinos, un corte del veedor o un boletín aprobado lo
  *sostuvo*, aunque no cambiara. Nunca es anterior a `actualizadoEn`. Un barrio con servicio estable puede
  llevar días sin cambiar y estar verificado hace una hora: muestra las dos y calcula la advertencia de
  «Sin verificación reciente» (24 horas) sobre `verificadoEn`, **sin cambiar el estado publicado**.
- Verificar sin cambiar **no emite evento SSE** (no hay nada que avisar): `verificadoEn` se renueva cuando
  vuelves a pedir la lista, por un evento o al volver a la pestaña.
- Va ordenado por nombre.
- **`poblacion`** son los habitantes según el censo. **Es `null` cuando el barrio no tiene dato censal** (27 de los 211): no es 0; no lo muestres como «0 habitantes».
- **No trae la geometría.** Sale de `/geometria`.
- El histórico de cortes de un sector se pide aparte: `GET /api/sectores/{id}/cortes` (abajo).

## `GET /api/sectores/{id}/cortes` — histórico de cortes (RF002)

Público, sin sesión. Los cortes oficiales que afectaron al sector, **del más reciente al más antiguo**, abiertos y
cerrados. Es un arreglo de objetos con la misma forma que los del panel (`id`, `sectoresAfectados`, `inicio`,
`finPrometido`, `finReal`, `causa`, `origen`, `estado`; `finReal` es `null` mientras el corte sigue abierto).
Paginado por cabeceras (`?pagina=0&tamano=50`, máximo 200). Un sector sin cortes devuelve `[]`, no `404`; un
sector inexistente, `404`. Pídelo **al abrir la ficha del sector**, no en segundo plano.

## `GET /api/sectores/geometria`

`application/geo+json`. **No fuerces `Accept: application/json` en esta petición**: la ruta solo produce
`geo+json`, y con otro `Accept` el servidor la confunde con `GET /api/sectores/{id}` y responde un `404`
engañoso («No existe el sector 'geometria'»). `fetch` sin cabecera (`*/*`) funciona. Un `Feature` por sector; su **`id` es el mismo identificador** que devuelve el
listado, así que se unen sin calcular nada:

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": "bocagrande",
      "properties": { "nombre": "BOCAGRANDE" },
      "geometry": { "type": "Polygon", "coordinates": [[[-75.55, 10.40], "..."]] }
    }
  ]
}
```

- La geometría es `Polygon`, salvo **`zona-industrial`, que es `MultiPolygon`** (el único). Cualquier librería de mapas lo
  soporta, pero no supongas siempre un solo anillo.
- Pesa unos 0,7 MB sin comprimir y **solo cambia si se vuelve a sembrar**. El servidor la marca cacheable
  un día (`Cache-Control: public, max-age=86400`): pídela una vez y guárdala.
- Coordenadas en orden GeoJSON: **`[longitud, latitud]`**. Ojo: la API de reportes usa `latitud` y
  `longitud` con nombre, no en arreglo.

## Estados del servicio

| Valor | Significa | Regla de presentación |
|---|---|---|
| `CON_SERVICIO` | Servicio normal verificado. | |
| `SIN_SERVICIO` | Corte confirmado. | |
| `PRESION_BAJA` | Servicio degradado. | |
| `CORTE_PROGRAMADO` | Anunciado, aún no iniciado. | |
| `null` | **Sin dato verificado.** | «Sin datos». Nunca «con servicio». |

Los cuatro colores del estado están reservados para eso y nada más: ver [`DESIGN.md`](../../DESIGN.md) §2.

## Tiempo real: el SSE

`GET /api/sectores/stream` mantiene una conexión abierta (`text/event-stream`).

**Importante: el SSE ya no envía el estado.** Solo **avisa** de que algo cambió. Al recibir el aviso, el
cliente pide `GET /api/sectores` (que está cacheado). Así 50 000 personas conectadas no reciben cada una
25 KB por cada cambio.

Cada evento:

```
retry:4210
event:sectores
data:{"actualizadoEn":"2026-08-08T15:30:00Z"}

```

- **`event: sectores`** — hubo un cambio; `actualizadoEn` es cuándo.
- **`:latido`** — comentario cada 25 s para mantener viva la conexión. Se ignora.
- **`retry:`** — cuántos ms esperar antes de reconectar (entre 3 y 10 s, distinto por cliente). Un
  `EventSource` del navegador lo respeta solo.
- Al conectar llega **un primer evento** con la hora del último cambio conocido: úsalo para pedir el
  listado inicial.
- El servidor **agrupa los avisos** hasta 1 por segundo: una avería que cambie 40 sectores seguidos
  produce un solo aviso, no 40.
- La conexión se cierra cada ~10–12 minutos por diseño. Reconectar es normal.

### Usarlo bien

```js
const fuente = new EventSource('/api/sectores/stream');
fuente.addEventListener('sectores', () => {
  // Un retardo aleatorio evita que 50 000 clientes pidan en el mismo milisegundo.
  setTimeout(cargarSectores, Math.random() * 3000);
});
```

- **Reintenta con jitter** si el servidor responde `429` (se alcanzó el tope de conexiones en vivo): la
  respuesta trae `Retry-After`. Mientras tanto, cae a **sondeo cada 15–30 s** de `GET /api/sectores`, que
  es perfectamente válido.
- **El SSE es opcional.** Una interfaz que solo sondee cada 15 s con `If-None-Match`/caché funciona y cuesta
  menos. Úsalo si la frescura de segundos importa.
- **Detrás de un proxy**, el SSE necesita que el proxy no acumule la respuesta (`proxy_buffering off`). El
  nginx del proyecto ya lo hace.

## Errores

`404` con `type: recurso-no-encontrado` si el `id` no existe. `503` con `type:
base-de-datos-no-disponible` si Mongo no responde. Detalle: [Errores y límites](errores-y-limites.md).
