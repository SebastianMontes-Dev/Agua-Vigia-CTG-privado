# Panel del veedor

Todo `/api/veedor/**` exige `Authorization: Bearer <token>` (salvo el login), y **cada operación exige un
permiso**. Si falta la sesión, `401`; si la sesión no tiene el permiso, `403` con `type: acceso-denegado`.
Cómo se obtiene el token: [Cuentas y sesión](cuentas-y-sesion.md).

> **Pinta la interfaz con `permisos[]`** de la sesión: un botón que el servidor va a rechazar es mala
> experiencia. Pero el servidor **siempre** valida; ocultar un botón no es seguridad.

## Mapa de rutas y permisos

| Ruta | Permiso |
|---|---|
| `GET /api/veedor/cortes?sectorId=…` · `GET /api/veedor/cortes/{id}` · `GET /api/veedor/cortes/vencidos` · `GET /api/veedor/disputas` | `VER_PANEL` |
| `POST /api/veedor/cortes` · `PATCH /api/veedor/cortes/{id}/cierre` · `PATCH …/cortes/{id}/sectores/{sectorId}/{cierre,confirmacion}` · `PATCH …/cortes/{id}/anulacion` | `GESTIONAR_CORTES` |
| `GET /api/veedor/reportes/pendientes` | `VER_PANEL` |
| `PATCH /api/veedor/reportes/{id}/aprobar` · `…/descartar` | `MODERAR_REPORTES` |
| `GET /api/veedor/ingesta/propuestas` · `GET /api/veedor/ingesta/salud` · `GET /api/veedor/ingesta/fallidos` | `VER_PANEL` |
| `PATCH /api/veedor/ingesta/propuestas/{id}/aprobar` · `…/descartar` · `…/anulacion` | `REVISAR_INGESTA` |
| `GET /api/veedor/usuarios` · `POST …/usuarios/invitaciones` · `POST …/usuarios/{id}/invitacion/reenvio` · `PATCH …/usuarios/{id}/{aprobacion,rechazo,suspension,reactivacion,permisos}` | `GESTIONAR_USUARIOS` |
| `GET /api/veedor/auditoria` | `VER_AUDITORIA` |
| `POST /api/veedor/segundo-factor/{alta,confirmacion,baja}` | `CONFIGURAR_SEGUNDO_FACTOR` |
| `POST /api/veedor/cuenta/clave` (cambiar la propia clave; detalle en [Cuentas y sesión](cuentas-y-sesion.md)) | `VER_PANEL` |
| `POST /api/veedor/sesion/cierre` · `GET /api/veedor/yo` | solo estar autenticado |

Esquemas en [`referencia-de-rutas.md`](referencia-de-rutas.md).

## Cortes oficiales

Un **corte** es un anuncio oficial de que un conjunto de sectores se quedará sin agua entre dos instantes.

`POST /api/veedor/cortes` → `201`

```json
{ "sectoresAfectados": ["bocagrande", "manga"],
  "inicio": "2026-08-10T13:00:00Z", "finPrometido": "2026-08-10T21:00:00Z",
  "causa": "Mantenimiento de la línea de 24 pulgadas" }
```

Efecto sobre los sectores:
- Si `inicio` es **futuro**, los sectores pasan a `CORTE_PROGRAMADO`.
- Si `inicio` **ya ocurrió**, pasan a `SIN_SERVICIO`.
- Se anexa un evento `CORTE_ANUNCIADO` a la [bitácora](bitacora-estadisticas-cumplimiento.md).

`PATCH /api/veedor/cortes/{id}/cierre` `{ "horaReal": "2026-08-10T23:30:00Z" }` es el atajo que cierra **todos los barrios
que siguen pendientes** de una vez (los ya cerrados se respetan):
- Cada barrio queda con su cierre (`fuente: VEEDOR`, definitivo) y **cuando todos están cerrados el corte pasa a
  `RESTABLECIDO` y cuenta para el Índice de Cumplimiento**.
- El estado del barrio lo recalcula el resolutor: vuelve a `CON_SERVICIO` **salvo** que otro corte o boletín siga
  afirmando lo contrario sobre ese mismo barrio.
- Se anexa `CORTE_RESTABLECIDO` por barrio.

Los barrios de un corte se restablecen a horas distintas, así que lo normal es cerrarlos uno por uno:

- `PATCH …/cortes/{id}/sectores/{sectorId}/cierre` `{ "horaReal": … }` cierra un solo barrio.
- `PATCH …/cortes/{id}/sectores/{sectorId}/confirmacion` `{ "horaReal": … }` confirma —o corrige la hora de— un cierre
  **provisional** (el que pusieron los vecinos o los sensores al confirmar que volvió el agua). Un cierre ya confirmado
  responde `409`.
- `PATCH …/cortes/{id}/anulacion` `{ "motivo": … }` anula un corte publicado por error: queda como historia con su motivo,
  fuera del Índice y de las estadísticas, y la bitácora anexa la corrección. Queda constancia en la auditoría
  (`CORTE_ANULADO`).
