# Bitácora de decisiones (ADR)

> Registro cronológico y **append-only** de las decisiones de diseño y arquitectura.
> Las entradas no se editan salvo para cambiar su estado a *Reemplazada*.
>
> Existe para que nadie —humano o agente— vuelva a proponer un camino que ya se exploró y se
> descartó. **Léelo antes de proponer alternativas.**
>
> Para agregar una entrada: usa la skill `registrar-decision`.
>
> **Aquí vive el *porqué*, no el *qué*.** El comportamiento del sistema está en
> `docs/ingenieria/comportamiento-del-sistema.md` (`ADR-047`). Un ADR no repite lo que
> hace el sistema; ese documento no repite por qué se decidió así.

---

## ADR-001 — Adoptar Arquitectura Limpia con puertos y adaptadores

- **Fecha:** 2026-08-06
- **Estado:** Aceptada

### Contexto
Un proyecto anterior (ODYXS) usó MVC monolítico con Thymeleaf y MySQL: los controladores
contenían lógica de negocio, no había capa de DTOs y las entidades JPA viajaban directo a la vista.
Funcionó, pero no era demostrable como diseño ni testeable por capas. Este proyecto debe evidenciar
SOLID y patrones.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| MVC en capas (como ODYXS) | Familiar, rápido de arrancar | No evidencia SOLID; dominio acoplado al framework |
| Arquitectura Limpia | Dominio testeable sin framework; SOLID demostrable con el dedo | Más carpetas, curva de aprendizaje |
| Microservicios | Escalable | Sobreingeniería absoluta para un proyecto de una persona |

### Decisión
Arquitectura Limpia con cuatro capas (`domain`, `application`, `infrastructure`, `api`) y
dependencias apuntando hacia adentro.

### Consecuencias
- **Gana:** el dominio se testea sin levantar Spring; cada principio SOLID tiene un lugar concreto
  que señalar en la sustentación.
- **Pierde:** más ceremonia — un caso de uso simple toca 4 archivos en vez de 1.
- **Condiciona:** obliga a mantener DTOs y mappers; sin ellos la capa `api` se contamina.

### Cómo se revierte
No se revierte parcialmente. Volver a MVC implicaría reescribir `application/` e `infrastructure/`.

---

## ADR-002 — Verificar la regla de capas con ArchUnit en la build

- **Fecha:** 2026-08-06
- **Estado:** Aceptada

### Contexto
Una regla de arquitectura que depende de que la gente la recuerde se rompe en el primer sprint con
presión de entrega. Especialmente cuando se escribe rápido y con ayuda de un agente.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Confiar en la revisión de PR | Cero configuración | Depende de quien revise; se cuela lo que se cuela |
| Documentar la regla y ya | Rápido | Nadie relee la documentación bajo presión |
| Test automático con ArchUnit | La build falla, no se puede ignorar | Una dependencia más |

### Decisión
Test de ArchUnit que falla la build si `domain/` importa framework o si `application/` importa
`infrastructure`.

### Consecuencias
- **Gana:** la regla deja de ser un acuerdo y pasa a ser una restricción del sistema. Además es
  evidencia objetiva y demostrable en la sustentación.
- **Pierde:** puede frustrar a quien no entienda por qué falla su build — hay que explicarlo bien.

### Cómo se revierte
Borrar el test. Trivial, pero se perdería la garantía.

---

## ADR-003 — MongoDB para persistencia y Redis para estado efímero

- **Fecha:** 2026-08-06
- **Estado:** Aceptada

### Contexto
Los cortes son documentos de estructura variable (lista de sectores afectados, historial embebido,
campos que solo existen cuando el corte cerró). Además el producto necesita responder "¿a qué sector
pertenece esta coordenada?" y contar reportes recientes por sector en ventanas de tiempo.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Solo MySQL/PostgreSQL | Familiar; PostGIS es potente | Esquema rígido para documentos variables; PostGIS no era conocido |
| Solo MongoDB | Documentos flexibles; geoespacial nativo | No sirve para rate limiting ni ventanas deslizantes |
| MongoDB + Redis | Cada motor hace lo que hace bien | Dos tecnologías que aprender y operar |

### Decisión
MongoDB para persistencia (con índices `2dsphere` sobre GeoJSON) y Redis para caché, rate limiting,
ventana deslizante de consenso y pub/sub.

### Consecuencias
- **Gana:** consultas geoespaciales sin librerías extra; el rate limiting sin login se vuelve trivial.
- **Pierde:** dos motores que levantar y monitorear; sin transacciones ACID entre ambos.
- **Condiciona:** Redis es efímero por diseño — nada crítico puede vivir solo ahí.

### Cómo se revierte
Reemplazable por adaptadores alternativos gracias a ADR-001; el dominio no cambia.

---

## ADR-004 — Consumir la API REST de Acuacar en vez de scrapear HTML

- **Fecha:** 2026-08-06
- **Estado:** Aceptada · **Reemplaza el supuesto erróneo del plan inicial**

### Contexto
El plan inicial afirmaba que el `robots.txt` de Acuacar **prohibía el acceso automatizado**, y sobre
ese supuesto se construyó una "decisión ética de no scrapear" con carga manual asistida.

**El supuesto era falso y nunca se verificó.** El archivo real solo contiene `Disallow: /wp-admin/`.

Al verificarlo apareció algo mejor: `acuacar.com` es WordPress con la **API REST habilitada**.
Verificado en producción el 2026-08-06:

- `GET /wp-json/wp/v2/posts` → **HTTP 200**, JSON, **307 boletines**, paginado
- Soporta `?after=`, `?modified_after=`, `?_fields=`
- `/feed/` y `/sitemap_index.xml` también responden 200

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Carga manual por el veedor | Cero riesgo técnico | No escala; depende de que alguien esté pendiente |
| Scraping de HTML | Funciona sin API | Frágil ante cualquier rediseño |
| **API REST oficial** | Estructurada, estable, paginada, permitida | Depende de que no la deshabiliten |

### Decisión
Consumir la API REST de WordPress como fuente primaria (capa L1), con el RSS como respaldo.

### Consecuencias
- **Gana:** fuente estructurada y estable; se elimina el scraping frágil; permite reprocesar los 307
  boletines históricos para el Índice de Cumplimiento.
- **Pierde:** si Acuacar deshabilita `/wp-json/`, hay que caer al RSS.
- **Lección conservada:** verificar antes de afirmar.

### Cómo se revierte
Cayendo al RSS o al sitemap. El resto del pipeline no cambia (ADR-001).

---

## ADR-005 — Respetar los bloqueos de `robots.txt` a agentes de IA aunque sean evadibles

- **Fecha:** 2026-08-06
- **Estado:** Aceptada

### Contexto
Al auditar cada medio se encontró que **El Universal, El Tiempo, El Heraldo y Blu Radio** incluyen
reglas `Disallow: /` dirigidas por nombre a `anthropic-ai`, `Claude-Web`, `ClaudeBot`, `GPTBot` y
`CCBot`. El Universal es el diario local más relevante para Cartagena.

**Técnicamente, un colector propio con `User-Agent` `AguaVigiaCTG/0.1` no cae bajo esos nombres y
pasaría sin ser detectado.** No hay consecuencia técnica por hacerlo.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Usar `User-Agent` propio y acceder igual | Recupera la mejor fuente local; nadie se enteraría | Evade una restricción declarada; incoherente con la tesis del proyecto |
| Pedir permiso al medio | Legítimo | Sin canal claro; tiempos incompatibles con el proyecto |
| **Respetar el bloqueo y cubrir vía agregador** | Coherente; sin riesgo reputacional | Se pierde el acceso directo a la mejor fuente local |

### Decisión
Se respeta el bloqueo sin excepción. No se disfraza el `User-Agent`. La cobertura de esos medios se
recibe indirectamente vía Google News RSS, que es un producto de agregación al que el propio medio
decide alimentar.

Además, esos dominios se agregan a la lista `deny` de `.claude/settings.json`, para que la regla deje
de depender de que alguien la recuerde.

### Consecuencias
- **Gana:** coherencia total con la tesis del proyecto. Es difícil exigirle transparencia a un
  operador de servicios públicos mientras se entra por la puerta trasera de un periódico. Además es
  material de sustentación fuerte: un principio ético sostenido sin consecuencia técnica que lo obligue.
- **Pierde:** acceso directo a El Universal, la cobertura local más relevante.

### Cómo se revierte
Solo si el medio cambia su `robots.txt` o concede permiso explícito por escrito.

---

## ADR-006 — Exigir cita textual verificable a toda extracción de IA

- **Fecha:** 2026-08-06
- **Estado:** Aceptada

### Contexto
La capa de IA extrae sectores, fechas y horas de texto libre. Un modelo puede alucinar un corte que
no existe. En una plataforma cuyo único activo es la credibilidad, publicar un corte falso sería peor
que no publicar nada.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Confiar en la salida del modelo | Simple | Riesgo de publicar información inventada |
| Revisión humana de todo | Máxima seguridad | Anula el propósito de automatizar |
| **Cita textual obligatoria + umbrales** | Verificable por código; automatiza lo seguro | Algunas extracciones válidas se rechazan |

### Decisión
Todo evento extraído incluye `citaTextual`: el fragmento exacto del documento que sustenta la
extracción. Si `documento.texto().contains(citaTextual)` es falso, **la extracción se rechaza
automáticamente**. Además: confianza ≥ 0.85 publica; 0.5–0.85 va a revisión humana; < 0.5 se archiva.

### Consecuencias
- **Gana:** la anti-alucinación deja de ser una promesa y pasa a ser una comprobación de código.
- **Pierde:** se rechazan extracciones correctas cuya cita fue parafraseada. Sesgo deliberado hacia
  la precisión sobre la exhaustividad — un corte omitido lo reporta la comunidad (capa L4); uno
  inventado destruye el proyecto.

### Cómo se revierte
Bajando el umbral o quitando la verificación. No recomendado sin sustituir por otro control.

---

## ADR-007 — Reportes ciudadanos sin registro, con rate limiting como control

- **Fecha:** 2026-08-06
- **Estado:** Aceptada

### Contexto
El usuario principal es un vecino sin agua, en el celular, con datos limitados y con prisa. Cualquier
fricción de registro lo pierde. Pero sin identidad, el sistema queda expuesto a reportes masivos
falsos que podrían pintar la ciudad de rojo.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Registro obligatorio | Trazabilidad total; anti-abuso robusto | Se pierde al usuario principal |
| Sin control alguno | Máxima facilidad | Vulnerable a manipulación trivial |
| **Sin registro + rate limit + consenso** | Fricción cero y abuso contenido | Un atacante decidido con muchas IP podría manipular |

### Decisión
Reportar no requiere cuenta. El control es triple: rate limiting por huella de dispositivo/IP en
Redis, consenso de N reportes independientes antes de cambiar un estado, y moderación posterior del
veedor.

### Consecuencias
- **Gana:** fricción cero para el usuario principal; menos datos personales que proteger (RNF008).
- **Pierde:** no hay trazabilidad individual del reportante; un ataque coordinado sigue siendo posible.
- **Condiciona:** el consenso (M3) deja de ser una funcionalidad y pasa a ser un control de seguridad.

### Cómo se revierte
Agregando autenticación opcional para reportes "verificados" sin quitar la vía anónima.

---

## ADR-008 — Registrar implementaciones, bugs y sesiones en el repositorio, no en la conversación

- **Fecha:** 2026-08-07
- **Estado:** Aceptada

### Contexto
Varias sesiones de agente de IA trabajan el mismo repositorio, una tras otra. Nada de
lo que ocurre en una conversación sobrevive a su cierre: ni el bug que se encontró y se arregló, ni
el motivo por el que un endpoint quedó como quedó, ni en qué punto quedó el trabajo.

Dos consecuencias concretas, no hipotéticas:

1. **Los resultados medibles** (defectos encontrados, requisitos
   cubiertos, cobertura) no se pueden reconstruir meses después: se termina inventando.
2. **El contexto de IA tiene un costo real.** Sin un lugar acordado donde vive cada dato, cada sesión
   vuelve a explicar el proyecto, y cada archivo permanente crece hasta que leerlo cuesta más que el
   trabajo mismo.

La auditoría de documentación del 2026-08-07 encontró además el síntoma: la misma información
duplicada en dos carpetas, tres carpetas declaradas en `CLAUDE.md` que no existían, y
referencias a archivos nunca creados.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Confiar en el historial de Git y en los PRs | Cero esfuerzo adicional | Un commit dice *qué* cambió, no *por qué* ni qué falló antes; no hay causa raíz ni siguiente paso |
| Llevarlo todo en GitHub Issues/Projects | Herramienta hecha para eso; buena para tareas | El agente no lo lee sin conectar el MCP; se pierde al cerrar el tablero; no sirve como fuente de datos acumulados |
| Documento único de bitácora | Simple | Crece sin control y mezcla cosas de naturaleza distinta; nadie lo lee a los dos meses |
| **Tres registros separados + protocolo de contexto y rotación** | Cada registro tiene formato y límite; el agente puede llenarlos con skills | Disciplina diaria; si nadie registra, queda peor que no tenerlo |

### Decisión
Tres registros en `docs/gestion/`, cada uno con su skill que lo llena:
`registro-de-implementaciones.md` (`registrar-implementacion`), `registro-de-bugs.md`
(`registrar-bug`) y `bitacora-sesiones.md` (`cerrar-sesion`).

Los gobierna `docs/gestion/protocolo-de-contexto.md`, que fija tres cosas: **una información vive en
un solo archivo**, los archivos permanentes tienen **presupuesto de líneas** (`CLAUDE.md` ≤ 200,
`MEMORY.md` ≤ 150, `DESIGN.md` ≤ 200), y los registros **rotan** al superar su límite.

Registrar pasa a ser parte de la definición de terminado.

### Consecuencias
- **Gana:** los resultados se construyen desde datos reales acumulados, no desde la memoria; una
  sesión nueva arranca en tres líneas; los bugs dejan de repetirse porque quedan con causa raíz y
  prueba; las sesiones paralelas comparten los mismos hechos.
- **Pierde:** disciplina diaria. Un registro que se llena a medias es peor que no tenerlo, porque da
  falsa sensación de trazabilidad. Las skills existen justamente para bajar ese costo.
- **Condiciona:** obliga a rotar los registros al cerrar cada sprint.

### Cómo se revierte
Se dejan de usar las skills y los archivos quedan como histórico. No se borran: lo ya registrado
sigue siendo evidencia válida.

---

## ADR-009 — El Sprint 0 admite esqueletos e infraestructura, no funcionalidad

- **Fecha:** 2026-08-07
- **Estado:** Aceptada

### Contexto
El acuerdo del 2026-08-06 (`MEMORY.md`) dice que **no se escribe código de la aplicación** hasta
autorización explícita. Al día siguiente se fusionaron el PR #1 (Docker Compose, CI),
el PR #2 (GeoJSON) y el PR #5 (proyecto `/frontend` con React 19, Vite, TypeScript, Tailwind,
componentes y rutas). Nadie objetó, y con razón: sin eso el Sprint 0 no puede cerrar.

Pero el acuerdo quedó escrito como una prohibición absoluta, así que el repositorio pasó a
contradecirse solo. Tres archivos afirmaban que el código no había iniciado mientras el código ya
estaba fusionado. Un agente que lee `CLAUDE.md` literalmente se detiene ante una tarea legítima; uno
que lo ignora pierde la regla entera.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Mantener la prohibición literal y revertir `/frontend` | Coherente con el acuerdo | Destruye trabajo válido y deja el Sprint 0 sin poder cerrar; el entorno reproducible exige que `/frontend` exista |
| Quitar la restricción: código libre desde ya | Sin fricción | Se pierde lo que la regla protegía: que nadie implemente un RF antes de que el requisito y el dominio estén cerrados |
| Autorización caso por caso en el chat | Flexible | No queda escrita; el siguiente agente no la encuentra y vuelve a preguntar |
| **Distinguir esqueleto de funcionalidad, con un criterio verificable** | Conserva la protección real y desbloquea el Sprint 0; el criterio se puede aplicar sin discutir | Hay que juzgar los casos de frontera |

### Decisión
En el Sprint 0 se permite **andamiaje**: estructura de proyecto, configuración, tokens visuales,
rutas vacías, infraestructura y CI. Se prohíbe la **funcionalidad**.

**Criterio que los separa, en una pregunta:** *¿este código implementa un `RF` de
`docs/product-requirements.md`?* Si la respuesta es sí, no va en el Sprint 0. Si es no, sí va.

Ejemplos resueltos con el criterio: una ruta `/mapa` que muestra un marcador de posición **sí**; esa
misma ruta pintando sectores desde la API **no** (RF001). Los tokens de `DESIGN.md` **sí**; el
cálculo del Índice de Cumplimiento **no** (RF021).

La restricción de fondo no cambia y sigue siendo la importante: **no se implementa un RF antes de que
su dominio esté modelado y su contrato publicado.**

### Consecuencias
- **Gana:** el Sprint 0 puede cerrar; `CLAUDE.md` deja de contradecir al repositorio; el criterio se
  aplica solo, sin pedir permiso en cada tarea.
- **Pierde:** los casos de frontera necesitan juicio. Ante la duda, se pregunta.
- **Condiciona:** el andamiaje del Sprint 0 se registra en `registro-de-implementaciones.md` con
  `RF = —`, para que la cobertura de requisitos siga contando 0/36 mientras no haya funcionalidad.

### Cómo se revierte
Volviendo a la prohibición absoluta. Lo ya fusionado no se revierte: es andamiaje necesario.

---

## ADR-010 — La protección de ramas es política documentada, no control técnico

- **Fecha:** 2026-08-07
- **Estado:** Aceptada

### Contexto
`CLAUDE.md` afirma que *"nadie hace push directo a `main`"*. Configurar branch protection técnica en
GitHub exige un rol `admin` en el repositorio remoto, y se decidió no hacerlo.

