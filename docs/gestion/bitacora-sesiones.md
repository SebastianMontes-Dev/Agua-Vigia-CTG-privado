# Bitácora de sesiones de trabajo

> Registro **append-only** de cada sesión de trabajo con IA que produjo un cambio en el repositorio.
> Existe para que la sesión siguiente arranque sabiendo dónde quedó todo,
> sin reconstruir una conversación de horas.
>
> **Para agregar una entrada: usa la skill `cerrar-sesion`.**
> **Formato: 3 líneas máximo.** Una entrada larga es una entrada que nadie lee.

---

## Cómo se lee esto

| Campo | Qué significa |
|---|---|
| **Fecha** | AAAA-MM-DD |
| **Rama** | Dónde quedó el trabajo |
| **Qué** | Una frase, en pasado, con el resultado — no la intención |
| **Sigue** | El siguiente paso concreto. Sin esto, la próxima sesión empieza decidiendo |

Referencias cruzadas: `ADR-NNN` · `BUG-NNN` · `RF0NN` · `archivo:línea`.
**Nunca se pega código aquí.**

---

## Preparación del backend

### 2026-09-24 · `chore/entorno-para-frontend`
**Qué:** Fase 5 del plan de Yordy: entorno construido desde cero (copia del repo sin `.env` ni datos, `up --build`, siembra) y recorrido con HTTP real por el nuevo `scripts/verificar-flujos.mjs`: 21 pasos, 0 fallos (plan de pruebas §8). Hallazgos: `BUG-101` (volumen de fotos como `root` → 500 al subir foto en instalación limpia; corregido en el `Dockerfile` y vigilado en `despliegue-ci.yml`), CORS cerrado en el perfil `docker` (ahora abre 5173/3000/4200, `CORS_ORIGENES`, `CorsPorPerfilTest`) y la trampa del `$` del hash en `.env` (documentada). Guía de consumo, entorno local y comportamiento actualizados. Build: 884 pruebas, 0 fallos. Un fallo de revocación de sesión visto una vez con estado sucio no se reprodujo (3 intentos) y queda sin explicar.
**Sigue:** Fusionar los PR #48 y el de esta rama (este apunta a la rama del #48) y registrar ambas implementaciones. Del dueño: `sprint-6.md` y qué cuenta como demo sin frontend propio (`REC-018`); un volumen `fotos-data` creado con la imagen vieja sigue siendo de `root` (borrarlo o `chown` una vez). Sin cubrir: `RF041`, TLS/proxy de producción y los 50 000 usuarios (`ADR-057`).

### 2026-09-23 · `refactor/casos-de-uso-sin-spring`
**Qué:** Fase 4 del plan de Yordy completa (`ADR-065`): `application/` sin ningún import de Spring (`CasosDeUsoConfig` los registra), listeners, evento y SSE movidos a `infrastructure/eventos` y `infrastructure/sse`, y la lógica de tres controladores (histórico de cortes, sustento de bitácora, Open311) pasada a casos de uso nuevos con `Pagina.deLista`. Dos reglas de ArchUnit nuevas; build completa: 881 pruebas, 0 fallos. No se dividieron `AdministrarCuentaService` ni `ConfigurarSegundoFactorService` (guardas compartidas, ver ADR).
**Sigue:** Al fusionar el PR, registrar la implementación. Fase 5 (entregar el entorno al frontend: arranque limpio, CORS, flujos HTTP reales) sin empezar. Del dueño: borrar `./respaldos-mongo-drill/`, `REC-018` y las 5 etiquetas git viejas.

