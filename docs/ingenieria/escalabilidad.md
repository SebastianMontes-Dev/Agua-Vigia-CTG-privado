# Escalabilidad — la meta de 50 000 usuarios simultáneos

Requisito: **`RNF027`**, «el backend debe soportar como mínimo 50 000 usuarios simultáneos». Decisiones:
`ADR-049` (arquitectura), `ADR-050`–`ADR-053` (`ADR-053`: consenso acotado y caché del proxy). Guía para el frontend: [`docs/api/escalabilidad-para-el-cliente.md`](../api/escalabilidad-para-el-cliente.md).

## Estado, sin adornos

**La meta NO está demostrada.** El diseño y el código están preparados para ella y se midió a escala
reducida (una sola máquina, un solo backend), con resultados buenos y con dos defectos graves que **solo
apareció al medir**. Lo que falta para poder afirmar «soporta 50 000» es un despliegue con réplicas y una prueba
de carga distribuida: ver [Lo que no se probó](#lo-que-no-se-probó) y [Pendiente](#pendiente-antes-de-afirmar-50-000).

## Qué significa «50 000 simultáneos» (supuestos)

El requisito no dice qué hace cada persona. Se asume, y conviene confirmarlo con quien lo pidió:

| Concepto | Supuesto | Consecuencia |
|---|---|---|
| Personas con el mapa abierto a la vez | 50 000 | 50 000 conexiones SSE si todas usan el canal en vivo |
| Lecturas por persona | 1 cada 10–30 s (al abrir, al recibir un aviso, al navegar) | **1 700 – 5 000 lecturas/s** en total |
| Peso medio de una respuesta | ~11 KB (medido, con gzip) | 20–55 MB/s de salida (**160–440 Mbit/s**) |
| Personas que reportan | 1–2 % en una avería masiva | decenas a cientos de escrituras/s, concentradas en pocos sectores |

Las lecturas son idénticas para todos, así que se sirven de una caché. Las escrituras no se pueden cachear:
son el punto delicado (sección «Lo que la medición encontró»).

## Arquitectura objetivo

```
personas ──► CDN (opcional, recomendable) ──► nginx ×2–3 (micro-caché, límites) ──► backend ×N ──► MongoDB (réplica de 3)
                                                                                          └────────► Redis (+ réplica)
```

- **nginx** (`infra/nginx/nginx.conf`, `nginx-main.conf`): micro-caché de 5 s en las lecturas públicas
  (`proxy_cache_lock`: una petición por entrada expirada llega al backend; `use_stale`: si el backend cae se sirve lo
  último bueno), `Authorization` salta la caché, `limit_req`/`limit_conn` por IP, 16 384 conexiones por *worker*.
- **Backend**: sin estado (JWT), hilos virtuales, réplicas con `BACKEND_REPLICAS` (por defecto 2 en
  `docker-compose.prod.yml`), *sondas* de liveness/readiness que no dependen de fuentes externas ni del correo.
- **SSE**: el canal solo **avisa** (`{"actualizadoEn": …}`); el cliente pide el estado a la ruta cacheada. Difusión
  agrupada (1/s), latido cada 25 s, tope de conexiones por instancia (`aguavigia.sse.max-conexiones`, 20 000 →
  `429` + `Retry-After`), *jitter* en el reintento.
- **Redis**: caché, límite de frecuencia atómico (script Lua, tolerante a que Redis caiga), ventana de consenso,
  *backplane* del SSE, cerrojos de tareas programadas (`EjecucionUnica`) y reserva de evaluación del consenso.
  Con `maxmemory 512mb` y `noeviction`.

## Qué cambió y por qué

| Cambio | Por qué | Dónde |
|---|---|---|
| Micro-caché HTTP en nginx | Convierte 50 000 lecturas en unas pocas al backend | `infra/nginx/nginx.conf` |
| SSE liviano (aviso, no estado), tope, latido, difusión agrupada | El SSE anterior serializaba y enviaba ~25 KB a cada cliente en el hilo del evento | `SseSectoresBroadcaster` |
| Hilos virtuales, límites de Tomcat, tiempos de espera acotados de Mongo/Redis | Un origen lento no debe colgar todos los hilos; falla rápido (`503`) | `application.yml`, `MongoPoolConfig` |
| Caché tolerante a Redis caído; `@Cacheable(sync=true)` | Antes `GET /api/sectores` daba `500` si Redis caía; y la estampida tras expirar | `ManejadorDeErroresDeCache`, `SectorMongoAdapter` |
| Límite de frecuencia atómico y *fail-open* | `INCR`+`EXPIRE` no atómico: una clave sin TTL bloqueaba a una IP para siempre | `RateLimitingInterceptor` |
| Cerrojo para las tareas `@Scheduled` | Con N réplicas la ingesta corría N veces | `EjecucionUnica`, 4 tareas |
| Consenso con *compare-and-set* | Dos POST simultáneos duplicaban el evento en la bitácora | `SectorMongoAdapter.cambiarEstadoSiEs` |
| Proyección sin polígonos; índice `finReal` | Cada lectura de un sector traía ~0,7 MB de geometría | `SectorMongoRepository`, `IndicesMongo` |
| **Consenso en O(1) por petición** (votos contados en Mongo, evaluación acotada a 1/s por sector, barrido) | Ver abajo: era O(reportes de la ventana) por POST y agotaba el pool | `EvaluarConsensoService`, `ReservaDeEvaluacionPort` |
| Índice `sectorId+huella+timestamp` | El cupo por dispositivo (RF006) recorría toda la ventana del sector | `IndicesMongo` |
| Producción: réplicas, `read_only`, límites, `noeviction` | — | `docker-compose.prod.yml` |

## Lo que se midió

**Banco de pruebas** (2026-09-21): un solo equipo (Ryzen 5 9600X, 12 hilos, 33 GB, Windows 11) donde corren **a la
vez** el generador de carga (k6 en Docker), nginx en Docker, el backend (JAR nativo, JVM con valores por defecto),
MongoDB y Redis en Docker. Todo compite por la misma CPU y la red pasa por Docker Desktop. Los números sirven para
comparar antes/después y para ver el orden de magnitud; **no son la capacidad de un servidor de producción**.

### Lecturas públicas (`scripts/carga/lectura-publica.js`)

| Objetivo | Origen | Peticiones | Errores | p95 | p99 | Notas |
|---|---|---|---|---|---|---|
| 500 req/s | backend directo (sin caché) | 86 374 | 0,01 % | 5,1 ms | 7,5 ms | RSS ≈ 440 MB, 58 hilos |
| 1 000 req/s | backend directo (sin caché) | 172 750 | 0,03 % | 7,7 ms | 16 ms | ≈ 1 núcleo de CPU; RSS ≈ 460 MB |
| 5 000 req/s (media 3 730) | **nginx** (con micro-caché), k6 en su misma red | **862 591** | **0** | **12,7 ms** | 42 ms | nginx ≈ 1,2 núcleos; **el backend ≈ 13 s de CPU en 231 s (≈ 6 % de un núcleo)** |

- Con la micro-caché, el backend casi no nota la carga: la CPU del backend fue ~17 veces menor que sirviendo
  1 000 req/s sin caché, con 3–5 veces más tráfico. Ese es el mecanismo que sostiene los 50 000.
- Los errores del backend directo (0,01–0,03 %) son `dial: i/o timeout` en ráfagas de 1–2 s: fallos de
  **conexión TCP** a través del reenvío de puertos de Docker Desktop, no respuestas del backend. **No se aisló la
  causa**; se deja anotado como observación abierta.
- Una primera medición contra nginx por un puerto publicado dio p95 = 3,4 s y 245 000 iteraciones descartadas: era
  el banco (reenvío de puertos saturado a ~1 200 req/s), no el sistema. Se descartó y se repitió dentro de la red de
  Docker. Está en `scripts/carga/README.md` para que nadie repita el error.

### Canal en vivo (`scripts/carga/sse-conexiones.mjs`)

| Conexiones | Abiertas | Rechazadas | Primer evento (p50 / p95 / máx) | Aviso a las 10 000 | API durante la carga |
|---|---|---|---|---|---|
| 10 000 (un backend, directo) | 10 000 | 0 | 331 / 575 / 679 ms | sí, en ≤ 6 s (resolución del muestreo) | `GET /api/sectores` en 13 ms |

- **Memoria: ≈ 100 KB vivos por conexión** (heap tras un GC forzado: ~1,18 GB con 10 000 abiertas; RSS ~1,8 GB).
  Con el tope por defecto de 20 000 por instancia hacen falta ~2 GB de heap vivo → contenedor de **≥ 4 GB**. Para
  50 000 conexiones: al menos 3 instancias con tope de 20 000, o 5 con 10 000.
- El cliente Windows solo permite ~16 000 conexiones desde una máquina (puertos efímeros): pasar de ahí exige repartir
  el cliente. **No se probaron 50 000.**

### Escritura de reportes (`scripts/carga/escritura-reportes.js`)

100 POST/s repartidos en 211 sectores + un pico de 300/s (3×) sobre **un solo sector** (una avería masiva):

| Versión | Errores | p95 | Observación |
|---|---|---|---|
| Antes de corregir | **35 %** (5 052 × `503`) | **8 s** | Pool de Mongo agotado; RNF002 (1 s) incumplido |
| Tras contar votos en Mongo | 4 % | 4,9 s | Mejor, pero el pico seguía arrastrando todo |
| **Tras acotar la evaluación por sector** | **0 %** (21 000 peticiones) | **15,6 ms** | — |
| 3× esa carga (300/s + pico de 900/s, 63 001 peticiones) | **0 %** | **36 ms** (pico: 42 ms) | 0 eventos duplicados en la bitácora; los 1 411 eventos de la bitácora de esa corrida, sin repeticiones; todos los de consenso llevan sus reportes de sustento (RF011) |

## Lo que la medición encontró (y ninguna prueba unitaria veía)

1. **La micro-caché no cacheaba nada** (`BUG-085`). El backend responde `Cache-Control: no-store` (valor por
   defecto de Spring Security) y nginx respeta la cabecera del origen, así que todas las lecturas llegaban al backend.
   Una prueba previa con un backend simulado dio *MISS→HIT* y lo ocultó. Solo se vio al probar con el backend real.
   Corregido en nginx; `scripts/carga/verificar-cache-proxy.mjs` lo vigila.
2. **El consenso era O(reportes de la ventana) en cada POST** (`BUG-086`). Al superar el umbral, cada reporte nuevo
   cargaba de Mongo todos los reportes de los últimos 30 min de ese sector, los deduplicaba en Java y solo para
   descubrir que el estado no cambiaba. En una avería masiva son miles de documentos por petición: el pool de 100
   conexiones se agotaba y el sistema respondía `503` justo cuando más gente reporta. Ahora Mongo devuelve solo el
   conteo por tipo (≤ 3 filas), solo se cargan reportes cuando el estado va a cambiar, y la evaluación de un sector
   se acota a **una por segundo** (los reportes intermedios dejan el sector *pendiente* y un barrido lo evalúa).

**Costo de esta corrección, para que nadie se sorprenda:** el cambio de estado por consenso puede tardar hasta
~2 s más que antes (el intervalo de reserva de 1 s + el barrido de 1 s). La confirmación al ciudadano no se
demora (es más rápida). Configurable: `aguavigia.consenso.intervalo-evaluacion-ms` y `aguavigia.consenso.barrido-ms`.

## Lo que no se probó

- **50 000 usuarios**, ni conexiones (se llegó a 10 000) ni tasa (se llegó a ~5 000 lecturas/s y ~1 200 escrituras/s
  de pico, en una máquina compartida con el generador).
- **Más de una réplica de backend**: cerrojos de tareas, reparto de SSE, `BACKEND_REPLICAS`. Hay pruebas
  unitarias e integración de los cerrojos, pero no se ejecutó una prueba con réplicas reales.
- **SSE a través de nginx** (con su `limit_conn`) ni el tope `429` con carga real (lo cubre `SseSectoresBroadcasterTest`).
- **Resiliencia bajo carga**: matar una réplica, *failover* de Mongo o de Redis, reconexión masiva, Redis caído
  durante lecturas. El comportamiento con Redis caído está cubierto por pruebas, no por una prueba de carga.
- **MongoDB y Redis no son de alta disponibilidad**: un Mongo único, un Redis único. La réplica de 3 nodos y el
  Sentinel están en la arquitectura objetivo, no en el compose.
- **Fotos**: van a disco local; con varias réplicas una foto subida a una no la sirve otra (`404`). Falta el
  adaptador S3/MinIO (el puerto `AlmacenamientoPort` ya existe).
- **Red real, TLS, CDN**: todo fue en `localhost`.
- **Métricas** (Prometheus) ni trazas: hoy solo se puede observar con `docker stats` y los logs.

## Riesgos que conviene decidir antes de producción

- **Usuarios tras una misma IP (CGNAT móvil):** `limit_req` (30 req/s) y `limit_conn` (20 SSE) son **por IP**. En
  redes móviles miles de personas comparten IP pública y el límite podría cortar a usuarios legítimos. Hay que
  medir con tráfico real y subir los límites o mover el control a un WAF/CDN.
- **Caché con parámetros de consulta:** la clave es la URL completa; quien varía `?tamano=` evita la caché y llega
  al backend. Lo acota el `limit_req` por IP, pero no lo elimina. Conviene validar/normalizar los parámetros.
- **Gzip en línea:** nginx comprime al servir cada respuesta (≈ 1,2 núcleos a 3 700 req/s). A escala, mejor cachear
  ya comprimido o dejarlo al CDN.

## Pendiente antes de afirmar «50 000»

1. Prueba de carga **distribuida** (varias máquinas generadoras) contra un despliegue con **≥ 3 réplicas**, con
   nginx delante y SSE a través de él.
2. Repetir con **red real** y con Mongo/Redis de alta disponibilidad; ejecutar la prueba de resiliencia.
3. Adaptador de fotos en almacenamiento de objetos.
4. Métricas y alertas (conexiones SSE, pool de Mongo, latencia p95, tasa de `429`/`503`).
5. Decidir los límites por IP frente a CGNAT (arriba).

## Procedimiento de resiliencia (manual; no se ha ejecutado)

Con el despliegue de prueba y una carga de lectura + SSE en marcha, en este orden:

1. `docker stop` de **una** réplica del backend → esperado: 0 errores visibles (nginx reintenta en la otra), los
   clientes SSE de esa réplica reconectan con *jitter* y `Retry-After`; la ingesta sigue corriendo una vez por ciclo.
2. Detener **Redis** 60 s → esperado: lecturas siguen (caché degradada a Mongo), límites *fail-open*, `/actuator/health/readiness`
   en `DOWN` (Redis es dependencia de readiness), sin `500` en `GET /api/sectores`.
3. Detener **Mongo** 60 s → esperado: lecturas cacheadas en nginx siguen 30 s por `use_stale`; escrituras `503`
   con cuerpo RFC 7807 (nunca colgadas más de ~2 s); recuperación automática al volver.
4. Reiniciar **todas** las réplicas a la vez → esperado: reconexión escalonada, no una avalancha (jitter 3–10 s).

## Cómo repetir las mediciones

Guía y trampas del banco local en [`scripts/carga/README.md`](../../scripts/carga/README.md). Resumen: vaciar
`aguavigia.rate-limit.reglas` en el backend de prueba; para medir nginx, una **copia** de `nginx.conf` sin `limit_req`
y k6 en la misma red de Docker; comprobar antes con `node scripts/carga/verificar-cache-proxy.mjs`.
