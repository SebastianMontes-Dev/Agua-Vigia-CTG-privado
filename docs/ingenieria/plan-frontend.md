# Plan del frontend nuevo

> **Qué es.** El plan completo para construir el frontend nuevo de AguaVigía CTG. Trae todo lo necesario para
> empezar a trabajar: las decisiones tomadas, el stack, la estructura, las pantallas, las reglas que no se negocian,
> las trampas de la API, las fases con criterios de terminado y cómo se verifica cada una.
>
> **Estado (2026-09-25):** plan aprobado por el dueño. `ADR-067` escrito. F0 (andamiaje) construido; falta verlo pasar
> en el CI de GitHub. Lo siguiente es F1 (prototipos), que necesita la aprobación del dueño antes de F2.
>
> **Fuente de verdad.** Este plan **no reemplaza** a `DESIGN.md` (diseño), a `docs/api/` (cómo consumir la API) ni a
> `backend/openapi.yaml` (el contrato). Resume lo que hace falta tener a mano y apunta a esos documentos. Si algo de
> aquí contradice a uno de ellos, gana el otro documento y este plan se corrige.

---

## 1. Por qué y para qué

- El frontend anterior se retiró de `main` (`ADR-048`). Su código sigue en la etiqueta `pre-retiro-frontend`.
- El backend está terminado: M1–M15, 58 rutas, 929 pruebas. El contrato vive en `backend/openapi.yaml` y la guía para
  el cliente en `docs/api/`.
- El objetivo es una interfaz **sólida y con identidad propia, que no parezca generada por IA** (`DESIGN.md` §9).
- La prueba que manda es esta: **una persona responde «¿tengo agua o no, y hasta cuándo?» en menos de 5 segundos,
  desde un celular con 3G, sin registrarse y sin hacer scroll** (`DESIGN.md` §1).
- Es un proyecto académico que corre en local (`ADR-057`): sin hosting, sin dominio y sin CDN. Todo tiene que
  funcionar con `docker compose` y sin internet para el mapa.

### Por qué el anterior se veía genérico, y qué se prohíbe

El stack anterior era React 19, Tailwind, framer-motion, gsap, ogl (WebGL decorativo), GooeyNav, GradientWaves,
lucide, Recharts y Leaflet con tiles de OSM/Esri, dentro de un shell tipo Adminator (`ADR-029`).

**Queda fuera de este frontend:** Tailwind, shadcn/ui, lucide y cualquier set de iconos genérico, framer-motion, gsap,
ogl o WebGL decorativo, Recharts, el shell con sidebar de `ADR-029`, los tiles de terceros, los gradientes, los emoji
como marcadores, las tarjetas redondeadas con barrita de color al costado y los KPIs decorativos.

---

## 2. Decisiones tomadas

| Tema | Decisión | Estado |
|---|---|---|
| Framework | **React 19 + Vite + TypeScript estricto** | Decidido por el dueño (2026-09-24) |
| Mapa base | **PMTiles propio de Cartagena** (extracto de OSM servido por nginx, estilo con la paleta del proyecto) | Decidido por el dueño |
| Ejecución | Claude en ramas propias, un PR por fase, con los registros al día | Decidido por el dueño |
| Registro formal | `ADR-067` en `docs/design-decisions.md`: stack, dirección visual y alternativas descartadas. `ADR-029` queda reemplazado | **Escrito (2026-09-25)** |
| ¿Es el Sprint 7? | Sin decidir. Los sprints 0–6 están cerrados | **Lo decide el dueño** |

Las alternativas descartadas (SvelteKit, Astro con islas, mapa sin fondo, tiles de OSM/Esri) y su motivo están en
`ADR-067`.

---

## 3. Stack

| Capa | Elección | Notas |
|---|---|---|
| Base | React 19, Vite, TypeScript `strict` | |
| Rutas | **TanStack Router** | Tipado y con *code-splitting* por ruta: el panel no pesa en la carga del mapa |
| Datos del servidor | **TanStack Query** | Una clave de caché por recurso; sin sondeo en segundo plano salvo el de sectores (§6.3) |
| Cliente HTTP | **openapi-fetch** + tipos de **openapi-typescript** | `npm run api:sync` genera `src/api/generado/esquema.ts` desde `../backend/openapi.yaml`; `api:check` falla en CI si hay deriva |
| Primitivas UI | **React Aria Components** | Sin estilos: foco, teclado y lector de pantalla resueltos |
| Estilos | CSS propio: custom properties, `@layer` y CSS Modules | Tokens en un solo archivo (§5.1) |
| Mapa | **MapLibre GL JS** + protocolo **`pmtiles`** | Estilo propio claro/oscuro; la trama de «sin datos» es un `fill-pattern` |
| Gráficos | SVG a mano, con `d3-scale`/`d3-time` como mucho | Nada de librerías de gráficos |
| Iconos | Set SVG propio | Los 4 glifos de estado, «sin datos» y unos pocos de interfaz |
| Movimiento | Solo CSS | Todo dentro de `@media (prefers-reduced-motion: no-preference)` |
| PWA | `vite-plugin-pwa` (Workbox) | Cachea la app, la geometría, el último `/api/sectores` y los PMTiles. **Sin cola offline de reportes** (`ADR-044`) |
| QR del TOTP | `qrcode` (local) | El secreto no sale a ningún servicio |
| Lint y tipos | oxlint + `tsc -b` | |
| Pruebas unitarias | Vitest + Testing Library + jsdom | |
| E2E | Playwright contra el **backend real** | Más `@axe-core/playwright` |
| Rendimiento | Lighthouse CI con 3G simulado | Presupuesto de `RNF001` |