### 2026-09-22 · `feat/transacciones-estado-bitacora`
**Qué:** Fase 3 del plan de Yordy, tercer punto: `TransaccionPort`/`TransaccionMongoAdapter` (`ADR-064`,
`TransactionTemplate` sobre `MongoTransactionManager`, reintento acotado ante `TransientTransactionError`).
`GestionarCorteOficialService`, `EvaluarConsensoService`, `ActualizarEstadosPorVentanaService` y
`RevisarPropuestaIngestaService` agrupan ahora su par estado+evento en una transacción real, probada con
rollback contra Mongo (`TransaccionMongoAdapterIntegrationTest`). `SectorMongoAdapter` difiere el
`SectorActualizadoEvent` y la invalidación de caché hasta que la transacción confirma
(`TransactionSynchronizationManager`, probado con Redis+Mongo reales en `SectorMongoAdapterTransaccionTest`).
TDD en las 5 piezas; build completa: 874 pruebas, 0 fallos. Aparte, se corrió el simulacro de restauración
de `BUG-088` (§5 de `respaldo-y-restauracion.md`, nadie lo había hecho): contra `docker-compose.yml`
poblado (211 sectores, 20 001 usuarios, 117 eventos), respaldo + restauración con `restore-mongo.sh`,
21 534 documentos, 0 fallos, `GET /api/sectores` y `GET /api/bitacora` en 200 con el mismo contenido —
sin poder probar el paso de la foto porque no había ningún reporte con `fotoUrl` sembrado.
Fusionado después en el PR #47.
**Sigue:** Fase 3, lo que falta: decidir si esta transacción reemplaza el documento de control de
`BUG-100`/`ADR-062` (insinuado en su propio "cómo se revierte", no urgente). Del dueño: pedir el commit
de esta rama si aprueba el resultado, y borrar a mano `./respaldos-mongo-drill/` (1.7M, `rm -rf` denegado
en `settings.json`). `docker-compose.prod.yml` sigue sin *replica set*, a propósito, sin tocar.

### 2026-09-22 · `fix/bug-100-bloqueo-ultimo-administrador` + `feat/mongo-replica-set-local`
**Qué:** Fase 3 del plan de Yordy, primeros dos puntos. `BUG-100` cerrado: `AdministrarCuentaService` no serializaba el conteo y la escritura del último administrador (`BloqueoDeAdministradoresPort`/Mongo, `ADR-062`). Mongo local pasa a *replica set* de un nodo (`ADR-063`, `mongo-init-replica` idempotente), prerrequisito para transacciones; se encontró y corrigió en el camino que los `scripts/sembrar-*.mjs` dejaban de conectar desde el host (`?directConnection=true`). PR #45 y #46 fusionados con un conflicto esperado entre sus dos ADR, resuelto y reverificado (853 pruebas, 0 fallos). `docker-compose.prod.yml` no se tocó, a propósito.
**Sigue:** Fase 3, lo que falta — agrupar estado y bitácora en transacciones (ya con el replica set listo), emitir notificaciones/invalidaciones solo tras confirmar (depende de lo anterior), y decidir si el respaldo/restauración manual de `BUG-088` ya satisface esta fase o hace falta un ensayo automatizado. Del dueño, sin tocar: `REC-018` y las 5 etiquetas git viejas; y el PR #43 quedó fusionado ya (confirmado, ver entrada de abajo).

### 2026-09-22 · `fix/fase-2-transicion-unica-por-sector`
**Qué:** Fusionados Dependabot #33/#34 y cerrada por completo la Fase 2 del plan de Yordy («estabilizar el estado»): `BUG-097`, `BUG-098` y `BUG-099` corregidos con TDD, `ADR-061` (`EstadoServicio.masSevero`) reutilizado en `GestionarCorteOficialService` y `ActualizarEstadosPorVentanaService`. PR #44 fusionado, registrado en `registro-de-implementaciones.md`. Build completa: 842 pruebas, 0 fallos.
**Sigue:** El PR #43 (`docs/rec-006-y-rec-007`, ver entrada de abajo) seguía abierto sin fusionar al cerrar esta sesión. Del dueño: `REC-018` y las 5 etiquetas git viejas. Del agente: Fase 3 del plan de Yordy (persistencia y concurrencia).

