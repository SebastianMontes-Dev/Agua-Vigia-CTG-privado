# Consumir la API a escala

El requisito es que el sistema aguante **50 000 personas a la vez**. El servidor hace su parte (caché,
réplicas, límites: ver [`docs/ingenieria/escalabilidad.md`](../ingenieria/escalabilidad.md)), pero **el
frontend decide cuánta carga genera**. Estas reglas no son opcionales: un cliente que las ignore puede tumbar
un servicio que por lo demás aguanta.

## La regla de oro

**50 000 personas mirando el mapa deben costar casi lo mismo que 50.** Eso solo pasa si todas piden lo mismo
y eso se sirve de una caché. Por eso las lecturas públicas son idénticas para todos y llevan
`Cache-Control: public, max-age=5, stale-while-revalidate=30`.

## Qué pedir, cuándo y cada cuánto

| Dato | Ruta | Frecuencia recomendada | Notas |
|---|---|---|---|
| Polígonos | `GET /api/sectores/geometria` | **Una vez** y guardar | Pesa ~0,7 MB sin comprimir; el servidor la cachea un día. Guárdala en `IndexedDB`/caché del navegador; no la pidas en cada visita. |
| Estado de los sectores | `GET /api/sectores` | Al abrir, y luego **al recibir un aviso del SSE** (o cada 15–30 s si no usas SSE) | Es lo que más se pide. Nunca más de una vez cada 5 s. |
| Bitácora, estadísticas, cumplimiento | `GET /api/bitacora`, `/estadisticas`, `/cumplimiento…` | **Al entrar en esa pantalla** | Cambian poco. No los sondees en segundo plano. |
| Un sector | `GET /api/sectores/{id}` | Al abrir su ficha | Ya tienes su estado del listado; solo la pides si necesitas algo más. |

**Nunca** sondees en un bucle rápido, ni desde una pestaña en segundo plano: usa `document.visibilityState`
para pausar el sondeo cuando la pestaña está oculta.

## Tiempo real sin tumbar el servidor

1. **Prefiere el SSE al sondeo**: una conexión que avisa cuesta menos que mil peticiones que preguntan.
2. **Al recibir un aviso, no pidas de inmediato**: espera un retardo **aleatorio de 0 a 3 s**. Si 50 000
   clientes reciben el aviso a la vez y piden al instante, el pico es de 50 000 peticiones en un milisegundo.
   (El servidor las colapsa en una gracias a la caché, pero el jitter cuesta nada.)
3. **Reconecta con espera creciente y jitter**, no en bucle:

```js
let intentos = 0;
function conectar() {
  const fuente = new EventSource('/api/sectores/stream');
  fuente.addEventListener('sectores', () => setTimeout(cargarSectores, Math.random() * 3000));
  fuente.onopen = () => { intentos = 0; };
  fuente.onerror = () => {
    fuente.close();                       // EventSource reintentaría solo, pero sin tu jitter
    const espera = Math.min(60_000, 2 ** intentos++ * 1000) * (0.5 + Math.random());
    setTimeout(conectar, espera);
  };
}
```

4. **Si el servidor responde `429`** al abrir el SSE (tope de conexiones alcanzado), **no insistas**: espera
   `Retry-After` y mientras tanto sondea `GET /api/sectores` cada 30 s. La interfaz sigue funcionando.
5. **Cierra el SSE cuando la pestaña queda oculta** durante un rato y vuelve a abrirlo al volver: ahorras
   una conexión abierta por cada persona que dejó la pestaña olvidada.

## Reintentos

- Solo se reintenta **lo idempotente** (`GET`). **Nunca reintentes automáticamente un `POST /api/reportes`**:
  un reintento tras un timeout puede registrar el reporte dos veces (y gasta el cupo del dispositivo).
- Ante `429`: espera `Retry-After`. Ante `5xx`: espera creciente con jitter, máximo 3 intentos, y muestra un
  mensaje.
- Ante `503 base-de-datos-no-disponible`: el sistema está degradado; muestra lo último bueno que tengas en
  caché local, marcado como **«dato de hace N min»**.

## Sin conexión y datos viejos

El servicio tiene que ser útil justo cuando falla la red o el agua. Recomendaciones:

- Guarda la última respuesta de `GET /api/sectores` y muéstrala con su fecha (`generadoEn`) si no hay red.
- **Muestra siempre la frescura** del dato: `actualizadoEn` de cada sector y `generadoEn` del listado. Un
  dato de hace horas sin fecha es peor que ningún dato.
- Un *service worker* que cachee la geometría y el listado hace la aplicación instantánea la segunda vez.

## Reportar sin hacer daño

- **Desactiva el botón al enviar** hasta recibir respuesta (evita el doble toque).
- **Guarda un reporte pendiente** si no hay red y envíalo al volver la conexión — pero **con la misma
  huella** y una sola vez.
- Recuerda el cupo: 3 reportes por sector cada 30 min por dispositivo. Tras el tercero, muestra un mensaje
  amable en lugar del `429` en crudo, y no vuelvas a intentar.

## Imágenes

- Las fotos de reportes salen de `/fotos/<uuid>.<ext>` con `Cache-Control` de un día: usa `<img loading="lazy">`.
- Las portadas de los boletines (`imagenUrl` de la bitácora) apuntan a `acuacar.com` y **no cargan desde otro
  dominio**; sustituye el prefijo por `/acuacar-media/` (ver
  [Bitácora](bitacora-estadisticas-cumplimiento.md)).

## Lo que este documento no garantiza

Estas reglas reducen la carga que **tú** generas. Que el conjunto soporte 50 000 usuarios simultáneos también
depende de la infraestructura desplegada (réplicas, base de datos, CDN). El estado real de esa verificación,
con lo que se midió y lo que no, está en
[`docs/ingenieria/escalabilidad.md`](../ingenieria/escalabilidad.md).