El problema no es la decisión, es lo que quedó escrito: el documento prometía una red que no existe.
Y ya falló: **7 de los 11 PRs fusionados hasta el 2026-08-07 no registran revisor** (#2, #4, #6, #7,
#10, #11 y #12), contra la regla de 1 revisor mínimo entonces vigente.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Configurar branch protection en GitHub | Se cumple sola, sin depender de nadie | En repositorios privados de plan gratuito las reglas son limitadas; puede estorbar en una demo o una corrección urgente |
| **Política documentada, sin bloqueo técnico** | Cero fricción; se sostiene por disciplina propia | Depende de disciplina, y ya falló en la mitad de los PRs del Sprint 0 |
| Ninguna regla | Honesto | Deja el proyecto sin ninguna revisión |

### Decisión
La regla **"los cambios no triviales entran por PR, y nadie hace push directo a `main`"** se mantiene
como **política del proyecto**, sin refuerzo técnico. Se documenta como tal en `CLAUDE.md` para que
nadie confíe en una protección inexistente.

**Control compensatorio:** revisar el propio diff antes de fusionar. Un PR sin segunda revisión no es
un delito, pero sí un dato.

### Consecuencias
- **Gana:** el repositorio deja de prometer lo que no cumple; la regla se sostiene por acuerdo, y el
  incumplimiento queda medido en vez de invisible.
- **Pierde:** nada impide un push directo. Es un riesgo aceptado conscientemente.
- **Condiciona:** si se vuelve a incumplir de forma sistemática, se activa branch protection y esta
  decisión pasa a *Reemplazada*.

### Cómo se revierte
Activando las reglas de protección en GitHub.

---

## ADR-014 — Un sector sin dato verificado se publica con estado nulo, no como `CON_SERVICIO`

- **Fecha:** 2026-08-08
- **Estado:** Parcialmente reemplazada por ADR-035 — el contrato sigue transmitiendo `estado: null`; lo que cambia es cómo lo presenta el frontend

### Contexto

El sembrador (`scripts/sembrar-sectores.mjs`) carga los 211 barrios **sin `estadoActual`**, y
deja escrita la pregunta en un comentario: *"El adaptador de SectorRepository decide el valor inicial
al leer un sector que todavía no tiene estado registrado."* Hasta que el consenso (M3, Sprint 2)
empiece a escribir estados, **ningún sector de Cartagena tiene estado verificado**: son 211 de 211.

`EstadoServicio` es un enum cerrado de cuatro valores y no tiene `SIN_DATO` — por decisión de diseño, que
`modelo-de-dominio.md` §1 anota: *"el 'sin dato' se resuelve en presentación, no en el dominio"*.
Así que el adaptador tiene que elegir entre un valor del enum o la ausencia de valor.

El frontend ya tomó la decisión contraria por su cuenta: `MapaCartagena.tsx:92` hace
`sector?.estado ?? 'CON_SERVICIO'`, es decir pinta de verde todo barrio del que no sabe nada.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Por omisión `CON_SERVICIO` | El mapa se ve completo desde el primer día; ningún cliente maneja nulos | Afirma ante el vecino que hay agua en un barrio del que no se sabe nada. Es exactamente el falso positivo que `MEMORY.md` (acuerdo del 2026-08-06) manda evitar: *"un corte inventado destruye la credibilidad"* — y su simétrico, un servicio inventado, también |
| Agregar un quinto valor `SIN_DATO` | El dominio expresaría la ausencia explícitamente | Toca `domain/` y contradice la decisión ya registrada de resolver el "sin dato" en presentación. Además obligaría a un quinto color en `DESIGN.md` §2 |
| **Estado nulo en el adaptador y en el contrato** | Dice la verdad: no hay dato. No toca `domain/`. El frontend ya sabe representarlo — `useFrescura` devuelve *"sin datos"* ante un timestamp nulo | Obliga al frontend a manejar el nulo en `InsigniaEstado` y a quitar su `?? 'CON_SERVICIO'` |

### Decisión

`SectorMongoAdapter` traduce a `null` tanto el estado ausente como un estado guardado que ya no
corresponde a ningún valor del enum. El contrato lo transmite tal cual: `"estado": null` viaja
explícito en el JSON, no se omite la clave, para que el cliente generado lo tipe como anulable.

### Consecuencias

- **Gana:** la plataforma no afirma nada que no haya verificado, que es la única razón por la que un
  vecino le creería. La coherencia con `ADR-006` (cita textual obligatoria) es la misma idea aplicada
  a otra capa: ante la duda, no se publica.
- **Pierde:** el mapa se ve mayormente gris hasta que M3 empiece a registrar estados en el Sprint 2.
  Se ve peor en una demostración, y es honesto.
- **Condiciona:** `MapaCartagena.tsx:92` e `InsigniaEstado` deben tratar el nulo como *"sin datos"*.
  Queda registrado como `BUG-008`.

### Cómo se revierte

Una línea en el adaptador (`orElse(EstadoServicio.CON_SERVICIO)`). Se desaconseja: revertirlo es
elegir que la plataforma afirme lo que no sabe.

---

## ADR-015 — Las consultas de solo lectura van del controlador al puerto de salida, sin caso de uso

- **Fecha:** 2026-08-08
- **Estado:** Aceptada

### Contexto

`GET /api/sectores` (RF001–RF004) no tiene regla de negocio: lee, ordena por nombre y serializa.
`CLAUDE.md` dice que los controladores *"traducen HTTP ↔ caso de uso"*, pero los cinco casos de uso
que define `domain/port/in` son de escritura o de cálculo (registrar reporte, evaluar consenso,
gestionar corte, calcular cumplimiento, registrar evento). **No existe un caso de uso de consulta de
sectores, y `application/` está vacío.**

Crear uno significaría escribir en `application/` una clase que solo delega.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Escribir `ConsultarSectoresService` en `application/` | Cumple la letra de "controlador ↔ caso de uso" | Crea una clase que solo delega, sin ninguna regla de negocio detrás |
| **Controlador → puerto de salida** | No inventa capas; las dependencias siguen apuntando hacia adentro; ArchUnit sigue en verde | Se aparta de la lectura estricta de `CLAUDE.md`; hay que sostener la disciplina de no dejar que crezca lógica ahí |

### Decisión

Para consultas sin regla de negocio, el controlador depende de `domain/port/out` directamente.
`application/` se reserva para lo que tenga decisión de negocio.

**Límite explícito:** en cuanto una consulta necesite una regla —filtrar por frescura, combinar
sectores con cortes activos, calcular un agregado— deja de ser cosa del controlador y pasa a ser un
caso de uso. Si aparece un `if` de negocio en `SectorController`, este ADR se está violando.

### Consecuencias

- **Gana:** el contrato se publica sin inventar un intermediario vacío.
- **Pierde:** la regla "controlador ↔ caso de uso" pasa a tener una excepción, y las excepciones se
  erosionan solas si nadie las vigila. Por eso el límite de arriba está escrito y no sobreentendido.
- **Condiciona:** si se define después un caso de uso de consulta, el controlador se migra a él.

### Cómo se revierte

Introduciendo el caso de uso en `application/` y apuntando el controlador ahí. El adaptador, el DTO
y el contrato no cambian.

---

## ADR-016 — El panel del veedor usa una sola credencial compartida, no cuentas individuales

- **Fecha:** 2026-08-08
- **Estado:** Reemplazada por ADR-039

### Contexto

RF019 exige que el panel del veedor requiera autenticación con token; RNF011 fija la expiración
máxima en 8 horas. Ninguno de los dos dice si hay una cuenta por veedor o una credencial compartida
— y el dominio tampoco lo decide: no existe una entidad `Usuario` ni `Veedor` en `domain/`, y crearla
es una decisión de diseño que no se tomó todavía.

El propio frontend ya venía asumiendo una credencial única: `PaginaVeedor.tsx` comparaba el acceso
contra una contraseña literal en el código (`'1234'`, `BUG-004`) antes de que se la reemplazara por
un botón "Simular ingreso" sin credencial real, a la espera de JWT server-side (comentario en el
propio archivo: esperaba el contrato del backend para integrar JWT y endpoints).

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| **Credencial única compartida** (elegida) | No requiere entidad `Usuario`; RF019 habla de "un usuario autenticado" en singular; coincide con el patrón que ya asumía el frontend | No hay auditoría de qué persona hizo qué cambio como veedor |
| Cuenta individual por veedor | Trazabilidad individual de acciones administrativas | Exige modelar `Usuario`/`Veedor` en `domain/`, gestión de altas/bajas y recuperación de contraseña — desproporcionado para un panel con muy pocas personas |

### Decisión

Una sola clave, cuyo hash BCrypt vive en la variable de entorno `VEEDOR_PASSWORD_HASH` (nunca la
clave en texto plano). `POST /api/veedor/sesion` la valida y devuelve un JWT firmado con
`JWT_SECRET`, válido 8 horas. `SecurityConfig` protege `/api/veedor/**` (menos el propio login) y
deja todo lo demás público, siguiendo la letra de RF019.

### Consecuencias

- **Gana:** Sprint 3 no queda detenido diseñando un modelo de usuarios que ningún
  requisito pide todavía. La superficie nueva es pequeña: un filtro, un proveedor de JWT y un
  controlador de login.
- **Pierde:** ninguna acción del panel queda atribuida a una persona concreta — si se
  necesita esa trazabilidad más adelante, hay que migrar a cuentas
  individuales, lo que sí requeriría una entidad de dominio.
- **Condiciona:** `JwtProvider` valida el secreto de forma perezosa (al usarse, no al arrancar) para
  que un `JWT_SECRET` sin configurar no tumbe el resto del backend — los endpoints públicos no
  dependen de esto. `POST /api/veedor/sesion` responde `503` explícito si `JWT_SECRET` o
  `VEEDOR_PASSWORD_HASH` no están configurados, en vez de fallar con un error críptico.
- **Fuera de alcance de este PR, señalado:** no hay límite de intentos en el login.
  Con una sola credencial compartida, un ataque de fuerza bruta contra `POST /api/veedor/sesion` no
  tiene ningún freno todavía. `ContadorReportesPort` (Redis, PR #57) está diseñado para el consenso
  de M3, no para esto — un rate limiter de login es trabajo aparte, no incluido aquí a propósito
  para no exceder el alcance de RF019/RNF011.

### Cómo se revierte

Migrando a cuentas individuales: una entidad `Usuario` en `domain/`, un
`UsuarioRepository`, y `VeedorAuthController` pasa de comparar un hash fijo a consultar el
repositorio. `JwtProvider` y `JwtAuthenticationFilter` no cambian.

---

## ADR-017 — `DocumentoCrudo` vive en `infrastructure/ingest/`, no en `domain/`

- **Fecha:** 2026-08-08
- **Estado:** Aceptada

### Contexto

`docs/ingenieria/pipeline-ingesta-datos.md` define `DocumentoCrudo` como la forma normalizada a la
que convergen todos los colectores, con un campo `hash` (SHA-256) para deduplicar. Es tentador
tratarlo como un Value Object de dominio —se parece a `Sector` o `Coordenada` en que es inmutable y
se valida al construirse— pero no representa nada del acueducto: representa la forma de un boletín
de prensa antes de que la IA decida si le importa al dominio o no. Si `EventoExtraido` alguna vez se
publica como `CorteAgua`, ahí sí cruza a `domain/` — `DocumentoCrudo` nunca lo hace.

También se decidió el alcance de `DeduplicadorReciente`: el diseño pide dos chequeos, uno rápido en
Redis y uno autoritativo contra Mongo ("¿el hash ya existe en Mongo? → descartar"). El segundo
depende de dónde se decida persistir los documentos o eventos procesados —una colección que
todavía no existe—, así que este PR construye solo la
mitad Redis, deliberadamente no permanente (ventana de 7 días, no un registro definitivo).

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| `DocumentoCrudo` como Value Object en `domain/` | Consistente con `Coordenada`/`VentanaTiempo` | Acopla el dominio a la forma de un boletín de prensa; ArchUnit (Regla de Oro) prohibiría que dependa de nada de infraestructura, y su único propósito es alimentar una llamada a una API externa |
| **`DocumentoCrudo` en `infrastructure/ingest/`** (elegida) | Refleja lo que es: un DTO interno del pipeline, no un concepto del negocio | Ningún test de ArchUnit lo protege de mutar libremente — pero tampoco lo necesita, no es una invariante del dominio |
| Deduplicación completa (Redis + Mongo) en este PR | Cierra el diseño de una vez | Obliga a decidir ahora dónde persisten los documentos procesados, una decisión de modelado que merece su propia discusión |
| **Solo la mitad Redis, con el límite escrito en el código** (elegida) | Entrega valor real (evita reprocesar el mismo boletín en la semana) sin inventar una colección de Mongo que nadie diseñó todavía | La deduplicación no es permanente — un boletín republicado después de 7 días se reprocesaría |

### Decisión

`DocumentoCrudo`, `PrefiltroDeterminista` y `DeduplicadorReciente` viven en
`infrastructure/ingest/`. `DeduplicadorReciente` cubre solo la ventana reciente vía Redis; el
chequeo autoritativo contra Mongo queda pendiente de que se diseñe dónde persisten los documentos
procesados.

### Consecuencias

- **Gana:** Sprint 4 avanza sin inventar una colección de Mongo ni una decisión de modelado que se
  discutirá aparte, y sin arriesgar la pureza de `domain/` que protege ArchUnit.
- **Pierde:** la deduplicación no es definitiva todavía — un reprocesamiento después de 7 días es
  posible y esperado hasta que exista la mitad Mongo.
- **Condiciona:** cuando se diseñe la persistencia de documentos/eventos procesados, alguien decide
  si el chequeo autoritativo va en un nuevo puerto de dominio (como `ContadorReportesPort`) o si vive enteramente en infraestructura. Ese es el momento de revisar este ADR.

### Cómo se revierte

Moviendo `DocumentoCrudo` a `domain/` si algún día representa algo que el dominio necesita conocer
directamente — hoy no es el caso.

---

## ADR-018 — Rate limiting HTTP genérico, opt-in por configuración, clave por IP

- **Fecha:** 2026-08-08
- **Estado:** Aceptada

### Contexto

El plan de Sprint 2 pedía "Rate limiting en Redis (`INCR` + `EXPIRE`)", y
`ADR-016` dejó señalado que `POST /api/veedor/sesion` no tenía freno contra fuerza bruta. Ninguno
de los dos endpoints que más lo necesitan (login del veedor, `POST /api/reportes`) existe todavía en
`develop` — viven en PRs sin fusionar (#58) o sin construir (`application/` vacía). Construir
el limitador acoplado a un endpoint concreto habría significado depender de una rama ajena sin
fusionar, o inventar el endpoint que falta.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Un interceptor hardcodeado para `/api/veedor/sesion` | Resuelve el hueco exacto de `ADR-016` | Depende del PR #58 sin fusionar; sirve un solo caso cuando `POST /api/reportes` va a necesitar lo mismo |
| **Interceptor genérico, reglas por `application.yml`** (elegida) | Reutilizable para cualquier ruta futura sin tocar código Java; no depende de ningún PR sin fusionar; opt-in — sin reglas configuradas, cero cambio de comportamiento | Una capa de indirección más (propiedades → interceptor) para un caso que hoy es solo uno |
| Clave por `HuellaDispositivo` (como `ContadorReportesPort`, PR #57) | Coherente con ADR-007 (rate limiting "por huella de dispositivo/IP") | La huella la calcula el cliente y la manda en un header — es información de negocio (M2), no algo que un interceptor HTTP genérico de infraestructura deba conocer. Mezclarlo aquí acoplaría este componente a un contrato de request específico |
| **Clave por IP del request** (elegida) | Disponible en cualquier petición HTTP sin contrato adicional; suficiente para frenar fuerza bruta contra un login | Un atacante con muchas IPs no queda contenido — el mismo límite que ya acepta `ADR-007` para el resto del proyecto |

### Decisión

`RateLimitingInterceptor` + `RateLimitConfig` (`WebMvcConfigurer`), configurable vía
`aguavigia.rate-limit.reglas` (lista de `{ruta, limite, ventanaSegundos}`). Lista vacía por
defecto. Clave en Redis: IP del cliente (`request.getRemoteAddr()`), no huella de dispositivo.

**No cubre `/actuator/**`**: Actuator se sirve por un `HandlerMapping` propio
(`WebMvcEndpointHandlerMapping`) que no recoge los interceptores de `WebMvcConfigurer` —
verificado en vivo. No hacía falta de todas formas: solo `health` está expuesto y nadie querría
limitar un healthcheck.

### Consecuencias

- **Gana:** cierra `ADR-016` sin esperar a que se fusione ningún PR; cualquier ruta futura se
  protege con 3 líneas de `application.yml`, sin tocar Java.
- **Pierde:** no protege por dispositivo, solo por IP — un atacante con IPs rotativas no queda
  contenido. Suficiente para el caso que motivó esto (fuerza bruta simple contra un login).
- **Condiciona:** cuando alguien active esto para `/api/veedor/sesion`, el valor sugerido es
  `limite: 5, ventanaSegundos: 300` (5 intentos cada 5 min) — documentado en el javadoc de
  `RateLimitProperties`, no forzado por código.

### Cómo se revierte

Vaciando `aguavigia.rate-limit.reglas`. El interceptor no se registra si la lista está vacía.

---

## ADR-020 — Los correos de M4 se renderizan con sustitución simple de `{{marcador}}`, no con un motor de plantillas

- **Fecha:** 2026-08-08
- **Estado:** Aceptada

### Contexto

El commit `a6a8ae4` (plantillas HTML de M4, `confirmar-suscripcion.html` y `aviso-corte.html`) dejó
anotado a propósito: *"elegir motor de plantillas es una decisión de Sprint 1 y merece su ADR"*, y usó
marcadores `{{nombreSector}}`, `{{urlConfirmacion}}`, `{{horasVigencia}}` sin comprometerse a ningún
motor. Sprint 1 solo necesita renderizar `confirmar-suscripcion.html`: un correo con tres marcadores
fijos, interpolación de texto plano, sin condicionales ni loops.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Thymeleaf (`spring-boot-starter-thymeleaf`) | Motor completo, integración nativa con Spring, escaping automático | Dependencia nueva para un caso de uso que no tiene lógica condicional que justificarla |
| Freemarker | Igual que Thymeleaf | Igual que Thymeleaf |
| Sustitución simple `{{marcador}}` → `String.replace` (`infrastructure/mail/PlantillaCorreo`) | Cero dependencias nuevas, ~30 líneas, hace exactamente lo que el correo de hoy necesita | Sin escaping automático de HTML en los valores interpolados |

### Decisión

Sustitución simple de `{{marcador}}` por `String.replace`, implementada en
`infrastructure/mail/PlantillaCorreo` (clase interna del paquete, no expuesta como puerto de dominio).

### Consecuencias

- **Gana:** ninguna dependencia nueva en `pom.xml` para renderizar un correo; la clase es trivial de
  leer y de testear.
- **Pierde:** sin escaping automático de HTML. Aceptable hoy porque nada de lo que se interpola viene
  de texto libre de terceros (nombre de sector, una URL con UUID propio, un número de horas). Si un
  futuro marcador interpola texto libre — por ejemplo la cita textual de un boletín en
  `aviso-corte.html`, que `ADR-006` exige mostrar — esta decisión debe revisarse **antes** de usarla
  ahí, porque en ese punto sí hay contenido externo que sanitizar.
- **Condiciona:** si `aviso-corte.html` (Sprint 5, notificación de cambio de estado) termina
  necesitando lógica condicional real (mostrar/ocultar bloques según el tipo de evento), esta decisión
  se reabre con el caso de uso real en mano, no por anticipación (`CLAUDE.md`: no diseñar para
  requisitos hipotéticos).

### Cómo se revierte

Sustituir `PlantillaCorreo` por un `TemplateEngine` de Thymeleaf/Freemarker el día que un correo
necesite condicionales o loops, o que haya que interpolar texto libre sin sanitizar a mano. El cambio
queda contenido en `infrastructure/mail/`: ni el puerto `NotificacionPort` ni `application/` conocen
cómo se renderiza el HTML.

---

## ADR-022 — El Índice de Cumplimiento agrega por suma de duraciones, no por promedio de porcentajes

- **Fecha:** 2026-08-09
- **Estado:** Aceptada

### Contexto

`CalcularCumplimientoUseCase` (`domain/port/in/`) y su salida `IndiceCumplimiento` (`domain/`) ya
existían desde el Sprint 1, con las firmas `porCorte(CorteId)`, `porSector(SectorId)` y `global()`,
pero sin implementación ni decisión sobre cómo agregar el cumplimiento de varios cortes. `DESIGN.md`
§6 exige que el índice se muestre "como comparación explícita, prometido vs. real, no como puntaje
aislado", con el ejemplo `Prometieron 2 horas · Fueron 8`.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Promediar el `porcentajeCumplimiento` de cada corte por separado | Cada corte pesa igual sin importar su duración | Un corte de 10 minutos y uno de 10 horas pesarían lo mismo — distorsiona el índice hacia cortes cortos, que son fáciles de cumplir |
| **Sumar duraciones prometidas y reales de todos los cortes cerrados, calcular el porcentaje sobre los totales** (elegida) | Coincide directamente con el ejemplo de `DESIGN.md` — una comparación de tiempo total, no un promedio de porcentajes; un corte largo que incumple pesa más que uno corto que cumple, que es la lectura correcta para la ciudadanía | Un solo corte muy largo puede dominar el índice de un sector con pocos cortes |

### Decisión

Por corte: `duracionPrometida = finPrometido - inicio`, `duracionReal = finReal - inicio`,
`desviacion = duracionReal - duracionPrometida`, `porcentajeCumplimiento = min(100%,
duracionPrometida / duracionReal * 100)` — capado en 100% cuando el corte termina antes o a tiempo.
Para `porSector` y `global`, se suman las duraciones de todos los cortes **cerrados** del conjunto
(RF020: "por cada corte cerrado") y el porcentaje se calcula sobre esos totales, no sobre el
promedio de porcentajes individuales.

Si no hay cortes cerrados para el sector o la ciudad, el servicio lanza `IllegalArgumentException`
en vez de devolver un índice con duración cero — mismo criterio que `ADR-014` (no fabricar un dato
que parezca real cuando no hay verificación). Mapea a 400 vía `ManejadorGlobalDeErrores` existente,
sin manejador nuevo.

Se agregó `CorteAguaRepository.listarTodos()` (puerto de salida, no existía), necesario para
`global()`. Implementado en `CorteAguaMongoAdapter` con `MongoRepository.findAll()`, gratis en
Spring Data.

### Consecuencias

- **Gana:** el índice agregado refleja el tiempo real que la ciudadanía estuvo sin servicio, no un
  promedio abstracto que un corte corto y cumplido podría inflar.
- **Pierde:** un sector con pocos cortes es sensible a que uno solo sea muy largo — el índice puede
  parecer peor de lo que "la mayoría de las veces" sugeriría. Es una lectura deliberada: un corte de
  8 horas cuando se prometieron 2 le pesa más a un vecino que tres cortes de 10 minutos cumplidos.
- **Condiciona:** cualquier futura UI de M6 debe mostrar la comparación de duraciones totales, no
  solo el porcentaje — es lo que hace legible la fórmula elegida.

### Cómo se revierte

Cambiar la agregación a promedio de porcentajes es un cambio de fórmula localizado en
`CalcularCumplimientoService.indiceDe()` — no afecta el puerto ni `IndiceCumplimiento`, que ya
expresan ambas duraciones por separado.

---

## ADR-023 — "Dudoso" en RF018 es "todo reporte sin moderar", no una heurística de fraude

- **Fecha:** 2026-08-09
- **Estado:** Aceptada

### Contexto

RF018 pide "moderar (aprobar o descartar) reportes ciudadanos marcados como dudosos" y `HU018` da el Gherkin: *"Dado que un reporte ciudadano está marcado
como dudoso, cuando el veedor lo aprueba o lo descarta..."* — pero **en ningún documento del
proyecto existe una definición de qué hace que un reporte sea "dudoso"**. Ni `product-requirements.md`,
ni `ADR-007` (que decide el control triple: rate limiting + consenso + moderación posterior, pero no
el criterio de selección), ni `docs/ingenieria/` proponen una heurística. Es un vacío de especificación: nadie definió qué hace dudoso a un reporte.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Inventar una heurística de fraude (p. ej. reportes que contradicen el consenso vigente, ráfagas desde una misma huella) | Se acerca más a la intención literal de "dudoso" | Es una decisión de producto (qué patrón cuenta como sospechoso), no un detalle de implementación — inventarla sin que nadie la haya especificado viola el principio del proyecto de no afirmar lo que no se puede sostener (`ADR-006`) |
| **Todo reporte nace `PENDIENTE` y es candidato a moderación hasta que el veedor decida** (elegida) | No inventa ningún criterio no especificado; el veedor —que sí tiene criterio humano— ve la cola completa y decide; cumple la letra del Gherkin sin fabricar un algoritmo no pedido | El panel puede llenarse de reportes que nadie consideraría "dudosos" en el sentido coloquial; si se define después una heurística de preselección, hay que revisar esta decisión |

### Decisión

`ReporteCiudadano` gana un campo `EstadoModeracion` (`PENDIENTE` · `APROBADO` · `DESCARTADO`),
`PENDIENTE` por defecto al crearse. El veedor consulta la cola de pendientes y decide sobre
cualquiera. Aprobar o descartar es idempotente (se puede repetir o cambiar de decisión sin error) —
mismo criterio que `Suscripcion.confirmar()`.

**Alcance deliberadamente acotado:** descartar un reporte lo saca de la cola de pendientes y lo deja
visible con su decisión, pero **no** recalcula retroactivamente el consenso (M3) ni cambia el conteo
de RF006 (límite de reportes por dispositivo) — ninguna de las dos cosas está pedida por el Gherkin,
y hacerlo bien (¿un sector cambia de estado si el reporte que lo sostenía se descarta?) es una
decisión de producto propia, no una consecuencia obvia de "moderar". Queda como recomendación para
validarla si se necesita.

### Consecuencias

- **Gana:** RF018 (`Debería`, no `Debe`) queda funcional sin fabricar un criterio de fraude que
  nadie pidió ni especificó.
- **Pierde:** un reporte "dudoso" en el sentido literal (contradice el consenso, viene de una huella
  con historial de descartes) no se distingue de uno normal en la cola — el veedor ve todo, sin
  preselección.
- **Condiciona:** si se decide después que sí se quiere una heurística de preselección, se agrega
  como un filtro sobre la cola existente (`ReporteCiudadanoRepository.listarPendientes()`), sin tocar
  el mecanismo de aprobar/descartar.

### Cómo se revierte

Agregar una heurística de preselección es aditivo: un método de filtrado nuevo sobre la cola de
pendientes, sin cambiar `EstadoModeracion` ni el flujo de aprobar/descartar ya construido.

---

## ADR-024 — `CorteAgua` valida coherencia estado/ventana en `build()` en vez de eliminar el campo `estado`

- **Fecha:** 2026-08-09
- **Estado:** Aceptada

### Contexto

Auditoría de dominio (`BUG-044`) encontró que `CorteAgua.Builder.build()` no validaba que
`estado == RESTABLECIDO` correspondiera con `ventana.finReal() != null` — se podía construir un
corte incoherente. En producción nadie lee `corte.estado()` para decidir si un corte está cerrado:
`CalcularCumplimientoService` (M6) y el resto del código usan exclusivamente
`ventana.estaCerrada()` (verificado leyendo los tres archivos que consultan cierre). El campo
`estado` es, en la práctica, redundante frente a la ventana.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| (a) Validar coherencia en `Builder.build()` | Defensa en profundidad: protege también la reconstrucción desde Mongo (`CorteAguaMongoAdapter.aDominio()`), no solo el flujo de negocio | El único caller de riesgo real es la reconstrucción desde Mongo: si algún día hay un documento corrupto, la *lectura* falla, no solo la escritura |
| (b) Eliminar el campo `estado`, derivar todo de `ventana.estaCerrada()` | Elimina la redundancia de raíz, imposible que diverjan | `estado` ya se persiste en `CorteAguaDocumento.estado` y se expone en la API (`CorteApiMapper`, `CorteRespuesta.estado`) — es un cambio de contrato de datos, fuera de alcance de una corrección de bug |
| (c) No validar en el dominio, confiar en que `CorteAgua.cerrar(Instant)` sea el único productor de `RESTABLECIDO` | Cambio mínimo | No protege la reconstrucción desde Mongo ni ningún caller futuro que no pase por `cerrar()` |

### Decisión

Se valida la coherencia en `Builder.build()` (opción a), comparando `ventana.estaCerrada()` contra
`estado == EstadoCorte.RESTABLECIDO` y lanzando `IllegalStateException` si no coinciden. En paralelo,
se centralizó la transición de cierre en `CorteAgua.cerrar(Instant finReal)` (agregado de dominio),
así que el único flujo de negocio real ya no puede producir la inconsistencia — la validación en
`build()` queda como red de seguridad para los demás caminos (tests, reconstrucción Mongo, futuros
callers).

### Consecuencias

- **Gana:** ningún camino de construcción (negocio, tests, persistencia) puede producir un
  `CorteAgua` con `estado`/`ventana` contradictorios.
- **Cuesta:** `CorteAguaMongoAdapter.aDominio()` ahora falla rápido (`IllegalStateException`, con el
  id del documento) si lee un dato corrupto, en vez de servirlo en silencio — una lectura (`GET`,
  listados, cálculo de cumplimiento) se rompería visiblemente en vez de mostrar un dato incoherente.
  Se acepta ese costo: es coherente con la ética de datos del proyecto (`CLAUDE.md`, "nada llega al
  mapa público sin verificación") y el riesgo es bajo — no hay datos de producción migrados de un
  modelo anterior en este punto del proyecto (Sprint 2-4).
- **Deja pendiente:** el campo `estado` sigue siendo redundante con la ventana; la opción (b)
  (eliminarlo) queda descartada por ahora, no evaluada de nuevo salvo que cambie el contrato de la
  API o de persistencia.

### Cómo se revierte

Quitar el `if` de coherencia en `Builder.build()` y el `try/catch` de `CorteAguaMongoAdapter.aDominio()`.
Si en el futuro se prefiere la opción (b), requiere además tocar `CorteAguaDocumento`,
`CorteApiMapper`/`CorteRespuesta` y sus tests — cambio de contrato, no solo de invariante.

---

## ADR-025 — Descartar funcionalidades de Inteligencia Artificial (M9) para cumplir plazos

- **Fecha:** 2026-08-10
- **Estado:** Aceptada

### Contexto
El Módulo 9 (Ingesta automática con IA) requería usar el SDK de Anthropic para estructurar avisos no estructurados de la prensa local y Acuacar. Sin embargo, para poder destrabar el Módulo 9 en su funcionalidad base (ingesta por heurísticas), se eliminó la dependencia de Anthropic (PR #137) ya que bloqueaba el despliegue y desarrollo por falta de API keys o limitaciones de integración.
Como consecuencia, los requisitos específicos de IA (RF032, RF033, RF034, RF035, RF036, y RNF019) quedaron huérfanos y sin posibilidad de implementación, lo cual representa un riesgo si se mantienen en el alcance.

### Decisión
Se declaran **oficialmente fuera de alcance (Descartados)** los requisitos RF032 a RF036 y el RNF019. El Módulo 9 (Ingesta) continuará funcionando mediante el `HeuristicaExtractor` (heurísticas deterministas y expresiones regulares) que ya está en `main`, sin modelos de IA.

### Consecuencias
- **Gana:** El alcance del proyecto se ajusta a la realidad del código; esto queda registrado como una decisión técnica sustentable en vez de un fallo de incumplimiento.
- **Pierde:** Se sacrifica la clasificación semántica avanzada; los falsos positivos/negativos del extractor basado en heurísticas no tendrán la confianza estructurada de la IA.

---

## ADR-026 — Open311 expone el estado agregado por sector, no cada reporte ciudadano

- **Fecha:** 2026-08-11
- **Estado:** Aceptada

### Contexto
RF039 pide "exponer los reportes bajo el estándar Open311". La lectura literal es un
`service_request` por reporte ciudadano, que es lo que hace la mayoría de implementaciones de
GeoReport v2: cada uno con su `lat`/`long`, su `requested_datetime` y su descripción.

El problema es que un reporte de AguaVigía trae la coordenada que el vecino autorizó a compartir
(RF007), y esa coordenada es su casa. Publicar la serie completa en un endpoint abierto y sin
autenticación permitiría a cualquiera reconstruir quién reportó desde dónde y a qué hora —
exactamente el tipo de inferencia que RNF008 ("sin datos personales identificables del reportante")
existe para impedir. Que cada dato suelto sea anónimo no hace anónimo al conjunto.

### Alternativas consideradas
1. **Un `service_request` por reporte, con coordenada.** Máxima fidelidad al estándar y máximo
   riesgo: es publicar un mapa de domicilios de gente que reportó sin registrarse.
2. **Un `service_request` por reporte, con la coordenada redondeada.** Mitiga, no resuelve: con
   suficientes reportes en el tiempo, la casa se vuelve a distinguir.
3. **Un `service_request` por sector afectado.** Menos granular, sin riesgo de reidentificación.

### Decisión
Se expone un `service_request` por **sector** cuyo estado no es `CON_SERVICIO`, con `address` = el
nombre del barrio. Los campos `lat`/`long` viajan ausentes, cosa que el estándar admite cuando hay
`address`: la unidad geográfica de esta API es el barrio, no un punto.

Se completan los campos que sí se pueden llenar con honestidad y que un consumidor estándar
necesita: `service_code`, `description`, `requested_datetime` y `updated_datetime`.

### Consecuencias
- **Gana:** RF039 queda cumplido y justificado en vez de incumplido, y RNF008 se sostiene también
  para el dato publicado, no solo para el almacenado.
- **Pierde:** Un consumidor que espere granularidad de reporte individual recibe granularidad de
  barrio. Para la pregunta que la plataforma responde —"¿hay agua en este barrio?"— es la unidad
  correcta de todas formas.

### Cómo se revierte
Cambiar la fuente del controlador de `SectorRepository` a `ReporteCiudadanoRepository`. Exigiría
antes una decisión explícita sobre reidentificación y, muy probablemente, dejar de publicar la
coordenada igual.

---

## ADR-027 — Modelo de privacidad y retención de la evidencia fotográfica (M10)

- **Fecha:** 2026-08-11
- **Estado:** Aceptada

### Contexto
M10 permite adjuntar una foto a un reporte. Esa foto se sirve en `/fotos/<uuid>.jpg` sin
autenticación, y hasta ahora ni el modelo de acceso ni el periodo de retención estaban escritos en
ninguna parte, mientras RNF008 y RNF009 figuraban cumplidos en la matriz.

Una foto de un tanque vacío o de una tubería rota no es un dato personal, pero puede contener una
fachada, una placa o una persona. Y a diferencia del reporte —tres campos y un timestamp—, el
binario es el dato más pesado y el de mayor riesgo si el servidor se ve comprometido.

### Alternativas consideradas
1. **Autenticar la descarga.** No hay cuentas de ciudadano en el sistema (ADR-007): habría que
   inventar una sesión solo para ver una foto que el propio autor subió para que se viera.
2. **URLs firmadas con expiración.** Requiere que el frontend renueve la firma; el proyecto no
   tiene la infraestructura de claves ni el despliegue lo justifica.
3. **URL con identificador no adivinable + retención acotada.**

### Decisión
- El nombre del archivo es un **UUID v4** generado por el servidor, nunca el nombre que mandó el
  cliente. No hay listado de directorio ni índice: sin la URL exacta no se llega a la foto.
- Se sirve con `X-Content-Type-Options: nosniff` y solo se aceptan `image/jpeg`, `image/png` y
  `image/webp` por lista blanca de `Content-Type`.
- La retención es configurable (`aguavigia.mantenimiento.retencion-evidencia`) y viene
  **deshabilitada por defecto**, para no borrar datos en la máquina de quien solo levanta el
  proyecto. En el perfil `prod` se activa con **365 días**.
- `PurgaEvidenciaAntiguaJob` borra únicamente el binario y limpia `fotoUrl`. El reporte (sector,
  tipo, timestamp, moderación, confirmaciones) se conserva indefinidamente porque sustenta RF024 y
  el Índice de Cumplimiento.

### Consecuencias
- **Gana:** RNF008/RNF009 tienen un modelo escrito y verificable en vez de un supuesto. El dato de
  mayor riesgo vence solo; el de valor histórico no.
- **Pierde:** Una URL filtrada sigue siendo pública mientras la foto exista. Es un riesgo aceptado
  y acotado por la retención.

### Cómo se revierte
Subir `dias-retencion` o poner `habilitada: false` en `prod`. Volver a un modelo autenticado exigiría
antes resolver la identidad del ciudadano, que ADR-007 dejó fuera a propósito.

---

## ADR-028 — La ingesta automatizada propone; publicar es decisión del veedor

- **Fecha:** 2026-08-11
- **Estado:** Parcialmente reemplazada por ADR-034 — sigue rigiendo para las fuentes de prensa; ya no para los boletines de Acuacar

### Contexto
Tras ADR-025, M9 quedó con `HeuristicaExtractor`: expresiones regulares sobre boletines y notas de
prensa. Su Javadoc decía que la confianza baja (0.6) "obliga a que los resultados pasen a moderación
manual (M5)", pero eso no era cierto en el código: `PipelineOrquestador` ignoraba el número y
llamaba a `SectorRepository.guardar()` directamente. Los campos `confianza`, `camposInferidos` y
`citaTextual` de `EventoExtraido` no los leía nadie.

En consecuencia, una expresión regular podía cambiar el estado público de un barrio y disparar
correo a sus suscriptores, notificación push y actualización del mapa en vivo, sin que ninguna
persona lo revisara. Una plataforma que existe para desmentir información poco confiable no puede
publicar así.

### Alternativas consideradas
1. **Publicar automáticamente por encima de un umbral de confianza.** El extractor emite un valor
   constante de 0.6: el umbral no distinguiría nada.
2. **Apagar M9.** Cumple con no desinformar y deja RF029/RF030 sin valor real.
3. **Cola de revisión, como la de reportes ciudadanos (RF018).**

### Decisión
La ingesta registra una `PropuestaIngesta` en estado `PENDIENTE`. El mapa no cambia hasta que un
veedor la aprueba desde `/api/veedor/ingesta/propuestas`. Aprobar aplica el estado al sector y anexa
el evento a la bitácora (RF026); descartar archiva la propuesta sin borrarla, para que la cola sea
auditable.

Cada propuesta guarda la `citaTextual` literal del documento y la `confianza`, que dejan de ser
código muerto: son lo que el veedor lee para decidir. Es la misma exigencia de ADR-006 (cita
verificable en toda extracción), que no se cayó con el descarte de la IA.

### Consecuencias
- **Gana:** Ningún dato entra al mapa sin que una persona lo sostenga. El patrón es el mismo que ya
  existía para moderar reportes ciudadanos, así que no hay un concepto nuevo que aprender.
- **Pierde:** M9 deja de ser tiempo real. Un corte detectado a las 3 a.m. espera a que alguien
  revise. Para el caso de uso —comparar lo prometido con lo cumplido— la exactitud importa más que
  los minutos.

### Cómo se revierte
Hacer que `RegistrarPropuestaIngestaService` cree la propuesta ya aprobada e invoque a
`RevisarPropuestaIngestaService.aprobar`. Exigiría antes un extractor cuya confianza signifique algo.

---

## ADR-029 — Adoptar un shell operativo inspirado en Adminator sin convertir la experiencia en un dashboard genérico

- **Fecha:** 2026-08-09
- **Estado:** Reemplazada por ADR-067

### Contexto

La barra horizontal anterior desaprovechaba el ancho disponible, comprimía el mapa y no establecía
una jerarquía clara entre navegación y contenido. El usuario pidió explorar como referencia el shell
de Adminator, conservando intacta la funcionalidad y pudiendo volver al estado anterior.

`DESIGN.md` §9 prohíbe que AguaVigía se convierta en un dashboard corporativo de KPIs decorativos.
La referencia se toma solo para patrones de composición: sidebar, topbar contextual, tarjetas
contenidas, sistema de tokens y drawer móvil; no se incorporan su código, dependencias ni módulos.

### Decisión

La SPA usa un shell común con sidebar fija en escritorio, drawer accesible en móvil, topbar por ruta
y un área de contenido que prioriza el mapa. Los componentes, rutas, llamadas API, formularios,
estados y manejadores existentes permanecen sin cambios de comportamiento.

### Consecuencias

- **Gana:** mejor uso del viewport, navegación consistente y jerarquía visual profesional.
- **Mantiene:** identidad turquesa de AguaVigía y protagonismo de la pregunta ciudadana, sin KPIs
  decorativos ni semántica cromática nueva.
- **Cuesta:** el shell agrega CSS responsive y un estado local para abrir/cerrar el drawer móvil.

### Cómo se revierte

Restaurar el contenido de `frontend_checkpoint_2026-08-09_antes_adminator.zip`, creado antes de la
primera modificación de este rediseño. El archivo conserva `src`, `public` y la configuración completa
del frontend en ese punto.

---

## ADR-030 — Los enlaces de `/api/suscripciones/confirmar` y `/cancelar` responden HTML o JSON según el `Accept`, no dos rutas separadas

- **Fecha:** 2026-08-12
- **Estado:** Aceptada

### Contexto
`MailNotificacionAdapter` manda el enlace de confirmación y el de baja apuntando directo al backend
(`{urlBasePublica}/api/suscripciones/confirmar?token=...`), no al frontend. Un vecino que hacía clic
desde su cliente de correo veía JSON crudo en pantalla en vez de una confirmación legible — el
endpoint solo sabía responder `application/json`.

### Alternativas consideradas
1. **El backend redirige (302) a una URL del frontend**, que muestra la página. Exige coordinar dos
   despliegues (backend y frontend) para una sola respuesta y agrega una ruta nueva al frontend solo
   para esto.
2. **Dejarlo así, sin resolver**, documentado como pendiente.
3. **El mismo endpoint decide el formato según el `Accept` de quien pide.** El enlace del correo no
   cambia.

### Decisión
`SuscripcionController.confirmar`/`cancelar` inspeccionan el header `Accept`: si contiene
`text/html` (como manda cualquier navegador al abrir un enlace), responden una página HTML mínima de
éxito o error, con el mismo texto tanto en modo claro como oscuro. Si no —ausente, `application/json`,
o cualquier cliente de API que no pida HTML explícitamente— responden el JSON de siempre, sin cambios
de contrato para nadie que ya lo consumiera así.

Se descartó registrar dos `@GetMapping` distintos sobre la misma ruta diferenciados solo por
`produces`: sin un `Accept` explícito (el caso de `MockMvc` por defecto, o de `curl` a pelo), Spring
no puede desempatar entre ambos y lanza `IllegalStateException: Ambiguous handler methods` — se
verificó rompiendo los tests existentes de `SuscripcionControllerTest` antes de corregirlo. Un único
método con la decisión hecha a mano evita la ambigüedad por completo.

### Consecuencias
- **Gana:** el enlace del correo funciona igual de bien abierto desde un navegador que desde un
  cliente de API, sin tocar `MailNotificacionAdapter` ni el contrato JSON existente.
- **Pierde:** la página HTML vive como una plantilla `String.formatted()` dentro del controlador, no
  como un archivo de plantilla reusable (`PlantillaCorreo` es de paquete privado en
  `infrastructure.mail` y no se expone a `api`). Aceptable por ahora: son dos variantes (éxito/error)
  para dos endpoints, no una plantilla que vaya a crecer.

### Cómo se revierte
Quitar la rama `prefiereHtml(accept)` de ambos métodos y volver a devolver siempre
`ResponseEntity<SuscripcionRespuesta>`. El enlace del correo seguiría funcionando igual de mal que
antes de este ADR.

---

## ADR-031 — Allowlist de gitleaks acotado a `docs/ingenieria/entorno-local.md`, no borrar la clave del documento

- **Fecha:** 2026-08-12
- **Estado:** Aceptada

### Contexto
El job "Escaneo de secretos" (`gitleaks`) empezó a fallar en `ad7d660` — el mismo commit que creó
`docs/ingenieria/entorno-local.md` con la clave de desarrollo `JWT_SECRET=jHZczr...` en texto plano,
descrita ahí mismo como "clave de desarrollo lista para copiar" para no repetir la fricción que "quedó
sin resolver durante varias sesiones seguidas". Verificado con `gh run view --log-failed`: gitleaks
detecta exactamente esa línea (`generic-api-key`, línea 42) y no hay `.gitleaks.toml` en el repo, así
que corre con la regla por defecto sin ninguna excepción.

No es un secreto de producción: `ValidacionDeSecretosProd` aborta el arranque del perfil `prod` si
`JWT_SECRET`/`VEEDOR_PASSWORD_HASH` faltan, y exige que sean propias de ese entorno — la clave del
documento solo sirve para `docker compose up` local (`docker-compose.yml`, no `.prod.yml`).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Dejar el CI en rojo | Cero cambios | Entrena a ignorar el gate; un hallazgo real futuro pasaría desapercibido en medio del ruido |
| Borrar la clave del documento, cada quien genera la suya | El scanner no tiene nada que marcar | Revive el problema que el propio `entorno-local.md` fue escrito para cerrar |
| `.gitleaks.toml` con allowlist acotado por ruta a ese único archivo | CI vuelve a verde de forma honesta (hallazgo evaluado, no ignorado); cualquier otro archivo del repo se sigue escaneando igual | Si algún día se pega un secreto real distinto en ese mismo archivo, no se detectaría |

### Decisión
Crear `.gitleaks.toml` en la raíz con `extend.useDefault = true` y un `[allowlist]` cuyo `paths`
excluye solo `docs/ingenieria/entorno-local.md`, con una descripción que explica por qué. La clave del
documento no se toca.

### Consecuencias
- **Gana:** el job "Escaneo de secretos" vuelve a estar en verde sin ocultar el motivo — el propio
  archivo de configuración documenta la excepción.
- **Pierde:** ese archivo específico queda fuera del radar de gitleaks por completo. Riesgo aceptado
  porque tiene un propósito único y declarado (credenciales de *desarrollo local*, nunca de
  producción) y quien lo edite es responsable de no meter ahí algo que sí importe.

### Cómo se revierte
Borrar `.gitleaks.toml` (o solo la entrada de `allowlist`) y rotar la clave de desarrollo del documento —
ambos pasos juntos, porque borrar solo el allowlist sin rotar la clave deja el mismo secreto expuesto
sin la excepción que lo explica.

---

## ADR-032 — La confianza de la extracción se gradúa; el veedor sigue decidiendo

- **Fecha:** 2026-08-22
- **Estado:** Parcialmente reemplazada por ADR-034 — la graduación de confianza sigue vigente; que el veedor decida ya no aplica a Acuacar

### Contexto

`ADR-028` descartó publicar automáticamente por encima de un umbral de confianza, con un argumento
concreto y correcto en su momento:

> El extractor emite un valor constante de 0.6: el umbral no distinguiría nada.

Al corregir la extracción (`BUG-057` a `BUG-060`) esa premisa dejó de ser cierta. El extractor ahora
sí distingue evidencias muy distintas entre sí: un boletín con enumeración explícita de barrios y
ventana horaria declarada no se parece en nada a una mención suelta en prosa, y hasta ahora ambos
salían con el mismo 0.6.

Medido sobre 37 boletines reales de Acuacar (mayo–agosto 2026), los tres niveles se separan de forma
limpia: el 100% de los boletines con enumeración identifica al menos un barrio del catálogo oficial,
mientras que las menciones en prosa producen sobre todo nombres genéricos que el catálogo descarta.

### Decisión

La confianza pasa a tener tres niveles, según la evidencia que el extractor encontró de verdad:

| Valor | Evidencia |
|---|---|
| `0.85` | Enumeración explícita de barrios **y** ventana horaria declarada |
| `0.75` | Enumeración explícita, sin horario |
| `0.45` | Mención en prosa, sin lista |

**No se cambia la política de publicación.** Nada se publica solo: toda propuesta sigue naciendo
`PENDIENTE` y el mapa solo se mueve cuando un veedor aprueba, exactamente como decidió `ADR-028`.
Lo que cambia es que el número sirve para **ordenar la cola** por lo que más se sostiene, y que
`citaTextual` ahora cita el tramo que nombra los barrios y el horario en vez de la frase de resumen,
que era una cita literal pero inútil para contrastar.

### Consecuencias

- **Gana:** el veedor revisa primero lo mejor respaldado y lee una cita que de verdad le permite
  decidir. Si se quisiera más adelante reabrir la publicación automática, ahora existe la
  señal que `ADR-028` echaba en falta.
- **Pierde:** los tres valores son un juicio calibrado sobre 37 boletines, no una probabilidad
  medida. No deben leerse como tal ni exponerse al público como si lo fueran.
- **Queda pendiente:** validar los umbrales contra el conjunto dorado de 100 boletines etiquetados a
  mano (`pipeline-ingesta-datos.md` §4), que sigue sin construirse.

---

## ADR-033 — El estado de un barrio evoluciona con la ventana que la fuente prometió

- **Fecha:** 2026-08-22
- **Estado:** Aceptada

### Contexto

Un boletín dice «suspensión mañana viernes 21 de agosto, entre las 9:00 a.m. y las 6:00 p.m.». Con la
ingesta corregida ese dato ya se lee y se guarda, pero nadie volvía a mirarlo: una propuesta aprobada
dejaba el barrio en un estado fijo. Un corte anunciado para mañana se quedaba en `CORTE_PROGRAMADO`
indefinidamente, y el barrio aparecía «con corte programado» semanas después de que el agua volviera.

### Alternativas consideradas

1. **Que el veedor cierre cada corte a mano.** Es lo que ya ocurre con los cortes oficiales, pero
   aplicado a la ingesta multiplica el trabajo manual por cada barrio de cada boletín — 17 en un solo
   aviso — y el estado queda mal mientras nadie entra.
2. **Estimar la duración cuando el boletín no la declara.** Descartada: es exactamente el dato
   inventado que `ADR-006` prohíbe y que ya se eliminó del extractor.
3. **Aplicar solo la ventana que la fuente declaró explícitamente.**

### Decisión

`ActualizarEstadosPorVentanaService` barre cada minuto las propuestas **ya aprobadas** que traen
ventana declarada y pone cada sector en el estado que le corresponde en ese instante:

```
antes del inicio → CORTE_PROGRAMADO
dentro           → SIN_SERVICIO (o el estado propuesto)
después del fin  → CON_SERVICIO
```

Tres restricciones que hacen que esto no contradiga `ADR-028`:

- Solo actúa sobre propuestas **que un veedor ya aprobó**. No publica nada nuevo: mueve en el tiempo
  algo que una persona ya validó.
- **Sin ventana declarada, el sector no se toca.** No se estima ni el inicio ni el fin.
- Solo escribe cuando el estado cambia de verdad, para no disparar correo, push y SSE en cada barrido.

### Consecuencias

- **Gana:** el mapa deja de envejecer solo. El ciclo «se anuncia → ocurre → termina» se refleja sin
  intervención, que es lo que un vecino espera de un mapa «en vivo».
- **Pierde:** si Acuacar promete una ventana y no la cumple, el mapa dirá que el servicio volvió
  cuando no volvió. Es un riesgo real y es precisamente lo que el Índice de Cumplimiento (RF020–RF022)
  existe para medir; la corrección vendrá de los reportes ciudadanos, no de la ingesta.
- El barrido queda acotado a un día después del fin prometido, para no recorrer el histórico entero.

## ADR-034 — Los boletines de Acuacar se publican solos; la revisión del veedor queda para la prensa y los reportes ciudadanos

- **Fecha:** 2026-08-29
- **Estado:** Aceptada

### Contexto
`ADR-028` mandó toda detección a una cola de revisión. Medido en local el 2026-08-29 con la base de
producción de desarrollo: **17 propuestas PENDIENTE, 0 cortes, 0 eventos de bitácora, 211 barrios sin
estado**. El mapa llevaba semanas vacío no por falta de datos sino porque nadie vaciaba la cola, y el
único camino para hacerlo (`/veedor`) respondía `503` por dos variables sin configurar. El costo real
de la cola no fue prudencia: fue que la plataforma no publicó nada.

El argumento de `ADR-028` era que *"una expresión regular sobre una nota de prensa"* no puede mover el
mapa sola. Ese argumento es correcto y sigue en pie **para la prensa**. Pero no describe a Acuacar:
las 17 propuestas venían de la API oficial del operador, con `citaTextual` que enumera los barrios y
la ventana horaria, `urlOriginal` verificable y confianza 0.85 —el nivel más alto que `ADR-032`
reserva para boletines con enumeración explícita—. Acuacar no es una fuente *sobre* el corte: es
quien lo ejecuta y lo anuncia.

En paralelo, los 211 barrios en gris (`COLOR_SIN_DATOS`) hacían ver el mapa averiado. La petición
inicial fue pintarlos de verde por descarte —"si nadie reporta, es que tiene agua"—, que es publicar
un *todo despejado* que nadie verificó y choca de frente con la regla 4 de ética de datos.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Mantener `ADR-028` intacta | Ninguna publicación sin humano | Demostrado: el mapa se queda vacío. La cola no se vacía sola |
| Publicar todo automático (Acuacar + prensa) | Mapa siempre lleno | Reintroduce exactamente el riesgo que `ADR-028` cerró: una regex sobre prensa moviendo el mapa |
| **Publicar solo lo oficial** | Acuacar es el operador, con cita y URL; la prensa sigue revisada | Un error del propio boletín se publica sin filtro |
| Sin datos → verde pleno | Mapa uniforme y vivo | Afirma servicio en 211 barrios sin verificar. Un barrio sin agua donde nadie reportó saldría "con agua" |
| **Sin datos → verde pálido, "Sin reportes de falla"** | Mapa vivo sin afirmar lo que no se sabe | Un tercer verde que hay que saber leer; el color por sí solo puede leerse como "todo bien" |

### Decisión
Lo que viene de Acuacar (`PropuestaIngesta.esDeFuenteOficial()`) se publica en el acto, delegando en
el mismo `RevisarPropuestaIngestaUseCase` que usa el panel. Lo que viene de prensa (RSS) y los
reportes ciudadanos siguen esperando al veedor. Los barrios sin dato pasan de gris a verde pálido
`#9FD8AB` con la etiqueta **"Sin reportes de falla"** — que describe el dato, no lo que se supone de él.

### Consecuencias
- El mapa se llena solo con lo oficial: las 17 propuestas represadas se publicaron y dejaron 17
  eventos de bitácora y 17 barrios en `CON_SERVICIO`.
- **El veedor deja de ser cuello de botella y pasa a ser moderador de lo ciudadano**, que es donde su
  criterio aporta: el reporte anónimo es lo que nadie más puede validar.
- Se acepta un riesgo nuevo y real: **si Acuacar publica un boletín equivocado, ese error llega al
  mapa sin filtro humano** y dispara correo, push y SSE. Se mitiga con la trazabilidad —cada estado
  publicado conserva `citaTextual` y `urlOriginal`— pero no se elimina.
- Publicar automático reusa el caso de uso del veedor a propósito: el camino automático y el manual
  no pueden divergir, y la guarda de estado repetido y el evento de bitácora valen para ambos.
- El verde pálido depende de que la leyenda se lea. `DESIGN.md` §2 ya exige que el color nunca vaya
  solo, y `InsigniaEstado` siempre muestra la etiqueta; si esa regla se rompe, este verde miente.

### Cómo se revierte
Quitar la rama `esDeFuenteOficial()` de `RegistrarPropuestaIngestaService` devuelve todo a la cola:
es un `if`, y las propuestas siguen naciendo `PENDIENTE`. Lo ya publicado **no** se revierte solo —
la bitácora es de solo anexado (`RF028`) y los eventos emitidos quedan. Volver atrás exige además
restaurar `ADR-028` y `ADR-032` a *Aceptada*. El color es un cambio de una constante.

## ADR-035 — Sin corte anunciado ni reporte vigente, el barrio se muestra con servicio

- **Fecha:** 2026-08-30
- **Estado:** Reemplazada por ADR-069

### Contexto
`ADR-014` decidió lo contrario y su argumento era correcto **en su momento**: el 2026-08-08 no
existía la ingesta, ningún proceso miraba a Acuacar, y los 211 barrios estaban sin estado por
ausencia de sistema. En ese mundo, pintar de verde era afirmar sobre un vacío, y `BUG-061` (S1) se
cerró quitando justamente el `sector?.estado ?? 'CON_SERVICIO'` del mapa.

El supuesto cambió. Desde `ADR-034` el colector revisa la API de Acuacar cada 10 minutos sobre una
ventana de 7 días y **publica sin intervención**. La ausencia de aviso dejó de ser "no tenemos
sistema" y pasó a ser una señal que se mantiene sola. Verificado el 2026-08-30 sobre los 40
boletines más recientes: 7 hablan de suspensión del servicio, y no solo de mantenimiento programado
—incluyen trabajo reactivo como *"repara fuga en tubería"* y *"avanza en la reparación de la
conducción"*—, así que Acuacar no publica únicamente lo planificado.

Hay además un segundo canal correctivo que `ADR-014` no tenía: el reporte ciudadano moderado por el
veedor, que puede sacar a un barrio del verde sin esperar a que Acuacar diga nada.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Mantener `ADR-014` (gris "sin datos") | No afirma nada sin verificar | 194 de 211 barrios en gris permanente: el mapa se lee como app rota, no como app prudente |
| Verde pálido distinto, "Sin reportes de falla" | Mapa vivo sin igualar lo sabido con lo supuesto | Obliga al lector a distinguir dos verdes; y si la premisa es que Acuacar cubre los cortes, la distinción no describe nada real |
| **Verde de `CON_SERVICIO`, sin marca de tiempo** | Coincide con el modelo real: el barrio tiene agua salvo aviso del operador o reporte ciudadano | Un corte que Acuacar aún no publicó y que nadie reportó se ve verde |

### Decisión
`COLOR_SIN_DATOS` pasa a los valores y la etiqueta de `CON_SERVICIO`. **No se fabrica
`actualizadoEn`**: el backend sigue mandando `estado: null` y `actualizadoEn: null`, así que
`useFrescura` sigue diciendo *"sin datos"*. El resaltado del mapa trata el nulo como `CON_SERVICIO`
(`MapaCartagena.tsx`, `estadoEfectivo`) para que filtrar por *Con servicio* no atenúe justo a los
barrios que esta regla considera con agua.

### Consecuencias
- **Gana:** el mapa comunica el estado real de la ciudad en vez de un gris que nadie sabía leer.
- **Pierde y hay que decirlo:** **un corte que Acuacar no haya publicado todavía y que ningún
  ciudadano haya reportado se muestra verde.** Es el riesgo que `ADR-014` quiso evitar y no
  desaparece; se acota con la ingesta cada 10 minutos y con el reporte ciudadano, no se elimina.
- **El contrato no cambia:** `estado` sigue siendo anulable y el backend sigue sin inventar nada.
  Esto es una decisión de presentación, y por eso `ADR-014` queda *parcialmente* reemplazada.
- **`BUG-061` sigue cerrado y su corrección intacta:** su defecto real era `actualizadoEn: new
  Date()`, que afirmaba una verificación inexistente. Eso no vuelve.
- **Límite conocido:** un barrio del GeoJSON ausente del catálogo del backend no lo vigila la
  ingesta, así que ahí el verde no estaría respaldado. Hoy no ocurre —verificado el 2026-08-30: 211
  en el GeoJSON, 211 en el catálogo, 0 de diferencia— y el mapa además lo dibujaría al 15% de opacidad.
  Si esa cifra deja de ser 0, esta decisión debe revisarse.

### Cómo se revierte
Devolver `COLOR_SIN_DATOS` a un color y etiqueta propios y quitar `estadoEfectivo` de
`calcularEstiloFeature`. Son dos cambios de presentación y ningún dato guardado cambia, porque
ninguno se fabricó.

## ADR-036 — La ingesta crea el corte pero nunca su hora real de restablecimiento

- **Fecha:** 2026-08-31
- **Estado:** Aceptada

### Contexto
Las estadísticas (M7) y el Índice de Cumplimiento (M6) agregan sobre la colección `cortes`, que la
ingesta nunca alimentaba: solo la llenaba el veedor a mano. Con 1.106 propuestas aprobadas y 0
cortes, `sectoresMasAfectados` y `cortesPorDiaDeSemana` salían vacíos aunque hubiera datos de sobra.

Al crear el corte aparece la tentación evidente: el boletín trae `inicioDeclarado` y `finPrometido`,
así que rellenar `finReal = finPrometido` deja las tres métricas completas de inmediato.

**Eso sería una mentira, y de la peor clase para este proyecto.** El Índice de Cumplimiento existe
para comparar lo prometido con lo real; igualarlos por defecto da **100% de cumplimiento
permanente**, que es exactamente la afirmación que la plataforma existe para poder contrastar.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| No crear cortes desde la ingesta | Nada que discutir | Las estadísticas se quedan vacías con 1.106 propuestas disponibles |
| Crear el corte con `finReal = finPrometido` | Las tres métricas se llenan solas | Cumplimiento del 100% inventado. Destruye la tesis del proyecto |
| **Crear el corte sin `finReal`** | Se puebla lo que sí se sabe; lo que no se sabe se queda vacío y se nota | `duracionPromedioHoras` y el Índice siguen sin datos hasta que alguien confirme la hora real |

### Decisión
Al aprobar una propuesta con ventana declarada se crea un `CorteAgua` con `OrigenCorte.INGESTA_IA`
y estado `ANUNCIADO`. **`finReal` se deja nulo.** El corte queda abierto hasta que el consenso
ciudadano o el veedor confirmen cuándo volvió el agua.

El id del corte se deriva del boletín y su ventana, no de un UUID nuevo: un boletín nombra muchos
barrios y genera una propuesta por cada uno, y sin esa clave la estadística se inflaba con un corte
por barrio en vez de uno por evento.

### Consecuencias
- `sectoresMasAfectados` y `cortesPorDiaDeSemana` se pueblan con datos reales.
- **`duracionPromedioHoras` y `/api/cumplimiento` siguen vacíos, y es correcto que lo estén.** El
  endpoint responde *"No hay cortes cerrados todavía"* porque de verdad no los hay.
- La interfaz debe decir "Sin datos" y nunca un número: ver `BUG-063`, que es justo lo que pasó.
- Queda cubierto por `RevisarPropuestaIngestaServiceTest.debeRegistrarElCorteDelBoletinCuandoDeclaraVentana`,
  que falla si alguien rellena `finReal` desde la ingesta.

### Cómo se revierte
Quitar la llamada a `registrarCorteDelBoletin`. Los cortes ya creados no se borran solos; habría que
eliminar los de origen `INGESTA_IA` a mano.

## ADR-037 — «Sectores más afectados» cuenta menciones en avisos, no cortes con duración medida

- **Fecha:** 2026-08-31
- **Estado:** Aceptada

### Contexto
Un `CorteAgua` exige ventana completa (inicio + fin prometido). Medido sobre los 100 boletines más
recientes de Acuacar el 30/08/2026: **18 anuncian suspensión del servicio, los 18 traen la fecha,
pero solo 5 declaran el rango horario**. Es decir, ~5% de los boletines pueden generar un corte.

Contra la colección `cortes`, el top de sectores se calculaba sobre 3 registros: no representaba la
ciudad ni de lejos, y desde luego no los cinco años de historia que se quería mostrar.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Dejarlo contra `cortes` | El número significa "cortes con duración medida" | Se calcula sobre 3 registros; el ranking no dice nada |
| Inventar la ventana que falta | Habría muchos más cortes | Dato fabricado; contamina además el Índice de Cumplimiento |
| **Contar propuestas aprobadas** | 1.106 menciones desde 2020: cubre los cinco años | Cambia lo que el número significa, y hay que decirlo donde se muestre |

### Decisión
`EstadisticasMongoAdapter.calcularGlobales` agrega `propuestas_ingesta` con `estadoRevision:
APROBADA` en vez de `cortes`. El número pasa a ser **«veces que el barrio apareció en un aviso de
corte»**, no «cortes con duración medida».

### Consecuencias
- El ranking cubre cinco años y sí representa a la ciudad.
- **Cambia el significado de la cifra**, así que el rótulo donde se muestre tiene que decirlo. Hoy
  la sección dice "Cortes cerrados registrados por barrio", que ya no describe lo que cuenta —
  queda pendiente corregir ese texto.
- Una propuesta sin aprobar no cuenta: nadie ha confirmado que ese aviso sea real.
- La otra cifra —cortes con duración medida— sigue viviendo en el Índice de Cumplimiento, que sigue
  exigiendo ventana real (`ADR-036`).

### Cómo se revierte
Volver a agregar sobre `CorteAguaDocumento` y restaurar el `unwind` por `sectoresAfectados`.

## ADR-038 — Las portadas de Acuacar se sirven por proxy propio, no enlazadas directo

- **Fecha:** 2026-08-31
- **Estado:** Aceptada

### Contexto
Las tarjetas de la bitácora muestran la portada del boletín. Enlazarla directo a
`acuacar.com/wp-content/uploads/…` no funciona: **el sitio bloquea el hotlinking**. Verificado el
31/08/2026 sobre la misma imagen — responde `200 image/jpeg` sin cabecera `Referer` y **`403` con un
`Referer` de otro dominio**. Por eso todas las pruebas con `curl` pasaban y el navegador fallaba
siempre, y por eso costó tanto encontrarlo.

Aparte, `_embed=wp:featuredmedia` **no devuelve nada si `_fields` recorta `_links`**: WordPress
construye `_embedded` a partir de los enlaces del recurso. Sin `_links` el boletín llega sin imagen
y sin ningún error que lo delate.

### Decisión
El colector captura la URL de la portada al ingerir (tamaño `medium`, no el original de varios MB) y
viaja con el evento hasta la API. El navegador la pide por `/acuacar-media/`, que `nginx.conf` y
`vite.config.ts` proxean sin mandar `Referer`.

El bloque de nginx usa `location ^~` a propósito: más abajo hay un `location ~* \.(jpg|png|…)$` para
los assets propios, y en nginx las expresiones regulares ganan sobre los prefijos — sin `^~` la
portada caía en ese bloque, se buscaba en el disco local y devolvía 404.

### Consecuencias
- **Gana:** la portada funciona para cualquier evento, también los de 2020. La alternativa —que el
  navegador pidiera los boletines recientes y cruzara por URL— solo cubría los últimos 100.
- **Pierde:** el proxy queda acotado a `/wp-content/uploads/`. Abrirlo a todo el dominio lo
  convertiría en un proxy abierto hacia acuacar.com.
- Se respeta la ética de datos: el proxy se identifica con el `User-Agent` del proyecto y no
  disfraza nada; lo único que omite es el `Referer`.

### Cómo se revierte
Volver a apuntar `src` a la URL de acuacar.com y borrar los dos bloques de proxy. La portada dejará
de verse, que es el estado del que se venía.


## ADR-039 — El panel del veedor usa cuentas individuales con rol y ajustes de permisos por persona

- **Fecha:** 2026-08-31
- **Estado:** Aceptada
- **Reemplaza a:** `ADR-016`

### Contexto

`ADR-016` eligió una credencial compartida y dejó escrito su propio costo: *"ninguna acción del panel
queda atribuida a una persona concreta"*, y señaló que migrar a cuentas individuales exigiría una
entidad de dominio. También dejó abierto que `POST /api/veedor/sesion` no tenía freno contra fuerza
bruta; `ADR-018` cerró la mitad de ese hueco con un límite por IP, que no ve el ataque repartido
entre muchas direcciones contra un mismo correo.

Ahora se pide lo que aquella ADR aplazó: que cada persona se registre con su correo, y que un
administrador decida desde el panel quién entra y qué puede hacer. `RF019` solo exige *"autenticación
con token"* y `RNF011` fija la expiración en 8 horas — ninguno de los dos dice nada sobre el modelo de
cuentas, así que esto es requisito nuevo (`RF042`–`RF046`), no una reinterpretación.

Verificado en local el 2026-08-31 sobre el stack de `docker compose`: el flujo completo —siembra del
primer administrador, alta de TOTP con un código calculado fuera del sistema, registro abierto,
verificación por correo, aprobación con permisos recortados, suspensión y revocación— funciona de
punta a punta.

### Alternativas consideradas

| Opción | A favor | En contra |
|---|---|---|
| Seguir con la credencial compartida (`ADR-016`) | Cero trabajo | No atribuye ninguna acción a nadie; una filtración obliga a rotar la clave de todas las personas del panel a la vez |
| Solo invitación del administrador | Superficie mínima; nadie llega sin que alguien lo llame | No es lo que se pidió, y obliga a un administrador disponible para cada alta |
| Solo auto-registro sin aprobación | El alta no depende de nadie | Registro abierto = panel de moderación abierto. Inaceptable |
| **Auto-registro + invitación, ambos con aprobación o rol asignado** (elegida) | Cubre las dos formas de entrar; en ninguna se concede acceso sin decisión humana | Dos flujos de alta y dos tipos de token que mantener |
| Roles fijos sin ajustes | Imposible dejar a alguien mal configurado | No permite el caso real de "veedor que no cierra cortes" |
| **Roles como paquete de permisos + ajustes por persona** (elegida) | El día a día es elegir un rol; el caso raro se resuelve sin inventar un rol nuevo | Para saber qué puede hacer alguien hay que resolver rol + excepciones, no basta con leer el rol |

### Decisión

Entidad `Usuario` en `domain/`, con `EstadoCuenta` (verificación → aprobación → activa, más invitada,
suspendida y rechazada), `RolVeedor` (`ADMIN`, `VEEDOR`, `OBSERVADOR`) y `PermisosEfectivos`
(rol + concedidos − revocados). **La autorización se comprueba siempre contra un `Permiso` concreto**
vía `@PreAuthorize`, nunca contra el rol: añadir un rol no obliga a repasar cada endpoint.

`VEEDOR_PASSWORD_HASH` deja de ser una credencial compartida y pasa a ser la semilla del primer
administrador (`SembradorAdminInicial`, junto con `ADMIN_INICIAL_CORREO`); en cuanto existe alguna
cuenta, deja de usarse.

Cinco medidas sostienen la parte de seguridad, y cada una cubre un agujero distinto:

1. **Enumeración.** Registro, login y "olvidé mi clave" responden igual exista o no la cuenta. El
   login además gasta el mismo tiempo (`CifradorClavePort.gastarTiempoEquivalente`), porque con
   mensajes idénticos y tiempos distintos el cronómetro sigue delatando qué correos existen.
2. **Fuerza bruta por cuenta.** Bloqueo tras 5 fallos en 15 minutos (`ControlIntentosPort`, Redis),
   complementario al límite por IP de `ADR-018`. La clave es el correo, también cuando no existe.
3. **Revocación inmediata.** El token lleva los permisos dentro para no leer Mongo en cada petición;
   a cambio, suspender, rechazar, cambiar permisos —también al ampliarlos— o cerrar sesión escribe un
   instante de corte en Redis, y el filtro rechaza todo token anterior. Sin esa pareja de medidas,
   meter los permisos en el token sería un error.
4. **Segundo factor obligatorio para `ADMIN`** (TOTP, RFC 6238, implementado sin dependencia nueva).
   Un administrador sin TOTP entra con una sesión de alcance `ALTA_SEGUNDO_FACTOR` que solo abre el
   alta: negarle la entrada lo dejaría fuera para siempre, y darle sesión completa haría que
   "obligatorio" no significara nada.
5. **Guardas de integridad.** Nadie se administra a sí mismo, y no se puede suspender ni despromover
   al último `ADMIN` activo — eso deja el sistema sin nadie capaz de otorgar permisos, y no se
   arregla desde la aplicación.

Todo cambio de acceso queda en una bitácora de auditoría de solo anexado (`auditoria_cuentas`), que
es exactamente la carencia que `ADR-016` se reprochó.

### Consecuencias

- **Gana:** cada acción del panel queda atribuida a una persona con nombre y correo — trazabilidad
  directa. Una filtración afecta a una cuenta, no a todas. Un `OBSERVADOR`
  puede acompañar la moderación sin poder ejecutarla.
- **Pierde:** la superficie crece mucho. Diez casos de uso nuevos, tres colecciones de Mongo, dos
  claves de Redis, dos plantillas de correo y cinco pantallas. Es la parte del sistema con más
  código por requisito.
- **Pierde:** el reparto de fallo abierto/cerrado es deliberadamente asimétrico y hay que recordarlo.
  Con Redis caído, el bloqueo por intentos falla **abierto** (la cuenta sigue protegida por su clave)
  pero la revocación falla **cerrado** (el panel deja de aceptar sesiones). Las dos decisiones están
  justificadas en el javadoc de su adaptador; leer una y suponer la otra lleva a conclusiones falsas.
- **Pierde:** un token emitido dentro del mismo segundo en que se revocó sobrevive, porque el `iat`
  de un JWT tiene precisión de segundo. Redondear hacia arriba mataría el token que la propia persona
  acaba de obtener al volver a entrar. El margen es de un segundo y está documentado en el filtro.
- **Condiciona:** los permisos viajan dentro del token, así que **cualquier** cambio de permisos debe
  revocar sesiones. Quien añada una vía nueva para cambiarlos y olvide la revocación deja a esa
  persona operando con los permisos viejos hasta 8 horas.
- **Condiciona:** `CONFIGURAR_SEGUNDO_FACTOR` no se puede revocar. `PermisosEfectivos` lo rechaza al
  construir, porque es la única puerta que dejaría a un `ADMIN` sin forma de entrar.
- **Condiciona:** los enlaces de los correos apuntan al frontend (`APP_URL_PUBLICA`), no a la API. Si
  esa variable apunta al backend, los correos llevan a respuestas JSON.

### Cómo se revierte

No se revierte a `ADR-016` sin perder las cuentas ya creadas. Lo que sí se puede desactivar por
partes: dejar `ADMIN_INICIAL_CORREO` vacío desactiva la siembra; bajar el rol del único `ADMIN` a
`VEEDOR` desactiva de hecho la gestión de cuentas; y quitar `exigeSegundoFactor()` de `RolVeedor`
apaga el TOTP obligatorio sin tocar nada más. Volver a una clave compartida exigiría reponer el
`VeedorAuthController` anterior y aceptar que la auditoría deje de atribuir acciones.


## ADR-040 — Adoptar OpenSpec como capa de especificación viva, sin mover la bitácora de decisiones

- **Fecha:** 2026-09-04
- **Estado:** Reemplazada por ADR-047

### Contexto
El repositorio describe lo que el sistema hace en cuatro sitios a la vez: `docs/product-requirements.md`
(los RF y RNF), `docs/ingenieria/matriz-trazabilidad.md` (RF → prueba), `backend/openapi.yaml` (el
contrato HTTP) y los nombres de las 563 pruebas. Ninguno de los cuatro es ejecutable como
especificación: nada falla si el código y el requisito se separan, y ya ocurrió — `RNF017` decía en
el PRD que la validación estricta de cobertura «fue removida» mientras el `pom.xml` la exigía al 85%.

Además, el trabajo entra al repositorio sin un artefacto previo que diga qué se va a cambiar y por
qué. Las decisiones se registran aquí *después*, y los bugs en `registro-de-bugs.md` *después*. Falta
el paso de antes.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Seguir solo con el PRD y la matriz | Cero herramienta nueva; ya se conoce | Nada verifica que el documento y el código digan lo mismo; ya se desincronizaron |
| Migrar los ADR a un archivo por decisión bajo `docs/adr/` | Archivos más cortos, menos conflictos de merge | Rompe las ~200 referencias `ADR-0XX` del repositorio y la skill `registrar-decision`, a cambio de comodidad |
| Adoptar OpenSpec y mover los ADR dentro | Un solo sitio para todo | La bitácora append-only y los ADR ya funcionan; moverlos es riesgo sin ganancia |
| Adoptar OpenSpec solo como capa de especificación | Specs validables por CLI y un flujo de propuesta antes del código; no toca lo que ya sirve | Dos directorios que hay que distinguir |

### Decisión
Se adopta OpenSpec en `openspec/`, con las trece capacidades ya construidas capturadas como specs
vivas y validadas con `openspec validate --specs`. Los ADR **se quedan** en este archivo, append-only
y con su numeración intacta.

El reparto es: **`openspec/specs/` dice qué hace el sistema hoy** (comportamiento observable, en
`WHEN`/`THEN`); **`docs/design-decisions.md` dice por qué se decidió así** (contexto, alternativas
descartadas, cómo se revierte); **`docs/product-requirements.md` sigue siendo la especificación de requisitos** con
los identificadores `RF`/`RNF`. Cada spec cita los `RF` y los `ADR` que la
sostienen, y ningún dato se duplica: la spec no repite el porqué, el ADR no repite el comportamiento.

Un cambio de comportamiento entra por `openspec/changes/` antes de tocar el código; si además elige
entre alternativas técnicas, deja su ADR aquí.

### Consecuencias
- **Gana:** la especificación deja de ser prosa y pasa a ser un artefacto validable; el desfase entre
  documento y código se vuelve detectable con un comando en vez de con una lectura atenta.
- **Gana:** el trabajo tiene un artefacto de «antes», que era el hueco del protocolo actual.
- **Pierde:** una herramienta más que instalar (`openspec`, global de npm) y un directorio más que
  entender al entrar al proyecto.
- **Condiciona:** los `RF` y `RNF` del PRD siguen siendo la numeración oficial. Las
  specs los citan, no los reemplazan ni los renumeran.
- **Condiciona:** una spec que se separa del código es un defecto, igual que una prueba que miente.
  Quien cambie comportamiento actualiza su spec en el mismo PR.

### Cómo se revierte
Borrar `openspec/`, las seis skills `openspec-*` de `.claude/skills/` y `.agents/skills/`, y los
comandos `opsx:*`. Nada más depende de ello: ni la build, ni el CI, ni el contrato OpenAPI. Los ADR y
el PRD quedan como estaban.

---

## ADR-041 — El rediseño «carta náutica» se archiva; la paleta oficial sigue siendo la de DESIGN.md

- **Fecha:** 2026-09-04
- **Estado:** Aceptada

### Contexto
La rama `rediseno/frontend-premium` sostenía un rediseño completo del frontend —fondo crema
`#EDE7D8`, acento `#14657A`, tipografía serif de display, «cuaderno de sondas», «brecha»— hecho sobre
una base que quedó 35 commits por detrás de `origin/main`. En ese mismo intervalo, `origin/main`
construyó M15 (cuentas individuales, roles, TOTP, verificación de correo), la ingesta precisa de
Acuacar y las tarjetas de bitácora: 13.050 líneas de funcionalidad nueva contra 3.574 líneas
puramente visuales.

Al fusionar, chocaron 16 archivos. El choque no era técnico sino de producto: dos lenguajes visuales
distintos para el mismo sistema.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Que gane la carta náutica y portar M15 encima | Preserva el trabajo visual completo | Reconstruir a mano pantallas de cuentas, roles y TOTP ya probadas; máximo riesgo de romper funcionalidad verificada |
| Híbrido: función de `origin/main` con piel de carta náutica | Conserva ambas cosas en teoría | Las pantallas nuevas de M15 no tienen estilos de carta náutica; quedarían a medio vestir, y habría que reescribir `DESIGN.md` |
| Que gane `origin/main` | Merge limpio; conserva toda la funcionalidad; su paleta ya es la que `DESIGN.md` declara oficial | Se descarta el trabajo visual de seis commits |

### Decisión
En los 16 archivos en conflicto —y en `frontend/` entero, porque `origin/main` seguía usando los
componentes que la otra rama había retirado (`GooeyNav`, `SplashScreen`, `PanelProyecto`,
`WaterField`, `StrokeText`)— gana `origin/main`. El rediseño «carta náutica» queda archivado en el
tag `archivo/PUNTO-DE-PARTIDA-2026-09-04`.

`DESIGN.md` §3 sigue siendo la paleta oficial: acento turquesa `#087f8c` en claro y `#54c6ca` en
oscuro, sobre fondo `#f3f8f7`.

### Consecuencias
- **Gana:** una sola identidad visual, coherente con el documento que la declara, y ninguna
  funcionalidad perdida.
- **Pierde:** seis commits de trabajo visual que no llegan a producción. Recuperables desde el tag,
  como propuesta con su propio ADR si se retoma.
- **Condiciona:** el token `--font-cuerpo` no debe volver a nombrar `Inter`. `DESIGN.md` §9 la
  descarta explícitamente y, además, el proyecto no carga webfonts: nombrarla solo produce una fuente
  que nunca se aplica.

---


## ADR-042 — Los cuatro colores de estado se fijan en una sola pareja de valores, elegida por contraste

- **Fecha:** 2026-09-04
- **Estado:** Aceptada

### Contexto
Los cuatro colores de estado son «la única jerarquía cromática que importa» (`DESIGN.md` §2), y
estaban definidos en **seis sitios con cinco valores distintos** para el mismo estado:

| Dónde | «con servicio» |
|---|---|
| `:root` de `index.css` | `#1c7f55` |
| `@media (prefers-color-scheme: dark)`, primer bloque | `#30d158` |
| `:root[data-theme="dark"]` | `#4fbf89` |
| `@media dark` con `:not([data-theme="light"])`, segundo bloque | `#4fbf89` |
| `:root[data-theme="light"]` | `#34c759` |
| `.panel-mapa-unificado` (isla oscura) | `#30d158` |
| `COLOR_POR_ESTADO` de `tipos-dominio.ts` | `#34c759` claro / `#4FBF89` oscuro |

Tres consecuencias, todas comprobadas en el navegador con el dev server:

1. **El tema claro cambiaba de colores según cómo se llegara a él.** Con el sistema en claro y sin
   tocar el interruptor salía `#1c7f55`; al pulsar «claro», `#34c759`. El mismo tema, dos paletas.
2. **El primer bloque `@media dark` era CSS muerto.** El segundo tiene la misma consulta, mayor
   especificidad y viene después, así que sus cuatro valores nunca llegaban a aplicarse.
3. **El mapa y su leyenda discrepaban.** Los polígonos se pintan desde `tipos-dominio.ts` y la
   leyenda desde el CSS: en el tema claro por defecto, el mapa decía `#34c759` y la leyenda
   `#1c7f55` para el mismo estado.

Al medir el contraste apareció el fondo del asunto: **los valores de `DESIGN.md` §2 no pasaban el
AA que el propio `DESIGN.md` §7 exige.** Sobre superficie clara `#fbfdfc`:

| Estado | Valor §2 | Contraste | Valor de `:root` | Contraste |
|---|---|---|---|---|
| Con servicio | `#34c759` | **2.17:1** | `#1c7f55` | 4.88:1 |
| Sin servicio | `#ff453a` | **3.33:1** | `#ae3428` | 6.19:1 |
| Presión baja | `#ff9f0a` | **2.01:1** | `#a87310` | **4.01:1** |
| Corte programado | `#98989d` | **2.81:1** | `#2a628f` | 6.34:1 |

Es el peor escenario posible para este producto: el usuario que describe `DESIGN.md` §1 está de pie,
en la calle, con el sol en la pantalla.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Dejar los valores de §2 y bajar el listón de contraste | La paleta viva se ve mejor en una captura | Contradice §7 y falla justo con el sol de Cartagena, que es el caso de uso real |
| Usar la paleta viva solo como relleno y otra para texto | Cada uso con su color óptimo | Dos verdes para «con servicio»; el vecino no sabe que uno es relleno y otro texto |
| Fijar una sola pareja por estado, elegida por contraste | Un color = un estado, en todo el producto y en ambos temas | Los tonos claros son menos vivos que la paleta de la que salieron |

### Decisión
Una sola pareja de valores por estado, la que pasa AA, replicada en exactamente dos sitios que deben
moverse juntos: `--color-estado-*` en `index.css` y `COLOR_POR_ESTADO` en `tipos-dominio.ts`.

| Estado | Claro | Oscuro |
|---|---|---|
| Con servicio | `#1c7f55` | `#4fbf89` |
| Sin servicio | `#ae3428` | `#e2695b` |
| Presión baja | `#94640c` | `#d9a63c` |
| Corte programado | `#2a628f` | `#6ba8da` |

El ámbar claro pasa de `#a87310` a `#94640c`: el anterior daba 4.01:1 y no llegaba al umbral. El
nuevo da 5.03:1 sobre superficie y 4.75:1 sobre el fondo de página.

Se eliminan las tres redefiniciones sobrantes: el bloque muerto de `@media dark`, la del interruptor
`[data-theme="light"]` y la de la isla oscura de `.panel-mapa-unificado`, que ahora heredan. `DESIGN.md`
§2 queda actualizado con estos valores y con la medición que los sostiene.

### Consecuencias
- **Gana:** un color significa un estado, y significa lo mismo en el mapa, en la leyenda, en las dos
  formas de llegar a cada tema y dentro del panel del mapa.
- **Gana:** los cuatro estados pasan AA como texto en ambos temas, que es lo que §7 pedía y no se
  cumplía.
- **Pierde:** los tonos del tema claro son más apagados que los de la paleta viva de Apple de la que
  salieron. Es el precio de que se lean con sol.
- **Condiciona:** `index.css` y `tipos-dominio.ts` son ahora una pareja. Cambiar uno sin el otro
  vuelve a partir el mapa de su leyenda, y ninguna prueba lo detecta hoy.

### Cómo se revierte
Se revierte cambiando los cuatro valores en los dos archivos. Volver a los de §2 exigiría además
bajar el umbral de contraste de §7 o dejar de usarlos como color de texto.

---

## ADR-043 — Allowlist de gitleaks acotado al valor de la semilla TOTP de prueba del RFC 6238

- **Fecha:** 2026-09-04
- **Estado:** Aceptada

### Contexto
El job "Escaneo de secretos" (`gitleaks`) quedó en rojo en `main` desde el merge del
PR #152 (`c56afcb`). Verificado con
`gh run view 33874369838 --log-failed`: el hallazgo es la regla `generic-api-key` sobre
`backend/src/test/java/com/aguavigia/ctg/infrastructure/security/TotpAdapterTest.java:106`,
introducido en el commit `df216373` (2026-09-01).

El valor marcado es `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ` — la codificación Base32 de la cadena
`"12345678901234567890"`, que es la semilla de prueba oficial de los vectores de test del RFC 4226
§B.1 / RFC 6238, reutilizada en el propio test de este proyecto (`SEMILLA_DEL_RFC`,
`TotpAdapterTest.java`) para verificar que `uriDeAlta(...)` arma bien el `otpauth://` con
`secret=`, `issuer=`, `digits=` y `period=`. No es una clave que exista fuera de ese test: no abre
ninguna cuenta real ni corresponde a `JWT_SECRET`/`VEEDOR_PASSWORD_HASH`/`ANTHROPIC_API_KEY` ni a
ningún secreto de `.env`.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Dejar el CI en rojo | Cero cambios | Mismo riesgo que `ADR-031`: entrena a ignorar el gate |
| Cambiar el valor del test por otro inventado | El scanner no tiene nada que marcar | Deja de probar contra el vector oficial del RFC — si `TotpAdapter` alguna vez rompiera el caso de RFC, el test ya no lo detectaría con la misma autoridad |
| `paths` en `.gitleaks.toml` que excluye todo `TotpAdapterTest.java` (mismo patrón de `ADR-031`) | Simple, consistente con la excepción ya existente | Más ancho de lo necesario: ese archivo podría ganar un secreto real distinto en el futuro y quedaría fuera del radar |
| `regexes` en `.gitleaks.toml` que excluye solo ese valor exacto, en cualquier archivo | Tan acotado como es posible — no exime ningún archivo ni ninguna otra cadena | Si el mismo valor apareciera alguna vez como parte de un secreto real (improbable: es un vector público del RFC), tampoco se detectaría |

### Decisión
Agregar `regexes` al `[allowlist]` de `.gitleaks.toml` con el valor literal
`GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`, sin tocar `paths` (que sigue acotado solo a
`docs/ingenieria/entorno-local.md`, `ADR-031`). El test no se modifica.

### Consecuencias
- **Gana:** el job "Escaneo de secretos" vuelve a verde sin ocultar el motivo, y sin ampliar la
  superficie de la excepción existente — `TotpAdapterTest.java` sigue escaneado por completo salvo
  por esta cadena puntual.
- **Pierde:** nada de valor real — el vector es público (RFC 4226/6238) y no protege ningún acceso.
- **Condiciona:** cualquier otro test que reutilice el mismo vector de RFC en el futuro queda cubierto
  por la misma regla, sin necesidad de una entrada nueva.

### Cómo se revierte
Quitar la entrada de `regexes` en `.gitleaks.toml`. Si para entonces `TotpAdapterTest.java` ya no usa
`SEMILLA_DEL_RFC`, revertir no reabre ningún hallazgo.

---

## ADR-044 — El reporte ciudadano no se encola offline por ahora; falla explícito y pide reintentar

- **Fecha:** 2026-09-05
- **Estado:** Aceptada

### Contexto
Una auditoría de frontend (2026-09-04) encontró que el service worker (`vite.config.ts`, Workbox)
cachea tiles, el GeoJSON y el logo para lectura offline, pero `POST /api/reportes` y
`POST /api/reportes/:id/foto` no tienen ninguna cola ni reintento — si el vecino pierde señal justo
al enviar (el escenario central de `DESIGN.md` §1: "de pie, en la calle, con sol"), ve el error de
red normalizado y debe reintentar a mano cuando recupere conexión.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Cola manual en `localStorage` + reintento al recuperar `online` | No depende de Workbox, control total | Duplica lo que Background Sync ya resuelve; hay que reimplementar expiración, límite de reintentos y la UI de "pendiente de enviar" |
| `workbox-background-sync` (`BackgroundSyncPlugin`) sobre esas dos rutas | Es la solución estándar para exactamente este caso | El reporte lleva una huella de dispositivo y un cupo (RF006) resuelto en el momento del POST — encolar la petición tal cual y reproducirla horas después no reevalúa esas reglas de negocio del lado del cliente, y confirmar éxito con `onSuccess` de la mutación (como hace hoy `FormularioReporte`) deja de ser cierto: la petición encolada "tuvo éxito" en encolarse, no en registrarse |
| No encolar; fallar explícito con opción de reintentar a mano | Ningún envío se confirma sin que el servidor realmente lo recibiera — no hay riesgo de una falsa confirmación de éxito ni de una huella/cupo evaluados con datos viejos | El vecino sin señal debe acordarse de volver a intentar |

### Decisión
No implementar cola offline para el reporte ciudadano en esta pasada. Se documenta la decisión en
vez de dejarla como una omisión silenciosa (que es lo que la auditoría encontró). El fallo de red ya
es explícito (`normalizarErrorApi`, mensaje visible) y no bloquea reintentar manualmente.

### Consecuencias
- **Gana:** ningún reporte se confirma como enviado sin que el backend lo haya recibido y evaluado
  de verdad — coherente con la regla de no publicar ni confirmar nada sin verificación real
  (`CLAUDE.md` § Ética de datos).
- **Pierde:** el escenario central de producto (reportar con mala señal) sigue exigiendo un
  reintento manual del vecino.
- **Condiciona:** si se retoma, la cola debe reevaluar huella/cupo en el momento real de envío (no
  al momento de encolar) y el frontend debe distinguir "en cola" de "confirmado por el servidor" en
  vez de tratarlos como el mismo estado de éxito.

### Cómo se revierte
No aplica — no se implementó nada que deshacer. Retomarlo es una decisión de producto nueva, con su
propio ADR.

## ADR-045 — El proyecto es individual: se retira el aparato de coordinación de equipo

- **Fecha:** 2026-09-19
- **Estado:** Aceptada

### Contexto
El repositorio nació con un marco de cinco personas: roles `D1`–`D5` con capas y módulos asignados,
cuatro compuertas de habilitación entre roles, un registro de bloqueos y desbloqueos temporales,
Scrum Master rotativo, ceremonias, y una Sala de control que mapeaba usuarios de GitHub a roles y se
publicaba en GitHub Pages bajo la cuenta de otro integrante. El proyecto lo desarrolla una sola
persona; el historial de git ya se reinició bajo su cuenta (`chore: iniciar historial privado`). Sin
más gente, ese aparato no coordinaba a nadie y cada sesión pagaba su costo en contexto
(`docs/gestion/protocolo-de-contexto.md`). Los códigos de rol también se habían filtrado a unos 20
comentarios Javadoc de `backend/src` (incluida la prueba `ContratoOpenApiTest`), solo en prosa.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Dejar el marco de equipo tal cual | Cero trabajo, historial de decisiones intacto | Documentación que describe una organización que no existe; las compuertas y el Scrum Master rotativo no tienen quién los ejerza |
| Marcarlo como histórico y conservarlo | Se conserva la trazabilidad de cómo se decidió | Los agentes lo leen igual y proponen trabajo por rol; sigue costando contexto |
| Retirarlo y despersonalizar los registros (hechos sí, actores no) | Los documentos describen la realidad; la Sala de control deja de depender de `gh` y de una cuenta ajena | Se pierde el detalle de quién hizo qué; edita registros históricos y rompe la regla de *append-only* |

### Decisión
Retirar el aparato de equipo. Se borran `docs/equipo/`, la skill `registrar-bloqueo`,
`registro-de-bloqueos.md` (todas sus entradas ya estaban cerradas) y el workflow `dashboard.yml`
(Pages). Los registros de gestión, la bitácora, los ADR y los comentarios de código se reescriben en
voz de hechos, sin actores. La Sala de control se reduce a lo que sale de `docs/`: sprint actual,
cobertura, bugs, ADR y recomendaciones; se genera solo en local y ya no lee PRs ni issues con `gh`.

### Consecuencias
- **Gana:** documentación coherente con un solo desarrollador; menos archivos permanentes que leer
  en cada sesión; la Sala de control funciona sin `gh`, sin red y sin apuntar a un repositorio ajeno.
- **Pierde:** la atribución por persona en el historial documental, y con ella la lectura de "quién
  estaba detenido esperando a quién". Los ADR `011`, `012`, `013`, `019` y `021` (asignación de roles,
  compuertas y herramienta de equipo) se eliminaron enteros, y el `010` se reescribió, en contra de
  la regla de *append-only*: la numeración queda con huecos, que no se renumeran.
- **Condiciona:** si el proyecto vuelve a tener más de una persona, hay que decidir de nuevo cómo se
  reparte y coordina el trabajo; no se reactiva nada de lo retirado.

### Cómo se revierte
Este repositorio ya no conserva el marco: el historial se reinició y los archivos se borraron.
Rehacerlo sería una decisión nueva, con su propio ADR. No hay código que deshacer, solo
documentación y una skill.

## ADR-046 — «% operativa» se calcula sobre los sectores con estado verificado

- **Fecha:** 2026-09-20
- **Estado:** Aceptada

### Contexto
La barra superior muestra «Red Distrital: X% operativa» y las tarjetas de resumen dependen de que haya
datos disponibles, pero `PaginaMapa` no pasaba ninguno de los dos valores a sus componentes (BUG-074):
la barra decía «undefined% operativa» y las tarjetas se quedaban en «—» aun con sectores. Ninguna
función del repositorio calculaba ese porcentaje, así que había que definir qué significa «operativa».
Los sectores llegan con `estado` nulo cuando nadie los ha verificado (ADR-014), y `poblacion` también
es nulable.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Sectores en `CON_SERVICIO` sobre los que tienen estado verificado; `null` («calculando») si ninguno lo tiene | Simple; respeta ADR-014 (nunca se supone «con servicio»); se explica en una frase | Trata igual a un barrio pequeño que a uno grande |
| Ponderado por población | Más fiel a cuánta gente tiene agua | `poblacion` es nulable: habría que decidir qué hacer con los sectores sin ese dato, y cada decisión inventa una cifra |
| Pasar `null` siempre hasta definirlo | No publica nada dudoso | La barra dice «calculando» de forma permanente y no informa |

### Decisión
`resumirServicio` (`frontend/src/utils/resumenServicio.ts`) calcula el porcentaje como sectores en
`CON_SERVICIO` sobre sectores con estado verificado, redondeado, y devuelve `null` si ninguno tiene
estado; ese mismo caso apaga las cifras de las tarjetas («—», «Esperando datos validados»). Es la
opción recomendada, aceptada por el usuario el 2026-09-20.

### Consecuencias
- **Gana:** una cifra que no publica nada sin sustento y que se puede reproducir a mano.
- **Pierde:** es conservadora. Presión baja y corte programado no cuentan como operativos, aunque un
  corte programado es un corte anunciado y el sector aún tiene agua (BUG-057), así que la cifra puede
  subestimar. Tampoco pondera por tamaño.
- **Condiciona:** ponderar por población o contar el corte programado como operativo es una decisión
  nueva, con su propio ADR y un cambio en `resumenServicio.test.ts`.

### Cómo se revierte
Un cambio localizado en `resumirServicio` y sus pruebas; no toca la API ni el modelo de dominio.

## ADR-047 — Se retira OpenSpec: el comportamiento del sistema vive en `docs/ingenieria/comportamiento-del-sistema.md`

- **Fecha:** 2026-09-20
- **Estado:** Aceptada

### Contexto
`ADR-040` (2026-09-04) adoptó OpenSpec como capa de especificación viva: trece specs bajo `openspec/`,
seis skills `openspec-*` repetidas en `.claude/skills/` y `.agents/skills/`, y seis comandos `/opsx:*`. El
desarrollador actual indicó el 2026-09-20 que no la había incorporado él. El 2026-09-20 se comprobó que el
CLI no está instalado en este equipo, que el repositorio nunca nombra el paquete npm y que, por tanto,
`openspec validate` no se había podido ejecutar; las tres specs que se actualizaron ese día se revisaron
a mano. El proyecto lo desarrolla una sola persona, y la herramienta sumaba tres directorios y doce
copias de instrucciones para lo que era, en la práctica, documentación.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Mantener OpenSpec | Specs validables por CLI y un flujo de propuesta antes del código | Herramienta sin instalar y sin validar; doce copias de skills que mantener; dos lugares donde documentar |
| Retirarlo y borrar las specs | Lo más simple | Se pierde el comportamiento documentado: 54 requisitos y 93 escenarios |
| Retirarlo y llevar su contenido a `docs/ingenieria/` | Un solo lugar para toda la documentación, sin herramienta; no se pierde nada de lo escrito | Nada valida ya por comando que documento y código coincidan |

### Decisión
Se retira OpenSpec: se borran `openspec/`, las seis skills `openspec-*` de `.claude/skills/` y de
`.agents/skills/`, y los comandos `opsx`. Las trece capacidades pasan, con sus 54 requisitos y 93
escenarios, a un único documento, `docs/ingenieria/comportamiento-del-sistema.md`, en formato «Cuando /
Entonces». `ADR-040` queda como *Reemplazada por ADR-047*.

### Consecuencias
- **Gana:** toda la documentación en `docs/`, ordenada y en un solo sitio; una herramienta menos.
- **Pierde:** la validación por comando y el flujo de propuesta antes del código (`/opsx:propose`). El
  desfase entre documento y código, que `ADR-040` ya había visto ocurrir (`RNF017`), vuelve a
  depender de la disciplina de quien cambia comportamiento.
- **Condiciona:** el documento se actualiza en el mismo cambio que el comportamiento (`CLAUDE.md`); la
  matriz de trazabilidad (RF → prueba) sigue siendo la verificación de que cada requisito tiene su prueba.

### Cómo se revierte
El contenido anterior (`openspec/`, las doce skills y los seis comandos) queda en el historial de git:
restaurarlo es recuperar esos directorios de un commit anterior a este cambio. No depende de ello la
build, ni el CI, ni el contrato OpenAPI.

---

## ADR-048 — El repositorio pasa a ser backend + datos + infraestructura: el frontend se retira y lo rehace otra persona

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
El frontend (React 19, `frontend/`, 137 archivos versionados) estaba completo y conectado, pero el dueño
quiere que lo reconstruya desde cero una persona de su confianza, a partir de una documentación completa de
las funcionalidades y rutas, y quiere antes pulir el backend (incluida la escalabilidad: mínimo 50 000
usuarios simultáneos). Dejar un frontend viejo en el repositorio confundiría a quien lo rehaga.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Conservar `frontend/` y reescribirlo encima | Punto de partida | Arrastra decisiones de diseño que se quieren revisar; el backend se pule sin un contrato de referencia |
| Retirar `frontend/` y documentar la API para el nuevo | Contrato explícito; nada que estorbe | Sin cliente para las cuentas (M15) más allá de las páginas HTML de cortesía |
| Reescribir también el backend | Base limpia | Se pierden 660 pruebas verdes y semanas de trabajo; el backend estaba sano |

### Decisión
Se retira `frontend/` (queda en la etiqueta git `pre-retiro-frontend`), el backend se conserva y se pule, y
se publica `docs/api/` como guía para construir el cliente. `DESIGN.md` se conserva (el backend lo cita y sus
plantillas de correo usan su paleta).

### Consecuencias
- **Gana:** un contrato claro (`backend/openapi.yaml` + `docs/api/`); ninguna deriva entre backend y un cliente viejo.
- **Pierde:** la interfaz funcionando; los requisitos de interfaz (`RNF001`, `RNF012`–`RNF016` y las partes de
  UI de `RF001`–`RF004`, `RF008`) quedan **retirados por alcance** hasta que exista el frontend nuevo. Los
  enlaces de los correos apuntan a páginas HTML de cortesía del backend en lugar de a una SPA.
- **Condiciona:** el proxy (`infra/nginx/`) sirve solo la API, las fotos y el proxy de imágenes de Acuacar.

### Cómo se revierte
`git checkout pre-retiro-frontend -- frontend/` recupera el código. El resto no depende de ello.

---

## ADR-049 — El backend escala con micro-caché HTTP, avisos SSE ligeros y ejecución única de jobs, no con más hardware

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
Se pidió que el sistema soporte **50 000 personas a la vez**. Una auditoría del código (2026-09-21) concluyó
que **no** lo soportaba: una instancia con valores por defecto (Tomcat de 200 hilos, sin límites en los pools
de Mongo y Redis), SSE que difundía el listado completo (~25 KB) a cada cliente en cada cambio con una lista
`CopyOnWriteArrayList` sin tope (~1,25 GB por evento con 50 000 clientes), `Cache-Control: no-store` forzado en
toda la API, jobs `@Scheduled` que correrían N veces con N réplicas y `INCR`+`EXPIRE` no atómicos en el rate
limit (una clave sin caducidad bloqueaba a una IP para siempre).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Solo más instancias | Nada que cambiar | Cada instancia seguiría enviando 25 KB por cliente y cambio; los jobs se duplicarían |
| Empujar el estado por SSE, optimizado | Mismo contrato para el cliente | El costo crece con clientes × cambios; inviable a 50 000 |
| **SSE de aviso + `GET` cacheado** | El costo de leer no depende del número de clientes | El cliente debe pedir tras el aviso (contrato cambiado) |
| WebSocket / servicio de tiempo real gestionado | Más margen | Infraestructura nueva sin necesidad demostrada |

### Decisión
1. **Lecturas públicas** (`/api/sectores`, `/estadisticas`, `/cumplimiento`, `/bitacora`): micro-caché de 5 s en
   nginx (`proxy_cache_lock`, servir versión vieja si el backend cae) con `Cache-Control: public, max-age=5,
   stale-while-revalidate=30`. Nunca se cachea una petición con `Authorization`.
2. **SSE**: solo avisa (`{"actualizadoEn":…}`); el cliente pide `GET /api/sectores`. Difusión en hilos
   virtuales, avisos agrupados a 1/s, tope de conexiones (429 con `Retry-After`), latido y `retry` con jitter.
3. **Concurrencia**: hilos virtuales, timeouts y pool de Mongo acotados, `CacheErrorHandler` (un Redis caído
   degrada a Mongo en vez de dar 500), `@Cacheable(sync=true)`.
4. **Réplicas**: cada `@Scheduled` con efectos pasa por `EjecucionUnica` (`SET NX PX` en Redis); el rate limit
   es un script Lua atómico y tolera Redis caído; el cambio de estado por consenso es *compare-and-set* en Mongo.
5. **Operación**: `liveness`/`readiness` sin las fuentes externas, Redis con `noeviction`, `MaxRAMPercentage=75`.

### Consecuencias
- **Gana:** el costo de una lectura pública deja de depender del número de clientes; el sistema puede correr
  con varias réplicas sin duplicar trabajo.
- **Pierde:** **el contrato del SSE cambia** (ya no trae el estado); una lectura pública puede tener hasta ~5 s
  (nginx) más el `max-age` del navegador de antigüedad, y hasta ~30 s tras un fallo del backend; con Redis caído
  el rate limit por IP deja de aplicarse y los jobs programados se omiten.
- **Queda pendiente y NO resuelto** (ver `docs/ingenieria/escalabilidad.md`): las fotos van a disco local (con
  réplicas en hosts distintos hacen falta almacenamiento de objetos o un volumen compartido); el estado de los
  colectores vive en memoria de cada instancia; Mongo y Redis son nodos únicos (sin replica set ni Sentinel); no
  hay CDN ni TLS. **Los 50 000 concurrentes reales no se han probado**: solo hay una medición a escala reducida.

### Cómo se revierte
Cada pieza es independiente: quitar el bloque `proxy_cache` de `infra/nginx/nginx.conf` restaura la lectura sin
caché; el resto son cambios de configuración o clases aisladas (`EjecucionUnicaRedis`, `SseSectoresBroadcaster`).

---

## ADR-050 — El sector de un reporte se infiere de la coordenada con una consulta geoespacial (`$geoIntersects`)

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
`RF007` pide ubicar el reporte por coordenada. La matriz de trazabilidad lo daba por hecho (✅), pero el código
nunca lo hizo: `POST /api/reportes` exigía el `sectorId` y la coordenada solo se guardaba; el índice `2dsphere`
de `sectores.geometry` no lo usaba ninguna consulta (verificado leyendo `RegistrarReporteService`).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Que el cliente resuelva el barrio (punto en polígono) | Sin trabajo en el servidor | Necesita los ~0,7 MB de polígonos en cada cliente; cada cliente lo reimplementa |
| Punto en polígono en memoria del servidor | Sin consulta a Mongo | Cargar y mantener 213 polígonos; duplica la fuente de verdad |
| **`$geoIntersects` sobre el índice `2dsphere`** | Usa lo ya sembrado; funciona con `MultiPolygon` (`zona-industrial`) | Una consulta más por reporte sin `sectorId` |

### Decisión
`sectorId` pasa a ser opcional si viaja `coordenada`. Con solo la coordenada, `SectorRepository.buscarPorCoordenada`
resuelve el barrio; si no cae en ninguno, `400`. Si viaja `sectorId`, manda y no se consulta la geometría.

### Consecuencias
- **Gana:** el cliente puede reportar con un solo toque de ubicación; el `id` sale siempre en la respuesta.
- **Pierde:** con `sectorId` y `coordenada` a la vez **no se comprueba** que coincidan (no se paga una consulta
  que nadie pidió); un cliente podría declarar un sector distinto al de su coordenada.

### Cómo se revierte
Volver a hacer `sectorId` obligatorio en `SolicitudReporte` y quitar la rama de inferencia de `RegistrarReporteService`.

---

## ADR-051 — El cambio de estado por consenso es compare-and-set y el evento de bitácora guarda los reportes que lo sustentan

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
`EvaluarConsensoService` leía el estado, decidía y escribía sin control: dos reportes simultáneos del mismo
sector leían el mismo estado y **los dos anexaban su evento** a la bitácora, que es de solo anexado (`RF028`), así
que el duplicado no se podía corregir. Además `RF011` (dejar constancia de qué reportes sustentan el cambio)
estaba marcado ✅ pero el evento solo guardaba un conteo: los ids se calculaban y se descartaban.

### Decisión
`SectorRepository.cambiarEstadoSiEs(id, esperado, nuevo)` (un `findAndModify` con el estado esperado en el
filtro) decide quién gana; solo el ganador anexa el evento. `EventoBitacora` gana `reportesSustento` y el evento de
consenso ahora también afirma su `estado`. Verificado con 16 hilos concurrentes contra Mongo real: un único ganador.

### Consecuencias
- **Gana:** una bitácora sin duplicados y trazable hasta la evidencia; el campo es aditivo (`reportesSustento`).
- **Pierde:** el perdedor de la carrera responde `alcanzado: false` aunque su reporte sí contó; y **no hay ruta
  pública que consulte esos reportes** por id.

### Cómo se revierte
Volver a `guardar()` en el servicio; el campo `reportesSustento` puede quedar sin usar.

---

## ADR-052 — «No existe» de un recurso de la URL es 404 (`EntidadNoEncontradaException`); un sector en el cuerpo sigue siendo 400

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
La API era inconsistente: `GET /api/sectores/{id}` y `GET /api/veedor/cortes/{id}` daban 404, pero cerrar un corte,
moderar un reporte, revisar una propuesta, confirmar un reporte o pedir el cumplimiento de un corte inexistentes
daban 400 (el mismo `IllegalArgumentException`). Quien construye un cliente desde cero no puede predecirlo. Además
`POST /api/iot/presion` respondía 400/401/503 sin cuerpo, el único punto que no seguía RFC 7807.

### Decisión
`EntidadNoEncontradaException` (dominio, **extiende** `IllegalArgumentException`) para los recursos identificados
en la URL; el manejador global la traduce a 404. Un sector inexistente **dentro del cuerpo** de un POST sigue siendo
400 (el recurso no está en la URL). IoT responde ahora en RFC 7807 y el umbral de presión baja (15 psi) sale del
controlador a `RegistrarLecturaDePresionService`.

### Consecuencias
- **Gana:** una regla simple y previsible para el cliente; ningún código existente que capture la excepción genérica se rompe.
- **Pierde:** cambia el código de estado de esas rutas (400 → 404): un cliente que ya dependiera del 400 se rompería
  (no hay ninguno tras el retiro del frontend). La frontera «recurso en la URL vs. en el cuerpo» es una convención, no
  algo que el compilador haga cumplir.

### Cómo se revierte
Lanzar `IllegalArgumentException` en lugar de `EntidadNoEncontradaException` en los siete servicios afectados.

---

## ADR-053 — El consenso se evalúa como mucho una vez por segundo y sector, y la micro-caché de nginx ignora las cabeceras de caché del origen

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegó las decisiones de escalabilidad, «tú decide qué pulir»)

### Contexto
Al medir con carga (`scripts/carga/`, 100 POST/s repartidos + un pico de 300/s sobre un sector) aparecieron dos
defectos que ninguna prueba unitaria veía y que anulaban la base de `ADR-049`:
1. **`BUG-085`:** el backend responde `Cache-Control: no-cache, no-store` (Spring Security) y nginx respeta la cabecera
   del origen: la micro-caché nunca guardaba nada. Una prueba con un backend simulado dio *MISS→HIT* y lo ocultó.
2. **`BUG-086`:** `EvaluarConsensoService` cargaba de Mongo todos los reportes de la ventana del sector (30 min) en
   **cada** POST una vez superado el umbral, para descubrir casi siempre que el estado no cambiaba. Con miles de
   reportes por sector, el pool de 100 conexiones se agotaba: 35 % de errores `503` y p95 de 8 s (RNF002 exige 1 s).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Consenso: solo contar votos en Mongo (agregación) | Sencillo; sin estado nuevo | Sigue siendo O(ventana) por POST; con 10 000 reportes en el sector el pico seguía dando 4 % de errores y p95 de 5 s |
| Consenso: contadores por tipo en Redis, incrementales | O(1) | Rediseño grande; hay que reconstruir la ventana al expirar reportes |
| **Consenso: votos contados en Mongo + una evaluación por segundo y sector + barrido de pendientes** | O(1) por POST; ningún reporte queda sin evaluar (solo se agrupan) | El cambio de estado puede tardar hasta ~2 s más; una pieza más (reserva en Redis + tarea) |
| Caché: que el backend emita `Cache-Control: public, max-age=5` | Correcto para un CDN delante | Hay que tocar cada controlador; un `404` con `max-age` sería cacheable por nginx |
| **Caché: nginx ignora y oculta las cabeceras del origen y fija la validez él** | Un solo lugar; un `404` no se cachea | El backend «miente» (`no-store`) a quien lo consulte sin nginx |

### Decisión
Consenso: `ReporteCiudadanoRepository.contarVotosRecientes` (agregación en Mongo, ≤ 3 filas) decide si hay consenso y
hacia dónde; **solo si el estado va a cambiar** se cargan los reportes de sustento (RF011). Además
`ReservaDeEvaluacionPort` (`SET NX PX`, 1 s por sector) deja evaluar a una petición por intervalo; el resto deja el
sector pendiente y `EvaluacionPendienteJob` (cada 1 s, en todas las réplicas) lo evalúa. Con Redis caído se evalúa en
cada petición (más caro, pero nunca se pierde una evaluación). Índice nuevo `sectorId+huella+timestamp` para el
cupo por dispositivo. Caché: `proxy_ignore_headers Cache-Control Expires Vary` + `proxy_hide_header` en el bloque de
lecturas públicas; la validez la fija solo `proxy_cache_valid 200 5s`.

### Consecuencias
- **Gana (medido, un solo backend, un solo equipo):** 100 POST/s + pico de 300/s: de 35 % de errores y p95 de 8 s a 0 %
  y 15,6 ms; a 3× esa carga (63 001 peticiones): 0 % y p95 de 36 ms, sin eventos duplicados. Con la micro-caché real,
  un nginx sirvió ~3 700 req/s con el backend al ~6 % de un núcleo.
- **Pierde:** el cambio de estado por consenso puede retrasarse hasta ~2 s (intervalo + barrido); es configurable
  (`aguavigia.consenso.intervalo-evaluacion-ms`, `aguavigia.consenso.barrido-ms`). La clave de caché es la URL completa,
  así que variar parámetros de consulta evita la caché (lo acota el `limit_req` por IP).
- **Lo que enseña:** una prueba de caché solo vale contra el backend real (`scripts/carga/verificar-cache-proxy.mjs`).

### Cómo se revierte
Consenso: quitar la reserva (`reservar` siempre `true`) devuelve la evaluación en cada POST, manteniendo el conteo
en Mongo. Caché: eliminar las líneas `proxy_ignore_headers`/`proxy_hide_header` (y la micro-caché vuelve a no
funcionar hasta que el backend emita sus propias cabeceras).

---

## ADR-054 — Confirmar y cancelar una suscripción es un POST; el GET del enlace del correo solo muestra un botón

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegó las decisiones de contrato: «las que puedas tomar tú, hazlas»)

### Contexto
`GET /api/suscripciones/confirmar` y `/cancelar` cambiaban el estado de la suscripción (`ADR-030`). Los enlaces de los
correos son GET y un antivirus, un filtro de correo o una vista previa de enlaces los abre sin que nadie los pida: podían
confirmar o, peor, **dar de baja** solas a una persona. Las páginas de cuentas ya lo evitaban (GET muestra, POST actúa).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Dejar el GET que actúa | Sin cambios | Un precargador confirma o cancela por su cuenta |
| Token de un solo uso en el GET | Sin pantalla nueva | El precargador consume el token antes que la persona |
| **GET muestra una página con botón; POST actúa** | Igual que en cuentas; los enlaces ya enviados siguen funcionando | Un clic más para la persona; los clientes que llamaban al GET con JSON deben usar POST |

### Decisión
`GET …/confirmar` y `…/cancelar` devuelven solo HTML con un botón (con `Accept: application/json` responden 406); el botón hace
`POST` a la misma ruta, que ejecuta la acción y responde HTML o JSON según el `Accept`.

### Consecuencias
- **Gana:** un precargador ya no mueve suscripciones; los enlaces de correos ya enviados siguen abriendo (ahora con botón).
- **Pierde:** un clic adicional; cambia el contrato (`GET` con JSON → `POST`); no hay clientes que dependieran de ello tras el retiro del frontend.
- **Efecto colateral:** al probarlo apareció que un `Accept` no soportado daba 500 (`BUG-087`); ahora es 406.

### Cómo se revierte
Volver a mover la lógica del POST al GET en `SuscripcionController` y quitar la página del botón.

---

## ADR-055 — La bitácora pública entrega cuántos reportes sustentan un evento y sus ids en un detalle paginado

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegó las decisiones de contrato)

### Contexto
RF011 hizo que cada evento de consenso guardara los ids de los reportes que lo sustentan y el listado público los devolvía
todos. Medido con datos de prueba: una página de 20 eventos pesó **205 KB** (frente a 11 KB de `/api/sectores`, con 4 449 ids)
y crece con cada avería grande (miles de ids por evento).

### Decisión
`GET /api/bitacora` devuelve `cantidadReportesSustento` (un número) y ya no la lista; los ids salen por
`GET /api/bitacora/{id}/sustento`, paginado (50 por defecto, 200 máximo) y con 404 si el evento no existe.
`EventoBitacoraRepository` gana `buscarPorId`. La trazabilidad de RF011 no cambia: el evento sigue guardando los ids.

### Consecuencias
- **Gana:** el listado que más se pide pesa lo mismo con 3 reportes que con 30 000; el detalle se pide solo si alguien lo abre.
- **Pierde:** un cliente que leía `reportesSustento` del listado debe pedir el detalle (no hay ninguno tras el retiro del frontend).

### Cómo se revierte
Volver a mapear la lista en `EventoBitacoraRespuesta` y quitar el endpoint de detalle.

---

## ADR-056 — Cuatro rutas que el frontend nuevo necesitaba: histórico público de cortes, población, cambio de clave con sesión y reenvío de enlaces

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegó las decisiones de contrato)

### Contexto
Quien rehace el frontend no podía: ver el histórico de cortes de un sector sin sesión (RF002 quedaba parcial), mostrar la población
de un barrio, cambiar su propia clave estando dentro (solo por el flujo del correo) ni reenviar un correo de verificación o
invitación que no llegó (invitar con el correo caído dejaba la cuenta creada y sin forma de reenviar el enlace).

### Decisión
- `GET /api/sectores/{id}/cortes` (público, paginado, el más reciente primero; lista vacía si no hay cortes, 404 si el sector no existe).
- `poblacion` en `SectorRespuesta`, `null` (nunca 0) cuando el barrio no tiene dato censal.
- `POST /api/veedor/cuenta/clave`: exige la clave actual, comparte el contador de intentos con el inicio de sesión, cierra todas
  las sesiones y avisa por correo; una sesión de alcance `ALTA_SEGUNDO_FACTOR` no llega (pide `VER_PANEL`).
- `POST /api/cuentas/verificacion/reenvio` (público, 202 siempre, una vez cada 2 minutos por cuenta) y
  `POST /api/veedor/usuarios/{id}/invitacion/reenvio` (`GESTIONAR_USUARIOS`, 404/409). Reemitir invalida el enlace anterior.
- **No** se añade una lista pública de reportes: expone huellas y ubicaciones y no hay caso de uso que la exija.

### Consecuencias
- **Gana:** ningún flujo de cuenta queda sin salida; RF002 completo; el mapa puede mostrar habitantes.
- **Pierde:** más superficie de API que mantener (5 rutas, 65 en total) y más frenos que calibrar.
- **Riesgo aceptado:** el reenvío público podría usarse para molestar a un correo ajeno; lo acota el enfriamiento por cuenta y el límite por IP.

### Cómo se revierte
Quitar los controladores `HistorialDeCortesController`, `CuentaPropiaController` y `ReenvioDeEnlacesController` y sus casos de uso.

---

## ADR-057 — El proyecto es académico y corre en local: sin hosting, dominio, CDN ni servicios gestionados

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
Al pedir decisiones de despliegue (CDN, servicios gestionados de Mongo y Redis, almacenamiento de objetos para las fotos,
dominio y TLS) el dueño aclaró que AguaVigía es un **proyecto académico que se presenta en clase corriendo en los PC del
equipo**, sin hosting, sin dominio y con **presupuesto cero**. Las respuestas «CDN», «servicios gestionados» y
«S3-compatible» se dieron antes de esa aclaración, sobre mis recomendaciones para un despliegue real.

### Decisión
Prevalece la aclaración: **no se contratan ni se implementan** CDN, servicios gestionados, S3, dominio ni TLS. Todo corre con
`docker compose` en local; las fotos siguen en disco local (una sola réplica). La arquitectura de `escalabilidad.md`
(CDN, réplicas, Mongo de 3 nodos, S3) queda como **referencia para un despliegue futuro**, no como pendiente del proyecto.
Los 50 000 usuarios simultáneos (`RNF027`) **no pueden demostrarse en local**: lo defendible es la arquitectura preparada
y las mediciones locales reproducibles de `scripts/carga/`, dichas con su límite.

### Consecuencias
- **Gana:** cero costo y cero infraestructura que mantener; un solo `docker compose` levanta todo.
- **Pierde:** la meta de 50 000 no se puede probar a escala real; sin alta disponibilidad ni CDN, un fallo de un contenedor
  interrumpe el servicio. Los límites por IP de nginx no importan en local (un solo cliente).
- **Para la presentación:** decir qué se midió (nginx con micro-caché ~3 700 req/s con el backend al ~6 % de un núcleo,
  10 000 conexiones SSE, escritura a 3× carga sin errores) y que eso es un banco local, no producción.

### Cómo se revierte
Si el proyecto se despliega algún día, retomar `escalabilidad.md` («Pendiente antes de afirmar 50 000»).

---

## ADR-058 — Los reportes se conservan 12 meses y los eventos de la bitácora son permanentes

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
`reportes` crecía sin límite y guarda la huella del dispositivo de cada persona que reporta. La bitácora es de solo anexado
(`RF028`) y cada evento de consenso guarda los ids de sus reportes de sustento (`RF011`).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Conservar todo para siempre | Trazabilidad completa | La colección y las huellas crecen sin límite; peor para la privacidad |
| **Reportes 12 meses, eventos permanentes** | Acota el crecimiento y el tiempo que se guarda una huella; alinea con las fotos (365 días) | Los ids de sustento de un evento viejo apuntan a reportes ya borrados |
| Archivar en vez de borrar | Conserva la evidencia | Más piezas; sin destino de archivo en un proyecto local |

### Decisión
Índice TTL de Mongo sobre `reportes.timestamp` a `aguavigia.retencion.reportes-dias` (365 por defecto; 0 lo desactiva). Los eventos
de la bitácora no se tocan. `RF024` (evolución del índice) usa los cortes cerrados, no los reportes, y no se ve afectado. Las fotos
de reportes borrados quedan huérfanas y las limpia el job nocturno existente.

### Consecuencias
- **Gana:** la colección deja de crecer sin límite y ninguna huella se guarda más de un año.
- **Pierde:** pasados 12 meses, el evento sigue diciendo *cuántos* reportes lo sustentaron y sus ids, pero el contenido de esos reportes ya no existe.
- Cambiar el plazo en una base ya creada exige retirar antes el índice `timestamp_1` (Mongo rechaza el mismo índice con otra caducidad).

### Cómo se revierte
`aguavigia.retencion.reportes-dias: 0` y retirar el índice `timestamp_1`; lo ya borrado no se recupera.

---

## ADR-059 — El backend se queda en Spring Boot 3.5.x y Dependabot no propone sus saltos mayores

- **Fecha:** 2026-09-21
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
Dependabot abrió el 2026-09-17 tres PR de salto mayor contra `backend/pom.xml`: Spring Boot 3.5.16 → 4.1.1 (#7), springdoc 2.8.6 → 3.1.1 (#11) y Testcontainers 1.21.3 → 2.0.5 (#2). Los tres fallaron el `Backend CI`. El propio `pom.xml` ya documentaba por qué el parent está en 3.5.x: Boot 4 arrastra Spring Framework 7 «y no es un salto que hacer a la ligera». springdoc 3.x solo funciona con Boot 4, así que #11 no es independiente de #7. El resto de PR de Dependabot (Actions, jjwt 0.13, ArchUnit 1.5) pasó el CI y se fusionó.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Migrar a Boot 4 ahora | Versión vigente | Migración grande de Spring Framework 7 sobre 823 pruebas y un contrato OpenAPI que el frontend nuevo ya va a consumir; sin necesidad funcional |
| **Quedarse en 3.5.x e ignorar los mayores de Boot y springdoc** | Congruente con el `pom.xml`; los parches de seguridad de la rama 3.5 siguen llegando por Dependabot | Hay que migrar algún día, cuando 3.5 salga de soporte |
| Dejar los PR abiertos sin fusionar | No decide nada | Tres PR en rojo permanentes que ocultan los que sí importan |

### Decisión
`.github/dependabot.yml` ignora `semver-major` de `spring-boot-starter-parent` y de `springdoc-openapi-starter-webmvc-ui`; parches y menores se siguen proponiendo. Se cierran #7 y #11. Testcontainers 2.x **no** se ignora: #2 queda abierto como migración pendiente (`estado-del-backend.md` §5), porque no depende de Boot 4.

### Consecuencias
- **Gana:** Dependabot deja de contradecir el `pom.xml`; los PR abiertos son solo los que hay algo que decidir.
- **Pierde:** la migración a Boot 4 queda sin fecha. Antes de que Boot 3.5 pierda soporte hay que retirar estas dos reglas y abrir su propio ADR.
- **Condiciona:** el aviso de Trivy sobre CVE en transitivas de 3.5.x se sigue resolviendo, como hasta ahora, fijando la versión de parche en `pom.xml` (`BUG-069`).

### Cómo se revierte
Borrar las dos entradas `ignore` de `.github/dependabot.yml`; Dependabot volverá a proponer el salto en su siguiente ejecución semanal.

---

<!--
## ADR-060 — Testcontainers se queda en 1.21.3; Dependabot no propone su salto mayor

- **Fecha:** 2026-09-22
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegado al agente)

### Contexto
El PR #2 de Dependabot (Testcontainers 1.21.3 → 2.0.5) seguía abierto desde el 2026-09-17 y se reverificó dos veces contra el `main` nuevo, ambas en rojo. El `Backend CI` falla al leer el `pom.xml`, no al ejecutar una prueba: `'dependencies.dependency.version' for org.testcontainers:junit-jupiter:jar is missing` y lo mismo para `org.testcontainers:mongodb`. El `pom.xml` importa `testcontainers-bom` en `dependencyManagement` y declara esas dos dependencias sin versión propia, confiando en que el BOM la fije — así lo hacía 1.21.3. El BOM de 2.0.5 ya no las gestiona con esas coordenadas: es un cambio de estructura del propio Testcontainers, no un error de configuración del proyecto.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Fijar `<version>` a mano en esas dos dependencias | Podría desbloquear el PR sin más cambios | No se verificó que sea suficiente ni que el resto de la API de Testcontainers 2.x sea compatible con `MongoDBContainer`/`GenericContainer` tal como se usan hoy; arriesgar 823 pruebas por una dependencia de tests sin necesidad funcional |
| **Quedarse en 1.21.3 e ignorar el salto mayor** | Congruente con `ADR-059` (mismo criterio: builds rotas que exigen migración real, no parche) | Toca migrar algún día |
| Dejar el PR abierto indefinidamente | No decide nada | Un PR en rojo permanente, mismo problema que motivó `ADR-059` |

### Decisión
`.github/dependabot.yml` ignora también `semver-major` de `org.testcontainers:testcontainers-bom`. Se cierra el #2.

### Consecuencias
- **Gana:** Dependabot deja de reabrir un PR que rompe la build sin arreglo trivial; los PR de Testcontainers que queden abiertos son parches o menores, compatibles con 1.x.
- **Pierde:** la migración a Testcontainers 2.x queda sin fecha ni investigada a fondo — este ADR no descarta la opción de fijar la versión a mano, solo no la intentó sin evidencia de que baste.
- **Condiciona:** ninguna prueba de integración depende hoy de una función exclusiva de Testcontainers 2.x.

### Cómo se revierte
Borrar la entrada `ignore` de `testcontainers-bom` en `.github/dependabot.yml`; Dependabot volverá a proponer el salto en su siguiente ejecución semanal.

---

## ADR-061 — Un solo orden de severidad entre estados de servicio en conflicto: SIN_SERVICIO > CORTE_PROGRAMADO > PRESION_BAJA > CON_SERVICIO

- **Fecha:** 2026-09-22
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegado al agente, Fase 2 de `docs/ingenieria/plan-validacion-backend.md`)

