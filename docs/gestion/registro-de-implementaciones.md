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
| — | infra | `BUG-096`: `jacoco:check` (`RNF017`) no evaluaba ninguna cobertura real desde siempre — `domain.*`/`application.*` no incluyen los paquetes raíz en JaCoCo; corregido agregando los nombres exactos. Hallado al revisar el commit de un compañero que ya lo sospechaba. Se endureció además el extractor de bugs de la Sala de control: dejó de cortarse en la primera línea en blanco de la tabla, tras perder filas por ese motivo tres veces (`BUG-092`) | #37 | Build completa con Docker: 825 pruebas, 0 fallos, cobertura real 90.2%/97.6%; con el umbral subido a 99.9% en una copia descartable del `pom.xml`, la regla vieja seguía en verde y la corregida falla citando los paquetes reales · `generarDatos()`: 90 bugs, `BUG-091` y `BUG-095` salen `Abierto` |
| RF044 · RF045 | func | `BUG-095` cerrado: `AdministrarCuentaService.reactivar` registraba al reactivado como autor de su propia reactivación en la bitácora de auditoría (`RF045`: quién cambió el acceso de quién); ahora registra al administrador. Diagnóstico de Yordy, corrección y prueba de este PR | #38 | `AdministrarCuentaServiceTest#reactivarDebeRegistrarAlAdministradorComoAutorYAlReactivadoComoSujeto` (`ArgumentCaptor` distingue autor de sujeto; el `any()` de las pruebas existentes no lo hacía) · build completa: 826 pruebas, 0 fallos |
| — | proceso | Pusheada la única etiqueta git que pertenece al historial actual (`pre-retiro-frontend`); las otras cinco locales son del repositorio público de cinco personas que `ADR-045` retiró a propósito y se dejaron sin pushear. `REC-016` resuelta: `Read(**/*secret*)` en `.claude/settings.json` acotada a `**/secrets/**`, `**/*secrets.*`, `**/*.secret`, `**/*.secrets` — ya no atrapa código fuente legítimo sobre secretos de TOTP | #39 | `git ls-remote --tags origin` → solo `pre-retiro-frontend` · `SecretoTotp.java` ya se puede leer |
| — | infra | Fase 1 del plan de Yordy (`plan-validacion-backend.md`), un punto: `backend-ci.yml` solo corría con cambios bajo `backend/**`, así que un PR que solo editara ese workflow y lo rompiera se fusionaba sin que el propio CI lo verificara. Agregado `.github/workflows/backend-ci.yml` a sus propios `paths`. Los otros dos workflows ya corren en todo push/PR, sin el mismo hueco | #40 | Esta misma edición dispara `Backend CI` al tocar el archivo listado en sus propios `paths` |
| RNF018 | infra | Fase 1 del plan de Yordy, otros dos puntos, decididos con criterio propio: (1) dos reglas de ArchUnit nuevas — `domain/` solo depende de Java (antes solo se vetaban Spring y Mongo por nombre; `domain/` ya era 100% Java puro, la regla solo lo protege) y `application/` no depende de tecnología concreta (web, seguridad, correo, Redis), permitiendo el cableado de Spring que ya usa (`@Service`, `@EventListener`, `@Async`, `@Value`) porque sacarlo es la Fase 4 del propio plan, no esta; (2) `ContratoOpenApiTest` ahora compara semánticamente parámetros requeridos, cuerpo requerido, códigos de respuesta y esquemas de seguridad por operación, no solo el conjunto de rutas — reutilizando el modelo de swagger-core que ya trae `springdoc-openapi-starter-webmvc-ui`, sin dependencia nueva | #41 | Ambas reglas probadas contra una violación real deliberada (una clase con `@JsonIgnore` en domain, un `@RestController` en application, un `required: true`→`false` en `openapi.yaml`) y revertida; build completa: 829 pruebas, 0 fallos, ArchUnit (7 reglas) y JaCoCo en verde |
| RNF006 | func | `BUG-091` cerrado: cola muerta real de la ingesta — `DocumentoFallidoDocumento` en Mongo (`documentos_fallidos`), `upsert` por hash con contador de reintentos, borrada al procesarse con éxito; expuesta en `GET /api/veedor/ingesta/fallidos` | #42 | `PipelineOrquestadorTest` (3 pruebas nuevas) y `IngestaFallidosControllerTest` (2 pruebas) · la prueba semántica de `ContratoOpenApiTest` (recién escrita en #41) detectó el endpoint nuevo y obligó a regenerar `openapi.yaml` — primera vez que atrapa un cambio real, no uno sintético · build completa: 834 pruebas, 0 fallos |
| — | proceso | Tres recomendaciones resueltas con criterio propio: `REC-006` (javadoc en `RateLimitConfig.java` sobre la trampa de `@WebMvcTest`), `REC-007` (`delete_branch_on_merge` activado en el repositorio) y `REC-013` (allowlist de gitleaks acotado al valor exacto de `JWT_SECRET`, ya no al archivo completo) | #43 | `REC-013` verificado con `gitleaks` real (Docker): el valor legítimo sigue sin hallazgo; un secreto distinto pegado en el mismo archivo (probado y revertido) sí se detecta |
| — | infra | Dependencias del backend: resilience4j 2.3.0→2.4.0, jacoco-maven-plugin 0.8.12→0.8.15, Maven wrapper 3.9.9→3.9.16, springdoc 2.8.6→2.9.1 — dentro de la rama 2.x, no choca con `ADR-059` (Dependabot) | #26, #27, #28, #29 | `Backend CI` completo (`./mvnw verify`) en verde sobre el `main` nuevo; `main` en verde en sus tres workflows tras la fusión (`2c6d6f7`) · ⚠️ `gitleaks` por `pull_request` fallaba por `BUG-089`, ajeno al cambio |
| — | infra | Testcontainers 1.21.3→1.21.4 y Maven wrapper 3.3.2→3.3.4 (Dependabot, dentro de la rama que `ADR-060` sigue soportando) | #33, #34 | `Backend CI` completo (`./mvnw verify`) en verde sobre el `main` nuevo |
| — | infra | Fase 2 del plan de Yordy (`plan-validacion-backend.md`), «estabilizar el estado»: tres defectos que dejaban el estado de un sector a merced del orden de llegada de las fuentes — `RevisarPropuestaIngestaService.aprobar()` fijaba el estado congelado al detectar el boletín, no el vigente al aprobarlo (`BUG-097`); el barrido de ventanas no era determinista con avisos solapados del mismo sector (`BUG-098`); y no respetaba un corte oficial del veedor todavía abierto (`BUG-099`). `ADR-061` define un único orden de severidad (`EstadoServicio.masSevero`) reutilizado en los dos servicios para resolver los tres de raíz. Cierra la Fase 2 completa: sus otros dos puntos (autor/sujeto al reactivar) ya estaban cubiertos por `BUG-095` | #44 | `EstadoServicioTest` (orden total y conmutatividad), `RevisarPropuestaIngestaServiceTest#aprobarUnaPropuestaCuyaVentanaYaVencioDebeFijarConServicioYNoElEstadoDetectado`, `ActualizarEstadosPorVentanaServiceTest#avisosSolapadosDelMismoSectorDebenDarElMismoResultadoEnCualquierOrden` y `#unCorteOficialAbiertoDebePrevalecerSobreUnAvisoDeIngestaVencido` · build completa con Docker: 842 pruebas, 0 fallos |
| RF044 | func | Fase 3 del plan de Yordy, primer punto («serializar y revalidar cambios del último administrador mediante un documento de control compartido»): `BUG-100` cerrado — `AdministrarCuentaService.exigirQueQuedeUnAdministrador` comprobaba el conteo de ADMIN activos y escribía la cuenta en dos pasos separados, sin exclusión mutua, así que dos suspensiones o despromociones concurrentes sobre dos ADMIN distintos podían dejar el sistema sin ninguno. `BloqueoDeAdministradoresPort`/`BloqueoDeAdministradoresMongoAdapter` (`ADR-062`) serializan el conteo y la escritura con un bloqueo nativo de Mongo, no el bloqueo de Redis que ya usa el proyecto para jobs de fondo (ese omite la tarea si Redis cae, semántica que no sirve para una petición HTTP) | #45 | `BloqueoDeAdministradoresMongoAdapterTest` (8, Mongo real) y `AdministrarCuentaServiceTest` (3 nuevas) · build completa: 853 pruebas, 0 fallos |
| — | infra | Fase 3 del plan de Yordy, segundo punto: Mongo local pasa a *replica set* de un nodo (`ADR-063`), prerrequisito de esta misma fase para transacciones multi-documento. `mongo-init-replica` inicia el replica set una sola vez, de forma idempotente; `backend` espera a que termine. Verificado en vivo con Docker real (backend arranca sano contra el replica set) y se encontró en el camino que los `scripts/sembrar-*.mjs` dejaban de conectar desde el host (`getaddrinfo ENOTFOUND mongo`); corregido con `?directConnection=true` en su URI por defecto. `docker-compose.prod.yml` no se toca | #46 | Verificado en vivo: `mongo-init-replica` idempotente (segunda corrida sale 0 sin reiniciar), `GET /actuator/health` → `UP` contra el replica set, `sembrar-sectores.mjs` reproducido en rojo y en verde (211 sectores) · build completa: 853 pruebas, 0 fallos |
| — | infra | Fase 3 del plan de Yordy, tercer punto: `GestionarCorteOficialService`, `EvaluarConsensoService`, `ActualizarEstadosPorVentanaService` y `RevisarPropuestaIngestaService` agrupan ahora el guardado de estado y su evento de bitácora en una transacción multi-documento real (`TransaccionPort`/`TransaccionMongoAdapter`, `ADR-064`) sobre el *replica set* de `ADR-063` — antes una falla a mitad de camino podía dejar el estado movido sin su evento, o viceversa. `SectorMongoAdapter` difiere el correo/push/SSE (`SectorActualizadoEvent`) y la invalidación de la caché `sectores` hasta que la transacción confirma. Aparte, corrido el simulacro de restauración de `BUG-088` que seguía pendiente: respaldo y restauración contra `docker-compose.yml` poblado, sin fallos | #47 | `TransaccionMongoAdapterTest` (5, reintento y rollback con un `PlatformTransactionManager` de mentira) y `TransaccionMongoAdapterIntegrationTest` (3, rollback real contra Mongo) · `SectorMongoAdapterTransaccionTest` (4, Mongo+Redis reales: evento y caché diferidos hasta el commit) · pruebas actualizadas en los 4 servicios · build completa: 874 pruebas, 0 fallos · simulacro de `BUG-088`: 21534 documentos restaurados, 0 fallos, `GET /api/sectores` y `GET /api/bitacora` en 200 |
| RNF018 | infra | Fase 4 del plan de Yordy, «completar la separación de capas» (`ADR-065`): `application/` sin ningún import de Spring — `CasosDeUsoConfig` registra los casos de uso por escaneo y declara a mano los seis que leen `aguavigia.*`; los listeners, `SectorActualizadoEvent` y el aviso SSE pasan a `infrastructure/eventos`, y `SseSectoresBroadcaster` de `api/` a `infrastructure/sse`; el orden y la paginación del histórico de cortes, el sustento de un evento de la bitácora y el filtro de Open311 salen de los controladores a tres casos de uso (`Pagina.deLista` sustituye la paginación duplicada). No se dividen `AdministrarCuentaService` ni `ConfigurarSegundoFactorService` (guardas compartidas, decisión en el ADR) | #48 | ArchUnit: `applicationSoloDebeDependerDeDominioJavaYLogging` y `apiNoDebeEscucharEventosNiProgramarTareas` · `PaginaDeListaTest` (5), `NotificarSuscripcionesListenerTest`, `AvisoSseSectorListenerTest` · pruebas de controlador y del contrato OpenAPI sin cambios y en verde · build completa: 881 pruebas, 0 fallos · el arranque real con el nuevo cableado se comprobó en la Fase 5 |
| — | infra | Fase 5 del plan de Yordy, «entregar el entorno al frontend»: recorrido de los flujos HTTP contra un entorno construido desde cero con el nuevo `scripts/verificar-flujos.mjs` (mapa, CORS, suscripción con correo en MailHog, reporte, consenso, foto, SSE, bitácora, estadísticas, panel con alta de TOTP, cortes, invitación, revocación). Corrige `BUG-101` (el volumen `fotos-data` nacía como `root` y toda subida de foto daba 500 en una instalación limpia) y abre CORS en el perfil `docker` a los dev servers habituales (`CORS_ORIGENES`; producción sigue cerrado); actualiza la guía de consumo, el entorno local, el comportamiento del sistema y el plan de pruebas §8 | #49 | `CorsPorPerfilTest` (3, rojo sin el cambio) · paso de `despliegue-ci.yml` que falla si `/app/data/fotos` falta o no es de `aguavigia` · `verificar-flujos.mjs`: 21 pasos, 0 fallos, dos pasadas · build completa: 884 pruebas, 0 fallos |
| — | proceso | Sprint 6 (entrega final) abierto y cerrado el 2026-09-24: `sprint-6.md` con review y retrospectiva, `guion-de-demo.md` ensayado de punta a punta contra el backend, `credenciales-y-accesos.md` (inventario sin valores), mediciones locales de `RNF027` repetidas (lectura pública a 500 y 1 000 req/s, `RNF002` y 2 000 SSE; nginx, escritura en pico y 10 000 SSE **sin repetir**) y rotación de 5 entradas de la bitácora a `historico/bitacora-sprint-2.md` | #50 | `scripts/verificar-flujos.mjs`: **21 pasos, 0 fallos** sobre una copia limpia de `main` (`488bb6c`) y sobre la base de la demo; Índice de Cumplimiento de la API igual a un cálculo independiente en `mongosh`; CI del PR en verde (compose, dependencias, gitleaks). Sin cambios de código: no se re-ejecutó `./mvnw verify` |
| RF041 | func | Alertas por Telegram **armadas y apagadas hasta tener el bot** (`ADR-066`): `SuscripcionTelegram`, `ProcesarMensajeTelegramService` (`/suscribir`, `/baja`, `/estado`, `/mis`, `/ayuda`), `EnviarAlertaPushService` que avisa a los chats suscritos y da de baja a quien bloquea al bot, `TelegramApiAdapter` con sondeo (`getUpdates`, sin webhook), persistencia en `suscripciones_telegram` y baja que borra el id del chat (`RNF009`). Sin `TELEGRAM_BOT_TOKEN` el canal queda desactivado. Reemplaza el simulacro de M14. **Cubierto en parte:** no probado contra Telegram real | #52 | `SuscripcionTelegramTest`, `ProcesarMensajeTelegramServiceTest`, `EnviarAlertaPushServiceTest`, `TelegramApiAdapterTest` (servidor HTTP falso), `SuscripcionTelegramMongoAdapterTest` (Mongo real); `./mvnw verify`: 929 pruebas, 0 fallos; de extremo a extremo en Docker con un Telegram falso (suscribir, estado, aviso, baja automática del chat bloqueado y `/baja`) |