Las dependencias se agregan en la fase que las usa, no todas en F0: F0 trae la base, el cliente HTTP y las
herramientas de prueba; el router, Query y React Aria entran con las primeras pantallas (F2); MapLibre y `pmtiles`
con el mapa (F1/F2); la PWA con la integración (F6).

**Tipografía:** solo las pilas de sistema de `DESIGN.md` §4. Nada de webfonts. El carácter sale de la escala, el
contraste de pesos, `tabular-nums` y el mono para horas y códigos.

> **Ojo con las etiquetas del mapa.** Para pintar texto, MapLibre necesita glifos SDF (`.pbf`), que no son una
> webfont de interfaz pero sí son fuentes. Hay dos salidas, y se deciden en F1 **con un ADR propio** (la bitácora es
> append-only: `ADR-067` no se edita): (a) servir en local los glifos Noto de Protomaps (`basemaps-assets`),
> dejándolo registrado como excepción acotada al mapa; o (b) mapa sin etiquetas de texto, con los nombres en la
> tarjeta, la ficha y la lista. **No se cargan glifos desde internet.**

---

## 4. Estructura

```
frontend/
├── index.html
├── vite.config.ts            proxy de /api, /fotos y /acuacar-media → http://localhost:8081 · PWA
├── playwright.config.ts
├── lighthouserc.json
├── package.json              scripts: dev, build, lint, test, test:e2e, api:sync, api:check, mapa:estilo
├── public/
│   └── mapa/                 cartagena.pmtiles (ver §8) · sprites de la trama
├── e2e/                      recorridos de Playwright contra el backend real
└── src/
    ├── app/                  router, proveedores (Query, tema), raíz y límites de error
    ├── api/
    │   ├── generado/esquema.ts   ← generado, no se edita
    │   ├── cliente.ts            openapi-fetch + normalización RFC 7807 por `type`
    │   ├── paginacion.ts         lee X-Total-Count, X-Page, Link
    │   ├── canal-en-vivo.ts      SSE con jitter, backoff, 429 y visibilidad (§6.3)
    │   ├── huella.ts             huella anónima persistente (§6.4)
    │   └── sesion.ts             token en sessionStorage, reacción a 401 por `type`
    ├── dominio/
    │   ├── estados.ts            EstadoServicio | null → token, glifo y texto (una sola tabla)
    │   ├── tiempo.ts             UTC → hora de Cartagena (UTC-5) · «hace X» · «4 horas y media»
    │   └── permisos.ts           qué pinta cada permiso
    ├── estilos/
    │   ├── tokens.css            §5.1 (única fuente de color en el frontend)
    │   ├── base.css              reset, tipografía, foco, reduced-motion
    │   └── capas.css             orden de @layer
    ├── mapa/                     estilo MapLibre claro/oscuro, capas de sectores, trama, lista accesible
    ├── componentes/              piezas reutilizables (glifo de estado, frescura, regla de tiempo, hoja…)
    └── pantallas/
        ├── publico/              mapa, sector, reporte, confirmar, cumplimiento, bitácora, estadísticas, avisos
        ├── cuentas/              solicitar, verificar, invitación, olvidé mi clave, restablecer
        └── panel/                ingreso, TOTP, cola, cortes, ingesta, cuentas, auditoría, seguridad
```

Convenciones: los identificadores del dominio van en español (`EstadoServicio`, `calcularFrescura`) y los términos
técnicos universales en inglés (`Provider`, `hook`). No se mezclan en un mismo identificador (`CLAUDE.md`). Los
comentarios solo explican el porqué. Las pruebas llevan nombre descriptivo en español.

---

## 5. Diseño

### 5.1 Tokens (copiar tal cual de `DESIGN.md` §2–§3)

- **Estado del servicio:** con servicio `#1c7f55`/`#4fbf89` · sin servicio `#ae3428`/`#e2695b` · presión baja
  `#94640c`/`#d9a63c` · corte programado `#2a628f`/`#6ba8da` (claro/oscuro).
