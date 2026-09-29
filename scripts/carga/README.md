# Pruebas de carga

Scripts para medir cuánta carga aguanta el backend en el banco local (un solo PC, `ADR-080`). Contexto y resultados en
[`docs/ingenieria/escalabilidad.md`](../../docs/ingenieria/escalabilidad.md).

| Script | Qué mide | Herramienta |
|---|---|---|
| `lectura-publica.js` | Lecturas públicas (`/api/sectores`, `/estadisticas`, `/cumplimiento`, `/bitacora`) a tasa creciente. | k6 |
| `escritura-reportes.js` | `POST /api/reportes` repartido y un pico concentrado en un sector (avería masiva). | k6 |
| `rnf002-registrar-reporte.js` | RNF002: confirmar un reporte en menos de 1 s. | k6 |
| `sse-conexiones.mjs` | Conexiones SSE simultáneas, latencia al primer evento, latidos. | Node |

k6 no hace falta instalarlo: se usa la imagen `grafana/k6`.

## Antes de medir

1. Levantar el stack y sembrar datos: `docker compose up -d --build --wait` y `node scripts/sembrar-sectores.mjs`.
2. **Hacer una copia de Mongo** (`scripts/backup-mongo.sh`): las escrituras dejan decenas de miles de reportes.
3. **Vaciar el rate limit por IP** para las pruebas de escritura (`aguavigia.rate-limit.reglas` vacío): k6 sale desde una
   sola IP y mediría el `429` del limitador, no la latencia.

## Generar la carga dentro de la red de Docker

En Docker Desktop (Windows) el reenvío de puertos del host se satura en ~1 200 req/s y resetea conexiones con miles de SSE
abiertas: esos errores **no son del backend** (medido el 2026-09-29: 0 errores en 172 750 peticiones desde dentro de la red).
Por eso k6 y el cliente SSE corren en contenedores conectados a la red del compose y apuntan a `http://backend:8080`:

```bash
MSYS_NO_PATHCONV=1 docker run --rm -i --network agua-vigia-ctg_aguavigia \
    -e BASE_URL=http://backend:8080 -e TASA=300 \
    grafana/k6 run - < scripts/carga/lectura-publica.js
```

El nombre de la red es `<proyecto>_aguavigia` (`docker network ls` lo muestra).

## Trampas del banco local (Docker Desktop, Windows)

- En Git Bash, Docker recibe rutas mal traducidas (`/tmp/x` se vuelve `C:/Program Files/Git/tmp/x`): antepón
  `MSYS_NO_PATHCONV=1` a los `docker run`/`docker exec` que llevan rutas del contenedor. **Pero no lo exportes** en la misma
  sesión desde la que llamas a `curl`: con él, `curl -o /dev/null` falla al escribir (código 23) aunque la respuesta sea 200.
- `docker stats --no-stream a b c` no imprime **nada** si alguno de esos contenedores aún no existe.
- El generador de carga y el backend comparten la CPU: los números dicen el orden de magnitud, no la capacidad de un servidor.

## SSE

```bash
node scripts/carga/sse-conexiones.mjs --conexiones 2000 --rampa 200 --duracion 60
```

Desde una sola máquina el límite lo pone el cliente (puertos efímeros y descriptores de archivo), no el servidor: el
resultado es «hasta dónde llegó el cliente», no el techo del backend.
