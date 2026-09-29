# Pruebas de carga

Scripts para medir cuánta carga aguanta el backend en el banco local (un solo PC, `ADR-080`). Contexto y resultados en
[`docs/ingenieria/escalabilidad.md`](../../docs/ingenieria/escalabilidad.md).

**Para la presentación:** `demo.mjs` lo hace todo con un comando (ver abajo). Los demás miden una sola cosa.

| Script | Qué mide | Herramienta |
|---|---|---|
| `demo.mjs` | La ciudad entera a la vez: orquesta `flujo-ciudadano.js` y `sse-conexiones.mjs`, con respaldo, perfil `carga`, resumen y vuelta atrás. | Node + Docker |
| `flujo-ciudadano.js` | Reportes de miles de vecinos (con coordenada y confirmaciones), averías masivas en varios barrios, lecturas, inicios de sesión de veedores y suscripciones, todo a la vez. | k6 |
| `lectura-publica.js` | Lecturas públicas (`/api/sectores`, `/estadisticas`, `/cumplimiento`, `/bitacora`) a tasa creciente. | k6 |
| `escritura-reportes.js` | `POST /api/reportes` repartido y un pico concentrado en un sector (avería masiva). | k6 |
| `rnf002-registrar-reporte.js` | RNF002: confirmar un reporte en menos de 1 s. | k6 |
| `sse-conexiones.mjs` | Conexiones SSE simultáneas, latencia al primer evento, latidos. | Node |

k6 no hace falta instalarlo: se usa la imagen `grafana/k6`.

## La demo de carga (`demo.mjs`, `ADR-083`)

```bash
node scripts/carga/demo.mjs --usuarios 30000 --ventana 60 --conectados 30000 --restaurar
```

Requiere el stack levantado (`docker compose up -d --build --wait`), los sectores sembrados y, si no hay 30 000 cuentas, las siembra
sola (`scripts/sembrar-usuarios-demo.mjs`). Deja el informe HTML de k6, el `resumen.json` y el `resumen.txt` en
`resultados/<fecha>/` (ignorado por git) y muestra el panel en vivo de k6 en `http://localhost:5665`.

| Opción | Por defecto | Qué hace |
|---|---|---|
| `--usuarios` | 30000 | Reportes en total, uno por vecino |
| `--ventana` | 60 | Segundos en que llegan |
| `--conectados` | 30000 | Conexiones SSE simultáneas (0 = sin SSE); se reparten en contenedores de hasta 20 000 |
| `--focos` | 12 | Barrios con avería masiva que se encienden uno tras otro |
| `--lectores` / `--veedores` / `--suscripciones` | 150 / 2 / 1 | Por segundo |
| `--restaurar` | no | Al terminar, vuelve Mongo y Redis al estado de antes |
| `--esperar` | no | Se detiene antes de disparar para abrir el mapa y el panel de k6 |
| `--sin-respaldo` / `--sin-reinicio` / `--dejar-perfil` | no | Omiten el respaldo, el reinicio del backend o su vuelta al perfil normal |

El perfil `carga` (`docker-compose.carga.yml` + `application-carga.yml`) solo vacía el límite de peticiones por IP y sube los
topes de conexiones; el cupo por dispositivo de `RF006` y el consenso son los de siempre. Cifras medidas y sus límites en
[`docs/ingenieria/escalabilidad.md`](../../docs/ingenieria/escalabilidad.md).

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
