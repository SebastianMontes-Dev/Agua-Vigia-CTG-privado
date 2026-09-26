# Evidencia de F2 — 2026-09-25

Núcleo ciudadano contra Spring, Mongo y Redis reales en Docker, con preview de producción en 4173. Datos de demostración; no son observaciones actuales del servicio. PR del núcleo queda abierto para revisión visual del dueño en teléfono.

## Verificación

Desde frontend: lint y typecheck sin errores; api:check coincide con OpenAPI; Vitest **150 aprobadas, 1 omitida** (prueba real optativa del canal, cubierta por la suite de integración). Build correcto, con aviso de tamaño del motor MapLibre diferido. Playwright: **16 del muestrario + 10 contra el backend real**. El job separado «Núcleo ciudadano contra backend real» levanta Docker y conserva trazas y capturas durante 14 días en el artefacto `f2-backend-real`.

Los escenarios reales verifican 211 barrios y recursos del mismo origen, reporte en dos acciones, cuarto reporte 429, tres huellas y consenso por SSE, confirmación sin actuar al abrir el enlace, 404, foto solo después del 201, límite de 10 MiB y ubicación fuera de Cartagena con elección manual. Los POST no se reintentan automáticamente.

## DESIGN.md §10, punto por punto

| Pregunta | Resultado y evidencia |
|---|---|
| ¿Se responde «¿tengo agua?» en menos de 5 segundos? | Lista textual y estado publicado primero; mapa inicial de 211 polígonos en 2560,2 ms bajo la conexión definida abajo. No cambia null ni un estado antiguo para dar una respuesta afirmativa. No se midió comprensión humana. |
| ¿Funciona en 360 px? | Capturas de las cuatro pantallas a 360 y 375 px; hoja inferior con scroll propio, búsqueda visible y navegación inferior. Sin desplazamiento horizontal. |
| ¿Los dos temas están diseñados? | Neutros, cardenillo y latón de tokens; mapa base neutro local distinto en cada tema. 56 capturas revisadas. |
| ¿El color va acompañado de forma o texto? | Cinco glifos y nombres en leyenda y lista; null con trama diagonal y «Sin datos verificados». Lista accesible sustituye la interpretación visual del mapa. |
| ¿Contraste AA verificado en ambos? | contraste.test.ts aprobado y axe sin violaciones en ficha, reporte y confirmación en ambos temas y siete anchos. El mapa conserva alternativa textual. |
| ¿Navegable solo con teclado y foco visible? | Apertura por Enter, foco contenido en diálogo, Tab y Escape, retorno al botón. Contorno latón de 2 px visible en capturas. Salto al contenido y etiquetas reales. |
| ¿Carga, vacío y error diseñados? | Consulta inicial y preparación progresiva; búsqueda sin resultados; cortes vacíos; GET fallido con reintento manual; offline explícito; errores por type RFC 7807; 429, 400, 404 y foto demasiado grande. |
| ¿Textos desde el lado del usuario? | «Busca tu barrio», «No hay agua», «Ya volvió el agua», agradecimiento sin prometer cambio del mapa, sin cuenta y ubicación opcional. |
| ¿Se ve la frescura del dato? | Generación del listado, registro del estado y verificación separados; «En vivo» solo junto al mapa con SSE conectado; 48 h conserva estado y avisa. Offline fecha del último listado. Hora de Cartagena. |

## Capturas completas

Cada enlace abre la captura original. Todas se tomaron con movimiento reducido. Orden: mapa, ficha de zona-industrial, reporte y confirmación. Las capturas de ficha muestran estado y población null reales.