- **Base:** acento `#087f8c`/`#54c6ca`, acento vivo `#0796a5`/`#78d9db`, acento suave `#dcefee`/`#153f44`, tinta
  `#102f39`/`#eef8f7`, tinta secundaria `#526a70`/`#aac0c0`, tinta terciaria `#789095`/`#789296`, línea
  `#d8e5e3`/`#24454b`, superficie `#fbfdfc`/`#0c2830`, fondo `#f2f7f6`/`#061c23`.
- Van en `:root`, se redefinen en `@media (prefers-color-scheme: dark)` y otra vez bajo `:root[data-theme="dark"]` y
  `:root[data-theme="light"]`, para que el interruptor gane en las dos direcciones.
- **El estilo de MapLibre lee los mismos valores** (desde `getComputedStyle` o desde un módulo que exporte los
  tokens). Si el mapa y la leyenda dan dos colores distintos para un mismo estado, el color deja de significar algo.
- **Los cuatro colores de estado no se usan para nada más:** ni verde de éxito, ni rojo de error de formulario, ni
  ámbar de advertencia. Todo lo demás de la interfaz usa el acento turquesa.

### 5.2 Lenguaje visual

| Pieza | Cómo es |
|---|---|
| **Mapa (pantalla principal)** | Ocupa toda la pantalla, sin shell ni sidebar. En móvil hay una hoja inferior con tres posiciones (asomada, media, completa); en escritorio, un panel lateral |
| **Tarjeta de respuesta** | Lo primero que se lee, en una línea: `Bocagrande · Sin agua desde las 6:10 a. m. · Prometieron volver a las 2:00 p. m.`, y abajo `actualizado hace 4 min` |
| **Glifos de estado** | Una forma propia por estado, además del color: con servicio (gota llena), sin servicio (gota tachada), presión baja (gota a medias), programado (gota con reloj). Van siempre con su texto |
| **«Sin datos»** | **Trama diagonal** en el mapa y en la leyenda, con el texto «Sin datos verificados». Nunca verde |
| **Frescura** | `hace X` en cada sector. Si un dato envejece, se atenúa y lo dice («dato de hace 3 horas») |
| **Índice de Cumplimiento** | Una **regla de tiempo**: el tramo prometido y el real sobre un eje de horas, con el exceso marcado. Texto: `Prometieron 2 horas · Fueron 8`. La serie mensual muestra `cantidadCortes` («sobre 3 cortes») |
| **Bitácora** | Un **acta pública**: hora en mono a la izquierda, evento en prosa y enlace a la fuente. Los eventos con `estado: null` van neutros |
| **Panel del veedor** | Denso y pensado para teclado: tablas y colas con atajos (`j`/`k` para moverse, `a` aprobar, `d` descartar, con confirmación). Sin tarjetas de KPI |
| **Carga y vacío** | Esqueletos con la forma del contenido, nunca un spinner solo. El vacío explica y ofrece una acción: `Todavía nadie ha reportado en este sector` |
| **Textos** | Tuteo, voz activa y escritos desde el lado del vecino. Errores que dicen qué pasó y cómo salir (`DESIGN.md` §5) |

### 5.3 Mínimos de accesibilidad y rendimiento (`DESIGN.md` §7–§8)

Contraste AA en los dos temas · todo operable con teclado y `:focus-visible` de 2 px · objetivos táctiles de al menos
44×44 px · `prefers-reduced-motion` · lista textual de sectores como alternativa al mapa (RF004) · etiquetas reales en
los formularios · funciona desde 360 px · el cuerpo nunca hace scroll horizontal · primero se pinta el estado y después
la geometría · primera respuesta útil en menos de 3 s en 3G (`RNF001`).

---

## 6. La API: lo que el frontend debe hacer bien

Detalle completo en `docs/api/`. Aquí van las reglas que, si se olvidan, rompen algo.

### 6.1 Generales

- Todo cuelga de `/api/…`, más `/fotos/…` y `/acuacar-media/…`. En desarrollo, Vite hace de proxy a
  `http://localhost:8081`. CORS ya deja pasar `localhost:5173`, pero con el proxy no hace falta.
- Las fechas llegan en **UTC** y se muestran en **hora de Cartagena (UTC-5, sin horario de verano)**.
- **`null` es «no hay dato», nunca un valor por defecto.** El caso crítico es `estado: null`, que se muestra como
  «sin datos» y **nunca como con servicio**.
- Los errores son RFC 7807: **se reacciona por `type`**, nunca por `title` ni `detail`. El `detail` se puede mostrar,
  salvo en un `500`. Catálogo: `docs/api/errores-y-limites.md`.
