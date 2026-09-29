# Escalabilidad — cuánta carga aguanta el backend en el banco local

Requisito: **`RNF027`** (usuarios simultáneos). Decisiones: `ADR-049` (arquitectura; en parte reemplazada por `ADR-080`),
`ADR-050`–`ADR-053`, `ADR-057` (proyecto académico, solo local) y `ADR-080` (se retiran nginx, el compose de producción y
el perfil `prod`). Guía para el frontend: [`docs/api/escalabilidad-para-el-cliente.md`](../api/escalabilidad-para-el-cliente.md).

## Alcance (léelo primero)

AguaVigía es un **proyecto académico que corre en un solo PC** (`ADR-057`, `ADR-080`): sin hosting, dominio, CDN, TLS,
réplicas ni presupuesto. Todo se levanta con `docker compose` (`docker-compose.yml`): **un backend**, Mongo como *replica
set* de un nodo, Redis y MailHog.

- Lo que se mide aquí es **el backend en un banco local**, con el generador de carga en la misma máquina. Los números
  sirven para ver el orden de magnitud y comparar antes/después, no son la capacidad de un servidor de producción.
- La infraestructura de despliegue que existió (nginx con micro-caché, varias réplicas, compose de producción) **se retiró**
  el 2026-09-29 y queda en la etiqueta git `pre-solo-local`. Las mediciones que se hicieron con ella se conservan abajo como
  antecedente, marcadas como tales.

## La arquitectura que corre (un backend)

Lo que sostiene la carga no es el hardware sino el diseño de `ADR-049`, que sigue vigente salvo la micro-caché de nginx:

| Pieza | Por qué | Dónde |
|---|---|---|
| SSE liviano: solo avisa (`{"actualizadoEn": …}`), difusión agrupada a 1/s, latido, tope de conexiones con `429` + `Retry-After` | El SSE anterior serializaba y enviaba ~25 KB a cada cliente en el hilo del evento | `SseSectoresBroadcaster` |
| Hilos virtuales, límites de Tomcat, tiempos de espera acotados de Mongo/Redis | Un origen lento no debe colgar todos los hilos; falla rápido (`503`) | `application.yml`, `MongoPoolConfig` |
| Caché en Redis tolerante a Redis caído; `@Cacheable(sync=true)` | Antes `GET /api/sectores` daba `500` si Redis caía; y la estampida tras expirar | `ManejadorDeErroresDeCache`, `SectorMongoAdapter` |
| Límite de frecuencia atómico (script Lua) y *fail-open* | `INCR`+`EXPIRE` no atómico: una clave sin TTL bloqueaba a una IP para siempre | `RateLimitingInterceptor` |
| Cerrojo para las tareas `@Scheduled` | Garantiza una sola ejecución por ciclo; el CI lo usa para apagar la ingesta | `EjecucionUnica`, 4 tareas |
| Consenso con *compare-and-set* | Dos POST simultáneos duplicaban el evento en la bitácora | `SectorMongoAdapter.cambiarEstadoSiEs` |
| **Consenso en O(1) por petición** (votos contados en Mongo, evaluación acotada a 1/s por sector, barrido) | Era O(reportes de la ventana) por POST y agotaba el pool (`BUG-086`) | `EvaluarConsensoService`, `ReservaDeEvaluacionPort` |
| Proyección sin polígonos; índices de la ruta caliente | Cada lectura de un sector traía ~0,7 MB de geometría; la cola de moderación y la bitácora recorrían colecciones enteras | `SectorMongoRepository`, `IndicesMongo` |

## Mediciones del backend directo (reproducibles hoy)

Banco del 2026-09-29: un solo PC (Ryzen 5 9600X, 12 hilos, 15 GB para Docker), **generador de carga dentro de la red de
Docker** (k6 y el cliente SSE en contenedores que hablan con `backend:8080`). Sin eso, el reenvío de puertos de Docker
Desktop falsea los resultados (ver [`scripts/carga/README.md`](../../scripts/carga/README.md)).

| Prueba | Resultado |
|---|---|
| Lectura pública, 1 000 req/s (172 750 peticiones) | 0 errores, p95 **4,5 ms**, p99 6,6 ms |
| Lectura pública, objetivo 3 000 req/s (media 2 251) | 0 errores, p95 9,2 ms, p99 23 ms; backend ≈ 3 núcleos, Mongo ≈ 2,5 |
| SSE, 10 000 conexiones | 10 000 abiertas, 0 rechazadas, primer evento p50 112 / p95 213 ms; backend ≈ 2 GB |
| `RNF002` (`rnf002-registrar-reporte.js`, 2026-09-24) | p95 32,6 ms, 0 % de errores, umbral de 1 s |

