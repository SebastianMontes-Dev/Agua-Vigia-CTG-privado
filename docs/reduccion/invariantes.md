# Invariantes que la reducción no puede romper

Lo que cada fase debe dejar **igual**. Inventario tomado de `main` el 2026-10-06. Si una fase encuentra algo que falta aquí,
lo añade antes de seguir.

## 1. Colecciones de Mongo

El modelo nuevo (`@Document`) debe mapear a la misma colección, con los mismos nombres de campo.

| Colección | Documento de hoy | Va a |
|---|---|---|
| `sectores` | `SectorDocumento` | `sectores/` |
| `eventos_bitacora` | `EventoBitacoraDocumento` | `bitacora/` |
| `cortes` | `CorteAguaDocumento` | `cortes/` |
| `reportes` | `ReporteCiudadanoDocumento` | `reportes/` |
| `subidas_foto` | `SubidaDeFotoDocumento` | `reportes/` |
| `dispositivos` | `DispositivoDocumento` | `reportes/` |
| `propuestas_ingesta` | `PropuestaIngestaDocumento` | `ingesta/` |
| `documentos_fallidos` | `DocumentoFallidoDocumento` | `ingesta/` |
| `marcas_ingesta` | `MarcaDeIngestaDocumento` | `ingesta/` |
| `suscripciones` | `SuscripcionDocumento` | `suscripciones/` |
| `suscripciones_telegram` | `SuscripcionTelegramDocumento` | `suscripciones/` |
| `usuarios` | `UsuarioDocumento` | `cuentas/` |
| `tokens_cuenta` | `TokenCuentaDocumento` | `cuentas/` |
| `auditoria_cuentas` | `EventoAuditoriaDocumento` | `cuentas/` |
| `bloqueos_administracion` | `BloqueoDocumento` | `cuentas/` |
| `config_sistema` | `ConfigSistemaDocumento` | `compartido/` |