### Contexto
Un mismo sector puede recibir, a la vez, más de una señal sobre su estado: dos boletines de Acuacar
aprobados que se solapan (uno extiende el corte que el otro ya había anunciado), o un boletín de
ingesta con la ventana vencida mientras el veedor tiene un corte oficial (`CorteAgua`, RF016-017)
todavía abierto sobre ese mismo barrio (`BUG-098`, `BUG-099`). Antes de esta decisión no había una
regla única para resolver el conflicto: `GestionarCorteOficialService` ya elegía "el más severo entre
cortes oficiales" con una tabla propia de solo tres valores (`SIN_SERVICIO`, `CORTE_PROGRAMADO`,
cualquier otro), pero `ActualizarEstadosPorVentanaService` no tenía ninguna, y ninguna de las dos
sabía qué hacer con `PRESION_BAJA`, que solo produce la ingesta y el consenso ciudadano, nunca un
corte oficial.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Gana la propuesta/corte más reciente por fecha de publicación | Se acerca a "la última noticia manda" | Un boletín de prensa mal fechado o un corte registrado tarde podría pisar una fuente más autorizada; no resuelve el caso corte-oficial-vs-aviso, donde "autorizado" importa más que "reciente" |
| Extender la tabla propia de `GestionarCorteOficialService` (3 valores) para que además reconozca `PRESION_BAJA` | Cambio mínimo, un solo sitio | Deja el mismo criterio implícito y duplicado en cuanto `ActualizarEstadosPorVentanaService` necesite su propia versión — el error que `REC-015` ya advirtió con los colores de estado |
| **Un orden total único en el dominio (`EstadoServicio.masSevero`), con SIN_SERVICIO como el más severo y CON_SERVICIO como el menos** | Una sola fuente de verdad, reutilizable donde haga falta; conmutativo y asociativo por construcción, así que plegar una lista de candidatos da el mismo resultado sin importar su orden — condición explícita de la Fase 2 | Coloca `PRESION_BAJA` entre `CORTE_PROGRAMADO` y `CON_SERVICIO` por criterio propio, no por un hecho medido: es defendible, no demostrado |

