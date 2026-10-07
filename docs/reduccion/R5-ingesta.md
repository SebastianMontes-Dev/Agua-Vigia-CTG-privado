# R5 · Ingesta

**Objetivo:** mover a `ingesta/` la ingesta de boletines:
- colectores: Acuacar API, RSS, local y simulado
- pipeline y planificador de ventanas
- extracción determinista
- propuestas y su revisión por el veedor
- salud de los colectores y documentos fallidos

Riesgo: medio. La extracción ya es pura y tiene muchos tests. Lo delicado es la ética de datos y el `synchronized` del ciclo.
Esfuerzo: 1–2 sesiones.

## Estructura destino

```
ingesta/
├── PropuestaIngesta.java          @Document("propuestas_ingesta") + PropuestaId, EstadoRevision, AvisoDeIngesta
├── PropuestaAlmacen.java          PropuestaIngestaRepository + adaptador + repo Spring (implementa temporalmente el puerto)
├── PropuestaService.java          Registrar y revisar (aprobar, descartar, anular) — antes 2 puertos + 2 servicios
├── IngestaRevisionController · IngestaFallidosController · IngestaSaludController   (mismos nombres, rutas, métodos y @PreAuthorize)
├── IngestaDtos.java               PropuestaIngestaRespuesta, DocumentoFallidoRespuesta, SaludColectorRespuesta
├── DocumentoFallido.java          @Document("documentos_fallidos") + su almacén
├── MarcaDeIngesta.java            @Document("marcas_ingesta") + su repo
├── colectores/
│   ├── FuenteDeBoletines.java     (antes FuenteDatosPort) interfaz: tiene 4 implementaciones reales
│   ├── AcuacarApiCollector · RssCollector · ColectorLocalDeBoletines · ColectorSimulado
│   ├── PipelineOrquestador        (synchronized, igual) · PlanificadorDeVentanas · DeduplicadorReciente
│   ├── EstadoColector · EstadoColectorRegistry · ColectorHealthIndicator · SaludDeColector
│   └── IngestaProperties · IngestaConfig
└── reglas/                        HeuristicaExtractor, LectorDeVentanaDeclarada, LimpiadorHtml, NormalizadorDeNombres,
                                   PrefiltroDeterminista, EmparejadorDeSectores, AliasDeBarrios, EventoExtraido,
                                   DocumentoCrudo, CompuertaDePublicacion (sin cambios)
```

**Puertos que se absorben:**
- `RegistrarPropuestaIngestaUseCase`, `RevisarPropuestaIngestaUseCase`
- `CicloDeIngestaPort`, `DocumentosFallidosPort`, `SaludDeColectoresPort`, `PropuestaIngestaRepository`
- `BuzonDeBoletinesSimuladosPort` se queda como clase concreta en `colectores/`, porque lo usa la simulación (R9)

`RecalculoDeEstadoService` pasa a leer las propuestas aprobadas con `PropuestaAlmacen`.

## Lo delicado: ética de datos (no negociable, `CLAUDE.md`)

- **El `User-Agent` del colector se lee igual de la configuración** y sigue identificando al proyecto con su correo. Si viene vacío, los colectores se niegan a llamar, como hoy.
- **No se cambia ninguna URL, frecuencia ni lectura de `robots.txt`.** No se añaden fuentes.
- **La regla «nada llega al mapa sin verificación»** (`CompuertaDePublicacion`, ADR-092) se mueve sin cambios de código.
- **Resilience4j:** las anotaciones o la configuración de reintentos y circuito de los colectores se conservan. Lo prueba `ResilienciaDeColectoresTest`.

## Terminado cuando

- La puerta está en verde.
- `IngestaLocalDeExtremoAExtremoTest` y `HeuristicaExtractorBoletinesRealesTest` pasan sin cambios en sus casos.
- El guion de simulación inyecta boletines y ve los cortes publicados.
- `verificar-datos.mjs` muestra las tres colecciones de ingesta sin cambios.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R5-ingesta.md.
Ejecuta R5 en refactor/reduccion-backend (antes: git merge main). Respeta la estructura destino; las reglas de extracción
van a reglas/ sin cambios de código; los controladores conservan nombre, métodos, rutas y @PreAuthorize.
No toques User-Agent, URLs, frecuencias, robots.txt ni la compuerta de publicación. Tests: mismos casos y aserciones.
Corre la puerta completa y muéstrame la salida. No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
