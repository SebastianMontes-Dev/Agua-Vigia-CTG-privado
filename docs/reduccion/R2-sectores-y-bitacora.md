# R2 · Sectores y bitácora

**Objetivo:** mover el centro del sistema a `sectores/` y `bitacora/`:
- el estado de un barrio, el resolutor y el recálculo
- el consenso de vecinos y las tareas que lo barren
- el evento `SectorActualizadoEvent`
- la bitácora completa (escritura, lectura y sustento)

Todo lo demás depende de esto, por eso va primero.

Riesgo: **alto**. Es la fase con más operaciones atómicas y con el evento posterior al commit. Esfuerzo: 2–3 sesiones.

## Alcance: `sectores/` (51 archivos hoy)

| Hoy | Destino |
|---|---|
| **Reglas puras:** `ResolutorDeEstadoSector`, `ReglasDeEstado`, `Afirmacion`, `EstrategiaConsenso`, `UmbralFijoEstrategiaConsenso`, `UmbralProporcionalEstrategiaConsenso`, `ResultadoConsenso`, `VotosDeVecinos`, `VotoReciente`, `RespaldoVecinal`, `ComposicionDelSustento`, `MemoriaDelBarrio`, `MarcasDeEstado`, `ResultadoDeRecalculo`, `DescripcionDeEstado`, `NivelDeVerificacion`, `VentanaTiempo` | `sectores/reglas/`, sin cambios de código |
| **Modelo:** `Sector` + `SectorDocumento` + `GeometriaSector`, `SectorId`, `EstadoServicio`, `EstadoPublicado`, `OrigenEstado` | `sectores/Sector.java` (`@Document("sectores")`) y sus tipos de valor en `sectores/` |
| **Almacén:** `SectorRepository` (puerto) + `SectorMongoAdapter` + `SectorMongoRepository` + `GeometriaSectoresPort` + `GeometriaSectoresMongoAdapter` | `sectores/SectorAlmacen.java`. Implementa temporalmente `SectorRepository` (patrón de transición) |
| **Recálculo:** `RecalcularSectorUseCase`/`Service`, `PonerAlDiaSectoresUseCase`/`Service`, `ActualizarEstadosPorVentanaUseCase`/`Service` | `sectores/RecalculoDeEstadoService.java`. Implementa temporalmente `RecalcularSectorUseCase` |
| **Consenso:** `EvaluarConsensoUseCase`/`Service`, `ReservaDeEvaluacionPort` + `RedisReservaDeEvaluacionAdapter` | `sectores/ConsensoService.java` (el `SETNX` de la reserva va dentro, copiado literal). Implementa temporalmente `EvaluarConsensoUseCase` |
| **Consulta:** `ListarSectoresAfectadosUseCase`/`Service` | `sectores/SectorService.java` |
| **Controlador y DTO:** `SectorController`, `GeometriaSectoresRespuesta`, `RespuestaSectores`, `SectorRespuesta`, `SectorApiMapper` | `sectores/SectorController.java`, `sectores/SectorDtos.java` (records con el mismo nombre simple y `desde(...)`) |
| **Infraestructura:** `EvaluacionPendienteJob`, `PuestaAlDiaDeEstadosJob`, `SectorActualizadoEvent`, `AvisoSseSectorListener` | `sectores/` |
| **Config:** `ConsensoConfig` + `EstadoConfig` | `sectores/SectoresConfig.java`, con las mismas propiedades y los mismos valores por defecto |

## Alcance: `bitacora/` (16 archivos hoy)

| Hoy | Destino |
|---|---|
| `EventoBitacora` + `EventoBitacoraDocumento`, `EventoId`, `TipoEvento`, `FiltroBitacora` | `bitacora/EventoBitacora.java` (`@Document("eventos_bitacora")`) y sus tipos |
| `EventoBitacoraFactory` | `bitacora/reglas/` si es puro. Si no, sus métodos pasan a `BitacoraService` |
| `EventoBitacoraRepository` + `EventoBitacoraMongoAdapter` + `EventoBitacoraMongoRepository` | `bitacora/BitacoraAlmacen.java`. Implementa temporalmente `EventoBitacoraRepository` |
| `RegistrarEventoBitacoraUseCase`/`Service`, `ConsultarSustentoDeEventoUseCase`/`Service` | `bitacora/BitacoraService.java`. Implementa temporalmente `RegistrarEventoBitacoraUseCase` |
| `BitacoraController`, `EventoBitacoraRespuesta`, `EventoBitacoraApiMapper` | `bitacora/BitacoraController.java`, `bitacora/BitacoraDtos.java` |