### Decisión
`EstadoServicio.masSevero(a, b)` (Java puro, en `domain/`) define el orden total
`SIN_SERVICIO > CORTE_PROGRAMADO > PRESION_BAJA > CON_SERVICIO`. Ante candidatos en conflicto para el
mismo sector, gana siempre el más alejado de `CON_SERVICIO`. `GestionarCorteOficialService` se
refactorizó para usarlo (sin cambiar su comportamiento: nunca evalúa `PRESION_BAJA`, así que la tabla
de 3 valores y la de 4 coinciden en los casos que le llegan) y `ActualizarEstadosPorVentanaService` lo
usa tanto para resolver avisos solapados del mismo sector como para no dejar que un aviso de ingesta
vencido rebaje un sector por debajo de lo que un corte oficial todavía abierto exige.

### Consecuencias
- **Gana:** un único criterio, en el dominio, para toda decisión de "¿cuál de estos estados manda?" —
  sin importar si los candidatos vienen de boletines solapados, de un corte oficial, o de ambos a la
  vez. Es conmutativo por diseño, así que el resultado no depende del orden en que Mongo devuelva las
  filas (`BUG-098`).
- **Pierde:** el orden entre `PRESION_BAJA` y `CORTE_PROGRAMADO` es una llamada de criterio ("un corte
  ya anunciado es más urgente que una presión baja actual"), no algo que un dato mida. Si en la
  práctica resulta al revés, hay que revisar esta única función, no cazar el criterio disperso en dos
  servicios.
- **Condiciona:** cualquier código nuevo que combine estados en conflicto para el mismo sector debe
  reusar `EstadoServicio.masSevero`, no inventar su propia tabla de severidad — es exactamente el
  defecto que `REC-015` señaló para los colores de estado.

### Cómo se revierte
Cambiar el orden es editar los cuatro casos de `EstadoServicio.severidad()` — un solo método, sin
tocar a quien lo llama. Volver a un criterio "por servicio" (cada uno con su propia regla) implica
deshacer el refactor de `GestionarCorteOficialService` y quitarle a `ActualizarEstadosPorVentanaService`
la dependencia de `CorteAguaRepository` que le dio esta decisión.

---

## ADR-062 — El bloqueo del último administrador se implementa nativo en Mongo, no reutilizando el bloqueo distribuido de Redis

- **Fecha:** 2026-09-22
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegado al agente, Fase 3 de `docs/ingenieria/plan-validacion-backend.md`)

