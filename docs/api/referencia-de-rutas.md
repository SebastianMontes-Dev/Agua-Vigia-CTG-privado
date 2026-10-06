# Referencia de rutas y esquemas

> **Generado — no editar a mano.** Lo produce `scripts/generar-referencia-api.mjs` a partir de
> `backend/openapi.yaml` (rutas, cuerpos, respuestas, esquemas) y de los `@PreAuthorize` de los
> controladores (permisos). Para regenerarlo, ver la cabecera del script.
>
> Las **guías** de esta carpeta explican el porqué y los flujos; esta página es el catálogo exacto.

**83 operaciones** en 77 rutas, más las páginas HTML de cortesía y el SSE.

Leyenda de **Acceso**: *Público* no exige token · *Sesión + `PERMISO`* exige `Authorization: Bearer <token>` de una
cuenta que tenga ese permiso · *Sesión (cualquier cuenta)* exige token pero ningún permiso concreto.
Todo error sale en RFC 7807: ver [Errores y límites](errores-y-limites.md).

## Índice

- [Bitácora](#bit-cora) (2)
- [Cuentas](#cuentas) (13)
- [Cumplimiento](#cumplimiento) (6)
- [Dispositivos](#dispositivos) (1)
- [Estadisticas](#estadisticas) (2)
- [Fotos](#fotos) (2)
- [IoT](#iot) (1)
- [Open311](#open311) (1)
- [Reportes](#reportes) (4)
- [Sectores](#sectores) (5)
- [Sistema](#sistema) (1)
- [Suscripciones](#suscripciones) (5)
- [Vecinos](#vecinos) (6)
- [Veedor](#veedor) (3)
- [Veedor - Cortes](#veedor-cortes) (8)
- [Veedor - Cuenta propia](#veedor-cuenta-propia) (1)
- [Veedor - Cuentas](#veedor-cuentas) (8)
- [Veedor - Disputas](#veedor-disputas) (1)
- [Veedor - Ingesta](#veedor-ingesta) (6)
- [Veedor - Moderación](#veedor-moderaci-n) (4)
- [Veedor - Segundo factor](#veedor-segundo-factor) (3)
- [Esquemas](#esquemas)

## Bitácora

Bitácora pública de eventos, de solo anexado (RF026-RF028)

### `GET /api/bitacora`

**Listar los eventos de la bitácora, más recientes primero**

Paginado: la bitácora es de solo anexado (RF028), así que crece sin cota. El total, la página y el enlace a la siguiente viajan en las cabeceras `X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size` y `Link` — el cuerpo sigue siendo un arreglo JSON, así que un cliente que las ignore no se rompe. Por defecto 50 eventos; el máximo por página es 200. Filtros opcionales, que se combinan y buscan en todo el historial: `sectorId`, `tipo`, `desde` (inclusivo) y `hasta` (exclusivo), ambos instantes ISO 8601 en UTC. El enlace `Link` a la siguiente página conserva los filtros. Sin coincidencias, la respuesta es una página vacía, no un error; un `tipo` que no existe o un `hasta` que no es posterior a `desde` son un 400.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `pagina` (query)<br>`tamano` (query)<br>`sectorId` (query)<br>`tipo` (query)<br>`desde` (query)<br>`hasta` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Listado generado → lista de [EventoBitacoraRespuesta](#esquema-eventobitacorarespuesta)<br>`400` Tipo de evento desconocido, fecha mal formada o rango invertido → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/bitacora/{id}/sustento`

**Los reportes que sustentan un evento de consenso (RF011)**

Ids de los reportes ciudadanos que sostuvieron el cambio de estado, para contrastarlo con la evidencia. Van aparte del listado porque en una avería grande pueden ser miles. Paginado con las mismas cabeceras que el listado; por defecto 50 ids por página, máximo 200. Vacío en los eventos que no son de consenso.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `id` (path, obligatorio)<br>`pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Ids de la página pedida (vacía si se pasa del final) → lista de string<br>`404` No existe el evento → [ProblemDetail](#esquema-problemdetail) |

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
| **Cuerpo** | object (`application/x-www-form-urlencoded`) |
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
| **Respuestas** | `202` Solicitud recibida; revisa tu correo<br>`400` Correo mal formado, clave que no cumple la politica o barrio inexistente |

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

### `GET /api/cumplimiento/calidad`

**Calidad del dato del índice: cuánto se midió y cuánto no**

Cuantos cierres sostienen el indice, cuantos son provisionales, y cuantos cortes ya vencidos no tienen cierre en algun barrio. Responde aunque no haya un solo cierre —cuando `/api/cumplimiento` responde 400—: es lo que permite mostrar «sin datos suficientes» con cifras. `sectorId` es opcional.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `sectorId` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Calidad calculada → [CalidadDelCumplimientoRespuesta](#esquema-calidaddelcumplimientorespuesta) |

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

## Dispositivos

Identidad pseudonima de quien reporta sin cuenta

### `POST /api/dispositivos`

**Pedir una identidad de dispositivo**

Crea un dispositivo nuevo y devuelve su token firmado. Pidelo la primera vez, guardalo y enviarlo en `X-Dispositivo` en cada reporte y confirmacion. Cada llamada crea una identidad distinta: no la pidas en cada reporte. Si un reporte responde 401 `dispositivo-invalido`, pide otro. Maximo 10 por hora por IP.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `201` Token emitido → [TokenDeDispositivoRespuesta](#esquema-tokendedispositivorespuesta)<br>`429` Demasiados tokens pedidos desde esta IP → [TokenDeDispositivoRespuesta](#esquema-tokendedispositivorespuesta) |

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

## Fotos

Fotos de los reportes: públicas solo si el reporte está aprobado

### `GET /api/fotos/{nombre}`

**Ver la foto de un reporte aprobado**

Sin sesion. Responde 404 si la foto no existe, si su reporte aun no esta aprobado o si el veedor la descarto: son el mismo 404 a proposito, para que nadie pueda sondear que fotos hay. La interfaz debe mirar `fotoEstado` del reporte y no pedir la imagen mientras este EN_REVISION.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `nombre` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` La imagen (image/jpeg o image/png) → —<br>`404` No hay una foto publica con ese nombre → string (byte) |

### `GET /api/veedor/fotos/{nombre}`

**Ver la foto de cualquier reporte (panel)**

Para moderar: el panel ve la foto en cualquier estado, aprobada o no.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | `nombre` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` La imagen (image/jpeg o image/png) → —<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` Ningun reporte reclama esa foto → string (byte) |

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

Sin registro ni cuenta (RF005), pero con identidad: la cabecera `X-Dispositivo` (token de `POST /api/dispositivos`) o la sesion de un vecino. Sin ninguna de las dos responde 401 `dispositivo-invalido`. Limita automaticamente los reportes por identidad en la ventana vigente (RF006, 3 para un dispositivo y 5 para un vecino) — ver 429. Hace falta el `sectorId`, la `coordenada` o ambos (RF007): con solo la coordenada el servidor infiere el sector que la contiene y responde 400 si cae fuera de todo barrio de Cartagena. La coordenada se envia solo si el usuario autorizo compartir su ubicacion; con su `precisionMetros` el servidor verifica el reporte (campo `verificacion` de la respuesta) y guarda solo una aproximacion de ella. La respuesta trae `subidaToken`, el permiso de un solo uso para subir la foto de este reporte.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `X-Dispositivo` (header) |
| **Cuerpo** | [SolicitudReporte](#esquema-solicitudreporte) (`application/json`) |
| **Respuestas** | `201` Reporte registrado → [ReporteRespuesta](#esquema-reporterespuesta)<br>`400` Sector inexistente, tipo inválido, coordenada fuera de Cartagena o sin sector ni coordenada → [ProblemDetail](#esquema-problemdetail)<br>`401` Falta `X-Dispositivo`, no lo firmó este servidor o el dispositivo ya no existe (type `dispositivo-invalido`): pide otro… → [ProblemDetail](#esquema-problemdetail)<br>`429` La identidad superó el límite de reportes para este sector → [ProblemDetail](#esquema-problemdetail) |

### `POST /api/reportes/{id}/confirmar`

**Confirmar un reporte**

Permite a otro vecino confirmar un reporte ciudadano (M11). Sin cuerpo: la identidad de quien confirma viaja en `X-Dispositivo` o en la sesion de un vecino, igual que al reportar.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `id` (path, obligatorio)<br>`X-Dispositivo` (header) |
| **Cuerpo** | — |
| **Respuestas** | `200` Reporte confirmado → [ReporteRespuesta](#esquema-reporterespuesta)<br>`401` Falta o no es válida la identidad del dispositivo (type `dispositivo-invalido`) → [ProblemDetail](#esquema-problemdetail)<br>`404` Reporte no encontrado → [ProblemDetail](#esquema-problemdetail) |

### `POST /api/reportes/{id}/foto`

**Agregar evidencia a un reporte**

Sube la foto de un reporte (M10). Exige la cabecera `X-Subida` con el `subidaToken` que recibio quien creo el reporte: sirve una sola vez y vence a los pocos minutos. Solo JPEG y PNG (se comprueba la firma del archivo, no solo el tipo declarado). La foto queda en revision: el publico la ve cuando el veedor aprueba el reporte, y nunca cuenta como voto.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `id` (path, obligatorio)<br>`X-Subida` (header) |
| **Cuerpo** | object (`multipart/form-data`) |
| **Respuestas** | `200` Evidencia agregada → [ReporteRespuesta](#esquema-reporterespuesta)<br>`400` Falta el archivo o no es una imagen valida → [ProblemDetail](#esquema-problemdetail)<br>`403` Falta el token de subida, ya se uso, vencio o es de otro reporte (type `subida-no-autorizada`); es la misma respuesta s… → [ProblemDetail](#esquema-problemdetail)<br>`409` El reporte ya tiene foto → [ProblemDetail](#esquema-problemdetail)<br>`415` Formato no permitido, como WebP (type `formato-no-permitido`) → [ProblemDetail](#esquema-problemdetail) |

### `POST /api/sectores/{sectorId}/restablecimiento`

**Confirmar con un toque que volvió el agua**

El `token` es el del enlace del correo de aviso. Sin sesion ni `X-Dispositivo`: lo que identifica a quien toca es el token, que firma el servidor, vence y es de un barrio y una suscripcion. Registra un reporte `SERVICIO_RESTABLECIDO` en ese barrio, con el mismo cupo y el mismo quorum de cualquier otro reporte: un toque no cambia el estado por si solo.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | `sectorId` (path, obligatorio)<br>`token` (query, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `201` Voto registrado → [ReporteRespuesta](#esquema-reporterespuesta)<br>`400` Falta el token → [ProblemDetail](#esquema-problemdetail)<br>`403` El enlace no sirve (type `enlace-invalido`): vencio, es de otro barrio o ya no recibes los avisos → [ProblemDetail](#esquema-problemdetail)<br>`429` Se agoto el cupo de reportes de esta suscripcion en el barrio → [ProblemDetail](#esquema-problemdetail) |

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
| **Respuestas** | `200` Cortes de la página pedida → lista de [CorteRespuesta](#esquema-corterespuesta)<br>`404` No existe el sector → [ProblemDetail](#esquema-problemdetail) |

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

## Sistema

Qué instancia es y cuánto de lo que contiene es sintético

### `GET /api/sistema/modo`

**Modo del sistema**

`modo` es REAL o SIMULACION: la interfaz muestra un banner permanente en la simulacion, que nunca se presenta como real. `cuentasSinteticas` es cuantas cuentas de vecino las creo el sistema para probar el volumen: se dicen tal cual, no son adopcion. Se recuerda un minuto.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → [ModoDelSistemaRespuesta](#esquema-mododelsistemarespuesta) |

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

## Vecinos

Registro, ingreso y perfil de los vecinos

### `POST /api/cuentas/vecino`

**Registrarse como vecino**

Crea la cuenta sin clave y envia al correo el enlace para elegirla. Al elegirla la cuenta queda ACTIVA, sin aprobacion de un administrador. La clave no viaja aqui: asi nadie puede registrar el correo de otra persona con una clave suya. Exige el barrio y aceptar el aviso de privacidad; la casilla de avisos es aparte. Responde 202 aunque el correo ya tenga cuenta, para no revelar que direcciones estan registradas.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudRegistroVecino](#esquema-solicitudregistrovecino) (`application/json`) |
| **Respuestas** | `202` Solicitud recibida; revisa tu correo<br>`400` Correo mal formado, barrio ausente o inexistente, o privacidad no aceptada |

### `PATCH /api/vecino/perfil`

**Actualizar el perfil**

Cambia nombre, barrio y la casilla de avisos; solo se aplica lo que viene. Cambiar de barrio anula la verificacion de barrio anterior.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudPerfilVecino](#esquema-solicitudperfilvecino) (`application/json`) |
| **Respuestas** | `200` Perfil actualizado → [PerfilVecinoRespuesta](#esquema-perfilvecinorespuesta)<br>`400` Nombre invalido o barrio inexistente → [PerfilVecinoRespuesta](#esquema-perfilvecinorespuesta) |

### `POST /api/vecino/sesion`

**Iniciar sesion como vecino**

Devuelve un token JWT valido por 8 horas que sirve en `/api/vecino/**` (y, del panel, solo en `GET /api/veedor/yo` y `POST /api/veedor/sesion/cierre`). Una cuenta del panel no entra por aqui, ni una de vecino por `/api/veedor/sesion`: en los dos casos la respuesta es la misma 401 que ante una clave incorrecta.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [CredencialVecino](#esquema-credencialvecino) (`application/json`) |
| **Respuestas** | `200` Credencial correcta, token emitido → [SesionVecino](#esquema-sesionvecino)<br>`401` Credencial incorrecta → [SesionVecino](#esquema-sesionvecino)<br>`403` La cuenta esta suspendida (si aun no eligio su clave, responde 401 como con una clave incorrecta) → [SesionVecino](#esquema-sesionvecino)<br>`423` Cuenta bloqueada por intentos fallidos → [SesionVecino](#esquema-sesionvecino)<br>`429` Demasiados intentos desde esta IP → [SesionVecino](#esquema-sesionvecino) |

### `POST /api/vecino/sesion/cierre`

**Cerrar sesion**

Revoca en el servidor todas las sesiones vivas de la cuenta, no solo la de este navegador.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `204` No Content |

### `POST /api/vecino/verificacion-barrio`

**Verificar el barrio con la ubicacion del momento**

Compara la coordenada con el poligono del barrio que declaraste. Si cae dentro, el barrio queda verificado con su fecha; la coordenada se descarta y no se guarda. Maximo 3 intentos por dia (429 con `Retry-After`). Si ya estaba verificado, no hace nada y devuelve el perfil. Es una senal blanda: sube el costo de votar desde un barrio ajeno, no prueba identidad.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | [SolicitudVerificacionBarrio](#esquema-solicitudverificacionbarrio) (`application/json`) |
| **Respuestas** | `200` Perfil con el barrio verificado → [PerfilVecinoRespuesta](#esquema-perfilvecinorespuesta)<br>`400` Coordenada fuera de rango o precision ausente o negativa → [PerfilVecinoRespuesta](#esquema-perfilvecinorespuesta)<br>`422` `ubicacion-fuera-del-barrio` (la ubicacion no cae en tu barrio declarado) o `ubicacion-imprecisa` (precision peor que 2… → [PerfilVecinoRespuesta](#esquema-perfilvecinorespuesta)<br>`429` Ya usaste los 3 intentos de hoy → [PerfilVecinoRespuesta](#esquema-perfilvecinorespuesta) |

### `GET /api/vecino/yo`

**Perfil del vecino con la sesion**

Devuelve el estado vigente en la base de datos, no lo que dice el token.

| | |
|---|---|
| **Acceso** | Público |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → [PerfilVecinoRespuesta](#esquema-perfilvecinorespuesta) |

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

### `PATCH /api/veedor/cortes/{id}/anulacion`

**Anular un corte publicado por error**

Queda como historia con su motivo, fuera del Índice y de las estadísticas; los barrios se recalculan y la bitácora anexa la corrección (no se edita lo ya publicado).

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_CORTES` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | [SolicitudAnulacion](#esquema-solicitudanulacion) (`application/json`) |
| **Respuestas** | `200` Corte anulado → [CorteRespuesta](#esquema-corterespuesta)<br>`400` Falta el motivo → [ProblemDetail](#esquema-problemdetail)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El corte no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El corte ya estaba anulado → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/cortes/{id}/cierre`

**Cerrar un corte con la hora real de restablecimiento (RF017)**

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_CORTES` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | [SolicitudCierreCorte](#esquema-solicitudcierrecorte) (`application/json`) |
| **Respuestas** | `200` Corte cerrado → [CorteRespuesta](#esquema-corterespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El corte no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El corte ya estaba cerrado → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/cortes/{id}/sectores/{sectorId}/cierre`

**Cerrar un solo barrio del corte**

Los barrios de un corte se restablecen a horas distintas. El corte pasa a RESTABLECIDO cuando todos sus barrios tienen cierre.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_CORTES` |
| **Parámetros** | `id` (path, obligatorio)<br>`sectorId` (path, obligatorio) |
| **Cuerpo** | [SolicitudCierreCorte](#esquema-solicitudcierrecorte) (`application/json`) |
| **Respuestas** | `200` Barrio cerrado → [CorteRespuesta](#esquema-corterespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El corte no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El corte ya no está abierto o el barrio ya estaba cerrado → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/cortes/{id}/sectores/{sectorId}/confirmacion`

**Confirmar —o corregir la hora de— un cierre provisional**

Un cierre que solo sostenían los vecinos o los sensores pasa a definitivo con la hora que fije el veedor.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_CORTES` |
| **Parámetros** | `id` (path, obligatorio)<br>`sectorId` (path, obligatorio) |
| **Cuerpo** | [SolicitudCierreCorte](#esquema-solicitudcierrecorte) (`application/json`) |
| **Respuestas** | `200` Cierre confirmado → [CorteRespuesta](#esquema-corterespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El corte no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El barrio no tiene cierre o ya estaba confirmado → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/cortes/vencidos`

**Cola de cortes vencidos**

Los cortes cuya promesa ya pasó sin cierre y los que tienen un cierre provisional que nadie ha confirmado, del más antiguo al más reciente.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → lista de [CorteRespuesta](#esquema-corterespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

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

Paginado, con el total y el enlace a la siguiente pagina en `X-Total-Count` y `Link`. `estado` filtra por PENDIENTE_APROBACION para ver solo la cola de altas y `barrioId` (slug de un sector) por el barrio donde viven las personas.

| | |
|---|---|
| **Acceso** | Sesión + `GESTIONAR_USUARIOS` |
| **Parámetros** | `estado` (query)<br>`barrioId` (query)<br>`pagina` (query)<br>`tamano` (query) |
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
| **Respuestas** | `201` Invitacion enviada → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`400` Datos invalidos o barrio inexistente → [UsuarioRespuesta](#esquema-usuariorespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`409` Ya existe una cuenta con ese correo → [UsuarioRespuesta](#esquema-usuariorespuesta) |

## Veedor - Disputas

Barrios cuyo estado oficial contradicen los vecinos

### `GET /api/veedor/disputas`

**Listar los barrios en disputa, los más contradichos primero**

Cada barrio trae `reportesEnContra`: cuántos vecinos sostienen que el estado oficial no es el real.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` OK → lista de [SectorRespuesta](#esquema-sectorrespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

## Veedor - Ingesta

Revisión de las propuestas de la ingesta automatizada (M9)

### `GET /api/veedor/ingesta/fallidos`

**Listar los documentos que siguen fallando al procesarse, más recientes primero**

Un documento sale de esta lista en cuanto se procesa con éxito: es lo que sigue roto *ahora*, no un histórico. Máximo 200 filas.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | — |
| **Cuerpo** | — |
| **Respuestas** | `200` Listado generado → lista de [DocumentoFallidoRespuesta](#esquema-documentofallidorespuesta)<br>`401` Falta el token del veedor → lista de [DocumentoFallidoRespuesta](#esquema-documentofallidorespuesta)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/ingesta/propuestas`

**Listar las propuestas pendientes de revisión, más recientes primero**

Paginado, con el total y el enlace a la siguiente página en las cabeceras `X-Total-Count` y `Link`. Por defecto 50; el máximo por página es 200.

| | |
|---|---|
| **Acceso** | Sesión + `VER_PANEL` |
| **Parámetros** | `pagina` (query)<br>`tamano` (query) |
| **Cuerpo** | — |
| **Respuestas** | `200` Listado generado → lista de [PropuestaIngestaRespuesta](#esquema-propuestaingestarespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/ingesta/propuestas/{id}/anulacion`

**Anular una propuesta ya aprobada**

Deshace una aprobación por error: la propuesta queda ANULADA con su motivo, deja de afirmar nada del presente, la bitácora anexa una corrección que cita el boletín y el barrio se recalcula. Queda constancia en la auditoría.

| | |
|---|---|
| **Acceso** | Sesión + `REVISAR_INGESTA` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | [SolicitudAnulacion](#esquema-solicitudanulacion) (`application/json`) |
| **Respuestas** | `200` Propuesta anulada → [PropuestaIngestaRespuesta](#esquema-propuestaingestarespuesta)<br>`400` Falta el motivo → [ProblemDetail](#esquema-problemdetail)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` La propuesta no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` La propuesta no estaba aprobada → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/ingesta/propuestas/{id}/aprobar`

**Aprobar una propuesta**

Aplica el estado propuesto al sector y anexa el evento a la bitácora pública (RF026). Es el único camino por el que la ingesta llega al mapa.

| | |
|---|---|
| **Acceso** | Sesión + `REVISAR_INGESTA` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Propuesta aprobada y estado aplicado → [PropuestaIngestaRespuesta](#esquema-propuestaingestarespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` La propuesta no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El sector de la propuesta ya no existe, o la propuesta ya estaba descartada → [ProblemDetail](#esquema-problemdetail) |

### `PATCH /api/veedor/ingesta/propuestas/{id}/descartar`

**Descartar una propuesta**

No toca el sector. La propuesta se archiva como descartada, no se borra. Descartar una ya aprobada responde 409.

| | |
|---|---|
| **Acceso** | Sesión + `REVISAR_INGESTA` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Propuesta descartada → [PropuestaIngestaRespuesta](#esquema-propuestaingestarespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` La propuesta no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` La propuesta ya estaba aprobada → [ProblemDetail](#esquema-problemdetail) |

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

### `PATCH /api/veedor/reportes/{id}/foto/descartar`

**Descartar solo la foto de un reporte**

El reporte sigue como esta (su voto no cambia); la foto deja de servirse al publico y el panel la sigue viendo como evidencia.

| | |
|---|---|
| **Acceso** | Sesión + `MODERAR_REPORTES` |
| **Parámetros** | `id` (path, obligatorio) |
| **Cuerpo** | — |
| **Respuestas** | `200` Foto descartada → [ReporteModeracionRespuesta](#esquema-reportemoderacionrespuesta)<br>`401` Sin sesión, token inválido, caducado o revocado → [ProblemDetail](#esquema-problemdetail)<br>`403` La sesión no tiene el permiso que exige esta operación → [ProblemDetail](#esquema-problemdetail)<br>`404` El reporte no existe → [ProblemDetail](#esquema-problemdetail)<br>`409` El reporte no tiene foto → [ProblemDetail](#esquema-problemdetail) |

### `GET /api/veedor/reportes/pendientes`

**Listar los reportes pendientes de moderación, más antiguos primero**

Paginado, con el total y el enlace a la siguiente página en las cabeceras `X-Total-Count` y `Link`. Por defecto 50; el máximo por página es 200. Cada reporte trae `senalRed`: verdadero si viene de una red que ya envió una ráfaga de reportes a ese barrio (D9).

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

<a id="esquema-calidaddelcumplimientorespuesta"></a>

### CalidadDelCumplimientoRespuesta

Lo que sostiene el Indice de Cumplimiento: cuantos cierres se midieron y cuantos cortes no se pudieron medir. Existe aunque no haya un solo cierre (el indice entonces responde 400): es lo que permite decir «sin datos suficientes» con cifras.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `cierresMedidos` | integer (int64) |  |  | Pares corte-barrio con cierre que entran al indice |
| `cierresProvisionales` | integer (int64) |  |  | De ellos, los que solo sostienen vecinos o sensores |
| `porcentajeProvisional` | number (double) |  |  | cierresProvisionales sobre cierresMedidos, de 0 a 100 |
| `cortesSinCierreConfirmado` | integer (int64) |  |  | Cortes ya vencidos con algun barrio sin cierre, incluidos los expirados |
| `cortesAnulados` | integer (int64) |  |  | Cortes retirados por error |

<a id="esquema-cierrerespuesta"></a>

### CierreRespuesta

Cómo y cuándo se restableció un barrio dentro del corte

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectorId` | string |  |  |  |
| `hora` | string (date-time) |  |  |  |
| `fuente` | string |  |  | Quién lo sostiene: VEEDOR, ACUACAR, VECINOS, SENSOR o PRENSA |
| `provisional` | boolean |  |  | Un cierre que solo sostienen los vecinos o los sensores: un veedor o un boletín puede confirmarlo o corregirlo |

<a id="esquema-consentimiento"></a>

### Consentimiento

Las dos casillas son independientes: aceptar la privacidad es obligatorio para registrarse; `avisos` autoriza recibir avisos de cortes de tu barrio y por defecto es falso.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `privacidad` | boolean |  |  | Acepta el aviso de privacidad vigente. Debe ser true. |
| `avisos` | boolean |  |  | Acepta recibir avisos de cortes de su barrio |

<a id="esquema-consentimientorespuesta"></a>

### ConsentimientoRespuesta

Lo que acepto, con la version del texto y la fecha

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `tipo` | string |  |  | PRIVACIDAD o AVISOS |
| `version` | string |  |  |  |
| `fecha` | string (date-time) |  |  |  |

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
| `causa` | string |  |  |  |
| `origen` | string |  |  |  |
| `estado` | string |  |  | ANUNCIADO, CONFIRMADO, RESTABLECIDO, EXPIRADO o ANULADO. RESTABLECIDO solo cuando todos sus barrios tienen cierre; EXPIRADO nadie lo confirmó a tiempo y no cuenta en el Índice. |
| `cierres` | lista de [CierreRespuesta](#esquema-cierrerespuesta) |  |  | Un cierre por cada barrio ya restablecido; los pendientes no aparecen. Vacío mientras ningún barrio se haya restablecido. |
| `motivoAnulacion` | string |  | sí | Solo en un corte ANULADO: por qué se anuló |
| `caducaEn` | string (date-time) |  | sí | Solo en el corte del veedor, que es el override: desde esta hora deja de afirmar nada. Nulo si dura hasta que alguien lo cierre o expire. |

<a id="esquema-credencialvecino"></a>

### CredencialVecino

Credencial de un vecino registrado

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  | Correo de la cuenta |
| `clave` | string | sí |  | Clave de la cuenta |

<a id="esquema-credencialveedor"></a>

### CredencialVeedor

Credencial de acceso al panel del veedor

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  | Correo de la cuenta |
| `clave` | string | sí |  | Clave de la cuenta |
| `codigoTotp` | string |  |  | Codigo de 6 digitos de la app de autenticacion. Se omite en el primer intento; si la cuenta tiene segundo factor, la respuesta 401 con type `segundo-factor-requerido` indica que hay que reintentar incluyendolo. |

<a id="esquema-documentofallidorespuesta"></a>

### DocumentoFallidoRespuesta

Documento de la ingesta que falló al procesarse y sigue en cola de reintento

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `fuente` | string |  |  |  |
| `urlOriginal` | string |  |  |  |
| `titulo` | string |  | sí |  |
| `motivo` | string |  |  | Mensaje de la excepción que hizo fallar el procesamiento |
| `primerIntento` | string (date-time) |  |  |  |
| `ultimoIntento` | string (date-time) |  |  |  |
| `reintentos` | integer (int32) |  |  | Veces que se reintentó sin éxito, una por ciclo de ingesta |

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
| `fuente` | string |  | sí | Quién sostiene el evento: ACUACAR o PRENSA (boletín o nota), VECINOS (quórum) o VEEDOR (panel). Nulo en los eventos anteriores a este dato. |
| `respaldo` | [RespaldoRespuesta](#esquema-respaldorespuesta) |  |  |  |

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
| `porcentajeProvisional` | number (double) |  |  | De los cierres que sostienen este indice, que porcentaje (0 a 100) solo lo sostienen vecinos o sensores y un veedor o un boletin aun puede corregir. Publicalo junto al porcentaje: el numero es solo tan solido como esto. |
| `cortesSinCierreConfirmado` | integer (int64) |  |  | Cortes cuya ventana prometida ya termino y en los que algun barrio no tiene cierre (incluidos los que expiraron sin confirmacion). No cuentan a favor ni en contra de Acuacar: se declaran. |
| `cortesAnulados` | integer (int64) |  |  | Cortes publicados por error y retirados: no entran al indice |

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
| `sensorId` | string | sí |  |  |
| `sectorId` | string | sí |  |  |
| `presionPsi` | number (double) |  |  |  |
| `coordenada` | [IotCoordenada](#esquema-iotcoordenada) |  |  |  |

<a id="esquema-mododelsistemarespuesta"></a>

### ModoDelSistemaRespuesta

Qué instancia es esta y cuánto de lo que contiene es sintético

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `modo` | string |  |  | REAL o SIMULACION |
| `cuentasSinteticas` | integer (int64) |  |  | Cuentas de vecino creadas por el sistema para probar el volumen; no son personas registradas |

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

<a id="esquema-perfilvecinorespuesta"></a>

### PerfilVecinoRespuesta

Perfil del vecino con sesion

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `correo` | string |  |  |  |
| `nombre` | string |  |  |  |
| `estado` | string |  |  | ACTIVA mientras pueda iniciar sesion |
| `barrioId` | string |  |  | Slug del barrio que declaro |
| `barrioVerificado` | boolean |  |  | true si probo con su ubicacion que vive en `barrioId` |
| `barrioVerificadoEn` | string (date-time) |  | sí |  |
| `recibeAvisos` | boolean |  |  | true si acepto recibir avisos de cortes de su barrio |
| `consentimientos` | lista de [ConsentimientoRespuesta](#esquema-consentimientorespuesta) |  |  | Lo que acepto, con la version del texto y la fecha |
| `creadoEn` | string (date-time) |  |  |  |
| `actualizadoEn` | string (date-time) |  |  |  |

<a id="esquema-problemdetail"></a>

### ProblemDetail

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `type` | string (uri) |  |  |  |
| `title` | string |  | sí |  |
| `status` | integer (int32) |  |  |  |
| `detail` | string |  | sí |  |
| `instance` | string (uri) |  | sí |  |
| `properties` | mapa de object |  | sí |  |

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
| `motivoDeRevision` | string |  | sí | Por qué esta propuesta espera al veedor en vez de haberse publicado sola (D5): confianza baja, una ventana de más de 72 horas, más de 40 barrios, un nombre ambiguo, o que viene de prensa. Nulo si salió sola. |

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
| `verificacion` | string |  |  | CUENTA_VERIFICADA, UBICACION_VERIFICADA o NINGUNA: cuánto respalda el servidor que quien reporta está en el barrio |
| `fotoEstado` | string |  |  | SIN_FOTO, EN_REVISION, PUBLICA o DESCARTADA |
| `fotoUrl` | string |  |  | Ruta de la foto para el panel (`/api/veedor/fotos/{nombre}`), que la ve en cualquier estado. Nulo si no hay foto |
| `senalRed` | boolean |  | sí | Solo en la cola de pendientes: el reporte viene de una red (resumen diario de la IP, que no se expone) que ya envio una rafaga de reportes a este barrio. No bloquea nada: es donde mirar primero. Nulo en el resto de respuestas |

<a id="esquema-reporterespuesta"></a>

### ReporteRespuesta

Reporte ciudadano registrado

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `id` | string |  |  |  |
| `sectorId` | string |  |  |  |
| `tipo` | string |  |  |  |
| `timestamp` | string (date-time) |  |  |  |
| `fotoUrl` | string |  |  | Ruta de la foto: `/api/fotos/{nombre}`. Existir no significa que se pueda ver: el servidor la sirve al publico solo si `fotoEstado` es PUBLICA; antes responde 404. |
| `fotoEstado` | string |  |  | SIN_FOTO, EN_REVISION (el reporte espera moderacion), PUBLICA o DESCARTADA. Con EN_REVISION la interfaz muestra «en revision» en vez de pedir la imagen. |
| `confirmaciones` | integer (int32) |  |  |  |
| `verificacion` | string |  |  | Cuanto respalda el servidor que quien reporta esta en el barrio: CUENTA_VERIFICADA, UBICACION_VERIFICADA o NINGUNA. Lo decide el servidor; el cliente solo lo muestra. |
| `subidaToken` | string |  |  | Solo al crear el reporte: el token con el que su autor sube la foto en `POST /api/reportes/{id}/foto` (cabecera `X-Subida`). De un solo uso y vence a los pocos minutos; no se vuelve a entregar. |

<a id="esquema-respaldorespuesta"></a>

### RespaldoRespuesta

Cuántos vecinos respaldan un cambio y cuántos hacían falta

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `vecinos` | integer (int32) |  |  |  |
| `umbral` | integer (int32) |  |  |  |

<a id="esquema-respaldovecinalrespuesta"></a>

### RespaldoVecinalRespuesta

Cuántos vecinos respaldan un estado y cuántos hacían falta

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `vecinos` | integer (int32) |  |  |  |
| `umbral` | integer (int32) |  |  |  |

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
| `verificadoEn` | string (date-time) |  | sí | Última vez que una fuente con autoridad (consenso de vecinos, corte del veedor o boletín aprobado) sostuvo ese estado, haya cambiado o no (ADR-073). Nunca anterior a `actualizadoEn`. Nulo si el sector no tiene estado. Confirmar sin cambiar… |
| `origen` | enum(ACUACAR, VEEDOR, VECINOS, PRENSA, SENSOR) |  | sí | Quién sostiene el estado: ACUACAR (boletín oficial), PRENSA (nota aprobada por el veedor), VEEDOR (corte o cierre del veedor), VECINOS (quórum de reportes) o SENSOR. Nulo si el sector no tiene estado. |
| `ventanaPrometida` | [VentanaPrometidaRespuesta](#esquema-ventanaprometidarespuesta) |  |  |  |
| `restablecimientoPorConfirmar` | boolean |  |  | La promesa ya venció y nadie confirmó que volvió el agua: el barrio sigue como estaba, pero por confirmar. Un restablecimiento pide menos vecinos que reportar una avería. |
| `enDisputa` | boolean |  |  | Un quórum de vecinos contradice a la fuente oficial. El color no cambia: la contradicción se muestra como una insignia y llega a la cola del veedor. |
| `reportesEnContra` | integer (int32) |  |  | Cuántos vecinos sostienen esa contradicción. 0 si no hay disputa. |
| `respaldo` | [RespaldoVecinalRespuesta](#esquema-respaldovecinalrespuesta) |  |  |  |

<a id="esquema-sesionvecino"></a>

### SesionVecino

Sesion de un vecino. Sirve en `/api/vecino/**`; del panel solo abre `GET /api/veedor/yo` y `POST /api/veedor/sesion/cierre`, que muestran o cierran lo propio.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `token` | string |  |  | Token JWT. Se envia como 'Authorization: Bearer <token>' |
| `usuarioId` | string |  |  |  |
| `nombre` | string |  |  |  |
| `correo` | string |  |  |  |
| `permisos` | lista de string |  |  | Siempre `GESTIONAR_PERFIL_PROPIO` |

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

<a id="esquema-solicitudanulacion"></a>

### SolicitudAnulacion

Anulación de un corte o de una propuesta de la ingesta que se publicó por error

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `motivo` | string | sí |  | Por qué se anula. Queda en la bitácora pública y en la auditoría. |

<a id="esquema-solicitudcambioclave"></a>

### SolicitudCambioClave

Cambiar la propia clave con la sesión iniciada

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `claveActual` | string | sí |  | La clave de hoy. Sin ella un token robado bastaría para cambiarla. |
| `claveNueva` | string | sí |  | La nueva: de 12 a 128 caracteres y distinta de la actual. |

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
| `codigo` | string | sí |  |  |

<a id="esquema-solicitudcorte"></a>

### SolicitudCorte

Registro de un corte oficial por el veedor (RF016)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectoresAfectados` | lista de string | sí |  | Identificadores de los sectores afectados |
| `inicio` | string (date-time) | sí |  |  |
| `finPrometido` | string (date-time) | sí |  |  |
| `causa` | string | sí |  |  |
| `caducaEn` | string (date-time) |  | sí | Opcional. El corte del veedor es el override: desde esta hora deja de afirmar nada, aunque nadie lo haya cerrado. |

<a id="esquema-solicitudfijarclave"></a>

### SolicitudFijarClave

Fijar clave desde un enlace de un solo uso (invitacion o restablecimiento)

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `token` | string | sí |  | Token que venia en el enlace del correo |
| `clave` | string | sí |  |  |

<a id="esquema-solicitudinvitacion"></a>

### SolicitudInvitacion

Invitacion emitida por un ADMIN: crea la cuenta con su rol y manda el enlace

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  |  |
| `nombre` | string | sí |  |  |
| `rol` | string | sí |  | ADMIN, VEEDOR u OBSERVADOR |
| `barrioId` | string |  | sí | Opcional: slug del barrio de la persona. 400 si no existe. |

<a id="esquema-solicitudperfilvecino"></a>

### SolicitudPerfilVecino

Cambios al perfil. Solo se aplica lo que viene; un campo ausente no se toca.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `nombre` | string |  |  |  |
| `barrioId` | string |  | sí | Slug del barrio donde vives. Cambiarlo anula la verificacion de barrio anterior. 400 si no existe. |
| `recibirAvisos` | boolean |  | sí | true acepta avisos de cortes de tu barrio; false retira ese consentimiento |

<a id="esquema-solicitudpermisos"></a>

### SolicitudPermisos

Rol de base mas los ajustes por persona. Los permisos del rol se aplican solos; `concedidos` anade sobre ellos y `revocados` quita. Un permiso en las dos listas es un error y se rechaza.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `rol` | string | sí |  | ADMIN, VEEDOR u OBSERVADOR |
| `concedidos` | lista de string |  |  |  |
| `revocados` | lista de string |  |  |  |

<a id="esquema-solicitudreenvioverificacion"></a>

### SolicitudReenvioVerificacion

Pedir de nuevo el correo de verificación. Responde siempre 202, exista o no la cuenta.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  |  |

<a id="esquema-solicitudregistro"></a>

### SolicitudRegistro

Solicitud de acceso al panel. No concede nada: exige verificar el correo y que un ADMIN apruebe.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  |  |
| `nombre` | string | sí |  | Nombre con el que apareceras en la auditoria del panel |
| `clave` | string | sí |  | Minimo 12 caracteres. La politica completa vive en ClaveEnClaro. |
| `barrioId` | string |  | sí | Opcional: slug del barrio donde vives (uno de `GET /api/sectores`). 400 si no existe. |

<a id="esquema-solicitudregistrovecino"></a>

### SolicitudRegistroVecino

Registro de un vecino. Confirmar el correo activa la cuenta: no hay aprobacion de un administrador porque un vecino solo gestiona su propio perfil.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  |  |
| `nombre` | string | sí |  |  |
| `barrioId` | string | sí |  | Slug del barrio donde vives (uno de `GET /api/sectores`). 400 si no existe. |
| `consentimiento` | [Consentimiento](#esquema-consentimiento) | sí |  |  |

<a id="esquema-solicitudreporte"></a>

### SolicitudReporte

Reporte ciudadano (RF005-RF008). La identidad de quien reporta no va en el cuerpo: viaja en la cabecera `X-Dispositivo` (token de `POST /api/dispositivos`) o en la sesion de un vecino (`Authorization: Bearer`). Un campo `huella` en el cuerpo, como el de versiones anteriores, se ignora.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `sectorId` | string |  | sí | Identificador del sector reportado. Opcional si viaja la coordenada: entonces el servidor infiere el barrio que la contiene (RF007). Si no viaja ninguno, 400. |
| `tipo` | string | sí |  | SIN_AGUA, PRESION_BAJA o SERVICIO_RESTABLECIDO |
| `coordenada` | [CoordenadaDTO](#esquema-coordenadadto) |  |  |  |
| `precisionMetros` | number (double) |  | sí | Precision de la coordenada en metros (`coords.accuracy` del navegador). Sin ella, o si es peor que 200 m, la ubicacion no verifica el reporte. |

<a id="esquema-solicitudrestablecer"></a>

### SolicitudRestablecer

Pedir el enlace de restablecimiento. Responde siempre 202, exista o no la cuenta.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  |  |

<a id="esquema-solicitudsuscripcion"></a>

### SolicitudSuscripcion

Solicitud para suscribirse a los avisos de uno o más sectores

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `correo` | string (email) | sí |  | Correo al que llegarán los avisos |
| `sectorIds` | lista de string | sí |  | Identificadores de los sectores a seguir |

<a id="esquema-solicitudverificacionbarrio"></a>

### SolicitudVerificacionBarrio

La ubicacion del momento. El servidor la compara con el barrio que declaraste y la descarta: no se guarda ni se audita; solo queda que el barrio quedo verificado.

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `coordenada` | [CoordenadaDTO](#esquema-coordenadadto) | sí |  |  |
| `precisionMetros` | number (double) | sí |  | Precision de la lectura en metros (`coords.accuracy` del navegador). Una precision peor que 200 m (ubicacion aproximada por red) no verifica y responde 422 `ubicacion-imprecisa`. |

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

<a id="esquema-tokendedispositivorespuesta"></a>

### TokenDeDispositivoRespuesta

Identidad de dispositivo emitida por el servidor

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `token` | string |  |  | Se envia en la cabecera `X-Dispositivo` de `POST /api/reportes` y de `/confirmar`. Guardalo: perderlo es perder la identidad. |

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
| `barrioId` | string |  | sí | Slug del barrio donde vive la persona; nulo si no lo dio (ADR-081) |
| `permisosEfectivos` | lista de string |  |  |  |
| `permisosConcedidos` | lista de string |  |  |  |
| `permisosRevocados` | lista de string |  |  |  |
| `segundoFactorActivo` | boolean |  |  |  |
| `creadoEn` | string (date-time) |  |  |  |
| `actualizadoEn` | string (date-time) |  |  |  |
| `sintetica` | boolean |  |  | Cuenta de vecino creada por el sistema para probar el volumen (ADR-094): no es una persona registrada y no puede iniciar sesion. El listado las incluye; esta marca permite ocultarlas. |

<a id="esquema-ventanaprometidarespuesta"></a>

### VentanaPrometidaRespuesta

Desde cuándo y hasta cuándo prometió la fuente oficial la afectación

| Campo | Tipo | Oblig. | Nulo | Descripción |
|---|---|---|---|---|
| `inicio` | string (date-time) |  |  |  |
| `fin` | string (date-time) |  |  |  |