## Sprint 7 — frontend nuevo (abierto el 2026-09-25)

| RF/RNF | Tipo | Qué | PR | Prueba |
|---|---|---|---|---|
| — | andamio | F0 del frontend nuevo (`plan-frontend.md`, `ADR-067`; `ADR-029` reemplazado): `frontend/` con Vite 8, React 19 y TS `strict`; `tokens.css` copiado de `DESIGN.md`; cliente tipado con openapi-fetch y `api:sync`/`api:check` contra `backend/openapi.yaml`; normalización RFC 7807 y cierre de sesión ante `401`; `null` → «Sin datos verificados»; proxy de Vite a `:8081`; `frontend-ci.yml`; Playwright y Chrome DevTools MCP. **Sin requisito cubierto todavía:** las partes de UI de `RF001`–`RF004` y `RNF001`/`RNF012`–`RNF016` se reactivan en F6. ⚠️ El proxy solo se probó contra un servidor falso en `:8081`, no contra el backend real | #54 | `tokens.test.ts` (falla si `tokens.css` diverge de `DESIGN.md`), `cliente.test.ts`, `estados.test.ts`, `Muestrario.test.tsx` y `muestrario.spec.ts` (360 y 1280 px): 22 unitarias y 6 E2E · `api:check` falla con un valor agregado al enum de estado en `openapi.yaml` · `Frontend CI` en verde en el PR (lint, tipos, contrato, pruebas, build y E2E) |
| — | andamio | F1 del frontend nuevo, en parte (recoge el PR #55, cerrado sin fusionar): glifos del mapa Noto Sans locales (`ADR-068`) y `scripts/preparar-mapa-base.sh`; glifos SVG de los cuatro estados y de «sin datos» con trama de 1 px; contraste AA medido en los dos temas y acento claro `#06747f` en `DESIGN.md` y `tokens.css` (`REC-019`); guía integral (`ADR-069`); identidad formal propuesta (`ADR-070`, `docs/diseno/identidad.md`) y skills `disenar-frontend` y `revisar-diseno`. **Sin requisito cubierto:** la identidad formal está pendiente de aprobación visual y sus tokens y fuentes no se migraron; el extracto PMTiles sigue bloqueado | #56 | `iconos.test.ts` (sin colores fijos), `contraste.test.ts` (pares permitidos con sus cifras en ambos temas), `muestrario.spec.ts` (360 y 1280 px, ambos temas, tema manual que persiste): 111 unitarias y 14 E2E · `Frontend CI` en verde en el PR (lint, tipos, contrato, pruebas y build), además de compose, dependencias y gitleaks |
| RF003 · RF026 · RF027 | func | Ampliación mínima de la API para el frontend (`ADR-073`): `GET /api/bitacora` filtra por `sectorId`, `tipo`, `desde` (inclusivo) y `hasta` (exclusivo) en Mongo, con total y `Link` del filtro; `GET /api/sectores` y `/{id}` publican `verificadoEn` (consenso con mayoría clara, corte del veedor o boletín aprobado, como mucho cada 5 min por sector, sin SSE ni aviso). Además, F1 en parte: identidad contenida (`ADR-071`, cardenillo y latón, Newsreader local solo en marca y titulares) migrada en `DESIGN.md`, `tokens.css` y el muestrario; PMTiles con Git LFS decidido (`ADR-072`), sin extracto todavía; `BUG-102` y `BUG-103` cerrados. **Requisitos ya cubiertos antes:** esto los amplía, no mueve la cobertura. ⚠️ `openapi.yaml` se editó a mano (sin regenerar) y el squash llegó a `main` con un trailer de coautoría de Claude (`BUG-104`) | #57 | `FiltroBitacoraTest`, `BitacoraControllerTest`, `EventoBitacoraMongoAdapterTest` (Mongo real), `SectorTest`, `EvaluarConsensoServiceTest`, `SectorMongoAdapterTest` y `SectorMongoAdapterCacheTest` (Mongo y Redis reales), `ContratoOpenApiTest`; frontend `contraste.test.ts`, `tokens.test.ts` y `muestrario.spec.ts`: 127 unitarias y 16 E2E · CI en verde en el PR: `Backend CI` completo con Docker, `Frontend CI`, compose, dependencias y gitleaks |
| — | proceso | Registro de la fusión del #57 y cierre de `BUG-104`: `scripts/verificar-autoria.sh`, en el workflow `autoria.yml` (job «Commits sin firma de la IA»), falla si un commit del PR lo firma la IA o trae un trailer `Co-authored-by`; `CLAUDE.md` lo señala como refuerzo de la regla de autoría. Además, prototipos de F1 publicados en un Artifact privado (ocho pantallas, 390–1440 px, ambos temas, contraste medido). **F1 sigue abierto:** falta la revisión en un teléfono real y el extracto PMTiles | #59 | `verificar-autoria.sh` en rojo contra el rango real del #57 (`567ab57..1ff007c`, commit `aeb510b`) y contra `752806c`, en verde contra `0bc06da..567ab57` · CI del PR en verde, con el job de autoría corriendo por primera vez, además de compose, dependencias y gitleaks · el squash `1818723` pasó `verificar-autoria.sh` ya en `main` |

---

## Estado de cobertura de requisitos

Se actualiza al cerrar cada sprint. Es el insumo directo de `docs/ingenieria/matriz-trazabilidad.md` y
debe coincidir con ella: **46 RF = 40 implementados + 5 descartados (RF032–RF036) + 1 armado sin conectar (RF041)**.
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
| M14 Alertas push | 1 | 0 | 0% — RF041 armado (PR #52, `ADR-066`): Telegram construido y probado contra un servidor falso, apagado hasta tener `TELEGRAM_BOT_TOKEN`; cuenta como implementado cuando se pruebe contra el bot real |
| M15 Cuentas y permisos | 5 | 5 (RF042–RF046) | 100% |
| **Total funcionales** | **46** | **40** | **87%** |
| **No funcionales** | **27** | **17** | **63%** |

Los 17 RNF verificados: RNF002–RNF011, RNF017, RNF018, RNF020 y RNF022–RNF025. `RNF006` bajó de
verificado a parcial el 2026-09-22 (`BUG-091`: la matriz lo marcaba ✅ sin que exista la cola muerta
que pide el requisito) y volvió a subir el mismo día, cerrado con la cola real (`documentos_fallidos`
en Mongo, `GET /api/veedor/ingesta/fallidos`). Los otros diez: RNF001 y
RNF012–RNF016 **retirados por alcance** (interfaz, `ADR-048`) · RNF019 descartado (`ADR-025`) · RNF021 y RNF027
parciales · RNF026 sin verificar, no aplica al entorno local (`ADR-057`).

---

<!--
Rotación: al cerrar el sprint N+2, el sprint N se comprime a una sola fila de resumen
(módulos tocados, requisitos cubiertos, PRs) y el detalle se archiva en
docs/gestion/historico/implementaciones-sprint-<N>.md. Ver protocolo-de-contexto.md §5.
-->