- `GET …/cortes/vencidos` es la cola de trabajo: cortes con la promesa vencida sin cierre y cortes con un cierre
  provisional por confirmar, del más antiguo al más reciente.

`POST /api/veedor/cortes` acepta además `caducaEn` (opcional): el corte del veedor es el *override* y, a esa hora, deja
de afirmar nada aunque nadie lo haya cerrado.

Respuesta (`CorteRespuesta`): `id`, `sectoresAfectados[]`, `inicio`, `finPrometido`, `causa`, `origen` (quién lo creó: un
veedor o la ingesta), `estado` (`ANUNCIADO`, `CONFIRMADO`, `RESTABLECIDO`, `EXPIRADO` o `ANULADO`), `cierres[]` (uno por
barrio ya restablecido: `sectorId`, `hora`, `fuente`, `provisional`), `motivoAnulacion` y `caducaEn`. **Ya no hay `finReal`.**
Un corte que nadie cierra ni confirma pasa a `EXPIRADO` a las 72 h del fin prometido y el barrio vuelve a «sin datos».

| Código | Cuándo |
|---|---|
| `400` | Datos inválidos, `finPrometido` anterior a `inicio`, o algún sector no existe. |
| `404` | El corte (en `cierre` o `GET`) no existe. |
| `409` | Cerrar un corte o un barrio que ya estaba cerrado, confirmar un cierre ya confirmado, o anular lo ya anulado. |

`GET /api/veedor/cortes?sectorId=…` — el `sectorId` es **obligatorio**. Lista los cortes de ese sector.

> Los cortes creados por la **ingesta** (detectados en un boletín) nacen en estado anunciado y **también se cierran
> con `PATCH …/cierre`**: el servicio los busca por id sin mirar su origen (`GestionarCorteOficialServiceTest`). Al cerrarse
> cuentan para el Índice de Cumplimiento como cualquier otro.

## Moderación de reportes