- **Paginación por cabeceras:** la respuesta es un arreglo y trae `X-Total-Count`, `X-Total-Pages`, `X-Page` (desde 0),
  `X-Page-Size` y `Link rel="next"`. Parámetros `?pagina=0&tamano=50` (máximo 200).
- Solo se reintenta lo idempotente (`GET`), como mucho 3 veces, con espera creciente y jitter. Ante un `429` se espera
  `Retry-After`.

### 6.2 Mapa (`docs/api/sectores-y-tiempo-real.md`)

1. `GET /api/sectores/geometria` **una vez**, guardada en IndexedDB. Tiene caché de un día y pesa ~0,7 MB.
   **No mandes `Accept: application/json`**: el servidor responde un `404` engañoso. Deja el `*/*` de `fetch`.
2. `GET /api/sectores` (211 sectores con `estado`, `actualizadoEn`, `poblacion` y `generadoEn`). Se une a la
   geometría por `id` sin calcular nada. **Nunca más de una vez cada 5 s.**
3. `GET /api/sectores/{id}` y `GET /api/sectores/{id}/cortes` (paginado) al abrir la ficha, no en segundo plano.
4. Las coordenadas del GeoJSON van como `[lon, lat]`; las del reporte llevan `latitud` y `longitud` con nombre.
5. `zona-industrial` es `MultiPolygon`; todos los demás son `Polygon`.
6. `poblacion: null` (27 barrios) se muestra como «sin dato censal», nunca como 0.

### 6.3 Canal en vivo (SSE, `docs/api/escalabilidad-para-el-cliente.md`)

- `GET /api/sectores/stream`: el evento `sectores` **solo avisa**. Al recibirlo se pide `GET /api/sectores` tras un
  **retardo aleatorio de 0 a 3 s**.
- Si falla, se cierra y se reconecta con espera exponencial y jitter (tope de 60 s). El `retry:` del servidor se
  respeta.
- Ante un `429` al abrir: se espera `Retry-After` y mientras tanto se **sondea cada 30 s**.
- **Con la pestaña oculta** (`visibilitychange`), un rato después se cierra y se reabre al volver. No hay sondeo en
  segundo plano.
- El servidor corta cada 10–12 min por diseño: reconectar es normal y no se muestra como error.
- Sin red: se muestra el último listado guardado, con su `generadoEn` («dato de hace N min»).

### 6.4 Reporte ciudadano (`docs/api/reportes.md`)

- **Huella:** se genera **una vez por dispositivo** (UUID aleatorio más sal, SHA-256, 64 caracteres hex) y se guarda
  en `localStorage`. Se reutiliza siempre y **no se deriva de nada identificable**.
- `POST /api/reportes` `{ tipo, huella, sectorId? , coordenada? }`, donde `tipo` es `SIN_AGUA`, `PRESION_BAJA` o
  `SERVICIO_RESTABLECIDO`. Basta con el sector abierto **o** con la ubicación (RF007). **Dos toques como máximo**
  desde el mapa (RF008).
- **Nunca se reintenta solo un `POST`.** El botón se desactiva mientras se envía.
- `429 limite-reportes-excedido` significa 3 reportes por sector cada 30 min. Se responde con un mensaje amable y sin
  reintentar. `400` con una coordenada fuera de Cartagena: se ofrece elegir el sector a mano.
- Después de enviar **no se promete que el mapa cambió.** Se dice «Gracias. Tu reporte cuenta junto con el de tus
  vecinos».
- Foto opcional: `POST /api/reportes/{id}/foto` en multipart con la parte `foto`. JPEG, PNG o WebP, 10 MB como
  máximo (`413`).
- Confirmar un reporte ajeno: `/confirmar/:id` hace `POST /api/reportes/{id}/confirmar` `{ huella }`. Un `404`
  significa que el reporte no existe o se descartó. No hay listado público de reportes.
- No hay cola offline (`ADR-044`): si no hay red, se falla de forma explícita y se ofrece reintentar a mano.

### 6.5 Historia pública (`docs/api/bitacora-estadisticas-cumplimiento.md`)

- `GET /api/bitacora` (paginado). Hay que tolerar `null` en `sectorId`, `corteId`, `estado`, `urlOriginal` e
  `imagenUrl`. **La fuente (`urlOriginal`) se muestra siempre.** En `imagenUrl` se cambia
  `https://www.acuacar.com/wp-content/uploads/` por `/acuacar-media/`. El sustento
  (`/api/bitacora/{id}/sustento`) solo se pide al abrir el detalle.
- `GET /api/cumplimiento`, `/sectores/{id}` y `/serie`. **Un `400` sin cortes cerrados es «aún sin datos», no un
  error.**
