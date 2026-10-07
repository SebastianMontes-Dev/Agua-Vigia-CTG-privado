# R1 · Compartido y cableado

**Objetivo:** crear `compartido/` y llevar ahí lo transversal: errores, configuración, reloj, HTTP, SSE, rate limit,
tareas únicas, correo y secretos. Es la base que usan todas las fases siguientes.

Riesgo: medio. Toca el manejador de errores y la configuración, pero no la lógica de negocio. Esfuerzo: 1–2 sesiones.

## Alcance

| Hoy | Va a | Cómo |
|---|---|---|
| `api/error/ManejadorGlobalDeErrores`, `RecursoNoEncontradoException`, `ServicioNoDisponibleException`, `domain/EntidadNoEncontradaException`, `domain/LimiteDePeticionesExcedidoException` | `compartido/error/` | Mover |
| Las excepciones de la tabla de [invariantes §3](invariantes.md#3-errores-rfc-7807): `LimiteReportesExcedido`, `CredencialInvalida`, `SegundoFactorRequerido`, `SesionSinCuenta`, `DispositivoInvalido`, `CuentaNoHabilitada`, `SubidaNoAutorizada`, `EnlaceDeRestablecimientoInvalido`, `CuentaBloqueada`, `FormatoNoPermitido`, `UbicacionFueraDelBarrio`, `UbicacionImprecisa` | `compartido/error/` | Mover. Solo cambian `package` e `import` |
| `infrastructure/config/`: `AsyncConfig`, `CacheConfig`, `CorsProperties`, `MongoPoolConfig`, `MongoTransaccionConfig`, `OpenApiConfig`, `RateLimitConfig`, `RedisConfig`, `RelojConfig`, `SchedulingConfig`, `ValidacionDeSecretos` | `compartido/config/` | Mover |
| `infrastructure/cache/` (`CacheProperties`, `ManejadorDeErroresDeCache`), `infrastructure/persistence/mongo/IndicesMongo` | `compartido/config/` | Mover |
| `domain/port/out/RelojPort` + `infrastructure/RelojDelSistema` | `compartido/reloj/Reloj` (interfaz) + `RelojDelSistema` | Renombrar `RelojPort` a `Reloj` en todo el código (cambio mecánico). Sigue siendo interfaz: `RelojSimulado` (R9) es la segunda implementación |
| `api/CabecerasDePaginacion`, `api/PaginaDeCortesia`, `domain/Pagina`, `api/EscritorCsv` | `compartido/http/` | Mover |
| `infrastructure/logging/` (`CorrelationIdFilter`, `MdcTaskDecorator`) | `compartido/logging/` | Mover |
| `infrastructure/ratelimit/` (`RateLimitingInterceptor`, `RateLimitProperties`), `domain/RedDeOrigen` | `compartido/ratelimit/` | Mover |
| `infrastructure/sse/` (`SseConfig`, `SseSectoresBroadcaster`), `domain/port/out/CanalEnVivoPort` | `compartido/sse/` | Mover. `CanalEnVivoPort` tiene una sola implementación: los que la usan pasan a depender de `SseSectoresBroadcaster` directamente y se borra la interfaz |
| `infrastructure/scheduling/` (`EjecucionUnica`, `EjecucionUnicaRedis`) | `compartido/tareas/` | Mover |
| `infrastructure/mail/PlantillaCorreo` | `compartido/correo/` | Mover |
| `domain/port/out/SecretosDelSistemaPort` + `ConfigSistemaMongoAdapter` + `ConfigSistemaDocumento` | `compartido/secretos/SecretosDelSistema` (clase concreta) + `ConfigSistema` (`@Document("config_sistema")`) | Unir puerto, adaptador y documento. El upsert de `leerOCrear` se copia literal |
| `domain/Coordenada`, `domain/CorreoElectronico`, `api/dto/CoordenadaDTO` | `compartido/valor/` | Mover. El record `CoordenadaDTO` conserva su nombre |

**No entran en R1:**
- `ContextoHttp` y `ContextoDeAccion` dependen de la sesión, así que van a `cuentas/` en R8.
- `GeneradorSecretosSeguroAdapter` usa `SecretoTotp` y va a `cuentas/` en R8.
- `SecurityConfig` y `JwtAuthenticationFilter` van a `cuentas/seguridad/` en R8.

### `CasosDeUsoConfig` y `TransaccionPort`

Hoy `CasosDeUsoConfig` crea a mano los 57 servicios de `application/`. Es necesario porque ArchUnit prohíbe que
`application/` importe Spring, ni siquiera `@Service`. **Se vacía fase a fase.** Cuando una funcionalidad se mueve, sus
servicios pasan a ser `@Service` en su paquete nuevo y se borran sus métodos `@Bean` de `CasosDeUsoConfig`. El archivo
desaparece en R9.

`TransaccionPort` sigue igual mientras quede código viejo que lo use. El código nuevo usa `@Transactional` o
`TransactionTemplate` en el **mismo método** que hoy abre la transacción.

## Pasos

1. `git checkout refactor/reduccion-backend && git merge main`.
2. Crear `compartido/` con sus subpaquetes. Mover las clases con el IDE (*Move class*) para que actualice `import`/`package`.
3. Mover los tests con su clase: `ManejadorGlobalDeErroresTest`, `CorrelationIdFilterTest`, `MdcTaskDecoratorTest`, `RateLimitingInterceptorTest`, `RateLimitPropertiesTest`, `EjecucionUnicaRedisTest`, `CachePropertiesTest`, `ManejadorDeErroresDeCacheTest`, `EscritorCsvTest`, los `*ConfigTest` de las clases movidas, `ValidacionDeSecretosTest` e `IndicesMongoTest`. **No se edita el cuerpo de ningún test**, solo `package`/`import`.
4. Renombrar `RelojPort` → `Reloj`.
5. Unir `SecretosDelSistema` y borrar `CanalEnVivoPort`.
6. `./mvnw verify`, y después la puerta completa.

## Terminado cuando

- La puerta está en verde, con 0 diferencias de contrato y de forma. El manejador de errores es el lugar donde una errata se vería, y la muestra de errores de `instantanea.mjs` lo cubre.
- `compartidoNoDependeDeFuncionalidades` pasa **sin** `allowEmptyShould`.
- Ya no existen `infrastructure/config`, `cache`, `logging`, `ratelimit`, `sse`, `scheduling` ni `api/error`. Solo queda `SecurityConfig`, que se mueve en R8.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R1-compartido-y-cableado.md.
Ejecuta R1 en refactor/reduccion-backend (antes: git merge main). Mueve exactamente las clases de la tabla de alcance a
compartido/, con sus tests, sin cambiar el cuerpo de ningún test ni ninguna fila del manejador de errores.
Renombra RelojPort a Reloj, une SecretosDelSistema (puerto + adaptador + documento) y elimina CanalEnVivoPort.
Haz commits pequeños en Conventional Commits en español. Al final corre la puerta completa del README
(mvnw verify, comparar-contrato, instantanea, verificar-flujos, guion de simulación) y muéstrame la salida.
No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
