# Anexo 4 — Historias de usuario

---

## Ficha técnica

| Campo | Detalle |
|---|---|
| Artefacto | Historias de usuario en formato Gherkin (`Dado` / `Cuando` / `Entonces`) |
| Trazabilidad | **Una historia por cada requisito funcional**, numeración pareja `RF0NN → HU0NN` (ver [`../ingenieria/matriz-trazabilidad.md`](../ingenieria/matriz-trazabilidad.md)) |
| Actor | El de `docs/product-requirements.md`: vecino, veedor, ciudadanía, periodista, sistema, administrador, aspirante a veedor o sensor IoT |
| Alcance vigente | Proyecto académico que corre **solo en local** (`ADR-057`, `ADR-080`): sin hosting, dominio, CDN ni TLS. **Sin SDK de IA** (`ADR-025`): la ingesta la hace una heurística determinista. Interfaz: el frontend nuevo de `frontend/` (React 19 + Vite, `ADR-067`) |
| Estado de la interfaz | Construidas F2 (núcleo ciudadano), F3 (historia pública) y F4 (avisos); **F5 (cuentas y panel del veedor; avance en la rama `feat/f5-ingreso-panel`, sin fusionar) y F6 (integración) siguen pendientes** (`docs/gestion/sprint-7.md`). Hasta entonces, las historias del panel (HU016–HU019) y de las cuentas (HU042–HU046) se ejercen por la API, con Swagger (`http://localhost:8081/swagger-ui.html`) |
| Estado de cada requisito | **No se repite aquí**: vive en la matriz de trazabilidad (un dato, un archivo). Solo se anota en la historia cuando el requisito no se cumple tal como está escrito (HU032–HU036, HU041) |
| Estado de este documento | Cubre RF001–RF046 (M1–M15, incluida la Fase 2 de `product-requirements.md` §5). Revisado el 2026-09-29 contra `product-requirements.md`, la matriz y `comportamiento-del-sistema.md`. Se actualiza si cambia un requisito, no aparte |

**Regla de trazabilidad:** una historia sin requisito, o un requisito sin historia, es un hueco. Por eso este anexo y
`matriz-trazabilidad.md` se actualizan juntos.

**Convención de escenarios:** el `Entonces` describe el comportamiento esperado según el requisito, no una
implementación particular. Donde el requisito es prohibitivo (RF028, RF034, RF036), el escenario describe lo que **no**
debe ocurrir y cómo el sistema lo garantiza. Donde el requisito pedía IA (RF032–RF036), el escenario describe lo que hace
la heurística determinista y una nota dice qué parte del requisito no se cumple.

---

## M1 — Mapa en vivo

### HU001 — Mapa con sectores coloreados por estado *(RF001)*

- **Como** vecino de Cartagena
- **Quiero** ver el mapa de la ciudad con cada sector coloreado según su estado
- **Para** ver de un vistazo si mi sector tiene servicio

```gherkin
Dado un mapa de Cartagena cargado con la información de los sectores
Cuando el vecino abre la plataforma
Entonces el mapa muestra todos los sectores de Cartagena
Y cada sector aparece coloreado según su estado actual (con servicio, sin servicio, presión baja, corte programado)
Y un sector del que ninguna fuente verificada ha dicho nada aparece como «sin datos verificados», nunca como con servicio
Y el estado no se comunica solo por color (_RNF016_)
```

### HU002 — Detalle de un sector *(RF002)*

- **Como** vecino
- **Quiero** seleccionar un sector y ver su detalle
- **Para** conocer su estado, el último cambio y su histórico de cortes

```gherkin
Dado que el mapa muestra los sectores de Cartagena
Cuando el vecino selecciona un sector
Entonces se muestra el detalle del sector
Y ese detalle incluye el estado actual, el último cambio y el histórico de cortes
```

### HU003 — Antigüedad de la información *(RF003)*

- **Como** vecino
- **Quiero** ver cuánto tiempo hace que se actualizó el estado de cada sector
- **Para** no confundir un registro viejo con uno reciente

```gherkin
Dado que el mapa muestra un sector con su estado
Cuando el vecino lo consulta
Entonces el sistema muestra, junto al sector, cuánto tiempo hace que se actualizó su información
Y si el estado lleva más de 24 horas sin verificarse, lo advierte con «Sin verificación reciente» sin cambiar el estado publicado
```

### HU004 — Lista textual accesible *(RF004)*

- **Como** vecino que usa lector de pantalla o prefiere texto
- **Quiero** consultar los sectores y sus estados como lista textual
- **Para** usar la plataforma sin depender del mapa