`IndicesMongo` crea los índices al arrancar, entre ellos el `2dsphere`. Se mueve a `compartido/config/` con la misma lista.
Comprobación antes y después de cada fase que toque persistencia:
- `node scripts/verificar-datos.mjs`: que la base quede como la deja `docker compose up` (211 barrios, cuentas sintéticas, ADMIN inicial).
- `node scripts/reduccion/esquema-datos.mjs comparar`: que colecciones, campos, índices y claves de Redis sean idénticos a la línea base de R0 ([requisito 6](README.md#requisitos-del-dueño-añadidos-el-2026-10-07)).

**«Intacta» significa más que los nombres de colección.** Incluye el nombre y el tipo de cada campo (también los que hoy son `null` o
no vienen), los índices con sus opciones (`2dsphere` de `sectores.geometry`, los únicos, los TTL de `subidas_foto`, `tokens_cuenta`,
`auditoria_cuentas`, `dispositivos` y la retención de `reportes`, los dispersos) y que **documentos escritos por el código viejo se
sigan leyendo** con el nuevo. Las definiciones están en `IndicesMongo`, que se mueve a `compartido/config/` sin editar ni una.

## 2. Operaciones atómicas o sensibles a concurrencia

Se copian **literalmente**: el mismo filtro, la misma operación y el mismo orden. Cada una conserva su test de integración.

**Mongo, `findAndModify` con filtro de estado esperado (hoy en `SectorMongoAdapter`):**
- `cambiarEstadoSiEs`, `publicarSiEs`, `abrirDisputaSiEs`
- `confirmarEstado` (`updateFirst` con el mismo filtro)
- `trasConfirmar(...)` publica `SectorActualizadoEvent` **después del commit**

**Mongo, upsert:**
- `ConfigSistemaMongoAdapter.leerOCrear` (`$setOnInsert`)
- `BloqueoDeAdministradoresMongoAdapter.ejecutarExclusivo` (adquiere con `findAndModify` + upsert, libera con `findAndModify`)
- `CorteAguaMongoAdapter.anexarSectorAlCorte`

**Mongo, actualizaciones condicionales:**
- `ReporteCiudadanoMongoAdapter`: `asignarFotoSiNoTiene`, `agregarConfirmacionSiVigente` (`$addToSet`), `cambiarEstadoDeModeracion`, `marcarFotoDescartada`, `quitarFotosDe` (`updateMulti`)
- `TokenCuentaMongoAdapter`: `marcarUsadoSiVigente`, `invalidarVigentes`
- `DispositivoMongoAdapter.registrarVisto` (`$max`, sin crear el documento)

**Mongo, otros:**
- `UsuarioMongoAdapter.insertarSinteticasSiNoExisten` (`bulkOps` UNORDERED)
- `TransaccionMongoAdapter` (frontera de transacción). Se reemplaza por `@Transactional` o `TransactionTemplate` **en el mismo método** que hoy abre la transacción.

**Redis:**
- `INCR` + `EXPIRE`: `RedisControlIntentosAdapter.registrarFallo`, `RedisCupoPorCuentaAdapter.consumir`, `RedisContadorReportesAdapter.intentarReservarCupo`
- `SETNX`: `RedisControlIntentosAdapter.consumirPorPrimeraVez`, `RedisReservaDeEvaluacionAdapter.reservar`, `EjecucionUnicaRedis.ejecutar`
- Lua: `RateLimitingInterceptor.preHandle` (`CONTAR_Y_CADUCAR`), `EjecucionUnicaRedis` (`SOLTAR`)
- `RedisRevocacionSesionAdapter` **falla cerrado** si Redis no responde. El contador de reportes falla abierto. No se igualan.

**JVM:**
- `PipelineOrquestador.ejecutarCiclo` es `synchronized`
- `MetricasEnMemoriaAdapter` usa `LongAdder` / `ConcurrentHashMap`
- `EstadoColectorRegistry` y `SseSectoresBroadcaster` usan estructuras concurrentes
- `TelegramApiAdapter` usa `AtomicLong`

## 3. Errores (RFC 7807)

El prefijo de `type` es `https://aguavigia.example/errores/`. El manejador se mueve a `compartido/error/` **sin cambiar ninguna fila**.
Si una excepción cambia de paquete, la fila sigue igual.

| Excepción | Estado | `type` |
|---|---|---|
| `RecursoNoEncontrado`, `EntidadNoEncontrada`, `NoResourceFound` | 404 | `recurso-no-encontrado` |
| `ServicioNoDisponible` | 503 | `servicio-no-disponible` |
| `DataAccessException` | 503 | `base-de-datos-no-disponible` |
| `IllegalArgument`, validación, parámetro o parte faltante, cuerpo ilegible, tipo de argumento | 400 | `peticion-invalida` |
| `IllegalState` | 409 | `conflicto-de-estado` |
| `LimiteReportesExcedido` | 429 | `limite-reportes-excedido` |
| `LimiteDePeticionesExcedido` | 429 | `limite-de-peticiones-excedido` |
| `CredencialInvalida` | 401 | `credencial-invalida` |
| `SegundoFactorRequerido` | 401 | `segundo-factor-requerido` |
| `SesionSinCuenta` | 401 | `sesion-sin-cuenta` |
| `DispositivoInvalido` | 401 | `dispositivo-invalido` |
| `CuentaNoHabilitada` | 403 | `cuenta-no-habilitada` |
| `SubidaNoAutorizada` | 403 | `subida-no-autorizada` |
| `EnlaceDeRestablecimientoInvalido` | 403 | `enlace-invalido` |
| `AccessDenied` | 403 | `acceso-denegado` |
| Sin sesión en una ruta protegida (lo emite el punto de entrada de `SecurityConfig`, no el manejador global) | 401 | `no-autenticado` |
| `CuentaBloqueada` | 423 | `cuenta-bloqueada` |
| `HttpRequestMethodNotSupported` | 405 | `metodo-no-permitido` |
| `HttpMediaTypeNotAcceptable` | 406 | `formato-no-aceptable` |
| `FormatoNoPermitido` | 415 | `formato-no-permitido` |
| `HttpMediaTypeNotSupported` | 415 | `tipo-de-contenido-no-soportado` |
| `UbicacionFueraDelBarrio` / `UbicacionImprecisa` | 422 | `ubicacion-fuera-del-barrio` / `ubicacion-imprecisa` |
| `MaxUploadSizeExceeded` | 413 | `archivo-demasiado-grande` |
| `AsyncRequestNotUsable` | — | sin cuerpo (se ignora) |
| cualquier otra | 500 | `error-interno` |

Las excepciones que el manejador traduce viven todas en `compartido/error/`, junto a él. Así `compartido` no depende de
ninguna funcionalidad, y la tabla y sus clases quedan en un solo lugar. Simplificación permitida: unir en una sola clase,
con mensajes distintos, las excepciones que comparten estado y `type`. Una excepción con `type` propio sigue siendo una
clase propia.

## 4. Eventos y tareas programadas

- **`SectorActualizadoEvent(Sector)`** se publica después del commit en `guardar`, `cambiarEstadoSiEs`, `publicarSiEs` y `abrirDisputaSiEs`. **No** se publica en `confirmarEstado`. Lo escuchan, con `@Async @EventListener`:
  - `AvisoSseSectorListener` (SSE)
  - `AlertaPushSectorListener` (Telegram)
  - `NotificarSuscripcionesListener` (correo)
- **`ApplicationReadyEvent`** lo escuchan `SembradorAdminInicial`, `ImportadorDeVecinosSinteticos`, `PuestaAlDiaDeEstadosJob` e `IndicesMongo`.
- **Redis pub/sub:** canal `aguavigia:sse:sectores` (`SseSectoresBroadcaster`).
- **`@Scheduled`** (mismas frecuencias y el mismo `EjecucionUnica` donde lo haya):

| Tarea | Frecuencia |
|---|---|
| `SseSectoresBroadcaster.difundir` | 1 s |
| `SseSectoresBroadcaster.latido` | 25 s |
| `EvaluacionPendienteJob` | 1 s |
| `PlanificadorDeVentanas` | 60 s |
| `PuestaAlDiaDeEstadosJob` | 5 min y al arrancar |
| `PipelineOrquestador.ejecutarCiclo` | según configuración |
| `LimpiezaFotosHuerfanasJob` | 03:00 |
| `PurgaEvidenciaAntiguaJob` | 03:30 |
| `TelegramSondeoJob` | 3 s |

## 5. Reglas de ArchUnit que se conservan con otra forma

Hoy están en `backend/src/test/java/com/aguavigia/ctg/architecture/ReglaDeOroArchitectureTest.java`.

| Hoy | Después de R9 |
|---|---|
| `domain/` no importa Spring ni Mongo | `..reglas..` no importa `org.springframework..` ni `com.mongodb..` |
| `EventoBitacora` solo se crea desde su factory o el adaptador | Solo `bitacora..` llama al constructor de `EventoBitacora` |
| Nadie en producción llama a `SectorRepository.guardar` ni a `cambiarEstadoSiEs` | La misma regla, sobre los métodos equivalentes de `SectorAlmacen` |
| Toda ruta del panel exige `@PreAuthorize` | **Igual** |
| Toda ruta del vecino exige `@PreAuthorize` | **Igual** |
| `api` no escucha eventos ni programa tareas | Los `@RestController` no usan `@EventListener` ni `@Scheduled` |
| `api` y `application` no dependen de `infrastructure` | Ningún `@RestController` depende de un `*Almacen` ni de un `MongoRepository` |
| — (nueva) | `compartido..` no depende de ninguna funcionalidad |

## 6. Datos de arranque y entornos

Lo que existe hoy y la reducción no puede mover. Dos instancias del mismo código, con datos y reloj distintos:

| | Instancia real | Simulación |
|---|---|---|
| Puerto de la API | 8081 | 8082 |
| Servicio de Compose | `backend` | `backend-sim` (perfil `simulacion`) |
| Base de Mongo | `aguavigia` | `aguavigia_sim` |
| Redis | base 0 | base 1 |
| Reloj | el del sistema | `RelojSimulado` (`POST /api/sim/reloj`) |
| Boletines | los lee de Acuacar | los entrega el simulador (`POST /api/sim/boletines`) |
| `/api/sim/**` | 404 | activo, con `X-Sim-Key` (503 sin `SIMULACION_CLAVE`) |
| `AGUAVIGIA_MODO` | `REAL` | `SIMULACION` (lo muestra `GET /api/sistema/modo`) |
| Cuentas sintéticas al arrancar (`VECINOS_SINTETICOS`) | **30 000** | 0 |
| Límite de peticiones | `RATE_LIMIT_FACTOR` (1 en uso normal) | `SIM_RATE_LIMIT_FACTOR` (1000) |

- **Las 30 000 cuentas** las crea el backend de la instancia real al arrancar (`ImportadorDeVecinosSinteticos`, con
  `ApplicationReadyEvent`; el valor por defecto está en `application-docker.yml` y en `docker-compose.yml`). Son lo único inventado
  del sistema: vecinos activos, marcados como demostración, con barrio del catastro y sin consentimiento que nadie dio.
  `verificar-datos.mjs` lo comprueba. **Pendiente de medir en R0:** la base que había al empezar R0 tenía 1 usuario, así que hay que
  comprobar con qué comando exacto aparecen las 30 000 y cuánto tarda.
- **Los scripts de apoyo dependen de la forma exacta de los documentos:** `agregar-usuarios.mjs` en modo `directo` inserta cuentas
  completas (usuario, tokens, auditoría, suscripciones) con una marca `lote`; el simulador y `verificar-datos.mjs` leen campos concretos
  (el contrato de estos últimos lo guarda `UsuarioMongoAdapterTest.elDocumentoSinteticoTieneLosCamposQueVerificarDatosEspera`).
- **La simulación nunca toca la real:** el simulador se niega a trabajar si la base no acaba en `_sim` o si Redis es la base 0.
- Lo que muestra cada instancia es **el estado de los sectores (barrios)**. Que un día de presentación la real muestre agua en todas
  partes es correcto; para ver barrios sin agua y reportes activos está la simulación.

### Claves de Redis

Mismo nombre, mismo tipo y misma caducidad. **Medido en R0** con `esquema-datos.mjs` sobre la base 0, después de `verificar-flujos.mjs`
(`scripts/reduccion/linea-base/esquema-datos.json`). Esa herramienta manda sobre esta tabla. Todas las claves de abajo son de tipo
`string` y **caducan**, salvo las marcadas.

| Patrón de clave | Qué guarda | Quién la escribe hoy |
|---|---|---|
| `consenso:sector:{sectorId}` (**zset**, caduca) | ventana de votos del consenso de un barrio | `RedisContadorReportesAdapter` |
| `cupo:{id}` y `cupo:{sectorId}:{id}` | cupo de reportes por dispositivo o cuenta (`INCR` + `EXPIRE`) | `RedisContadorReportesAdapter`, `RedisCupoPorCuentaAdapter` |
| `login:fallos:{id}` (y `login:bloqueo:{id}` al bloquearse) | contador de fallos de ingreso y bloqueo de la cuenta | `RedisControlIntentosAdapter` |
| `unico:{id}` | «solo la primera vez» (`SETNX`): un código TOTP vale una vez | `RedisControlIntentosAdapter` |
| `sesion:revocada:{id}` | marca de revocación de sesión (**falla cerrado** si Redis no responde) | `RedisRevocacionSesionAdapter` |
| `rate-limit:{ruta}:{ip}` con la ruta tal cual la declara la regla: `/api/cuentas/**`, `/api/dispositivos`, `/api/sectores/*/restablecimiento`, `/api/suscripciones/**`, `/api/vecino/sesion`, `/api/veedor/segundo-factor/**`, entre otras | contador por IP y ruta (script Lua `CONTAR_Y_CADUCAR`) | `RateLimitingInterceptor` |
| `tarea-unica:{nombre}` (`ingesta`, `puesta-al-dia`, `ventanas`…) | `SETNX` de ejecución única entre réplicas, liberado con Lua (`SOLTAR`) | `EjecucionUnicaRedis` |
| `ingesta:visto:{id}` | hash de documentos ya vistos por la ingesta (deduplicador) | `DeduplicadorReciente` |
| `aguavigia:consenso:reserva:{sectorId}` y `aguavigia:consenso:pendientes` | reserva (`SETNX`) para que corra una evaluación a la vez, y barrios pendientes | `RedisReservaDeEvaluacionAdapter` |
| `aguavigia:sse:sectores` (canal pub/sub, no es una clave) | difusión de cambios de estado a las conexiones SSE | `SseSectoresBroadcaster` |

La caché de Spring (`@Cacheable`) aparece como `sectores::SimpleKey []` (tipo `string`, caduca) cuando alguien consultó ese dato poco antes. Todas las entradas de
caché (el nombre lleva `::`) se tratan como transitorias. Algunas claves solo existen mientras dura su caso: `tarea-unica:*` (candados de unos segundos), `aguavigia:consenso:*`, `login:bloqueo:*` y las entradas de caché.
`esquema-datos.mjs` las trata como **aviso** cuando aparecen o desaparecen entre dos lecturas, y como **diferencia** si cambian de tipo o de caducidad.

### El campo `_class` de Mongo

Spring Data guarda en cada documento `_class` con el **nombre completo de la clase Java** (por ejemplo
`com.aguavigia.ctg.infrastructure.persistence.mongo.CorteAguaDocumento`). Medido en R0, lo llevan 11 de las 16 colecciones; no lo llevan `sectores`,
`config_sistema` ni `bloqueos_administracion`, y `documentos_fallidos` y `suscripciones_telegram` estaban vacías (se desconoce si lo llevan).

**Cuando una fase mueva un `@Document` a otro paquete, el valor de `_class` de los documentos nuevos cambiaría**, aunque los campos sigan igual, y
el requisito 6 pide la base «igualita». Dos consecuencias que cada fase tiene que resolver y anotar:
- los documentos viejos deben seguir leyéndose. **No está verificado** qué hace Spring Data si `_class` nombra una clase que ya no existe; se prueba restaurando el respaldo de R0 sobre el código nuevo antes de mover el primer `@Document`;
- para que los nuevos escriban el **mismo** valor, hay que fijarlo (un `TypeInformationMapper` que devuelva los nombres de hoy). `esquema-datos.mjs` compara los
  valores distintos de `_class` por colección y marca cualquier cambio. `scripts/restablecer-admin.mjs` escribe la clase de auditoría a mano.

