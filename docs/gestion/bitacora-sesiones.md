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

## Sprint 7 — Frontend nuevo

### 2026-09-28 · `feat/f4-avisos`
**Qué:** PR #87 adaptado a la prueba de 5 segundos para las tres pantallas de avisos, con fichas de barrios, dos columnas y capturas completas en ambos temas; 248 unitarias y 102 E2E pasaron.
**Sigue:** El dueño revisa el PR #87 y sus capturas antes de fusionarlo; incorporar ADR-079 cuando llegue a `main`.

### 2026-09-28 · `feat/f4-avisos`
**Qué:** F4 quedó en el PR #87 con ADR-078 y BUG-124–126 (registrados como BUG-120–122 en #87); frontend, backend, Mailhog real y CI completo pasaron. Se revisaron capturas en ambos temas y tamaños.
**Sigue:** El dueño revisa el PR #87 y las capturas antes de fusionarlo; después registra la implementación.

### 2026-09-27 · `docs/cerrar-f2-tecnico`
**Qué:** Se delegaron a Codex las E2E de F2 contra el backend real (PR #79, 5 casos en CI; se amplió el filtro de rutas del workflow) y F3. Se fusionaron #76 (cifras `es-CO`, sin las fechas que repetían `tiempo.ts`), #77 y #79, se registraron #74/#76/#77/#79 y se cerraron #65, #75 y el issue #70. Una medición con 3G y caché vacía encontró que el mapa pinta a 7,5–11 s (`BUG-115`).
**Sigue:** El dueño revisa F2 en un teléfono real y decide si `BUG-115` se corrige en F2 o en F6; Codex construye F3 en tres PR. `git-lfs` no está instalado en esta máquina: el `.pmtiles` local es solo el puntero.

### 2026-09-26 · `claude/tender-shannon-lp71uu`
**Qué:** F2 avanzó: mapa con PMTiles local, buscador, lista accesible, ficha (horario, regla de tiempo, cortes cerrados, frescura) y reporte en dos toques con huella y foto, contra el backend real (`ADR-075`). Cerrado `BUG-112` (canal en vivo rompía en el navegador). 170 unitarias y 32 E2E con API simulada en verde; revisión de diseño en 4 tamaños y 2 temas.
**Sigue:** «Usar mi ubicación», `/confirmar/:id` y el job de E2E contra el backend real; el dueño revisa la interfaz en un teléfono antes de fusionar el PR de F2.

### 2026-09-25 · `claude/charming-shannon-9xqogb`
**Qué:** Registrada la fusión del PR #57 y cerrado `BUG-104` (coautoría de Claude en el squash; `autoria.yml` lo impide). Prototipos de F1 con la identidad contenida publicados en un Artifact: ocho pantallas a 390–1440 px, ambos temas, contraste AA medido en vivo (`plan-frontend.md` §10). F1 sigue abierto.
**Sigue:** Ver el CI del PR #59 (primera corrida de `autoria.yml`), fusionarlo y registrarlo; el dueño revisa los prototipos en un teléfono real y corre `scripts/preparar-mapa-base.sh pmtiles` en local. Sin eso no empieza F2.

### 2026-09-25 · `claude/intelligent-curie-dhs2jr`
**Qué:** Registrada la fusión del PR #56. Aplicado el plan «identidad propia y respuestas claras» del dueño: `ADR-071` (Newsreader solo en marca y titulares), `ADR-072` (PMTiles con Git LFS), `ADR-073` (filtros de la bitácora y `verificadoEn`, backend con pruebas; `BUG-102`), tokens y fuente migrados con el muestrario (`BUG-103`). Frontend 127 unitarias y 16 E2E; backend sin fallos salvo las 24 suites que exigen Docker, sin correr aquí. Sin PR.
**Sigue:** Abrir el PR para que el CI corra las pruebas con Mongo y Redis reales; después, prototipos de F1 a 360/375 y 1280 px para revisarlos en un teléfono real.

### 2026-09-25 · `claude/tender-gauss-ntutsm`
**Qué:** Prototipos adaptados a la guía y PR #55 abierto; revisión atendida (trama a 1 px con prueba, `ADR-069`, `sprint-7.md`). El dueño rechazó la interfaz por genérica: identidad formal nueva (`ADR-070`, `docs/diseno/identidad.md`), prototipo en Artifact y skills `disenar-frontend` y `revisar-diseno`. PMTiles sigue bloqueado (403).
**Sigue:** Aprobación visual del rumbo formal y migración de `identidad.md` §8. La rama local reescribió la autoría al dueño y no se pudo subir (force-push bloqueado): el dueño decide si autoriza el push o cierra el PR #55.

### 2026-09-25 · `claude/wonderful-maxwell-dfxfgu`
**Qué:** Paso 1 de la guía §7 (fundamentos visuales) aplicado sobre `docs/guia-diseno-frontend`: acento claro `#06747f` en `DESIGN.md` y `tokens.css` a la vez, pares de contraste ampliados y cifras fijadas, muestrario con las combinaciones del acento medidas en pantalla; `REC-019` resuelta. 110 unitarias y 14 E2E (360/1280, ambos temas) en verde. F1 sigue abierto.
**Sigue:** Fusionar primero `docs/guia-diseno-frontend` y luego esta rama (el commit va encima; sin PR abierto). Después, paso 2 de la guía §7: adaptar los cuatro prototipos (con cuentas, panel y «Sin datos verificados»), obtener el PMTiles en local y pedir la aprobación visual; sin ella no empieza F2.

### 2026-09-25 · `docs/guia-diseno-frontend`
**Qué:** Completada la guía integral del frontend y `ADR-069`: matriz de rutas, composiciones adaptables, contraste, estados y presentación honesta de `estado: null`; `REC-019` quedó validada, no implementada. F1 sigue abierto.
**Sigue:** Aplicar el acento junto con sus pruebas, adaptar los prototipos, obtener y medir el PMTiles y pedir la aprobación visual del dueño antes de F2.

### 2026-09-25 · `claude/laughing-bardeen-a4p6nb`
**Qué:** Registrado el PR #54 (F0) y abierto el Sprint 7. F1 casi completo: glifos del mapa (`ADR-068`, Noto locales ya en `frontend/public/mapa/glifos/`), glifos de estado en SVG, contraste AA en `contraste.test.ts` (`REC-019`), prototipos publicados en un Artifact y `scripts/preparar-mapa-base.sh`.
**Sigue:** El dueño aprueba o corrige los prototipos y decide `REC-019`; correr `preparar-mapa-base.sh pmtiles` en local (aquí `build.protomaps.com` está bloqueado), medir el `.pmtiles` y decidir si se versiona; luego F2.

### 2026-09-25 · `claude/youthful-lovelace-de7ult`
**Qué:** Plan del frontend (`docs/ingenieria/plan-frontend.md`) y `ADR-067` (React 19 + Vite + CSS propio, PMTiles local; `ADR-029` reemplazado). F0 construido en `frontend/`: tokens verificados contra `DESIGN.md`, cliente tipado con `api:check`, RFC 7807 por `type`, CI propio; 22 unitarias y 6 E2E en verde.
**Sigue:** Ver pasar `frontend-ci.yml` en el PR y probar el proxy contra el backend real (aquí no había Docker); el dueño decide Sprint 7 y la rama definitiva; luego F1 (prototipos).

## Preparación del backend

### 2026-09-29 · `fix/estabilidad-bajo-carga`
**Qué:** Backend de `main` revisado con los cambios del compañero (`./mvnw verify`: 970 pruebas, 0 fallos) y `RNF027` medido de nuevo con la carga dentro de la red de Docker y 3 réplicas: 50 100 SSE sostenidas, lecturas a 6 000 req/s, escrituras a 900/s de pico, todo junto dentro de umbrales hasta 25 000 SSE (`escalabilidad.md`). Corregidos `BUG-117`, `BUG-118`, `BUG-119` y cerrado el hallazgo del puerto publicado (era Docker Desktop); `scripts/carga/escenario-integrado.sh` repite el escenario. Base de la demo restaurada de una copia tras las pruebas.
**Sigue:** Revisar y fusionar el PR de esta rama y registrarlo en `registro-de-implementaciones.md`. Fusionar `fix/jackson-cve-2026-68497` (CVE alto en `main`; ocupa `BUG-116`, por eso el siguiente libre aquí es 120). Con 50 000 SSE y todo el tráfico la latencia se sale de umbrales: solo una prueba con varias máquinas dice de quién es el límite. Sin probar: estampida de lecturas tras un aviso SSE, cerrojos de tareas con réplicas. La bitácora pasa de 30 entradas y no se rotó.
### 2026-09-24 · `docs/entregables-bd2`
**Qué:** Entrega de Base de Datos 2 (ER → NoSQL): `docs/ingenieria/transformacion-er-a-nosql-bd2.docx` y `.pdf` (14 tablas → 10 colecciones, índices tomados de `IndicesMongo.java`) y `modelo-nosql-moon-modeler.dmm` con el diagrama de las 10 colecciones. No hay `.sql` en el repo; el documento no lo cubre. Los diagramas omiten `suscripciones_telegram`, `bloqueos_administracion` y `documentos_fallidos`.
**Sigue:** El `.docx` y el `.pdf` se versionan el 2026-09-29. El `.dmm` queda fuera de git: gitleaks toma por claves los UUID de sus relaciones (`parent_key`) y exceptuarlo exige que el dueño decida la allowlist. El PDF no se revisó renderizado.

### 2026-09-24 · `feat/mensajeria-telegram`
**Qué:** `RF041` construido por Telegram y apagado hasta tener el token (`ADR-066`): sondeo sin webhook, `/suscribir`, `/baja`, `/estado`, `/mis`, baja que borra el chat; reemplaza el simulacro de M14. `./mvnw verify`: 929 pruebas, 0 fallos; probado de extremo a extremo con un Telegram falso, **no contra Telegram real**. Limpieza hecha con copia previa (base de la demo y etiquetas viejas en `Documentos/respaldos-aguavigia`); `verificar-flujos.mjs` espera 1,1 s antes del cierre de sesión.
**Sigue:** Crear el bot con `@BotFather` y poner `TELEGRAM_BOT_TOKEN` (`docs/ingenieria/telegram.md`); fusionar el PR y registrarlo; fecha real de la presentación para la retrospectiva.

### 2026-09-24 · `docs/sprint-6-demo-y-credenciales`
**Qué:** Sprint 6 abierto y cerrado (`REC-018`: demo local contra el backend): histórico sembrado y verificado, guion ensayado y `verificar-flujos.mjs` en 21/0 sobre una copia limpia de `main` y sobre la base de la demo (ADMIN nuevo, 40 001 cuentas). `RNF027` queda parcial; el fallo intermitente del cierre de sesión es el margen de 1 s del filtro JWT, no un bug.
**Sigue:** Fusionar el PR #50 y registrarlo; `ADR-066` (credenciales con valor) sin subir: falta el inventario y las dos líneas de `.gitleaks.toml`; decidir el recorte de alcance (IoT, `RF041`, cuentas del panel).

### 2026-09-24 · `chore/entorno-para-frontend`
**Qué:** Fase 5 del plan de Yordy: entorno construido desde cero (copia del repo sin `.env` ni datos, `up --build`, siembra) y recorrido con HTTP real por el nuevo `scripts/verificar-flujos.mjs`: 21 pasos, 0 fallos (plan de pruebas §8). Hallazgos: `BUG-101` (volumen de fotos como `root` → 500 al subir foto en instalación limpia; corregido en el `Dockerfile` y vigilado en `despliegue-ci.yml`), CORS cerrado en el perfil `docker` (ahora abre 5173/3000/4200, `CORS_ORIGENES`, `CorsPorPerfilTest`) y la trampa del `$` del hash en `.env` (documentada). Guía de consumo, entorno local y comportamiento actualizados. Build: 884 pruebas, 0 fallos. Un fallo de revocación de sesión visto una vez con estado sucio no se reprodujo (3 intentos) y queda sin explicar.
**Sigue:** Los PR #48 y #49 quedaron fusionados y registrados. Del dueño: `sprint-6.md` y qué cuenta como demo sin frontend propio (`REC-018`); un volumen `fotos-data` creado con la imagen vieja sigue siendo de `root` (borrarlo o `chown` una vez). Sin cubrir: `RF041`, TLS/proxy de producción y los 50 000 usuarios (`ADR-057`).

### 2026-09-23 · `refactor/casos-de-uso-sin-spring`
**Qué:** Fase 4 del plan de Yordy completa (`ADR-065`): `application/` sin ningún import de Spring (`CasosDeUsoConfig` los registra), listeners, evento y SSE movidos a `infrastructure/eventos` y `infrastructure/sse`, y la lógica de tres controladores (histórico de cortes, sustento de bitácora, Open311) pasada a casos de uso nuevos con `Pagina.deLista`. Dos reglas de ArchUnit nuevas; build completa: 881 pruebas, 0 fallos. No se dividieron `AdministrarCuentaService` ni `ConfigurarSegundoFactorService` (guardas compartidas, ver ADR).
**Sigue:** Fase 5 (hecha después, ver la entrada de arriba). PR #48 fusionado y registrado. Del dueño: borrar `./respaldos-mongo-drill/`, `REC-018` y las 5 etiquetas git viejas.

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

Rotado a [`historico/bitacora-sprint-2.md`](historico/bitacora-sprint-2.md).

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
