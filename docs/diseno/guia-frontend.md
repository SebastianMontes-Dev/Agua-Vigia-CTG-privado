# Guía de diseño del frontend — AguaVigía CTG

**Fecha:** 2026-09-25, Cartagena · **Decisión:** [ADR-069](../design-decisions.md).

> **Parte visual reemplazada (`ADR-070`, 2026-09-25):** color, tipografía, componentes y composición (§2–§4) los define
> ahora la [identidad formal](identidad.md). De esta guía siguen vigentes los datos, las rutas, los estados y los
> mensajes (§5–§8). Antes de tocar la interfaz, skill `disenar-frontend`.
>
> **Plan del dueño (2026-09-25, `ADR-071`–`ADR-073`):** navegación y tipografía en `identidad.md` §3–§4; la bitácora
> ya se filtra por barrio, tipo y fecha contra la API, y cada sector trae `verificadoEn` para la advertencia de
> «Sin verificación reciente» a las 24 horas ([plan §5.2](../ingenieria/plan-frontend.md)). Donde esta guía diga lo
> contrario, ganan esos documentos.

**Aprobado:** el dueño encargó implementar este plan documental para todo el frontend. **Pendiente de aplicación:** las composiciones descritas aquí. **Aplicado:** el acento claro de §2.1 (paso 1 de §7) y la adaptación de los prototipos (paso 2, a la espera de la revisión visual del dueño), ambos el 2026-09-25. **Implementado antes de esta guía:** F0 y los avances de F1 registrados en el [plan de construcción](../ingenieria/plan-frontend.md). Esta entrega no aprueba el prototipo existente, no cierra F1 y no inicia F2.

## Índice

