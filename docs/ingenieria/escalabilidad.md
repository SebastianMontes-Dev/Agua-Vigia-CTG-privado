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

## Lo que no se probó

- **Resiliencia bajo carga** (Mongo o Redis caídos durante la prueba). El comportamiento con Redis caído está cubierto por
  pruebas, no por una prueba de carga.
- **Estampida de lecturas** tras un aviso SSE con decenas de miles de clientes contra el backend directo (sin micro-caché,
  la protege la caché de Redis con `sync=true`).
- **Métricas** (Prometheus) ni trazas: se observa con `docker stats` y los logs.

## Cómo repetir las mediciones

Guía y trampas del banco local en [`scripts/carga/README.md`](../../scripts/carga/README.md). Resumen: vaciar
`aguavigia.rate-limit.reglas` en el backend de prueba (k6 sale desde una sola IP), generar la carga **dentro de la red de
Docker** y hacer una copia de Mongo antes (`scripts/backup-mongo.sh`): las escrituras dejan decenas de miles de reportes.
