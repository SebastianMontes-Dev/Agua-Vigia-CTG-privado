# R6 · Consultas públicas

**Objetivo:** mover las consultas de rendición de cuentas, que solo leen: el Índice de Cumplimiento, las estadísticas y
Open311. La bitácora ya se movió en R2.

Riesgo: bajo. Solo leen; el cálculo del Índice ya es puro. Esfuerzo: 1 sesión.

## Estructura destino

```
cumplimiento/
├── CumplimientoService.java        CalcularCumplimientoUseCase + Service (lee con CorteAlmacen)
├── IndiceCumplimientoController.java   mismo nombre y mismas 6 rutas, incluida /serie.csv
├── CumplimientoDtos.java           CalidadDelCumplimientoRespuesta, IndiceCumplimientoRespuesta, PuntoSerieRespuesta
└── reglas/                         IndiceCumplimiento, CalidadDelCumplimiento, CalidadDelDato, AgregadoDuraciones,
                                    PuntoSerieCumplimiento, PuntoAgregadoMensual (sin cambios)
estadisticas/
├── EstadisticasService.java        CalcularEstadisticasUseCase + Service + EstadisticasRepository + EstadisticasMongoAdapter
│                                   (las agregaciones de Mongo se copian literal)
├── EstadisticasController.java     mismas 2 rutas, incluida /exportar.csv
└── EstadisticasDtos.java           EstadisticasRespuesta
sistema/Open311Controller.java      (se mueve aquí ya, porque solo lee)
```

**Lo que cae:**
- Puertos de entrada: `CalcularCumplimientoUseCase`, `CalcularEstadisticasUseCase`
- Puerto de salida: `EstadisticasRepository`
- Mappers: `CumplimientoApiMapper`, `EstadisticasApiMapper`
- `CorteAguaRepository`, si ya no le quedan consumidores viejos. Ingesta se movió en R5 y cumplimiento ahora. Comprobar con `grep` antes de borrarlo.

## Lo delicado

- **La regla ADR-022** (se suman duraciones, no se promedian porcentajes) y la medición por par corte-barrio viven en `reglas/` y no se tocan.
- **CSV:** `EscritorCsv` (R1) sigue anteponiendo una comilla a los textos que empiezan por `=`, `+`, `-` o `@`. Las columnas y el separador no cambian.
- **Caché:** las anotaciones `@Cacheable` de estas consultas, si las hay, se conservan con el mismo nombre.

## Terminado cuando

- La puerta está en verde. La instantánea cubre `/api/cumplimiento*`, `/api/estadisticas` y `/api/v2/requests.json`.
- Los dos CSV son idénticos byte a byte antes y después, sobre la misma base. Se comprueba descargándolos y comparando con `diff`.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R6-consultas-publicas.md.
Ejecuta R6 en refactor/reduccion-backend (antes: git merge main). Mueve cumplimiento, estadísticas y Open311 según la
estructura destino; reglas sin cambios; agregaciones de Mongo literales. Compara los CSV byte a byte antes y después.
Corre la puerta completa y muéstrame la salida. No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
