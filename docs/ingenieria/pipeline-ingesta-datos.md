# Pipeline de ingesta de datos y detección con IA

> **Estado (2026-09-29): diseño de Sprint 0, ya construido — y lo construido difiere.** Sin IA (`ADR-025`):
> extracción por heurística determinista. Lo que el sistema hace de verdad está en
> `comportamiento-del-sistema.md` § Ingesta automatizada; si este documento y aquel discrepan, gana aquel.
> **Corrige:** una afirmación previa errónea sobre el `robots.txt` de Acuacar (ver §1).

### Qué difiere entre este diseño y lo construido

| Tema | Diseño (abajo) | Construido | Dónde |
|---|---|---|---|
| Extracción | Claude con `anthropic-java` y salida estructurada (§4) | `HeuristicaExtractor`: expresiones regulares ancladas en la enumeración de barrios del boletín; sin dependencia de Anthropic en `backend/pom.xml` | `infrastructure/ingest/HeuristicaExtractor.java`, `ADR-025` |
| Qué se publica | Por umbral: ≥ 0.85 publica solo, 0.5–0.85 a revisión, < 0.5 se archiva (§3, etapa 5) | Todo va a `RegistrarPropuestaIngestaUseCase`. Una propuesta de Acuacar se aprueba en el acto; una de prensa queda `PENDIENTE` para el veedor. La confianza (0.85 / 0.75 / 0.45) solo ordena la cola | `PipelineOrquestador.java:28-33`, `RegistrarPropuestaIngestaService.java:85-89`, `ADR-028`, `ADR-032`, `ADR-034` |
| Frecuencia | Por fuente: 10, 15, 30 min, 6 h (§7) | Un solo ciclo para todas las fuentes, cada `aguavigia.ingesta.intervalo-ms` = 600 000 ms (10 min) | `application.yml:171`, `PipelineOrquestador.java:99` |
| Fuentes | Acuacar API REST + RSS, prensa, Meta Content Library | Acuacar solo por `wp-json`; prensa: Google News, Zona Cero, Caracol Radio y W Radio. `MetaLibraryCollector` no existe. Modo `local` sin red (`ADR-082`) | `application.yml:175-188` |
| Cortesía con el sitio | 1 petición cada 30 s, `If-Modified-Since`, jitter aleatorio (§6, §7) | No existen. Sí: `User-Agent` identificable obligatorio, timeouts de 10 s / 15 s, lectura incremental con `after=` desde la última marca | `application.yml:167-169`, `AcuacarApiCollector.java:85` |
| Reintentos y cortacircuitos | Backoff exponencial **con jitter**; circuito que reintenta con backoff creciente | Reintento: 3 intentos con 2 s y 4 s de espera, **sin jitter**. Cortacircuitos: abre tras 3 fallos seguidos, **5 min fijos** abierto y luego una sola llamada de prueba | `application.yml:192-214` |
| Deduplicación | Hash en Mongo + set en Redis | Solo el set de Redis, 7 días (`DeduplicadorReciente`); además no se repite una propuesta pendiente igual | `DeduplicadorReciente.java`, `RegistrarPropuestaIngestaService.java:61` |
| Guardar crudo primero | `DocumentoCrudo` se persiste antes de procesar | No se persiste. Solo los que fallan quedan en `documentos_fallidos` (visibles en `GET /api/veedor/ingesta/fallidos`); cada ciclo relee con 2 días de solape para no perder nada | `PipelineOrquestador.java`, `DocumentoFallidoDocumento.java` |
| Ubicación | `DocumentoCrudo` y `FuenteDatosPort` en el dominio (§9) | Los dos en `infrastructure/ingest/`: su forma la define el pipeline, no el negocio | `infrastructure/ingest/` |
| Evaluación | Conjunto dorado, F1 en CI (§4) | No existe: sin modelo no hay clasificador que medir. Las heurísticas tienen pruebas unitarias con boletines reales | — |

---

## 1. Hallazgo que cambia la estrategia

