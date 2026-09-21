# Bitácora de sesiones — Sprint 0 (rotada)

Entradas rotadas desde `docs/gestion/bitacora-sesiones.md` el 2026-09-21 (la bitácora superó las 30 entradas; el Sprint 0 está cerrado).

### 2026-08-09 · `docs/revision-prs-62-69`
**Qué:** Revisión de código de los PRs #62–#69. 13 hallazgos registrados (`BUG-017`–`BUG-029`; tres,
sobre herramientas ya retiradas, se eliminaron después), el más grave `BUG-017` (S1:
`FormularioReporte.tsx` muestra éxito aunque el envío falle) y `BUG-018` (`BUG-008` no quedó
corregido del todo — el `style` inicial de `MapaCartagena.tsx` todavía usa `?? 'CON_SERVICIO'`,
aunque el registro lo marca `Cerrado`). Ninguno corregido en esa sesión: eran archivos de frontend y
de la sala de control, fuera de la capa de infraestructura.
**Sigue:** Corregir `BUG-017`/`BUG-018` (los dos más graves) primero.

### 2026-08-08 · `vista-previa-total`
**Qué:** Misión 1 (Dividir rama gigante y reporte offline interactivo con IA). Construcción masiva de todas las vistas (Estadísticas, Bitácora, Mapa, Veedor, Reportar) con componentes nativos interactivos, glassmorphism y tooltips SVG puros (offline). Arreglamos `BUG-016` (cortes en línea SVG por stroke-dasharray) y quitamos la clave mock 1234 del panel del Veedor (`BUG-004`).
**Sigue:** PR a la rama principal para integrar esta base de UI y continuar conectando con la sala de control y backend.

### 2026-08-08 · `feature/sprint1-mongo-y-api-sectores`
**Qué:** Entregables de Sprint 1: adaptador Mongo de `SectorRepository` (índice `2dsphere`,
conserva la geometría sembrada al guardar), adaptador de `RelojPort`, `GET /api/sectores` y
`/api/sectores/{id}`, errores RFC 7807 y `backend/openapi.yaml` generado desde la app — **publica el
contrato OpenAPI**.
`./mvnw clean verify` → 34 pruebas, 0 fallos. `ADR-014` (estado nulo en vez de `CON_SERVICIO` sin
dato verificado) y `ADR-015` (consultas de lectura van al puerto de salida, sin invadir
`application/`). Encontrados `BUG-007` (Testcontainers vs. Docker Engine 29, corregido
aquí mismo) y `BUG-008` (el mapa pinta de verde los 211 sectores sin dato).
**Sigue:** Abrir PR; el frontend debe tener presentes los dos avisos del contrato (`estado`
anulable, OpenAPI 3.0.1). Sprint 2: `POST /api/reportes` y rate limiting.

### 2026-08-08 · `feature/sprint2-redis-consenso`
**Qué:** Adelanto de Sprint 2 mientras el PR #56 (Sprint 1) sigue abierto: adaptador Redis de
`ContadorReportesPort` (ventana deslizante con `ZSET`, TTL de retención) para RF009–RF011. Encontrado
y corregido `BUG-009` (bean `RedisTemplate<String,String>` ambiguo con `stringRedisTemplate` de
Spring — afectaba a cualquier futura inyección por tipo, no solo a este adaptador).
`./mvnw clean verify` → 29 pruebas, 0 fallos, incluida integración contra `redis:7-alpine` real.
**No se tocó** `POST /api/reportes` ni `EvaluarConsensoUseCase`: son casos de uso de `application/`,
que sigue vacía. El resto del backlog de Sprint 2 (rate limiting HTTP, caché del mapa,
SSE) sigue pendiente y depende de decisiones de diseño.
**Sigue:** Rama publicada sin PR todavía. Cuando exista `EvaluarConsensoUseCase`, este adaptador queda
listo para conectarse sin cambios.

