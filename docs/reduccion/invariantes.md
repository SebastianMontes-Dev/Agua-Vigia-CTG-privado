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
Comprobación: `node scripts/verificar-datos.mjs` antes y después de cada fase que toque persistencia.

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