- `GET /api/estadisticas`: `cortesPorDiaDeSemana` trae 7 claves en español que hay que **ordenar a mano**. Los CSV se
  descargan con `<a href download>`, no por `fetch`.

### 6.6 Avisos (`docs/api/suscripciones.md`, `docs/api/correos-y-enlaces.md`)

- `POST /api/suscripciones` `{ correo, sectorIds[] }`, con un selector que busca entre los 211. El mensaje de
  respuesta es siempre neutro: «Si la dirección es válida, te enviamos un correo. El enlace vence en 48 horas».
- Pantallas propias `/avisos/confirmar?token=` y `/avisos/baja?token=`: se lee el token, se quita de la URL
  (`history.replaceState`) y **el `POST` se hace al pulsar el botón**, con `Accept: application/json`. Llevan
  `<meta name="referrer" content="no-referrer">` y ningún script de terceros.
- Telegram (RF041) no tiene endpoint. Si el bot está armado, basta un enlace `t.me/<bot>` con los comandos
  (`docs/ingenieria/telegram.md`).

### 6.7 Cuentas y panel (`docs/api/cuentas-y-sesion.md`, `docs/api/panel-veedor.md`)

- `POST /api/veedor/sesion`: el primer intento va sin `codigoTotp`. Un `401 segundo-factor-requerido` **no es clave
  mala**: se pide el código y se repite. `403 cuenta-no-habilitada` trae un mensaje según `estado`, `423` trae
  `segundosRestantes` y `429` trae `Retry-After`.
- El token se guarda en **`sessionStorage`** y viaja en `Authorization: Bearer`. Nunca va en una URL. No hay refresco:
  dura 8 h. Al recargar se llama a `GET /api/veedor/yo`.
- **Ante un `401` con un `type` que no sea `credencial-invalida` ni `segundo-factor-requerido`, se borra el token y se
  va al ingreso.** Un `403 acceso-denegado` solo quita la acción de la pantalla.
- **La interfaz se pinta con `permisos[]`, nunca con el rol.**
- Un ADMIN sin TOTP entra con `alcance: "ALTA_SEGUNDO_FACTOR"`: se le lleva a `alta` (QR local, **el secreto se
  muestra una sola vez**) y luego a `confirmacion`, que devuelve un token nuevo con alcance completo que reemplaza al
  anterior.
- En ingesta, la `citaTextual` y el `urlOriginal` van **junto** a los botones de aprobar y descartar (`ADR-006`). Una
  propuesta ya resuelta deshabilita sus botones. Después de aprobar se vuelve a pedir `GET /api/sectores`, porque un
  `200` no garantiza que el mapa haya cambiado.
- Los cortes creados por la ingesta no se pueden cerrar (limitación conocida): no se muestra el botón.
- Clave: entre 12 y 128 caracteres, validada en el cliente. `restablecimiento` y `reenvio` responden siempre `202`,
  así que el mensaje es siempre el mismo. Cambiar la clave o desactivar el TOTP cierra todas las sesiones, así que
  después toca volver a ingresar.
- Un ADMIN no puede cambiar su propio acceso: la interfaz no lo ofrece.

---

## 7. Pantallas y rutas del frontend