### 2026-08-08 · `feature/sprint3-jwt-veedor`
**Qué:** Adelanto de Sprint 3, tercer PR de la sesión: infraestructura JWT del panel del
veedor (RF019, RNF011) — `JwtProvider`, `JwtAuthenticationFilter`, `SecurityConfig`
(`/api/veedor/**` protegido, el resto público) y `POST /api/veedor/sesion`. `ADR-016`: credencial
única compartida (BCrypt en `VEEDOR_PASSWORD_HASH`), no cuentas individuales — no existe entidad
`Usuario` en `domain/` todavía. Encontrado y corregido `BUG-010` antes de
comitear (validación perezosa del secreto que casi tumbaba rutas públicas con 500).
`./mvnw clean verify` → 35 pruebas, 0 fallos. Verificado además en vivo: login correcto (200+token),
incorrecto (401), ruta protegida sin token (401), con token válido pasa el filtro (404, no 401/403),
expiración exacta de 8h, `/actuator/health` sigue público.
**No se tocó** el CRUD de cortes oficiales ni la moderación de reportes: ambos necesitan casos de
uso de `application/` (`GestionarCorteOficialUseCase` y uno de moderación aún sin definir). Señalado
en el ADR, sin construirlo: no hay rate limiting en el login todavía.
**Sigue:** Rama publicada sin PR todavía. Cuando se definan los casos de uso de M5, el controlador
que los use puede vivir bajo `/api/veedor/**` sin tocar `SecurityConfig`.

