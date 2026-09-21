# Pruebas de carga

Scripts para medir cuánta carga aguanta el backend. Contexto y resultados en
[`docs/ingenieria/escalabilidad.md`](../../docs/ingenieria/escalabilidad.md).

| Script | Qué mide | Herramienta |
|---|---|---|
| `lectura-publica.js` | Lecturas públicas (`/api/sectores`, `/estadisticas`, `/cumplimiento`, `/bitacora`) a tasa creciente. | k6 |
| `escritura-reportes.js` | `POST /api/reportes` repartido y un pico concentrado en un sector (avería masiva). | k6 |
| `rnf002-registrar-reporte.js` | RNF002: confirmar un reporte en menos de 1 s. | k6 |
| `sse-conexiones.mjs` | Conexiones SSE simultáneas, latencia al primer evento, latidos. | Node |
| `verificar-cache-proxy.mjs` | Que la micro-caché de nginx funcione con el backend real (MISS→HIT, un solo `Cache-Control`, 404 sin cachear, `Authorization` con `BYPASS`). Ejecutarlo tras tocar `infra/nginx/` (`BUG-085`). | Node |

## Sin instalar k6

La imagen de k6 sirve igual (con `--network host` en Linux; en Docker Desktop usa
`host.docker.internal` como `BASE_URL`):

```bash
docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e TASA=300 \
    grafana/k6 run - < scripts/carga/lectura-publica.js
```

## Antes de medir

1. Levantar el stack y sembrar datos: `docker compose up -d --build --wait` y
   `node scripts/sembrar-sectores.mjs`.
2. **Vaciar el rate limit por IP** para las pruebas de escritura (`aguavigia.rate-limit.reglas` vacío): k6
   sale desde una sola IP y mediría el `429` del limitador, no la latencia. Nunca en producción.
3. Decidir **a qué se apunta**:
   - al backend directo (`:8081`): mide el backend sin la caché de nginx (la prueba dura);
   - al proxy de producción (`:80`): mide el conjunto, pero nginx limita a 30 peticiones/s por IP y k6 sale
     de una sola IP; para tasas altas hay que repartir la generación de carga entre máquinas.

## Trampas del banco de pruebas local (Docker Desktop, Windows)

- **k6 en Docker contra `host.docker.internal` o contra un puerto publicado se satura en ~1 200 req/s**
  (reenvío de puertos de Docker Desktop): p95 de segundos y miles de iteraciones descartadas que **no son
  del sistema**. Para medir el proxy, ejecuta k6 **en la misma red Docker** que nginx
  (`--network <red> -e BASE_URL=http://<contenedor>`); así se midieron ~3 700 req/s por un nginx.
- A tasas altas contra el **backend directo** aparecen ráfagas de `dial: i/o timeout` (~0,03 %) por esa
  misma capa; la latencia de lo completado sigue siendo buena. No se aisló la causa.
- El `limit_req` de 30/s por IP de nginx hay que comentarlo en una **copia** de la configuración para la
  prueba (todo sale de una IP). Nunca en producción.

## SSE

```bash
node scripts/carga/sse-conexiones.mjs --conexiones 2000 --rampa 200 --duracion 60
```

Desde una sola máquina el límite lo pone el cliente (puertos efímeros y descriptores de archivo), no el
servidor: el resultado es «hasta dónde llegó el cliente», no el techo del backend.

## Qué no cubren

Las pruebas de resiliencia (matar una réplica, *failover* de Mongo o Redis bajo carga, reconexión masiva)
se hacen a mano; el procedimiento está en `docs/ingenieria/escalabilidad.md`.