En un análisis anterior se afirmó que el sitio de Acuacar **prohibía el acceso automatizado** vía `robots.txt`.
**Esa afirmación era incorrecta.** El archivo real (verificado el 2026-08-06) es:

```
User-agent: *
Disallow: /wp-admin/
Allow: /wp-admin/admin-ajax.php

Sitemap: https://www.acuacar.com/sitemap_index.xml

User-agent: *
Disallow: /wp-content/uploads/wpo/wpo-plugins-tables-list.json
```

Solo se excluye el panel administrativo. **Todo el contenido público es rastreable**, y el sitio incluso
publica un sitemap para facilitarlo.

Más aún: `acuacar.com` es un **WordPress con la API REST habilitada**. Verificado en producción:

| Endpoint | Resultado |
|---|---|
| `GET /wp-json/wp/v2/posts` | **HTTP 200**, `application/json`, 307 posts, 103 páginas |
| `GET /feed/` | **HTTP 200**, `application/rss+xml` |
| `GET /sitemap_index.xml` | **HTTP 200**, 3 sitemaps, actualizado a diario |

Campos disponibles por boletín: `id`, `date`, `modified`, `link`, `title.rendered`,
`content.rendered`, `excerpt.rendered`, `categories`. Cabeceras `X-WP-Total` y `X-WP-TotalPages`
para paginar. Además soporta `?after=`, `?modified_after=` y `?_fields=` para traer solo lo nuevo.

**No necesitamos scraping de HTML frágil: hay una API estructurada, pública y estable.** El proyecto
gana un pilar técnico que antes no tenía, y la "decisión ética de no scrapear" se reemplaza por algo
más honesto: *consumir la fuente oficial de la forma menos invasiva posible*.

Los boletines relevantes existen y son abundantes. Ejemplos reales del sitemap:

- `#2846 — AGUAS DE CARTAGENA ANUNCIA SUSPENSIÓN PROGRAMADA DEL SERVICIO … EN EL 40 % DE LA CIUDAD`
- `#2547 — SUSPENSIONES EN EL ACUEDUCTO AL 63 % DE CARTAGENA POR OBRAS PRIORITARIAS`
- `#2549 — RESTABLECIMIENTO GRADUAL DEL SERVICIO DE ACUEDUCTO`
- `#2574 — EMERGENCIA EN TIERRA BAJA: ROTURA DE CONDUCCIÓN TERRESTRE`

Cada uno contiene sectores, fechas, horas prometidas y causa: exactamente lo que alimenta el
**Índice de Cumplimiento** (M6).

---

## 2. Las cuatro capas de datos

El sistema no depende de una sola fuente. Cada capa tiene distinta confiabilidad, latencia y estatus legal.

| Capa | Fuente | Método | Latencia | Confiabilidad | Estatus |
|---|---|---|---|---|---|
| **L1 — Oficial** | `acuacar.com` | WP REST API (el RSS se verificó, pero no se consume) | ~minutos | Alta (es la fuente autoritativa) | ✅ Permitido por `robots.txt` |
| **L2 — Prensa** | Medios locales y nacionales | Google News RSS + RSS propios | ~horas | Media (puede exagerar o simplificar) | ✅ RSS es publicación deliberada |
| **L3 — Social** | Facebook / Instagram / X | Meta Content Library (académico) | ~horas | Baja (ruido, sarcasmo, duplicados) | ⚠️ Solo vía API oficial — ver §5 |
| **L4 — Ciudadana** | La propia plataforma | Reportes de usuarios (M2/M3) | **segundos** | Alta en agregado, baja individual | ✅ Datos propios |

**L4 es la más valiosa y la más rápida**, y es la única que nadie más tiene. L1 dice lo que se
prometió; L4 dice lo que realmente pasó. El Índice de Cumplimiento vive exactamente en esa diferencia.

### L2 — Prensa, verificado

Google News RSS funciona y agrega todos los medios en una sola consulta:

```
https://news.google.com/rss/search
  ?q=acuacar+OR+%22corte+de+agua%22+Cartagena
  &hl=es-419&gl=CO&ceid=CO:es-419
```