### Contexto
`BUG-100`: `AdministrarCuentaService.exigirQueQuedeUnAdministrador` comprueba el conteo de ADMIN
activos y escribe la cuenta afectada en dos pasos separados, sin nada que serialice dos peticiones
concurrentes entre sí. El proyecto ya tiene un bloqueo distribuido — `EjecucionUnicaRedis` (`SET NX
PX` sobre Redis) — que usa `PlanificadorDeVentanas` y otros jobs programados para no correr la misma
tarea dos veces. Reutilizarlo aquí era la opción obvia por consistencia, hasta revisar su semántica
de fallo: *"Con Redis caído se OMITE la tarea en vez de ejecutarla"* (javadoc de la clase). Correcto
para un job de fondo (el próximo ciclo reintenta solo); no sirve para una petición HTTP de un
administrador, que no puede "omitirse" en silencio — tiene que fallar con un error claro y
reintentable, o el cliente nunca se entera de que no pasó nada.

Mongo, en cambio, no está configurado como *replica set* (`docker-compose.yml`, sin `--replSet`), así
que una transacción multi-documento tampoco es una opción hoy — es justamente el punto anterior de
esta misma Fase 3, sin resolver todavía.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Reutilizar `EjecucionUnicaRedis` tal cual | Cero código nuevo, mismo patrón que ya conoce el equipo | Su semántica de "omitir si Redis cae" no es válida en una petición síncrona de usuario — un cambio de administrador que "no pasó nada" sin avisar es peor que rechazarlo |
| Escribir una variante de `EjecucionUnicaRedis` que falle en vez de omitir | Reusa la infraestructura de Redis ya en producción | Añade una segunda dependencia (Redis) a una guarda cuya fuente de verdad ya es Mongo (`usuarios`); si Redis cae pero Mongo no, la guarda se vuelve indisponible aunque los datos que protege sí lo estén |
| **Bloqueo nativo en Mongo**, un documento de control (`findAndModify` atómico, con vencimiento) | Sin dependencia nueva: si Mongo no responde, la petición ya iba a fallar de todas formas (ahí vive `usuarios`). Mismo patrón ya probado del proyecto (`SectorMongoAdapter.cambiarEstadoSiEs`) | Hay que reimplementar la adquisición/liberación con vencimiento que `EjecucionUnicaRedis` ya resolvía sobre Redis |

