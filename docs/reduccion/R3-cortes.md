# R3 · Cortes

**Objetivo:** mover los cortes oficiales a `cortes/`. Incluye el cierre por barrio, la confirmación, la anulación, la
expiración, los vencidos y el historial público.

Riesgo: medio. Tiene un upsert y una transacción que agrupa el corte, su bitácora y el estado de los barrios. Esfuerzo: 1–2 sesiones.

## Alcance (24 archivos hoy)

| Hoy | Destino |
|---|---|
| `CorteAgua` + `CorteAguaDocumento`, `CorteId`, `EstadoCorte`, `OrigenCorte`, `CierreDeCorte` | `cortes/CorteAgua.java` (`@Document("cortes")`) y sus tipos. Las validaciones del constructor se conservan |
| `CorteAguaRepository` + `CorteAguaMongoAdapter` + `CorteAguaMongoRepository` | `cortes/CorteAlmacen.java`. Implementa temporalmente `CorteAguaRepository`, que todavía usan ingesta (R5) y cumplimiento (R6) |
| `GestionarCorteOficialUseCase`/`Service`, `ExpirarCortesVencidosUseCase`/`Service`, `ListarCortesVencidosUseCase`/`Service`, `ConsultarHistorialDeCortesUseCase`/`Service` | `cortes/CorteService.java` (crear, cerrar, confirmar, anular, consultar) y `cortes/ExpiracionDeCortesService.java` (expirar y listar vencidos), si juntos pasan de ~400 líneas |
| `CorteController`, `HistorialDeCortesController` | `cortes/` |
| `CorteRespuesta`, `SolicitudCierreCorte`, `SolicitudCorte`, `SolicitudAnulacion`, `CorteApiMapper` | `cortes/CorteDtos.java`. `IngestaRevisionController` también usa `SolicitudAnulacion`: la importa desde `cortes` |

Lo que ya se movió en R2 y ahora se conecta directo:
- `RecalculoDeEstadoService` lee cortes con `CorteAlmacen`, no con el puerto.
- `CorteService` llama a `RecalculoDeEstadoService` y a `BitacoraService` como clases, no por sus interfaces.

## Lo delicado

- `CorteAguaMongoAdapter.anexarSectorAlCorte` es un upsert: se copia literal.
- **La transacción:** hoy guardar un corte, su bitácora y el estado de sus barrios es una sola transacción (decisión de la limpieza del 2026-09-29). Se mantiene en `CorteService` con `@Transactional`/`TransactionTemplate`, en el mismo método. Hay que probarlo con un fallo provocado a mitad del proceso, si el test no existe ya.
- **Las rutas del panel conservan su `@PreAuthorize` exacto.** La regla ArchUnit de permisos lo vigila.
- **`ExpirarCortesVencidosService` corre al arrancar o con un job.** Hay que conservar cuándo corre y su `EjecucionUnica`.

## Lo que cae

- **Puertos de entrada:** `GestionarCorteOficialUseCase`, `ExpirarCortesVencidosUseCase`, `ListarCortesVencidosUseCase`, `ConsultarHistorialDeCortesUseCase`.
- **Mapper:** `CorteApiMapper`.
- **Documento y adaptador Mongo** de cortes.
- **Sigue vivo** (patrón de transición): `CorteAguaRepository`.

## Terminado cuando

- La puerta está en verde. `verificar-flujos.mjs` recorre crear, cerrar por barrio, confirmar, anular y vencidos.
- `verificar-datos.mjs` muestra la colección `cortes` sin cambios.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R3-cortes.md.
Ejecuta R3 en refactor/reduccion-backend (antes: git merge main). Mueve cortes según la tabla de alcance con el patrón de
transición; conecta RecalculoDeEstadoService a CorteAlmacen. Conserva la transacción corte+bitácora+estado, el upsert de
anexarSectorAlCorte y los @PreAuthorize. Tests: mismos casos y aserciones. Corre la puerta completa y muéstrame la salida.
No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
