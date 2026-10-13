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

- [x] La puerta está en verde, con 0 diferencias de contrato y de forma. El manejador de errores es el lugar donde una errata se vería, y la muestra de errores de `instantanea.mjs` lo cubre.
- [x] `compartidoNoDependeDeFuncionalidades` pasa **sin** `allowEmptyShould`.
- [x] Ya no existen `infrastructure/cache`, `logging`, `ratelimit`, `sse`, `scheduling` ni `api/error`. En `infrastructure/config` quedan solo las configuraciones de cada funcionalidad, que se mueven con ella: `CasosDeUsoConfig` (R9), `ConsensoConfig` y `EstadoConfig` (R2), `IngestaConfig` (R5), `MantenimientoConfig` y `SimulacionConfig` (R9), `SecurityConfig`, `SembradorAdminInicial` y `ImportadorDeVecinosSinteticos` (R8).

## Resultado de R1

Cerrada el 2026-10-08. Commits en `refactor/reduccion-backend`, uno por unidad de trabajo:
1. Mover a `compartido/` las 78 clases y tests del alcance (solo `package` e `import`; ningún test cambió de cuerpo).
2. Renombrar `RelojPort` a `Reloj`.
3. Eliminar `CanalEnVivoPort`: `SectorController` y su test usan `SseSectoresBroadcaster` directamente.
4. Unir los secretos del sistema: `compartido/secretos/SecretosDelSistema` (clase concreta, el upsert `$setOnInsert` sin cambios) y `ConfigSistema` (`@Document("config_sistema")`).
5. Fijar el campo `_class` (ver abajo).

**Decisiones y desviaciones de la guía:**
- Cuatro clases eran de acceso de paquete (`CabecerasDePaginacion`, `PaginaDeCortesia`, `EscritorCsv`, `PlantillaCorreo`) y ahora las usan otros paquetes: pasaron a `public` junto con sus métodos. Es el único cambio que no es `package`/`import`.
- Además de los tests que listaba el paso 3 se movieron con su clase los de las configuraciones movidas (`RateLimitConfigAvisoTest`, `ReglasDeRateLimitDeProduccionTest`, `CorsPorPerfilTest`, sus ayudantes), `JobsProgramadosEjecucionUnicaTest` y `TareasProgramadasTest` (a `compartido/tareas`), `ReglasDeLimiteDeApplicationYmlTest` (a `compartido/ratelimit`), `RedDeOrigenTest`, `CoordenadaTest` y `CorreoElectronicoTest`.
- `ConfigSistemaMongoAdapterTest` pasó a `SecretosDelSistemaTest` (misma prueba contra Mongo real). Los tests de los adaptadores HMAC pasaban un lambda como puerto: ahora usan `SecretosDePrueba`, un ayudante de test que sobrescribe `obtenerOCrear`.
- **`_class`: se implementó con una anotación, no con un mapa.** ADR-099 decía «`ConfigurableTypeInformationMapper` en `compartido/config`»; un mapa con las clases de todas las funcionalidades obligaría a `compartido` a importarlas, y ArchUnit lo prohíbe. Por eso `@ClaseGuardadaComo("<nombre de antes>")` va sobre cada `@Document` movido y `MapeadorDeClaseGuardada` lo escribe. Lo comprueban `MapeadorDeClaseGuardadaTest` y `MongoClaseGuardadaConfigTest` (contra Mongo real). **R2–R8: copiar el nombre de la clase vieja a la anotación en el mismo commit que mueve el `@Document`.**
- `compartidoNoDependeDeFuncionalidades` ya no usa `allowEmptyShould`.
- No se hizo la simplificación opcional de unir excepciones con el mismo estado y `type` (invariantes §3): el manejador y la tabla quedaron intactos.
- `CasosDeUsoConfig` no cambió: ningún servicio se movió todavía.

**Puerta (todo ejecutado el 2026-10-08):** `./mvnw clean verify` 2 099 tests, 0 fallos, 1 omitido (los 2 094 de R0 más 5 nuevos); `puerta.sh comparar` desde `down -v`: TODO EN VERDE (contrato 84 operaciones, forma con y sin datos, 33 flujos y esquema de Mongo y Redis sin diferencias); `sembrador verificar` OK; `agregar-usuarios` directo (50) y api (20) con `--borrar-lote`; `demo.mjs` en miniatura con `--restaurar`: todos los umbrales de k6 cumplidos; guion de simulación ×300: 54/54 en 177 s. La capacidad completa no se repite en R1 (solo se exige al cerrar R2, R4, R8 y R9).

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