```gherkin
Dado que el mapa de Cartagena está disponible
Cuando el vecino elige la lista textual o navega con el lector de pantalla
Entonces encuentra una lista accesible de todos los sectores con su estado actual
```

---

## M2 — Reporte ciudadano

### HU005 — Reporte sin registro ni cuenta *(RF005)*

- **Como** vecino
- **Quiero** reportar "no tengo agua", "presión baja" o "ya volvió el servicio" sin registrarme ni iniciar sesión
- **Para** reportar en el momento, sin barreras

```gherkin
Dado que el vecino abre la plataforma sin estar registrado ni autenticado
Cuando elige "no tengo agua", "presión baja" o "ya volvió el servicio"
Entonces su reporte se registra
Y el sistema no le pide en ningún momento registro, cuenta, ni datos personales identificables
```

### HU006 — Límite de reportes por dispositivo *(RF006)*

- **Como** el sistema
- **Quiero** limitar los reportes que puede enviar cada dispositivo en una ventana de tiempo
- **Para** contener el abuso sin pedir registro

```gherkin
Dado que un dispositivo ya envió la cantidad máxima de reportes permitida en la ventana de tiempo vigente
Cuando ese dispositivo vuelve a intentar un reporte
Entonces el sistema rechaza el reporte y no lo guarda
Y explica el límite y el momento en que podrá volver a reportar
```

### HU007 — Coordenada e inferencia de sector *(RF007)*

- **Como** vecino
- **Quiero** autorizar mi ubicación al reportar
- **Para** que se registre la coordenada y se infiera el sector correcto

```gherkin
Dado que el vecino está reportando un incidente
Cuando autoriza que el sistema lea su ubicación
Entonces el sistema registra la coordenada del reporte
Y a partir de ella infiere el sector donde se encuentra el evento
Y si el vecino no autoriza la ubicación, el reporte usa el sector que tenía abierto y el flujo continúa sin error
```

### HU008 — Reporte en dos toques *(RF008)*

- **Como** vecino
- **Quiero** reportar en pocos pasos desde el mapa
- **Para** responder rápido mientras no tengo agua

```gherkin
Dado que el vecino está viendo el mapa
Cuando toca «Reportar que no tengo agua» y confirma el tipo de reporte
Entonces el reporte queda enviado en no más de dos toques desde el mapa
Y sin campos opcionales ni captcha visible
```

---

## M3 — Consenso automático

### HU009 — Cambio de estado por consenso *(RF009)*

- **Como** el sistema
- **Quiero** cambiar el estado de un sector cuando N reportes coinciden dentro de una ventana
- **Para** cambiar el mapa solo con varias voces, no con la de una sola persona

```gherkin
Dado que hay reportes recientes guardados de un sector
Y la ventana de tiempo está configurada
Cuando llegan N reportes independientes que coinciden dentro de la misma ventana
Entonces el sistema actualiza el estado del sector automáticamente
Y los reportes que no alcanzan el umbral no cambian el estado
```

### HU010 — Estrategias de consenso intercambiables *(RF010)*

- **Como** el sistema
- **Quiero** conmutar entre estrategias de consenso
- **Para** usar hoy un umbral fijo y mañana un umbral proporcional a la población del sector

```gherkin
Dado que la estrategia de consenso está configurada como umbral fijo o proporcional a la población
Cuando el sistema evalúa un cambio de estado
Entonces aplica la estrategia de consenso activa
Y el cambio de estrategia no modifica el resto del flujo de consenso
```

### HU011 — Reportes que sustentaron el cambio *(RF011)*

- **Como** veedor
- **Quiero** que el sistema registre los reportes que sustentaron cada cambio de estado
- **Para** poder revisar la causa de cualquier cambio

```gherkin
Dado que un sector cambió de estado por consenso
Cuando el veedor consulta ese cambio
Entonces el sistema muestra cuántos reportes lo sustentaron
Y permite consultar cuáles fueron
```

---

## M4 — Alertas por correo

### HU012 — Suscripción a sectores solo con correo *(RF012)*

- **Como** vecino
- **Quiero** suscribirme a uno o varios sectores con solo mi correo
- **Para** recibir avisos de mi barrio sin registrarme

```gherkin
Dado que el vecino quiere recibir avisos de un sector
Cuando indica un correo electrónico y elige los sectores
Entonces la suscripción queda creada, pendiente de confirmación
Y no se le envía ningún aviso todavía
```

### HU013 — Doble opt-in *(RF013)*