Se queda **igual**: el parseo de filtros que hoy hace `BitacoraController`. Se puede mover al servicio, pero sin cambiar
qué filtros acepta ni sus errores.

## Lo delicado

1. **Copiar literal las operaciones atómicas de `SectorMongoAdapter`** ([invariantes §2](invariantes.md#2-operaciones-atómicas-o-sensibles-a-concurrencia)):
   - `cambiarEstadoSiEs`, `publicarSiEs` y `abrirDisputaSiEs` (`findAndModify` con el estado esperado en el filtro)
   - `confirmarEstado` (`updateFirst` con el mismo filtro)
   - `trasConfirmar(...)`, que publica `SectorActualizadoEvent` **después del commit**, y **no** desde `confirmarEstado`
2. **Caché:** si `SectorMongoAdapter` tiene `@Cacheable`/`@CacheEvict` (lo prueba `SectorMongoAdapterCacheTest`), las anotaciones y los nombres de caché pasan tal cual a `SectorAlmacen`.
3. **La transacción del recálculo:** se abre donde hoy abre `TransaccionPort` en `RecalcularSectorService`. En el código nuevo es `@Transactional` o `TransactionTemplate`. Lo verifica `SectorMongoAdapterTransaccionTest`, que se mueve con el almacén.
4. **ArchUnit:** las dos invariantes pasan a sus formas nuevas:
   - «solo `bitacora..` crea `EventoBitacora`»
   - «nadie en producción llama a `SectorAlmacen.guardar` ni a `cambiarEstadoSiEs`»
5. **El recálculo lee cortes, reportes y propuestas por sus puertos viejos** (`CorteAguaRepository`, `ReporteCiudadanoRepository`, `PropuestaIngestaRepository`). Es correcto en esta fase: R3, R4 y R5 los cambian por los almacenes nuevos.

## Tests

- Los tests de las reglas (`ResolutorDeEstadoSectorTest`, `ReglasDeEstadoTest`, `VotosDeVecinosTest`, `EventoBitacoraFactoryTest`, `SectorTest`…) se mueven sin tocar el cuerpo.
- Los tests de servicio que usan puertos falsos (`RecalcularSectorService*Test`, `EvaluarConsensoService*Test`, `ConsensoContraVentanaTest`, `PonerAlDia*Test`…) pasan a usar `Mockito.mock(SectorAlmacen.class)` o el almacén real con Testcontainers. Los **casos y las aserciones no cambian**.
- `SectorMongoAdapter*Test` y `EventoBitacoraMongoAdapterTest` se convierten en `SectorAlmacenTest` y `BitacoraAlmacenTest`, que conservan todos sus casos.
- `SectorControllerTest` y `BitacoraControllerTest` solo cambian el tipo que se mockea.
- Test nuevo: `debePublicarSectorActualizadoSoloDespuesDelCommit()`. Si ya existe, se conserva.

## Lo que cae en esta fase

- **Puertos de entrada:** `ListarSectoresAfectadosUseCase`, `PonerAlDiaSectoresUseCase`, `ActualizarEstadosPorVentanaUseCase`, `ConsultarSustentoDeEventoUseCase`.
- **Puertos de salida sin consumidores viejos:** `GeometriaSectoresPort`, `ReservaDeEvaluacionPort`.
- **Mappers:** `SectorApiMapper`, `EventoBitacoraApiMapper`.
- **Documentos y adaptadores Mongo:** los del sector y los de la bitácora.
- **Siguen vivos** por el patrón de transición: `SectorRepository`, `RecalcularSectorUseCase`, `EvaluarConsensoUseCase`, `RegistrarEventoBitacoraUseCase` y `EventoBitacoraRepository`.

## Terminado cuando

- La puerta está en verde, incluido el guion de simulación, que ejercita consenso, disputas y recálculo a escala.
- `node scripts/verificar-datos.mjs` muestra las colecciones `sectores` y `eventos_bitacora` sin cambios.
- `CasosDeUsoConfig` ya no crea servicios de sectores ni de bitácora.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R2-sectores-y-bitacora.md.
Ejecuta R2 en refactor/reduccion-backend (antes: git merge main). Mueve sectores y bitácora según las tablas de alcance,
aplicando el patrón de transición (la clase nueva implementa la interfaz vieja mientras queden consumidores viejos).
Copia literal las operaciones atómicas y el trasConfirmar de SectorMongoAdapter; conserva caché y transacciones.
Las reglas puras van a reglas/ sin cambios de código. Los tests conservan sus casos y aserciones; solo cambia el andamiaje.
Haz commits pequeños. Al final corre la puerta completa (incluido el guion de simulación y verificar-datos.mjs) y muéstrame
la salida. No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