| Ancho | Claro | Oscuro |
|---|---|---|
| 360 | [Mapa](capturas/mapa-360-light.png), [Ficha](capturas/ficha-360-light.png), [Reporte](capturas/reporte-360-light.png), [Confirmar](capturas/confirmar-360-light.png) | [Mapa](capturas/mapa-360-dark.png), [Ficha](capturas/ficha-360-dark.png), [Reporte](capturas/reporte-360-dark.png), [Confirmar](capturas/confirmar-360-dark.png) |
| 375 | [Mapa](capturas/mapa-375-light.png), [Ficha](capturas/ficha-375-light.png), [Reporte](capturas/reporte-375-light.png), [Confirmar](capturas/confirmar-375-light.png) | [Mapa](capturas/mapa-375-dark.png), [Ficha](capturas/ficha-375-dark.png), [Reporte](capturas/reporte-375-dark.png), [Confirmar](capturas/confirmar-375-dark.png) |
| 390 | [Mapa](capturas/mapa-390-light.png), [Ficha](capturas/ficha-390-light.png), [Reporte](capturas/reporte-390-light.png), [Confirmar](capturas/confirmar-390-light.png) | [Mapa](capturas/mapa-390-dark.png), [Ficha](capturas/ficha-390-dark.png), [Reporte](capturas/reporte-390-dark.png), [Confirmar](capturas/confirmar-390-dark.png) |
| 768 | [Mapa](capturas/mapa-768-light.png), [Ficha](capturas/ficha-768-light.png), [Reporte](capturas/reporte-768-light.png), [Confirmar](capturas/confirmar-768-light.png) | [Mapa](capturas/mapa-768-dark.png), [Ficha](capturas/ficha-768-dark.png), [Reporte](capturas/reporte-768-dark.png), [Confirmar](capturas/confirmar-768-dark.png) |
| 1024 | [Mapa](capturas/mapa-1024-light.png), [Ficha](capturas/ficha-1024-light.png), [Reporte](capturas/reporte-1024-light.png), [Confirmar](capturas/confirmar-1024-light.png) | [Mapa](capturas/mapa-1024-dark.png), [Ficha](capturas/ficha-1024-dark.png), [Reporte](capturas/reporte-1024-dark.png), [Confirmar](capturas/confirmar-1024-dark.png) |
| 1280 | [Mapa](capturas/mapa-1280-light.png), [Ficha](capturas/ficha-1280-light.png), [Reporte](capturas/reporte-1280-light.png), [Confirmar](capturas/confirmar-1280-light.png) | [Mapa](capturas/mapa-1280-dark.png), [Ficha](capturas/ficha-1280-dark.png), [Reporte](capturas/reporte-1280-dark.png), [Confirmar](capturas/confirmar-1280-dark.png) |
| 1440 | [Mapa](capturas/mapa-1440-light.png), [Ficha](capturas/ficha-1440-light.png), [Reporte](capturas/reporte-1440-light.png), [Confirmar](capturas/confirmar-1440-light.png) | [Mapa](capturas/mapa-1440-dark.png), [Ficha](capturas/ficha-1440-dark.png), [Reporte](capturas/reporte-1440-dark.png), [Confirmar](capturas/confirmar-1440-dark.png) |

## Rendimiento y visibilidad

[Medición conservada](medicion-3g.json): Chromium con CDP, bajada 1,6 Mbps, subida 0,75 Mbps, latencia configurada de 150 ms, caché HTTP desactivada, contexto sin IndexedDB previo. Marca de performance al frame posterior al dibujo SVG: **2560,2 ms** desde navegación. `rendimiento.spec.ts` exige los 211 paths y menos de 3000 ms; la traza incluye red y captura inicial. No equivale a interacción completa de MapLibre ni prueba en teléfono físico. Newsreader se solicita después del dibujo; tiles y glifos locales se cargan progresivamente.

`revision.spec.ts` oculta la pestaña mediante la API de visibilidad reproducida en headless: cero solicitudes iniciadas durante 16 s, reconexión al volver. Las reglas temporales del canal tienen pruebas deterministas con reloj y visibilidad inyectados. No se demuestra oclusión física del sistema operativo.

## Revisión propia: revisar-diseno

Se inspeccionaron las cuatro composiciones en los siete anchos y ambos temas, junto con CSS, tokens, movimiento y fidelidad del dato.

| Gravedad | Pantalla/tamaño | Elemento y hallazgo | Corrección |
|---|---|---|---|
| Corrige | Ficha/escritorio, mapa/768 | Encabezado principal ausente y menú desbordado | H1 del barrio y menú compacto; BUG-112, axe y scrollWidth. |
| Corrige | Mapa/ambos temas | Zoom duplicado y blanco en oscuro | Controles propios de 44 px con tokens; BUG-113. |
| Corrige | Mapa/3G | Motor bloqueaba estados cerca de 5 s | SVG inicial, motor y fuente diferidos; BUG-114, ADR-075. |
| Corrige | Confirmación/todos | Salto al contenido sin destino | Main enfocable y salto dentro de header; BUG-116. |
| Corrige | Reporte/offline | Primer módulo no podía descargarse | Fallo explícito antes de abrir; BUG-117. |
| Pendiente del dueño | Teléfono físico | Composición y tacto reales | Aprobar o indicar cambios en el PR antes de fusionar. |

No se observaron otros bloqueos visuales en las capturas. Newsreader solo marca/titular/barrio; CSS nuevo sin hexadecimales, sin sombras en superficies no flotantes y con movimiento reducido. La lista y los registros preservan null, 24 h y fechas separadas.

## Límites pendientes

- **Fuentes degradadas sin probar:** salud del colector vive en memoria (`EstadoColectorRegistry`), no en Mongo, y su endpoint es del panel autenticado del veedor. F2 no dispone de ese contrato público. Cambiar fechas o estado del barrio no demuestra degradación de fuentes; no se fingió. Resolver en F5 o definir exposición pública antes de esa prueba.
- Prototipo Artifact no pudo abrirse; implementación basada en DESIGN, identidad, guía y plan del repositorio. Revisión visual y aprobación en teléfono siguen pendientes.
- Destinos de historia pública y avisos pertenecen a F3/F4; muestran sección pendiente. Enlace de eventos lleva el sector pero su página todavía no está construida.
- La medición de 3G es simulada, con margen limitado; no prueba 50 000 usuarios ni rendimiento de un celular físico.
