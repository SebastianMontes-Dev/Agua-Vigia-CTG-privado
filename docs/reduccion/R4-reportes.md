# R4 · Reportes

**Objetivo:** mover a `reportes/` todo lo que entra por el vecino:
- reportes y confirmaciones
- identidad del reportante (token de dispositivo, vecino)
- límites
- fotos con token de subida
- moderación, disputas
- «¿ya volvió el agua?» con enlace firmado
- la limpieza y purga de fotos

Riesgo: **alto**. Es la funcionalidad más grande (86 archivos), con criptografía HMAC, cupos en Redis, archivos en disco y
muchas actualizaciones condicionales. Esfuerzo: 3 sesiones. Se puede partir en R4a (reportes y moderación) y R4b (fotos y
dispositivos), cada una con su puerta.

## Estructura destino

```
reportes/
├── ReporteCiudadano.java          @Document("reportes") + ReporteId, TipoReporte, Reportante, EstadoModeracion, ReportesPendientes
├── ReporteAlmacen.java            ReporteCiudadanoRepository + adaptador + repo Spring (implementa temporalmente el puerto)
├── ContadorDeReportes.java        ContadorReportesPort + RedisContadorReportesAdapter (INCR+EXPIRE literal; falla abierto)
├── ReporteService.java            Registrar, confirmar, identificar al reportante, repoblar el contador
├── ModeracionService.java         Moderar, pendientes, disputas, descartar foto
├── RestablecimientoService.java   ConfirmarRestablecimientoPorEnlace + firma de enlaces (HMAC)
├── ReporteController · ModeracionReporteController · DisputaController · RestablecimientoController
├── ReporteDtos.java               ReporteRespuesta, SolicitudReporte, ReporteModeracionRespuesta
├── reglas/                        LimitesDeReporte, FirmaDeImagen, RedEnRafaga, EvidenciaVencida, EnlaceDeRestablecimiento (sin cambios)
├── fotos/
│   ├── SubidaDeFoto.java          @Document("subidas_foto") + EstadoDeFoto, FotoGuardada, FotoLeida
│   ├── FotoService.java           Emitir token de subida, agregar evidencia, obtener foto
│   ├── AlmacenDeFotos.java        AlmacenamientoPort + AlmacenamientoLocalAdapter + CompresorDeImagenes
│   ├── TokenDeSubida.java         TokenDeSubidaPort + TokenDeSubidaAdapter
│   ├── FotoController.java
│   └── LimpiezaFotosHuerfanasJob · PurgaEvidenciaAntiguaJob   (mismos cron y EjecucionUnica)
└── dispositivos/
    ├── Dispositivo.java           @Document("dispositivos") + DispositivoId, HuellaDispositivo
    ├── DispositivoAlmacen.java    registrarVisto con $max, sin crear el documento (literal)
    ├── DispositivoService.java    Emitir token de dispositivo
    ├── FirmasHmac.java            HmacFirmaDeDispositivos + HmacHashDeRed (clases concretas; algoritmos y claves idénticos)
    ├── DispositivoController.java
    └── TokenDeDispositivoRespuesta (en DispositivoDtos o junto al controlador)
```

**Puertos y servicios que se absorben:**
- **De entrada (13):** `AgregarEvidencia`, `ConfirmarReporte`, `ConfirmarRestablecimientoPorEnlace`, `DescartarFoto`, `EmitirTokenDeDispositivo`, `EmitirTokenDeSubida`, `IdentificarReportante`, `ListarDisputas`, `ListarReportesPendientes`, `ModerarReporte`, `ObtenerFoto`, `RegistrarReporte`, `RepoblarContadorDeReportes`.
- **De salida (9):** `AlmacenamientoPort`, `ContadorReportesPort`, `DispositivoRepository`, `FirmaDeDispositivosPort`, `FirmaDeEnlacesPort`, `HashDeRedPort`, `ReporteCiudadanoRepository`, `SubidaDeFotoRepository`, `TokenDeSubidaPort`.

`RecalculoDeEstadoService` (R2) pasa a leer los votos con `ReporteAlmacen`. `ReporteService` llama a `ConsensoService` como clase.

## Lo delicado

- **Actualizaciones condicionales de `ReporteCiudadanoMongoAdapter`**, que se copian literal: `asignarFotoSiNoTiene`, `agregarConfirmacionSiVigente` (`$addToSet`), `cambiarEstadoDeModeracion`, `marcarFotoDescartada` y `quitarFotosDe`.
- **HMAC:** el algoritmo, el formato del token y de dónde sale la clave (`SecretosDelSistema`, R1) no cambian. Si cambiara algo, los tokens emitidos antes del cambio dejarían de validar. Los tests de las firmas se conservan con sus vectores.
- **Fotos:**
  - la ruta en disco y el volumen `fotos-data` no cambian
  - se sigue rechazando WebP (415 `formato-no-permitido`)
  - se sigue limpiando el EXIF
  - el tope de tamaño (413) no cambia
  - una foto solo se sirve si el reporte está aprobado
- **Las cabeceras `X-Dispositivo` y `X-Subida`** se leen igual. La regla de 3 reportes por dispositivo y 5 por vecino está en `LimitesDeReporte` y no se toca.
- **`IdentificarReportante` necesita la sesión del vecino**, que hoy está en `cuentas`, todavía vieja. Se usa por su clase o puerto actual. La flecha `reportes → cuentas` está permitida en el mapa del README.

## Terminado cuando

- La puerta está en verde.
- `verificar-flujos.mjs` recorre: reporte con dispositivo, confirmación, foto con token, moderación (aprobar, descartar, descartar foto), disputas, restablecimiento con enlace, y foto pública frente a foto del panel.
- El guion de simulación pasa: cientos de reportes y consenso.
- `verificar-datos.mjs` muestra `reportes`, `subidas_foto` y `dispositivos` sin cambios.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R4-reportes.md.
Ejecuta R4 en refactor/reduccion-backend (antes: git merge main). Si es grande, hazlo en dos tandas (R4a reportes y
moderación; R4b fotos y dispositivos), cada una con la puerta en verde. Respeta la estructura destino, copia literal las
actualizaciones condicionales y los $max/INCR, no cambies algoritmos ni formato de HMAC, ni rutas de fotos, ni límites.
Tests: mismos casos y aserciones. Corre la puerta completa y muéstrame la salida.
No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