- **Como** el sistema
- **Quiero** confirmar una suscripción antes de enviar cualquier aviso
- **Para** cumplir la Ley 1581/2012 y no enviar correos no verificados

```gherkin
Dado que alguien solicita la suscripción con un correo
Cuando el sistema envía el correo de confirmación con un enlace
Y el dueño del correo abre el enlace
Entonces ve una página con un botón y la suscripción todavía no cambia (un antivirus puede abrir el enlace)
Cuando pulsa el botón
Entonces la suscripción queda confirmada
Y no se envió ningún aviso antes de esa confirmación
```

### HU014 — Notificación de cambio de estado *(RF014)*

- **Como** vecino suscrito
- **Quiero** que me avisen cuando mi sector cambia de estado
- **Para** enterarme aunque el boletín no llegue

```gherkin
Dado que el vecino tiene una suscripción confirmada a un sector
Cuando el estado de ese sector pasa a corte anunciado, corte confirmado o restablecimiento
Entonces el sistema envía un correo al suscriptor
Y si el envío falla, el cambio de estado se publica igual
```

### HU015 — Baja en un clic *(RF015)*

- **Como** vecino suscrito
- **Quiero** darme de baja de los avisos con un enlace
- **Para** que los avisos cesen cuando no los quiero más, sin credenciales

```gherkin
Dado que un suscriptor desea dejar de recibir avisos
Y que todo correo que recibió incluye un enlace de baja
Cuando abre el enlace y pulsa «Dejar de recibir avisos» (sin credenciales)
Entonces el sistema cancela la suscripción
Y su correo deja de estar almacenado (_RNF009_)
Y deja de enviarle correos
```

---

## M5 — Panel del veedor

> Interfaz del panel: pendiente (F5, `docs/gestion/sprint-7.md`). Hoy estas historias se ejercen por la API con el token
> de `POST /api/veedor/sesion`.

### HU016 — Registrar corte oficial *(RF016)*

- **Como** veedor autenticado
- **Quiero** registrar un corte oficial con sus sectores, inicio, fin prometido y causa
- **Para** que la comunidad tenga la versión oficial del corte

```gherkin
Dado que el veedor está autenticado
Cuando registra un corte oficial indicando sectores afectados, inicio, fin prometido y causa
Entonces el corte se almacena y su información se publica en el mapa
Y se anota el evento «corte anunciado» en la bitácora pública
Y si el fin prometido es anterior al inicio, el sistema lo rechaza con un error 400
```

### HU017 — Cerrar corte con hora real *(RF017)*

- **Como** veedor autenticado
- **Quiero** cerrar los cortes registrando la hora real de restablecimiento
- **Para** que sirva de insumo al Índice de Cumplimiento

```gherkin
Dado que existe un corte oficial abierto
Cuando el veedor lo cierra registrando la hora real de restablecimiento
Entonces el corte queda cerrado y la hora real queda guardada en el historial
Y su desviación entre lo prometido y lo real pasa a estar disponible
```

### HU018 — Moderar reportes dudosos *(RF018)*

- **Como** veedor autenticado
- **Quiero** aprobar o descartar reportes ciudadanos que nadie ha moderado
- **Para** controlar la calidad de los datos que se ven en el mapa

```gherkin
Dado que hay reportes ciudadanos sin moderar
Cuando el veedor aprueba o descarta uno
Entonces el reporte queda con la decisión del veedor
Y si fue aprobado, cuenta para el consenso; si fue descartado, queda fuera de la publicación
```

### HU019 — Panel protegido con token *(RF019)*

- **Como** el sistema
- **Quiero** restringir el acceso al panel del veedor a quien esté autenticado con un token
- **Para** que la moderación sea cosa de personas autorizadas y el resto siga público

```gherkin
Dado que una persona no está autenticada, o su token tiene más de 8 horas (_RNF011_)
Cuando intenta acceder a cualquier ruta bajo `/api/veedor/` (salvo el inicio de sesión)
Entonces el sistema la rechaza con un error 401
Y el resto de la plataforma permanece público
```

---

## M6 — Índice de Cumplimiento ⭐

### HU020 — Desviación prometido vs real *(RF020)*

- **Como** el sistema
- **Quiero** calcular la desviación entre duración prometida y real de cada corte cerrado con hora prometida
- **Para** medir de verdad el cumplimiento

```gherkin
Dado que un corte cerrado tiene hora prometida y hora real
Cuando se consulta su cumplimiento
Entonces el sistema expone la duración prometida, la duración real y su desviación
Y un corte cerrado sin hora prometida no aporta al índice, en vez de contarse como cumplido
```

