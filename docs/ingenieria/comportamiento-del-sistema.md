# Comportamiento del sistema

> **Qué hace AguaVigía hoy**, en comportamiento observable. Este archivo dice el *qué*; el *porqué* vive
> en `docs/design-decisions.md` (ADR), los requisitos con su prioridad en
> `docs/product-requirements.md` (RF/RNF), el contrato HTTP en `backend/openapi.yaml` y la prueba que
> sostiene cada requisito en `docs/ingenieria/matriz-trazabilidad.md`. **Un dato vive en un solo
> archivo:** aquí no se repite el porqué, y los ADR no repiten el comportamiento.
>
> **Regla del proyecto:** un documento que se separa del código es un defecto, igual que una prueba que
> miente. Quien cambie comportamiento actualiza la capacidad que corresponda en este archivo, en el
> mismo cambio.
>
> Cada capacidad tiene un propósito, requisitos («debe…») y escenarios «Cuando / Entonces» que se
> pueden convertir en prueba. El contenido viene de las especificaciones de OpenSpec que se retiraron
> en `ADR-047`.

## Contenido

| Capacidad | Módulo | Requisitos |
|---|---|---|
| [Mapa en vivo](#mapa-en-vivo) | M1 | RF001–RF004 |
| [Reporte ciudadano](#reporte-ciudadano) | M2 · M10 · M11 | RF005–RF008, RF037, RF038 |
| [Consenso automático](#consenso-automático) | M3 | RF009–RF011 |
| [Alertas por correo](#alertas-por-correo) | M4 | RF012–RF015 |
| [Panel del veedor](#panel-del-veedor) | M5 | RF016–RF019 |
| [Índice de Cumplimiento](#índice-de-cumplimiento) | M6 | RF020–RF022 |
| [Estadísticas](#estadísticas) | M7 | RF023–RF025 |
| [Bitácora pública](#bitácora-pública) | M8 | RF026–RF028 |
| [Ingesta automatizada](#ingesta-automatizada) | M9 | RF029–RF031 |
| [API abierta Open311](#api-abierta-open311) | M12 | RF039 |
| [Telemetría IoT pasiva](#telemetría-iot-pasiva) | M13 | RF040 |
| [Alertas push](#alertas-push) | M14 | RF041 |
| [Cuentas y permisos del panel](#cuentas-y-permisos-del-panel) | M15 | RF042–RF046 |

---

## Mapa en vivo

*M1 · RF001–RF004*

Responder la única pregunta con la que la gente entra a AguaVigía —«¿tengo agua o no, y hasta
cuándo?»— en menos de cinco segundos, sin leer, sin registrarse y sin hacer scroll. Publica el
estado actual de cada sector de Cartagena sobre un mapa, con una alternativa textual equivalente
para quien no puede ver el mapa. Cubre M1 (RF001–RF004).

### Requisito: Histórico público de cortes de un sector

El sistema debe mostrar sin iniciar sesión los cortes oficiales que afectaron a un sector, del más reciente al
más antiguo (RF002).

#### Escenario: Consulta anónima del histórico

- **Cuando** cualquiera consulta `GET /api/sectores/{id}/cortes`
- **Entonces** obtiene los cortes abiertos y cerrados del sector, paginados, el más reciente primero
- **Y** un sector sin cortes devuelve una lista vacía, y uno inexistente, 404

#### Escenario: La población viaja nula si no hay dato censal

- **Cuando** un sector no tiene dato censal
- **Entonces** `GET /api/sectores` publica su `poblacion` como `null`, nunca como 0

### Requisito: Estado de todos los sectores en el mapa

El sistema debe mostrar un mapa de Cartagena con todos los sectores coloreados según su estado
actual: con servicio, sin servicio, presión baja o corte programado. Un sector sin dato verificado
debe publicarse con estado nulo, nunca como «con servicio» (ADR-014).

El color debe ir siempre acompañado de forma o texto: nunca es el único portador del mensaje
(RNF016).

#### Escenario: Sector con corte confirmado

- **Cuando** un sector tiene un corte confirmado vigente
- **Entonces** `GET /api/sectores` lo devuelve con estado `SIN_SERVICIO`
- **Y** el mapa lo pinta en rojo y lo acompaña de su etiqueta textual

#### Escenario: Sector del que no se sabe nada

- **Cuando** ninguna fuente verificada ha reportado el estado de un sector
- **Entonces** su estado es `null`, no `CON_SERVICIO`
- **Y** la interfaz lo presenta como «sin dato», distinguible de un sector operando normal

### Requisito: Resumen del servicio sin suponer datos

El sistema debe mostrar en la barra superior el porcentaje de la red operativa y, en el panel, el
conteo de sectores por estado, calculados **solo sobre los sectores con estado verificado** (ADR-014,
ADR-046). Un sector con estado nulo no cuenta ni como operativo ni como afectado.

El porcentaje operativo debe ser la proporción de sectores en `CON_SERVICIO` sobre los sectores con
estado verificado; presión baja y corte programado no cuentan como operativos. Mientras ningún sector
tenga estado verificado, el sistema debe mostrar «calculando» en la barra y «—» con «Esperando datos
validados» en las tarjetas, sin cifras inventadas.

#### Escenario: Ningún sector con estado verificado

- **Cuando** la API no devuelve sectores con estado verificado
- **Entonces** la barra superior dice «Red Distrital: calculando»
- **Y** las tarjetas por estado muestran «—» y «Esperando datos validados», con «Ver en el mapa» deshabilitado

#### Escenario: Sectores verificados y uno sin dato

- **Cuando** hay tres sectores con estado verificado, dos de ellos en `CON_SERVICIO`, y un cuarto sin dato
- **Entonces** la barra superior dice «Red Distrital: 67% operativa»
- **Y** el sector sin dato no entra en la cuenta

### Requisito: Detalle de un sector

El sistema debe permitir consultar el detalle de un sector —estado, último cambio e histórico de
cortes— al seleccionarlo.

#### Escenario: Consulta del detalle

- **Cuando** el usuario selecciona un sector en el mapa o en la lista
- **Entonces** `GET /api/sectores/{id}` devuelve su estado, la marca de tiempo del último cambio y su
  histórico de cortes

#### Escenario: Sector inexistente

- **Cuando** se consulta un id de sector que no existe
- **Entonces** la API responde 404 con un cuerpo en formato RFC 7807

### Requisito: Frescura visible del dato

El sistema debe mostrar, junto a cada sector, cuánto tiempo hace que se actualizó su información.
Un mapa congelado que presenta datos viejos como actuales es peor que un mapa que admite que no
sabe.

#### Escenario: Fuente muda por horas

- **Cuando** la última actualización de un sector supera el umbral de frescura
- **Entonces** la interfaz lo marca como degradado en vez de presentar el dato como vigente

### Requisito: Alternativa textual accesible al mapa

El sistema debe ofrecer una lista textual de sectores con su estado como alternativa equivalente
al mapa. Un mapa sin lista es inaccesible para lector de pantalla (RNF012–RNF016).

#### Escenario: Navegación solo con teclado

- **Cuando** una persona recorre la página únicamente con el teclado
- **Entonces** puede alcanzar la lista de sectores y leer el estado de cada uno, con foco visible en
  todo momento

### Requisito: Actualización del mapa sin recargar

El sistema debe **avisar** de los cambios de estado de sector por un flujo de eventos
servidor-cliente (`ADR-049`). El aviso no lleva el estado: el cliente lo pide a `GET /api/sectores`,
que está cacheado, para que el costo de mantener el mapa al día no dependa de cuántos clientes haya.

#### Escenario: Cambio de estado mientras el mapa está abierto

- **Cuando** un sector cambia de estado y hay un cliente suscrito a `GET /api/sectores/stream`
- **Entonces** el cliente recibe un evento `sectores` con `actualizadoEn`
- **Y** vuelve a pedir `GET /api/sectores` y el mapa se repinta sin recarga

#### Escenario: Ráfaga de cambios

- **Cuando** varios sectores cambian de estado en el mismo segundo
- **Entonces** cada cliente recibe **un solo** aviso, no uno por cambio

#### Escenario: Conexión inactiva

- **Cuando** no hay cambios durante un rato
- **Entonces** el servidor envía un latido cada 25 segundos para mantener viva la conexión

#### Escenario: Se alcanza el tope de conexiones

- **Cuando** la instancia ya tiene el máximo de conexiones en vivo
- **Entonces** responde `429` con `Retry-After` y el cliente sigue funcionando por sondeo de `GET /api/sectores`

### Requisito: Polígonos de los sectores servidos por la API

El sistema debe entregar la geometría de los sectores por la API, con el mismo `id` que el listado, para
que un cliente pueda dibujar el mapa sin llevar el GeoJSON ni calcular identificadores.

#### Escenario: Pedir las geometrías

- **Cuando** un cliente consulta `GET /api/sectores/geometria`
- **Entonces** recibe un `FeatureCollection` con un `Feature` por sector cuyo `id` es el del listado
- **Y** la respuesta es cacheable un día

### Requisito: Primera respuesta útil bajo tres segundos en 3G

El sistema debe mostrar el estado de todos los sectores en menos de 3 segundos sobre conexión 3G
simulada (RNF001). El mapa debe cargar primero el estado y después la geometría detallada.

#### Escenario: Medición con throttling 3G

- **Cuando** se audita la página principal con throttling 3G
- **Entonces** el estado de los sectores es visible antes de los 3 segundos

---

### Requisito: Un frontend en su propio dev server puede llamar a la API en local

En el entorno local (perfiles `dev` y `docker`) la API deja pasar, por CORS, a los orígenes de los dev servers habituales (`5173`, `3000`, `4200`) o a los que declare `CORS_ORIGENES`. En producción sigue cerrado: todo va detrás del mismo proxy.

#### Escenario: Preflight desde un origen permitido

- **Dado** el entorno levantado con `docker compose up`
- **Cuando** el navegador hace `OPTIONS /api/sectores` con `Origin: http://localhost:5173`
- **Entonces** responde `200` con `Access-Control-Allow-Origin: http://localhost:5173`

#### Escenario: Preflight desde un origen ajeno

- **Cuando** el `Origin` no está en la lista
- **Entonces** responde `403` y no emite cabeceras CORS

## Reporte ciudadano

*M2 · M10 · M11 · RF005–RF008, RF037, RF038*

Dejar que un vecino diga «no tengo agua» en dos toques, sin registrarse y sin cuenta, porque el
usuario real no se va a registrar. El control de abuso que sustituye al registro es un cupo por
dispositivo, no una barrera de entrada. Cubre M2 (RF005–RF008), M10 (RF037) y M11 (RF038).

### Requisito: Reportar sin registro

El sistema debe permitir reportar «no tengo agua», «presión baja» o «ya volvió el servicio» sin
requerir registro ni cuenta.

#### Escenario: Reporte anónimo aceptado

- **Cuando** un vecino envía `POST /api/reportes` con tipo de reporte, sector y huella de dispositivo
- **Entonces** la API responde 201 con el identificador del reporte
- **Y** no se almacena ningún dato personal identificable más allá de la huella anónima (RNF008)

#### Escenario: Confirmación bajo un segundo

- **Cuando** se envía un reporte en condiciones normales
- **Entonces** el usuario recibe la confirmación en menos de 1 segundo (RNF002)

### Requisito: Cupo de reportes por dispositivo

El sistema debe limitar la cantidad de reportes que un mismo dispositivo puede enviar dentro de
una ventana de tiempo configurable, como sustituto del registro (RF006).

El cupo del dispositivo ciudadano y el del sensor IoT deben ser independientes: un sensor reporta
cada pocos minutos por diseño y con el cupo ciudadano se autobloquearía.

#### Escenario: Dispositivo dentro del cupo

- **Cuando** un dispositivo ha enviado menos reportes que `limite-por-dispositivo` en la ventana
- **Entonces** el reporte se acepta

#### Escenario: Dispositivo que excede el cupo

- **Cuando** un dispositivo supera `limite-por-dispositivo` dentro de `ventana-limite-minutos`
- **Entonces** la API responde 429 con un cuerpo RFC 7807 que explica el límite y cuándo se libera
- **Y** el reporte no se persiste

### Requisito: Inferencia del sector desde la ubicación

El sistema debe registrar la coordenada del reporte cuando el usuario la autorice, e inferir el
sector a partir de ella. Si el usuario no autoriza la ubicación, debe usar el sector que tenía
abierto.

#### Escenario: Ubicación autorizada

- **Cuando** el usuario autoriza la ubicación y su coordenada cae dentro de un sector conocido
- **Entonces** el reporte queda asociado a ese sector sin que el usuario lo elija

#### Escenario: Ubicación denegada

- **Cuando** el usuario deniega la ubicación
- **Entonces** el reporte usa el sector que el usuario tenía abierto y el flujo continúa sin error

#### Escenario: Solo coordenada, sin sector declarado

- **Cuando** el reporte llega con `coordenada` y sin `sectorId`
- **Entonces** el servidor infiere el sector con una consulta geoespacial (`ADR-050`) y lo devuelve en la respuesta

#### Escenario: Coordenada fuera de Cartagena

- **Cuando** el reporte llega solo con una coordenada que no cae en ningún sector
- **Entonces** la API responde `400` y no registra nada

#### Escenario: Ni sector ni coordenada

- **Cuando** el reporte no trae ninguno de los dos
- **Entonces** la API responde `400`

### Requisito: Reporte en dos toques

El sistema debe permitir completar un reporte en un máximo de dos toques desde el mapa, sin
campos opcionales y sin captcha visible.

#### Escenario: Camino más corto

- **Cuando** el usuario toca «Reportar que no tengo agua» y confirma el tipo de reporte
- **Entonces** el reporte queda enviado, sin pasos intermedios

#### Escenario: Reportar desde la barra o desde el llamado a veedores

- **Cuando** el usuario toca «Reportar afectación» en la barra superior o en el llamado a veedores
- **Entonces** se abre el mismo formulario de reporte, sin sector preseleccionado

### Requisito: Evidencia fotográfica opcional

El sistema debe permitir adjuntar una fotografía a un reporte ya creado. Al guardarla debe
comprimirla y eliminar sus metadatos EXIF, para que la foto no delate la ubicación ni el
dispositivo de quien reporta (RNF021, ADR-027).

#### Escenario: Foto con EXIF de geolocalización

- **Cuando** se sube por `POST /api/reportes/{id}/foto` una imagen con coordenadas en su EXIF
- **Entonces** la imagen almacenada queda sin metadatos EXIF
- **Y** se guarda comprimida

#### Escenario: Archivo demasiado grande

- **Cuando** la imagen supera el tamaño máximo de subida
- **Entonces** la API responde con un error 413 en formato RFC 7807, no con un 500

### Requisito: Confirmación comunitaria de un reporte

El sistema debe permitir confirmar un reporte ciudadano existente con un solo clic —«¿tú también
estás sin agua?»— sin registro (RF038).

#### Escenario: Vecino confirma un reporte abierto

- **Cuando** otro dispositivo llama a `POST /api/reportes/{id}/confirmar`
- **Entonces** la confirmación se suma al reporte y cuenta para el consenso
- **Y** el mismo dispositivo no puede confirmar dos veces el mismo reporte

---

## Consenso automático

*M3 · RF009–RF011*

Convertir muchos reportes sueltos en un hecho publicable: cuando suficientes vecinos independientes
coinciden dentro de una ventana de tiempo, el sector cambia de estado sin que nadie lo apruebe a
mano. Es el diferencial del producto y también su mayor riesgo, así que cada cambio queda con los
reportes que lo sustentaron. Cubre M3 (RF009–RF011).

### Requisito: Cambio de estado por masa crítica de reportes

El sistema debe cambiar el estado de un sector automáticamente cuando N reportes independientes
coincidan dentro de una ventana de tiempo configurable.

#### Escenario: Se alcanza el umbral

- **Cuando** el número de reportes independientes del mismo tipo en un sector alcanza el umbral
  dentro de la ventana
- **Entonces** el estado del sector cambia
- **Y** se registra el evento correspondiente en la bitácora pública

#### Escenario: Reportes insuficientes

- **Cuando** los reportes no alcanzan el umbral dentro de la ventana
- **Entonces** el estado del sector no cambia
- **Y** los reportes siguen contando hasta que la ventana los deje fuera

### Requisito: Estrategias de consenso intercambiables

El sistema debe soportar al menos dos estrategias de consenso intercambiables: umbral fijo y
umbral proporcional a la población del sector. La estrategia debe ser seleccionable por
configuración sin tocar el caso de uso.

#### Escenario: Umbral proporcional en un sector populoso

- **Cuando** la estrategia activa es la proporcional y el sector tiene una población alta registrada
- **Entonces** el umbral exigido es mayor que el de un sector pequeño

#### Escenario: Sector sin población registrada

- **Cuando** la estrategia proporcional no encuentra población para el sector
- **Entonces** recurre al umbral fijo en vez de fallar

### Requisito: Trazabilidad del cambio por consenso

El sistema debe registrar qué reportes sustentaron cada cambio de estado por consenso, para que un
veedor pueda auditar por qué el mapa dice lo que dice.

#### Escenario: Auditoría de un cambio publicado

- **Cuando** un veedor consulta un cambio de estado producido por consenso
- **Entonces** obtiene la lista de los reportes que lo sustentaron (`reportesSustento` en el evento de la bitácora)

#### Escenario: Un sector recibe cientos de reportes por segundo

- **Cuando** un sector ya en el umbral recibe reportes a gran velocidad (una avería masiva)
- **Entonces** el consenso se evalúa como mucho una vez por segundo y sector: el resto de reportes deja el sector
  pendiente y un barrido lo evalúa en el segundo siguiente
- **Y** el cambio de estado puede tardar hasta unos 2 s más, pero ningún reporte se queda sin evaluar
- **Y** solo se cargan los reportes de sustento cuando el estado va a cambiar (`ADR-053`)

#### Escenario: Dos reportes simultáneos del mismo sector

- **Cuando** dos reportes del mismo sector alcanzan el consenso a la vez
- **Entonces** el estado cambia una sola vez y la bitácora recibe **un** evento (`ADR-051`)

---

## Alertas por correo

*M4 · RF012–RF015*

Avisar al vecino en su correo cuando su sector cambie de estado, sin pedirle más que la dirección.
El doble opt-in y la baja en un clic no son cortesía: son la Ley 1581 de 2012 sobre datos
personales. Cubre M4 (RF012–RF015).

### Requisito: Suscripción con solo un correo

El sistema debe permitir suscribirse a uno o más sectores indicando únicamente un correo
electrónico.

#### Escenario: Alta de suscripción

- **Cuando** alguien envía `POST /api/suscripciones` con su correo y uno o más sectores
- **Entonces** la suscripción queda creada en estado pendiente de confirmación
- **Y** no se envía ninguna alerta todavía

### Requisito: Doble opt-in antes de cualquier alerta

El sistema debe confirmar la suscripción mediante doble opt-in antes de enviar cualquier alerta.

#### Escenario: Suscripción sin confirmar

- **Cuando** el sector de una suscripción pendiente cambia de estado
- **Entonces** no se le envía ninguna alerta a ese correo

#### Escenario: Confirmación desde el enlace del correo

- **Cuando** el suscriptor abre el enlace de `GET /api/suscripciones/confirmar` con su token
- **Entonces** ve una página con un botón y la suscripción **no cambia** (un antivirus o una vista previa de enlaces
  abre los GET sin que nadie los pida, `ADR-054`)
- **Y** al pulsar el botón, `POST /api/suscripciones/confirmar` la pasa a confirmada; la respuesta es HTML o
  JSON según la cabecera `Accept`, sin rutas separadas (ADR-030)

#### Escenario: Un cliente de API pide JSON al GET del enlace

- **Cuando** un cliente pide `GET /api/suscripciones/confirmar` con `Accept: application/json`
- **Entonces** recibe `406` (el GET ya no actúa; debe usar `POST`)

### Requisito: Notificación al cambiar el estado del sector

El sistema debe notificar al suscriptor confirmado cuando su sector cambie de estado: corte
anunciado, confirmado o restablecido.

#### Escenario: Corte confirmado en un sector suscrito

- **Cuando** un sector con suscriptores confirmados pasa a `SIN_SERVICIO`
- **Entonces** cada suscriptor confirmado recibe un correo con el cambio

#### Escenario: Fallo del servidor de correo

- **Cuando** el envío del correo falla
- **Entonces** el cambio de estado del sector se publica igual y el fallo no propaga al flujo principal

### Requisito: Baja en un clic sin credenciales

Todo correo debe incluir un enlace de baja que funcione en un solo clic, sin pedir credenciales.
Al darse de baja, el correo debe eliminarse (RNF009).

#### Escenario: Baja desde el enlace

- **Cuando** el suscriptor abre el enlace de `GET /api/suscripciones/cancelar` con su token
- **Entonces** ve una página con un botón «Darme de baja» y la suscripción **no cambia**
- **Y** al pulsarlo, `POST /api/suscripciones/cancelar` la cancela sin pedirle contraseña ni datos adicionales
- **Y** su correo deja de estar almacenado (se sustituye por una dirección `.invalid`)

#### Escenario: El correo de confirmación también lleva la baja

- **Cuando** el sistema envía el correo de confirmación de una suscripción
- **Entonces** incluye el enlace de baja, igual que los correos de aviso

---

## Panel del veedor

*M5 · RF016–RF019*

Dar a quien vigila el servicio las tres acciones que sostienen el resto del producto: registrar un
corte oficial con lo que el operador prometió, cerrarlo con la hora real en que volvió el agua, y
moderar los reportes ciudadanos que aún nadie ha revisado. Sin el corte prometido y el corte real
no hay Índice de Cumplimiento. Cubre M5 (RF016–RF019).

### Requisito: Registro de un corte oficial

El sistema debe permitir a un usuario autenticado registrar un corte oficial con sus sectores
afectados, hora de inicio, fin prometido y causa.

`CorteAgua` debe validar en su construcción la coherencia entre estado y ventana de tiempo: un
corte cuyo fin es anterior a su inicio no puede existir (ADR-024).

#### Escenario: Corte válido

- **Cuando** un veedor autenticado envía `POST /api/veedor/cortes` con sectores, inicio, fin
  prometido y causa
- **Entonces** el corte queda registrado
- **Y** se anota el evento «corte anunciado» en la bitácora pública

#### Escenario: Ventana de tiempo incoherente

- **Cuando** el fin prometido es anterior al inicio
- **Entonces** la construcción del corte falla y la API responde 400 en formato RFC 7807

### Requisito: Cierre de un corte con la hora real

El sistema debe permitir cerrar un corte registrando la hora real de restablecimiento. Ese dato es
el insumo del Índice de Cumplimiento.

#### Escenario: Cierre de un corte abierto

- **Cuando** un veedor envía `PATCH /api/veedor/cortes/{id}/cierre` con la hora real
- **Entonces** el corte queda cerrado con esa hora
- **Y** su desviación prometido/real pasa a estar disponible

#### Escenario: Cierre de un corte inexistente

- **Cuando** se intenta cerrar un id de corte que no existe
- **Entonces** la API responde 404 en formato RFC 7807, no 500

### Requisito: Moderación de reportes ciudadanos

El sistema debe permitir aprobar o descartar los reportes ciudadanos pendientes de moderación.
«Dudoso» debe significar «todo reporte sin moderar», no el resultado de una heurística de fraude
(ADR-023).

#### Escenario: Cola de pendientes

- **Cuando** un veedor consulta `GET /api/veedor/reportes/pendientes`
- **Entonces** recibe los reportes aún sin moderar, paginados

#### Escenario: Reporte aprobado

- **Cuando** el veedor llama a `PATCH /api/veedor/reportes/{id}/aprobar`
- **Entonces** el reporte pasa a aprobado y cuenta para el consenso

### Requisito: El panel exige autenticación, el resto es público

El acceso al panel debe requerir un token JWT con expiración máxima de 8 horas; el resto de la
plataforma debe ser público (RF019, RNF011).

La regla por defecto de la cadena de seguridad debe ser pública, y solo `/api/veedor/**` —salvo el
inicio de sesión— debe exigir autenticación, para que un endpoint público nuevo no dependa de que
alguien recuerde declararlo.

#### Escenario: Petición sin token al panel

- **Cuando** se llama a cualquier ruta bajo `/api/veedor/` sin token, salvo `POST /api/veedor/sesion`
- **Entonces** la API responde 401

#### Escenario: Token expirado

- **Cuando** se presenta un token emitido hace más de 8 horas
- **Entonces** la API responde 401 y la sesión no se renueva sola

#### Escenario: Fuerza bruta contra el inicio de sesión

- **Cuando** una misma IP falla el inicio de sesión más de 5 veces en 5 minutos
- **Entonces** las peticiones siguientes se rechazan con 429 durante lo que resta de la ventana

---

## Índice de Cumplimiento

*M6 · RF020–RF022*

Es la pieza que define el proyecto: comparar la duración que el operador prometió con la que el
corte realmente duró, y publicarlo. No es un puntaje de calidad ni una calificación; es la
diferencia entre lo dicho y lo hecho, presentada como comparación explícita. Cubre M6
(RF020–RF022).

### Requisito: Desviación entre duración prometida y real

El sistema debe calcular, por cada corte cerrado que tenga hora prometida, la desviación entre su
duración prometida y su duración real.

#### Escenario: Corte que duró más de lo prometido

- **Cuando** se consulta `GET /api/cumplimiento/cortes/{corteId}` de un corte cerrado que prometía
  2 horas y duró 8
- **Entonces** la respuesta expone ambas duraciones y la desviación entre ellas

#### Escenario: Corte cerrado sin hora prometida

- **Cuando** un corte cerrado no tiene fin prometido
- **Entonces** no aporta al índice, en vez de contarse como cumplido

### Requisito: Índice agregado por sector y global

El sistema debe publicar un índice agregado de cumplimiento por sector y uno global de la ciudad.

La agregación debe hacerse por suma de duraciones, no por promedio de porcentajes: promediar
porcentajes le daría a un corte de 20 minutos el mismo peso que a uno de 12 horas (ADR-022).

#### Escenario: Agregado de un sector

- **Cuando** se consulta `GET /api/cumplimiento/sectores/{sectorId}`
- **Entonces** el índice devuelto resulta de sumar las duraciones prometidas y las reales de sus
  cortes cerrados, no de promediar sus porcentajes

#### Escenario: Todavía no hay nada medido

- **Cuando** no existe ningún corte cerrado con hora prometida
- **Entonces** el sistema informa que no hay dato, y nunca un cumplimiento del 100%

### Requisito: El índice se presenta como comparación, no como puntaje

El sistema debe presentar el índice como comparación explícita entre lo prometido y lo real. Un
`87%` sin referencia no comunica nada; `Prometieron 2 horas · Fueron 8` sí (RF022).

#### Escenario: Presentación en la interfaz

- **Cuando** la interfaz muestra el índice de un sector
- **Entonces** muestra la barra de «prometido» y la de «real» una junto a la otra, con las cifras en
  lenguaje natural

### Requisito: Evolución del índice en el tiempo

El sistema debe exponer la serie temporal del índice y permitir exportarla en formato abierto.

#### Escenario: Serie mensual

- **Cuando** se consulta `GET /api/cumplimiento/serie`
- **Entonces** se obtiene la evolución del índice mes a mes

#### Escenario: Exportación para uso periodístico

- **Cuando** se consulta `GET /api/cumplimiento/serie.csv`
- **Entonces** se descarga la misma serie en CSV

---

## Estadísticas

*M7 · RF023–RF025*

Dar al veedor y al periodista la evidencia acumulada que un mapa en vivo no puede dar: qué sectores
sufren más, cuánto duran los cortes, con qué frecuencia ocurren y cómo evoluciona el cumplimiento.
Es el insumo de la denuncia informada, no un tablero decorativo. Cubre M7 (RF023–RF025).

### Requisito: Sectores más afectados, duración y frecuencia

El sistema debe mostrar los sectores más afectados, la duración promedio de los cortes y su
frecuencia mensual.

#### Escenario: Consulta de estadísticas

- **Cuando** se consulta `GET /api/estadisticas`
- **Entonces** la respuesta incluye el ranking de sectores afectados, la duración promedio y la
  frecuencia mensual de cortes

#### Escenario: Base de datos sin cortes

- **Cuando** todavía no hay cortes cerrados
- **Entonces** cada métrica se presenta como «sin dato», con su estado vacío explicado, y no como cero

### Requisito: Coherencia entre los totales presentados

Las cifras que la interfaz presenta juntas deben provenir del mismo conjunto de cortes. Un total
calculado sobre un universo distinto al de su desglose es un dato falso aunque cada mitad sea
correcta.

#### Escenario: Total y desglose

- **Cuando** la interfaz muestra un total junto a su reparto por día de la semana
- **Entonces** el total es la suma de ese reparto, no un conteo de otro conjunto

### Requisito: Exportación en formato abierto

El sistema debe permitir exportar las estadísticas en CSV, para uso periodístico y académico.

#### Escenario: Descarga del CSV

- **Cuando** se consulta `GET /api/estadisticas/exportar.csv`
- **Entonces** se descarga un CSV con las mismas cifras que muestra la pantalla

---

## Bitácora pública

*M8 · RF026–RF028*

Sostener el valor probatorio del proyecto: un registro cronológico de solo anexado, público y sin
autenticación, donde queda cada corte anunciado, cada confirmación ciudadana y cada
restablecimiento. Si un evento pudiera editarse, la bitácora no probaría nada. Cubre M8
(RF026–RF028).

### Requisito: Registro de solo anexado de todo evento relevante

El sistema debe registrar en la bitácora cada evento relevante: corte anunciado, confirmado por
ciudadanos y restablecido.

#### Escenario: Corte anunciado

- **Cuando** un veedor registra un corte oficial
- **Entonces** la bitácora suma un evento «corte anunciado» con su marca de tiempo y su sector

#### Escenario: Estado cambiado por consenso

- **Cuando** el consenso automático cambia el estado de un sector
- **Entonces** la bitácora suma el evento correspondiente

### Requisito: Consulta pública sin autenticación

La bitácora debe ser consultable públicamente, sin autenticación.

#### Escenario: Lectura anónima

- **Cuando** cualquiera consulta `GET /api/bitacora` sin token
- **Entonces** obtiene los eventos, paginados y en orden cronológico
- **Y** de cada evento de consenso ve cuántos reportes lo sustentaron (`cantidadReportesSustento`), no sus ids

#### Escenario: Filtro por barrio, tipo y fecha en todo el historial

- **Cuando** cualquiera consulta `GET /api/bitacora` con `sectorId`, `tipo`, `desde` o `hasta`
- **Entonces** obtiene solo los eventos que cumplen todos los filtros, buscados en todo el historial y no
  en la página ya cargada, con `desde` inclusivo y `hasta` exclusivo en UTC
- **Y** el enlace `Link` a la siguiente página conserva los filtros
- **Y** sin coincidencias recibe una página vacía; un tipo desconocido, una fecha mal formada o un `hasta`
  que no es posterior a `desde` responden 400

#### Escenario: Detalle de los reportes que sustentan un evento

- **Cuando** cualquiera consulta `GET /api/bitacora/{id}/sustento`
- **Entonces** obtiene los ids de los reportes que sostuvieron ese cambio, paginados (`ADR-055`)
- **Y** un evento inexistente responde 404 y una página fuera de rango, una lista vacía

### Requisito: Inmutabilidad de los eventos

Ningún evento de la bitácora debe poder editarse ni eliminarse una vez registrado. No existe
operación de escritura sobre un evento ya publicado.

#### Escenario: No hay forma de modificar un evento

- **Cuando** se busca una operación de actualización o borrado sobre un evento de bitácora
- **Entonces** no existe en la API ni en el puerto de salida del dominio

### Requisito: Vista pública alimentada solo por Acuacar, con el estado real de la fuente

La sección pública «Bitácora & Boletines Oficiales» debe mostrar exclusivamente las publicaciones que
devuelve Acuacar y debe decir cuál es el estado real de la fuente: consultando, con publicaciones, sin
publicaciones o no disponible. La vista permanece vacía antes que mostrar boletines de otra fuente o
eventos internos como si fueran oficiales.

#### Escenario: Acuacar responde con publicaciones

- **Cuando** la fuente devuelve publicaciones
- **Entonces** la sección las muestra de la más reciente a la más antigua, con el conteo de boletines

#### Escenario: Acuacar no responde

- **Cuando** la consulta a Acuacar falla
- **Entonces** la sección dice «Acuacar no está disponible» y ofrece reintentar
- **Y** no muestra datos alternativos

#### Escenario: Acuacar responde sin publicaciones

- **Cuando** la fuente responde con una lista vacía
- **Entonces** la sección dice «Acuacar no devolvió publicaciones»

---

## Ingesta automatizada

*M9 · RF029–RF031*

Traer al sistema lo que el operador y la prensa publican, sin que nadie tenga que copiarlo a mano, y
sin publicar nunca algo que no se pueda respaldar. La clasificación por IA se descartó (ADR-025); lo
que queda es una heurística que **propone** a una cola de revisión del veedor y no publica por su
cuenta (ADR-028), salvo el boletín oficial del propio operador. Cubre M9 (RF029–RF031); RF032–RF036
quedaron fuera de alcance.

### Requisito: Consumo periódico de la fuente oficial

El sistema debe consumir periódicamente la API oficial del operador y detectar publicaciones
nuevas o modificadas. debe recordar hasta dónde leyó cada fuente, en vez de mirar siempre los
últimos N días.

#### Escenario: Publicación nueva desde la última lectura

- **Cuando** el colector corre y la fuente tiene boletines posteriores a su última marca de lectura
- **Entonces** los ingiere y avanza la marca

#### Escenario: Ejecución sin novedades

- **Cuando** no hay publicaciones posteriores a la marca
- **Entonces** el colector no ingiere nada y no retrocede la marca

### Requisito: Consumo de prensa por RSS respetando robots.txt

El sistema debe consumir fuentes de prensa vía RSS de agregadores públicos, y no debe acceder a
fuentes cuyo `robots.txt` bloquee agentes de IA. El colector debe identificarse siempre con un
`User-Agent` que incluya el nombre del proyecto y un correo de contacto, y no debe disfrazarlo
(ADR-005).

#### Escenario: Fuente que bloquea agentes de IA

- **Cuando** el `robots.txt` de una fuente bloquea a los agentes de IA
- **Entonces** el sistema no la consume, aunque el bloqueo sea técnicamente evadible

#### Escenario: Identificación del colector

- **Cuando** el colector hace una petición saliente
- **Entonces** su `User-Agent` nombra al proyecto y da un correo de contacto

### Requisito: Descarte de duplicados por hash del contenido

El sistema debe descartar automáticamente el contenido duplicado mediante hash del contenido
normalizado.

#### Escenario: El mismo boletín republicado

- **Cuando** una fuente vuelve a publicar un contenido ya ingerido
- **Entonces** su hash coincide y no se crea un documento nuevo

### Requisito: Robustez ante fallo de una fuente

La caída de cualquier fuente externa no debe impedir que el resto del sistema funcione. Ante un
fallo, el sistema debe reintentar con retroceso exponencial y abrir un cortacircuitos tras 3
fallos consecutivos (RNF004, RNF005).

#### Escenario: Fuente caída

- **Cuando** una fuente falla tres veces seguidas
- **Entonces** su cortacircuitos se abre y deja de llamarla hasta que expire la espera
- **Y** las demás fuentes y el resto del sistema siguen funcionando

#### Escenario: Ningún documento se pierde en silencio

- **Cuando** el procesamiento de un documento falla
- **Entonces** el documento no se marca como visto (se reintenta el próximo ciclo) y queda anotado en
  la cola muerta con el motivo del fallo, consultable en `GET /api/veedor/ingesta/fallidos` (RNF006,
  `BUG-091`)
- **Y** si el mismo documento vuelve a fallar, su fila se actualiza con el reintento en vez de
  acumular una fila nueva por ciclo

#### Escenario: Un documento que estaba en la cola muerta se procesa con éxito

- **Cuando** un documento que había fallado antes se procesa sin errores en un ciclo posterior
- **Entonces** sale de la cola muerta

### Requisito: Salud observable de cada colector

El sistema debe exponer el estado de salud de cada colector: última ejecución exitosa, ítems
procesados y tasa de error (RNF007).

#### Escenario: Consulta de salud de la ingesta

- **Cuando** un veedor consulta `GET /api/veedor/ingesta/salud`
- **Entonces** obtiene, por colector, su última ejecución exitosa, sus ítems procesados y su tasa de
  error

### Requisito: La ingesta propone; publicar es decisión del veedor

Lo que la heurística deduce de una fuente de prensa debe entrar como propuesta a una cola de
revisión, y no debe cambiar el estado publicado de un sector por su cuenta (ADR-028). Los
boletines oficiales del propio operador son la excepción declarada: se publican sin revisión porque
su origen ya es la autoridad del dato.

#### Escenario: Propuesta desde una nota de prensa

- **Cuando** la heurística deduce un corte a partir de una nota de prensa
- **Entonces** crea una propuesta pendiente en `GET /api/veedor/ingesta/propuestas`
- **Y** el mapa público no cambia hasta que un veedor la apruebe

#### Escenario: Propuesta aprobada

- **Cuando** el veedor llama a `PATCH /api/veedor/ingesta/propuestas/{id}/aprobar`
- **Entonces** el cambio se publica y queda anotado en la bitácora

#### Escenario: Propuesta descartada

- **Cuando** el veedor llama a `PATCH /api/veedor/ingesta/propuestas/{id}/descartar`
- **Entonces** la propuesta se cierra sin publicar nada

### Requisito: Un sector tiene una sola transición de estado, sin importar cuántas fuentes opinen a la vez

Cuando más de una fuente propone un estado para el mismo sector al mismo tiempo —dos boletines
aprobados que se solapan, o un boletín de ingesta y un corte oficial del veedor abiertos a la vez—,
el sistema debe resolver un único resultado determinista, sin importar el orden en que se evalúen las
fuentes, y ese resultado nunca debe ser menos severo que el que exige un corte oficial todavía abierto
(`ADR-061`, `BUG-097`, `BUG-098`, `BUG-099`).

#### Escenario: Aprobar una propuesta con la ventana ya vencida

- **Cuando** el veedor aprueba una propuesta de prensa cuya ventana declarada ya terminó para ese
  momento (no para cuando la ingesta la detectó)
- **Entonces** el sector se fija en el estado que le corresponde ahora según esa ventana, no en el
  estado congelado al detectarla

#### Escenario: Boletines solapados sobre el mismo sector

- **Cuando** dos boletines aprobados con ventana vigente afectan al mismo sector a la vez (uno
  extiende el corte que el otro ya había anunciado)
- **Entonces** el barrido de ventanas aplica el estado más severo entre ambos
- **Y** el resultado es el mismo sin importar en qué orden se hayan evaluado

#### Escenario: Un corte oficial abierto no se rebaja por un aviso de ingesta vencido

- **Cuando** el veedor tiene un corte oficial registrado y todavía abierto sobre un sector, y un
  boletín de ingesta aprobado sobre ese mismo sector tiene la ventana ya vencida
- **Entonces** el barrido de ventanas no rebaja el sector por debajo de lo que ese corte exige

---

## API abierta Open311

*M12 · RF039*

Exponer los datos cívicos del proyecto bajo un estándar internacional, para que otra plataforma
pueda consumirlos sin acuerdo previo. Publica el estado agregado por sector, nunca el reporte
individual de un vecino: la interoperabilidad no puede costar la privacidad de quien reporta.
Cubre M12 (RF039).

### Requisito: Exposición Open311 del estado agregado

El sistema debe exponer los reportes confirmados y los cortes oficiales mediante una API que
cumpla el estándar Open311.

Lo publicado debe ser el estado agregado por sector, no cada reporte ciudadano individual
(ADR-026).

#### Escenario: Consumo por un tercero

- **Cuando** un tercero consulta `GET /api/v2/requests.json`
- **Entonces** obtiene, en el formato del estándar, el estado agregado por sector y los cortes
  oficiales

#### Escenario: Ningún dato de quien reporta

- **Cuando** se inspecciona cualquier respuesta de la API Open311
- **Entonces** no aparece la huella de dispositivo ni ningún dato que permita identificar a un
  reportante

---

## Telemetría IoT pasiva

*M13 · RF040*

Dejar que un sensor de presión casero —un ESP32 en la casa de un vecino— reporte solo, sin que
nadie tenga que abrir la aplicación. Un sensor no es un ciudadano: se autentica con su propia
clave y tiene su propio cupo, porque reporta cada pocos minutos por diseño. Es una **solución que se implementaría en físico**:
el endpoint está construido y probado, pero no hay sensores instalados. Cubre M13 (RF040).

### Requisito: Endpoint autenticado para sensores de presión

El sistema debe exponer un endpoint para recibir reportes automáticos de caída de presión desde
sensores IoT residenciales, autenticado con una clave propia del sensor.

#### Escenario: Sensor con clave válida

- **Cuando** un sensor envía `POST /api/iot/presion` con su cabecera `X-IoT-Key` válida
- **Entonces** el reporte se registra y cuenta para el consenso del sector

#### Escenario: Sensor sin clave o con clave inválida

- **Cuando** la petición llega sin `X-IoT-Key` o con una clave que no corresponde
- **Entonces** la API responde `401` en formato RFC 7807 y no registra nada

#### Escenario: Servidor sin clave de sensores configurada

- **Cuando** el servidor no tiene `IOT_KEY`
- **Entonces** responde `503` en RFC 7807, incluso a una petición que trae cabecera

### Requisito: Cupo propio del sensor, separado del ciudadano

El cupo de reportes de un sensor debe ser independiente del cupo por dispositivo ciudadano, y
debe existir: una clave filtrada o un sensor mal configurado no puede inundar el consenso.

#### Escenario: Sensor que reporta cada pocos minutos

- **Cuando** un sensor reporta con su cadencia normal
- **Entonces** no se autobloquea contra el cupo ciudadano

#### Escenario: Sensor desbocado

- **Cuando** un sensor supera su propio cupo dentro de la ventana
- **Entonces** sus reportes siguientes se rechazan hasta que la ventana se libere

---

## Alertas push

*M14 · RF041*

Ofrecer al vecino que su aviso llegue por Telegram en vez del correo. El bot recibe por sondeo (sin webhook, porque el
proyecto corre en local sin dominio, `ADR-066`) y **queda apagado hasta que exista `TELEGRAM_BOT_TOKEN`**. Está probado contra
un servidor HTTP falso y un Mongo real, **no contra Telegram real**. Cubre M14 (RF041).

### Requisito: Suscribirse por Telegram sin doble opt-in

El sistema debe permitir seguir uno o varios sectores escribiéndole al bot desde un chat privado, sin registro ni cuenta.
El propio mensaje es la confirmación: solo la persona puede escribir desde su chat.

#### Escenario: Seguir un sector

- **Cuando** una persona escribe `/suscribir Bocagrande` al bot
- **Entonces** el chat queda suscrito a ese sector, sin distinguir mayúsculas ni tildes
- **Y** el bot responde confirmando el sector

#### Escenario: Sector desconocido

- **Cuando** el nombre no corresponde a ningún sector
- **Entonces** no se guarda nada
- **Y** el bot sugiere hasta cinco sectores parecidos

#### Escenario: Límite de sectores por chat

- **Cuando** un chat ya sigue diez sectores y pide otro
- **Entonces** el bot lo rechaza y explica cómo liberar uno con `/baja`

### Requisito: La baja borra el dato personal

El sistema debe borrar el identificador del chat cuando deja de seguir el último sector o pide `/baja` sin sector (`RNF009`).

#### Escenario: Baja completa

- **Cuando** el chat escribe `/baja` o deja de seguir su último sector
- **Entonces** el registro del chat se elimina de la base

### Requisito: Aviso al cambiar el estado de un sector

El sistema debe avisar por Telegram a los chats que siguen un sector cuando este cambia de estado, sin que un chat que falla
impida avisar a los demás.

#### Escenario: Cambio de estado con suscriptores

- **Cuando** un sector cambia de estado
- **Entonces** cada chat que lo sigue recibe un mensaje con el nombre del sector y su estado en palabras
- **Y** un sector sin estado verificado se describe como «sin datos», nunca como «con servicio»

#### Escenario: El usuario bloqueó al bot

- **Cuando** Telegram responde que el usuario bloqueó al bot o que el chat ya no existe
- **Entonces** el chat se da de baja y su registro se borra

#### Escenario: Fallo pasajero de Telegram

- **Cuando** Telegram falla de forma pasajera (red, error 5xx)
- **Entonces** el chat conserva su suscripción y los demás chats reciben su aviso

### Requisito: Canal armado pero apagado sin token

El sistema debe arrancar y funcionar igual cuando falta `TELEGRAM_BOT_TOKEN`, sin enviar ni recibir nada por Telegram.

#### Escenario: Entorno sin token

- **Cuando** el backend arranca sin `TELEGRAM_BOT_TOKEN`
- **Entonces** registra en el log que Telegram está desactivado
- **Y** no sondea ni envía mensajes

---

## Cuentas y permisos del panel

*M15 · RF042–RF046*

Sustituir la credencial compartida del panel por cuentas individuales, para que se pueda responder
quién hizo qué y retirarle el acceso a una sola persona sin cambiarle la clave a las cinco. Amplía
RF019, que solo exigía «autenticación con token» y no decía nada del modelo de cuentas. Reemplaza a
ADR-016; la decisión y sus alternativas descartadas están en ADR-039. Cubre M15 (RF042–RF046) y los
RNF022–RNF025.

### Requisito: Alta por solicitud con verificación y aprobación

El sistema debe permitir que una persona solicite una cuenta del panel con su correo, y no debe
concederle ningún permiso hasta que verifique el correo y un administrador la apruebe.

#### Escenario: Solicitud recién creada

- **Cuando** alguien envía `POST /api/cuentas/registro` con su correo
- **Entonces** la cuenta queda en `PENDIENTE_VERIFICACION` y no puede entrar al panel

#### Escenario: Correo verificado, aprobación pendiente

- **Cuando** la persona verifica su correo por `POST /api/cuentas/verificacion`
- **Entonces** la cuenta pasa a `PENDIENTE_APROBACION` y sigue sin poder entrar al panel

#### Escenario: Aprobación del administrador

- **Cuando** un administrador llama a `PATCH /api/veedor/usuarios/{id}/aprobacion` con un rol
- **Entonces** la cuenta pasa a `ACTIVA` con los permisos base de ese rol

### Requisito: Alta por invitación con rol ya asignado

El sistema debe permitir a un administrador invitar a una persona por correo con un rol asignado.
Al fijar su clave desde el enlace, la cuenta debe quedar activa sin otra aprobación.

#### Escenario: Invitación aceptada

- **Cuando** el administrador crea la invitación por `POST /api/veedor/usuarios/invitaciones` y la
  persona fija su clave por `POST /api/cuentas/clave`
- **Entonces** la cuenta queda `ACTIVA` con el rol de la invitación, sin pasar por aprobación

### Requisito: Roles como paquetes de permisos, con ajustes por persona

El sistema debe ofrecer los roles `OBSERVADOR`, `VEEDOR` y `ADMIN` como paquetes de permisos con
nombre, y debe permitir conceder o revocar permisos sueltos a una persona sobre su rol.

La autorización de cada acción debe evaluarse contra un permiso concreto, nunca contra el nombre
del rol (RNF022).

#### Escenario: Observador intenta moderar

- **Cuando** una cuenta con rol `OBSERVADOR` llama a un endpoint de moderación
- **Entonces** la API responde 403, porque le falta el permiso `MODERAR_REPORTES`

#### Escenario: Permiso concedido por persona

- **Cuando** un administrador concede `REVISAR_INGESTA` a una cuenta `OBSERVADOR` por
  `PATCH /api/veedor/usuarios/{id}/permisos`
- **Entonces** esa cuenta puede revisar la ingesta sin cambiar de rol

### Requisito: Suspensión y cambio de permisos con efecto inmediato

Suspender una cuenta o cambiar sus permisos debe invalidar sus sesiones vivas de inmediato, sin
esperar a que expire el token (RNF023).

#### Escenario: Cuenta suspendida con sesión abierta

- **Cuando** un administrador suspende por `PATCH /api/veedor/usuarios/{id}/suspension` una cuenta
  con un token todavía vigente
- **Entonces** la siguiente petición de esa sesión se rechaza con 401

#### Escenario: Reactivación

- **Cuando** el administrador la reactiva por `PATCH /api/veedor/usuarios/{id}/reactivacion`
- **Entonces** la persona puede volver a iniciar sesión, con una sesión nueva

### Requisito: Siempre queda un administrador activo

El sistema debe rechazar cualquier suspensión o cambio de permisos que deje sin ningún `ADMIN`
activo, incluso si dos administradores intentan reducir el conteo a la vez sobre cuentas distintas
(`BUG-100`, `ADR-062`).

#### Escenario: Único administrador activo

- **Cuando** se intenta suspender o despromover al único `ADMIN` con sesión permitida
- **Entonces** la API rechaza la acción y ninguna cuenta cambia

#### Escenario: Dos cambios concurrentes sobre dos administradores distintos

- **Cuando** dos peticiones llegan a la vez, cada una suspendiendo o despromoviendo a un `ADMIN`
  activo distinto, y solo quedan dos
- **Entonces** una se aplica y la otra se rechaza — nunca las dos a la vez

### Requisito: Segundo factor TOTP obligatorio para ADMIN

Las cuentas con rol `ADMIN` deben exigir un segundo factor TOTP conforme al RFC 6238 (RNF025). La
cuenta que puede crear y despromover cuentas es la que más daño hace si se la roban.

Un token emitido a un `ADMIN` que aún no ha activado su segundo factor debe tener alcance
restringido: sirve para activarlo y para nada más.

#### Escenario: Primera sesión de un administrador sembrado

- **Cuando** el `ADMIN` inicial inicia sesión y todavía no tiene TOTP activo
- **Entonces** su token solo le permite dar de alta el segundo factor por
  `POST /api/veedor/segundo-factor/alta` y confirmarlo

#### Escenario: Código TOTP inválido

- **Cuando** se presenta un código que no corresponde a la ventana de tiempo vigente
- **Entonces** la confirmación se rechaza y el segundo factor no queda activo

### Requisito: Bitácora de auditoría del acceso

El sistema debe registrar en una bitácora inmutable quién cambió el acceso de quién, cuándo y
desde qué IP.

#### Escenario: Consulta de la auditoría

- **Cuando** una cuenta con permiso `VER_AUDITORIA` consulta `GET /api/veedor/auditoria`
- **Entonces** obtiene los cambios de acceso con su autor, su destinatario, su instante y su IP de
  origen

### Requisito: Cambio de clave con la sesión iniciada

Una persona con sesión debe poder cambiar su propia clave sin pasar por el correo, sin que un token robado
baste para hacerlo.

#### Escenario: Cambio correcto

- **Cuando** una sesión completa envía su clave actual y una clave nueva válida y distinta a `POST /api/veedor/cuenta/clave`
- **Entonces** la clave cambia, se cierran **todas** las sesiones de la cuenta (la actual incluida) y se avisa por correo

#### Escenario: Clave actual incorrecta

- **Cuando** la clave actual no coincide
- **Entonces** responde 400, la clave no cambia y el intento cuenta para el bloqueo por intentos fallidos (compartido con el inicio de sesión)

#### Escenario: Cuenta bloqueada

- **Cuando** la cuenta está bloqueada por intentos fallidos
- **Entonces** responde 423 sin comprobar la clave

### Requisito: Reenvío de los enlaces de cuenta

Quien no recibió un enlace de verificación o de invitación debe poder pedirlo de nuevo sin volver a registrarse.

#### Escenario: Reenvío de la verificación

- **Cuando** alguien pide `POST /api/cuentas/verificacion/reenvio` con el correo de una cuenta pendiente de verificar
- **Entonces** recibe un enlace nuevo y el anterior deja de servir
- **Y** la respuesta es 202 exista o no la cuenta, y solo se reenvía una vez cada 2 minutos por cuenta

#### Escenario: Reenvío de la invitación

- **Cuando** un administrador pide `POST /api/veedor/usuarios/{id}/invitacion/reenvio` de una cuenta `INVITADA`
- **Entonces** la persona recibe una invitación nueva y la anterior deja de servir
- **Y** una cuenta que ya aceptó la invitación responde 409, y una inexistente 404

### Requisito: Restablecimiento de clave por enlace de un solo uso

El sistema debe permitir restablecer la clave mediante un enlace de un solo uso enviado al correo,
y ese cambio debe cerrar todas las sesiones abiertas de la cuenta.

#### Escenario: Enlace usado dos veces

- **Cuando** se intenta reutilizar un enlace de restablecimiento ya consumido
- **Entonces** la operación se rechaza

#### Escenario: Sesiones abiertas tras el cambio

- **Cuando** la persona fija su clave nueva
- **Entonces** todas sus sesiones previas quedan invalidadas

### Requisito: Las respuestas no revelan qué correos tienen cuenta

El registro, el ingreso y el restablecimiento de clave no deben revelar qué correos tienen cuenta,
ni por el mensaje ni por el tiempo de respuesta (RNF024).

#### Escenario: Restablecimiento sobre un correo desconocido

- **Cuando** se pide restablecer la clave de un correo que no tiene cuenta
- **Entonces** la respuesta es indistinguible —en cuerpo y en tiempo— de la de un correo que sí la
  tiene

#### Escenario: Ingreso con correo inexistente

- **Cuando** se intenta iniciar sesión con un correo sin cuenta
- **Entonces** el error es el mismo que el de una clave equivocada, y tarda lo mismo