### 2026-08-08 · `feature/sprint4-prefiltro-dedup`
**Qué:** Adelanto de Sprint 4, cuarto PR de la sesión: la parte del pipeline M9 que no toca
la red — `DocumentoCrudo` (normalización + hash SHA-256), `PrefiltroDeterminista` (9 palabras clave
ya aprobadas en `pipeline-ingesta-datos.md`, sin ampliarlas por cuenta propia) y
`DeduplicadorReciente` (Redis, ventana de 7 días). `./mvnw clean verify` → 41 pruebas, 0 fallos.
**No se construyeron** `AcuacarApiCollector` ni `RssCollector`: `COLLECTOR_USER_AGENT` sigue con un
correo de contacto literalmente `pendiente`, y hacerles una petición real a Acuacar/Google
News/Zona Cero con esa identidad sería incoherente con la ética de datos del proyecto — faltaba el
correo real. Tampoco se construyó la capa de IA: sin `ANTHROPIC_API_KEY` no se puede probar
ni una vez, y el propio diseño avisa que hay que verificar la firma del SDK contra código real
antes de darla por buena (la capa de IA se descartó después, `ADR-025`).
**Sigue:** Cuatro PRs de esta sesión (#56, #57, #58 y este, sin número todavía) abiertos. En cuanto
se fije el correo real, los colectores se conectan directo después del prefiltro sin rehacer nada de esto.

### 2026-08-08 · `feature/sprint2-rate-limiting-http`
**Qué:** Quinto PR de la sesión: rate limiting HTTP genérico (Redis `INCR`+`EXPIRE`), pendiente de
Sprint 2 y hueco señalado en `ADR-016`. `RateLimitingInterceptor` + `RateLimitConfig`, configurable
por `application.yml` (`aguavigia.rate-limit.reglas`), opt-in, sin depender de ningún PR sin
fusionar. `ADR-018`: clave por IP, no por huella de dispositivo (eso es de M2/negocio, no de este
componente genérico). `./mvnw clean verify` → 28 pruebas, 0 fallos. Verificado en vivo contra la
app corriendo y Redis real: 3 peticiones pasan, la 4ª y 5ª reciben `429` con `Retry-After`.
Confirmado (y documentado en el código) que `/actuator/**` no queda cubierto porque Actuator usa su
propio `HandlerMapping`. En el camino: diagnosticado que Git Bash (MSYS) reescribe rutas tipo
`/actuator/health` a rutas de Windows al pasarlas por variable de entorno — no es un bug del
proyecto, es del entorno de verificación local (`MSYS_NO_PATHCONV=1` lo evita).
**Sigue:** Cuando el PR #58 (JWT del veedor) se fusione, activar
`aguavigia.rate-limit.reglas[0].ruta=/api/veedor/sesion` con `limite: 5, ventanaSegundos: 300`.

### 2026-08-08 · `feature/cache-redis`
**Qué:** Sexto PR de la sesión: configuración de caché sobre Redis (`@EnableCaching` +
`RedisCacheManager`, valores en JSON no serialización Java), pendiente de Sprint 2 ("caching de
respuestas del mapa") y Sprint 5 ("decorador de caché") — misma pieza para ambos. TTL configurable
por `application.yml`, 30s por defecto, con overrides por nombre de cache. `./mvnw clean verify` →
27 pruebas, 0 fallos, incluida verificación del TTL real vía inspección directa de Redis.
**Sigue:** Cuando exista un caso de uso de consulta real que valga la pena cachear
(`GET /api/sectores` una vez fusione el PR #56), anotarlo con `@Cacheable("sectores")`.

### 2026-08-08 · `feature/dockerfile-frontend-y-jacoco`
**Qué:** Registrados en `registro-de-implementaciones.md` los PRs #27 y #33 (Dockerfiles backend/frontend,
JaCoCo, perfiles de Spring, `/actuator/health`), fusionados sin registrar. Actualizado
`docs/gestion/sprint-0.md` (32 PRs, 4 bugs cerrados), desactualizado desde el 2026-08-07. Confirmado
que `ReglaDeOroArchitectureTest` ya falla el Backend CI.
**Sigue:** Testcontainers espera a que se cree el primer adaptador real de infraestructura (Sprint 2).

### 2026-08-08 · `feature/dockerfile-backend`
**Qué:** Verificado el entorno reproducible con evidencia real (encontró y corrigió `BUG-003` en el
camino). Resueltos los conflictos de los PR #19, #24 y #25 verificando build/tests después de cada
uno. Registrado `BUG-004` (contraseña mock en `PaginaVeedor.tsx`). Escrito el Dockerfile multi-etapa
del backend y activado en `docker-compose.yml`, revisado con hadolint pero sin construir la imagen
(sin daemon Docker).
**Sigue:** Confirmar `docker compose build backend` en una máquina con Docker completo. Pendiente:
publicar el contrato OpenAPI.

### 2026-08-08 · `feature/dominio-sprint1`
**Qué:** Modelado el dominio de M3/M6 (PR #21): Value Objects, entidades (`CorteAgua` con Builder),
`domain/port/in` y `port/out`, test de ArchUnit. 23 pruebas, 0 fallos. Dominio y puertos listos.
**Sigue:** Implementar los puertos de infraestructura contra `domain/port/out`.

### 2026-08-07 · `feature/sprint2-reportar`
**Qué:** M2 completado (UI): FormularioReporte con selecciones accesibles, sin registro (RF005), 2 toques desde el mapa leyendo sector de URL (RF008) y opción de ubicación (RF007). Pantalla de éxito. Usa datos mock provisionales. PR pendiente de crear.
**Sigue:** Crear PR y, cuando exista el contrato OpenAPI, integrar `POST /api/reportes` con TanStack Query.

### 2026-08-07 · `docs/alistamiento-sprint0`
**Qué:** Auditoría de coherencia de todo el repositorio (8 PRs, 4 ramas, 20 documentos). Corregidas 10
contradicciones entre documentos y la realidad del repositorio: `ADR-009` (el Sprint 0 admite
andamiaje, no funcionalidad) y `ADR-010` (branch protection es política, no candado). Registrados los
8 PRs del Sprint 0, `BUG-001` y `BUG-002` —encontrados en revisión y nunca registrados—. Creado
`sprint-0.md`. Normalizadas 6 fechas escritas en UTC a hora de Cartagena.
**Sigue:** Verificar el entorno reproducible —el PR #10 ya lo habilitó— y regularizar los datos
simulados (`SECTORES_MOCK`) del PR #12.

### 2026-08-07 · `feature/backend-base`
**Qué:** Creado el proyecto base de `/backend` (issue #9, PR #10): Maven, Java 21, Spring Boot 3.4.1,
estructura vacía de Arquitectura Limpia. `./mvnw verify` → BUILD SUCCESS local y en CI. El comando de
verificación del entorno (`docker compose config -q && ls backend frontend`) ya pasa completo.
**Sigue:** Verificar el entorno con ese comando.

### 2026-08-07 · `docs/diseno-dominio-sprint0`
**Qué:** Diseño adelantado del dominio de M3/M6 (`docs/ingenieria/modelo-de-dominio.md`).
Corregida la duplicación de `SuscribirseService` (pertenece a M4).
**Sigue:** Crear el esqueleto de `/backend` en Sprint 0.

### 2026-08-07 · `feature/sprint3-bitacora`
**Qué:** M8 (Bitácora - UI) maquetada. Componente `PaginaBitacora` con formato de línea de tiempo y componentes `InsigniaEstado`. Cumple RF026 y RF027 visualmente usando datos mock.
**Sigue:** Integrar `GET /api/bitacora` real cuando exista el contrato OpenAPI.

### 2026-08-07 · `feature/sprint3-panel-veedor`
**Qué:** M5 (Panel Veedor UI) y M7 (Dashboard Recharts) maquetados. Componente `PaginaVeedor` con auth simulada y `PaginaEstadisticas` con gráficos (RF023, RF024). Agregado enlace en Encabezado. Todo usando datos mock.
**Sigue:** Crear PR e integrar las APIs cuando exista el contrato OpenAPI.

### 2026-08-07 · `feature/sprint2-reportar`
**Qué:** M1 completado: MapaCartagena (Leaflet + GeoJSON de 213 barrios), ListaSectores (RF004), InsigniaEstado, EtiquetaFrescura, useFrescura, tipos-dominio. PR #12 abierto.
**Sigue:** Fusionar el PR #12. Cuando exista el contrato OpenAPI, reemplazar SECTORES_MOCK con TanStack Query → GET /api/sectores.

### 2026-08-07 · `feature/sprint5-pwa-tests`
**Qué:** Configurada PWA (`vite-plugin-pwa`) para soporte offline y cacheo de `barrios-cartagena.geojson`. Configurado Vitest + React Testing Library y agregada la primera prueba (`InsigniaEstado.test.tsx`). PR pendiente.
**Sigue:** Conseguir iconos PWA (192 y 512) para completar el manifiesto y crear PR.

### 2026-08-07 · `feature/sprint3-panel-veedor`
**Qué:** Esqueleto de `/frontend` creado: React 19 + Vite + TypeScript + Tailwind CSS v4. Tokens de `DESIGN.md` como custom properties CSS (paleta, temas claro/oscuro, tipografía, estado del servicio). `useTheme` hook + `SelectorTema` + `Encabezado` + rutas placeholder para M1, M2, M7, M8. Dev server en `localhost:5173`.
**Sigue:** Fusionar el PR.

### 2026-08-07 · `feature/sprint0-infraestructura`
**Qué:** Creada `develop`, `.env.example`, `docker-compose.yml` base (Mongo+Redis+Mailhog) y workflows
de GitHub Actions (backend-ci, frontend-ci, secret-scan). Abierto PR #1 hacia `develop`. Sin permiso
`admin` en el repo remoto, no se pudo configurar branch protection (`ADR-010`).
**Sigue:** Fusionar el PR #1. Falta que existan `/backend` y `/frontend` para que el entorno pueda
verificarse.

### 2026-08-07 · `main`
**Qué:** Auditoría completa de la documentación. Se crearon `docs/gestion/` y el sistema de registro
(bitácora, bugs, implementaciones) y el protocolo de contexto. Ver `ADR-008`.
**Sigue:** Leer `docs/gestion/protocolo-de-contexto.md` antes de la primera sesión.

### 2026-08-06 · `main`
**Qué:** Auditoría de fuentes de datos con peticiones reales. Se corrigió el supuesto falso sobre el
`robots.txt` de Acuacar y se encontró su API REST (307 boletines). `ADR-004`, `ADR-005`.
**Sigue:** Reintentar GDELT, RCN, Caracol y W Radio con throttling (ver auditoría §8).