### 2026-09-22 · `docs/rec-006-y-rec-007`
**Qué:** Resueltas tres recomendaciones sueltas, con criterio propio. `REC-007`: `delete_branch_on_merge` activado vía API en el repositorio. `REC-006`: javadoc en `RateLimitConfig.java` documentando la trampa de `@WebMvcTest` (solo afecta a slices sobre `/api/veedor/sesion` y `/api/reportes/**`, las dos únicas rutas con reglas reales hoy — los dos tests afectados ya tenían el workaround). `REC-013`: allowlist de gitleaks acotado al valor exacto de `JWT_SECRET` en vez del archivo `entorno-local.md` completo; verificado con `gitleaks` real por Docker en las dos direcciones — el valor legítimo sigue pasando, un secreto distinto pegado ahí (probado y revertido) ahora sí se detecta.
**Sigue:** Del dueño: `REC-018` y las 5 etiquetas git viejas. Del agente: Dependabot #33/#34, luego la Fase 2 del plan de Yordy.

### 2026-09-22 · `fix/bug-091-cola-muerta-ingesta`
**Qué:** Cerrado `BUG-091`, decidido con criterio propio ("toma tú esa decisión"). Cola muerta real: `DocumentoFallidoDocumento` en Mongo (`documentos_fallidos`), `upsert` por hash con contador de reintentos (un documento roto no acumula una fila por ciclo para siempre), borrada al procesarse con éxito; expuesta en `GET /api/veedor/ingesta/fallidos`, mismo patrón que `IngestaSaludController` (sin puerto de dominio, `ADR-015`). El endpoint nuevo desincronizó `openapi.yaml` — la prueba semántica que se escribió ayer para la Fase 1 lo atrapó en su primer caso real, no sintético. Contrato y referencia de rutas regenerados (66 operaciones, 21 controladores, 37 esquemas). Build completa: 834 pruebas, 0 fallos.
**Sigue:** Del dueño: `REC-018` (qué cuenta como demo del Sprint 6) y las 5 etiquetas git viejas. Del agente: `REC-006`, `REC-007`, `REC-013`, Dependabot #33/#34, y la Fase 2 del plan de Yordy.

### 2026-09-22 · `test/archunit-application-sin-tecnologia`
**Qué:** Cerrados los dos puntos de la Fase 1 que quedaban, delegado por el dueño ("decide tú mismo la manera más profesional"). Investigado primero: `domain/` ya era 100% Java puro y `application/` ya no tocaba tecnología concreta, solo cableado de Spring (`@Service`, `@EventListener`, `@Async`, `@Value`, confirmado con `grep` sobre los 33 archivos que Spring sí toca ahí) — no hacía falta ningún refactor, solo reglas de ArchUnit que protegieran lo que ya era cierto. Se agregaron dos: dominio solo Java, aplicación sin tecnología concreta (con el cableado de Spring explícitamente permitido, con su porqué en el javadoc). Para OpenAPI: `springdoc-openapi-starter-webmvc-ui` (ya usado) trae transitivamente el modelo de swagger-core (`OpenAPI`, `Operation`, `Parameter`) y Jackson con soporte YAML — se pudo comparar semánticamente parámetros requeridos, cuerpo requerido, códigos de respuesta y seguridad por operación sin agregar ninguna dependencia. Las tres reglas nuevas se probaron contra una violación real deliberada cada una (revertida después). Build completa: 829 pruebas, 0 fallos.
**Sigue:** Fases 2 a 5 del plan de Yordy sin empezar (estabilizar estado/auditoría, persistencia/concurrencia, separación de capas, entrega al frontend).

### 2026-09-22 · `chore/ci-workflow-se-verifica-a-si-mismo`
**Qué:** Empezada la Fase 1 del plan de Yordy. De sus cuatro puntos, se resolvió el más chico sin necesitar decisión del dueño: `backend-ci.yml` solo corría con cambios bajo `backend/**`, así que un PR que rompiera ese workflow se fusionaba sin que el propio CI lo verificara. Agregado a sus propios `paths`. Los otros tres puntos de la Fase 1 quedan sin tocar, a propósito: JaCoCo ya se resolvió antes (`BUG-096`); ArchUnit («aplicación solo depende de dominio y puertos») falla hoy contra 33 archivos que usan `@Service`/`@Component`/`@EventListener`/`@Async`/`@Value` de Spring, algo que es la Fase 4 del propio plan de Yordy, no la 1; y la comparación semántica de OpenAPI (parámetros, cuerpos, esquemas, respuestas — hoy solo se comparan las rutas) es un desarrollo nuevo sustancial, no un ajuste.
**Sigue:** Del dueño: decidir el alcance de esos dos puntos antes de seguir — si adelantar la Fase 4 para poder escribir la regla de ArchUnit que pide la Fase 1, o suavizarla; y si vale la pena construir la comparación semántica de OpenAPI ahora o más adelante.