**Escritura de reportes** (`escritura-reportes.js`: 100 POST/s en 211 sectores + pico de 300/s sobre **un solo sector**):

| Versión | Errores | p95 | Observación |
|---|---|---|---|
| Antes de corregir | **35 %** (5 052 × `503`) | **8 s** | Pool de Mongo agotado; RNF002 (1 s) incumplido |
| Tras contar votos en Mongo | 4 % | 4,9 s | Mejor, pero el pico seguía arrastrando todo |
| **Tras acotar la evaluación por sector** | **0 %** (21 000 peticiones) | **15,6 ms** | — |
| 3× esa carga (300/s + pico de 900/s, 63 001 peticiones) | **0 %** | **36 ms** | 0 eventos duplicados en la bitácora; todos los de consenso con sus reportes de sustento (RF011) |

**Memoria del SSE:** ≈ 100 KB vivos por conexión (≈ 1,18 GB de heap con 10 000 abiertas). El tope por instancia lo fija
`aguavigia.sse.max-conexiones`.

## Antecedente: medición con nginx y 3 réplicas (2026-09-29, infraestructura retirada)

Se hizo con la infraestructura que `ADR-080` retiró (código en la etiqueta `pre-solo-local`). **No es reproducible con
el repo actual**; se conserva porque sus resultados y los defectos que encontró siguen siendo ciertos:

- **50 100 conexiones SSE** sostenidas 5 min sin perder ninguna, repartidas entre 3 réplicas por el *backplane* de Redis.
- Lectura por la micro-caché de nginx a 6 006 req/s de media, p95 1,5 ms, con el backend a ≈ 10 % de un núcleo.
- Escritura: 300 reportes/s + pico de 900/s, p95 23,7 ms, 0 errores.
- Todo a la vez: dentro de los umbrales hasta **25 100 SSE**; con 50 100 la latencia se salía de los umbrales (el
  generador, nginx y las 3 JVM compartían los mismos 12 hilos).
- Defectos que encontró, todos corregidos: `BUG-117` (cada desconexión SSE dejaba un `ERROR`), `BUG-118` (reparto de
  conexiones entre los workers de nginx) y `BUG-119` (la bitácora contaba toda la colección). Antes, `BUG-085` (la
  micro-caché de nginx no cacheaba).

## Base de datos

Medido con 84 000 reportes sintéticos (auditoría del 2026-09-21): las consultas de la ruta caliente usan índice (cupo por
dispositivo, sector por `slug`, suscripciones por sector, cortes cerrados, bitácora paginada: 0–2 ms). La cola de
moderación pasó de examinar 84 000 documentos (87 ms) a 20 (0 ms). La página 1 000 de la cola (~52 ms) es el coste de
paginar con `skip`, no del índice.

Resuelto desde entonces: la bitácora pública entrega solo el conteo de sustento (`ADR-055`); los reportes caducan a los
12 meses con un índice TTL (`ADR-058`); el respaldo manual (`scripts/backup-mongo.sh`) está comprobado.

## La ciudad entera reportando a la vez (demo de carga, 2026-09-29)

`scripts/carga/demo.mjs` (`ADR-083`) reproduce el flujo completo contra el backend real, no un endpoint aislado: N vecinos
reportan una vez cada uno (una parte con coordenada dentro de su barrio y una parte confirmada después), 12 barrios sufren una
avería masiva y el consenso real cambia sus estados, 150 lecturas por segundo del mapa, dos inicios de sesión de veedor por
segundo, una suscripción por segundo y, en paralelo, las conexiones SSE del mapa en vivo. Cada corrida arrancó del **mismo
punto de partida** (724 reportes, 141 eventos, 205 barrios sin datos, 30 004 cuentas) y se restauró al terminar.

