# Registro de implementaciones

> Qué se construyó de verdad, sprint por sprint, con su trazabilidad a requisitos. No es una lista de
> tareas ni un tablero: es la evidencia de que un requisito pasó de escrito a funcionando.
>
> Se actualiza **al fusionar un Pull Request a `main`**, no antes.

---

## Cómo se llena

Una fila por unidad entregada. Si no tiene requisito asociado, o no debería haberse construido, o
falta un requisito por escribir — ambas cosas hay que resolverlas antes de agregar la fila.

| Campo | Regla |
|---|---|
| **RF/RNF** | El id de `docs/product-requirements.md`. Obligatorio **para todo lo que implemente funcionalidad**. El andamiaje del Sprint 0 y el trabajo de proceso llevan `—` (ver `ADR-009`). |
| **Tipo** | `func` funcionalidad · `infra` infraestructura · `datos` conjunto de datos · `andamio` estructura sin funcionalidad · `proceso` reglas y documentación de trabajo. Solo `func` cuenta para la cobertura de requisitos. |
| **Qué** | Una frase en pasado. `Endpoint POST /api/reportes con rate limiting`, no `trabajo en reportes`. |
| **PR** | Número del Pull Request. Es la traza a cuándo y por qué entró el cambio. Los `#N` anteriores al 2026-09-17 son del repositorio público anterior; desde el 2026-09-17 son del repositorio privado, donde la numeración reinició en #1 (por eso un mismo `#N` puede ser un cambio distinto en cada repositorio). |
| **Prueba** | Cómo se verifica. `RegistrarReporteServiceTest`, `E2E reporte.spec.ts`. Sin prueba, no está terminado. Para `proceso`, el comando o el documento que lo evidencia. |

---

## Sprint 0 — Configuración e infraestructura

Sin `RF` asociado a propósito: es arquitectura base, no funcionalidad (`ADR-009`).

| RF/RNF | Tipo | Qué | PR | Prueba |
|---|---|---|---|---|
| — | infra | `docker-compose.yml` base (Mongo 7 + Redis 7 + Mailhog), `.env.example`, plantillas de PR e issue y 3 workflows de GitHub Actions | #1 | `docker compose config -q` sin errores |
| — | datos | GeoJSON de los 213 barrios de Cartagena (ArcGIS de Cartagena Cómo Vamos, WGS84) en `data/geoespacial/` | #2 | 213 *features*; nombres contrastados con boletines reales de Acuacar en el PR #6 |
| — | proceso | Diseño del dominio de M3/M6 en `docs/ingenieria/modelo-de-dominio.md`; resuelta la duplicación de `SuscribirseService` | #4 | `docs/ingenieria/modelo-de-dominio.md` |
| — | andamio | Proyecto `/frontend`: React 19 + Vite + TypeScript + Tailwind v4, tokens de `DESIGN.md`, temas claro/oscuro, 4 rutas marcador de posición | #5 | `npm run build` y `npm run lint` en verde en Frontend CI |
| — | datos | Validación del GeoJSON contra boletines #2785, #2787 y #2547; hallazgo de granularidad por tramo de calle y manzana | #6 | `data/geoespacial/README.md` + `MEMORY.md` |
| — | andamio | Proyecto base de `/backend`: Maven, Java 21, Spring Boot 3.4.1, estructura vacía de Arquitectura Limpia (`domain/`, `application/`, `infrastructure/`, `api/`) | #10 | `./mvnw verify` → BUILD SUCCESS, local y en Backend CI |
| — | proceso | Registro del proyecto base de `/backend` en implementaciones y bitácora | #11 | esta tabla |
| — | datos | Población real por barrio (DANE 2018 + CORVIVIENDA) + script de siembra en Mongo, probado contra Mongo real | #13 | 211 sectores sembrados, `$geoIntersects` verificado |
| — | proceso | Auditoría de coherencia del repositorio: 10 contradicciones corregidas, `ADR-009`, `ADR-010`, `BUG-001`, `BUG-002`, `sprint-0.md` | #14 | `wc -l`/`grep` verificados en la revisión |
| — | andamio | Reparado el build de frontend en la rama principal, roto por un merge de PR anterior al fix | #16 | `npm run build` |
| — | proceso | Estrategia del plan de pruebas, trazada a los 20 RNF | #17 | `docs/ingenieria/plan-de-pruebas.md` |
| — | proceso | Decisión: `Sector.poblacion` nulable, respuesta a la pregunta del PR #13 | #18 | `modelo-de-dominio.md` §3.1 |
| — | infra | `env_file` opcional en `docker-compose.yml` (corrige `BUG-003`) y verificación del entorno reproducible | #23 | `docker compose config -q && ls backend frontend` → exit 0 |
| — | proceso | Registro de `BUG-004` (contraseña mock en `PaginaVeedor.tsx`) | #26 | `registro-de-bugs.md` |
| — | infra | Dockerfile multi-etapa del backend + activación del servicio `backend` en `docker-compose.yml` | #27 | `docker compose config -q` en verde, `hadolint backend/Dockerfile` limpio |
| — | infra | `.gitattributes` fuerza `eol=lf` en `backend/mvnw` — corrige `exec format error` al construir la imagen Docker en Windows | #28 | `docker build -t ctg-backend-test backend/` → BUILD SUCCESS |
| — | andamio | `BUG-004` corregido: se retira el campo de contraseña mock de `PaginaVeedor.tsx` | #30 | `PaginaVeedor.test.tsx` — 2 archivos, 4 pruebas en verde |
| — | infra | Dockerfile del frontend, JaCoCo en `pom.xml` + CI, perfiles de Spring (`dev`/`docker`/`prod`), `/actuator/health` | #33 | `./mvnw verify` → 23/23, JaCoCo 61.1 %; `curl localhost:8080/actuator/health` → `mongo: UP` |
| — | andamio | Andamiaje de infraestructura: dependencias de build en `pom.xml` (Testcontainers, MapStruct, `springdoc-openapi`, Resilience4j, Lombok), `RedisConfig` y paquetes vacíos de `infrastructure/persistence` | #40 | `./mvnw verify` en verde en Backend CI |