### Decisión
`BloqueoDeAdministradoresPort` (dominio, `domain/port/out/`) con un único método,
`ejecutarExclusivo(Supplier<T>)`. `BloqueoDeAdministradoresMongoAdapter` lo implementa sobre un
documento único (`bloqueos_administracion`, `_id="administradores"`) con `expiraEn` y `token`,
adquirido con `findAndModify` atómico (vencimiento de 10s si el proceso muere con el bloqueo tomado)
y liberado solo si el token sigue siendo el vigente. `AdministrarCuentaService.suspender` y
`.cambiarPermisos` ejecutan el conteo y la escritura dentro de ese bloqueo; si no se puede adquirir,
lanza y no toca la cuenta.

### Consecuencias
- **Gana:** la guarda del último administrador falla cerrado ante cualquier problema (rechaza y se
  puede reintentar), sin depender de un almacén distinto al que ya es su fuente de verdad.
- **Pierde:** hay dos mecanismos de bloqueo distintos en el proyecto (Redis para jobs de fondo, Mongo
  para esta guarda), en vez de uno solo — la consistencia se sacrifica por la semántica de fallo
  correcta en cada caso.
- **Condiciona:** si en el futuro Mongo pasa a *replica set* (siguiente punto de esta misma Fase 3) y
  se adoptan transacciones multi-documento, este bloqueo podría reemplazarse por una transacción que
  cuente y escriba atómicamente sin necesitar un documento de control aparte — no es urgente mientras
  siga corrigiendo el bug que motivó esta decisión.