| Ruta | Pantalla | API | Requisitos |
|---|---|---|---|
| `/` | Mapa + tarjeta de respuesta + lista accesible + buscador | geometria, sectores, stream | RF001, RF003, RF004 |
| `/sectores/:id` | Ficha (en la hoja o el panel): estado, frescura, histórico de cortes, cumplimiento del sector, reportar, avisos | sectores/{id}, /cortes, cumplimiento/sectores/{id} | RF002, RF021 |
| (hoja sobre el mapa) | Reportar en 2 toques + foto opcional | reportes, /foto | RF005–RF008, RF037 |
| `/confirmar/:id` | «¿Tú también estás sin agua?» | reportes/{id}/confirmar | RF038 |
| `/cumplimiento` | Índice global, regla de tiempo, serie mensual, CSV | cumplimiento, /serie, /serie.csv | RF020–RF022, RF024 |
| `/bitacora` | Acta pública paginada con su sustento | bitacora, /{id}/sustento | RF011, RF026–RF028 |
| `/estadisticas` | Sectores más afectados, cortes por día, duración media, CSV | estadisticas, exportar.csv | RF023, RF025 |
| `/avisos`, `/avisos/confirmar`, `/avisos/baja` | Suscribirse, confirmar y darse de baja | suscripciones | RF012–RF015, RF041 |
| `/cuenta/solicitar`, `/cuenta/verificar`, `/cuenta/invitacion`, `/cuenta/olvide`, `/cuenta/restablecer` | Alta y recuperación de cuenta | cuentas/** | RF042, RF043, RF046 |
| `/panel/ingreso`, `/panel/segundo-factor` | Ingreso y alta de TOTP | veedor/sesion, segundo-factor/** | RF019, RNF025 |
| `/panel` | Cola de moderación | reportes/pendientes, aprobar, descartar | RF018 |
| `/panel/cortes` | Registrar y cerrar cortes | veedor/cortes | RF016, RF017 |
| `/panel/ingesta` | Propuestas con cita y salud de los colectores | ingesta/propuestas, /salud | M9, RNF007 |
| `/panel/cuentas`, `/panel/auditoria` | Gestión de cuentas (ADMIN) y auditoría | veedor/usuarios/**, auditoria | RF044, RF045 |
| `/panel/seguridad` | Cambiar la clave, TOTP y cerrar sesión | cuenta/clave, segundo-factor, sesion/cierre | RF046 |

---

## 8. Mapa base PMTiles (a verificar en F1)

1. Instalar el CLI de `pmtiles` (go-pmtiles) y extraer Cartagena de un *build* diario de Protomaps:
   `pmtiles extract https://build.protomaps.com/<AAAAMMDD>.pmtiles cartagena.pmtiles --bbox=-75.70,10.25,-75.40,10.55 --maxzoom=15`
   (el bbox y el zoom máximo se ajustan viendo el resultado). El comando se deja en un script de `scripts/` para
   poder repetirlo.
2. **Medir el tamaño** y decidir si se versiona (con git LFS o sin él) o si se genera al preparar el entorno. Si se
   genera, va a `.gitignore` y se documenta en `docs/ingenieria/entorno-local.md`.
3. Estilo propio a partir de las capas de `@protomaps/basemaps`: agua, tierra, vías y edificios pintados con los
   neutros de §5.1, sin colores de estado, en claro y oscuro.
4. Licencia **ODbL**: atribución visible «© OpenStreetMap». Queda registrado en `ADR-067`.
5. nginx debe servir `.pmtiles` con *range requests* (`Accept-Ranges: bytes`) y caché larga.
6. **CSP:** MapLibre usa *workers*. Hay que probar si basta `worker-src 'self' blob:` o si se usa la variante CSP de
   MapLibre. Todo es del mismo origen: nada externo en `img-src` ni en `connect-src`.

---

## 9. MCP y conectores

| Herramienta | Para qué | Estado |
|---|---|---|
| **Playwright MCP** (`@playwright/mcp`) | Manejar la app real, tomar capturas en 360 y 1280 px en los dos temas, recorrer los flujos y probar solo con teclado | En `.mcp.json` (F0) |
| **Chrome DevTools MCP** (`chrome-devtools-mcp`) | Trazas de rendimiento, Lighthouse con 3G y revisar la red y el SSE | En `.mcp.json` (F0) |
| **MongoDB MCP** | Forzar estados de sector (nulo, viejo, degradado) para probar la interfaz | Configurado pero falla por *timeout*. Revisar `MONGODB_URI` con `directConnection=true` (`ADR-063`) |
| **Artifacts de claude.ai** | Prototipos HTML privados de F1 para revisar en el celular | Disponible |
| Canva | Solo piezas de la presentación y la demo, no la UI | Conectado |
| GitHub MCP | No hace falta en local: se usa `gh` | Deshabilitado |
| Microsoft 365 | No hace falta | Requiere autorizarse en claude.ai |

---

## 10. Fases

Cada fase tiene su rama y su PR, que se fusiona con squash y con CI en verde (`CONTRIBUTING.md`). **Una fase cierra
cuando su entregable se demuestra funcionando**, no por calendario.

### F0 — Decisión y andamiaje
- [x] `ADR-067`: stack, dirección visual y alternativas descartadas. `ADR-029` pasa a *Reemplazada*.
- [x] `frontend/` con Vite 8, React 19 y TS `strict` (configuraciones separadas para la app, Node y las pruebas).
      `tokens.css` copiado de `DESIGN.md` (`tokens.test.ts` falla si divergen), `base.css` y `capas.css`.
- [x] `api:sync` y `api:check` (`scripts/contrato-api.mjs`, con openapi-typescript). `cliente.ts` con openapi-fetch,
      normalización RFC 7807 por `type` y cierre de sesión ante un `401` que no sea de credencial ni de segundo
      factor. `dominio/estados.ts` con `null` → «Sin datos verificados».
- [x] Proxy de Vite a `:8081` para `/api`, `/fotos` y `/acuacar-media` (variable `AGUAVIGIA_BACKEND` para cambiarlo).
      Vitest, Playwright (360 y 1280 px) y oxlint configurados, con pruebas de cada uno.
- [x] `.github/workflows/frontend-ci.yml`: lint, tipos, `api:check`, pruebas, build y E2E. Se dispara también con
      cambios en `backend/openapi.yaml` y en `DESIGN.md`.
- [x] Playwright MCP y Chrome DevTools MCP en `.mcp.json`.
- **Hecho cuando:** `npm run dev` muestra una página con los tokens en los dos temas, el CI pasa y `api:check`
  detecta un cambio forzado en el contrato.
- **Verificado (2026-09-25):** 22 pruebas unitarias y 6 E2E en verde; `api:check` falla con un valor agregado al
  enum de estado en `openapi.yaml` y vuelve a verde al quitarlo; el proxy probado contra un servidor falso en `:8081`.
  **Falta:** el CI en GitHub (corre al abrir el PR) y probar el proxy contra el backend real (el contenedor de esta
  sesión no tenía Docker). La página del muestrario es provisional: la reemplaza el mapa en F2.
- **Las E2E de F0 no necesitan backend.** Desde F2 las que sí lo necesitan irán en un job aparte con `docker compose`.

### F1 — Prototipos y lenguaje visual (sin código de producción)
- [ ] Prototipos HTML en Artifacts de: mapa + tarjeta + ficha, reporte en 2 toques, cumplimiento con la regla de
      tiempo y bitácora. Cada uno en 360 y 1280 px, en claro y oscuro.
- [ ] Glifos de estado y trama de «sin datos» en SVG.
- [ ] Extracto PMTiles y estilo MapLibre claro/oscuro (§8). Decidir los glifos de texto (§3) en un ADR propio.
- [ ] Tabla de contraste AA medida para cada par de texto y fondo, en los dos temas.
- **Hecho cuando:** el dueño aprueba los prototipos. **Sin esa aprobación no empieza F2.**

### F2 — Núcleo ciudadano (M1, M2)
- [ ] Geometría en IndexedDB, estado unido por `id`, `MultiPolygon`, trama para `null`.
- [ ] `canal-en-vivo.ts` con todas las reglas de §6.3 y pruebas unitarias del jitter, el backoff, el `429` y la
      visibilidad.
- [ ] Tarjeta de respuesta, ficha del sector, lista accesible y buscador.
- [ ] Reporte en 2 toques con huella, ubicación opcional, foto y todos los errores de §6.4. `/confirmar/:id`.
- [ ] E2E: abrir el mapa, reportar, llegar al cuarto reporte y recibir el `429` con su mensaje, cambio de estado por
      consenso (3 huellas) que llega por SSE, y confirmar un reporte.
- **Hecho cuando:** pasa el checklist de `DESIGN.md` §10 y el mapa muestra todos los estados en menos de 3 s en 3G.

### F3 — Historia pública (M6, M7, M8)
- [ ] Bitácora paginada con fuente, portada por `/acuacar-media/` y sustento bajo demanda.
- [ ] Cumplimiento global y por sector, la regla de tiempo, la serie mensual con `cantidadCortes` y el CSV.
- [ ] Estadísticas con los días ordenados y el CSV.
- [ ] Estados vacíos: un `400` sin cortes cerrados se muestra como «aún sin datos».

### F4 — Avisos (M4)
- [ ] Formulario de suscripción y pantallas de confirmar y dar de baja (§6.6).
- [ ] **Backend en el mismo PR:** `MailNotificacionAdapter`
      (`backend/src/main/java/com/aguavigia/ctg/infrastructure/mail/`) arma hoy `urlReportar` como
      `urlBasePublica + "/api/sectores/{id}"` y los enlaces de confirmar y dar de baja hacia `/api/suscripciones/…`.
      Hay que apuntarlos a `/sectores/{id}`, `/avisos/confirmar` y `/avisos/baja`. Decidir si se usa una propiedad
      nueva (p. ej. `aguavigia.app.url-frontend`) o la misma `url-publica`: hoy vale `http://localhost:8081` en
      `docker-compose.yml`, que es la API y no la SPA. Hay que actualizar sus pruebas y
      `comportamiento-del-sistema.md`.
- [ ] E2E con Mailhog (`:8025`): suscribirse, abrir el enlace, confirmar y darse de baja.

### F5 — Cuentas y panel (M5, M15)
- [ ] Ingreso con todos los errores de §6.7, alta de TOTP con QR local, `yo`, cierre de sesión y reacción a `401`.
- [ ] Cola de moderación, cortes (registrar y cerrar), ingesta (cita, fuente y salud), cuentas, permisos, invitaciones
      y auditoría. Todo según `permisos[]`.
- [ ] Pantallas de `/cuenta/*`. **Backend:** `MailCuentaAdapter` arma hoy `…/api/cuentas/enlaces/{ruta}?token=` y
      hay que apuntarlo a `/cuenta/verificar`, `/cuenta/invitacion` y `/cuenta/restablecer`.
- [ ] E2E: ingreso de un VEEDOR, primer ingreso de un ADMIN con TOTP, moderar, registrar y cerrar un corte, invitar
      y aceptar la invitación leyendo el correo en Mailhog.

### F6 — Integración y endurecimiento
- [ ] `frontend/Dockerfile` multi-etapa. nginx (`infra/nginx/`) sirve la SPA en `/` con `try_files` a `index.html`,
      *assets* con hash e `immutable`, `.pmtiles` con *range* y CSP estricta, todo del mismo origen. **Esto cambia la
      condición de `ADR-048`** («el proxy sirve solo la API»), así que se registra en un ADR nuevo.
- [ ] Servicio en `docker-compose.yml`: todo se levanta con un solo comando (`RNF020`).
- [ ] Lighthouse CI con presupuesto de 3G, axe sin violaciones y capturas de regresión en 360 y 1280 px, en los dos
      temas.
- [ ] Reactivar `RNF001`, `RNF012`–`RNF016` y las partes de UI de `RF001`–`RF004` y `RF008` en
      `product-requirements.md` y en `matriz-trazabilidad.md`. Actualizar `guion-de-demo.md`, `CLAUDE.md` (§Estado y
      §Stack, dentro de su presupuesto) y `docs/ingenieria/integracion-frontend-backend.md`.

---

## 11. Registros del proyecto (definición de terminado)

| Ocurre | Dónde | Cómo |
|---|---|---|
| Se fusiona cada PR | `docs/gestion/registro-de-implementaciones.md` | skill `registrar-implementacion` |
| Aparece un bug, aunque se arregle en el acto | `docs/gestion/registro-de-bugs.md` | skill `registrar-bug` |
| Se elige entre alternativas (glifos del mapa, versionar PMTiles, URL de los correos…) | `docs/design-decisions.md` | skill `registrar-decision` |
| Cambia el comportamiento del sistema (enlaces de correo, nginx) | `docs/ingenieria/comportamiento-del-sistema.md`, en el mismo PR | — |
| Termina una sesión | `docs/gestion/bitacora-sesiones.md` | skill `cerrar-sesion` |
| Algo que podría hacerse mejor | `docs/gestion/recomendaciones-ia.md` | skill `registrar-recomendacion` |

**Reglas de Git:** Conventional Commits en español (`feat(frontend): …`). **Nunca** `Co-Authored-By` ni firmas de IA
(`CLAUDE.md` § Autoría). Las fechas van en hora de Cartagena.
**Presupuestos:** `DESIGN.md` ≤ 200 líneas (hoy tiene 192), así que el detalle nuevo de diseño va a un ADR o a este
plan, no ahí. `CLAUDE.md` ≤ 200 y `MEMORY.md` ≤ 150 (hoy 149: lo nuevo desplaza, no se suma).

---

## 12. Cómo arrancar y verificar

```bash
# Backend y datos
cp .env.example .env            # JWT_SECRET, VEEDOR_PASSWORD_HASH, ADMIN_INICIAL_CORREO
docker compose up -d --build --wait          # API en :8081 · Swagger :8081/swagger-ui.html · Mailhog :8025
node scripts/sembrar-sectores.mjs
node scripts/sembrar-demo.mjs
node scripts/sembrar-historico-cortes.mjs

# Frontend
cd frontend
npm install
npm run api:sync && npm run dev              # http://localhost:5173
npm run lint && npm test && npm run build
npm run test:e2e                             # contra el backend real
```

Trampas del entorno (`MEMORY.md`): después de traer cambios de `main`, reconstruir con
`docker compose up -d --build backend`. Sin un `.env` junto al compose, el backend arranca sin ADMIN. «Cerrar sesión»
a veces falla por el margen de 1 s del filtro JWT (`plan-de-pruebas.md` §8).

**Verificación por pantalla, antes de cerrar su PR:**

1. Checklist de `DESIGN.md` §10 completo.
2. Playwright MCP: capturas en 360 y 1280 px en claro y oscuro, y el flujo recorrido solo con teclado.
3. axe sin violaciones. Contraste AA medido.
4. Chrome DevTools MCP: la traza con 3G muestra la respuesta útil en menos de 3 s (en el mapa) y **no hay peticiones
   de sondeo con la pestaña oculta**.
5. MongoDB MCP: probar sectores con `estado: null`, datos viejos y fuentes degradadas (trama, «hace X» y aviso).
6. Probado contra el backend real, no contra un simulacro. Si una ruta no se probó, se dice.

---

## 13. Pendientes que decide el dueño

1. ¿Esto abre un **Sprint 7** en `docs/gestion/`?
2. En F1: ¿glifos locales para las etiquetas del mapa, o un mapa sin texto? ¿El PMTiles se versiona o se genera?
3. En F4/F5: ¿URL de los correos con una propiedad nueva (`url-frontend`) o reutilizando `url-publica`?
