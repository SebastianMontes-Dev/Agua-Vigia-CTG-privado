# Referencia de rutas y esquemas

> **Generado — no editar a mano.** Lo produce `scripts/generar-referencia-api.mjs` a partir de
> `backend/openapi.yaml` (rutas, cuerpos, respuestas, esquemas) y de los `@PreAuthorize` de los
> controladores (permisos). Para regenerarlo, ver la cabecera del script.
>
> Las **guías** de esta carpeta explican el porqué y los flujos; esta página es el catálogo exacto.

**65 operaciones** en 59 rutas, más las páginas HTML de cortesía y el SSE.

Leyenda de **Acceso**: *Público* no exige token · *Sesión + `PERMISO`* exige `Authorization: Bearer <token>` de una
cuenta que tenga ese permiso · *Sesión (cualquier cuenta)* exige token pero ningún permiso concreto.
Todo error sale en RFC 7807: ver [Errores y límites](errores-y-limites.md).

## Índice

- [Bitácora](#bit-cora) (2)
- [controlador-de-prueba-rate-limit](#controlador-de-prueba-rate-limit) (2)
- [Cuentas](#cuentas) (13)
- [Cumplimiento](#cumplimiento) (5)
- [Estadisticas](#estadisticas) (2)
- [IoT](#iot) (1)
- [Open311](#open311) (1)
- [Reportes](#reportes) (3)
- [Sectores](#sectores) (5)
- [Suscripciones](#suscripciones) (5)
- [Veedor](#veedor) (3)
- [Veedor - Cortes](#veedor-cortes) (4)
- [Veedor - Cuenta propia](#veedor-cuenta-propia) (1)
- [Veedor - Cuentas](#veedor-cuentas) (8)
- [Veedor - Ingesta](#veedor-ingesta) (4)
- [Veedor - Moderación](#veedor-moderaci-n) (3)
- [Veedor - Segundo factor](#veedor-segundo-factor) (3)
- [Esquemas](#esquemas)

## Bitácora

Bitácora pública de eventos, de solo anexado (RF026-RF028)

### `GET /api/bitacora`

**Listar los eventos de la bitácora, más recientes primero**

Paginado: la bitácora es de solo anexado (RF028), así que crece sin cota. El total, la página y el enlace a la siguiente viajan en las cabeceras `X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size` y `Link` — el cuerpo sigue siendo un arreglo JSON, así que un cliente que las ignore no se rompe. Por defecto 50 eventos; el máximo por página es 200.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Listado generado → lista de [EventoBitacoraRespuesta](#esquema-eventobitacorarespuesta) |

### `GET /api/bitacora/{id}/sustento`

**Los reportes que sustentan un evento de consenso (RF011)**

Ids de los reportes ciudadanos que sostuvieron el cambio de estado, para contrastarlo con la evidencia. Van aparte del listado porque en una avería grande pueden ser miles. Paginado con las mismas cabeceras que el listado; por defecto 50 ids por página, máximo 200. Vacío en los eventos que no son de consenso.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `id` (path, obligatorio)<br>`pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Ids de la página pedida (vacía si se pasa del final) → lista de string<br>`404` No existe el evento → lista de string |

## controlador-de-prueba-rate-limit

### `GET /protegida`

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → string |

### `GET /sin-proteger`

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → string |

## Cuentas

Páginas HTML a las que llevan los enlaces de los correos de cuenta

### `POST /api/cuentas/clave`

**Fijar la clave nueva con el token del enlace**

Cambia la clave y revoca todas las sesiones abiertas de esa cuenta.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudFijarClave](#esquema-solicitudfijarclave) (`application/json`) |
| **Respuestas** | `204` Clave cambiada<br>`400` Enlace invalido, vencido o ya usado |

### `GET /api/cuentas/enlaces/invitacion`

**Pantalla del enlace de invitación**

Muestra el formulario para elegir la clave. No consume el token.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → string |

### `POST /api/cuentas/enlaces/invitacion`

**Aceptar la invitación desde el formulario de la pantalla**

Mismo efecto que POST /api/cuentas/invitacion, pero recibe el formulario y responde HTML.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | object (`application/x-www-form-urlencoded`) |
| **Respuestas** | `200` OK → string |

### `GET /api/cuentas/enlaces/restablecer`

**Pantalla del enlace para restablecer la clave**

Muestra el formulario para elegir la clave nueva. No consume el token.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → string |

### `POST /api/cuentas/enlaces/restablecer`

**Restablecer la clave desde el formulario de la pantalla**

Mismo efecto que POST /api/cuentas/clave, pero recibe el formulario y responde HTML.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | object (`application/x-www-form-urlencoded`) |
| **Respuestas** | `200` OK → string |

### `GET /api/cuentas/enlaces/verificar`

**Pantalla del enlace «Confirmar mi correo»**

Muestra el botón de confirmación. No consume el token: eso ocurre al enviar el formulario.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → string |

### `POST /api/cuentas/enlaces/verificar`

**Confirmar el correo desde el formulario de la pantalla**

Mismo efecto que POST /api/cuentas/verificacion, pero recibe el formulario y responde HTML.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | string (`application/x-www-form-urlencoded`) |
| **Respuestas** | `200` OK → string |

### `POST /api/cuentas/invitacion`

**Aceptar una invitacion fijando la clave**

Deja la cuenta ACTIVA con el rol que eligio quien invito. No hace falta otra aprobacion.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudFijarClave](#esquema-solicitudfijarclave) (`application/json`) |
| **Respuestas** | `204` Cuenta activa; ya puedes iniciar sesion<br>`400` Enlace invalido o clave que no cumple la politica |

### `POST /api/cuentas/registro`

**Solicitar una cuenta del panel**

Crea la cuenta en PENDIENTE_VERIFICACION y envia el enlace de confirmacion. Registrarse no concede ningun permiso: hace falta verificar el correo y que un ADMIN apruebe. Responde 202 aunque el correo ya tenga cuenta, para no revelar que direcciones estan registradas.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudRegistro](#esquema-solicitudregistro) (`application/json`) |
| **Respuestas** | `202` Solicitud recibida; revisa tu correo<br>`400` Correo mal formado o clave que no cumple la politica |

### `POST /api/cuentas/restablecimiento`

**Pedir el enlace para restablecer la clave**

Responde 202 siempre, exista o no la cuenta. Ver el javadoc de esta clase.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudRestablecer](#esquema-solicitudrestablecer) (`application/json`) |
| **Respuestas** | `202` Accepted |

### `POST /api/cuentas/verificacion`

**Confirmar el correo con el token del enlace**

Pasa la cuenta a PENDIENTE_APROBACION. Sigue sin poder entrar hasta que un ADMIN la apruebe.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `204` Correo confirmado<br>`400` Enlace invalido, vencido o ya usado |

### `POST /api/cuentas/verificacion/reenvio`

**Reenviar el correo de verificación**

Para quien se registró y no recibió el enlace (o venció). Responde 202 **siempre**, exista o no la cuenta y ya esté verificada o no: no revela qué correos están registrados. Reenvía como mucho una vez cada 2 minutos por cuenta. El enlace anterior deja de servir.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudReenvioVerificacion](#esquema-solicitudreenvioverificacion) (`application/json`) |
| **Respuestas** | `202` Si había algo que reenviar, el correo va en camino<br>`400` Correo mal formado |

### `POST /api/veedor/usuarios/{id}/invitacion/reenvio`

**Reenviar la invitación a una cuenta que aún no la aceptó**

Requiere GESTIONAR_USUARIOS. El enlace anterior deja de servir y se reinicia su vigencia de 7 días.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `202` Invitación reenviada<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` No existe la cuenta<br>`409` La cuenta ya aceptó la invitación (no está en estado INVITADA) |

## Cumplimiento

Índice de Cumplimiento — prometido vs. real (RF020-RF022)

### `GET /api/cumplimiento`

**Índice global de la ciudad, sobre todos los cortes cerrados**

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` Índice calculado → [IndiceCumplimientoRespuesta](#esquema-indicecumplimientorespuesta)<br>`400` Todavía no hay cortes cerrados → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/cumplimiento/cortes/{corteId}`

**Índice de un corte cerrado**

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `corteId` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Índice calculado → [IndiceCumplimientoRespuesta](#esquema-indicecumplimientorespuesta)<br>`404` El corte no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El corte todavía no está cerrado → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/cumplimiento/sectores/{sectorId}`

**Índice agregado de un sector, sobre sus cortes cerrados**

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `sectorId` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Índice calculado → [IndiceCumplimientoRespuesta](#esquema-indicecumplimientorespuesta)<br>`400` El sector no tiene cortes cerrados todavía → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/cumplimiento/serie`

**Evolución del índice mes a mes (RF024)**

Un punto por mes con al menos un corte cerrado, en hora de Cartagena. Sin `sectorId`, la ciudad completa. `desde` y `hasta` son opcionales y acotan por la hora real de restablecimiento. Lista vacía si no hay cortes cerrados en el rango — una serie sin datos es una respuesta válida.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `sectorId` (query)<br>`desde` (query)<br>`hasta` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Serie generada → lista de [PuntoSerieRespuesta](#esquema-puntoserierespuesta) |

### `GET /api/cumplimiento/serie.csv`

**La misma serie en CSV (RF025)**

Separador `;` y BOM UTF-8, para que Excel en español la abra sin romper las tildes.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `sectorId` (query)<br>`desde` (query)<br>`hasta` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` CSV generado → string |

## Estadisticas

M7 — Estadísticas Públicas (RF023)

### `GET /api/estadisticas`

**Obtener estadísticas globales de la ciudad**

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → [EstadisticasRespuesta](#esquema-estadisticasrespuesta) |

### `GET /api/estadisticas/exportar.csv`

**Exportar las estadísticas en CSV (RF025)**

Separador `;` y BOM UTF-8, para que Excel en español lo abra sin romper las tildes.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` CSV generado → string |

## IoT

Telemetría de sensores de presión de la red (autenticada con X-IoT-Key)

### `POST /api/iot/presion`

**Registrar una lectura de presión de un sensor**

Una presión por debajo del umbral (15 psi por defecto) se registra como reporte de PRESION_BAJA con el cupo de sensor. Una lectura normal responde 200 sin registrar nada. Sin cuerpo de respuesta en el caso exitoso.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `X-IoT-Key` (header) |
| **Cuerpo** | [IotPresionRequest](#esquema-iotpresionrequest) (`application/json`) |
| **Respuestas** | `200` Lectura recibida (haya generado reporte o no)<br>`400` Falta el sensor, el sector no existe o la coordenada es inválida → [ProblemDetail](#esquema-problemdetail)<br>`401` X-IoT-Key ausente o incorrecta → [ProblemDetail](#esquema-problemdetail)<br>`503` El servidor no tiene configurada la clave de sensores → [ProblemDetail](#esquema-problemdetail) |

## Open311

API abierta bajo el estándar Open311 GeoReport v2 (RF039)

### `GET /api/v2/requests.json`

**Listar los sectores con el servicio afectado, en formato Open311**

Un `service_request` por sector que no está en CON_SERVICIO. Los sectores sin estado verificado no aparecen: publicar "sin novedad" sin haberlo comprobado es el falso positivo que ADR-014 evita.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` Listado generado → lista de [Open311Response](#esquema-open311response) |

## Reportes

Reportes ciudadanos de estado del servicio, sin registro

### `POST /api/reportes`

**Registrar un reporte ciudadano**

Sin registro ni cuenta (RF005). Limita automáticamente los reportes por dispositivo en la ventana vigente (RF006) — ver 429. Hace falta el `sectorId`, la `coordenada` o ambos (RF007): con solo la coordenada el servidor infiere el sector que la contiene y responde 400 si cae fuera de todo barrio de Cartagena. La coordenada se envía solo si el usuario autorizó compartir su ubicación.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudReporte](#esquema-solicitudreporte) (`application/json`) |
| **Respuestas** | `201` Reporte registrado → [ReporteRespuesta](#esquema-reporterespuesta)<br>`400` Sector inexistente, tipo inválido, huella fuera de 32-128 caracteres, coordenada fuera de Cartagena o sin sector ni coo… → [ProblemDetail](#esquema-problemdetail)<br>`429` El dispositivo superó el límite de reportes para este sector → [ProblemDetail](#esquema-problemdetail) |

### `POST /api/reportes/{id}/confirmar`

**Confirmar un reporte**

Permite a otro vecino confirmar un reporte ciudadano (M11).

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | [SolicitudConfirmar](#esquema-solicitudconfirmar) (`application/json`) |
| **Respuestas** | `200` Reporte confirmado → [ReporteRespuesta](#esquema-reporterespuesta)<br>`400` Error en la solicitud → [ReporteRespuesta](#esquema-reporterespuesta)<br>`404` Reporte no encontrado → [ReporteRespuesta](#esquema-reporterespuesta) |

### `POST /api/reportes/{id}/foto`

**Agregar evidencia a un reporte**

Permite subir una foto y asociarla a un reporte existente (M10).

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | object (`multipart/form-data`) |
| **Respuestas** | `200` Evidencia agregada → [ReporteRespuesta](#esquema-reporterespuesta)<br>`400` Error en la solicitud → [ReporteRespuesta](#esquema-reporterespuesta)<br>`404` Reporte no encontrado → [ReporteRespuesta](#esquema-reporterespuesta) |

## Sectores

Sectores de Cartagena y su estado del servicio

### `GET /api/sectores`

**Listar los sectores con su estado conocido**

Devuelve los sectores de Cartagena (211 barrios sembrados desde el GeoJSON oficial). `estado` viaja nulo mientras no haya dato verificado del sector — el cliente debe mostrarlo como "sin datos" y no suponer que hay servicio.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` Listado generado → [RespuestaSectores](#esquema-respuestasectores) |

### `GET /api/sectores/{id}`

**Consultar un sector por su identificador**

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Sector encontrado → [SectorRespuesta](#esquema-sectorrespuesta)<br>`404` No existe un sector con ese id → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/sectores/{sectorId}/cortes`

**Histórico de cortes de un sector, del más reciente al más antiguo**

Cortes oficiales que afectaron al sector, abiertos y cerrados. Paginado con las mismas cabeceras que la bitácora (`X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size`, `Link`); por defecto 50, máximo 200. Un sector sin cortes devuelve una lista vacía, no un 404.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `sectorId` (path, obligatorio)<br>`pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Cortes de la página pedida → lista de [CorteRespuesta](#esquema-corterespuesta)<br>`404` No existe el sector → lista de [CorteRespuesta](#esquema-corterespuesta) |

### `GET /api/sectores/geometria`

**Polígonos de todos los sectores (GeoJSON)**

FeatureCollection con un Feature por sector; `id` es el mismo que devuelve GET /api/sectores. Los polígonos solo cambian al volver a sembrar, por eso la respuesta se puede cachear un día.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` Geometrías → — |

### `GET /api/sectores/stream`

**Avisos en vivo de cambios de estado (SSE)**

Conexión abierta (`text/event-stream`) que AVISA de que algo cambió; no envía el estado. Cada evento `sectores` trae `{"actualizadoEn": "..."}` y el cliente pide entonces GET /api/sectores, que está cacheado. Un comentario `:latido` llega cada 25 s y `retry:` indica cuánto esperar antes de reconectar. El servidor agrupa los cambios en un aviso por segundo como máximo y cierra la conexión cada ~10-12 minutos por diseño. Por encima del tope de conexiones responde 429 con `Retry-After`.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `429` Tope de conexiones en vivo alcanzado; reintentar tras Retry-After → [ProblemDetail](#esquema-problemdetail) |

## Suscripciones

Alertas por correo cuando cambia el estado de un sector

### `POST /api/suscripciones`

**Suscribirse a los avisos de uno o más sectores**

Crea la suscripción en PENDIENTE_CONFIRMACION y envía un correo de doble opt-in (Ley 1581/2012, RF013). No empieza a recibir avisos hasta confirmarla.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudSuscripcion](#esquema-solicitudsuscripcion) (`application/json`) |
| **Respuestas** | `201` Suscripción creada, correo de confirmación en camino → [SuscripcionRespuesta](#esquema-suscripcionrespuesta)<br>`400` Correo inválido o algún sector no existe → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/suscripciones/cancelar`

**Pantalla del enlace de baja de todo correo (RF015)**

Página a la que lleva el enlace del correo. Solo muestra un botón: NO cancela nada, porque un antivirus o una vista previa de enlaces abre los GET sin que nadie los pida (`ADR-054`). La acción ocurre al enviar el formulario, que hace POST a la misma ruta.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Página con el botón → string |

### `POST /api/suscripciones/cancelar`

**Darse de baja en un clic (RF015)**

Acción del botón de la página de baja (o de un cliente de API). Sin pedir credenciales: el token que llega en cada correo es suficiente. Responde JSON o una página HTML de cortesía según el `Accept` de quien pide (mismo motivo que en {@code /confirmar}).

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio)<br>`Accept` (header) |
| **Cuerpo** | — |
| **Respuestas** | `200` Suscripción cancelada → object<br>`400` Token inválido o inexistente → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/suscripciones/confirmar`

**Pantalla del enlace «Confirmar» del correo**

Página a la que lleva el enlace del correo. Solo muestra un botón: NO confirma nada, porque un antivirus o una vista previa de enlaces abre los GET sin que nadie los pida (`ADR-054`). La acción ocurre al enviar el formulario, que hace POST a la misma ruta.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Página con el botón → string |

### `POST /api/suscripciones/confirmar`

**Confirmar la suscripción (doble opt-in)**

Acción del botón de la página de confirmación (o de un cliente de API). El token es de un solo enlace, no de un solo uso: confirmarla dos veces no falla (RF013). Responde JSON o una página HTML de cortesía según el `Accept` de quien pide: el formulario de la página responde HTML y un cliente de API responde JSON. `token` va como parámetro de consulta o del formulario.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `token` (query, obligatorio)<br>`Accept` (header) |
| **Cuerpo** | — |
| **Respuestas** | `200` Suscripción confirmada → object<br>`400` Token inválido, inexistente o de una suscripción ya cancelada → [ProblemDetail](#esquema-problemdetail) |

## Veedor

Autenticacion del panel del veedor

### `POST /api/veedor/sesion`

**Iniciar sesion en el panel del veedor**

Devuelve un token JWT valido por 8 horas (RNF011) junto con el rol y los permisos ya resueltos. Si la cuenta tiene segundo factor y no se envio `codigoTotp`, la respuesta es 401 con type `segundo-factor-requerido`: hay que reintentar con el codigo, no es un error de credencial.

| | |
|---|---|
| **Acceso** | Público (login) |
| **Parámetros** | — |
| **Cuerpo** | [CredencialVeedor](#esquema-credencialveedor) (`application/json`) |
| **Respuestas** | `200` Credencial correcta, token emitido → [SesionVeedor](#esquema-sesionveedor)<br>`401` Credencial incorrecta, o falta el segundo factor → [SesionVeedor](#esquema-sesionveedor)<br>`403` La cuenta existe pero no esta habilitada para entrar → [SesionVeedor](#esquema-sesionveedor)<br>`423` Cuenta bloqueada por intentos fallidos → [SesionVeedor](#esquema-sesionveedor)<br>`429` Demasiados intentos desde esta IP → [SesionVeedor](#esquema-sesionveedor) |

### `POST /api/veedor/sesion/cierre`

**Cerrar sesion**

Revoca en el servidor todas las sesiones vivas de la cuenta, no solo la de este navegador. Un token copiado antes del cierre deja de servir en el acto.

| | |
|---|---|
| **Acceso** | Sesión (cualquier cuenta) |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `204` No Content<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/yo`

**Datos de la cuenta que tiene la sesion**

Lo usa el frontend al recargar para saber que puede pintar sin volver a pedir la clave. Devuelve el estado vigente en la base de datos, no lo que dice el token.

| | |
|---|---|
| **Acceso** | Sesión (cualquier cuenta) |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

## Veedor - Cortes

Registro y cierre de cortes oficiales (RF016-RF017)

### `GET /api/veedor/cortes`

**Listar los cortes que afectan a un sector**

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | `sectorId` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → lista de [CorteRespuesta](#esquema-corterespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `POST /api/veedor/cortes`

**Registrar un corte oficial**

Sectores afectados, inicio, fin prometido y causa (RF016). Origen VEEDOR.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_CORTES` |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudCorte](#esquema-solicitudcorte) (`application/json`) |
| **Respuestas** | `201` Corte registrado → [CorteRespuesta](#esquema-corterespuesta)<br>`400` Datos inválidos o algún sector no existe → [ProblemDetail](#esquema-problemdetail)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/cortes/{id}`

**Consultar un corte por su identificador**

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Corte encontrado → [CorteRespuesta](#esquema-corterespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` No existe un corte con ese id → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/cortes/{id}/cierre`

**Cerrar un corte con la hora real de restablecimiento (RF017)**

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_CORTES` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | [SolicitudCierreCorte](#esquema-solicitudcierrecorte) (`application/json`) |
| **Respuestas** | `200` Corte cerrado → [CorteRespuesta](#esquema-corterespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El corte no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El corte ya estaba cerrado → [ProblemDetail](#esquema-problemdetail) |

## Veedor - Cuenta propia

Cambios que una persona hace sobre su propia cuenta

### `POST /api/veedor/cuenta/clave`

**Cambiar la propia clave**

Exige la clave actual y comparte el contador de intentos fallidos con el inicio de sesión (5 fallos en 15 minutos bloquean la cuenta 15 minutos). Al cambiarla se cierran **todas** las sesiones, la actual incluida: el cliente debe volver a pedir `POST /api/veedor/sesion` con la clave nueva. Se avisa por correo del cambio.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudCambioClave](#esquema-solicitudcambioclave) (`application/json`) |
| **Respuestas** | `204` Clave cambiada; todas las sesiones cerradas<br>`400` La clave actual no es correcta, la nueva no cumple la política (12 a 128 caracteres) o es igual a la actual<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`423` Cuenta bloqueada por intentos fallidos |

## Veedor - Cuentas

Gestion de cuentas y permisos del panel (solo ADMIN)

### `GET /api/veedor/auditoria`

**Bitacora de auditoria de cuentas, mas recientes primero**

Solo anexado: no hay forma de editar ni borrar un asiento desde la API.

| | |
|---|---|
| **Acceso** | Sesión + `VER_AUDITORIA` |
| **Parámetros** | `pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → lista de [EventoAuditoriaRespuesta](#esquema-eventoauditoriarespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/usuarios`

**Listar cuentas, mas recientes primero**

Paginado, con el total y el enlace a la siguiente pagina en `X-Total-Count` y `Link`. `estado` filtra por PENDIENTE_APROBACION para ver solo la cola de altas.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `estado` (query)<br>`pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → lista de [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/usuarios/{id}/aprobacion`

**Aprobar una cuenta que ya verifico su correo, asignandole permisos**

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | [SolicitudPermisos](#esquema-solicitudpermisos) (`application/json`) |
| **Respuestas** | `200` Cuenta activa → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` La cuenta no esta esperando aprobacion, o el ADMIN se administra a si mismo → [UsuarioRespuesta](#esquema-usuariorespuesta) |

### `PATCH /api/veedor/usuarios/{id}/permisos`

**Cambiar rol y ajustes de permisos de una cuenta**

Revoca las sesiones vivas de esa persona, tanto si los permisos se amplian como si se recortan: el token los lleva dentro y una sesion abierta seguiria usando los anteriores.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | [SolicitudPermisos](#esquema-solicitudpermisos) (`application/json`) |
| **Respuestas** | `200` Permisos actualizados → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` Dejaria al sistema sin ningun ADMIN activo → [UsuarioRespuesta](#esquema-usuariorespuesta) |

### `PATCH /api/veedor/usuarios/{id}/reactivacion`

**Devolver el acceso a una cuenta suspendida**

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/usuarios/{id}/rechazo`

**Denegar una solicitud de acceso**

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/usuarios/{id}/suspension`

**Suspender una cuenta activa**

Revoca sus sesiones al instante: no espera a que caduque su token.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Cuenta suspendida → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` Es el unico ADMIN activo, o el ADMIN se administra a si mismo → [UsuarioRespuesta](#esquema-usuariorespuesta) |

### `POST /api/veedor/usuarios/invitaciones`

**Invitar a una persona con un rol ya decidido**

Crea la cuenta en INVITADA y le envia un enlace para que fije su clave. Al aceptarlo queda ACTIVA sin necesitar otra aprobacion.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudInvitacion](#esquema-solicitudinvitacion) (`application/json`) |
| **Respuestas** | `201` Invitacion enviada → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` Ya existe una cuenta con ese correo → [UsuarioRespuesta](#esquema-usuariorespuesta) |

## Veedor - Ingesta

Salud de los colectores del pipeline de ingesta (RNF007)

### `GET /api/veedor/ingesta/propuestas`

**Listar las propuestas pendientes de revisión, más recientes primero**

Paginado, con el total y el enlace a la siguiente página en las cabeceras `X-Total-Count` y `Link`. Por defecto 50; el máximo por página es 200.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | `pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Listado generado → lista de [PropuestaIngestaRespuesta](#esquema-propuestaingestarespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/ingesta/propuestas/{id}/aprobar`

**Aprobar una propuesta**

Aplica el estado propuesto al sector y anexa el evento a la bitácora pública (RF026). Es el único camino por el que la ingesta llega al mapa.

| | |
|---|---|
| **Acceso** | Sesión + `REVISAR_INGESTA` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Propuesta aprobada y estado aplicado → [PropuestaIngestaRespuesta](#esquema-propuestaingestarespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` La propuesta no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El sector de la propuesta ya no existe → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/ingesta/propuestas/{id}/descartar`

**Descartar una propuesta**

No toca el sector. La propuesta se archiva como descartada, no se borra.

| | |
|---|---|
| **Acceso** | Sesión + `REVISAR_INGESTA` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Propuesta descartada → [PropuestaIngestaRespuesta](#esquema-propuestaingestarespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` La propuesta no existe → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/ingesta/salud`

**Salud de cada colector: última ejecución exitosa, ítems y tasa de error**

Lista vacía mientras el pipeline no haya corrido un ciclo. La telemetría vive en memoria del proceso, así que un reinicio la reinicia.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` Estado generado → lista de [SaludColectorRespuesta](#esquema-saludcolectorrespuesta)<br>`401` Falta el token del veedor → lista de [SaludColectorRespuesta](#esquema-saludcolectorrespuesta)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

## Veedor - Moderación

Moderar reportes ciudadanos pendientes (RF018)

### `PATCH /api/veedor/reportes/{id}/aprobar`

**Aprobar un reporte**

| | |
|---|---|
| **Acceso** | Sesión + `MODERAR_REPORTES` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Reporte aprobado → [ReporteModeracionRespuesta](#esquema-reportemoderacionrespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El reporte no existe → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/reportes/{id}/descartar`

**Descartar un reporte**

| | |
|---|---|
| **Acceso** | Sesión + `MODERAR_REPORTES` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Reporte descartado → [ReporteModeracionRespuesta](#esquema-reportemoderacionrespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El reporte no existe → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/reportes/pendientes`

**Listar los reportes pendientes de moderación, más antiguos primero**

Paginado, con el total y el enlace a la siguiente página en las cabeceras `X-Total-Count` y `Link`. Por defecto 50; el máximo por página es 200.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | `pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → lista de [ReporteModeracionRespuesta](#esquema-reportemoderacionrespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

## Veedor - Segundo factor

Alta y baja del TOTP de la propia cuenta

### `POST /api/veedor/segundo-factor/alta`

**Empezar el alta: genera el secreto y devuelve el QR**

El secreto queda guardado sin confirmar y todavia no se exige al entrar. Solo empieza a hacerlo tras confirmar un codigo valido. El secreto se muestra una sola vez: no hay endpoint para volver a leerlo.

| | |
|---|---|
| **Acceso** | Sesión + `CONFIGURAR_SEGUNDO_FACTOR` |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudCodigo](#esquema-solicitudcodigo) (`application/json`) |
| **Respuestas** | `200` Secreto generado, pendiente de confirmar → [AltaSegundoFactorRespuesta](#esquema-altasegundofactorrespuesta)<br>`401` El codigo actual no coincide → [AltaSegundoFactorRespuesta](#esquema-altasegundofactorrespuesta)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` Ya tiene segundo factor y no envio el codigo actual → [AltaSegundoFactorRespuesta](#esquema-altasegundofactorrespuesta) |

### `POST /api/veedor/segundo-factor/baja`

**Desactivar el segundo factor de la propia cuenta**

Exige un codigo valido: si bastara con la sesion, un token robado podria quitar de en medio justamente la defensa que impide usarlo. Un ADMIN no puede desactivarlo, su rol lo exige.

| | |
|---|---|
| **Acceso** | Sesión + `CONFIGURAR_SEGUNDO_FACTOR` |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudCodigo](#esquema-solicitudcodigo) (`application/json`) |
| **Respuestas** | `204` Segundo factor desactivado<br>`401` El codigo no coincide<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` El rol ADMIN exige segundo factor |

### `POST /api/veedor/segundo-factor/confirmacion`

**Confirmar el alta con un codigo de la app**

Devuelve una sesion nueva de alcance COMPLETO. Es lo que permite que un ADMIN recien sembrado pase de su sesion restringida al panel sin volver a escribir la clave que acaba de escribir.

| | |
|---|---|
| **Acceso** | Sesión + `CONFIGURAR_SEGUNDO_FACTOR` |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudCodigo](#esquema-solicitudcodigo) (`application/json`) |
| **Respuestas** | `200` Segundo factor activo; sesion nueva emitida → [SesionVeedor](#esquema-sesionveedor)<br>`401` El codigo no coincide → [SesionVeedor](#esquema-sesionveedor)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` No hay un alta en curso → [SesionVeedor](#esquema-sesionveedor) |

## Esquemas

Los tipos que viajan en cuerpos y respuestas. Un campo **nullable** puede llegar como `null`: significa «sin
dato», no «valor por defecto».

<a id="esquema-altasegundofactorrespuesta"></a>

### AltaSegundoFactorRespuesta

Datos para dar de alta el segundo factor. El secreto solo se muestra aqui, una vez.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `uri` | string |  |  | URI otpauth:// para pintar el QR |
| `secreto` | string |  |  | El mismo secreto en Base32, para teclearlo si la camara falla |

<a id="esquema-coordenadadto"></a>

### CoordenadaDTO

Coordenada GPS del reporte, solo cuando el usuario la autoriza (RF007)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `latitud` | number (double) | sí |  |  |
| `longitud` | number (double) | sí |  |  |

<a id="esquema-corterespuesta"></a>

### CorteRespuesta

Corte oficial (RF016-RF017)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `sectoresAfectados` | lista de string |  |  |  |
| `inicio` | string (date-time) |  |  |  |
| `finPrometido` | string (date-time) |  |  |  |
| `finReal` | string (date-time) |  |  | Nulo mientras el corte sigue abierto |
| `causa` | string |  |  |  |
| `origen` | string |  |  |  |
| `estado` | string |  |  |  |

<a id="esquema-credencialveedor"></a>

### CredencialVeedor

Credencial de acceso al panel del veedor

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string |  |  | Correo de la cuenta |
| `clave` | string |  |  | Clave de la cuenta |
| `codigoTotp` | string |  |  | Codigo de 6 digitos de la app de autenticacion. Se omite en el primer intento; si la cuenta tiene segundo factor, la respuesta 401 con type `segundo-factor-requerido` indica que hay que reintentar incluyendolo. |

<a id="esquema-estadisticasectorrespuesta"></a>

### EstadisticaSectorRespuesta

Sector con su cantidad de cortes registrados

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectorId` | string |  |  |  |
| `nombre` | string |  |  |  |
| `cantidadCortes` | integer (int32) |  |  |  |

<a id="esquema-estadisticasrespuesta"></a>

### EstadisticasRespuesta

Estadísticas públicas globales (M7, RF023)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectoresMasAfectados` | lista de [EstadisticaSectorRespuesta](#esquema-estadisticasectorrespuesta) |  |  |  |
| `cortesPorDiaDeSemana` | mapa de integer (int32) |  |  |  |
| `duracionPromedioHoras` | number (double) |  |  |  |

<a id="esquema-eventoauditoriarespuesta"></a>

### EventoAuditoriaRespuesta

Asiento de la bitacora de auditoria de cuentas: quien le hizo que a quien

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `accion` | string |  |  |  |
| `autorCorreo` | string |  |  | Nulo cuando actua el sistema o alguien sin sesion |
| `sujetoCorreo` | string |  |  |  |
| `detalle` | string |  |  |  |
| `ip` | string |  |  |  |
| `ocurrioEn` | string (date-time) |  |  |  |

<a id="esquema-eventobitacorarespuesta"></a>

### EventoBitacoraRespuesta

Evento de la bitácora pública, de solo anexado (RF026-RF028)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `tipo` | string |  |  | CORTE_ANUNCIADO, CORTE_CONFIRMADO_POR_CIUDADANOS, CORTE_RESTABLECIDO o CORTE_DETECTADO_POR_INGESTA |
| `sectorId` | string |  |  | Nulo si el evento no está atado a un sector |
| `corteId` | string |  |  | Nulo si el evento no está atado a un corte oficial (p. ej. consenso ciudadano) |
| `timestamp` | string (date-time) |  |  |  |
| `descripcion` | string |  |  |  |
| `estado` | string |  |  | Estado del servicio que afirma el evento: CON_SERVICIO, SIN_SERVICIO, PRESION_BAJA o CORTE_PROGRAMADO. Nulo si el evento no habla del servicio — presentarlo entonces como informativo, sin color de estado. |
| `urlOriginal` | string |  |  | Boletín o nota que respalda el evento. Nulo si la fuente no lo trae. |
| `imagenUrl` | string |  |  | Portada del boletín. Nula si la fuente no la trae. |
| `cantidadReportesSustento` | integer (int32) |  |  | RF011 — cuántos reportes ciudadanos sostuvieron el cambio, en los eventos de consenso; 0 en los demás. Los ids no viajan en el listado (pesaban cientos de KB por página): se piden con GET /api/bitacora/{id}/sustento. |

<a id="esquema-indicecumplimientorespuesta"></a>

### IndiceCumplimientoRespuesta

Índice de Cumplimiento (RF020-RF022): comparación explícita entre duración prometida y real, nunca un porcentaje aislado.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectorId` | string |  |  | Nulo cuando el índice es por corte o global, no por sector |
| `duracionPrometidaSegundos` | integer (int64) |  |  |  |
| `duracionRealSegundos` | integer (int64) |  |  |  |
| `desviacionSegundos` | integer (int64) |  |  | duracionReal - duracionPrometida. Negativa si terminó antes de lo prometido |
| `porcentajeCumplimiento` | number (double) |  |  | Capado en 100 cuando el corte termina antes o a tiempo |

<a id="esquema-iotcoordenada"></a>

### IotCoordenada

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `lat` | number (double) |  |  |  |
| `lon` | number (double) |  |  |  |

<a id="esquema-iotpresionrequest"></a>

### IotPresionRequest

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sensorId` | string |  |  |  |
| `sectorId` | string |  |  |  |
| `presionPsi` | number (double) |  |  |  |
| `coordenada` | [IotCoordenada](#esquema-iotcoordenada) |  |  |  |

<a id="esquema-open311response"></a>

### Open311Response

service_request de Open311 GeoReport v2

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `service_request_id` | string |  |  |  |
| `status` | string |  |  | open o closed |
| `service_code` | string |  |  | Código del tipo de servicio |
| `service_name` | string |  |  |  |
| `description` | string |  |  |  |
| `address` | string |  |  | Nombre del barrio. La unidad geográfica es el sector, no un punto (ADR-026) |
| `requested_datetime` | string (date-time) |  | sí | Cuándo se registró el estado actual del sector |
| `updated_datetime` | string (date-time) |  | sí | Igual a requested_datetime: el estado del sector es su propia actualización |

<a id="esquema-problemdetail"></a>

### ProblemDetail

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `type` | string (uri) |  |  |  |
| `title` | string |  |  |  |
| `status` | integer (int32) |  |  |  |
| `detail` | string |  |  |  |
| `instance` | string (uri) |  |  |  |
| `properties` | mapa de object |  |  |  |

<a id="esquema-propuestaingestarespuesta"></a>

### PropuestaIngestaRespuesta

Propuesta de cambio de estado detectada por la ingesta automatizada (M9), esperando la revisión de un veedor. No afecta el mapa público hasta que se apruebe.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `sectorId` | string |  |  |  |
| `estadoPropuesto` | string |  |  | SIN_SERVICIO, PRESION_BAJA, CORTE_PROGRAMADO o CON_SERVICIO |
| `fuente` | string |  |  | Colector que la detectó |
| `urlOriginal` | string |  | sí | Enlace al boletín o nota de prensa original |
| `citaTextual` | string |  |  | Fragmento del que se dedujo el estado, para que el veedor pueda verificarlo |
| `confianza` | number (double) |  |  | Entre 0 y 1, graduada según la evidencia que halló el extractor (ADR-032): 0.85 con enumeración explícita de barrios y horario, 0.75 con enumeración sin horario, 0.45 con una mención suelta en prosa. Sirve para ordenar la cola, no para pub… |
| `detectadaEn` | string (date-time) |  |  |  |
| `estadoRevision` | string |  |  | PENDIENTE, APROBADA o DESCARTADA |
| `inicioDeclarado` | string (date-time) |  | sí | Inicio de la ventana que el boletín prometió. Nulo cuando el texto no la declaraba: no se estima (ADR-006). |
| `finPrometido` | string (date-time) |  | sí | Fin prometido de la misma ventana. Junto con el inicio es lo que permite que el estado del sector evolucione solo (ADR-033) y lo que alimenta el Índice de Cumplimiento (RF020-RF022). |

<a id="esquema-puntoserierespuesta"></a>

### PuntoSerieRespuesta

Un mes de la evolución del Índice de Cumplimiento (RF024)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `periodo` | string |  |  | Mes en hora de Cartagena, ISO 8601 |
| `duracionPrometidaSegundos` | integer (int64) |  |  |  |
| `duracionRealSegundos` | integer (int64) |  |  |  |
| `desviacionSegundos` | integer (int64) |  |  | duracionReal - duracionPrometida. Negativa si terminaron antes de lo prometido |
| `porcentajeCumplimiento` | number (double) |  |  | Capado en 100 cuando los cortes terminan antes o a tiempo |
| `cantidadCortes` | integer (int32) |  |  | Cortes cerrados sobre los que se calculó el mes. Un 40% sobre un solo corte y uno sobre veinte no significan lo mismo. |

<a id="esquema-reportemoderacionrespuesta"></a>

### ReporteModeracionRespuesta

Reporte ciudadano en la cola de moderación del veedor (RF018)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `sectorId` | string |  |  |  |
| `tipo` | string |  |  |  |
| `coordenada` | [CoordenadaDTO](#esquema-coordenadadto) |  |  |  |
| `timestamp` | string (date-time) |  |  |  |
| `estadoModeracion` | string |  |  | PENDIENTE, APROBADO o DESCARTADO |

<a id="esquema-reporterespuesta"></a>

### ReporteRespuesta

Reporte ciudadano registrado

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `sectorId` | string |  |  |  |
| `tipo` | string |  |  |  |
| `timestamp` | string (date-time) |  |  |  |
| `fotoUrl` | string |  |  |  |
| `confirmaciones` | integer (int32) |  |  |  |

<a id="esquema-respuestasectores"></a>

### RespuestaSectores

Listado de sectores con la hora en que el servidor genero la respuesta

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectores` | lista de [SectorRespuesta](#esquema-sectorrespuesta) |  |  |  |
| `generadoEn` | string (date-time) |  |  | Instante en que el servidor genero esta respuesta (UTC) |

<a id="esquema-saludcolectorrespuesta"></a>

### SaludColectorRespuesta

Estado de salud de un colector de la ingesta automatizada

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `nombre` | string |  |  |  |
| `ultimaEjecucionExitosa` | string (date-time) |  | sí | Nulo si el colector todavía no ha completado un ciclo con éxito |
| `ultimoFallo` | string (date-time) |  | sí | Nulo si nunca ha fallado |
| `motivoDelUltimoFallo` | string |  | sí | Mensaje del último fallo, para diagnosticar sin entrar al servidor |
| `itemsProcesados` | integer (int64) |  |  | Documentos traídos desde que arrancó el proceso |
| `tasaDeError` | number (double) |  |  | Entre 0 y 1, sobre los ciclos corridos desde que arrancó el proceso |
| `fallosConsecutivos` | integer (int32) |  |  | Ciclos seguidos fallando. Desde 3, el colector se reporta caído en /actuator/health |

<a id="esquema-sectorrespuesta"></a>

### SectorRespuesta

Sector de Cartagena con el estado conocido de su servicio de agua

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  | Identificador estable del sector |
| `nombre` | string |  |  | Nombre del barrio segun el GeoJSON oficial |
| `poblacion` | integer (int32) |  | sí | Habitantes según el censo. **Nulo cuando el barrio no tiene dato censal** (27 de los 211): no es 0, y no debe mostrarse como «0 habitantes». |
| `estado` | enum(CON_SERVICIO, SIN_SERVICIO, PRESION_BAJA, CORTE_PROGRAMADO) |  | sí | Estado conocido del servicio. **Nulo cuando no hay dato verificado**: no se asume CON_SERVICIO por omision, porque publicar servicio normal sin verificarlo es el falso positivo que el proyecto evita (ADR-014). Presentarlo como "sin datos". |
| `actualizadoEn` | string (date-time) |  | sí | Cuando se registro ese estado. Nulo si el sector no tiene estado. |

<a id="esquema-sesionveedor"></a>

### SesionVeedor

Sesion emitida para el panel del veedor (RNF011: expira en 8 horas)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `token` | string |  |  | Token JWT. Se envia como 'Authorization: Bearer <token>' |
| `usuarioId` | string |  |  |  |
| `nombre` | string |  |  |  |
| `correo` | string |  |  |  |
| `rol` | string |  |  | ADMIN, VEEDOR u OBSERVADOR |
| `permisos` | lista de string |  |  | Permisos efectivos ya resueltos: rol mas concedidos menos revocados |
| `alcance` | string |  |  | COMPLETO, o ALTA_SEGUNDO_FACTOR cuando la cuenta es ADMIN y todavia no dio de alta su TOTP. Con ese alcance el token solo sirve para /api/veedor/segundo-factor. |

<a id="esquema-solicitudcambioclave"></a>

### SolicitudCambioClave

Cambiar la propia clave con la sesión iniciada

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `claveActual` | string |  |  | La clave de hoy. Sin ella un token robado bastaría para cambiarla. |
| `claveNueva` | string |  |  | La nueva: de 12 a 128 caracteres y distinta de la actual. |

<a id="esquema-solicitudcierrecorte"></a>

### SolicitudCierreCorte

Cierre de un corte con la hora real de restablecimiento (RF017)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `horaReal` | string (date-time) | sí |  |  |

<a id="esquema-solicitudcodigo"></a>

### SolicitudCodigo

Codigo de 6 digitos de la app de autenticacion

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `codigo` | string |  |  |  |

<a id="esquema-solicitudconfirmar"></a>

### SolicitudConfirmar

Solicitud para confirmar un reporte ciudadano por otro vecino

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `huella` | string |  |  | Huella hash del dispositivo del usuario que confirma (ADR-007) |

<a id="esquema-solicitudcorte"></a>

### SolicitudCorte

Registro de un corte oficial por el veedor (RF016)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectoresAfectados` | lista de string |  |  | Identificadores de los sectores afectados |
| `inicio` | string (date-time) | sí |  |  |
| `finPrometido` | string (date-time) | sí |  |  |
| `causa` | string |  |  |  |

<a id="esquema-solicitudfijarclave"></a>

### SolicitudFijarClave

Fijar clave desde un enlace de un solo uso (invitacion o restablecimiento)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `token` | string |  |  | Token que venia en el enlace del correo |
| `clave` | string |  |  |  |

<a id="esquema-solicitudinvitacion"></a>

### SolicitudInvitacion

Invitacion emitida por un ADMIN: crea la cuenta con su rol y manda el enlace

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string |  |  |  |
| `nombre` | string |  |  |  |
| `rol` | string | sí |  | ADMIN, VEEDOR u OBSERVADOR |

<a id="esquema-solicitudpermisos"></a>

### SolicitudPermisos

Rol de base mas los ajustes por persona. Los permisos del rol se aplican solos; `concedidos` anade sobre ellos y `revocados` quita. Un permiso en las dos listas es un error y se rechaza.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `rol` | string |  |  | ADMIN, VEEDOR u OBSERVADOR |
| `concedidos` | lista de string |  |  |  |
| `revocados` | lista de string |  |  |  |

<a id="esquema-solicitudreenvioverificacion"></a>

### SolicitudReenvioVerificacion

Pedir de nuevo el correo de verificación. Responde siempre 202, exista o no la cuenta.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string |  |  |  |

<a id="esquema-solicitudregistro"></a>

### SolicitudRegistro

Solicitud de acceso al panel. No concede nada: exige verificar el correo y que un ADMIN apruebe.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string |  |  |  |
| `nombre` | string |  |  | Nombre con el que apareceras en la auditoria del panel |
| `clave` | string |  |  | Minimo 12 caracteres. La politica completa vive en ClaveEnClaro. |

<a id="esquema-solicitudreporte"></a>

### SolicitudReporte

Reporte ciudadano sin registro (RF005-RF008)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectorId` | string |  | sí | Identificador del sector reportado. Opcional si viaja la coordenada: entonces el servidor infiere el barrio que la contiene (RF007). Si no viaja ninguno, 400. |
| `tipo` | string |  |  | SIN_AGUA, PRESION_BAJA o SERVICIO_RESTABLECIDO |
| `huella` | string |  |  | Huella anónima del dispositivo (ADR-007) — no es una cuenta ni un identificador personal. El cliente la genera una vez (p. ej. un UUID persistido en el dispositivo, hasheado) y la reutiliza en cada reporte; es lo único que permite RF006 (l… |
| `coordenada` | [CoordenadaDTO](#esquema-coordenadadto) |  |  |  |

<a id="esquema-solicitudrestablecer"></a>

### SolicitudRestablecer

Pedir el enlace de restablecimiento. Responde siempre 202, exista o no la cuenta.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string |  |  |  |

<a id="esquema-solicitudsuscripcion"></a>

### SolicitudSuscripcion

Solicitud para suscribirse a los avisos de uno o más sectores

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string |  |  | Correo al que llegarán los avisos |
| `sectorIds` | lista de string |  |  | Identificadores de los sectores a seguir |

<a id="esquema-suscripcionrespuesta"></a>

### SuscripcionRespuesta

Suscripción creada, pendiente de confirmación por correo (RF013)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `correo` | string |  |  |  |
| `sectorIds` | lista de string |  |  |  |
| `estado` | string |  |  | PENDIENTE_CONFIRMACION, CONFIRMADA o CANCELADA |
| `creadaEn` | string (date-time) |  |  |  |

<a id="esquema-usuariorespuesta"></a>

### UsuarioRespuesta

Cuenta del panel, tal como la ve un ADMIN

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `correo` | string |  |  |  |
| `nombre` | string |  |  |  |
| `estado` | string |  |  | PENDIENTE_VERIFICACION, PENDIENTE_APROBACION, INVITADA, ACTIVA, SUSPENDIDA o RECHAZADA |
| `rol` | string |  |  |  |
| `permisosEfectivos` | lista de string |  |  |  |
| `permisosConcedidos` | lista de string |  |  |  |
| `permisosRevocados` | lista de string |  |  |  |
| `segundoFactorActivo` | boolean |  |  |  |
| `creadoEn` | string (date-time) |  |  |  |
| `actualizadoEn` | string (date-time) |  |  |  |

