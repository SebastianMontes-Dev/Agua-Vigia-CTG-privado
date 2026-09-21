# Datos de referencia

Los valores fijos del dominio: los cuatro estados del servicio, los tipos de reporte, los roles y permisos, y
las reglas de negocio con sus números. Todo esto es **constantes estables**: el cliente las traduce a su
propio texto, pero no debe inventar valores nuevos.

## Estado del servicio

`EstadoServicio` — lo que devuelve `estado` en sectores y bitácora.

| Valor | Significa |
|---|---|
| `CON_SERVICIO` | Servicio normal, **verificado**. |
| `SIN_SERVICIO` | Corte confirmado. |
| `PRESION_BAJA` | Servicio degradado. |
| `CORTE_PROGRAMADO` | Corte anunciado, aún no iniciado. |
| `null` | **Sin dato verificado.** Mostrar como «sin datos». |

Los cuatro colores del sistema de diseño (verde, rojo, ámbar, azul-gris; ver [`DESIGN.md`](../../DESIGN.md))
están **reservados para este estado** y no se usan para nada más de la interfaz.

## Tipo de reporte

`TipoReporte` — el campo `tipo` de un reporte ciudadano.

| Valor | Efecto si gana el consenso |
|---|---|
| `SIN_AGUA` | El sector pasa a `SIN_SERVICIO`. |
| `PRESION_BAJA` | El sector pasa a `PRESION_BAJA`. |
| `SERVICIO_RESTABLECIDO` | El sector pasa a `CON_SERVICIO`. |

## Moderación de un reporte

`EstadoModeracion` — `PENDIENTE` (todo reporte nace así), `APROBADO`, `DESCARTADO`.

## Suscripción

`EstadoSuscripcion` — `PENDIENTE_CONFIRMACION`, `CONFIRMADA`, `CANCELADA`.

## Cuenta

`EstadoCuenta` — `PENDIENTE_VERIFICACION`, `PENDIENTE_APROBACION`, `INVITADA`, `ACTIVA`, `SUSPENDIDA`,
`RECHAZADA`. Transiciones y quién puede entrar: [Cuentas y sesión](cuentas-y-sesion.md).

## Tipo de evento de la bitácora

`CORTE_ANUNCIADO`, `CORTE_CONFIRMADO_POR_CIUDADANOS`, `CORTE_RESTABLECIDO`, `CORTE_DETECTADO_POR_INGESTA`.

## Roles y permisos

| Permiso | Qué permite |
|---|---|
| `VER_PANEL` | Ver cortes, cola de moderación, propuestas de ingesta y salud de colectores. |
| `MODERAR_REPORTES` | Aprobar o descartar reportes. |
| `GESTIONAR_CORTES` | Registrar y cerrar cortes oficiales. |
| `REVISAR_INGESTA` | Aprobar o descartar propuestas de la ingesta. |
| `GESTIONAR_USUARIOS` | Invitar, aprobar, rechazar, suspender y cambiar permisos de cuentas. |
| `VER_AUDITORIA` | Leer la auditoría de cuentas. |
| `CONFIGURAR_SEGUNDO_FACTOR` | Dar de alta, confirmar y desactivar el propio TOTP. Se concede a todos y **no se puede revocar**. |

| Rol | Permisos por defecto |
|---|---|
| `OBSERVADOR` | `VER_PANEL`, `CONFIGURAR_SEGUNDO_FACTOR` |
| `VEEDOR` | Los del observador + `MODERAR_REPORTES`, `GESTIONAR_CORTES`, `REVISAR_INGESTA` |
| `ADMIN` | **Todos.** Segundo factor obligatorio. |

**Alcance de sesión:** `COMPLETO` (normal) o `ALTA_SEGUNDO_FACTOR` (un ADMIN que aún no configuró su TOTP:
el token solo sirve para configurarlo).

## Geografía

- **211 sectores** (barrios de Cartagena). El GeoJSON de origen
  (`data/geoespacial/barrios-cartagena.geojson`) trae 213 filas; el sembrador **descarta 2 filas literalmente
  repetidas** (mismo nombre y misma geometría).
- El `id` de un sector es un *slug* del nombre: mayúsculas a minúsculas, sin tildes y con todo lo que no sea
  letra o número convertido en guion. Ejemplo: `ALAMEDA LA VICTORIA` → `alameda-la-victoria`. **No lo
  calcules en el cliente**: sale del listado y de la geometría.
- 27 de los 211 no tienen población censal (184 sí); su población es `null` internamente (no 0).
- Geometría: `Polygon`, salvo **`zona-industrial` (`MultiPolygon`)**, el único.
- Una **coordenada** válida es latitud entre −90 y 90 y longitud entre −180 y 180. Pero para **inferir un
  sector** (`POST /api/reportes` sin `sectorId`) debe caer **dentro de algún barrio de Cartagena**; si no,
  `400`.

## Reglas de negocio con sus números

| Regla | Valor |
|---|---|
| Cupo de reportes por dispositivo y sector | **3** cada **30 min** (RF006) |
| Cupo de un sensor IoT | **30** cada 30 min |
| Ventana del consenso | **30 min** |
| Umbral del consenso | `max(3, ceil(población × 0,001))` vecinos distintos (configurable) |
| Empate entre tipos de reporte | No cambia el estado |
| Índice de Cumplimiento | `prometido × 100 / real`, tope 100; solo cortes **cerrados** |
| Longitud de la huella | 32 a 128 caracteres |
| Foto | JPEG, PNG o WebP · máx. **10 MB** · lado máx. **1600 px** · sin EXIF |
| Vigencia de sesión del panel | **8 h**, sin renovación |
| Vigencia de enlaces | verificar cuenta 48 h · invitación 7 d · restablecer clave 30 min · confirmar suscripción 48 h |
| Clave de una cuenta | 12 a 128 caracteres |
| Bloqueo de cuenta | 5 fallos en 15 min → bloqueada 15 min |
| Retención de fotos | 365 días en producción (se borra el binario, no el reporte) |

## De dónde salen los datos

| Fuente | Cómo entra |
|---|---|
| Reportes ciudadanos | `POST /api/reportes` |
| Cortes oficiales | Registrados por un veedor (`POST /api/veedor/cortes`) |
| **Acuacar** (API REST de WordPress + RSS) | Colector automático cada 10 min → propuestas que revisa un veedor |
| **Google News RSS**, **Zona Cero RSS** | Ídem |
| Sensores de presión | `POST /api/iot/presion` (autenticado por clave) |

**No se scrapea Facebook, Instagram ni X, y se respeta siempre `robots.txt`** (`ADR-005`, `ADR-006`). El
colector se identifica siempre con un `User-Agent` con el nombre del proyecto y un correo de contacto.

## Lo que la API no ofrece

Lo que el frontend **no** puede pedir hoy, para no buscarlo:

- **Listar reportes ciudadanos públicamente.** Solo existe la cola de moderación (con sesión).
- **La población de un sector** por la API pública.
- **El histórico de cortes de un sector** sin sesión (`GET /api/veedor/cortes` exige `VER_PANEL`).
- **Cerrar un corte detectado por la ingesta.**
- **Reenviar** el correo de verificación o de invitación (invitar con el correo caído deja la cuenta creada
  y sin forma de reenviar el enlace).
- **Cambiar la propia clave con la sesión iniciada** (solo por el flujo de restablecer con correo).
- **Refrescar el token del panel.**
- **Avisos por WhatsApp o Telegram** (RF041): no está implementado; el adaptador de *push* solo escribe en el
  log. Requiere credenciales de terceros.