### HU021 — Índice por sector y global *(RF021)*

- **Como** ciudadano
- **Quiero** consultar un índice de cumplimiento por sector y uno global de la ciudad
- **Para** comparar la promesa contra la realidad

```gherkin
Dado que hay cortes cerrados con los datos necesarios para el cálculo
Cuando la ciudadanía consulta el índice
Entonces encuentra el índice por cada sector y un índice global de Cartagena
Y si todavía no hay nada medido, el sistema lo dice en vez de mostrar un 100 %
```

### HU022 — Presentación como comparación *(RF022)*

- **Como** ciudadano
- **Quiero** ver lo prometido y lo real, no un puntaje aislado
- **Para** entender de verdad el nivel de cumplimiento («Prometieron 2 horas · Fueron 8»)

```gherkin
Dado que la ciudadanía consulta el Índice
Entonces el sistema presenta la desviación como comparación explícita entre prometido y real
Y no como un simple puntaje
```

---

## M7 — Estadísticas

### HU023 — Sectores más afectados, duración y frecuencia *(RF023)*

- **Como** veedor o periodista
- **Quiero** ver los sectores más afectados, la duración promedio de los cortes y su frecuencia mensual
- **Para** sustentar una denuncia con evidencia acumulada y no con una anécdota

```gherkin
Dado que el sistema registra los cortes y su duración
Cuando un veedor o periodista consulta las estadísticas
Entonces el sistema muestra los sectores más afectados, la duración promedio de los cortes y su frecuencia mensual
Y donde todavía no hay datos lo dice, en vez de mostrar un cero
```

### HU024 — Evolución del índice *(RF024)*

- **Como** veedor
- **Quiero** ver cómo evoluciona el índice de cumplimiento mes a mes
- **Para** saber si el operador mejora o empeora con el tiempo

```gherkin
Dado que existe histórico del índice de cumplimiento
Cuando el veedor consulta la evolución
Entonces el sistema muestra cómo cambió el índice a lo largo del tiempo, mes a mes
```

### HU025 — Exportación CSV *(RF025)*

- **Como** periodista
- **Quiero** descargar las estadísticas y la serie del índice en CSV
- **Para** analizarlas con mis propias herramientas y citar las cifras

```gherkin
Dado que el periodista está mirando las estadísticas
Cuando solicita la descarga
Entonces el sistema exporta las estadísticas y la serie del índice en formato CSV abierto
Y las cifras del archivo son las mismas que muestra la pantalla
```

---

## M8 — Bitácora pública

### HU026 — Bitácora de solo anexado *(RF026)*

- **Como** el sistema
- **Quiero** registrar cada evento relevante (corte anunciado, confirmado, restablecido) en una bitácora
- **Para** que todo quede documentado de forma ordenada

```gherkin
Dado que ocurre un evento relevante (corte anunciado, confirmado, restablecido)
Entonces el sistema lo registra en la bitácora de solo anexado (append-only)
```

### HU027 — Bitácora pública *(RF027)*

- **Como** ciudadano
- **Quiero** consultar la bitácora sin registrarme ni autenticarme
- **Para** verificar por mí mismo qué se anunció y qué ocurrió

```gherkin
Dado un ciudadano sin sesión
Cuando consulta la bitácora
Entonces obtiene los eventos, paginados, sin registrarse ni autenticarse
Y puede filtrarlos por barrio, tipo y fecha en todo el historial
```

### HU028 — Eventos inmutables *(RF028)*

- **Como** el sistema
- **Quiero** que ningún evento de la bitácora pueda editarse ni eliminarse
- **Para** que la bitácora conserve su valor probatorio

```gherkin
Dado que un evento ya está en la bitácora
Cuando alguien busca editar o eliminar ese evento
Entonces no existe ninguna operación que lo permita, ni en la API ni en el puerto de salida del dominio
Y el registro original permanece íntegro
```

---

## M9 — Ingesta automática (heurística determinista)

> **Alcance real de M9.** El requisito nació como «Ingesta automática con IA», pero el SDK de IA se descartó (`ADR-025`):
> la ingesta usa un **prefiltro determinista** (9 palabras clave) y un **extractor por expresiones regulares**
> (`HeuristicaExtractor`) que emite una confianza graduada por la evidencia y la cita textual del boletín, y **propone**
> a una cola de revisión del veedor (`ADR-028`). Solo el boletín oficial de Acuacar se publica sin revisión (`ADR-034`).
> La matriz los da reformulados sin IA: RF033 y RF035 ✅, RF032, RF034 y RF036 🟡 (`matriz-trazabilidad.md` §M9); las
> notas de cada historia dicen qué parte se cumple de forma heurística y cuál no.