### Cómo se revierte
Quitar `BloqueoDeAdministradoresPort`/`BloqueoDeAdministradoresMongoAdapter` y volver a llamar
`exigirQueQuedeUnAdministrador` directamente reintroduce `BUG-100`. Migrar a una transacción de Mongo
exige primero el *replica set* de esta misma fase.

---

## ADR-063 — Mongo local corre como *replica set* de un nodo, con `directConnection=true` en los scripts del host

- **Fecha:** 2026-09-22
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegado al agente, Fase 3 de `docs/ingenieria/plan-validacion-backend.md`)

### Contexto
`docker-compose.yml` levantaba Mongo como una instancia única, sin `--replSet`. MongoDB solo soporta
transacciones multi-documento sobre un *replica set* (aunque sea de un solo nodo) — es el
prerrequisito explícito de esta misma Fase 3 antes de poder agrupar estado y bitácora en una
transacción. `ADR-062`, en esta misma fase, resolvió mientras tanto la guarda del último
administrador con un documento de control aparte, precisamente porque este prerrequisito no estaba
listo todavía.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Dejar Mongo como instancia única y usar el documento de control (`ADR-062`) para todo lo que necesite atomicidad | Cero cambio de infraestructura | No escala: cada invariante nueva entre documentos necesitaría su propio bloqueo a mano, en vez de una transacción real |
| **Replica set de un nodo en `docker-compose.yml`** | Habilita transacciones multi-documento reales; mismo modo en que ya corre Mongo en las pruebas de integración (Testcontainers lo hace por defecto, verificado: `setName='docker-rs'` en sus logs) | Un nodo que se anuncia con un nombre distinto según quién pregunta (contenedor vs. host) rompe a quien asuma `localhost` sin más |

### Decisión
`mongo` arranca con `command: ["--replSet", "rs0", "--bind_ip_all"]`. Un servicio nuevo,
`mongo-init-replica` (imagen `mongo:7.0`, sin build propio), corre una vez, comprueba con
`rs.status()` si ya está iniciado y si no, llama `rs.initiate()` con `host: "mongo:27017"` — idempotente,
así que un `docker compose up` repetido no falla. `backend` espera a
`mongo-init-replica: condition: service_completed_successfully`, no solo a que `mongo` esté sano.
`SPRING_DATA_MONGODB_URI` pasa a `mongodb://mongo:27017/aguavigia?replicaSet=rs0`.

**Hallazgo al verificarlo en vivo:** los scripts de siembra (`scripts/sembrar-*.mjs`) conectaban por
defecto a `mongodb://localhost:27017` desde el host. Con el replica set activo, el driver de Mongo
descubre que el único miembro se anuncia como `mongo:27017` (el nombre que solo resuelve dentro de la
red de Docker) e intenta reconectarse ahí — `getaddrinfo ENOTFOUND mongo`, reproducido y confirmado
antes de corregirlo. Se cambió el valor por defecto de los cuatro scripts a
`mongodb://localhost:27017/?directConnection=true`, que le dice al driver que hable con ese nodo
directamente sin seguir el descubrimiento de topología del replica set. Reverificado:
`sembrar-sectores.mjs` vuelve a sembrar los 211 sectores sin error.

`docker-compose.prod.yml` **no se toca en este ADR** — sigue como instancia única con autenticación.
Convertirlo exige repetir este mismo análisis con el usuario root de producción de por medio (el
respaldo/restauración de `BUG-088` corre `mongodump` dentro del propio contenedor, así que no le
afecta este cambio, pero un futuro replica set autenticado sí necesita su propio ADR).

### Consecuencias
- **Gana:** el siguiente punto de esta Fase 3 (transacciones multi-documento entre estado y bitácora)
  ya tiene su prerrequisito de infraestructura resuelto en local.
- **Pierde:** cualquier herramienta nueva que se conecte a Mongo desde el host (no desde un
  contenedor de la misma red) tiene que acordarse de `directConnection=true` — es una trampa fácil de
  repetir; queda anotada aquí y en el comentario de cada script.
- **Condiciona:** `docker-compose.prod.yml` sigue sin replica set — las transacciones que se
  construyan sobre este cambio funcionarán en local pero no en producción hasta que se repita este
  ADR para el compose de producción, con su propia complejidad de autenticación.

### Cómo se revierte
Quitar `command` de `mongo`, el servicio `mongo-init-replica` y el `depends_on` que lo espera; volver
`SPRING_DATA_MONGODB_URI` a la URI sin `?replicaSet=rs0`; quitar `?directConnection=true` de los
cuatro scripts de siembra (vuelven a conectar sin problema a una instancia única).

---

## ADR-064 — Transacción multi-documento detrás de un puerto, con notificaciones e invalidación de caché diferidas al commit

- **Fecha:** 2026-09-22
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto (delegado al agente, Fase 3 de `docs/ingenieria/plan-validacion-backend.md`)