| Escala | Reportes aceptados | p95 · p99 · máx de un reporte | Errores | Descartados por el generador | SSE abiertas | Backend (pico) |
|---|---|---|---|---|---|---|
| 10 000 reportes en 30 s + 10 000 conexiones | 10 000 | **39 ms** · 99 ms · 254 ms | 0 | 0 | 10 000 / 10 000 | 669 % CPU · 2,5 GiB |
| 30 000 reportes en 60 s + 30 000 conexiones (corrida 1) | 29 996 | **130 ms** · 323 ms · 614 ms | 0 | 0 | 30 000 / 30 000 | 779 % CPU · 6,3 GiB |
| 30 000 reportes en 60 s + 30 000 conexiones (corrida 2) | 30 000 | **131 ms** · 299 ms · 619 ms | 0 | 2 | 30 000 / 30 000 | 815 % CPU · 6,4 GiB |
| 30 000 reportes en 60 s + 30 000 conexiones (corrida 3, con `demo.mjs --restaurar` completo) | 29 999 | **164 ms** · 375 ms · 846 ms | 1 (`503`) | 0 | 30 000 / 30 000 | 845 % CPU · 6,3 GiB |

Con 30 000 conexiones abiertas, mientras hay cambios de estado se difunde un aviso por segundo a cada una: ≈ 1,6 millones de
avisos entregados en la ventana, el primer evento a las conexiones nuevas en p95 ≈ 1 s. `RNF002` (p95 < 1 s) se cumple con
holgura en todas las corridas.

El único error de las corridas a punto de partida conocido es un `503` («base de datos no disponible») en la corrida 3: una
petición de 30 000 esperó más de los 2 s que se da al pool de Mongo y el backend respondió rápido con el error, que es lo
diseñado (`aguavigia.mongo.espera-conexion-ms`, «falla rápido» en vez de encolar). El cliente reintenta; no se perdió ningún dato.

**Lo que hay que decir junto a estas cifras:**

- El generador de carga (k6 y los clientes SSE) y el backend comparten un PC de 12 hilos. El backend llega a ≈ 8 núcleos
  y ≈ 6,4 GiB con 30 000 conexiones: **es el techo de este equipo, no del diseño**, y no se probó más allá de 30 000
  conexiones con reportes a la vez. Los 50 000 de `RNF027` no se demuestran aquí.
- **No siempre da tan bien.** Antes de fijar el punto de partida, las mismas 30 000 sobre una base que ya acumulaba de 12 000 a
  42 000 reportes de corridas anteriores y con el mapa ya cambiado por esas corridas dieron p95 de 231 ms y de 788 ms (p99 hasta 1,5 s,
  máximo 3,2 s), con 190–249 iteraciones descartadas por el generador (0,6–0,8 %) y 2 errores en una de ellas. El
  descarte se atribuye a k6 y no al backend: solo había creado 343 de sus 6 000 VUs posibles (los crea bajo demanda y descarta
  lo que llega mientras tanto) y, al precargar 1,2 VUs por cada reporte por segundo, bajó a 0–2 descartadas.
  No se aisló la causa de la diferencia; coincide con más datos acumulados y otro estado del mapa, y por eso la demo se
  ensaya con `--restaurar`.
- La CPU no la gasta solo el canal en vivo: la misma carga de 30 000 reportes **sin** conexiones abiertas (sobre una base ya
  cargada, así que no es comparable al milímetro) también llevó al backend a ≈ 860 % de CPU, con p95 de 39 ms.
- La memoria del backend con 30 000 conexiones (≈ 6,4 GiB) no se desglosó entre memoria viva y heap sin recoger
  (`-XX:MaxRAMPercentage=75`); en la prueba de SSE de antes, 10 000 conexiones midieron ≈ 1,2 GB (≈ 100 KB cada una).

## Lo que no se probó

- **Resiliencia bajo carga** (Mongo o Redis caídos durante la prueba). El comportamiento con Redis caído está cubierto por
  pruebas, no por una prueba de carga.
- **Estampida de lecturas** tras un aviso SSE con decenas de miles de clientes contra el backend directo (sin micro-caché,
  la protege la caché de Redis con `sync=true`).
- **Métricas** (Prometheus) ni trazas: se observa con `docker stats` y los logs.

## Cómo repetir las mediciones

**La ciudad entera, con un comando** (respaldo, perfil `carga`, SSE, k6 con su panel en `localhost:5665`, resumen y vuelta atrás):

```bash
node scripts/carga/demo.mjs --usuarios 30000 --ventana 60 --conectados 30000 --restaurar
```

Guía y trampas del banco local en [`scripts/carga/README.md`](../../scripts/carga/README.md). Para las pruebas sueltas: vaciar
`aguavigia.rate-limit.reglas` en el backend de prueba (k6 sale desde una sola IP; el perfil `carga` lo hace), generar la carga
**dentro de la red de Docker** y hacer una copia de Mongo antes (`scripts/backup-mongo.sh`): las escrituras dejan decenas de miles de reportes.