### HU029 — Consumo periódico de la API oficial *(RF029)*

- **Como** el sistema
- **Quiero** consumir periódicamente la API oficial del operador y detectar publicaciones nuevas o modificadas
- **Para** traer los avisos de Acuacar sin que nadie los copie a mano

```gherkin
Dado que está configurada la API oficial del operador
Cuando el sistema ejecuta la ingesta programada
Entonces consume la API y detecta publicaciones nuevas o modificadas
Y recuerda hasta dónde leyó, sin retroceder cuando no hay novedades

Dado que la ingesta está en modo local (`INGESTA_MODO=local`, ADR-082)
Cuando el sistema ejecuta la ingesta programada
Entonces lee boletines reales de Acuacar guardados en el repositorio, sin hacer ninguna petición a la red
Y siguen el mismo camino que un boletín en vivo
```

### HU030 — Fuentes de prensa vía RSS *(RF030)*

- **Como** el sistema
- **Quiero** consumir fuentes de prensa vía RSS de agregadores públicos
- **Para** enterarme de cortes que la prensa cubre y el operador no anuncia

```gherkin
Dado que el sistema tiene habilitado un feed de prensa cuyo robots.txt permite el acceso
Cuando ejecuta la ingesta de prensa
Entonces lo consume con un User-Agent que nombra al proyecto y da un correo de contacto
Y lo que deduce de la prensa entra como propuesta a la cola del veedor, no al mapa
```

### HU031 — Descarte de duplicados *(RF031)*

- **Como** el sistema
- **Quiero** descartar el contenido duplicado mediante el hash del contenido normalizado
- **Para** que un mismo boletín republicado no genere propuestas repetidas

```gherkin
Dado un documento normalizado que ya existe en el sistema
Cuando la ingesta encuentra el mismo contenido
Entonces su hash coincide y el sistema no crea un documento ni una propuesta nueva
```

### HU032 — Clasificación y extracción *(RF032)*

- **Como** el sistema
- **Quiero** decidir si un documento habla de una interrupción del acueducto y extraer sectores, fechas, horas y causa
- **Para** que el veedor revise datos estructurados y no textos sueltos

```gherkin
Dado un documento crudo limpio y no duplicado
Cuando el prefiltro determinista y el extractor lo procesan
Entonces el prefiltro lo descarta si no contiene ninguna de sus palabras clave
Y si pasa, el extractor decide que habla de una interrupción solo si nombra al menos un barrio y menciona suspensión, presión baja o restablecimiento
Y extrae los barrios tal como los escribió la fuente, la ventana prometida (inicio y fin), la causa y el tipo de evento
Y lo que no logra leer lo declara como campo faltante, sin inventarlo
```

> **Nota de alcance.** RF032 pide hacerlo «mediante IA con salida estructurada». Eso **no se cumple** (`ADR-025`; la matriz
> lo marca 🟡 parcial). Se cumple la capacidad de clasificar y extraer con una heurística determinista, más tosca que
> un modelo: sin clasificación semántica y con reglas que asumen la plantilla de los boletines de Acuacar.

### HU033 — Confianza y cita textual *(RF033)*

- **Como** el sistema
- **Quiero** que toda extracción lleve un puntaje de confianza y la cita textual del fragmento que la sustenta
- **Para** que el veedor decida con la evidencia a la vista

```gherkin
Dado que el extractor produce una extracción
Entonces el resultado incluye un puntaje de confianza graduado por la evidencia que encontró (0,85 con enumeración de barrios y horario, 0,75 con enumeración sin horario, 0,45 con una mención suelta en prosa)
Y incluye la cita textual: un fragmento del boletín (hasta 300 caracteres) donde consta qué pasa, cuándo y en qué barrios
Y ambos quedan guardados en la propuesta que ve el veedor
```

> **Nota de alcance.** La cita textual se cumple tal como está escrito el requisito. La «confianza» **no** es la
> probabilidad de un modelo: es una graduación por reglas, útil para ordenar la cola del veedor. El extractor, no «la IA»,
> es quien debe citar la frase del boletín (`ADR-006`, `ADR-028`).

### HU034 — Rechazo de citas no literales *(RF034)*

- **Como** el sistema
- **Quiero** que ninguna cita pueda venir de fuera del documento origen
- **Para** que el veedor siempre pueda contrastar la cita con el boletín