`GET /api/veedor/reportes/pendientes` — la cola. **Los más antiguos primero**, paginada
(ver [Errores y límites §Paginación](errores-y-limites.md#paginación)). Cada elemento trae `id`, `sectorId`,
`tipo`, `coordenada` (opcional, aproximada a unos 110 m), `timestamp`, `estadoModeracion` y `verificacion`
(`CUENTA_VERIFICADA`, `UBICACION_VERIFICADA` o `NINGUNA`: cuánto respalda el servidor que quien reportó está en el barrio).
La red de origen no sale por la API. `senalRed` es verdadero si el reporte viene de una red que ya envió una ráfaga a ese barrio
(`aguavigia.moderacion.rafaga-minima`, 5 reportes, en `aguavigia.moderacion.rafaga-ventana-minutos`, 30 minutos): una señal para mirar primero, **no un bloqueo**
(una sala o una antena móvil comparten red). Solo viene en esta cola; en el resto de respuestas de moderación es nulo.

- `PATCH …/{id}/aprobar` y `…/descartar` → el reporte con su estado nuevo. `404` si no existe.
- **Un reporte descartado deja de contar** para el consenso y para las confirmaciones, pero **sigue
  contando para el cupo del dispositivo** (si no, moderar a un spammer le reiniciaría el cupo).
- Todo reporte nace `PENDIENTE` y **cuenta para el consenso desde el primer momento**; la moderación es una
  revisión posterior, no una puerta previa.

## Revisión de la ingesta

El sistema lee los boletines de **Acuacar** (solo su API REST de WordPress, `application.yml:175-177`) y la prensa por RSS
(**Google News**, **Zona Cero**, **Caracol Radio** y **W Radio**, `application.yml:180-188`), y propone cambios de estado por sector. Un veedor decide.

`GET /api/veedor/ingesta/propuestas` (paginada) devuelve las propuestas: qué sector, qué estado propone,
de qué fuente, el enlace al original, la **`citaTextual`** exacta que la respalda y una `confianza` entre 0 y
1 (sirve para ordenar la cola, **no para publicar sola**) y, desde F3, **`motivoDeRevision`**: por qué la propuesta espera
al veedor en vez de haberse publicado sola (confianza baja, ventana de más de 72 h, inicio a más de 7 días de la publicación,
más de 40 barrios, un nombre ambiguo, o que viene de prensa). Es nulo si salió sola. Muéstralo junto a la cita: es lo que
el veedor necesita para decidir.

Un boletín de Acuacar **fiable y con sentido se publica solo**; uno que no pasa las compuertas llega aquí. Un boletín cuya
ventana ya había terminado hace más de 72 h al ingerirse (el histórico) **no llega a la cola ni mueve el mapa**: se guarda
como corte `EXPIRADO` y deja un evento `CORTE_EXPIRADO` en la bitácora con la fecha del hecho (ver ADR-092).

- `PATCH …/propuestas/{id}/aprobar` aplica el cambio y anexa el evento a la bitácora.
- `PATCH …/propuestas/{id}/descartar` la rechaza.
- `PATCH …/propuestas/{id}/anulacion` `{ "motivo": … }` deshace una aprobación por error: la propuesta queda `ANULADA`, deja de
  afirmar nada del presente, la bitácora anexa la corrección (cita el boletín y el motivo) y el barrio se recalcula. Queda
  constancia en la auditoría (`PROPUESTA_ANULADA`). `409` si la propuesta no estaba aprobada; `400` si falta el motivo.
- `404` si la propuesta no existe. `409` si el sector de la propuesta ya no existe.
- Repetir la misma decisión sobre una propuesta es idempotente (`200`); contradecirla responde `409`: aprobar una
  descartada o descartar una aprobada (#96). Aprobar una propuesta de prensa **sin ventana horaria
  declarada** responde `200` pero **no cambia el estado del sector**: la interfaz debe deshabilitar los botones de
  una propuesta ya resuelta y no fiarse de que un `200` implique que el mapa cambió; vuelve a pedir `GET /api/sectores`.

**Fotos.** La cola de moderación (`GET /api/veedor/reportes/pendientes`) trae `fotoEstado` (`SIN_FOTO`, `EN_REVISION`, `PUBLICA`, `DESCARTADA`)
y `fotoUrl`, que apunta a `/api/veedor/fotos/{nombre}` (`VER_PANEL`): el panel ve la foto en cualquier estado; el público, solo con el
reporte aprobado. `PATCH /api/veedor/reportes/{id}/foto/descartar` (`MODERAR_REPORTES`) retira solo la foto, sin tocar el reporte ni su voto
(`404` si el reporte no existe, `409` si no tiene foto).

**Regla ética del proyecto (`ADR-006`): nada llega al mapa sin verificación.** Si la propuesta no puede citar
la frase exacta del boletín que la respalda, no debe aprobarse. La interfaz de revisión debe mostrar la
`citaTextual` y el enlace `urlOriginal` **junto** a los botones de aprobar y descartar.

Los boletines oficiales de **Acuacar** pueden aplicarse automáticamente por el barrido de ventanas
(cada 60 s); los de **prensa (RSS)** esperan siempre revisión de un veedor.

`GET /api/veedor/ingesta/salud` devuelve, por colector, cuándo fue su última ejecución exitosa, su último
fallo, su tasa de error y sus fallos consecutivos. Un colector con 3 fallos seguidos se considera caído.
**Ojo:** ese estado vive en la memoria de cada instancia del backend; con varias réplicas, cada una reporta lo
que ella misma ejecutó.

`GET /api/veedor/ingesta/fallidos` lista los documentos que siguen fallando al procesarse (fuente, `urlOriginal`, título,
motivo, primer y último intento, reintentos), más recientes primero y hasta 200. Un documento sale de la lista en
cuanto se procesa con éxito: es lo que está roto ahora, no un histórico (`IngestaFallidosController.java`).

## Gestión de cuentas (ADMIN)

| Ruta | Efecto |
|---|---|
| `GET /api/veedor/usuarios?estado=…&barrioId=…&pagina&tamano` | Lista paginada, filtrable por estado de cuenta y por barrio donde vive la persona (`barrioId`, slug de un sector; `AdminUsuariosController.java:77`). `400` si el estado no existe. **Incluye las cuentas de vecino sintéticas** (`sintetica: true`; ADR-094): son decenas de miles, las más nuevas van primero. |
| `POST /api/veedor/usuarios/invitaciones` `{ correo, nombre, rol }` | Crea una cuenta `INVITADA` y envía el correo. `409` si el correo ya tiene cuenta. |
| `POST …/{id}/invitacion/reenvio` | Reenvía la invitación a una cuenta `INVITADA` (invalida el enlace anterior y reinicia sus 7 días). `202`; `404` si no existe; `409` si la cuenta ya no está `INVITADA`. |
| `PATCH …/{id}/aprobacion` `{ rol, concedidos, revocados }` | Aprueba una cuenta de registro abierto, asignándole rol. |
| `PATCH …/{id}/rechazo` | Rechaza una solicitud (`PENDIENTE_APROBACION`). |
| `PATCH …/{id}/suspension` · `…/reactivacion` | Suspende o reactiva una cuenta `ACTIVA`. La suspensión **cierra sus sesiones**. |
| `PATCH …/{id}/permisos` `{ rol, concedidos, revocados }` | Cambia el rol y ajusta permisos sueltos. **Cierra sus sesiones.** |

Toda acción queda en la **auditoría** (`GET /api/veedor/auditoria`, paginada): acción, autor, sujeto, detalle,
IP y momento. La auditoría es de solo lectura y no se puede editar.

Un `409` en estas rutas es «esa acción no aplica al estado actual de la cuenta» (por ejemplo, suspender una
cuenta que no está activa). **Un ADMIN no puede aplicarse cambios de acceso a sí mismo ni dejar el sistema
sin ningún ADMIN activo.**

## Paginación

Las listas del panel (y la bitácora pública) devuelven **un arreglo JSON** y la paginación en cabeceras.
Ver [Errores y límites §Paginación](errores-y-limites.md#paginación).