### 2026-09-22 · `chore/acotar-regla-secret`
**Qué:** Se revisaron las 6 etiquetas git locales antes de pushearlas todas: solo `pre-retiro-frontend` pertenece al historial actual, las otras cinco son del repositorio público de cinco personas que `ADR-045` retiró a propósito — quedaron sin pushear. Delegado por el dueño, `REC-016` se resolvió acotando `Read(**/*secret*)` en `.claude/settings.json` a cuatro patrones más precisos. El harness bloqueó como "auto-modificación" la edición que agregaba el comentario explicativo dentro del propio archivo (la regla en sí sí se aplicó); quedó documentado solo en `recomendaciones-ia.md`.
**Sigue:** Del dueño: decidir qué hacer con las 5 etiquetas locales viejas (borrarlas o dejarlas). Del agente: seguir con la Fase 1 del plan de Yordy (ArchUnit + comparación semántica de OpenAPI).

### 2026-09-22 · `fix/bug-095-autoria-reactivar`
**Qué:** Cerrado `BUG-095`: `AdministrarCuentaService.reactivar` registraba al reactivado como autor de su propia reactivación. Corregido a `autor` y agregada una prueba con `ArgumentCaptor` que distingue autor de sujeto — las pruebas de auditoría existentes usaban `any()` para ambos y no lo habrían detectado. Build completa: 826 pruebas, 0 fallos.
**Sigue:** `BUG-091` (cola muerta de la ingesta) sigue abierto. Del dueño: la decisión sobre `REC-016` antes de seguir con la Fase 1 del plan de Yordy.

### 2026-09-22 · `fix/jacoco-check-no-op`
**Qué:** Al revisar el commit del compañero (`a4da6e5`) se confirmó `BUG-095` en código y se llevó su sospecha sobre JaCoCo más lejos: `jacoco:check` (`RNF017`) no evaluaba ninguna cobertura real desde siempre — `domain.*`/`application.*` no incluyen los paquetes raíz en JaCoCo. Verificado subiendo el umbral a 99.9% en una copia descartable del `pom.xml`: la build seguía en verde. Corregido y reverificado con la build completa (Docker): 825 pruebas, 0 fallos, cobertura real 90.2%/97.6% (`BUG-096`). Se endureció además el extractor de bugs de la Sala de control, que perdía filas por líneas en blanco sueltas por tercera vez.
**Sigue:** `BUG-095` (autor incorrecto al reactivar una cuenta) y `BUG-091` (cola muerta de la ingesta) siguen abiertos, sin tocar. El plan de 6 fases de `plan-validacion-backend.md` no se ejecutó, solo se revisó su primer hallazgo.

### 2026-09-22 · `main`
**Qué:** Se documentó en `docs/ingenieria/plan-validacion-backend.md` una secuencia de seis fases con pruebas de salida, sobre `6500e25`; se registró `BUG-095`.
**Sigue:** Iniciar la fase 0: ejecutar la suite completa con Docker y verificar la referencia histórica del frontend antes de corregir los documentos que la citan.

---

## Sprints 3, 4 y 5