1. [Base y diagnóstico](#1-base-y-diagnostico)
2. [Color y contraste](#2-color-y-contraste)
3. [Tipografía, medidas y componentes](#3-tipografia-medidas-y-componentes)
4. [Composición adaptable](#4-composicion-adaptable)
5. [Matriz de pantallas y datos](#5-matriz-de-pantallas-y-datos)
6. [Estados, tiempo y mensajes](#6-estados-tiempo-y-mensajes)
7. [Aplicación por Claude Code](#7-aplicacion-por-claude-code)
8. [Aceptación y verificación](#8-aceptacion-y-verificacion)

<a id="1-base-y-diagnostico"></a>

## 1. Base y diagnóstico

| Fuente revisada | Qué determina |
|---|---|
| `main` remoto `9862a1c`; F1 `d366ad9` | F0 fusionado; Sprint 7 y ADR-068 disponibles en la rama de F1, todavía no fusionada a `main` al revisar |
| Copia local inicial `a4da6e5` | Estaba 26 commits detrás de `main`; sus menciones a un frontend inexistente estaban desactualizadas |
| Captura del dueño y [prototipo privado F1](https://claude.ai/artifact/G88U8LYbsqwWsZN47B9JAA) | La captura es el muestrario F0. Se inspeccionaron mapa, reporte, cumplimiento y bitácora del prototipo; sus datos y controles de simulación no son funcionalidad de producción |
| [DESIGN.md](../../DESIGN.md), [brief](../brief.md), [requisitos](../product-requirements.md) | Identidad, destinatarios, lenguaje y mínimos del producto |
| [Contrato](../../backend/openapi.yaml) y [guías de API](../api/README.md) | Datos y acciones realmente disponibles. Esta revisión fue documental; no constituye una prueba del backend en ejecución |

**Dirección:** interfaz ciudadana sobria, con el turquesa como señal de acción y el estado del agua como información dominante. El mapa permite ubicar; la ficha responde. La persona debe identificar barrio, estado y horario disponible sin desplazarse una vez elegido el sector.

| Observación | Resolución de diseño |
|---|---|
| El muestrario muestra colores pero no la jerarquía del producto | Diseñar alrededor de una ficha de respuesta y sus acciones, no copiar la página de muestras como portada |
| Los polígonos del prototipo dominan frente al texto | Reducir su relleno y conservar bordes, glifos y nombres legibles |
| Barrio, estado y horas compiten en la misma frase | Separar barrio, estado, horario y antigüedad en cuatro niveles |
| El acento claro falla como texto sobre algunos fondos | REC-019 aplicada en DESIGN.md y tokens a la vez (§2) |
| «Ver los reportes» y «Cortes que más se pasaron» sugieren datos no expuestos públicamente de esa forma | Mostrar sustento como referencias y comparación por sector; no prometer contenido o clasificación global sin soporte (§5) |
| Fecha antigua de estado confundible con fallo del canal | Separar registro del estado, generación del listado, consulta y conexión (§6) |

No recuperar la carta náutica archivada en ADR-041, el shell administrativo de ADR-029 ni decoraciones descartadas por ADR-067. Mantener los SVG de estado de F1 y los glifos locales del mapa de ADR-068.

<a id="2-color-y-contraste"></a>

## 2. Color y contraste

### 2.1 Fuente única

Los **13 pares de valores vigentes**, incluidos todos los estados, los neutros y el acento claro de `REC-019`, viven exclusivamente en la [tabla canónica de DESIGN.md §2–§3](../../DESIGN.md#3-paleta-base); `frontend/src/estilos/tokens.css` la reproduce y `tokens.test.ts` falla si divergen. No copiar valores hexadecimales a esta guía, a componentes ni a estilos del mapa.

### 2.2 Roles y combinaciones

| Uso | Claro y oscuro | Restricción |
|---|---|---|
| Página / panel / énfasis suave | `--fondo` / `--superficie` / `--acento-suave` | Superficies opacas; el énfasis suave no identifica un estado del agua |
| Texto de lectura / ayuda y fecha | `--tinta` / `--tinta-secundaria` | Nunca bajar la opacidad del texto para mostrar antigüedad |
| Enlace | `--acento`, subrayado; hover con subrayado más grueso | Sobre `--fondo`, `--superficie` o `--acento-suave`; sobre otro fondo, medirlo antes |
| Botón principal | Relleno `--acento`; texto `--superficie` en claro y `--fondo` en oscuro | Hover conserva los colores y añade contorno interior del color del texto; no usar `--acento-vivo` como relleno de hover con texto claro |
| Botón secundario / campo | Superficie, texto tinta, borde tinta terciaria | El borde funcional no depende de `--linea` |
| Foco | Contorno de 2 px en acento con separación de 2 px | Los controles sobre el mapa llevan placa opaca; comprobar el anillo contra la placa |
| Separador decorativo | `--linea` | No es suficiente para identificar un control o su selección |
| Estado del agua | Token semántico + glifo propio + texto | Reservado al servicio; sin rojo genérico de formulario ni verde genérico de éxito |
| Sin datos | Trama diagonal, tinta secundaria y etiqueta | Estado nulo no crea un quinto enum de API y nunca hereda verde |
| Error / confirmación de interfaz | Texto tinta, icono de exclamación / marca y mensaje explícito | Error con borde de 2 px y asociación al campo; éxito anunciado sin cambiar el mapa |
| Deshabilitado | Superficie, tinta secundaria y etiqueta o motivo | Sin depender de una opacidad baja; no confundir con una acción disponible |

`--tinta-terciaria` se reserva a bordes e información gráfica; ninguna fecha, instrucción o ayuda pequeña usa ese token. `--acento-vivo` no se usa para texto ni para fondos de botones con texto claro. Sobre `--acento-suave`, los estados se expresan con texto tinta y un glifo alojado en superficie: no con texto semántico coloreado.

### 2.3 Evidencia de contraste

Medición sRGB con luminancia relativa de WCAG: `(L mayor + 0,05) / (L menor + 0,05)`. Comparar valores sin redondear contra 4,5:1 (texto normal) y 3:1 (gráficos y bordes funcionales). Las cifras se redondean solo al presentarlas. Fuentes: [contraste de texto](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html), [contraste no textual](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html) y [uso del color](https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html).

| Par | Claro anterior a REC-019 | Claro vigente | Oscuro |
|---|---:|---:|---:|
| Acento / fondo | 4,39 | 5,09 | 8,60 |
| Acento / superficie | 4,65 | 5,39 | 7,57 |
| Acento / acento suave | 3,98 | 4,62 | 5,64 |

El resto de las parejas permitidas se verifican en [contraste.test.ts](../../frontend/src/estilos/contraste.test.ts): tinta y secundaria sobre los tres fondos; estados sobre fondo y superficie; texto sobre rellenos sólidos; bordes y glifos sobre fondo y superficie. Acento sobre los tres fondos forma parte de los pares de texto, y la misma prueba fija las cifras vigentes y oscuras de esta tabla; el muestrario las mide en pantalla. El test no cubre la composición translúcida del mapa.

### 2.4 Mapa en los dos temas

| Capa | Claro | Oscuro |
|---|---|---|
| Tierra / agua | Fondo / línea | Superficie / fondo |
| Edificios / vías | Línea / superficie | Línea / tinta terciaria con menor grosor que los límites de sectores |
| Polígonos de estado | Token de estado, opacidad 0,20 | Token de estado, opacidad 0,28 |
| Borde de estado | Token sólido, 1,5 px | Token sólido, 1,5 px |
| Sector seleccionado | Borde semántico 3 px con halo de superficie 5 px | Igual; no cambiar el sector a turquesa por seleccionarlo |
| Etiquetas | Tinta sobre halo opaco de superficie | Igual; nunca texto directamente sobre relleno transparente |
| Sin datos | Superficie con trama de tinta secundaria: trazo 1 px cada 8 px (baldosa de 8 × 8 px de `trama-sin-datos.svg`), inclinación 45° | Igual |

Las opacidades solo suavizan áreas; no comunican el estado por sí mismas. Cada borde semántico lleva una base de superficie de 3,5 px para conservar contraste sobre calles o polígonos vecinos. Leyenda con glifo sólido y texto; muestra de relleno con la misma opacidad y fondo de tierra del mapa. No exigir 3:1 a un relleno decorativo: verificar el borde/glifo que identifica la zona y la alternativa textual.

La cobertura contiene todos los sectores devueltos por la API, también islas y `MultiPolygon`. Si el extracto PMTiles no cubre una isla, mantener geometría, nombre y ficha sobre fondo neutro, con «Mapa base no disponible en esta zona». No excluir el sector ni solicitar tiles externos. Zoom y atribución quedan fuera del área tapada por la hoja.

<a id="3-tipografia-medidas-y-componentes"></a>

## 3. Tipografía, medidas y componentes

Las familias siguen en [DESIGN.md §4](../../DESIGN.md#4-tipografía). Noto Sans queda limitada a las etiquetas del lienzo del mapa, según ADR-068.

| Rol | Medida a raíz de 16 px | Peso / interlineado | Uso |
|---|---|---|---|
| Título de página | 1,875 rem (30 px); 2,5 rem (40 px) desde 1024 px | 700 / 1,15 | Barrio en ficha y título de página |
| Sección / estado principal | 1,375 rem (22 px) | 600 / 1,3 | Estado del barrio, encabezado de sección |
| Cuerpo y campos | 1,0625 rem (17 px) | 400 / 1,5 | Lectura y entradas |
| Secundario | 0,9375 rem (15 px) | 400 / 1,4 | Ayuda, fecha, fuente |
| Utilidad | 0,8125 rem (13 px) | 400 / 1,4 | Identificadores y ejes auxiliares, siempre acompañados de explicación legible |

Horas y columnas numéricas usan `tabular-nums`; monoespaciada solo en horas, códigos y datos alineados. Texto corrido limitado a 65 caracteres de ancho, sin títulos enteros en mayúsculas. Un nombre largo envuelve; no se trunca la respuesta esencial.

| Componente | Especificación e interacción |
|---|---|
| Espaciado | Escala 4, 8, 12, 16, 24, 32 y 48 px. Margen de página 16 px en móvil, 24 px en tableta y 32 px en escritorio |
| Contenedores | Radio 6 px, borde 1 px. Hoja inferior con esquinas superiores de 12 px. Sin tarjetas anidadas para cada cifra |
| Elevación | Solo hoja, panel de mapa, menú y diálogo: sombra 0 8px 24px con tinta al 12% en claro y 24% en oscuro. No blur de fondo |
| Botones | Alto mínimo 44 px, relleno lateral 16 px, texto 600. Acción primaria a todo el ancho en móvil. Durante envío, «Enviando…», `aria-busy` y sin segundo envío |
| Campos | Etiqueta encima, ayuda debajo; error con texto asociado, `aria-invalid` y foco al primer campo inválido al enviar. No validar agresivamente al teclear |
| Buscar barrio | Combobox etiquetado, flechas para sugerencias, Enter para elegir, Escape para cerrar; conserva término al fallar y ofrece lista. Normalización de búsqueda no altera nombres oficiales |
| Glifo de servicio | SVG propio de F1, 20 px en filas, 24 px en ficha; decorativo para lector de pantalla cuando ya existe etiqueta |
| Lista de sectores | Nombre, estado con glifo y fecha. Fila seleccionada con fondo suave, texto tinta y marca explícita; zona táctil completa de al menos 44 px |
| Hoja de mapa | Tres posiciones: resumen ajustado al contenido esencial, media de 60dvh y completa hasta 100dvh menos cabecera. Botones «Ampliar»/«Reducir» equivalentes al arrastre |
| Diálogo | Máximo 480 px; hoja casi completa en móvil. Título, contenido y acciones. Foco contenido, Escape/cancelar, retorno al activador; fondo no interactivo |
| Tabla / fila móvil | En escritorio, cabeceras y cifras alineadas. En móvil, cada registro conserva etiquetas y acción en flujo vertical; matrices que deban seguir siendo tablas tienen scroll propio etiquetado |
| Paginación | Anterior/siguiente, página actual anunciada. Totales solo de cabeceras. No indicar que una página parcial contiene todos los resultados |
| Movimiento | Transiciones de 120 ms para controles y 180 ms para hojas; solo con `prefers-reduced-motion: no-preference`. Sin paneos automáticos al refrescar datos |

<a id="4-composicion-adaptable"></a>

## 4. Composición adaptable

La navegación pública ofrece **Mapa, Cumplimiento, Bitácora, Estadísticas y Avisos**. En menos de 1024 px: marca, enlace «Mapa» y botón «Menú» con los demás destinos y selector Sistema/Claro/Oscuro. El acceso al panel queda al final del menú. En escritorio, enlaces horizontales y tema. Cabecera mínima de 56 px, ampliable por texto; no una sidebar fija sobre el mapa.

### 4.1 Mapa y ficha

Esquemas de disposición, no datos reales. La barra del sitio que aloja los Artifacts y sus controles de simulación no forman parte de la app.

```text
360 px                         768 px
┌──────────────────────┐       ┌────────────────────────────────┐
│ AguaVigía  Mapa  Menú │       │ AguaVigía          Mapa  Menú  │
│ [Busca tu barrio   ] │       │ [Busca tu barrio             ] │
│                      │       │                                │
│ MAPA        [+] [−]  │       │ MAPA                 [+] [−]   │
│ leyenda / atribución │       │ leyenda / atribución           │
├──────────────────────┤       ├────────────────────────────────┤
│ Barrio               │       │ Barrio                         │
│ ◇ Estado del agua    │       │ ◇ Estado del agua              │
│ Horario / sin horario│       │ Horario / sin horario          │
│ Estado registrado…   │       │ Estado registrado…             │
│ [Reportar]           │       │ [Reportar]  Historial  Avisos   │
│ Historial  Avisos    │       │ Ampliar · Lista de sectores     │
└──────────────────────┘       └────────────────────────────────┘

1280 px
┌──────────────────────────────────────────────────────────────┐
│ AguaVigía   Mapa  Cumplimiento  Bitácora  Estadísticas  Avisos │
├───────────────────┬──────────────────────────────────────────┤
│ Panel 384 px      │ Buscador y mapa                           │
│ Barrio            │                                          │
│ ◇ Estado          │ Sectores, calles y nombres                │
│ Horario           │                                          │
│ Antigüedad        │                                          │
│ [Reportar]        │                               [+] [−]    │
│ Historial / Avisos│ Leyenda                    Atribución     │
└───────────────────┴──────────────────────────────────────────┘
```

- **360 y 768 px:** hoja inferior de ancho completo; buscador en el espacio de mapa. Al seleccionar sector, mostrar el resumen sin scroll interno y ajustar la cámara al área no tapada. Respetar `safe-area-inset-bottom`.
- **Desde 1024 px:** panel de 384 px a la izquierda bajo cabecera, separado del mapa; contenido desplazable dentro del panel. El mapa no queda oculto debajo.
- **Sin barrio:** resumen «Elige tu barrio para consultar el agua», con «Buscar barrio» y «Usar mi ubicación». No abrir el permiso de ubicación automáticamente. Si el permiso falla, volver a búsqueda sin bloquear nada.
- **Barrio elegido:** nombre → estado → horario disponible → fecha de registro → reportar. Historial y avisos son secundarios. La lista de sectores abre una vista equivalente accesible sin depender de WebGL.
- **Poca altura o zoom 200%:** permitir desplazamiento vertical y expansión de la hoja; no recortar contenido para sostener una altura fija. El criterio sin scroll se verifica a tamaño normal, no a costa de accesibilidad al ampliar.

### 4.2 Páginas de lectura, formularios y panel

| Familia | 360 px | 768 px | 1280 px |
|---|---|---|---|
| Cumplimiento / estadísticas | Una columna; contexto → comparación → cifras → serie/lista | Una columna con figuras más anchas | Contenido de máximo 960 px; evitar más de dos figuras en paralelo |
| Bitácora | Fecha encima de cada evento; fuente debajo del texto | Columna de fecha de 104 px, evento en el resto | Ancho máximo 960 px; mismo orden y lectura cronológica |
| Avisos / cuentas / ingreso | Formulario de una columna con margen 16 px | Formulario centrado de 480 px | Mismo ancho de 480 px; sin ilustración que compita con la tarea |
| Panel | Selector de sección y filas apiladas; detalle en pantalla completa | Filas apiladas y detalle debajo de selección | Área máxima 1200 px; cola de 384 px + detalle flexible cuando proceda; tablas para cuentas/auditoría |

El panel tiene navegación propia con secciones permitidas y enlace «Volver al mapa». Nunca hereda los colores de servicio para estados de cuenta o decisiones de moderación. Atajos `j`/`k`, `a`/`d` solo actúan con la cola enfocada, nunca dentro de campos; aprobar/descartar abre confirmación, no ejecuta directamente. Siempre hay botones equivalentes.

<a id="5-matriz-de-pantallas-y-datos"></a>

## 5. Matriz de pantallas y datos

Las rutas de interfaz siguen el [plan §7](../ingenieria/plan-frontend.md#7-pantallas-y-rutas-del-frontend). Los enlaces de esta matriz apuntan a contratos de comportamiento, no los duplican. Todos los registros de ejemplo se rotulan «Datos de ejemplo» solo en prototipos.

### 5.1 Consulta y participación ciudadana

| Ruta / vista | Jerarquía, texto y datos permitidos | Estados y aceptación específica |
|---|---|---|
| `/` mapa | §4.1; [sectores, geometría y SSE](../api/sectores-y-tiempo-real.md). Buscador y lista comparten selección | Primera visita, carga separada de mapa y estado, error parcial, sin WebGL, sin red. La ficha no espera tiles; ninguna selección inicial inventada |
| `/sectores/:id` ficha | Nombre, estado y registro del sector; histórico de cortes y cumplimiento bajo demanda. «Hora de restablecimiento no informada» cuando falta | `null`, sector inexistente, historial vacío, varios cortes, futuro y vencido. `actualizadoEn` no se convierte en inicio del corte |
| Reporte, hoja sobre mapa | «¿Qué pasa con el agua en tu casa?» y «Reportas en [barrio]». Botones «No tengo agua», «Llega poca agua», «Ya volvió el agua»; [reportes](../api/reportes.md) | Con sector conocido: abrir y elegir tipo = dos toques; el segundo envía. Cambiar barrio antes de enviar es una ruta alternativa, sin promesa de dos toques. Sin login ni campos extra |
| Reporte recibido / foto | «Reporte recibido. Tu reporte cuenta junto con los de tus vecinos». «Añadir foto» después del 201 | Foto fallida no deshace el reporte; mantiene su id. No cambia el estado del mapa de forma optimista. Envío incierto no se presenta como recibido |
| `/confirmar/:id` | «Confirmar reporte» y botón «Confirmar este reporte»; id recibido en el enlace, [contrato de confirmación](../api/reportes.md) | No ejecutar al abrir. Sin GET público de contenido, no inventar barrio, tipo ni foto. `404`: «Este reporte no está disponible». No crear un listado público de reportes |
| `/avisos` | «Recibe avisos de tus barrios», correo + selector buscable de sectores + «Enviar enlace de confirmación»; [suscripciones](../api/suscripciones.md) | Selección por teclado, campos inválidos, envío, 202 neutro, límite, error. No confundir enviar correo con estar suscrito |
| `/avisos/confirmar` | «Confirma tus avisos» + botón «Confirmar avisos»; [enlaces](../api/correos-y-enlaces.md) | Token leído y retirado de URL; POST solo al pulsar; éxito, inválido, vencido y error. Sin petición automática con efecto |
| `/avisos/baja` | «Dejar de recibir avisos» + botón del mismo nombre; [enlaces](../api/correos-y-enlaces.md) | Mismas protecciones; éxito «Ya no recibirás estos avisos». Abrir el enlace no cancela por sí solo |

**Horarios en ficha:** mostrar cada corte abierto del historial con su propio inicio, fin prometido y origen. No resumir varios cortes en una única hora de vuelta. Si solo hay un corte pertinente y con horario, «Inicio anunciado… / Fin prometido…» mantiene explícita la atribución; con estado proveniente de consenso no inferir que ese corte lo causó. Una promesa vencida sigue diciendo «Fin prometido… (hora ya pasada)»; no declarar restablecimiento sin el dato correspondiente. El estado mostrado siempre es el devuelto por sectores.

Telegram solo aparece como enlace si existe un nombre de bot configurado y utilizable, conforme a la [guía de Telegram](../ingenieria/telegram.md). No mostrar WhatsApp ni botones activos para un canal sin configurar.

### 5.2 Historia pública

| Ruta | Jerarquía, texto y datos permitidos | Estados y aceptación específica |
|---|---|---|
| `/cumplimiento` | «Lo prometido y lo que duró». Global o sector seleccionado: duración prometida → real → diferencia → índice. Serie mensual debajo; [cumplimiento](../api/bitacora-estadisticas-cumplimiento.md) | Solo cortes cerrados; 400 de ausencia de cortes = vacío explicado, no 0% ni 100%. Corte abierto = «Todavía no se puede medir». Error real conserva acción reintentar |
| Comparación en ficha | Histórico del sector y comparación de cada corte cerrado disponible | Eje desde cero, prometido con contorno y real con relleno; etiquetas en tinta. Exceso con trama, diferencia negativa «Terminó X antes». No usar rojo/verde para juzgar cumplimiento |
| Serie mensual | Mes, índice y `cantidadCortes` («sobre N cortes»), prometido/real, descarga CSV | Meses en orden; huecos no se rellenan con cero. Los filtros de fechas afectan solo serie y CSV con los mismos parámetros; no aparentar que filtran el agregado global |
| `/estadisticas` | Sectores más afectados, cortes de lunes a domingo y duración media; [estadísticas](../api/bitacora-estadisticas-cumplimiento.md) | Barras horizontales en el color de acción con texto y tabla alternativa. Ausencias no inventadas; exportación existente. Sin medidores ni tarjetas decorativas de KPI |
| `/bitacora` | Filtros de barrio, tipo y fecha arriba; después fecha y hora → procedencia → descripción → estado si existe → fuente / sustento; [bitácora](../api/bitacora-estadisticas-cumplimiento.md) | Filtros contra la API sobre todo el historial (`ADR-073`), fechas elegidas en hora de Cartagena; sin coincidencias, «No hay eventos con esos filtros». Nulos tolerados, imagen fallida no rompe el evento, paginación. «Informativo» cuando el evento no declara estado, aunque el tipo mencione un corte |
| Sustento de evento | «Sustentado por N reportes» y «Ver referencias de sustento», desplegable paginado de identificadores | La API solo devuelve ids. Explicar «Referencias de trazabilidad; el contenido de los reportes no es público». No abrir fotos, comentarios o detalles inexistentes |

**Límites del prototipo:** el agregado de cumplimiento no trae periodo ni cantidad de cortes: no trasladar los «121 cortes» y fechas de la demo a esa cabecera. Mostrar el conteo en la serie mensual, que sí lo devuelve. No construir el ranking global «Cortes que más se pasaron» mediante consultas a los 211 sectores; la comparación por corte vive en la ficha del sector. La bitácora filtra por barrio, tipo y fecha en todo el historial (`ADR-073`), no por origen: la procedencia se muestra como etiqueta, sin un filtro de origen que la API no ofrece.

«Boletín de Acuacar» se usa solo con procedencia sustentada; no llamar oficial a todo evento ni inventar una cita textual pública: `EventoBitacoraRespuesta` expone descripción y URL, no `citaTextual`. Mostrar la fuente si existe; sin ella, omitir enlace y explicar el origen disponible. El índice informa sobre cortes cerrados registrados, no sobre todas las interrupciones de Cartagena.

### 5.3 Cuentas y segundo factor

Todas usan el formulario de §4.2 y los mensajes de [cuentas y sesión](../api/cuentas-y-sesion.md). Son cuentas del panel; nunca requisito para consultar o reportar.

| Ruta | Contenido y acción | Estados y aceptación específica |
|---|---|---|
| `/cuenta/solicitar` | Nombre, correo, clave; «Solicitar acceso» | 202 neutro, validación, límite, fallo. Explicar verificación y aprobación posterior, sin prometer acceso inmediato |
| `/cuenta/verificar` | «Verifica tu correo», botón explícito y opción reenviar | 204 → «Correo verificado; tu acceso está pendiente de aprobación». Enlace usado/vencido y reenvío con respuesta neutra |
| `/cuenta/invitacion` | Clave y «Aceptar invitación» | Sin inventar nombre/correo/rol que el token opaco no expone; 204 ofrece ingreso. Invitación vencida no se presenta como recuperable por el invitado |
| `/cuenta/olvide` | Correo; «Enviar enlace» | Mismo mensaje tras 202 para correos existentes e inexistentes; no enumerar cuentas |
| `/cuenta/restablecer` | Clave nueva; «Guardar clave» | 204 vuelve a ingreso y explica cierre de sesiones; token inválido/vencido ofrece pedir otro enlace |
| `/panel/ingreso` | Correo, clave; «Ingresar»; recuperación y solicitud de acceso | Credencial inválida sin distinguir el campo; 401 de segundo factor abre código, 403 explica estado de cuenta, 423 indica espera real y 429 respeta Retry-After |
| `/panel/segundo-factor` | Alta: QR local + secreto textual + código de seis dígitos + «Activar segundo factor» | Sesión limitada no entra al panel. Secreto solo desde respuesta de alta; confirmación devuelve sesión completa. No activar antes de verificar código |

Los formularios de clave permiten pegar y usar gestor de contraseñas, con mostrar/ocultar y sin medidor inventado. Aplican la política completa enlazada, incluido límite de bytes; el servidor conserva la decisión final. Los tokens de enlaces se retiran de la URL tras capturarlos y no se envían por navegación o scripts de terceros.

### 5.4 Panel del veedor

| Ruta | Contenido y datos | Estados y aceptación específica |
|---|---|---|
| `/panel` | Cola de reportes pendientes, más antiguos primero; sector, tipo, hora y detalle disponible; [moderación](../api/panel-veedor.md) | Vacío «No hay reportes pendientes». Aprobar/descartar con confirmación y acción bloqueada durante envío. No inventar campos ausentes en la respuesta |
| `/panel/cortes` | Selector de sector obligatorio para consultar; listado y formulario de alta (sectores, inicio, fin prometido, causa); cierre con hora real | Validación de fechas, sector inexistente, corte ya cerrado. No ofrecer cerrar cortes de ingesta cuando el contrato no lo admite |
| `/panel/ingesta` | Cola de propuestas; sector, estado propuesto, cita textual y fuente junto a aprobar/descartar. Sección de salud con datos de colectores | Resueltas sin botones activos. Aprobación no implica cambio de estado: refrescar datos. Fuente o cita ausente se muestra explícita, sin inventar evidencia |
| Ingesta: fallidos | Dentro de la misma pantalla, sección de documentos fallidos y reintento existente; [panel](../api/panel-veedor.md) y [contrato](../../backend/openapi.yaml) | Diferenciar lista vacía, fallo del colector y rechazo del reintento; mostrar únicamente metadatos expuestos y permiso requerido por la ruta |
| `/panel/cuentas` | Tabla por estado, búsqueda solo si el contrato la permite, detalle de permisos, invitación y acciones de ciclo de cuenta | Acciones por permisos efectivos. No editar el propio acceso ni ofrecer quitar el último ADMIN. Permisos protegidos y confirmación con nombre de la cuenta afectada |
| `/panel/auditoria` | Registro de acciones con fecha, actor y objetivo según respuesta; solo lectura | Paginación, vacío, error y acceso denegado. Sin editar ni borrar eventos |
| `/panel/seguridad` | Cambio de clave, TOTP y «Cerrar todas mis sesiones»; [seguridad de cuenta](../api/cuentas-y-sesion.md) | Avisar cierre de sesiones antes de cambiar clave/desactivar TOTP. ADMIN no puede desactivarlo. Cambio de teléfono exige código actual |

Cada operación respeta [rutas y permisos del panel](../api/panel-veedor.md), con validación final del servidor. La ausencia de permiso oculta la acción; un 403 inesperado muestra explicación y refresca permisos sin borrar toda la sesión. Formularios usan `datetime-local` con etiqueta visible «Hora de Cartagena» y conversión explícita; no toman la zona del navegador como zona del servicio.

<a id="6-estados-tiempo-y-mensajes"></a>

## 6. Estados, tiempo y mensajes

### 6.1 Los cuatro relojes no son intercambiables

| Dato | Presentación | Lo que no demuestra |
|---|---|---|
| `actualizadoEn` del sector | «Estado registrado hace X»; fecha exacta accesible | No es comienzo físico de la interrupción ni último latido del colector |
| `generadoEn` del listado | «Listado generado a las…»; visible en detalle y lectura guardada | No actualiza por sí solo el estado de cada barrio |
| Último GET exitoso del cliente | «Última consulta a las…» | No verifica nuevamente la situación del agua |
| Conectividad observada | «Actualizando», «No pudimos actualizar» o «Sin conexión» cuando se detecta | Un SSE reconectándose normalmente no demuestra caída; tampoco la edad de un estado |

Fechas en `America/Bogota`, español de Colombia, formato de 12 horas. En otra fecha incluir el día. Un valor nulo dice «Sin fecha de registro», nunca «hace 0 min». No crear un umbral arbitrario que cambie el color del servicio tras unas horas: mostrar la edad exacta. La salud de colectores solo aparece en el panel con su endpoint y reglas documentadas.

### 6.2 Respuestas comunes

| Situación | Mensaje / comportamiento |
|---|---|
| Carga inicial | Esqueleto con forma de ficha/lista, etiqueta «Consultando el estado del agua…»; no un mapa verde provisional |
| Actualización con datos | Conservar lectura, indicar que se consulta y no mover el foco ni la cámara |
| Estado nulo | «Sin datos verificados» + «Puedes reportar lo que pasa en tu casa». No equivale a que no exista ningún reporte |
| Lista vacía | Mensaje específico del recurso: «No encontramos barrios con ese nombre» / «No hay cortes registrados para este barrio» |
| Fallo de lectura con caché | «No pudimos actualizar. Mostramos el último listado disponible», con hora de generación y botón «Reintentar» |
| Fallo de lectura sin caché | «No pudimos consultar el estado. Revisa tu conexión e inténtalo otra vez»; no cifras por defecto |
| Sin red al reportar | «No pudimos enviar tu reporte. Cuando recuperes la conexión, inténtalo otra vez». Sin cola automática |
| Resultado de envío desconocido | «No pudimos confirmar si se recibió tu reporte». No éxito ficticio ni reintento automático de POST |
| Límite de reportes | Explicar el cupo del sector sin inventar hora exacta de desbloqueo si no la devuelve el servidor |
| Límite por IP | «Espera antes de volver a intentarlo»; cuenta regresiva solo desde Retry-After, sin anuncios cada segundo al lector |
| Sesión vencida o revocada | «Tu sesión terminó. Ingresa de nuevo»; limpiar token y salir del panel. No conservar secretos, contraseñas ni formularios sensibles |
| Recurso inexistente | «No encontramos este barrio/recurso» y enlace al destino superior; no tratar todo 404 como vacío válido |
| Error de formulario | Mensaje junto al campo y resumen al enviar; `type` determina la reacción, no una búsqueda de palabras en el texto |

Éxitos se anuncian con `role=status`; errores que requieren atención, con `role=alert`. No anunciar toda la lista en cada refresco ni usar toast efímero como única confirmación. Los valores válidos de formularios no sensibles se conservan al fallar; fotos y claves no se registran en logs ni se persisten para recuperación.

<a id="7-aplicacion-por-claude-code"></a>

## 7. Aplicación por Claude Code

Esta es una entrega documental sobre `d366ad9`. Los cambios de F1 que ya estaban en esa base no son implementación de esta guía. Integrar primero las dependencias de esa rama cuando se lleve a `main`; no copiar esta rama entera como si fuera un cambio exclusivamente documental contra el `main` anterior.

| Orden | Trabajo posterior | Evidencia de salida |
|---|---|---|
| 1. Fundamentos ✅ 2026-09-25 | Migrar el acento claro de §2.1 en DESIGN.md y tokens.css juntos; ampliar pares de contraste; reemplazar la propuesta numérica por enlace canónico. Mantener los estados y contratos | Hecho: pruebas de tokens y contraste, tema manual en ambas direcciones, muestrario con las combinaciones del acento en 360/1280 px y ambos temas. REC-019 resuelta |
| 2. F1 🟡 2026-09-25 | Adaptar los cuatro prototipos a esta guía; añadir ejemplos de cuentas/panel y condiciones sin datos. Medir y preparar PMTiles; conservar glifos locales | Prototipos adaptados (Artifact, versión 3) y 62 capturas en 360/768/1280 px y ambos temas. Falta el PMTiles y la revisión visual del dueño; la aprobación del documento no la sustituye |
| 3. Mapa y reportes | Aplicar ficha, navegación, lista, búsqueda, tiempo y flujo de reporte; estados de error contra API real | RF001–RF008, RF037–RF038 y casos de §8 |
| 4. Historia pública | Cumplimiento, estadísticas, bitácora y sustento sin datos inventados | Correspondencia entre cada cifra y respuesta; CSV y paginación coherentes |
| 5. Avisos y cuentas | Formularios, enlaces y TOTP; aplicar decisión técnica pendiente sobre URLs de correo en su fase correspondiente | Recorridos de enlace válido, vencido, usado, doble pulsación y segundo factor |
| 6. Panel | Colas, formularios, cuentas, auditoría y seguridad | Permisos, teclado, conflictos y sesiones vencidas contra backend real |
| 7. Integración | Seguir F6 del plan, sin cambiar el stack aquí | Pruebas funcionales, accesibilidad y rendimiento registradas |

No modificar las tablas de colores semánticos, generar tipos de API a mano, reutilizar los controles del Artifact o añadir endpoints para imitar sus datos de ejemplo. Cada fase conserva su PR y registros según el plan; esta guía no publica ni fusiona cambios.

<a id="8-aceptacion-y-verificacion"></a>

## 8. Aceptación y verificación

### 8.1 Comprobaciones de esta entrega documental

- [x] Enlaces locales y anclas resuelven; cada ruta de interfaz del plan aparece en §5.
- [x] DESIGN.md mantiene sus 13 pares actuales y no supera 200 líneas; los cambios de esta entrega son solo Markdown.
- [x] La matriz de contraste de §2.3 se reproduce con la fórmula indicada; cada rol textual y funcional tiene pareja permitida.
- [x] ADR-069 reemplaza expresamente las presentaciones antiguas del estado nulo sin reescribir las decisiones históricas.
- [x] Plan, recomendación, sprint y bitácora distinguen decisión documental de implementación y aprobación visual.

### 8.2 Comprobaciones posteriores sobre la interfaz

| Escenario | Resultado exigido |
|---|---|
| 360×800, 768×1024, 1280×900, ambos temas | Composiciones de §4; sin scroll horizontal del cuerpo. Barrio seleccionado, estado y horario disponible visibles inicialmente |
| Texto al 200%, nombre largo, teclado móvil y poca altura | Contenido y acciones alcanzables; hoja ampliable; ningún texto esencial recortado |
| Teclado y lector de pantalla | Búsqueda, lista, reporte y diálogos completos; foco visible y devuelto al cerrar; alternativa de mapa equivalente |
| Sistema claro/oscuro + selección manual | Preferencia manual gana y persiste; mapa, SVG, gráficos y panel cambian con los mismos tokens |
| Nulo / sin fecha / sin horario / sin fuente / sin población | Mensajes explícitos; no verde, cero habitantes, cuenta regresiva ni enlace inventados |
| 211 sectores, islas y MultiPolygon | Búsqueda y ficha accesibles aunque falte mapa base; ninguna zona omitida por el encuadre del prototipo |
| Corte futuro, solapado, promesa vencida y cerrado | Horas atribuidas a cada corte; ningún fin prometido tratado como fin real; índice solo con datos medibles |
| Reportar con barrio seleccionado | Dos toques, un solo POST; foto posterior; 429, fallo de foto y desconexión independientes del estado del mapa |
| Cumplimiento vacío y serie con huecos | Sin 0%/100% artificial; conteos solo donde existen; comparación y tabla legibles sin color |
| Bitácora con procedencias mixtas y sustento paginado | Ningún feed presentado como exclusivamente oficial; ids no abren contenido público inexistente |
| Sesión limitada, vencida, permiso revocado y 409 | Acciones correctas, confirmaciones sin doble envío, explicación y recuperación |
| Movimiento reducido, axe y contraste manual | Sin transiciones obligatorias; axe sin violaciones y revisión de mapa/SVG/transparencias, que el cálculo de tokens no cubre |
| 3G simulado y mapa lento | Respuesta útil antes de 3 s según RNF001; comprensión de la ficha en menos de 5 s validada con una persona; sin inferir comprensión de Lighthouse |

Las pruebas funcionales y de rendimiento de esta tabla son criterios para Claude durante implementación, **no resultados obtenidos por escribir esta guía**. Registrar capturas y resultados reales por fase antes de marcar cada criterio como cumplido.
