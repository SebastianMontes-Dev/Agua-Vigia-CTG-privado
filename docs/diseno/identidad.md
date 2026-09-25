# Identidad visual del frontend — rumbo formal

**Fecha:** 2026-09-25, Cartagena · **Decisiones:** [ADR-070](../design-decisions.md) y ADR-071 · **Prototipo de referencia:**
[rumbo formal](https://claude.ai/artifact/KzhpRbnun3cVuEe6SSKQ6D) (Artifact privado del dueño).

**Estado:** adoptada por el dueño el 2026-09-25 con su plan «identidad propia y respuestas claras» (`ADR-071`): la
paleta de §2 rige; la identidad editorial queda **contenida** en la marca y los titulares (§3), y búsquedas,
formularios, mapa y datos se presentan de forma directa. El prototipo de referencia muestra la tipografía anterior
(serif en cifras y Schibsted Grotesk): donde difiera, gana este documento. Migrada el 2026-09-25 (§8).
Antes de tocar la interfaz, usa la skill `disenar-frontend`.

Este documento manda sobre **cómo se ve y cómo se mueve** el frontend. Lo que se muestra y con qué datos lo sigue
mandando la [guía del frontend](guia-frontend.md) y el contrato; lo que es un estado del servicio, `DESIGN.md` §2.

## 1. Principio

**Un instrumento público serio.** La persona entra preocupada y tiene que confiar en lo que lee: la interfaz se
parece más a un buen periódico o a un documento oficial bien compuesto que a una app de moda. Tres ideas la guían:

1. **Sobriedad.** Pocos colores, reglas finas en vez de tarjetas, mucho aire. El color fuerte se reserva a los cuatro
   estados del agua.
2. **Oficio.** Cada detalle está resuelto: cifras tabulares, alineaciones exactas, estados de foco, vacío y error
   diseñados, transiciones que acompañan en vez de adornar.
3. **Escritorio primero, celular completo.** La vista web se diseña como composición propia (panel + mapa, páginas de
   lectura a dos columnas), no como el celular estirado. El celular sigue siendo obligatorio y se verifica igual.

## 2. Paleta de interfaz

**Cardenillo y latón**: el verde del cobre oxidado de las tuberías y el metal de válvulas y medidores del acueducto.
Los neutros llevan un leve sesgo verde; nunca gris puro. Contraste medido con la fórmula de WCAG (§2.3 de la guía).

| Token | Claro | Oscuro | Uso | Contraste sobre papel (claro · oscuro) |
|---|---|---|---|---|
| `--papel` | `#f5f5f2` | `#0f1214` | Fondo de página | — |
| `--superficie` | `#fbfbf9` | `#15191c` | Panel contado, recuadro del índice | — |
| `--elevada` | `#ffffff` | `#1b2024` | Menús, sugerencias, información flotante | — |
| `--tinta` | `#15191c` | `#eceeea` | Texto principal | 16,19 · 16,10 |
| `--tinta-2` | `#4a5157` | `#a9b0b3` | Texto secundario, fechas, rótulos | 7,38 · 8,55 |
| `--tinta-3` | `#7b838a` | `#6d767b` | Bordes funcionales y gráficos. **Nunca texto** | 3,52 · 4,05 |
| `--linea` | `#dddcd6` | `#2a3034` | Separadores | decorativo |
| `--linea-suave` | `#e9e8e3` | `#20252a` | Separadores dentro de listas | decorativo |
| `--cardenillo` | `#2f5f57` | `#8cc2b4` | Acción principal, dato real en gráficos | 6,64 · 9,39 |
| `--sobre-cardenillo` | `#ffffff` | `#0f1214` | Texto sobre el botón principal | 7,25 · 9,39 |
| `--cardenillo-suave` | `#e3ece8` | `#1c2c29` | Fondo de opción resaltada | decorativo |
| `--laton` | `#836430` | `#c9a567` | Foco, selección en el mapa, subrayado activo | 5,02 · 8,11 |

Los cuatro estados del servicio **no cambian**: son los de `DESIGN.md` §2 (`ADR-042`). Sobre el papel nuevo miden
en claro 4,56 (con servicio), 5,79 (sin servicio), 4,70 (presión baja) y 5,93 (corte programado). Aun así, el texto
de un estado va en `--tinta` junto a su glifo de color; el color solo no es mensaje.

**Reglas de uso**
- Un solo color de acción: `--cardenillo`. El latón no es un segundo botón: marca foco, selección y el enlace activo.
- Nada de fondos de color detrás de secciones enteras, degradados ni sombras de colores.
- La sombra (`--sombra`) solo en lo que flota: menú, sugerencias, cajón de reporte, información del mapa.
- Oscuro diseñado, no invertido: el cardenillo y el latón se aclaran para mantener contraste y temperatura.

## 3. Tipografía

| Rol | Familia | Uso |
|---|---|---|
| Serif | **Newsreader** (Production Type, OFL), variable | **Solo** la marca, el titular de cada página y el nombre del barrio en la ficha |
| Sistema | La pila de `DESIGN.md` §4 | Todo lo demás: controles, buscador, formularios, texto, rótulos, horas y cifras |

La serif se sirve **desde el propio proyecto** (`frontend/public/fuentes/`, `woff2`, subconjunto latino,
`font-display: swap`). Nunca desde un CDN (`ADR-057`). Presupuesto: ≤ 90 KB. Si no ha llegado, el titular sale en la
serif del sistema y nada espera por ella: la primera respuesta no depende de la fuente (`ADR-071`).

| Nivel | Estilo | Ejemplo |
|---|---|---|
| Marca | Newsreader 500, 24–25 px | «AguaVigía» |
| Titular de página | Newsreader 300–400, `clamp(32px, 4.5vw, 56px)`, interlineado 1,05, tracking −2 %. Breve: una línea en escritorio | «Lo prometido y lo que duró» |
| Barrio | Newsreader 400, 40 px (32 px en celular), tracking −2 % | «Bocagrande» |
| Cifra | Sistema 600, 28–40 px, `tabular-nums` | «6:10», «99,6 %» |
| Sección | Sistema 600, 18–20 px | «Cortes cerrados en Manga» |
| Cuerpo | Sistema 400, 16–17 px, interlineado 1,5, ≤ 65 caracteres | |
| Acción | Sistema 600, 15–16 px | «Reportar lo que pasa en mi casa» |
| Rótulo | Sistema 600, 12 px, mayúsculas, tracking +8 % | «INICIO ANUNCIADO» |

Ninguna página repite por obligación un gran titular editorial: Bitácora, Estadísticas y Avisos abren con su función
(filtros, patrones, formulario). Nunca negrita en la serif.

## 4. Composición

**Navegación (`ADR-071`)** — escritorio: Mapa, Cumplimiento, Bitácora, Estadísticas y Avisos. Celular: Mapa,
Historial (Bitácora, Cumplimiento y Estadísticas) y Avisos. El panel del veedor tiene su propia navegación.
«Especificación» no está en la navegación ciudadana. «En vivo» solo aparece en el mapa y solo con el canal conectado.

**Escritorio (≥ 1100 px)** — cabecera de 68 px con marca, navegación centrada y tema.
- **Mapa:** panel contextual compacto de 400–460 px a la izquierda (buscador, respuesta, historial) y mapa a la derecha, sin tarjetas
  flotando encima salvo leyenda, zoom y crédito. Sin barrio elegido, el panel muestra Cartagena ahora: barra de la
  ciudad por estado, conteos y barrios con novedades.
- **Páginas de lectura:** ancho máximo 1180 px; encabezado a dos columnas (titular a la izquierda, entrada a la
  derecha); cifras en una fila de cuatro separadas por reglas; contenido a dos columnas 1,6 : 1.
- **Márgenes:** `max(20px, 3vw)` a los lados; 64 px entre bloques de página, 32 px dentro del panel.

**Tableta (600–1099 px)** — navegación en «Menú»; el mapa arriba (44 vh) y el panel debajo.

**Celular (< 600 px)** — búsqueda visible arriba, mapa y ficha inferior; márgenes de 16–20 px; el reporte sube como
hoja inferior. El mapa, el buscador y la lista accesible abren la misma ficha.

**Elementos**
- Separación por **reglas de 1 px**, no por cajas. Una regla de tinta abre los bloques de cifras.
- Radios: 999 px para botones y selectores de tema; 10–12 px solo en lo que flota o en el recuadro del índice;
  0 en tablas, listas y campos (el campo es una línea inferior).
- Botón principal: pastilla cardenillo de 50 px de alto con flecha. Secundario: la misma pastilla en hueco. Enlace:
  texto subrayado con regla fina que pasa a latón.
- Mapa: relleno de estado a baja opacidad (16 % claro, 22 % oscuro; más para sin servicio), borde de 0,8 px del
  color del estado; sin datos con trama; el barrio elegido lleva contorno de latón y los demás se atenúan.

## 5. Movimiento

El movimiento explica qué cambió; nunca retrasa la respuesta ni se repite sin motivo.

| Momento | Efecto | Duración | Curva |
|---|---|---|---|
| Carga del mapa | Los barrios aparecen ya rellenos con su estado; sin trazo previo, que retrasaría la respuesta | — | — |
| Elegir barrio | La cámara viaja al barrio; el contorno de latón se dibuja | 620 ms | `--lento` |
| Cambio de panel o página | El contenido entra escalonado 50 ms, 10 px hacia arriba | 560 ms | `--suave` |
| Cifras destacadas | Aparecen con el contenido; **no** cuentan desde cero (una cifra que corre retrasa la lectura) | — | — |
| Barras y reglas de tiempo | Crecen desde su origen | 0,9–1,1 s | `--suave` |
| Enlaces, opciones, navegación | Subrayado de latón que crece desde la izquierda; flecha que avanza 3–4 px | 320 ms | `--suave` |
| Reporte | El cajón entra desde la derecha (desde abajo en celular); la marca de recibido se dibuja | 480 ms | `--suave` |
| Estado «en vivo» | El punto respira | 2,8 s en bucle | `--lento` |
| Cambio de tema | Fondos y texto cambian de color | 240 ms | `--suave` |

`--suave: cubic-bezier(.2, .8, .2, 1)` · `--lento: cubic-bezier(.65, 0, .35, 1)`.

**Reglas:** solo `transform`, `opacity`, `stroke-dashoffset` y colores. Movimiento con CSS; la cámara del mapa la
anima MapLibre (`flyTo`/`easeTo`). Con `prefers-reduced-motion: reduce` todo aparece en su estado final, sin
excepción; el punto «en vivo» queda fijo. Ninguna animación bloquea la interacción ni el foco.

## 6. Prohibido

- **Fuentes:** una webfont en la interfaz fuera de la serif de §3, Inter, Roboto, Space Grotesk, Poppins, Montserrat.
  La fuente del sistema se usa para leer, no como identidad: la identidad la dan la marca, la paleta y las reglas.
- **Colores:** turquesa o azul genérico como acción, índigo, degradados, gris puro, crema con terracota, neón.
- **Formas de plantilla:** tarjetas con sombra y el mismo radio para todo, pastillas de colores para cada dato,
  barrita de color al costado de una tarjeta, iconos decorativos, emojis, ilustraciones.
- **Movimiento gratuito:** rebotes, parallax, desenfoques de fondo, bucles salvo el punto «en vivo», entradas que
  dejen contenido invisible si falla el script.
- **Datos inventados:** lo que el prototipo muestra de ejemplo no se convierte en campo de la API (guía §5).

## 7. Referencias

- Diarios con buena infografía (Financial Times, The Economist): serifa fina en titulares, cifras grandes, reglas.
- Sistemas de diseño de gobierno (GOV.UK, la Confederación Suiza): lenguaje directo, jerarquía clara, sin adornos.
- Instrumentos del acueducto: medidores de latón, placas de válvula y tubería de cobre como origen de la paleta.
- Pendiente: fotos del dueño de avisos y placas de calle de Cartagena, y pantallas de referencia de Mobbin o Refero.

## 8. Migración

En un solo cambio: `DESIGN.md` §3–§4 pasan a esta paleta y a esta tipografía, `tokens.css` y `tokens.test.ts` adoptan
los tokens de §2, `contraste.test.ts` fija las cifras de §2, Newsreader se versiona en `frontend/public/fuentes/` con
su licencia y el muestrario se rehace con esta identidad. Luego se rehacen los prototipos de F1
(`plan-frontend.md` §10) y F2 arranca sobre esta base después de revisarlos en un teléfono real.

## 9. Verificación de cada pantalla

Además de `DESIGN.md` §10: capturas en 1440 × 900, 1024 × 768, 768 × 1024 y 390 × 844, en claro y oscuro, y una con
movimiento reducido; ningún texto por debajo de AA; ningún elemento de §6; foco visible con latón. La skill
`revisar-diseno` hace esta revisión.