### 2026-09-22 · `docs/cerrar-sprints-3-y-4`
**Qué:** Delegado por el dueño (`REC-017`), se escribieron y cerraron retroactivamente `sprint-3.md`, `sprint-4.md` y `sprint-5.md` contra el código y `matriz-trazabilidad.md`, sin inventar una ceremonia que no ocurrió. ⚠️ `sprint-3.md` se fusionó por error dentro del PR #35 (CI de secretos): se escribió en esa misma rama mientras el CI corría, y un `git add -A docs` posterior lo arrastró — corregido en el registro, no en el historial de git (ver la fila de #35 en `registro-de-implementaciones.md`). El Sprint 5 quedó redefinido a solo cobertura de backend (su parte de interfaz es alcance retirado, `ADR-048`). Al verificar el Sprint 4 se encontró `BUG-091`: la matriz marcaba `RNF006` (cola muerta de la ingesta) ✅ sin que exista tal cola en el código — corregida a parcial, y bajó la cobertura de RNF de 17/27 a 16/27 en el registro. El Sprint 6 se redefinió para local (`ADR-057`) pero no se cerró: la pregunta de qué cuenta como demo sin frontend propio se separó en `REC-018`, pendiente del dueño. Cerrar varios sprints de golpe destapó tres bugs más en el generador de la Sala de control: una fila `Abierto` con la palabra «parcial» en su prosa se clasificaba mal (`BUG-092`), el «sprint activo» se calculaba mal — doblaba el avance por encima del 100% — en cuanto el último `sprint-N.md` documentado ya estaba cerrado (`BUG-093`), y ese mismo arreglo hizo que el aviso de secciones vacías reventara al toparse con un activo sin archivo (`BUG-094`). La Sala de control ahora corre sin error y da 85,7% (6/7 sprints cerrados).
**Sigue:** Del dueño: decidir `REC-018` y, aparte, `BUG-091` (construir la cola muerta o replantear `RNF006`).

## Sprint 2

### 2026-09-22 · `chore/cerrar-dependabot-testcontainers`
**Qué:** Se decidió el #2 de Dependabot, delegado por el dueño: el fallo no es transitorio (el BOM de Testcontainers 2.0.5 ya no fija la versión de `testcontainers-junit-jupiter` ni `testcontainers-mongodb`, confirmado en el log del `Backend CI`), así que se cerró con el mismo criterio que `ADR-059` y se registró `ADR-060`. Dependabot ya no propone ese salto. Fusionado como #32 con `gitleaks` en rojo solo por `BUG-089`; no queda ningún PR abierto en el repositorio.
**Sigue:** Del dueño: `permissions` en `secret-scan.yml` (`BUG-089`, `REC-016`) y la hoja de ruta de los Sprints 3–6 (`REC-017`).

### 2026-09-21 · `chore/registrar-dependabot-26-a-29`
**Qué:** Se fusionó el #25 y el #30 (quitan el pendiente de `frontend/`, ya borrado del disco). Se cerraron #7 y #11 por `ADR-059`. De cuatro PR nuevos de Dependabot (#26–#29: resilience4j, jacoco, Maven wrapper, springdoc 2.9.1), todos menores, se actualizaron sus ramas y se fusionaron con `Backend CI` en verde; `main` quedó en verde en sus tres workflows (`2c6d6f7`). El #2 (Testcontainers 2.0) se reverificó y sigue roto de verdad: queda abierto.
**Sigue:** Del dueño: añadir `permissions` a `secret-scan.yml` (`BUG-089`, `REC-016`), decidir el #2 y la hoja de ruta de los Sprints 3–6 (`REC-017`).

### 2026-09-21 · `chore/ordenar-dependabot-y-documentos`
**Qué:** Se fusionó el #23 y, de Dependabot, #1, #4, #24, #8, #5 y #9 con el CI verde sobre el `main` nuevo (`Backend CI` completo en los dos de `pom.xml`); `main` quedó en verde en sus cuatro workflows. Se decidió no migrar a Spring Boot 4 (`ADR-059`) y Dependabot ya no propone ese salto ni el de springdoc. Se corrigieron contradicciones entre documentos: línea 1 corrupta de la matriz, RNF012–016 sin marcar como retirados, cobertura del registro (28/36 → 40/46 RF, 17/27 RNF), tabla de recomendaciones, plan de pruebas con staging inexistente y sprint 2 con filas 🟡 ya entregadas. Se registró `BUG-089` (`gitleaks` por PR falla con 403) y las `REC-016`/`REC-017`.
**Sigue:** Del dueño: añadir `permissions` a `secret-scan.yml` (bloqueado por la regla `Read(**/*secret*)`, `REC-016`), decidir el #2 (Testcontainers 2.0) y la hoja de ruta de los Sprints 3–6 (`REC-017`). `frontend/` ya se borró del disco (2026-09-21).