### Contexto
Con el *replica set* local ya resuelto (`ADR-063`), quedaba el punto que ese ADR dejó pendiente:
agrupar el guardado de un sector y su evento de bitácora en una sola transacción. Cuatro servicios
escribían ambos documentos por separado, sin nada que revirtiera el primero si el segundo fallaba —
`GestionarCorteOficialService` incluso documentaba el riesgo en un comentario ("si esto falla a
mitad del `for`, el corte ya quedó guardado con algunos sectores movidos de estado y otros no").
`EvaluarConsensoService` y `RevisarPropuestaIngestaService` tenían la misma exposición. Además,
`SectorMongoAdapter` publicaba `SectorActualizadoEvent` (correo, push, SSE) e invalidaba la caché de
sectores de forma síncrona en cuanto el documento se guardaba — antes de que cualquier transacción
que lo envolviera confirmara.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| `@Transactional` de Spring directo en los servicios de `application/` | Una sola anotación, patrón conocido | Filtra una dependencia de framework a la capa de aplicación sin necesidad; el proyecto ya evita eso salvo el cableado explícitamente permitido (`ArchUnit`, sprint de Fase 1) |
| **Puerto `TransaccionPort` con un adaptador Mongo detrás** | Mismo patrón que `BloqueoDeAdministradoresPort` (`ADR-062`); la aplicación solo ve `ejecutar(Supplier)`, la tecnología concreta vive en `infrastructure/` | Una capa más de indirección para algo que en Spring puro sería una anotación |
| Reemplazar `BloqueoDeAdministradoresPort` por esta misma transacción, ahora que ya es posible (`ADR-062` lo dejó insinuado) | Un solo mecanismo de atomicidad en vez de dos | Fuera de alcance de esta tarea (agrupar estado+bitácora) y el bloqueo ya funciona; cambiarlo sin necesidad es riesgo sin beneficio inmediato |

### Decisión
`TransaccionPort` (dominio, `domain/port/out/`) con un único método `ejecutar(Supplier<T>)`.
`TransaccionMongoAdapter` lo implementa con `TransactionTemplate` sobre un `MongoTransactionManager`
(`MongoTransaccionConfig`, único `PlatformTransactionManager` del backend), y reintenta hasta 3 veces
si Mongo marca la falla como `TransientTransactionError` (conflicto de escritura entre transacciones
concurrentes) — cualquier otra excepción revierte y se propaga en el primer intento. Cuatro servicios
envuelven ahí su par estado+evento: `GestionarCorteOficialService` (todo el bucle de sectores de un
mismo corte, no una transacción por sector), `EvaluarConsensoService`, `ActualizarEstadosPorVentanaService`
(solo cuando hay una propuesta que sustente el evento; el saneado sin sustento sigue siendo una sola
escritura, ya atómica) y `RevisarPropuestaIngestaService`.

`SectorMongoAdapter` reemplaza el `@CacheEvict` inmediato y el `eventPublisher.publishEvent(...)`
síncrono por un registro en `TransactionSynchronizationManager` (`afterCommit`) cuando hay una
transacción Spring activa; fuera de una transacción (llamadas que no pasan por `TransaccionPort`)
mantiene el comportamiento inmediato de siempre. Los tres listeners existentes (`NotificarSuscripcionesService`,
`AlertaPushSectorListener`, el SSE de `SectorController`) no cambian: siguen con `@EventListener`
normal, sin saber que el evento ahora puede llegar diferido.

### Consecuencias
- **Gana:** una falla a mitad de una transacción revierte todas sus escrituras (probado contra Mongo
  real en `TransaccionMongoAdapterIntegrationTest`); un correo, push, SSE o invalidación de caché ya
  no puede anunciar un cambio que luego se revierte (`SectorMongoAdapterTransaccionTest`).
- **Pierde:** cada servicio que agrupa dos escrituras gana una dependencia más en su constructor; el
  diferido de eventos en `SectorMongoAdapter` es un mecanismo silencioso (nadie que lea
  `NotificarSuscripcionesService` sabe que su evento pudo demorarse) — documentado solo en el
  javadoc de `trasConfirmar`.
- **Condiciona:** como `ADR-063` ya advertía, esto solo funciona donde Mongo corre como *replica set*
  — `docker-compose.prod.yml` sigue como instancia única a propósito, así que estas transacciones no
  se activan ahí todavía.

### Cómo se revierte
Quitar `TransaccionPort`/`TransaccionMongoAdapter`/`MongoTransaccionConfig`, devolver los cuatro
servicios a escribir estado y evento por separado, y en `SectorMongoAdapter` volver a `@CacheEvict` y
`eventPublisher.publishEvent(...)` inmediatos. Reintroduce el riesgo de escritura parcial que este ADR
cierra.

---

## ADR-065 — Los casos de uso no llevan anotaciones de Spring; el cableado, los listeners y las tareas viven en `infrastructure/`

- **Fecha:** 2026-09-23
- **Estado:** Aceptada
- **Decide:** Sebastián (delegado: «continúa la Fase 4»)

### Contexto
El plan de validación (`plan-validacion-backend.md`, Fase 4) exige que los casos de uso no dependan
de Spring ni de Mongo. Hasta hoy 33 archivos de `application/` importaban Spring (`@Service`,
`@Component`, `@Value`, `@Async`, `@EventListener`), y `ArchUnit` solo vetaba la tecnología concreta
(Fase 1 del mismo plan). `SectorController` también escuchaba eventos con `@Async`/`@EventListener`, y
`SseSectoresBroadcaster` (Redis + `@Scheduled`) vivía en `api/`. Tres controladores tenían reglas:
`HistorialDeCortesController` (orden y paginación), la paginación del sustento en `BitacoraController`
y el filtro «sector afectado» en `Open311Controller`.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| A. `@Bean` explícito para cada caso de uso | Cableado 100 % visible | 35 métodos de puro trámite |
| B. `@ComponentScan` con filtro de nombres + `@Bean` solo para los 6 que leen `aguavigia.*` | Poco código; los servicios quedan sin anotación | El filtro es por nombre (`*Service`): una clase nueva con otro nombre no se registra |
| C. Dejar las anotaciones (estado previo) | Cero cambio | Incumple la Fase 4 |
| D. Dividir `AdministrarCuentaService` (5 acciones) y `ConfigurarSegundoFactorService` (3) | Un servicio = una acción literal | Copia las guardas de seguridad comunes (no auto-administrarse, último admin, revocar sesiones) 5 veces, justo lo que el javadoc de `AdministrarCuentaUseCase` quería evitar |

### Decisión
Opción B. `CasosDeUsoConfig` (en `infrastructure/config`) escanea `application/` por nombre y declara a
mano los seis servicios con propiedades. Los listeners (`AlertaPushSectorListener`,
`NotificarSuscripcionesListener`, `AvisoSseSectorListener`) y `SectorActualizadoEvent` pasan a
`infrastructure/eventos`; `SseSectoresBroadcaster` y su config, a `infrastructure/sse`. El orden y la
paginación del histórico de cortes, el sustento de un evento y el filtro de Open311 pasan a tres casos
de uso nuevos. No se divide `AdministrarCuentaService` ni `ConfigurarSegundoFactorService` (opción D
descartada): son familias cohesivas con guardas compartidas, no servicios que hagan «dos cosas»
sueltas. `ArchUnit` exige ahora que `application/` solo dependa de dominio, Java y slf4j, y que `api/`
no escuche eventos ni programe tareas.

### Consecuencias
- **Gana:** la aplicación se puede ejecutar sin Spring; el cableado está en un solo lugar; reglas
  automáticas que impiden regresar.
- **Pierde:** el registro por nombre es una convención frágil (un caso de uso llamado distinto no se
  registra y el fallo aparece al arrancar, no al compilar); los tests de controlador `@WebMvcTest`
  deben importar el servicio real con `@Import`.
- **Sigue sin resolverse:** las lecturas directas a repositorios desde controladores (`ADR-015`) se
  conservan por decisión del plan, incluido `IngestaFallidosController`, que usa el repositorio Mongo
  concreto; `SectorController` sigue inyectando `SseSectoresBroadcaster` (infraestructura) para
  abrir el stream SSE.

### Cómo se revierte
Devolver `@Service`/`@Component`/`@Value` a las clases de `application/` y borrar `CasosDeUsoConfig`;
mover de vuelta los listeners. Es mecánico, sin datos ni contratos involucrados.

---

## ADR-066 — Las alertas por mensajería se arman sobre Telegram, recibiendo por sondeo y quedando apagadas hasta tener el token del bot

- **Fecha:** 2026-09-24
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
`RF041` («Debe») pedía alertas por Telegram o WhatsApp y era el único requisito sin construir: el adaptador de *push*
(`NotificadorPushWebhookAdapter`) solo escribía «Simulando envío» en el log y nadie podía suscribirse. El dueño pidió
construirlo por Telegram y dejarlo armado para conectarlo después. Restricciones verificadas en el repo: el proyecto
corre en local, sin dominio ni HTTPS (`ADR-057`); no hay frontend (`ADR-048`); el presupuesto es cero; y no existe todavía
un bot ni su token, que entrega `@BotFather` y es una credencial de un tercero.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| A. Telegram con webhook | Latencia mínima; sin sondeo | Exige una URL pública con HTTPS: imposible en local (`ADR-057`) |
| B. Telegram con sondeo (`getUpdates`) | Funciona en local y detrás de cualquier red; sin dominio ni certificado | Latencia de unos segundos; solo una instancia puede sondear el bot (dos consumidores dan 409) |
| C. WhatsApp Business | Canal más usado en la ciudad | Cuenta de empresa, verificación y coste por mensaje: choca con presupuesto cero |
| D. Dejarlo declarado sin construir | Cero trabajo y cero riesgo | Deja un «Debe» incumplido y el canal sin probar |

### Decisión
Opción B. Un bot que recibe por **sondeo corto** (cada 3 s, sin espera larga: el planificador tiene un solo hilo y no debe
bloquear el barrido del consenso) y entiende cinco comandos (`/suscribir`, `/baja`, `/estado`, `/mis`, `/ayuda`). Una
suscripción por chat, hasta 10 sectores; **no hay doble opt-in** como en el correo (`RF013`), porque solo la persona puede
escribirle al bot desde su chat y ese mensaje es la confirmación. La baja **borra** el registro del chat (`RNF009`), y un chat
que bloquea al bot se da de baja solo. Sin `TELEGRAM_BOT_TOKEN` el canal queda **armado pero apagado**
(`TelegramDesactivadoAdapter`, como `IOT_KEY` vacía): el resto de la plataforma no cambia. Reemplaza el simulacro de `M14`.

### Consecuencias
- **Gana:** `RF041` deja de estar sin construir; se conecta poniendo un token y reiniciando el backend, sin tocar código; cero
  costo; el dominio y la aplicación no saben que existe Telegram (puertos `EnvioTelegramPort` y `RecepcionTelegramPort`).
- **Pierde / queda condicionado:** **no se probó contra Telegram real**, solo contra un servidor HTTP falso y un Mongo real; el
  primer arranque con un token verdadero puede revelar diferencias. Latencia de unos segundos. Solo una instancia del backend
  puede sondear el mismo bot. El offset de sondeo vive en memoria: tras un reinicio Telegram reenvía lo no confirmado, y los
  comandos son idempotentes. WhatsApp queda fuera.
- **Guarda un dato personal nuevo:** el id de chat de Telegram, mientras haya al menos un sector seguido.

### Cómo se revierte
Quitar `TELEGRAM_BOT_TOKEN` apaga el canal sin tocar código. Retirarlo del todo: borrar las clases `*Telegram*`, la colección
`suscripciones_telegram` y devolver `EnviarAlertaPushService` a un puerto de salida. Es mecánico y sin contratos públicos
involucrados (no hay endpoint HTTP nuevo).

---

## ADR-067 — El frontend nuevo se hace con React 19, Vite y CSS propio, sobre un mapa base PMTiles local y sin el shell de ADR-029

- **Fecha:** 2026-09-25
- **Estado:** Parcialmente reemplazada por ADR-070 — solo la exclusión de webfonts; el stack sigue vigente
- **Decide:** Dueño del proyecto

### Contexto
El frontend se retiró de `main` (`ADR-048`) para rehacerlo desde `docs/api/`. El anterior (React 19 con Tailwind,
framer-motion, gsap, ogl, lucide, Recharts, Leaflet con tiles de OSM/Esri y el shell de `ADR-029`) funcionaba, pero se
leía como una interfaz genérica: justo lo que `DESIGN.md` §9 prohíbe. El dueño aprobó el plan
`docs/ingenieria/plan-frontend.md` el 2026-09-24 y decidió el framework y el mapa base; `ADR-048` decía que lo rehacía
«otra persona», y ahora lo construye Claude en ramas propias, un PR por fase. Restricciones que pesan: todo corre en
local sin internet para el mapa (`ADR-057`), la primera respuesta útil debe llegar en menos de 3 s en 3G (`RNF001`) y
el proyecto no carga webfonts (`DESIGN.md` §4, `ADR-041`).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| A. React 19 + Vite + TS estricto, CSS propio | Primitivas accesibles maduras (React Aria), tipos del contrato con openapi-typescript, el dueño lo conoce; el anterior sirve de referencia de lógica | Runtime más pesado que Svelte: exige *code-splitting* por ruta para cumplir `RNF001` |
| B. SvelteKit | Runtime más liviano | Menos primitivas accesibles maduras; menos familiar para integrar y mantener |
| C. Astro con islas | HTML estático casi gratis | El mapa en vivo y el panel serían islas enormes: se pierde la ventaja |
| Mapa: PMTiles propio de Cartagena | Funciona sin internet, un solo archivo servido por nginx, estilo con la paleta del proyecto | Hay que extraerlo, versionarlo o generarlo y mantener un estilo propio; licencia ODbL que atribuir |
| Mapa: tiles de OSM/Esri | Cero trabajo | Dependen de terceros y de internet (choca con `ADR-057`) y se ven genéricos |
| Mapa: sin fondo, solo polígonos | Lo más liviano | Sin calles, el vecino no ubica su barrio |

### Decisión
Opción A con mapa PMTiles propio. Stack: React 19, Vite, TypeScript `strict`, TanStack Router y Query, openapi-fetch con
tipos generados de `backend/openapi.yaml`, React Aria Components sin estilos, CSS propio (custom properties, `@layer`,
CSS Modules) con los tokens de `DESIGN.md` §2–§3 como única fuente de color, MapLibre GL JS con el protocolo `pmtiles`,
gráficos en SVG a mano, iconos SVG propios y movimiento solo con CSS. **Quedan fuera:** Tailwind, shadcn/ui, lucide o
cualquier set de iconos genérico, framer-motion, gsap, WebGL decorativo, Recharts, tiles de terceros y webfonts. El
mapa ocupa la pantalla, sin shell ni sidebar: **`ADR-029` queda reemplazado** y no se retoma nada del stack anterior.
El detalle (estructura, pantallas, reglas de la API y fases F0–F6) vive en `docs/ingenieria/plan-frontend.md`.

### Consecuencias
- **Gana:** un cliente tipado contra el contrato (la deriva la detecta `api:check` en CI), identidad visual propia y un
  mapa que no depende de internet ni de terceros.
- **Pierde:** se reescribe la interfaz entera sin reutilizar componentes del anterior; los gráficos y los iconos son
  trabajo a mano.
- **Queda abierto, con ADR propio cuando se decida:** si el mapa lleva etiquetas de texto (glifos SDF locales de
  Protomaps, como excepción acotada a la regla de no webfonts) o no lleva texto; si el `.pmtiles` se versiona o se
  genera; y a qué URL apuntan los enlaces de los correos. Servir la SPA desde nginx (F6) cambia la condición de
  `ADR-048` («el proxy sirve solo la API») y también se registra aparte.
- **Obliga:** atribución visible «© OpenStreetMap» (ODbL) en el mapa.

### Cómo se revierte
Antes de F2 es barato: solo existe el andamiaje de `frontend/`. Después, cambiar de framework es reescribir las
pantallas; los tokens, el cliente generado del contrato y las pruebas E2E contra el backend real se conservan.

---

## ADR-068 — El mapa lleva etiquetas con glifos Noto Sans servidos en local, solo en los rangos que usa el español

- **Fecha:** 2026-09-25
- **Estado:** Aceptada
- **Decide:** Dueño del proyecto

### Contexto
`ADR-067` dejó abierto si el mapa lleva texto. MapLibre no pinta texto con las fuentes del sistema: pide glifos SDF
en `.pbf` por bloques de 256 puntos de código (`{fontstack}/{range}.pbf`) según los caracteres de cada etiqueta. El
proyecto no carga webfonts (`DESIGN.md` §4, `ADR-041`) y no depende de internet para el mapa (`ADR-057`). Medido el
2026-09-25 en `protomaps/basemaps-assets` (licencia SIL OFL 1.1): Noto Sans Regular pesa 76 KB en `0-255`, 128 KB en
`256-511` y 64 KB en `8192-8447`; Medium, 78, 130 y 65 KB. Con `0-255` se cubre todo el español (tildes, ñ, ¿, ¡).

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| A. Glifos Noto locales, solo los rangos necesarios | El vecino se orienta por calles y barrios en el mapa; mismo origen, sin internet; ~150 KB en el caso común | Es una fuente, aunque solo la usa el lienzo del mapa: excepción a «nada de webfonts» que hay que acotar |
| B. Mapa sin texto | Cero fuentes; el mapa pesa menos | Sin nombres, ubicar el barrio propio en un celular depende del buscador y la lista; el mapa pierde su función de orientación |
| C. Glifos desde `protomaps.github.io` | Cero trabajo | Depende de internet y de un tercero: choca con `ADR-057` y con la CSP del mismo origen |

### Decisión
Opción A. Se versionan en `frontend/public/mapa/glifos/` Noto Sans Regular y Medium en los rangos `0-255`, `256-511` y
`8192-8447` (guiones y comillas tipográficas), con su `OFL.txt`, y el estilo de MapLibre apunta a
`/mapa/glifos/{fontstack}/{range}.pbf`. **La excepción es solo para el lienzo del mapa:** la interfaz sigue con las
pilas de sistema de `DESIGN.md` §4.

### Consecuencias
- **Gana:** etiquetas de calles, barrios y agua en el mapa, sin internet ni terceros.
- **Pierde:** ~540 KB en el repositorio y ~150 KB extra la primera vez que se pinta el mapa (después, caché). La
  tarjeta de respuesta no espera a los glifos, así que no toca el presupuesto de `RNF001`.
- **Obliga:** un nombre con un carácter fuera de esos rangos se pinta sin ese carácter (MapLibre registra el `404` y
  sigue); si pasa con un nombre real de Cartagena, se agrega el rango. Conservar `OFL.txt` junto a los archivos.

### Cómo se revierte
Barato: se quitan las capas `symbol` del estilo y la carpeta `glifos/`, y el mapa queda como la opción B.

---

## ADR-069 — La guía integral del frontend rige cada pantalla y el estado nulo vuelve a «Sin datos verificados»

- **Fecha:** 2026-09-25
- **Estado:** Parcialmente reemplazada por ADR-070 — su parte visual; siguen las reglas de datos, estados y el nulo
- **Decide:** Dueño del proyecto

### Contexto
F1 produjo prototipos de mapa, reporte, cumplimiento y bitácora, pero el frontend abarca también avisos, cuentas y
el panel. Llevar esos prototipos directamente a código dejaba sin resolver composiciones adaptables, estados de
error y vacío, datos realmente expuestos por cada contrato y la distinción entre estado, consulta y conectividad.
Además, `REC-019` comprobó que el acento claro vigente no llega a 4,5:1 como texto sobre fondo ni acento suave.

La revisión del contrato encontró otra contradicción: `ADR-035` pintaba `estado: null` como `CON_SERVICIO`, aunque
el backend conserva el nulo y no fabrica `actualizadoEn`. La ausencia de aviso puede ser una señal operativa útil,
pero no prueba que un hogar tenga agua; la interfaz principal debe contestar sin convertir esa ausencia en un
estado verificado.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Implementar los prototipos de F1 tal como están | Menos trabajo antes de F2 | Solo cubren cuatro pantallas y contienen controles y cifras de simulación que la API no ofrece |
| Mantener reglas repartidas entre DESIGN.md y el plan | Menos documentación nueva | Obliga a reconstruir jerarquía, estados y límites del contrato en cada fase |
| Adoptar una guía por pantalla y representar el nulo de forma explícita | Una referencia verificable para toda la SPA; no afirma servicio sin dato | Exige adaptar los prototipos antes de aprobar F1 y hace visible que algunos barrios carecen de verificación |

### Decisión
Adoptar `docs/diseno/guia-frontend.md` como especificación de desarrollo por pantalla, subordinada a `DESIGN.md` y
al contrato OpenAPI. Cubre composiciones de 360, 768 y 1280 px, componentes, rutas, datos permitidos, estados de
interacción y criterios de aceptación. Su aprobación es **documental**: no aprueba los prototipos actuales, no
cierra F1 y no inicia F2.

`estado: null` se presenta como **«Sin datos verificados»**, con trama, glifo y texto, sin añadir un quinto valor al
dominio. `ADR-035` queda reemplazada; la parte contractual de `ADR-014` permanece: el backend sigue transmitiendo
el nulo. También se valida `REC-019`: el acento claro pasa a `#06747f`, aplicado a la vez en `DESIGN.md` y
`frontend/src/estilos/tokens.css`, con sus pruebas de contraste (2026-09-25, paso 1 de la guía §7).

### Consecuencias
- **Gana:** cada ruta tiene jerarquía, estados y límites de datos trazables; «sin datos» deja de parecer servicio
  confirmado y el turquesa vuelve a reservarse a acciones.
- **Pierde:** el mapa puede mostrar más zonas neutrales, y los cuatro prototipos deben adaptarse y volver a revisión
  visual antes de cerrar F1.
- **No cambia:** React, CSS propio, PMTiles local, rutas, contratos, dependencias y colores semánticos del servicio.
- **Condiciona:** el acento se movió en ambos tokens y se verificó el muestrario en los dos temas; `REC-019` quedó
  resuelta. F1 sigue esperando la revisión visual de los prototipos adaptados.

### Cómo se revierte
Un ADR futuro puede sustituir la guía o una parte de ella. Volver a presentar el nulo como servicio exige restaurar
expresamente el riesgo aceptado por `ADR-035`; volver al acento anterior exige cambiar a la vez `DESIGN.md`,
`tokens.css` y las cifras fijadas en `contraste.test.ts`, y reabre el incumplimiento de contraste de `REC-019`.

---

## ADR-070 — El frontend adopta una identidad formal: cardenillo y latón, Newsreader con Schibsted Grotesk y movimiento especificado

- **Fecha:** 2026-09-25
- **Estado:** Aceptada — la dirección; los valores concretos esperan la aprobación visual del prototipo
- **Decide:** Dueño del proyecto

### Contexto
Al ver los prototipos de F1 adaptados a la guía (`ADR-069`), el dueño los rechazó: la interfaz seguía el esquema del
frontend retirado (mapa arriba, hoja con la ficha, botón turquesa, fuente del sistema) y «se ve hecha por IA». Pidió
un rediseño total: **formal, minimalista, con colores no genéricos, animaciones y transiciones que den sensación
premium y la vista web como prioridad**, más skills y documentación que mantengan esa orientación. Las guías
consultadas sobre interfaces generadas por IA coinciden en la causa (el modelo vuelve a la estética promedio) y en el
remedio: especificación escrita antes del código, referencias reales, reglas que prohíban lo genérico y movimiento
planeado. Se exploraron tres rumbos (Cartel, Vecino, Reloj del corte) antes de fijar este.

### Alternativas consideradas
| Opción | A favor | En contra |
|---|---|---|
| Mantener la identidad de `DESIGN.md` §3–§4 y pulir | Sin migración | Es justamente lo que el dueño rechazó |
| Rumbo «Cartel», «Vecino» o «Reloj del corte» | Cada uno cambia la experiencia | Ninguno responde a «formal y minimalista»; el dueño pidió otra dirección |
| **Rumbo formal: cardenillo y latón, serif editorial y movimiento especificado** | Sobrio, propio del acueducto, legible en escritorio y celular | Dos fuentes a servir; se rehacen el muestrario y los prototipos |

### Decisión
Adoptar la identidad de `docs/diseno/identidad.md`: paleta cardenillo y latón con neutros de sesgo verde, Newsreader
para titulares y cifras y Schibsted Grotesk para la interfaz (ambas OFL, **servidas desde el propio proyecto**),
reglas finas en vez de tarjetas, composición de escritorio propia y la tabla de movimiento de su §5. Los cuatro
estados del servicio (`DESIGN.md` §2, `ADR-042`) y todas las reglas de datos de la guía no cambian. Dos skills
(`disenar-frontend` y `revisar-diseno`) obligan a leer esa especificación y a revisar capturas antes de dar por
terminada una pantalla. **Reemplaza en parte a `ADR-067`** (la exclusión de webfonts: ahora se permiten las dos
fuentes locales) y **a `ADR-069`** (su parte visual: acento, tipografía y composición de la guía §2–§4).

### Consecuencias
- **Gana:** una identidad que no se confunde con una plantilla y reglas escritas que cualquier sesión debe seguir.
- **Pierde:** el acento `#06747f` y las composiciones de la guía §4 recién aplicadas; los prototipos F1 anteriores
  quedan como histórico. Unos 160 KB de fuentes que el celular descarga una vez.
- **Condiciona:** la migración de `DESIGN.md` §3–§4, `tokens.css`, sus pruebas y el muestrario se hace en un solo
  cambio después de la aprobación visual (`identidad.md` §8). F1 no cierra ni F2 empieza sin esa aprobación.

### Cómo se revierte
Otro ADR vuelve a la identidad anterior: `DESIGN.md` §3–§4 siguen en el historial y el prototipo de la guía está en
el Artifact de F1. Mientras no se migre, revertir solo exige descartar `identidad.md` y las dos skills.

---

<!--
Siguiente número disponible: ADR-071
Para agregar: usa la skill `registrar-decision`.
Recuerda: append-only. Las entradas viejas solo cambian de estado, no de contenido.
-->