```gherkin
Dado que el extractor arma la cita de una extracción
Cuando la toma del texto del documento
Entonces la cita es un fragmento literal del documento (salvo los «…» que marcan un recorte)
Y el extractor nunca redacta texto propio
```

> **Nota de alcance.** RF034 pide **rechazar automáticamente**, al ejecutar, toda cita que no aparezca literal en el
> documento. Ese verificador **no existe**: sin un modelo que pueda inventar citas, la literalidad se garantiza por
> construcción (la cita se recorta del propio texto) y se fija con una prueba automatizada del extractor. El
> mecanismo de rechazo como tal no se implementó.

### HU035 — Revisión humana antes de publicar *(RF035)*

- **Como** veedor
- **Quiero** revisar en una cola lo que la ingesta deduce de la prensa antes de que cambie el mapa
- **Para** que un corte mal leído no llegue al público

```gherkin
Dado una propuesta que la ingesta dedujo de una fuente de prensa
Entonces queda pendiente en la cola de revisión del veedor y el mapa público no cambia
Cuando el veedor la aprueba
Entonces el cambio se publica y queda anotado en la bitácora
Cuando el veedor la descarta
Entonces la propuesta se cierra sin publicar nada
Y una propuesta ya resuelta no se resuelve al revés (el sistema responde 409)
```

> **Nota de alcance.** RF035 habla de «confianza intermedia». No hay una banda intermedia: **toda** propuesta de prensa va a
> la cola, sea cual sea su confianza, que solo sirve para ordenarla. La excepción declarada es el boletín oficial de
> Acuacar, que se publica sin revisión porque su origen ya es la autoridad del dato (`ADR-034`).

### HU036 — No usar fuentes que bloquean a los agentes de IA *(RF036)*

- **Como** el sistema
- **Quiero** no acceder a fuentes cuyo `robots.txt` bloquee agentes de IA
- **Para** que el proyecto sea coherente con su tesis: no disfrazar el origen del colector ni evadir un bloqueo

```gherkin
Dado que se quiere incorporar una fuente al pipeline
Cuando se verifica su robots.txt con una petición real antes de agregarla
Y la fuente bloquea a los agentes de IA
Entonces el sistema NO la usa y la deja fuera de los colectores
Y su cobertura llega, si llega, de forma indirecta por Google News
```

> **Nota de alcance.** La regla se cumple **por curación de las fuentes** (auditoría con petición real,
> `docs/ingenieria/auditoria-fuentes-de-datos.md`), y sigue siendo de obligado cumplimiento (`ADR-005`, `CLAUDE.md`
> § Ética de datos) aunque el proyecto no use IA. **No hay** en el backend un lector de `robots.txt` que lo consulte al
> ejecutar; la matriz marca el RF como 🟡 parcial (política cumplida por auditoría, no por código).

---

## M10 — Evidencia Multimedia (Fase 2)

### HU037 — Adjuntar fotografía a un reporte *(RF037)*

- **Como** vecino
- **Quiero** adjuntar una fotografía a mi reporte (por ejemplo, un tubo roto)
- **Para** que la evidencia visual respalde lo que estoy reportando

```gherkin
Dado que el vecino envió un reporte desde el mapa
Cuando adjunta una fotografía a ese reporte
Entonces el sistema almacena la evidencia comprimida y sin metadatos EXIF, para que no delate su ubicación (_RNF021_)
Y la asocia al reporte enviado
Y si la imagen supera el tamaño máximo, responde con un error 413 y no con un 500
```

> El almacenamiento es el volumen local `fotos-data` (`ADR-080`): no hay bucket. Se procesan `.jpg` y `.png`.

---

## M11 — Validación Comunitaria Rápida (Fase 2)

### HU038 — Confirmar un reporte con un clic *(RF038)*

- **Como** vecino
- **Quiero** confirmar con un solo clic un reporte reciente cercano a mí
- **Para** sumar mi voz sin llenar un formulario

```gherkin
Dado que el vecino abrió un reporte reciente («¿Tú también estás sin agua?»)
Cuando pulsa «Confirmar este reporte»
Entonces el sistema registra su confirmación y el contador de confirmaciones de ese reporte aumenta
Y el mismo dispositivo, o el que envió el reporte, no suma dos veces
Y la confirmación no entra al consenso (_BUG-114_): solo los reportes originales cuentan
```

---

## M12 — API Abierta Open311 (Fase 2)

### HU039 — Exponer el estado bajo el estándar Open311 *(RF039)*

- **Como** el sistema
- **Quiero** exponer los reportes confirmados y los cortes oficiales bajo el estándar Open311
- **Para** que otras plataformas cívicas los consuman sin inventar un formato propio