Devuelve **100 ítems** con `title`, `link`, `pubDate` y `source`. Resultados reales incluyen boletines
de Acuacar, comunicados de la Alcaldía Mayor de Cartagena y notas de prensa sobre el fallo del Tribunal.

De los medios locales, **Zona Cero** expone RSS propio y funcional (`zonacero.com/rss.xml`).
**Caracol Radio y W Radio, reverificados el 2026-08-08, también funcionan**: el feed real no estaba
en `/rss/` (esa ruta nunca existió) sino en `/arc/outboundfeeds/google-news-feed/?outputType=xml`
—mismo CMS Arc/PEP en ambos—, permitido explícitamente por su `robots.txt` (que sí bloquea la ruta
legacy `/feed.aspx`, evitada). **RCN Radio sigue sin feed localizado** pese a probar cuatro rutas
candidatas — su `robots.txt` lo permite, pero no se encontró el feed real.

**El Universal, El Tiempo, El Heraldo y Blu Radio quedan fuera del pipeline automatizado por decisión
explícita del propio medio**: sus archivos `robots.txt` bloquean directamente a `anthropic-ai`,
`Claude-Web`, `GPTBot` y `CCBot` con `Disallow: /` sobre el sitio completo. Se respeta sin excepción,
aunque un colector con `User-Agent` propio técnicamente podría no ser detectado — spoofear el origen
para saltarse una restricción declarada sería incoherente con lo que este proyecto le exige a Acuacar.
Esa cobertura se recibe igualmente, de forma indirecta, a través de Google News RSS. Detalle completo
de cada medio probado, con la petición y el resultado exacto, en
`docs/ingenieria/auditoria-fuentes-de-datos.md`.

---

## 3. Arquitectura del pipeline

Cinco etapas, tal como están construidas (`PipelineOrquestador.java`). El diseño original ponía una IA en la etapa 4 y un
enrutamiento por umbral en la 5; ver la tabla de estado arriba. Cada colector es un **adaptador**; su puerto,
`FuenteDatosPort`, vive en `infrastructure/ingest/` y no en el dominio.

```
┌── COLECTORES (infrastructure/ingest/) ─────────────────────────┐
│  AcuacarApiCollector · RssCollector · ColectorLocalDeBoletines │
│  cada uno implementa FuenteDatosPort (infrastructure/ingest/)  │
│  un ciclo cada 10 min para todas; modo local sin red: ADR-082  │
└───────────────────────────┬────────────────────────────────────┘
                            ▼
┌── 1. NORMALIZACIÓN ────────────────────────────────────────────┐
│  Todo se convierte a DocumentoCrudo (no se persiste):          │
│  { fuente, urlOriginal, publicadoEn, titulo, texto, hash,      │
│    imagenUrl }                                                 │
│  hash = SHA-256(titulo + texto normalizados)                   │
└───────────────────────────┬────────────────────────────────────┘
                            ▼
┌── 2. DEDUPLICACIÓN ────────────────────────────────────────────┐
│  Set de hashes vistos en Redis, 7 días (DeduplicadorReciente)  │
│  No hay chequeo contra Mongo                                   │
└───────────────────────────┬────────────────────────────────────┘
                            ▼
┌── 3. PREFILTRO DETERMINISTA (PrefiltroDeterminista) ───────────┐
│  9 palabras: suspensión · racionamiento · corte · avería       │
│  restablecimiento · fuga · PTAP · acueducto · presión          │
└───────────────────────────┬────────────────────────────────────┘
                            ▼
┌── 4. EXTRACCIÓN HEURÍSTICA (HeuristicaExtractor, sin IA) ──────┐
│  Barrios de la enumeración explícita + ventana declarada       │
│  Confianza 0.85 / 0.75 / 0.45 según la evidencia + citaTextual │
│  Si falla → documentos_fallidos; reintento dentro del solape   │
└───────────────────────────┬────────────────────────────────────┘
                            ▼
┌── 5. PROPUESTA (RegistrarPropuestaIngestaUseCase) ─────────────┐
│  Acuacar  → se aprueba en el acto (ADR-034)                    │
│  Prensa   → PENDIENTE hasta que un veedor decida (ADR-028)     │
│  La confianza ordena la cola; no publica nada por sí sola      │
└────────────────────────────────────────────────────────────────┘
```