**Cobertura de requisitos del Sprint 0: 0 de 36.** Es lo esperado y no es un retraso: por `ADR-009`
el Sprint 0 no implementa funcionalidad. Lo de arriba es lo que hace posible implementarla.

---

## Sprint 1 — Mapa base y dominio core

| RF/RNF | Tipo | Qué | PR | Prueba |
|---|---|---|---|---|
| RF001 · RF004 | func | M1: `MapaCartagena` (Leaflet + los 213 barrios reales), `ListaSectores` accesible, `InsigniaEstado`, `EtiquetaFrescura` | #12 | `npm run build` en verde · ⚠️ **se alimentaba de `SECTORES_MOCK`, no de la API** |
| RF009–RF011, RF016–RF017, RF020–RF022 | andamio | Dominio de M3/M6: Value Objects, entidades (`CorteAgua` con Builder), `domain/port/in` y `port/out`, test de ArchUnit. Deja listos el dominio y los puertos | #21 | `./mvnw verify` → 23 pruebas, 0 fallos, ArchUnit incluido |
| RF001 · RF002 · RF004 | func | M1 backend: adaptador Mongo de `SectorRepository` (índice `2dsphere`, geometría preservada al guardar), adaptador de `RelojPort`, `GET /api/sectores` y `/api/sectores/{id}`, errores RFC 7807, contrato OpenAPI publicado | #56 | `./mvnw clean verify` → **34 pruebas, 0 fallos**, ArchUnit incluido · verificado además contra Mongo real: 211 sectores servidos, 404 en `application/problem+json` |
| RF009–RF011 | infra | M3: adaptador Redis de `ContadorReportesPort` — ventana deslizante de reportes por sector sobre un `ZSET` (score = instante epoch millis), TTL de retención de 24h. No deduplica por `HuellaDispositivo` a propósito (responsabilidad del rate limiting HTTP, todavía sin construir). Sin consumidor todavía: `EvaluarConsensoUseCase` sigue sin existir en `application/` | #57 | `./mvnw clean verify` → 40 pruebas, 0 fallos, ArchUnit incluido · `RedisContadorReportesAdapterTest` — 6 pruebas de integración contra `redis:7-alpine` real (Testcontainers) |
| RF019 · RNF011 | func | M5: infraestructura JWT del panel del veedor — `POST /api/veedor/sesion` (credencial única BCrypt, RF019), `SecurityConfig` protege `/api/veedor/**` y deja el resto público, token expira a las 8h exactas (RNF011). Sin CRUD de cortes ni moderación todavía: necesitan casos de uso de `application/` | #58 | `./mvnw clean verify` → 52 pruebas, 0 fallos, ArchUnit incluido · `JwtProviderTest` (6), `VeedorAuthControllerTest` (8) · verificado además en vivo: login, 401/404 según corresponda, expiración exacta de 8h |
| — (parte de M9, RF029–RF036) | infra | M9: `DocumentoCrudo` (normalización + hash SHA-256), `PrefiltroDeterminista` (9 palabras clave ya aprobadas en el diseño, descarta ~70% del volumen antes de gastar un token de IA) y `DeduplicadorReciente` (mitad Redis del diseño, ventana de 7 días, deliberadamente no permanente). Sin colectores (`AcuacarApiCollector`, `RssCollector`) ni capa de IA todavía — pendientes, no rodeados (los colectores llegan en el PR #98) | #59 | `./mvnw clean verify` → 70 pruebas, 0 fallos, ArchUnit incluido · `PrefiltroDeterministaTest` (11, con titulares reales del diseño), `DeduplicadorRecienteTest` (3, integración contra `redis:7-alpine`), `DocumentoCrudoTest` (4) |
| — (RNF de rate limiting, ADR-007) | infra | Rate limiting HTTP genérico — `RateLimitingInterceptor` + `RateLimitConfig` (Redis `INCR`+`EXPIRE`), configurable por `application.yml` (`aguavigia.rate-limit.reglas`), **opt-in**: sin reglas configuradas, no protege nada. Cierra el hueco de fuerza bruta señalado en `ADR-016` (login del veedor) sin depender del PR que lo introdujo. `ADR-018`: clave por IP, no por huella de dispositivo | #60 | `./mvnw clean verify` → 75 pruebas, 0 fallos, ArchUnit incluido · `RateLimitingInterceptorTest` (3, integración contra `redis:7-alpine`), `RateLimitConfigTest` (2, extremo a extremo con `MockMvc`) · verificado además en vivo: 3 peticiones pasan, la 4ª y 5ª reciben `429` con `Retry-After` |
| — (infraestructura transversal) | infra | Configuración de caché sobre Redis — `@EnableCaching` + `RedisCacheManager`, valores serializados en JSON (no serialización Java), TTL configurable por `application.yml` (`aguavigia.cache`, 30s por defecto, con overrides por nombre de caché). Ningún método de producción usa `@Cacheable` todavía — queda listo para anotarlo cuando exista una consulta que valga la pena cachear | #61 | `./mvnw clean verify` → 79 pruebas, 0 fallos, ArchUnit incluido · `CacheConfigTest` (4, integración contra `redis:7-alpine`, incluida verificación del TTL real por inspección directa de Redis) |
| RF012 · RF013 (parcial) | func | M4: `POST /api/suscripciones` — primer caso de uso real en `application/` (`SuscribirseService`), valida sectores contra `SectorRepository`, persiste en Mongo y envía el correo de doble opt-in de forma asíncrona (`@Async` + `JavaMailSender`) usando las plantillas de `a6a8ae4`. Sin confirmar el token ni la baja de un clic todavía — RF013 completo y RF015 son Sprint 2 | #78 | `./mvnw clean verify` → **95 pruebas, 0 fallos**, ArchUnit incluido · verificado además extremo a extremo contra Mailhog real: `POST` → 201 → correo recibido con asunto, sector y token de confirmación correctos |
| RF005–RF008 | func | M2: `RegistrarReporteService` — segundo caso de uso real en `application/`, valida el sector contra `SectorRepository`, guarda el reporte y alimenta `ContadorReportesPort` (insumo de `EvaluarConsensoUseCase`, RF009-RF011, aún sin escribir). Incluye el adaptador Mongo de `ReporteCiudadanoRepository`, que no existía | #84 | `./mvnw clean verify` → **101 pruebas, 0 fallos**, ArchUnit incluido |
| RF001–RF004 | func | M1/M5/M8: `SECTORES_MOCK`/`MOCK_EVENTOS` retirados de `useDatosEnVivo.ts`, `PaginaVeedor.tsx` y `PaginaBitacora.tsx` — cierra los datos simulados provisionales | #85 | `npm run build` / `npm test` en verde |
| RF006 · RF029–RF030 (parcial) | func | M2/M9: RF006 real en `RegistrarReporteService` (límite por `HuellaDispositivo`, `LimiteReportesExcedidoException` → 429) cerrando `BUG-032`; `BUG-033` cerrado (`ListaSectores.tsx`, reportes inventados). Además, colectores de ingesta — `AcuacarApiCollector` y `RssCollector` (M9). Capa de IA descartada (`ADR-025`) | #89 · #98 | `./mvnw clean verify` → **110 pruebas, 0 fallos**, ArchUnit incluido · `npm test` → 12/12 |
| — | proceso | Sala de control: sección "qué falta para cerrar el sprint" (objetivo + criterio de cierre + checklist de compromisos) y cobertura por módulo — antes solo había conteos agregados sin explicar el porqué | #97 | `node scripts/generar-dashboard.mjs` sin advertencias de sección vacía · verificado en navegador (sin errores de consola, tema claro/oscuro, responsive 375px) |

⚠️ **El PR #12 introdujo datos simulados** (`SECTORES_MOCK`) en lugar de `GET /api/sectores`, que aún no
existía. No contó como RF001/RF004 implementados hasta que consumió la API real; la tabla de
cobertura siguió en 0% hasta entonces.

**El PR #56 publica el contrato pero no mueve la cobertura a más de 0%.** El backend ya sirve los 211
sectores reales, pero `PaginaMapa.tsx` seguía leyendo `SECTORES_MOCK`: mientras el frontend no
consuma `GET /api/sectores`, RF001–RF004 no están cubiertos de extremo a extremo. La fila va como
`func` porque el backend sí está terminado y probado; la cobertura la movió el frontend al conectar y
retirar los datos simulados. Contar antes sería inflar la cobertura.

**El PR #21 lleva `andamio`, no `func`:** define contratos (interfaces `port/in`) y entidades, pero
ningún caso de uso está implementado todavía — eso es Sprint 2. La cobertura de requisitos sigue en
0% hasta que exista una implementación real detrás de un `port/in`.

**El PR #57 lleva `infra`, no `func`:** implementa un adaptador de salida contra un puerto que ya
existía (`ContadorReportesPort`, de `port/out`), pero ningún caso de uso lo invoca todavía —
`EvaluarConsensoUseCase` sigue sin escribirse en `application/`. La cobertura de RF009–RF011 sigue en
0% hasta que exista ese caso de uso.

**El PR #58 lleva `func`:** a diferencia del PR #57, sí expone un endpoint que funciona de extremo a
extremo — `POST /api/veedor/sesion` emite un JWT real y `SecurityConfig` lo exige de verdad en
`/api/veedor/**`, verificado en vivo. **Aun así la cobertura de RF019 sigue en 0%:** `PaginaVeedor.tsx`
todavía usa el botón "Simular ingreso" (`BUG-004`), no el login real — falta conectarlo desde el
frontend.

**Al resolver el merge del PR #58 contra la rama principal** (que ya traía los PR #56 y #57) apareció
`BUG-011`: un error 500 que no existía en ninguno de los dos PRs por separado, solo en su combinación
(`ManejadorGlobalDeErrores` sin manejar `MethodArgumentNotValidException`/`NoResourceFoundException`,
y `SectorControllerTest` sin `@Import(SecurityConfig.class)`). Se corrigió antes de fusionar; detalle
en `registro-de-bugs.md` (`BUG-011`).

**El PR #59 no tiene `RF` porque es explícitamente parcial:** cubre solo la parte del pipeline M9 que
no toca la red externa. Sin `RF029`–`RF036` en la columna a propósito — asignárselos inflaría la
cobertura de un módulo que todavía no tiene ni un colector ni la capa de IA conectados. Faltaban un
correo de contacto real para el `User-Agent` y una clave de API, y se detuvo ahí en vez de rodearlo.

**El PR #60 tampoco tiene `RF` directo:** es infraestructura transversal, no acoplada a ningún
módulo — el propio PR evitó depender del PR #58 (login del veedor, sin fusionar en ese momento)
construyendo un interceptor genérico en vez de uno específico. **Opt-in real:** la cobertura de
`ADR-016` (freno de fuerza bruta) sigue sin cerrarse del todo — el interceptor existe y funciona,
pero nadie ha activado todavía `aguavigia.rate-limit.reglas` para `/api/veedor/sesion`.

**Al resolver el merge del PR #60 contra la rama principal** (que ya traía los PR #56, #58 y #59)
apareció `BUG-012`: `RateLimitConfig` implementa `WebMvcConfigurer`, y `@WebMvcTest` lo autodetecta
en *cualquier* slice de prueba del proyecto aunque no se importe — tumbó `SectorControllerTest` y
`VeedorAuthControllerTest` (que no tenían un `RedisTemplate` disponible) y dejó sin efecto la
protección de `SecurityConfig` en el propio `RateLimitConfigTest`. Ninguno de los PRs lo tenía por
separado; se corrigió antes de fusionar. Detalle en `registro-de-bugs.md` (`BUG-012`).

**El PR #61 tampoco tiene `RF` directo y sirve a dos sprints a la vez** (Sprint 2 "caching de
respuestas del mapa" y Sprint 5 "decorador de caché"), por eso no lleva número de módulo. **No repite
`BUG-011`/`BUG-012`:** a diferencia de `RateLimitConfig`, `CacheConfig` no implementa
`WebMvcConfigurer`, así que `@WebMvcTest` no lo autodetecta en otros slices — se verificó que las 79
pruebas combinadas pasan sin tocar ningún test existente.

---

## Sprint 2 — Reporte ciudadano y consenso

| RF/RNF | Tipo | Qué | PR | Prueba |
|---|---|---|---|---|
| RF005–RF008 | func | M2: `POST /api/reportes`, expone `RegistrarReporteService` (Sprint 1) — la API quedaba cerrada a propósito hasta este sprint. Sin registro ni cuenta, coordenada opcional, `429` real cuando el dispositivo supera el límite | #104 | `./mvnw clean verify` → **116 pruebas, 0 fallos**, ArchUnit incluido · probado extremo a extremo contra Mongo real: 3 reportes del mismo dispositivo pasan, el cuarto → 429 |
| RF009–RF011 | func | M3: `EvaluarConsensoService`, patrón Strategy (`UmbralFijoEstrategiaConsenso`, `UmbralProporcionalEstrategiaConsenso`, elegible por configuración). `RegistrarReporteService` la dispara automáticamente tras cada reporte. Anexa el cambio real de estado a `eventos_bitacora` (`TipoEvento.CORTE_CONFIRMADO_POR_CIUDADANOS`), sin duplicar si el estado no cambió. Incluye el adaptador Mongo mínimo de `EventoBitacoraRepository`, que no existía | #106 | `./mvnw clean verify` → **134 pruebas, 0 fallos**, ArchUnit incluido · probado extremo a extremo: sector de 500 habitantes, 3 reportes independientes → `SIN_SERVICIO` solo, evento real anexado |
| RF013 (completo) · RF015 | func | M4: `GET /api/suscripciones/confirmar` y `GET /api/suscripciones/cancelar`. `Suscripcion.confirmar()`/`cancelar()` como nuevas transiciones de estado; `SuscripcionRepository.buscarPorToken`. Confirmar dos veces no falla (idempotente); token inválido → 400 real | #107 | `./mvnw clean verify` → **150 pruebas, 0 fallos**, ArchUnit incluido · probado extremo a extremo: suscribirse → confirmar → confirmar de nuevo (200) → cancelar → token inválido (400) |
| RNF003 | infra | M2/M5: activados los dos pendientes de infraestructura del sprint — `@Cacheable` en `GET /api/sectores` (TTL 15s, `@CacheEvict` al confirmar un cambio de estado por consenso) y reglas de `aguavigia.rate-limit.reglas` para `/api/veedor/sesion` (5/300s, cierra el hueco de `ADR-016`) y `/api/reportes` (30/60s). La infraestructura ya existía desde el Sprint 1 (PR #60, #61) sin usarse. `REC-006` registrada: `RateLimitConfig` se instancia en cualquier `@WebMvcTest` aunque no se importe | #112 | `./mvnw clean verify` → **155 pruebas, 0 fallos**, ArchUnit incluido · `SectorMongoAdapterCacheTest` (Mongo + Redis reales, Testcontainers) y `ReglasDeRateLimitDeProduccionTest` contra el `application.yml` real |
| RF016–RF017 | infra | `CorteAguaMongoAdapter` — el dominio de `CorteAgua` existía desde el Sprint 1 sin adaptador que lo persistiera. Índice de `sectoresAfectados` agregado a `IndicesMongo`. Adelanto de Sprint 3 | #113 | `./mvnw clean verify` → **154 pruebas, 0 fallos**, ArchUnit incluido · Mongo real (Testcontainers) |
| RF016–RF017 | func | M5: `GestionarCorteOficialService` + `CorteController` en `/api/veedor/cortes` (registrar, cerrar, consultar, listar por sector), protegido por el JWT ya existente. Cerrar un corte ya cerrado responde 409. Adelanto de Sprint 3 | #116 | `./mvnw clean verify` → **175 pruebas, 0 fallos**, ArchUnit incluido |
| RF020–RF022 | func | M6 (el diferencial): `CalcularCumplimientoService` + `IndiceCumplimientoController` público en `/api/cumplimiento`. `ADR-022`: agrega por suma de duraciones, no promedio de porcentajes. Agregado `CorteAguaRepository.listarTodos()`. Adelanto de Sprint 4 | #118 | `./mvnw clean verify` → **178 pruebas, 0 fallos**, ArchUnit incluido |
| RF026 | func | `RegistrarEventoBitacoraService` (puerto sin implementación desde el Sprint 1) y `GestionarCorteOficialService` anexando `CORTE_ANUNCIADO`/`CORTE_RESTABLECIDO` por cada sector afectado — antes solo el consenso ciudadano anexaba a la bitácora | #119 | `./mvnw clean verify` → **176 pruebas, 0 fallos**, ArchUnit incluido |
| RF027 | func | M8: `BitacoraController` público en `GET /api/bitacora`, directo al puerto de salida sin caso de uso (`ADR-015`). Cierra M8 completo junto con el PR #119 | #120 | `./mvnw clean verify` → **178 pruebas, 0 fallos**, ArchUnit incluido |
| RF018 | func | M5: `ModerarReporteService` + `ModeracionReporteController` en `/api/veedor/reportes` (listar pendientes, aprobar, descartar). `ADR-023`: "dudoso" es "todo reporte sin moderar" — nadie había definido el criterio, y no se inventó una heurística de fraude no pedida | #121 | `./mvnw clean verify` → **209 pruebas, 0 fallos**, ArchUnit incluido |
| — | proceso | Regenerado `backend/openapi.yaml` contra la app corriendo (Mongo/Redis reales): de 7 a 17 rutas — faltaban por completo los cuatro módulos de los PRs #116, #118, #119 y #120. Sin esto, el frontend no podía generar un cliente que los viera | #124 | YAML válido, `openapi: 3.0.1` confirmado (`estado` anulable de sectores se preserva, `ADR-014`) |

**Pendiente de este sprint:** conectar `FormularioReporte` al `POST /api/reportes` real — sigue usando
el fallback que produce `BUG-017`, y el contrato exige un campo `huella` (huella anónima de
dispositivo, `ADR-007`) que el frontend todavía no genera. Documentado en el PR #104 para quien lo
tome.

---

## Trabajo de UI adelantado (Sprints 2–5, sin API real)

Se maquetaron varias pantallas de sprints futuros mientras el contrato OpenAPI aún no existía, con el
mismo patrón que el PR #12: datos escritos a mano en vez de la API. Ninguna de estas filas suma a la
cobertura de requisitos —son `andamio`, no `func`— hasta que consuman la API real. Todos esos datos
simulados se retiraron al cerrar el Sprint 1 (`sprint-1.md`).

| RF/RNF | Tipo | Qué | PR | Prueba |
|---|---|---|---|---|
| RF005, RF007, RF008 | andamio | M2: `FormularioReporte` accesible — flujo sin registro, preselección de sector por URL (`?sector=X`), consentimiento de geolocalización | #19 | `tsc --noEmit` en verde · ⚠️ usaba `SECTORES_MOCK` (ya retirado) |
| RF016, RF018 | andamio | M5: `PaginaVeedor` — acceso simulado, registro de cortes oficiales, moderación de reportes ciudadanos | #20 | `tsc --noEmit` en verde · ⚠️ usaba datos simulados de reportes (ya retirados) |
| RF023, RF024 | andamio | M7: `PaginaEstadisticas` — gráficos de Índice de Cumplimiento y sectores afectados (Recharts), botón de exportación | #20 | `tsc --noEmit` en verde · ⚠️ usaba datos simulados (ya retirados) |
| RF026, RF027 | andamio | M8: `PaginaBitacora` — línea de tiempo vertical de eventos | #25 | `tsc --noEmit` en verde · ⚠️ usaba `MOCK_EVENTOS` (ya retirado) |
| RNF020 (parcial) | andamio | PWA offline (`vite-plugin-pwa`, cachea el GeoJSON local) + primera prueba unitaria con Vitest (`InsigniaEstado.test.tsx`) | #24 | `npm test` en verde |

---

## Repositorio privado — serie del 2026-09-21 (cierre del Sprint 2)

PR fusionados a `main` en el repositorio privado. Sus pruebas son las que constan en la descripción de
cada PR y en `estado-del-backend.md` (`./mvnw verify`: 823 pruebas, 0 fallos).

| RF/RNF | Tipo | Qué | PR | Prueba |
|---|---|---|---|---|
| — | infra | Frontend retirado de `main` (queda en la etiqueta `pre-retiro-frontend`, `ADR-048`) y proxy de producción llevado a `infra/nginx/` | #16 | `docker compose config -q` en ambos compose y `nginx -t` (repetidos en CI) · micro-caché MISS → HIT con el backend real |
| RF007, RF011, RF015, RNF009 | func | Brechas de requisitos y de contrato cerradas antes de rehacer el frontend (`ADR-050`, `ADR-051`, `ADR-052`) | #17 | `./mvnw verify`: 823 pruebas, 0 fallos · verificado en vivo contra Mongo, Redis y Mailhog |
| RNF027 | infra | Backend preparado para 50 000 usuarios simultáneos (micro-caché nginx, avisos SSE, jobs de una sola réplica, rate limit atómico; `ADR-049`, `ADR-053`) y medido a escala local | #18 | `scripts/carga/`: ~3 700 req/s con micro-caché, 10 000 conexiones SSE, escritura a 3× la carga sin errores (p95 36 ms) |
| — | proceso | Guía `docs/api/` para el frontend nuevo y registros de la ronda de pulido | #19 | La referencia de rutas se regenera sin diferencias contra `openapi.yaml` · `generar-dashboard.mjs` corre sin error |
| — | infra | Índices de Mongo para la cola de moderación y la bitácora por sector | #20 | `IndicesMongoTest` · la página 1 de la cola pasa de 84 000 documentos examinados a 20 |
| RF002 | func | Rutas y ajustes de contrato para el frontend nuevo (`ADR-054`, `ADR-055`, `ADR-056`), incluido el histórico público de cortes, y respaldo de Mongo con autenticación (`BUG-088`) | #21 | `./mvnw verify`: 823 pruebas, 0 fallos · respaldo y restauración (`--drop`) contra un Mongo desechable con autenticación |
| — | proceso | Alcance académico local (`ADR-057`), retención de reportes a 12 meses con índice TTL (`ADR-058`) y cierre del Sprint 2 | #22 | `IndicesMongoTest`: TTL a 365 días y desactivado con 0 · ⚠️ el CORS de `dev` cambió sin prueba automática |
| — | datos | Sembrador de 20 000 cuentas de demostración para la presentación (`scripts/sembrar-usuarios-demo.mjs`) | #23 | Contra el backend real: `GET /api/veedor/usuarios` devuelve `X-Total-Count: 20001` en 101 páginas · una cuenta VEEDOR y una OBSERVADOR sembradas inician sesión con sus permisos; una suspendida recibe 403 |
| — | infra | Actions del CI actualizadas: `checkout` 4→7, `setup-java` 4→6, `upload-artifact` 4→7 y `gitleaks-action` 2→3 (Dependabot) | #4, #24, #1, #8 | CI en verde sobre el `main` nuevo (imágenes y compose, Trivy, gitleaks por `push`) · ⚠️ `gitleaks` por `pull_request` fallaba por `BUG-089`, ajeno al cambio |
| — | infra | Dependencias del backend: jjwt 0.12.6→0.13.0 y ArchUnit 1.3.0→1.5.0 (Dependabot) | #5, #9 | `Backend CI` completo (`./mvnw verify`) en verde sobre el `main` nuevo |
| — | proceso | Dependabot ignora los saltos mayores de Spring Boot y springdoc (`ADR-059`); matriz, cobertura, plan de pruebas y recomendaciones alineados con `ADR-048` y `ADR-057`; corregido el extractor de cobertura de la Sala de control (`BUG-090`) | #25 | `generarDatos()` devuelve 46/40 RF, 27/17 RNF y 15 módulos con su avance · `node scripts/generar-dashboard.mjs`: 54 ADR, 1 bug abierto, 5 recomendaciones pendientes |
| — | proceso | Retirado el pendiente «borrar `frontend/` del disco» de `estado-del-backend.md` y de la bitácora, ya cumplido | #30 | `ls frontend` → no existe · `git status` limpio · etiqueta `pre-retiro-frontend` intacta |
| — | infra | Dependencias del backend: resilience4j 2.3.0→2.4.0, jacoco-maven-plugin 0.8.12→0.8.15, Maven wrapper 3.9.9→3.9.16, springdoc 2.8.6→2.9.1 (Dependabot) | #26, #27, #28, #29 | `Backend CI` completo (`./mvnw verify`) en verde sobre el `main` nuevo; `main` en verde en sus tres workflows (`2c6d6f7`) |
| — | proceso | Cerrado #2 de Dependabot (Testcontainers 2.0.5): el BOM ya no fija la versión de `testcontainers-junit-jupiter` ni `testcontainers-mongodb`, migración real, mismo criterio que `ADR-059`. Dependabot ya no propone ese salto (`ADR-060`) | #32 | Log del `Backend CI` del #2: `'dependencies.dependency.version' for org.testcontainers:junit-jupiter:jar is missing` · `main` sigue en verde sin el cambio |
| — | infra | `BUG-089` cerrado: `permissions: contents: read, pull-requests: read` en el workflow de secretos, renombrado a `escaneo-de-fugas.yml` para esquivar `Read(**/*secret*)` sin tocar la regla del dueño. ⚠️ Este PR arrastró también `sprint-3.md` (ver la fila de abajo): se escribió en la misma rama mientras el CI de este PR corría, y un `git add -A docs` posterior lo mezcló por descuido con un commit que no tenía que ver con sprints | #35 | `gitleaks` en verde en el propio PR (antes fallaba con 403 en todo PR) |
| — | proceso | `sprint-3.md` reconstruido y cerrado retroactivamente con la evidencia que ya existía (`REC-017`, delegado por el dueño) — fusionado sin querer con el PR #35 (ver su fila) | #35 | `matriz-trazabilidad.md` y el código (evento `SectorActualizadoEvent` para `RF014`) contrastados el 2026-09-22 |
| — | proceso | `sprint-4.md` y `sprint-5.md` reconstruidos y cerrados retroactivamente (`REC-017`); `sprint-4.md` reveló `RNF006` sobreestimado en la matriz (`BUG-091`); Sprint 6 redefinido para local sin cerrarse — qué cuenta como demo sin frontend propio queda pendiente del dueño (`REC-018`) | #36 | `matriz-trazabilidad.md`, `registro-de-implementaciones.md` (esta tabla) y `estado-del-backend.md` contrastados contra el código el 2026-09-22 |
| — | infra | Cerrar varios sprints de una sentada destapó tres bugs más del generador de la Sala de control: una fila `Abierto` con la palabra «parcial» en su prosa se clasificaba mal (`BUG-092`), el «sprint activo» se calculaba mal en cuanto el último `sprint-N.md` documentado ya estaba cerrado, doblando el avance del proyecto (`BUG-093`), y ese mismo arreglo hizo que el aviso de secciones vacías reventara con un sprint activo sin archivo (`BUG-094`) | #36 | `generarDatos()`: 85 bugs (`BUG-091` sale `Abierto`) y `avanceProyecto` da `85.7%`, `sprintsCerrados: 6`, `sprintActivoNum: 6` · `node scripts/generar-dashboard.mjs` termina sin error |
| — | infra | Dependencias del backend: resilience4j 2.3.0→2.4.0, jacoco-maven-plugin 0.8.12→0.8.15, Maven wrapper 3.9.9→3.9.16, springdoc 2.8.6→2.9.1 — dentro de la rama 2.x, no choca con `ADR-059` (Dependabot) | #26, #27, #28, #29 | `Backend CI` completo (`./mvnw verify`) en verde sobre el `main` nuevo; `main` en verde en sus tres workflows tras la fusión (`2c6d6f7`) · ⚠️ `gitleaks` por `pull_request` fallaba por `BUG-089`, ajeno al cambio |

---

## Estado de cobertura de requisitos

Se actualiza al cerrar cada sprint. Es el insumo directo de `docs/ingenieria/matriz-trazabilidad.md` y
debe coincidir con ella: **46 RF = 40 implementados + 5 descartados (RF032–RF036) + 1 pendiente (RF041)**.
Los descartados cuentan en «Requisitos» pero no en «Implementados». Recalculada el 2026-09-21 desde la
matriz, no desde lo que los PR afirman en su descripción.

| Módulo | Requisitos | Implementados | % |
|---|---|---|---|
| M1 Mapa en vivo | 4 | 4 (RF001–RF004) | 100% |
| M2 Reporte ciudadano | 4 | 4 (RF005–RF008) | 100% — `POST /api/reportes` (PR #104), RF006 real con límite por dispositivo |
| M3 Consenso automático | 3 | 3 (RF009–RF011) | 100% — `EvaluarConsensoService`, patrón Strategy, sustento trazado en la bitácora |
| M4 Alertas por correo | 4 | 4 (RF012–RF015) | 100% — RF014 (aviso al suscriptor al cambiar el estado del sector) marcado ✅ en la matriz de trazabilidad |
| M5 Panel del veedor | 4 | 4 (RF016–RF019) | 100% — CRUD de cortes (PR #116), moderación de reportes (PR #121, `ADR-023`), login JWT |
| M6 Índice de Cumplimiento ⭐ | 3 | 3 (RF020–RF022) | 100% — `CalcularCumplimientoService`, `ADR-022` (PR #118) |
| M7 Estadísticas | 3 | 3 (RF023–RF025) | 100% — `EstadisticasMongoAdapterTest`, serie del índice y exportación CSV en el backend |
| M8 Bitácora pública | 3 | 3 (RF026–RF028) | 100% — `GET /api/bitacora` público (PR #120), eventos de todo el ciclo de vida del corte anexados (PR #119), inmutable por diseño del puerto (sin editar ni eliminar) |
| M9 Ingesta con IA ⭐ | 8 | 3 (RF029–RF031) | 38% — colectores y deduplicación reales (PR #59, #98); RF032–RF036 descartados (`ADR-025`) |
| M10 Evidencia multimedia | 1 | 1 (RF037) | 100% |
| M11 Validación comunitaria | 1 | 1 (RF038) | 100% |
| M12 API abierta Open311 | 1 | 1 (RF039) | 100% |
| M13 Integración IoT pasiva | 1 | 1 (RF040) | 100% |
| M14 Alertas push | 1 | 0 | 0% — RF041 pendiente: `NotificadorPushWebhookAdapter` solo registra un log; exige credenciales de WhatsApp Business o Telegram |
| M15 Cuentas y permisos | 5 | 5 (RF042–RF046) | 100% |
| **Total funcionales** | **46** | **40** | **87%** |
| **No funcionales** | **27** | **16** | **59%** |

Los 16 RNF verificados: RNF002–RNF005, RNF007–RNF011, RNF017, RNF018, RNF020 y RNF022–RNF025. `RNF006`
bajó de verificado a parcial el 2026-09-22 (`BUG-091`): la matriz lo marcaba ✅ sin que exista la cola
muerta que pide el requisito. Los otros once: RNF001 y
RNF012–RNF016 **retirados por alcance** (interfaz, `ADR-048`) · RNF019 descartado (`ADR-025`) · RNF006, RNF021 y RNF027
parciales · RNF026 sin verificar, no aplica al entorno local (`ADR-057`).

---

<!--
Rotación: al cerrar el sprint N+2, el sprint N se comprime a una sola fila de resumen
(módulos tocados, requisitos cubiertos, PRs) y el detalle se archiva en
docs/gestion/historico/implementaciones-sprint-<N>.md. Ver protocolo-de-contexto.md §5.
-->