```gherkin
Dado que existen sectores afectados y cortes oficiales
Cuando un sistema externo consulta `GET /api/v2/requests.json`
Entonces recibe la información en el formato del estándar
Y la respuesta agrega el estado por sector, sin exponer la coordenada individual de cada reporte ni la huella del dispositivo (_RNF008_, `ADR-026`)
```

---

## M13 — Integración IoT Pasiva (Fase 2)

### HU040 — Alerta automática desde un sensor IoT *(RF040)*

- **Como** sensor IoT residencial
- **Quiero** enviar mi lectura de presión al sistema con mi clave de API
- **Para** que se genere un reporte automático si la presión cae por debajo de lo normal

```gherkin
Dado un sensor IoT con una clave de API válida (cabecera `X-IoT-Key`)
Cuando envía una lectura de presión por debajo del umbral configurado (15 psi por defecto)
Entonces el sistema genera automáticamente un reporte de presión baja
Y lo asocia al sector correspondiente, con un cupo propio separado del de los vecinos

Dado un sensor sin clave o con una clave inválida
Cuando envía una lectura
Entonces el sistema responde 401 y no registra nada
```

> Es una **solución que se implementaría en físico**: el endpoint está construido y probado, pero no hay sensores
> instalados. Sin `IOT_KEY` en el servidor, responde 503.

---

## M14 — Alertas Push Instantáneas (Fase 2)

### HU041 — Suscripción por Telegram *(RF041)*

- **Como** vecino
- **Quiero** seguir los sectores de mi barrio escribiéndole a un bot de Telegram
- **Para** recibir el aviso donde ya reviso mis mensajes, sin depender del correo

```gherkin
Dado que el bot de Telegram está conectado (el servidor tiene `TELEGRAM_BOT_TOKEN`)
Cuando el vecino le escribe `/suscribir Bocagrande` desde un chat privado
Entonces el chat queda suscrito a ese sector y el bot lo confirma
Cuando ese sector cambia de estado
Entonces el chat recibe un mensaje con el nombre del sector y su estado en palabras
Cuando el vecino escribe `/baja`
Entonces se borra el identificador de su chat de la base

Dado que el servidor no tiene `TELEGRAM_BOT_TOKEN`
Cuando arranca
Entonces el canal queda apagado, no sondea ni envía nada, y el resto de la plataforma funciona igual
```

> **Nota de alcance.** Telegram está **construido y armado, pero apagado** hasta que exista el token (`ADR-066`,
> `docs/ingenieria/telegram.md`) y **no se ha probado contra Telegram real**, solo contra un servidor HTTP falso y un
> Mongo real (la matriz lo marca 🟡). WhatsApp queda fuera. Sin doble opt-in: el mensaje al bot es la confirmación.

---

## M15 — Cuentas y permisos del panel

> Amplía RF019 (M5). Decisión y alternativas descartadas en `ADR-039`, que reemplaza a `ADR-016`. El barrio de la cuenta
> es un dato opcional (`ADR-081`). Comportamiento vivo en `docs/ingenieria/comportamiento-del-sistema.md`. Interfaz:
> pendiente (F5); hoy por la API.

### HU042 — Solicitar cuenta y esperar verificación y aprobación *(RF042)*

- **Como** aspirante a veedor
- **Quiero** solicitar una cuenta del panel indicando mi correo y una clave, y opcionalmente mi barrio
- **Para** poder acceder al panel una vez que confirme mi correo y un administrador verifique que debo tener acceso

```gherkin
Dado que una persona sin cuenta quiere acceder al panel
Cuando envía su correo, su nombre y una clave a `POST /api/cuentas/registro` (con el `barrioId` de su barrio, si quiere)
Entonces la cuenta queda en estado "pendiente de verificación" y no puede iniciar sesión
Y si el barrio no existe, el sistema responde 400 y no crea la cuenta
Cuando confirma el enlace de verificación enviado a su correo (`POST /api/cuentas/verificacion`)
Entonces la cuenta pasa a "pendiente de aprobación" y sigue sin poder iniciar sesión
Cuando un administrador la aprueba asignándole un rol (`PATCH /api/veedor/usuarios/{id}/aprobacion`)
Entonces la cuenta queda activa con los permisos base de ese rol
```

### HU043 — Invitar con un rol ya asignado *(RF043)*

- **Como** administrador
- **Quiero** invitar a una persona por correo con un rol ya decidido
- **Para** incorporarla directamente, sin que pase por la cola de aprobación