**Nada llega al mapa público sin pasar por la etapa 5**, y de la prensa nada llega sin un veedor. Una extracción que
inventara un corte sería peor que no tener el dato: destruiría la credibilidad del proyecto.

---

## 4. La capa de IA (diseño no construido)

> **No existe en el código (`ADR-025`).** La dependencia `anthropic-java` se quitó (PR #137) y `backend/pom.xml` no
> la tiene; no hay llamada a ningún modelo ni `ANTHROPIC_API_KEY`. Lo que ocupa su lugar es `HeuristicaExtractor`
> (§3, etapa 4). De esta sección sobrevive el contrato: `infrastructure/ingest/EventoExtraido.java` tiene los campos
> de abajo, con `tipo` como `String`, y `citaTextual` es la frase literal que el veedor lee para decidir (`ADR-006`).
> Se conserva como registro del diseño descartado.

### Qué hace exactamente

Dos tareas distintas, no una:

1. **Clasificar** — ¿este texto habla de una interrupción del servicio de acueducto en Cartagena?
   (sí / no / relacionado pero no es interrupción)
2. **Extraer** — si es que sí: sectores afectados, tipo de evento, fecha y hora de inicio, hora
   prometida de restablecimiento, causa declarada, y **cuánto de esto estaba explícito en el texto
   frente a cuánto fue inferido**.

Ese último punto es el que evita el desastre. El modelo debe reportar qué dedujo, no presentar
inferencias como hechos.

### Contrato de salida

Se usa **salida estructurada** (`output_config.format`), no parsing de texto libre. El esquema es el
contrato; el modelo no puede devolver algo que no valide.

```java
public record EventoExtraido(
    boolean esInterrupcionDeAcueducto,
    TipoEvento tipo,              // SUSPENSION_PROGRAMADA | EMERGENCIA | RESTABLECIMIENTO | RACIONAMIENTO
    List<String> sectoresMencionados,
    Instant inicioDeclarado,
    Instant finPrometido,
    String causaDeclarada,
    double confianza,             // 0.0 – 1.0
    List<String> camposInferidos, // qué NO estaba explícito en el texto
    String citaTextual            // fragmento exacto que sustenta la extracción
) {}
```

`citaTextual` es la defensa contra alucinaciones: si el modelo no puede citar la frase del boletín que
respalda su extracción, la extracción no se publica. Es verificable automáticamente —
`documento.texto().contains(cita)`.

### Llamada al modelo

Backend en Java, así que se usa el SDK oficial de Anthropic:

```xml
<dependency>
  <groupId>com.anthropic</groupId>
  <artifactId>anthropic-java</artifactId>
</dependency>
```

```java
AnthropicClient client = AnthropicOkHttpClient.fromEnv();  // lee ANTHROPIC_API_KEY

StructuredMessageCreateParams<EventoExtraido> params = MessageCreateParams.builder()
    .model("claude-opus-5")
    .maxTokens(4096L)
    .system(PROMPT_SISTEMA)          // estable → se cachea entre peticiones
    .outputConfig(EventoExtraido.class)
    .addUserMessage(documento.texto())
    .build();

EventoExtraido evento = client.messages().create(params)
    .content().stream()
    .flatMap(cb -> cb.text().stream())
    .findFirst()
    .orElseThrow()
    .text();
```

> Verificar los nombres exactos del builder contra la versión del SDK que quede en el `pom.xml`
> antes de dar por buena esta firma.

### Control de costo (sin sacrificar calidad del modelo)

| Palanca | Efecto |
|---|---|
| Prefiltro determinista (etapa 3) | Elimina ~70 % del volumen antes de gastar un solo token |
| Deduplicación por hash (etapa 2) | El mismo boletín replicado en 5 medios se procesa **una** vez |
| Caché de prompt del sistema | El prompt de instrucciones es idéntico siempre → se cobra a ~0.1× |
| Procesamiento por lotes nocturno | El histórico se carga con la Batch API a mitad de precio |
| Solo texto nuevo o modificado | `?modified_after=` en la API de Acuacar |

El volumen real es modesto: Acuacar publica ~300 boletines en su histórico completo y unos pocos por
semana. Esto **no** es un problema de escala; es un problema de precisión.

### Cómo se sabe si la IA funciona

Sin esto, la capa de IA es fe ciega:

- **Conjunto dorado**: 100 boletines históricos etiquetados a mano (sí/no + campos).
  Es trabajo de una tarde entre cinco personas y se convierte en un anexo del informe.
- **Métricas**: precisión, exhaustividad y F1 sobre ese conjunto. Se reportan en el Capítulo IV.
- **Prueba de regresión**: el conjunto dorado corre en CI. Si un cambio de prompt baja el F1, la build falla.
- **Falsos positivos son peores que falsos negativos**: un corte inventado destruye la confianza; un
  corte omitido lo reporta la comunidad (L4). El umbral de confianza se calibra sesgado hacia la precisión.

Esto convierte "usamos IA" en una afirmación medible y defendible ante el docente.

---

## 5. Facebook e Instagram: la respuesta honesta

**No se va a scrapear Facebook ni Instagram, y no es por pereza técnica.**

Hacerlo viola los Términos de Servicio de Meta, y las herramientas de acceso público que existían
desaparecieron: CrowdTangle cerró en agosto de 2024, y la Instagram Basic Display API fue descontinuada
en diciembre de 2024. Leer publicaciones públicas de páginas de terceros vía Graph API exige el permiso
*Page Public Content Access*, que requiere revisión de la app y verificación de empresa — inviable
para un proyecto de aula, y honestamente inviable para casi cualquiera.

**Existe una vía legítima y encaja perfecto con el proyecto: Meta Content Library.** Es el reemplazo
oficial de CrowdTangle, diseñado específicamente para **investigadores de instituciones académicas**.
Da acceso a contenido público de Facebook e Instagram para investigación. El acceso se solicita a
través del ICPSR y exige afiliación institucional — que este proyecto no tiene.

**Plan realista:**

1. **Solicitud**: requiere respaldo institucional, que este proyecto no tiene; solo se intenta si
   aparece ese respaldo. Es un trámite, no una garantía, y toma semanas.
2. **Si lo aprueban**: se implementa `MetaLibraryCollector` como una fuente L3 más. Es material
   excelente.
3. **Si no se consigue** (lo más probable): **la capa L4 lo reemplaza**. Los
   reportes ciudadanos dentro de la propia plataforma cumplen exactamente la misma función —
   capturar la voz del vecino en tiempo real — sin depender del permiso de una empresa extranjera y
   sin recolectar datos personales de terceros.

El diseño ya lo contempla: un `MetaLibraryCollector` (no existe; no hubo acceso) implementaría `FuenteDatosPort` igual
que los demás. Si llega, se enchufa sin tocar nada más. **Eso es el principio Abierto/Cerrado demostrado con un caso real,
no con un ejemplo de libro.**

Lo mismo aplica a X/Twitter: el nivel gratuito de su API no permite búsqueda de publicaciones, y el
plan pagado cuesta más de lo que justifica un proyecto de aula. Queda documentado como fuera de alcance.

---

## 6. Robustez: "que no falle tan fácil"

Todo colector externo **va a fallar**. La pregunta no es si, sino qué pasa cuando falle.

| Modo de fallo | Qué ocurre sin protección | Mitigación diseñada | Construido |
|---|---|---|---|
| Acuacar cae o cambia de host | El colector lanza excepción en cada ciclo y satura los logs | **Circuit breaker** (Resilience4j): tras N fallos abre el circuito, deja de intentar, reintenta con backoff creciente | Sí, con espera **fija**: abre tras 3 fallos seguidos, 5 min abierto, luego una llamada de prueba (`application.yml:202-214`) |
| Respuesta lenta que nunca cierra | Un hilo bloqueado indefinidamente | Timeouts explícitos de conexión **y** de lectura en el cliente HTTP | Sí: 10 s y 15 s (`application.yml:168-169`) |
| Error transitorio 500 / 429 | Se pierde ese ciclo de datos | **Reintento con backoff exponencial + jitter**, máximo 3 intentos | Sí, **sin jitter**: 3 intentos, esperas de 2 s y 4 s (`application.yml:196-200`) |
| Cambia la forma del JSON | El parser revienta o —peor— devuelve nulos silenciosos | **Detección de deriva de esquema**: si faltan campos obligatorios, no se descarta el dato: se manda a la cola muerta y se alerta | Parcial: el documento que falla va a `documentos_fallidos` |
| Ítem individual malformado | Un ítem corrupto tumba el lote completo | Procesamiento **ítem por ítem** con aislamiento de errores; un fallo no contamina a los demás | Sí (`PipelineOrquestador.procesar`) |
| Doble ejecución del scheduler | Datos duplicados en el mapa | Idempotencia por hash de contenido + índice único en Mongo | Otra vía: `EjecucionUnicaRedis` (una sola réplica corre el ciclo), deduplicador en Redis y no se repite una propuesta pendiente igual |
| ~~La API de Claude falla o limita~~ | — | — | No aplica: no hay IA (`ADR-025`). Un documento que falla va a `documentos_fallidos` y se reintenta mientras caiga en el solape de 2 días |
| Nos bloquean por exceso de peticiones | Perdemos la fuente entera | Rate limiting propio (1 petición cada 30 s), `User-Agent` identificable con correo de contacto, `If-Modified-Since` para no pedir lo que no cambió | Solo el `User-Agent` (obligatorio: sin él los colectores no salen a la red) y la lectura incremental con `after=`. **No hay** límite de 1 cada 30 s ni `If-Modified-Since` |
| Todas las fuentes caen a la vez | El mapa queda congelado mostrando datos viejos como si fueran actuales | **Sello de frescura**: cada sector muestra "actualizado hace X"; si una fuente lleva más de N horas muda, se marca como degradada en la interfaz | Parcial: `Sector.estadoActualizadoEn` / `estadoVerificadoEn` y `GET /api/veedor/ingesta/salud` |

### Principios que sostienen todo lo anterior

**Guardar crudo primero, procesar después** *(diseño; no construido así)*. El diseño persistía cada `DocumentoCrudo`
antes de analizarlo. Lo construido no lo guarda: la marca de cada colector (`MarcaDeIngestaDocumento`) avanza a lo más
reciente recolectado y cada ciclo relee con 2 días de solape; un colector que falla no mueve su marca. Un documento cuyo
procesamiento falla queda en `documentos_fallidos` y se reintenta mientras caiga en ese solape
(`PipelineOrquestador.java:56`, `:165-174`). Reprocesar el histórico es volver a leer la fuente desde una fecha anterior.

**Cola muerta, no `catch` vacío.** Nada se descarta en silencio. Lo que falla queda en
`documentos_fallidos` con el motivo, visible en el panel del veedor. Un pipeline que pierde datos
callado es peor que uno que se cae ruidosamente.

**Salud de fuentes visible.** `/actuator/health` (`ColectorHealthIndicator`) y `GET /api/veedor/ingesta/salud`: por cada colector se expone última
ejecución exitosa, ítems procesados y tasa de error. Es una de las pantallas de la demo de sustentación.

**Ciudadano educado de la web.** `User-Agent` que identifica el proyecto y da un correo de contacto,
lectura incremental (solo lo publicado desde la última marca) y respeto del `robots.txt` (se verifica a mano al elegir
cada fuente, con `verificar-fuente`; el colector no lo lee en ejecución). Las peticiones condicionales y el límite de
una cada 30 segundos del diseño **no se construyeron**. Costo casi nulo y es la diferencia entre un proyecto serio y uno
que molesta al operador.

---

## 7. Frecuencia de sondeo

**Construido:** un solo ciclo, `PipelineOrquestador.ejecutarCicloEnUnaReplica()`, lee todas las fuentes cada
`aguavigia.ingesta.intervalo-ms` = 600 000 ms, 10 min (`application.yml:171`, `INGESTA_INTERVALO_MS`), en una sola réplica
(`EjecucionUnicaRedis`). No hay frecuencia por fuente, ni jitter, ni `If-Modified-Since`: cada ciclo procesa solo lo publicado
desde la marca de la fuente menos 2 días de solape (Acuacar lo pide con `after=`; los feeds RSS se leen enteros). En modo `local` (`ADR-082`) lee boletines guardados y no sale a la red.

| Fuente | Cada (construido) | Diseño original |
|---|---|---|
| Acuacar WP REST API | 10 min | 10 min — es la fuente autoritativa |
| Acuacar RSS | — (no se consume) | 15 min, como redundancia |
| Google News, Zona Cero, Caracol Radio, W Radio (RSS) | 10 min | 30 min — la prensa va detrás del boletín oficial |
| Meta Content Library | — (no existe) | 6 h, si se aprobaba el acceso |
| Reportes ciudadanos | **tiempo real** | Igual: es un evento entrante, no un sondeo |

---

## 8. Lo que esto le suma al proyecto académico

- **Requisitos no funcionales medibles**: disponibilidad del pipeline, latencia de detección
  (la precisión del clasificador era de la capa de IA, no construida). Deja de ser "el sistema debe ser confiable" y pasa a ser verificable.
- **Un patrón de diseño más, aplicado de verdad**: Strategy sobre `FuenteDatosPort`, con tres
  implementaciones reales (`AcuacarApiCollector`, `RssCollector`, `ColectorLocalDeBoletines`) y una cuarta condicionada a
  un permiso externo que no llegó.
- **Un ADR con sustancia**: por qué API REST y no scraping de HTML; por qué no se toca Meta sin
  permiso; qué se hace cuando el permiso no llega.
- **Un capítulo de resultados con números** *(no construido: dependía del conjunto dorado de la capa de IA)*:
  precisión y exhaustividad sobre el conjunto dorado, no impresiones.
- **Una lección honesta documentada**: la primera versión del proyecto afirmó que el `robots.txt`
  prohibía el acceso, sin verificarlo. Verificarlo abrió la mejor fuente de datos del proyecto.
  Eso va en las conclusiones — un docente valora más una corrección documentada que una certeza inventada.

---

## 9. Qué implementar y en qué sprint

Plan de Sprint 0, con lo que pasó de verdad.

| Sprint | Entregable de ingesta (plan) | Qué se construyó |
|---|---|---|
| 1 | `DocumentoCrudo` + `FuenteDatosPort` en el dominio; `AcuacarApiCollector`; carga histórica de los 307 boletines | Construido, con `DocumentoCrudo` y `FuenteDatosPort` en `infrastructure/ingest/`. La carga histórica sale de leer desde 2020 la primera vez (`PipelineOrquestador.java:49`) |
| 2 | Prefiltro determinista; deduplicación por hash; cola muerta; salud de fuentes en Actuator | Construido; la deduplicación es solo en Redis (7 días) |
| 3 | `RssCollector` (Google News + Zona Cero); circuit breakers y política de reintentos | Construido, más Caracol Radio y W Radio. Reintentos sin jitter y cortacircuitos de espera fija (§6) |
| 4 | Capa de IA: extracción estructurada, umbrales de confianza, cola de revisión humana, conjunto dorado y métricas | IA descartada (`ADR-025`). Sí: confianza graduada y `citaTextual` con heurísticas (`ADR-032`) y cola de revisión para la prensa (`ADR-028`, `ADR-034`). Sin umbrales ni conjunto dorado |
| 5 | Reproceso del histórico con el prompt final; prueba de regresión del clasificador en CI | No aplica sin IA |
