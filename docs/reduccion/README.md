# Reducción del backend

Plan para que el backend sea **más fácil de sustentar** sin perder funcionalidad ni romper lo que ya funciona. Lo ejecuta
Sebastian (backend) con Claude Code, fase por fase. Yordy sigue con el frontend en paralelo, porque **el contrato HTTP no
cambia**. Propuesto el 2026-10-06; decisión en [ADR-098](../07-decisiones-clave.md#adr-098--reducción-estructural-por-funcionalidad).

## Por qué

Es un proyecto académico que corre en local y nunca va a producción. Aun así, el backend creció con la forma de un sistema
de producción: un puerto por caso de uso, un adaptador por puerto, un mapper por DTO y cableado a mano. Ninguna pieza está
mal, pero el conjunto es difícil de recorrer y de explicar ante el jurado.

## Qué se conserva y qué cambia

**Se conserva todo lo que el sistema hace.** Son 89 endpoints en 30 controladores, y entre ellos:
- simulación, carga y métricas
- Telegram
- TOTP y auditoría
- token de dispositivo y rate limit
- cuentas por correo y vecino registrado

**Se conserva también el contrato**, sin tocar nada de esto:
- `backend/openapi.yaml`: rutas, nombres de los esquemas y de los campos JSON
- códigos de estado y `type` RFC 7807
- cabeceras: `X-Total-Count`, `Link`, `Retry-After`, `X-Dispositivo`, `X-Subida`
- colecciones de Mongo y sus campos
- claves de Redis
- variables de entorno y perfiles

**Cambia solo la forma interna**: se pasa de capas técnicas a paquetes por funcionalidad.

## Requisitos del dueño (añadidos el 2026-10-07)

Mandan sobre cualquier otra decisión de este plan. Cada uno trae la herramienta que lo comprueba, y esa herramienta es parte
de la [puerta](#la-puerta-lo-que-se-comprueba-al-cerrar-cada-fase): si falla, la fase no se cierra.

| # | Requisito | Cómo se garantiza |
|---|---|---|
| 1 | **Toda la lógica de negocio se queda, con todos sus escenarios, sin un solo hueco.** Por ejemplo: se va el agua y un vecino reporta que no fue así; el agua vuelve antes de lo prometido; los reportes llegan todos desde una sola red | [`escenarios-de-negocio.md`](escenarios-de-negocio.md) lista cada escenario y el test que lo cubre. R0 construye esa matriz y **escribe los tests que falten antes de mover nada**. Las reglas se mueven a `reglas/` sin reescribirse. Una fase no se cierra si un escenario de su área queda sin test |
| 2 | **El proyecto arranca con la base ya cargada con las 30 000 cuentas de vecinos al azar** | Puerta: tras `docker compose down -v` y `up`, `docker compose run --rm sembrador verificar` (o `node scripts/verificar-datos.mjs`) tiene que dar 211 barrios y 30 000 cuentas sintéticas en la instancia real. R0 mide cuánto tarda en estar lista y deja anotado el comando exacto. Ver [invariantes §6](invariantes.md#6-datos-de-arranque-y-entornos) |
| 3 | **Los endpoints aguantan a todos los usuarios acordados, a la vez**: por lo menos 10 000 personas registrándose, otras 5 000 creando reportes, otras suscribiéndose, y así con todos los endpoints | R0 mide la línea base con carga mixta (`scripts/carga/demo.mjs`) y con un recorrido por endpoint. R2, R4 y R8 (las de riesgo alto) y R9 repiten esa carga y **no pueden quedar peor** que la línea base. Los umbrales exactos se fijan en R0 con el visto bueno del dueño. Ver [R0 §8](R0-red-de-seguridad.md#8-línea-base-de-capacidad) |
| 4 | **Dos instancias en localhost**: la de tiempo real muestra el estado real de todos los sectores (si ese día hay agua en todas partes, así se ve) y la de simulación muestra barrios sin agua y reportes activos | Se conservan tal cual: real en `:8081` (base `aguavigia`, Redis db 0, reloj del sistema, boletines reales) y simulación en `:8082` (base `aguavigia_sim`, Redis db 1, `RelojSimulado`, `/api/sim/**`). El guion de simulación es obligatorio **desde R0**, y la puerta comprueba que la real responde 404 en `/api/sim/**`. Ver [invariantes §6](invariantes.md#6-datos-de-arranque-y-entornos) |
| 5 | **Los scripts que inyectan usuarios y simulan personas entrando, saliendo y haciendo peticiones siguen funcionando**, enlazados con la instancia de simulación | `agregar-usuarios.mjs` (modos `directo` y `api`, por lotes), `scripts/carga/demo.mjs` y `scripts/simulacion/simulador.mjs` no se tocan. La puerta corre una versión pequeña de cada uno al cerrar la fase. El modo `directo` escribe los documentos de `usuarios` con su forma exacta, así que **depende del requisito 6** |
| 6 | **MongoDB y Redis, absolutamente intactos**: la misma estructura, igualita | `scripts/reduccion/esquema-datos.mjs` ([R0 §6](R0-red-de-seguridad.md#6-scriptsreduccionesquema-datosmjs)) compara colecciones, nombres y tipos de campo, índices con sus opciones (`2dsphere`, únicos, TTL, parciales) y las claves de Redis (patrón, tipo, con o sin caducidad). Cualquier diferencia impide cerrar la fase |
| 7 | **El backend va primero, totalmente.** El frontend se adapta al backend | Si algo del plan del frontend ([`docs/frontend/`](../frontend/README.md)) choca con esta reducción o con estos requisitos, gana el backend. El contrato se mantiene por defecto; si el backend necesitara cambiarlo, lo decide Sebastian y avisa en [`cambios-para-frontend.md`](../api/cambios-para-frontend.md). Mientras haya trabajo de backend pendiente, no se espera por el frontend |

## Antes y después

| | Hoy (`main`, 2026-10-06) | Meta orientativa al cerrar R9 |
|---|---|---|
| Archivos `.java` en `src/main` | 523 (incluye 12 `package-info`) | 200–250 |
| Líneas en `src/main` | 28 018 | ~15 000 |
| Archivos de test | 247 | ~150 |
| Líneas de test | 34 249 | ~18 000 |
| Interfaces `port/in` (todas con una sola implementación) | 57 | 0 |
| Interfaces `port/out` | 44 | solo las que tienen varias implementaciones reales (~6) |
| DTO / mappers MapStruct | 49 / 9 | ~11 archivos `XDtos.java` / 0 |
| Cableado a mano (`CasosDeUsoConfig`) | 57 servicios | 0 (escaneo de `@Service`) |

Las cifras de la meta son una estimación. Se miden de verdad en R0 y en R9 con `scripts/reduccion/medir.sh`.

### Estructura de hoy

```
com.aguavigia.ctg
├── domain/          216 clases (101 son puertos)
├── application/      63 servicios
├── api/              30 controladores, 49 DTO, 9 mappers, manejador de errores
└── infrastructure/  146 clases (48 de persistencia Mongo, 22 de ingesta, 21 de config…)
```

Para tocar una funcionalidad hoy hay que abrir 6–8 archivos en 4 paquetes: controlador, DTO, mapper, puerto de entrada,
servicio, puerto de salida, adaptador y documento Mongo.

### Estructura objetivo

```
com.aguavigia.ctg
├── CtgApplication.java
├── compartido/     error/ (manejador RFC 7807 y sus excepciones), config/ (Mongo, Redis, CORS, async, caché, índices),
│                   reloj/, http/ (paginación, CSV), logging/, ratelimit/, sse/, tareas/, correo/, secretos/, valor/
├── sectores/       ← el estado de un barrio y el consenso
├── cortes/         ← cortes oficiales, cierre por barrio, expiración
├── reportes/       ← reportes, fotos, moderación, disputas, dispositivos, restablecimiento
├── bitacora/       ← eventos públicos y su sustento
├── cumplimiento/   ← Índice de Cumplimiento
├── estadisticas/
├── ingesta/        ← colectores (Acuacar, RSS, local, simulado), extracción, propuestas
├── suscripciones/  ← avisos por correo y Telegram
├── cuentas/        ← veedor, vecino, JWT, TOTP, administración, auditoría, enlaces; seguridad/ (SecurityConfig, filtro JWT)
└── sistema/        ← simulación, métricas, modo, mantenimiento, IoT, Open311
```

Cada funcionalidad sigue el mismo molde. Se recorre en un solo paquete:

```
cortes/
├── CorteController.java          HTTP ↔ servicio. Sin lógica de negocio.
├── HistorialDeCortesController.java
├── CorteService.java             Los casos de uso de cortes, como métodos (antes: 4 interfaces + 4 servicios)
├── CorteAlmacen.java             Persistencia: MongoRepository y/o MongoTemplate (antes: puerto + adaptador + repo Spring)
├── CorteAgua.java                El modelo con @Document. Valida al construirse (antes: entidad de dominio + documento + conversión)
├── CorteDtos.java                Records de petición y respuesta, con fábricas desde(...) (antes: 4 DTO + 1 mapper)
└── reglas/                       Java puro, sin Spring ni Mongo: las reglas de negocio y sus tests unitarios
```

### Convenciones (obligatorias en cada fase)

1. **El modelo es la clase `@Document`.** Se unen la entidad de dominio, el documento Mongo y la conversión entre ellos.
   - `collection`, `@Id` y `@Field` deben dar exactamente los mismos nombres que hoy. Ver la tabla de colecciones en [invariantes](invariantes.md#1-colecciones-de-mongo).
   - Los objetos de valor siguen siendo `record` que validan al construirse (`Coordenada`, `VentanaTiempo`, `SectorId`…).
2. **Las reglas de negocio viven en `<funcionalidad>/reglas/`, en Java puro**, y se mueven **sin reescribirse**, con sus tests. Son, entre otras:
   - el resolutor de estado
   - las estrategias de consenso
   - el cálculo del Índice
   - la heurística de extracción
   - los límites de reporte

   Es la nueva regla de oro, que ArchUnit hace cumplir: `..reglas..` no importa `org.springframework..` ni `com.mongodb..`.
   Este es el argumento de diseño ante el jurado.
3. **Un servicio por funcionalidad, o pocos.** Son `@Service` concretos con inyección por constructor, sin interfaz.
   - Se divide un servicio cuando pase de ~400 líneas o mezcle dos responsabilidades claras. Ejemplos: `ModeracionService` aparte de `ReporteService`; `RecalculoDeEstadoService` aparte de `SectorService`.
4. **Solo quedan interfaces con varias implementaciones reales:**
   - `Reloj` (sistema / simulado)
   - `FuenteDeBoletines` (Acuacar, RSS, local, simulada)
   - `EnvioTelegram` / `RecepcionTelegram` (API / desactivado)
   - `NotificacionCuenta` (correo / descartado)
5. **La persistencia va en una clase `XAlmacen` por agregado.**
   - Usa `MongoRepository` para lo simple y `MongoTemplate` para lo geoespacial y lo atómico.
   - Las operaciones atómicas se copian **tal cual**: mismo filtro, mismo `findAndModify`, mismo `upsert`. Ver [invariantes §2](invariantes.md#2-operaciones-atómicas-o-sensibles-a-concurrencia).
6. **Los DTO van en un archivo por funcionalidad** (`CorteDtos.java`), como `record` anidados.
   - **El nombre simple de cada record no cambia** (`CorteRespuesta`, `SolicitudCorte`…), porque springdoc lo usa como nombre del esquema y el frontend genera sus tipos de ahí.
   - La conversión se hace con `static desde(...)`, sin MapStruct.
7. **Los controladores solo dependen de servicios.** No tocan almacenes ni modelos de otra funcionalidad.
   - Conservan su nombre de clase, los nombres de sus métodos y sus `@Tag`/`@Operation`. springdoc deriva de ahí `operationId` y `tags`, que son parte del contrato.
   - No se fusionan controladores.
8. **Una funcionalidad usa otra solo a través de su servicio o su almacén**, nunca de su controlador ni de sus DTO. El mapa de quién usa a quién está abajo.
9. **Patrón de transición (estrangulamiento).** Cuando una clase nueva reemplaza a un puerto que código viejo todavía usa,
   la clase nueva **implementa temporalmente la interfaz vieja**. Ejemplo: `SectorAlmacen implements SectorRepository`,
   y `RecalculoDeEstadoService implements RecalcularSectorUseCase`.
   - Así `CasosDeUsoConfig` y los servicios viejos siguen compilando sin cambios.
   - La interfaz vieja se borra en la fase en que se mueve su **último** consumidor. Cada guía dice cuáles caen.
   - Nunca conviven dos implementaciones de la misma cosa: la vieja se borra en la misma fase en que nace la nueva.
10. **El resto de las convenciones de [`CLAUDE.md`](../../CLAUDE.md) siguen vigentes:**
   - español en el dominio
   - sin `@Autowired` en campos
   - comentarios solo para el porqué
   - tests con nombre descriptivo en español
   - errores RFC 7807 desde un único `@RestControllerAdvice`

### Quién usa a quién

```
                ┌──────────── recalcular(sector) ─────────────┐
 cortes ────────┤                                             ▼
 reportes ──────┤                          sectores.RecalculoDeEstadoService
 ingesta ───────┘                                             │
     ▲  ▲  ▲                                                  │ lee
     └──┴──┴──────────── CorteAlmacen · ReporteAlmacen · PropuestaAlmacen
 sectores, cortes, reportes, ingesta ──▶ bitacora.BitacoraService   (único que crea EventoBitacora)
 sectores (SectorActualizadoEvent, tras el commit) ──▶ SSE · suscripciones (correo, Telegram)
 cumplimiento, estadisticas, sistema ──▶ leen cortes / reportes / sectores
 compartido ◀── todas (compartido no depende de ninguna)
```

**Ciclo deliberado:** cortes, reportes e ingesta llaman al recálculo, y el recálculo lee sus almacenes. Es el centro del
sistema ([ADR-087](../07-decisiones-clave.md#adr-087--un-solo-resolutor-decide-el-estado-de-un-barrio)) y corre **en la
misma transacción** que la escritura que lo provoca: guardar un corte, su bitácora y el estado de sus barrios es una sola
transacción. Romper el ciclo con eventos asíncronos cambiaría ese comportamiento. Por eso ArchUnit no prohíbe ciclos entre
funcionalidades. Sí prohíbe lo que importa:
- que las reglas importen framework
- que un controlador toque un almacén
- que alguien fuera de `bitacora` cree eventos
- que alguien escriba el estado de un sector sin pasar por el recálculo

## Fases

Cada fase deja el sistema **funcionando y con la puerta en verde**. Si una fase se corta a medias, `main` no se entera.

| Fase | Guía | Qué hace | Riesgo |
|---|---|---|---|
| R0 | [Red de seguridad](R0-red-de-seguridad.md) | Línea base, comparador de contrato, de forma y de bases (Mongo y Redis), flujos ampliados, [matriz de escenarios](escenarios-de-negocio.md) con los tests que falten, línea base de capacidad, reglas ArchUnit de transición. **Sin tocar producción** | Bajo |
| R1 | [Compartido y cableado](R1-compartido-y-cableado.md) | `compartido/`: errores, config, reloj, HTTP, SSE, rate limit, secretos. `CasosDeUsoConfig` empieza a vaciarse (se borra en R9) | Medio |
| R2 | [Sectores y bitácora](R2-sectores-y-bitacora.md) | Sector, estado, consenso, recálculo, evento de sector y la bitácora completa | **Alto** |
| R3 | [Cortes](R3-cortes.md) | Cortes oficiales, cierre por barrio, expiración, historial | Medio |
| R4 | [Reportes](R4-reportes.md) | Reportes, fotos (y su limpieza), moderación, disputas, dispositivos, restablecimiento | **Alto** |
| R5 | [Ingesta](R5-ingesta.md) | Colectores, pipeline, propuestas, salud, fallidos | Medio |
| R6 | [Consultas públicas](R6-consultas-publicas.md) | Cumplimiento, estadísticas, Open311 | Bajo |
| R7 | [Suscripciones](R7-suscripciones.md) | Suscripciones, correo, Telegram | Medio |
| R8 | [Cuentas y seguridad](R8-cuentas-y-seguridad.md) | Veedor, vecino, JWT, TOTP, administración, auditoría, enlaces | **Alto** |
| R9 | [Sistema y cierre](R9-sistema-y-cierre.md) | Simulación, métricas, IoT, mantenimiento; borrar los paquetes viejos, ArchUnit final, poda de tests, docs | Medio |

El orden sigue las dependencias: primero lo que otros usan (sectores, bitácora) y al final lo que solo consume.
- Una clase ya movida puede usar código viejo que todavía no se movió; las reglas ArchUnit de transición lo permiten ([R0 §5](R0-red-de-seguridad.md#5-archunit-de-transición)).
- El código viejo sigue usando a la clase nueva gracias al patrón de transición (convención 9).

## La puerta: lo que se comprueba al cerrar cada fase

Una fase no se fusiona a `main` si falla cualquiera de estos pasos. Los comandos completos están en [R0](R0-red-de-seguridad.md).

1. `cd backend && ./mvnw verify` en verde. Con Docker corriendo, porque las pruebas de integración usan Testcontainers.
2. **Contrato idéntico:** `node scripts/reduccion/comparar-contrato.mjs` → 0 diferencias contra la línea base de R0. Compara el `/v3/api-docs` normalizado: rutas, métodos, `operationId`, `tags`, parámetros, esquemas y nombres de campo.
3. **Forma idéntica:** `node scripts/reduccion/instantanea.mjs comparar` → 0 diferencias. Cubre la forma de las respuestas de todos los GET con la siembra de `docker compose up`: claves, tipos y presencia, no valores.
4. **Flujos:** `node scripts/verificar-flujos.mjs` → todos pasan. R0 lo amplía a los 89 endpoints.
5. **Guion de simulación:** con `backend-sim` levantado (`docker compose --profile simulacion up -d --build backend-sim`), `docker compose --profile simulacion run --rm simulador iniciar --velocidad 300` → todas las aserciones pasan. Obligatorio **desde R0** (requisito 4).
6. **ArchUnit**, con las reglas de transición de R0 y, desde R9, las finales.
7. `scripts/reduccion/medir.sh` → se anota la fila de la fase en la tabla de [Avance](#avance).
8. **Base intacta (requisitos 2 y 6):**
   - `node scripts/reduccion/esquema-datos.mjs comparar` → 0 diferencias en Mongo y en Redis.
   - Tras `docker compose down -v` y `up`, `docker compose run --rm sembrador verificar` → 211 barrios y 30 000 cuentas sintéticas.
9. **Escenarios de negocio (requisito 1):** todos los escenarios de [`escenarios-de-negocio.md`](escenarios-de-negocio.md) que tocan el área de la fase siguen cubiertos por un test que pasa. La fase no añade ni quita ninguno sin dejarlo anotado en esa matriz.
10. **Scripts de apoyo (requisito 5):** una versión pequeña de cada uno sobre la instancia correspondiente:
    - `docker compose run --rm sembrador agregar-usuarios --cantidad 50` y luego `--borrar-lote <lote>`
    - `docker compose run --rm sembrador agregar-usuarios --cantidad 20 --modo api`
    - `node scripts/carga/demo.mjs` en miniatura (pocos cientos de usuarios, con `--restaurar`)
11. **Capacidad (requisito 3):** la carga mixta de [R0 §8](R0-red-de-seguridad.md#8-línea-base-de-capacidad) no queda peor que la línea base. Obligatorio al cerrar R2, R4, R8 y R9; en las demás fases basta el recorrido pequeño del paso 10.

## Cómo se trabaja

- **Rama:** `refactor/reduccion-backend`, creada desde `main` en el commit de este plan (etiqueta `pre-reduccion`). Es la única excepción a «todo directo a `main`».
- **Al empezar una fase:** `git checkout refactor/reduccion-backend && git merge main`. Así entra lo que haya llegado a `main`, por ejemplo frontend de Yordy.
- **Commits pequeños**, en Conventional Commits en español. Ejemplos:
  - `refactor(cortes): unir puerto, servicio y adaptador de cortes`
  - `refactor(cortes): mover CorteAgua a cortes/ como @Document`
- **Al cerrar la fase, con la puerta en verde:** `git checkout main && git merge --no-ff refactor/reduccion-backend -m "refactor: cerrar R<n> de la reducción (<tema>)"` y luego `git push`. Se vuelve a la rama para la siguiente fase.
- **Si una fase se complica:** se para y se anota en la guía de la fase qué faltó. No se fusiona a medias. `git reset --hard` a la etiqueta solo con el visto bueno del dueño.
- **Etiqueta al cerrar cada fase:** `reduccion-R<n>`. Se vuelve atrás con `git checkout reduccion-R<n>`.
- **Claude Code:** cada guía de fase termina con un *prompt* listo para pegar.
  - Una fase por sesión, y `/clear` entre fases.
  - Si se usan subagentes, con Sonnet: Opus dispara el consumo.

## Riesgos

| Riesgo | Cómo se ve | Mitigación |
|---|---|---|
| **Regresión silenciosa del contrato**: un campo renombrado, un `null` que pasa a ausente, un código 409 que pasa a 400 | El frontend falla en tiempo de ejecución, no al compilar | Puertas 2, 3 y 4. Los DTO conservan su nombre simple. El manejador de errores se mueve sin cambiar ni una fila de su tabla (R1) |
| **Se pierde una validación** al unir la entidad de dominio con el documento | Datos inválidos llegan a Mongo | Los constructores que validan se conservan en el modelo. Las reglas se mueven sin reescribir. Los tests de dominio se mueven con su clase y deben seguir pasando sin editar el cuerpo del test, solo `import`/`package` |
| **Una operación atómica pierde su atomicidad**: `cambiarEstadoSiEs`, `agregarConfirmacionSiVigente`, cupos en Redis… | Carreras: dos cierres o votos que se pisan | Copia literal de filtro y operación. Los tests de integración de cada almacén se conservan. Lista completa en [invariantes §2](invariantes.md#2-operaciones-atómicas-o-sensibles-a-concurrencia) |
| **El evento `SectorActualizadoEvent` se publica antes del commit** | SSE o correos con un estado que luego se revierte | Se conserva `trasConfirmar(...)` (sincronización de transacción). Test dedicado en R2 |
| **Los tests con puertos falsos dejan de compilar** al desaparecer las interfaces (46 tests de `application/`) | La fase se alarga | Se cambian por mocks de Mockito del almacén concreto, o por Testcontainers si el test valida una consulta. Es el grueso del trabajo de R2–R8, ya contado en las guías |
| **ArchUnit falla durante la transición**: código viejo que importa clases ya movidas | La build se rompe | R0 añade los paquetes nuevos a las listas permitidas de las reglas viejas. R9 borra las reglas viejas y deja las nuevas |
| **Choque con el frontend de Yordy** | Conflicto al fusionar | Ninguno previsto: su trabajo va en `frontend/` ([`docs/frontend/`](../frontend/README.md)) y el contrato no cambia. `feat/f4-avisos` se archivó (ya estaba en `main` por el PR #98). Si el CI del frontend o los scripts compartidos (`scripts/preparar-env-ci.mjs`) cambian, se avisan mutuamente. **Si hay conflicto de fondo, gana el backend** (requisito 7) |
| **Una regla de negocio se pierde o cambia sin que nadie lo note** | Un escenario que antes funcionaba (una disputa, una reapertura, un aplazamiento) deja de dar el mismo resultado | Requisito 1: matriz de escenarios con su test, tests nuevos antes de mover cada área, reglas movidas sin reescribir y puerta paso 9 |
| **La base cambia de forma sin que el contrato lo muestre** | `agregar-usuarios --modo directo`, el simulador o `verificar-datos.mjs` fallan, o un documento viejo ya no se lee | Requisito 6: `esquema-datos.mjs` compara colecciones, campos, índices y claves de Redis; puerta paso 8 |
| **La reducción empeora el rendimiento bajo carga** | El backend cumplía con 10 000 registros a la vez y ahora no | Requisito 3: línea base de capacidad en R0 y repetición al cerrar R2, R4, R8 y R9 (puerta paso 11) |
| **Ante el jurado se pierde el argumento de Arquitectura Limpia** | Pregunta: «¿por qué no usan puertos?» | ADR-098 dice el porqué. La regla de oro sigue existiendo (`reglas/` puro, ArchUnit) y la nueva guía `docs/02-arquitectura.md`, que se escribe en R9, la explica |
| **Skills, agentes y `CLAUDE.md` describen la estructura vieja** | El agente propone puertos otra vez | Durante la transición `CLAUDE.md` dice que manda este plan. R9 actualiza `verificar-arquitectura`, `revisor-dominio` y `CLAUDE.md` |
| **Sin Docker no corren las pruebas de integración ni la puerta** | Falso verde | La puerta exige Docker. El CI (`backend-ci.yml`, `simulacion-ci.yml`) la repite al subir |

## Avance

Se actualiza al cerrar cada fase, con la salida de `scripts/reduccion/medir.sh`.

| Fase | Fecha | `.java` main | Líneas main | `.java` test | Líneas test | Puerta |
|---|---|---|---|---|---|---|
| Línea base | 2026-10-06 | 523 | 28 018 | 247 | 34 249 | — |
| R0 (en curso) | 2026-10-07 | 523 | 28 018 | 247 | 34 384 | ver abajo |

### Línea base de R0 (2026-10-07)

Medida con `INGESTA_MODO=local` (boletines guardados, sin Internet; con `auto` ninguna línea base sería reproducible) y con `backend/src/main` sin tocar (523 archivos y 28 018 líneas, igual que `pre-reduccion`). Las líneas de test suben
por las reglas de ArchUnit y los dos tests nuevos de R0. Equipo: un solo PC de 12 hilos que ejecuta a la vez el generador de carga y el backend.

| Medición | Resultado |
|---|---|
| `./mvnw verify` | BUILD SUCCESS: 2 086 tests, 0 fallos, 1 omitido (incluye los 2 tests que escribió R0) |
| Contrato (`comparar-contrato.mjs`) | 84 operaciones (sin `/api/sim/**`); el `backend/openapi.yaml` coincide con lo que sirve el backend |
| Forma de las respuestas (`instantanea.mjs`) | 31 respuestas, base recién levantada y con datos; comparar contra sí misma: 0 diferencias |
| Esquema de las bases (`esquema-datos.mjs`) | 16 colecciones con sus índices, 14 patrones de clave en Redis, 1 canal; comparar contra sí misma: 0 diferencias |
| Flujos (`verificar-flujos.mjs`) | 33 pasos correctos, 0 con fallo; **84 de 84 operaciones recorridas** |
| **Reproducibilidad** (`scripts/reduccion/puerta.sh comparar`, tres veces desde `down -v`) | `TODO EN VERDE`: contrato, forma con y sin datos, flujos y esquema de Mongo y Redis idénticos a la línea base. Las dos primeras pasadas encontraron ruido legítimo (candado `tarea-unica:*` y entrada de caché `sectores::*`), que ahora son avisos |
| Arranque (requisito 2) | 211 barrios y 30 001 cuentas (30 000 sintéticas + el ADMIN) en ~20 s tras `docker compose up -d --build`; `sembrador verificar`: OK |
| Guion de simulación ×300 (`backend-sim`) | 54/54 aserciones, 185 s reales |
| `agregar-usuarios` | modo `directo` (50) y modo `api` (20) funcionan; `--borrar-lote` borra los dos |
| Escenarios de negocio | 72 escenarios: 54 cubiertos, 17 por confirmar, 1 sin test (concurrencia de cierre de cortes, decisión del dueño). R0 escribió 2 tests que faltaban (413 y `synchronized` de la ingesta) |

**Capacidad** (requisito 3), `scripts/carga/demo.mjs --usuarios 5000 --ventana 60 --conectados 10000 --registros 10000 --tasa-registros 50 --suscripciones 5 --veedores 0 --sin-correo --restaurar`.
Duró 202 s y **cumplió todos los umbrales de k6** (reporte p95 < 1 s, lectura p95 < 1 s, registro p95 < 2 s, fallos < 1 %):

| Qué | Resultado |
|---|---|
| Reportes (`POST /api/reportes`) | 4 999 aceptados de 5 000, 0 errores; mediana 28 ms · p95 **392 ms** · p99 923 ms · máx 1,7 s |
| Registros (`POST /api/cuentas/registro`) | **10 000 de 10 000** aceptados (202), 0 errores; mediana 402 ms · p95 **418 ms** (cada alta dura ≥ 100 ms a propósito, RNF024) |
| Mapa en vivo (SSE) | 10 000 conexiones abiertas y mantenidas; primer evento p50 454 ms · p95 676 ms; 310 000 avisos de cambio entregados |
| Suscripciones y lecturas, a la vez | 301 suscripciones; 30 838 peticiones en total (154/s) con 0,00 % de error |
| Recursos (pico) | backend 957 % de CPU (de 1 200 %) y 2,6 GiB; Mongo 186 % y 565 MiB; Redis 11 % y 8 MiB |
| Efecto en los datos | 43 barrios cambiaron de estado; `--restaurar` devolvió Mongo y Redis a como estaban |

**Limitaciones de esta medición:**
- No mide el ingreso de veedores bajo carga (`--veedores 0`): el generador pide `CLAVE_VEEDORES`, la clave de las cuentas de demostración, que ya no está en el repositorio.
- `demo.mjs` no reenviaba `CLAVE_VEEDORES` a k6, así que los registros fallaban con 400 (la clave iba vacía). R0 lo corrigió (`scripts/carga/demo.mjs`): ahora la hereda del entorno si está definida. Es el único cambio de R0 en la herramienta de carga.
- El generador y el backend comparten el PC: las cifras dicen el orden de magnitud que aguanta esta máquina, no la de un servidor.
- Es **una** corrida. Los umbrales de las fases siguientes se fijan con el dueño; esta tabla es la referencia.