```gherkin
Dado que el administrador conoce el correo, el nombre y el rol de la persona que quiere incorporar
Cuando crea la invitación (`POST /api/veedor/usuarios/invitaciones`), con el `barrioId` de la persona si lo conoce
Entonces el sistema envía un enlace para que la persona fije su clave
Cuando la persona invitada fija su clave desde ese enlace (`POST /api/cuentas/invitacion`)
Entonces su cuenta queda activa de inmediato, con el rol de la invitación, sin otra aprobación
```

### HU044 — Aprobar, rechazar, suspender, reactivar y ajustar permisos *(RF044)*

- **Como** administrador
- **Quiero** aprobar, rechazar, suspender y reactivar cuentas, y asignarles un rol con ajustes de permisos por persona
- **Para** que el acceso al panel corresponda siempre a quién debe tenerlo y con qué alcance

```gherkin
Dado una cuenta pendiente de aprobación, activa o suspendida
Cuando el administrador la aprueba, la rechaza, la suspende o la reactiva desde el panel
Entonces la cuenta queda en el estado correspondiente
Y al suspenderla o cambiarle los permisos, sus sesiones abiertas dejan de servir de inmediato (_RNF023_)
Cuando el administrador concede o revoca un permiso suelto sobre el rol de una persona
Entonces esa persona queda con el rol base más los ajustes indicados, sin necesitar un rol nuevo
Y ningún administrador puede aprobarse, suspenderse ni cambiarse los permisos a sí mismo
Y no puede quedar el sistema sin ningún administrador activo
```

### HU045 — Bitácora de auditoría del acceso *(RF045)*

- **Como** administrador
- **Quiero** que quede registrado en una bitácora inmutable quién cambió el acceso de quién, cuándo y desde qué IP
- **Para** poder responder ante cualquier duda sobre un cambio de acceso, incluso meses después

```gherkin
Dado que ocurre un cambio de acceso (aprobación, rechazo, suspensión, reactivación o cambio de permisos)
Entonces el sistema lo registra en la bitácora de auditoría con su autor, su destinatario, su instante y su IP de origen
Cuando una cuenta con el permiso de ver auditoría consulta `GET /api/veedor/auditoria`
Entonces encuentra ese registro, sin que nadie haya podido editarlo ni borrarlo
```

### HU046 — Restablecer la clave con un enlace de un solo uso *(RF046)*

- **Como** veedor
- **Quiero** restablecer mi clave con un enlace de un solo uso enviado a mi correo
- **Para** recuperar el acceso a mi cuenta sin depender de que un administrador me la reinicie

```gherkin
Dado que el veedor olvidó su clave
Cuando solicita el restablecimiento indicando su correo (`POST /api/cuentas/restablecimiento`)
Entonces recibe la misma respuesta, y tarda lo mismo, exista o no la cuenta con ese correo (_RNF024_)
Cuando fija su clave nueva desde el enlace que le llegó (`POST /api/cuentas/clave`)
Entonces la clave queda actualizada y todas sus sesiones abiertas se cierran
Cuando intenta reutilizar el mismo enlace
Entonces el sistema rechaza la operación
```

---

## Trazabilidad del artefacto

Rastreado a [`matriz-trazabilidad.md`](../ingenieria/matriz-trazabilidad.md), Nivel 2 (Requisitos funcionales):
**RF001–RF046 tienen su historia**, con el mismo número, el mismo actor y el mismo módulo que en
`docs/product-requirements.md`. Los RNF se verifican con mediciones (Nivel 3), no con historias de usuario. Las pruebas
que verifican cada historia son los casos de prueba CP001–CP046 del [Anexo 5](./anexo-5-manual-tecnico.md) §6.

**Revisión del 2026-09-29:** se verificó que `docs/product-requirements.md` define 46 `RF` y que este anexo los cubre todos,
uno a uno, sin historias huérfanas. Cambios respecto a la versión recuperada del historial: HU013, HU015 y HU019 reflejan
el comportamiento vigente (confirmar y dar de baja con un botón que hace `POST`; el panel responde 401); HU008 usa el
flujo de dos toques del núcleo ciudadano; HU031 ya no promete «registrar el descarte» (no es lo que hace el sistema);
HU032–HU036 dejan de hablar de IA y dicen qué parte del requisito se cumple con la heurística y cuál no; HU041 es Telegram
(WhatsApp queda fuera, `ADR-066`); HU042–HU043 incluyen el barrio opcional (`ADR-081`); y las historias sin bloque
«Como / Quiero / Para» (HU023–HU025, HU027–HU036) lo ganan.
