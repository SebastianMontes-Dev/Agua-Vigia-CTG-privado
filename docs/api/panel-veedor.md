# Panel del veedor

Todo `/api/veedor/**` exige `Authorization: Bearer <token>` (salvo el login), y **cada operación exige un
permiso**. Si falta la sesión, `401`; si la sesión no tiene el permiso, `403` con `type: acceso-denegado`.
Cómo se obtiene el token: [Cuentas y sesión](cuentas-y-sesion.md).

> **Pinta la interfaz con `permisos[]`** de la sesión: un botón que el servidor va a rechazar es mala
> experiencia. Pero el servidor **siempre** valida; ocultar un botón no es seguridad.

## Mapa de rutas y permisos

| Ruta | Permiso |
|---|---|
| `GET /api/veedor/cortes?sectorId=…` · `GET /api/veedor/cortes/{id}` | `VER_PANEL` |
| `POST /api/veedor/cortes` · `PATCH /api/veedor/cortes/{id}/cierre` | `GESTIONAR_CORTES` |
| `GET /api/veedor/reportes/pendientes` | `VER_PANEL` |
| `PATCH /api/veedor/reportes/{id}/aprobar` · `…/descartar` | `MODERAR_REPORTES` |
| `GET /api/veedor/ingesta/propuestas` · `GET /api/veedor/ingesta/salud` | `VER_PANEL` |
| `PATCH /api/veedor/ingesta/propuestas/{id}/aprobar` · `…/descartar` | `REVISAR_INGESTA` |
| `GET /api/veedor/usuarios` · `POST …/usuarios/invitaciones` · `POST …/usuarios/{id}/invitacion/reenvio` · `PATCH …/usuarios/{id}/{aprobacion,rechazo,suspension,reactivacion,permisos}` | `GESTIONAR_USUARIOS` |
| `GET /api/veedor/auditoria` | `VER_AUDITORIA` |
| `POST /api/veedor/segundo-factor/{alta,confirmacion,baja}` | `CONFIGURAR_SEGUNDO_FACTOR` |
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

`PATCH /api/veedor/cortes/{id}/cierre` `{ "horaReal": "2026-08-10T23:30:00Z" }` cierra el corte:
- Fija `finReal`, y **desde ese momento el corte cuenta para el Índice de Cumplimiento**.
- Los sectores vuelven a `CON_SERVICIO`, **salvo** los que sigan en otro corte abierto.
- Se anexa `CORTE_RESTABLECIDO`.

Respuesta (`CorteRespuesta`): `id`, `sectoresAfectados[]`, `inicio`, `finPrometido`, `finReal` (nulo si está
abierto), `causa`, `origen` (quién lo creó: un veedor o la ingesta) y `estado`.

| Código | Cuándo |
|---|---|
| `400` | Datos inválidos, `finPrometido` anterior a `inicio`, o algún sector no existe. |
| `404` | El corte (en `cierre` o `GET`) no existe. |
| `409` | Cerrar un corte que ya estaba cerrado. |

`GET /api/veedor/cortes?sectorId=…` — el `sectorId` es **obligatorio**. Lista los cortes de ese sector.

> Los cortes creados por la **ingesta** (detectados en un boletín) nacen en estado anunciado y hoy **no
> hay ruta para cerrarlos**; por eso no entran al Índice de Cumplimiento. Es una limitación conocida.

## Moderación de reportes

`GET /api/veedor/reportes/pendientes` — la cola. **Los más antiguos primero**, paginada
(ver [Errores y límites §Paginación](errores-y-limites.md#paginación)). Cada elemento trae `id`, `sectorId`,
`tipo`, `coordenada` (opcional), `timestamp` y `estadoModeracion`.

- `PATCH …/{id}/aprobar` y `…/descartar` → el reporte con su estado nuevo. `404` si no existe.
- **Un reporte descartado deja de contar** para el consenso y para las confirmaciones, pero **sigue
  contando para el cupo del dispositivo** (si no, moderar a un spammer le reiniciaría el cupo).
- Todo reporte nace `PENDIENTE` y **cuenta para el consenso desde el primer momento**; la moderación es una
  revisión posterior, no una puerta previa.

## Revisión de la ingesta

El sistema lee los boletines de **Acuacar** (API REST de WordPress y RSS), **Google News** y **Zona Cero**, y
propone cambios de estado por sector. Un veedor decide.

`GET /api/veedor/ingesta/propuestas` (paginada) devuelve las propuestas: qué sector, qué estado propone,
de qué fuente, el enlace al original, la **`citaTextual`** exacta que la respalda y una `confianza` entre 0 y
1 (sirve para ordenar la cola, **no para publicar sola**).

- `PATCH …/propuestas/{id}/aprobar` aplica el cambio y anexa el evento a la bitácora.
- `PATCH …/propuestas/{id}/descartar` la rechaza.
- `404` si la propuesta no existe. `409` si el sector de la propuesta ya no existe.
- **Limitación conocida:** el servidor no impide aprobar una propuesta ya descartada ni resolverla dos veces,
  y aprobar una propuesta de prensa **sin ventana horaria declarada** responde `200` pero **no cambia el
  estado del sector**. La interfaz debe deshabilitar los botones de una propuesta ya resuelta y no fiarse de
  que un `200` implique que el mapa cambió: vuelve a pedir `GET /api/sectores`.

**Regla ética del proyecto (`ADR-006`): nada llega al mapa sin verificación.** Si la propuesta no puede citar
la frase exacta del boletín que la respalda, no debe aprobarse. La interfaz de revisión debe mostrar la
`citaTextual` y el enlace `urlOriginal` **junto** a los botones de aprobar y descartar.

Los boletines oficiales de **Acuacar** pueden aplicarse automáticamente por el barrido de ventanas
(cada 60 s); los de **prensa (RSS)** esperan siempre revisión de un veedor.

`GET /api/veedor/ingesta/salud` devuelve, por colector, cuándo fue su última ejecución exitosa, su último
fallo, su tasa de error y sus fallos consecutivos. Un colector con 3 fallos seguidos se considera caído.
**Ojo:** ese estado vive en la memoria de cada instancia del backend; con varias réplicas, cada una reporta lo
que ella misma ejecutó.

## Gestión de cuentas (ADMIN)

| Ruta | Efecto |
|---|---|
| `GET /api/veedor/usuarios?estado=…&pagina&tamano` | Lista paginada, filtrable por estado de cuenta. `400` si el estado no existe. |
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