### 2026-09-21 · `feat/datos-de-demostracion`
**Qué:** Se publicaron 6 PR encadenados (#16 a #21) y se respondieron las decisiones del dueño: proyecto académico local sin hosting ni CDN (`ADR-057`), reportes 12 meses y eventos permanentes con índice TTL (`ADR-058`), Sprint 2 cerrado (`REC-014`), `aviso-corte.html` borrado, CORS abierto solo en `dev` para el frontend. Sembrador de 20 000 cuentas de demostración (nombres únicos, seis estados, dos roles), comprobado por la API: `X-Total-Count: 20001`, páginas y filtros en 17–58 ms. El CI encontró dos fallos de la rama 1 (`MAIL_USERNAME` sin definir en la validación del compose) y del workflow de secretos.
**Sigue:** Fusionar los PR en orden con squash, `@dependabot rebase` y fusionar #1, #4, #5, #8, #9. Pendiente del dueño: añadir `permissions: contents: read, pull-requests: read` a `secret-scan.yml` (no puedo abrirlo por tu regla sobre archivos «secret»), el token de Telegram (RF041) y borrar `frontend/` del disco.

### 2026-09-21 · `feat/ajustes-de-contrato-frontend`
**Qué:** Se tomaron las decisiones delegadas: confirmar/cancelar suscripción por POST (`ADR-054`), bitácora con conteo de sustento y detalle aparte (`ADR-055`), histórico público de cortes, población, cambio de clave con sesión y reenvío de verificación/invitación (`ADR-056`); 406 en vez de 500 (`BUG-087`); respaldo y restauración de Mongo corregidos y probados con autenticación (`BUG-088`). Contrato en 65 rutas; `./mvnw verify`: 823 pruebas en verde. Dependabot revisado, sin tocar.
**Sigue:** Commitear esta rama (nada commiteado) y publicar las 6 ramas. Sin hacer y solo del dueño: programar el respaldo, hosting/dominio/CDN, HA de Mongo y Redis, política de retención, cerrar el Sprint 2 (`REC-014`), `@dependabot rebase` y fusión de #1/#4/#5/#8/#9, RF041 y los 20 000 datos (falta saber de qué).

### 2026-09-21 · `chore/retirar-frontend`
**Qué:** Se retiró el frontend (`ADR-048`) y se pulió el backend para RNF027 (50 000 usuarios): `ADR-049`–`ADR-053`, `BUG-076`–`BUG-086`, `docs/api/` + `openapi.yaml` para el frontend nuevo y `docs/ingenieria/escalabilidad.md`. `./mvnw verify`: 782 pruebas en verde. Medido a escala reducida (nginx ~3 700 req/s con el backend al ~6 % de un núcleo, 10 000 SSE, escritura 63 001 peticiones a p95 36 ms); la medición destapó `BUG-085` y `BUG-086`. **50 000 NO está demostrado.**
**Sigue:** Hacer los commits (nada está commiteado; ramas sugeridas en el plan), borrar `frontend/` del disco a mano (`rm -rf` está denegado en `settings.json`), decidir cerrar el Sprint 2 (`REC-014`) y luego la prueba distribuida con ≥ 3 réplicas. Quedaron sin hacer: confirmar/cancelar suscripción sigue por GET, reenvío de verificación/invitación, cambio de clave con sesión, adaptador de fotos S3, Mongo/Redis con alta disponibilidad, RF041.

### 2026-09-20 · `main`
**Qué:** Se retiró OpenSpec (`ADR-047`): las 13 capacidades pasaron a `docs/ingenieria/comportamiento-del-sistema.md` (54 requisitos, 93 escenarios) y se borraron `openspec/`, las 12 copias de skills y los comandos `opsx`.
**Sigue:** Aprobar el diseño del rotulado de barrios (`BUG-073`) y hacer el commit de todo lo pendiente.

### 2026-09-20 · `main`
**Qué:** Se cerró el `BUG-074` (`resumirServicio`, `ADR-046`), se registró el CVE de Netty (`BUG-075`) y `openspec/` recogió el resumen del servicio, el reporte desde el llamado a veedores y la vista pública de Acuacar. Las reglas globales quedaron en un solo `CLAUDE.md` y se retiró Notion.
**Sigue:** Aprobar el diseño del rotulado de barrios (`BUG-073`: principales por área, N = 12, zoom 14) y luego confirmar con el commit de todo lo pendiente.

### 2026-09-19 · `main`
**Qué:** Se investigó el E2E en rojo del frontend: la bitácora pública no recibía los boletines (`BUG-071`) y no se cargaba el CSS de escritorio (`BUG-072`), ambos corregidos; quedan abiertos el rotulado del mapa (`BUG-073`) y los props sin cablear que rompen `npm run build` (`BUG-074`). Netty subió a 4.1.137 por CVE-2026-75595 y el CI del backend volvió a verde.
**Sigue:** Decidir el cálculo de «% operativa» y si se construye el rotulado de barrios; hasta entonces el Frontend CI seguirá en rojo.

### 2026-09-19 · `main`
**Qué:** El proyecto pasó a ser individual (`ADR-045`): se retiraron los roles D1–D5, las compuertas, el registro de bloqueos y Pages; registros y Javadoc quedaron sin actores y la Sala de control se genera solo en local desde `docs/`. `verify` del backend sin fallos (660 casos).
**Sigue:** Contrastar `sprint-2.md` contra el código (M10–M15 ya están entregados) y subir el commit a `origin`.

### 2026-09-08 · `main`
**Qué:** Refactor costero aplicado sin alterar Leaflet/GeoJSON/endpoints; feed público limitado a Acuacar, mapa rotulado y estable ante clics rápidos, y veeduría Stitch con olas dobles animadas. Build, lint, 107 pruebas unitarias y 12 E2E en verde.
**Sigue:** Revisar los cambios en la rama de trabajo y abrir un PR.

### 2026-08-09 · `fix/integrar-formulario-reportes`
**Qué:** RF008 conectado a `POST /api/reportes`: formulario real en dos pasos, huella anónima SHA-256, ubicación opcional, errores RFC 7807 y contrato OpenAPI regenerado; 26 pruebas, lint, build y `npm audit` en verde.
**Sigue:** Fusionar el PR a `main`; después registrar la entrega en `registro-de-implementaciones.md`.

### 2026-08-09 · `develop` (cierre de sesión)
**Qué:** Sesión larga sobre todo el backend — 8 PRs fusionados (#112, #113,
#116, #118, #119, #120, #121, #124). Con esto **M1–M6 y M8 quedan completos**: los 9 puertos de
entrada y 8 de salida del dominio tienen implementación real. Regenerado `backend/openapi.yaml` (de 7
a 17 rutas — faltaban los cuatro módulos nuevos, PR #124). Puesta al día `registro-de-implementaciones.md`
(7 PRs sin registrar) y su tabla de cobertura, que seguía en el estado del Sprint 1 (36 RF: 28% → 78%
funcional real). `/security-review` sobre las cuatro superficies nuevas: sin hallazgos que superaran
el umbral de confianza.
**Hallazgo real:** RF014 (avisar al suscriptor cuando su sector cambia de estado) sigue sin conectar
— `NotificacionPort` solo se dispara al suscribirse (`SuscribirseService`), ni `EvaluarConsensoService`
ni `GestionarCorteOficialService` lo llaman. M4 queda en 75%, no 100%, por esto.
**Sigue:** M9 (etapa IA) descartada (`ADR-025`). RF014 es el hueco funcional real más concreto
que queda en lo ya construido.

### 2026-08-09 · `feature/moderacion-reportes`
**Qué:** `ModerarReporteService` (RF018, M5) — el veedor aprueba o descarta reportes ciudadanos.
`ADR-023`: nadie había definido qué hace "dudoso" a un reporte, así que se interpreta como "todo
reporte sin moderar" (nace `PENDIENTE`) en vez de inventar una heurística de fraude no pedida.
`ReporteCiudadano` gana `EstadoModeracion`; `ModeracionReporteController` en `/api/veedor/reportes`.
209/209 pruebas en verde.
**Sigue:** Alcance acotado a propósito: descartar no recalcula consenso ni
el conteo de RF006 (ver el propio ADR).

### 2026-08-09 · `feature/bitacora-publica`
**Qué:** `BitacoraController` público en `GET /api/bitacora` (RF027), directo a
`EventoBitacoraRepository` sin caso de uso (ADR-015). 178/178 pruebas en verde. Fusionado a
`develop` en el PR #120 — **M8 completo**.
**Sigue:** —

### 2026-08-09 · `feature/crud-cortes-veedor` (PR #119, no #116)
**Qué:** `RegistrarEventoBitacoraService` (RF026) y `GestionarCorteOficialService`
actualizado para anexar un evento de bitácora por cada sector afectado al registrar
(`CORTE_ANUNCIADO`) y al cerrar (`CORTE_RESTABLECIDO`) un corte — antes solo el consenso ciudadano
anexaba. 176/176 pruebas en verde. Fusionado a `develop`.
**Hallazgo de proceso:** el PR #116 se fusionó *antes* de que este commit llegara al remoto —
quedó huérfano en la misma rama con el PR ya cerrado, así que abrí el PR #119 sobre el mismo commit.
Pasa cuando se empuja a una rama cuyo PR ya fue aprobado y fusionado en paralelo;
vale la pena revisar el estado del PR (`gh pr view <N> --json state`) antes de empujar, no solo al
abrirlo.
**Sigue:** RF018 (moderación de reportes) sigue fuera — sin puerto de dominio todavía.

### 2026-08-09 · `feature/indice-cumplimiento`
**Qué:** `CalcularCumplimientoService` (RF020-RF022, M6 — el diferencial del proyecto).
`ADR-022`: agrega por suma de duraciones, no promedio de porcentajes. `IndiceCumplimientoController`
público en `/api/cumplimiento` (porCorte, porSector, global). Agregado
`CorteAguaRepository.listarTodos()`. 178/178 pruebas en verde. Fusionado a `develop` en el PR #118.
**Sigue:** —

### 2026-08-09 · `feature/crud-cortes-veedor`
**Qué:** `GestionarCorteOficialService` (RF016-RF017) y `CorteController` en
`/api/veedor/cortes` (registrar, cerrar, consultar, listar por sector), protegido por el JWT ya
existente sin tocar `SecurityConfig`. Cerrar un corte ya cerrado responde 409 (nuevo
`IllegalStateException` en `ManejadorGlobalDeErrores`). 175/175 pruebas en verde. Fusionado a
`develop` en el PR #116.
**Sigue:** RF018 (moderación de reportes) queda fuera — sin puerto de dominio todavía.

### 2026-08-09 · `feature/corteagua-mongo-adapter`
**Qué:** Construido `CorteAguaMongoAdapter` (RF016-RF017) — el dominio de `CorteAgua` existía sin
adaptador que lo persistiera. Índice de `sectoresAfectados` agregado a `IndicesMongo`. 154/154
pruebas en verde. Trabajo adelantado de Sprint 3.
**Sigue:** Fusionado a `develop` en el PR #113.

### 2026-08-09 · `feature/cache-sectores-y-rate-limit`
**Qué:** Activados los dos pendientes de infraestructura (`sprint-2.md` §2): `@Cacheable` en `GET /api/sectores`
con invalidación al confirmar consenso, y reglas de rate limiting para `/api/veedor/sesion` y
`/api/reportes`. 155/155 pruebas en verde. `REC-006` registrada (trampa de `RateLimitConfig` en
`@WebMvcTest`). Fusionado a `develop` en el PR #112, implementación registrada.
**Sigue:** —

---

## Sprints 0 y 1

Rotados a [`historico/bitacora-sprint-1.md`](historico/bitacora-sprint-1.md) y [`historico/bitacora-sprint-0.md`](historico/bitacora-sprint-0.md).

<!--
Plantilla — copiar, rellenar, pegar ARRIBA de la entrada más reciente del sprint en curso.

### AAAA-MM-DD · `rama`
**Qué:** <resultado en pasado, máx. 2 líneas, con referencias ADR/BUG/RF>
**Sigue:** <siguiente paso concreto, una línea>

Rotación: al superar 30 entradas, las más viejas pasan a
docs/gestion/historico/bitacora-sprint-<N>.md. Se hace al cerrar el sprint.
-->
