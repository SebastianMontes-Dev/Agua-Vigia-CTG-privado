# Recomendaciones de la IA

> Observaciones que Claude registra al trabajar en este repositorio, cuando nota algo que se está
> haciendo mal o que podría hacerse mejor. No es un defecto (eso va a `registro-de-bugs.md`) ni una
> decisión ya tomada entre alternativas (eso va a `design-decisions.md`) — es una lectura que yo
> valido o descarto.
>
> **Para agregar una entrada: usa la skill `registrar-recomendacion`.**
> Aparecen en la Sala de control (sección **Recomendaciones**) en cada regeneración — no hace falta
> tocar el HTML a mano.

---

## Tabla de estado

| ID | Fecha | Título | Estado |
|---|---|---|---|
| REC-004 | 2026-08-08 | La cobertura de pruebas del frontend está muy por debajo de la del backend | Cerrada — obsoleta |
| REC-006 | 2026-08-09 | `RateLimitConfig` se cuela en cualquier `@WebMvcTest` aunque no se importe, y rompe pruebas en silencio al activar reglas reales | Resuelta |
| REC-007 | 2026-08-28 | Las ramas fusionadas se acumulan en GitHub porque falta activar el borrado automático | Resuelta |
| REC-008 | 2026-08-30 | El fuente de `index.css` está semi-minificado: el breakpoint móvil completo vive en una sola línea de 2.509 caracteres | Resuelta |
| REC-009 | 2026-08-30 | 25 reglas usan `transition: all`, que anima también propiedades de layout y dispara reflow en cada hover | Resuelta |
| REC-010 | 2026-08-31 | `CLAUDE.md` seguía declarando «Sprint 0 · ANDAMIAJE» sobre un backend terminado | Resuelta |
| REC-011 | 2026-09-04 | Los 15 endpoints de M15 (cuentas, permisos y segundo factor) no tienen prueba de contrato, y RNF022 la exige | Resuelta |
| REC-012 | 2026-09-04 | Las respuestas 401 y 403 de la cadena de seguridad no salen en RFC 7807, a diferencia del resto de la API | Resuelta |
| REC-013 | 2026-09-04 | El allowlist de gitleaks exceptúa un archivo entero, no un secreto concreto | Resuelta |
| REC-014 | 2026-09-04 | `sprint-2.md` lleva abierto desde el 2026-08-09 mientras el repositorio ya entregó M10–M15 | Resuelta |
| REC-015 | 2026-09-04 | Nada impide que `index.css` y `tipos-dominio.ts` vuelvan a discrepar en los colores de estado | Resuelta |
| REC-016 | 2026-09-21 | La regla `Read(**/*secret*)` de `.claude/settings.json` bloquea `secret-scan.yml`, el único archivo de CI que hay que corregir para `BUG-089` | Resuelta |
| REC-017 | 2026-09-21 | Los Sprints 3 a 6 de la hoja de ruta siguen escritos como si no se hubiera construido nada, y dos de sus entregables chocan con `ADR-048` y `ADR-057` | Resuelta |
| REC-018 | 2026-09-22 | Qué cuenta como "demo" del Sprint 6 sin frontend propio en el repositorio no está decidido | Resuelta |

**Estado:** `Pendiente` (sin revisar) · `Validada` (estoy de acuerdo, puede pasar a ADR/issue/tarea) ·
`Descartada` (no estoy de acuerdo — deja el motivo en el detalle) · `Resuelta` (ya se actuó sobre
ella)

---

## Detalle

### REC-004 — La cobertura de pruebas del frontend está muy por debajo de la del backend

- **Fecha:** 2026-08-08 · **Estado:** Cerrada — obsoleta

**Cerrada el 2026-09-21:** el frontend se retiró de `main` (`ADR-048`; su código sigue en la etiqueta `pre-retiro-frontend`) y se rehace en otras ramas de este repositorio. La recomendación no se resuelve: deja de aplicar aquí. La cobertura del frontend nuevo será asunto de esas ramas.

El backend tiene 23 pruebas reales, incluido ArchUnit protegiendo la Regla de Oro. El frontend tiene
2 (`InsigniaEstado.test.tsx`, `PaginaVeedor.test.tsx`) contra 20 archivos de componentes. `RNF017`
pide ≥70% de cobertura — vale la pena empezar a cerrar esa brecha antes de que el Sprint 2 traiga más
superficie de UI todavía sin probar.

**Avance del 2026-09-01** — cifras verificadas corriendo las dos suites, no las de agosto: backend
**632** pruebas, frontend **81** (eran 63) en 17 archivos contra **50** componentes y páginas. La
brecha sigue abierta, así que la recomendación no se cierra; lo que se hizo fue cubrir primero la
superficie donde ya hubo bugs de *afirmar lo que no se sabe*, que es el principio del proyecto:

| Archivo nuevo | Qué invariante protege |
|---|---|
| `SeccionEstadisticas.test.tsx` (4) | `BUG-063` (S1): con la API sin cortes cerrados, las cinco métricas dicen «Sin datos» y no 100% ni ceros |
| `SeccionBitacora.test.tsx` (6) | Un evento que no habla del servicio se lista como «Informativo» y no como corte; el estado del evento manda sobre el deducido del tipo; `BUG-049` (portadas por el proxy `/acuacar-media/`); fecha real del boletín |
| `EditorPermisos.test.tsx` (8) | M15: no se ofrece revocar `CONFIGURAR_SEGUNDO_FACTOR`; concesiones y revocaciones se deducen bien del rol; cambiar de rol no arrastra ajustes previos |

También se agregó un stub de `ResizeObserver` a `src/setupTests.ts`, junto al de
`IntersectionObserver` que ya estaba: jsdom no implementa ninguno de los dos y el carrusel de la
Bitácora no se podía montar sin él.

**Avance del 2026-09-05** — se añadieron pruebas unitarias para `PaginaCuentas.tsx` (`PaginaCuentas.test.tsx`, 4 pruebas) y `PaginaMapa.tsx` (`PaginaMapa.test.tsx`, 4 pruebas), cubriendo renderizado, estados de carga, navegación y protecciones defensivas ante permisos vacíos, además de `colores-estado.test.ts` (3 pruebas). La suite de Vitest alcanza **106** pruebas pasando en **22** archivos.

**Lo siguiente, por tamaño y por riesgo:** `PanelVeedor.tsx` (620 líneas) sigue pendiente de prueba directa. Para poder afirmar el ≥70% de `RNF017` con un
número y no con una impresión, hace falta además `@vitest/coverage-v8`, que hoy no es dependencia
del proyecto — decisión pendiente, no se agregó por cuenta propia.

### REC-006 — `RateLimitConfig` se cuela en cualquier `@WebMvcTest` aunque no se importe, y rompe pruebas en silencio al activar reglas reales

- **Fecha:** 2026-08-09 · **Estado:** Resuelta

**Resuelta el 2026-09-22:** se documentó en el propio `RateLimitConfig.java` (javadoc de la clase),
que es el único sitio que cualquiera que escriba un `@WebMvcTest` nuevo va a mirar cuando le falle
con un 500 inexplicable. Nombra las dos rutas reales afectadas hoy (`/api/veedor/sesion`,
`/api/reportes/**`) y el arreglo exacto.

Al llenar `aguavigia.rate-limit.reglas` con las reglas de `/api/veedor/sesion` y `/api/reportes`,
9 pruebas en `ReporteControllerTest` y
`VeedorAuthControllerTest` empezaron a fallar con 500: `RateLimitConfig` implementa
`WebMvcConfigurer`, así que Spring lo instancia en cualquier slice `@WebMvcTest` aunque la clase no
lo importe, y con reglas vacías nadie lo había notado. Se resolvió con
`@TestPropertySource(properties = "aguavigia.rate-limit.reglas=")` en los dos slices afectados.

### REC-007 — Las ramas fusionadas se acumulan en GitHub porque falta activar el borrado automático

- **Fecha:** 2026-08-28 · **Estado:** Resuelta

**Resuelta el 2026-09-22:** `delete_branch_on_merge` activado en la configuración del repositorio.
Las ramas de Dependabot ya fusionadas en esta sesión se limpiaron solas al cerrarse sus PR.

Al auditar las ramas del repositorio se encontraron 4 ramas
remotas y 11 locales completamente fusionadas a `main` (0 commits propios cada una), incluida
`fix/consenso-desempate-2` con un worktree local aparte (`ctg-fix-consenso`) que también quedó
huérfano. Ninguna traía trabajo pendiente, pero nadie las había borrado tras fusionar sus PRs. Vale la
pena activar **"Automatically delete head branches"** en Settings → General → Pull Requests del repo
para que esto no se repita cada pocos sprints.

### REC-008 — El fuente de `index.css` está semi-minificado: el breakpoint móvil completo vive en una sola línea de 2.509 caracteres

- **Fecha:** 2026-08-30 · **Estado:** Resuelta

`frontend/src/index.css` tiene 3.702 líneas y 147 KB, pero 29 de esas líneas concentran 32,5 KB: la
más larga son 4.680 caracteres (`index.css:202`) y **todo el breakpoint móvil está en
`index.css:214`, en una sola línea de 2.509 caracteres**. No es el CSS compilado, es el fuente que
se versiona. Eso hace que cualquier ajuste de responsividad sea ilegible en el diff de un PR —
tocar una regla móvil marca la línea entera como cambiada, así que no se puede ver qué
cambió. Basta correr Prettier sobre el
archivo una vez; el riesgo es un diff enorme irrepetible, así que conviene hacerlo en un PR propio
que no mezcle ningún cambio de estilo.

**Resuelta:** el 2026-09-05 se formateó `frontend/src/index.css` con Prettier. Las líneas densas y el breakpoint móvil quedaron estructurados multilínea de forma legible y revisable sin alterar estilos funcionales. Pruebas E2E y build verificados en verde.

### REC-009 — 25 reglas usan `transition: all`, que anima también propiedades de layout y dispara reflow en cada hover

- **Fecha:** 2026-08-30 · **Estado:** Resuelta

Hay 25 `transition: all` repartidas entre `ModalReporte.css`, `ModalSuscripcion.css`,
`PanelVeedor.css`, `SeccionBitacora.css`, `SeccionEstadisticas.css` y `GooeyNav.css` (por ejemplo
`PanelVeedor.css:87`). `all` no distingue: si la regla cambia `padding`, `width` o `border-width`,
el navegador recalcula layout y repinta en cada hover, en vez de quedarse en la GPU como haría con
`transform` y `opacity`. Se nota sobre todo en el móvil de gama baja, que es el dispositivo del
usuario objetivo de esta plataforma. La corrección no es mecánica —hay que mirar qué propiedad
cambia de verdad en cada regla y nombrarla— así que conviene repartirla por componente y no
intentarla de una sola pasada.

**Resuelta:** el 2026-09-05 se reemplazaron todas las ocurrencias de `transition: all` a lo largo de los 6 archivos de componentes y en `index.css` por transiciones explícitas y aceleradas por hardware (`transform`, `opacity`, `background-color`, `border-color`, `box-shadow`), eliminando recalculos de layout involuntarios.

### REC-010 — `CLAUDE.md` sigue declarando "Sprint 0 · ANDAMIAJE, se prohíbe la funcionalidad" sobre un backend ya terminado

- **Fecha:** 2026-08-31 · **Estado:** Resuelta

`CLAUDE.md` §Estado actual dice *"Sprint 0 · Fase: ANDAMIAJE. Se permite estructura de proyecto,
configuración, infraestructura, tokens visuales y rutas vacías. Se prohíbe la funcionalidad: si el
código implementa un `RF`, no va en el Sprint 0"*. El repositorio contradice eso de forma frontal: el
`README` declara backend y bases de datos completos salvo RF041, con 601 pruebas en verde, y
`docs/gestion/registro-de-implementaciones.md` lista los PRs de M1 a M14 ya fusionados.

Importa porque ese archivo se declara a sí mismo fuente de verdad —*"Si algo de este archivo
contradice una suposición, gana este archivo"*— y lo lee el agente en cada sesión. Un agente que lo
obedezca al pie de la letra se negará a escribir la funcionalidad que se le pida, o preguntará
por cada caso de frontera de una fase que terminó hace sprints. Se paga en cada sesión.

Basta actualizar §Estado actual al sprint real y a su entregable pendiente. Conviene que lo confirme
yo, no el agente: es el estado del proyecto, no un detalle técnico.

**Resuelta:** el 2026-09-04 se actualizó §Estado actual de `CLAUDE.md` al estado verificado —Sprint 0
y 1 cerrados, Sprint 2 abierto, M1–M15 construidos, `ADR-009` ya no aplica— con las cifras medidas en
esa sesión (563 pruebas de backend, 95 de frontend) y una advertencia explícita de que la gestión de
sprints va por detrás del código (ver `REC-014`).

---

### REC-011 — Los 15 endpoints de M15 no tienen prueba de contrato, y `RNF022` la exige

- **Fecha:** 2026-09-04 · **Estado:** Resuelta

`RNF022` dice que el panel debe autorizar cada acción contra un permiso concreto, y declara como
verificación *«ArchUnit + pruebas de contrato por endpoint»*. Esas pruebas no existen para
`AdminUsuariosController` (7 endpoints), `CuentaPublicaController` (5) y `SegundoFactorController`
(3): `backend/src/test/.../api/` no tiene un solo archivo que los nombre, y ninguna prueba de la
suite toca las rutas `/api/veedor/usuarios`, `/api/cuentas/*` ni `/api/veedor/segundo-factor/*`.

Los **casos de uso** sí están probados (`GestionDeCuentasDelPanelTest`, `AdministrarCuentaServiceTest`,
`AltaYRecuperacionDeCuentaTest`, `AutenticarUsuarioServiceTest`). Lo que falta es la capa web: nada
verifica que un `OBSERVADOR` reciba 403 al llamar a un endpoint de administración, ni que un token
de alcance restringido no pueda usarse fuera del alta del segundo factor. Justo la superficie donde
un permiso mal cableado no se nota hasta que alguien lo aprovecha.

Es además la parte más nueva del sistema (`ADR-039`) y la de mayor daño si falla: son los endpoints
que crean, aprueban y suspenden cuentas.

**Resuelta:** el 2026-09-05 se implementaron los slices web `@WebMvcTest` para los tres controladores: `AdminUsuariosControllerTest`, `CuentaPublicaControllerTest` y `SegundoFactorControllerTest`, cubriendo los 15 endpoints de M15, verificando autorización, respuestas 401/403/400 con RFC 7807 y payloads válidos (14 pruebas nuevas, 108 pruebas totales en `*ControllerTest`).

---

### REC-012 — Las respuestas 401 y 403 de la cadena de seguridad no salen en RFC 7807

- **Fecha:** 2026-09-04 · **Estado:** Resuelta

`CLAUDE.md` fija que los errores de API van en formato RFC 7807 centralizados en un
`@RestControllerAdvice`, y `ManejadorGlobalDeErrores` lo cumple para todo lo que pasa por un
controlador. Pero `SecurityConfig` resuelve sus dos casos con `response.sendError(...)`, que produce
la página de error del contenedor, no un `ProblemDetail`.

Son precisamente los dos casos más frecuentes que ve un cliente: entrar sin token y entrar sin
permiso. El frontend recibe ahí una forma distinta a la de cualquier otro error, y la única prueba
que los cubre (`debeRechazarUnaRutaDeVeedorSinTokenCon401`) comprueba el código de estado, no el
cuerpo, así que la divergencia no salta.

Se arregla escribiendo el `ProblemDetail` desde el `authenticationEntryPoint` y el
`accessDeniedHandler`, con su `type` propio, y afirmando el `content-type` en la prueba.

**Resuelta:** el 2026-09-05 se configuraron `authenticationEntryPoint` y `accessDeniedHandler` en `SecurityConfig.java` para serializar un `ProblemDetail` con `application/problem+json`, `type` correspondiente (`no-autenticado` / `acceso-denegado`) e `instance`. Verificado con aserciones en `VeedorAuthControllerTest`, `AdminUsuariosControllerTest` y `SegundoFactorControllerTest`.

---

### REC-013 — El allowlist de gitleaks exceptúa un archivo entero, no un secreto concreto

- **Fecha:** 2026-09-04 · **Estado:** Resuelta

**Resuelta el 2026-09-22:** `paths` cambiado por un `regexes` sobre el valor exacto de
`JWT_SECRET`, mismo criterio que ya usaba la semilla TOTP. Verificado con `gitleaks` real (Docker):
el valor legítimo sigue sin dar hallazgo, y un secreto distinto pegado en el mismo archivo (probado
y revertido) sí se detecta — antes no se habría detectado.

`ADR-031` decidió, con razón, no borrar la clave de desarrollo local de
`docs/ingenieria/entorno-local.md`. Pero el allowlist de `.gitleaks.toml` está escrito por **ruta**:

```toml
paths = ['''docs/ingenieria/entorno-local\.md''']
```

Eso apaga el escaneo para el archivo completo y para siempre. Cualquier secreto que alguien escriba
ahí en el futuro —incluido uno de producción, y ese archivo es justo donde alguien lo pegaría por
error— pasa el CI en verde y en silencio.

Acotarlo al secreto concreto en vez del archivo: `regexTarget` con el valor de la clave de
desarrollo, o `stopwords`, de forma que el archivo siga escaneándose para todo lo demás.

---

### REC-014 — `sprint-2.md` lleva abierto desde el 2026-08-09 mientras el repositorio ya entregó M10–M15

- **Fecha:** 2026-09-04 · **Estado:** Resuelta

**Resuelta:** el 2026-09-21 el dueño decidió cerrar el Sprint 2. Se cerró con la verificación de ese día (§4 de `sprint-2.md`); no se abrió un Sprint 3 todavía.

`docs/gestion/sprint-2.md` («Reporte ciudadano y consenso») sigue sin fecha de cierre desde el
2026-08-09, casi un mes. En ese intervalo el repositorio entregó M10 a M15 —evidencia multimedia,
validación comunitaria, Open311, IoT, alertas push y el modelo completo de cuentas y permisos— y
registró de `ADR-025` a `ADR-042`.

No es un detalle de forma: la Sala de control se genera de estos archivos, así que lo que muestra no
refleja lo que se hizo. Cerrar el Sprint 2 con
su entregable demostrado y abrir los siguientes es decisión mía, no del agente
— pero cuanto más se tarde, más caro es reconstruir qué pasó en cada uno.

---

### REC-015 — Nada impide que `index.css` y `tipos-dominio.ts` vuelvan a discrepar en los colores de estado

- **Fecha:** 2026-09-04 · **Estado:** Resuelta

`ADR-042` unificó los cuatro colores de estado, que estaban en seis sitios con cinco valores
distintos, y dejó dos fuentes que **deben** moverse juntas: `--color-estado-*` en `index.css` (pinta
la leyenda) y `COLOR_POR_ESTADO` en `tipos-dominio.ts` (pinta los polígonos del mapa). Está
documentado en `DESIGN.md` §2 y en el javadoc del propio `COLOR_POR_ESTADO`, pero **nada lo
verifica**: quien cambie uno y olvide el otro vuelve a partir el mapa de su leyenda, y la build sigue
en verde.

Una prueba corta lo cerraría: leer los cuatro valores de `index.css` y compararlos con
`COLOR_POR_ESTADO`, y de paso comprobar que cada uno alcanza 4.5:1 sobre la superficie de su tema
(`RNF012`). Hoy el contraste tampoco lo verifica nada — se midió a mano.

**Nota del 2026-09-21:** el frontend y esta prueba se retiraron con `ADR-048` (siguen en la etiqueta `pre-retiro-frontend`); quien rehaga el frontend debe reponer la comprobación.

**Resuelta:** el 2026-09-05 se implementó `frontend/src/types/colores-estado.test.ts`. La prueba extrae por regex los tokens `--color-estado-*` de `index.css`, valida la paridad exacta hex con `COLOR_POR_ESTADO` para los cuatro estados (NORMAL, BAJA_PRESION, SUSPENDIDO, RESTABLECIMIENTO) y calcula el ratio de contraste WCAG AA relativo (≥ 4.5:1) contra las superficies clara (`#fbfdfc`) y oscura (`#0c2830`).

---

### REC-016 — La regla `Read(**/*secret*)` de `.claude/settings.json` bloquea `secret-scan.yml`, el único archivo de CI que hay que corregir para `BUG-089`

- **Fecha:** 2026-09-21 · **Estado:** Resuelta

**Resuelta el 2026-09-22, delegado por el dueño:** `BUG-089` se cerró renombrando el workflow (opción b, la parte que no tocaba `.claude/settings.json`). La regla en sí quedó acotada después, también por delegación: `Read(**/*secret*)` era una subcadena sin anclar y atrapaba código fuente legítimo (`SecretoTotp.java`, `GeneradorSecretosPort.java`, `ValidacionDeSecretosProd.java`) además del propio workflow de escaneo. Se reemplazó por cuatro reglas más precisas: `**/secrets/**`, `**/*secrets.*`, `**/*.secret`, `**/*.secrets` — protegen una carpeta `secrets/`, un archivo `*secrets.json`/`.env`/`.yml` o con extensión `.secret`/`.secrets`, sin atrapar nombres que solo mencionan la palabra. Verificado: `SecretoTotp.java` ya se puede leer. **El agente no pudo agregar el comentario explicativo en `.claude/settings.json`** — el harness bloqueó esa segunda edición como "auto-modificación" aunque la primera (la regla en sí) sí pasó; quedó sin documentar en el propio archivo, solo aquí.

La regla `deny` `Read(**/*secret*)` existía para que el agente no abra archivos de credenciales, pero coincidía por nombre con
`.github/workflows/secret-scan.yml`, que no guarda ningún secreto (solo llama a `gitleaks`). Efecto: el agente no podía leerlo ni editarlo,
y el arreglo de `BUG-089` (tres líneas de `permissions`) llevó dos sesiones sin poder aplicarse. Ya se había anotado en la bitácora del 2026-09-21
y volvió a ocurrir.

Dos salidas, ambas del dueño: (a) aplicar tú mismo el bloque de `BUG-089`, o (b) acotar la regla para que no atrape ese archivo —por ejemplo
renombrar el workflow a `escaneo-secretos.yml`, en línea con `backend-ci.yml` y `despliegue-ci.yml`, o cambiar el patrón a
`**/secrets/**` y `**/*.secret`—. El agente no debe rodearla por su cuenta (`cat`, `sed` o renombrar), porque la regla es tuya.

---

### REC-017 — Los Sprints 3 a 6 de la hoja de ruta siguen escritos como si no se hubiera construido nada, y dos de sus entregables chocan con `ADR-048` y `ADR-057`

- **Fecha:** 2026-09-21 · **Estado:** Resuelta

**Resuelta el 2026-09-22, delegado por el dueño:** se escribieron `sprint-3.md`, `sprint-4.md` y `sprint-5.md` retroactivamente, cerrados con la evidencia que ya existía en `matriz-trazabilidad.md` y el código (sin inventar una ceremonia que no ocurrió — mismo criterio que `sprint-2.md` §4). El Sprint 5 quedó redefinido a solo cobertura de backend (`RNF017`, ya exigida en cada build); su parte de interfaz (WCAG, PWA, E2E) es alcance retirado (`ADR-048`), no un entregable pendiente. Al reverificar el Sprint 4 contra código se encontró `BUG-091`: `RNF006` estaba marcado ✅ en la matriz sin que exista la cola muerta que pide el requisito.

**Queda sin resolver, y es decisión del dueño, no del agente:** el Sprint 6 no se cerró ni se abrió — su entregable se redefinió en `docs/gestion/README.md` (demo local con `docker compose`, sin "desplegada", `ADR-057`), pero qué cuenta como "demo" para un proyecto sin frontend propio en el repositorio es una pregunta genuina. Se separó en `REC-018` para que siga visible como pendiente.

`docs/gestion/README.md` define siete sprints, pero solo existen `sprint-0.md`, `sprint-1.md` y `sprint-2.md`. El backend ya entrega lo que
los Sprints 3 (veedor y alertas) y 4 (ingesta e índice) prometían, sin que ningún archivo lo diga; por eso la Sala de control no puede
mostrarlo. Además, el entregable del Sprint 5 (WCAG AA, PWA, E2E) depende del frontend que `ADR-048` retiró, y el del Sprint 6 pedía una demo
desplegada, que `ADR-057` descartó. En esta sesión solo se ajustó el texto de esos dos entregables para que no contradigan los ADR.

---

### REC-018 — Qué cuenta como "demo" del Sprint 6 sin frontend propio en el repositorio no está decidido

- **Fecha:** 2026-09-22 · **Estado:** Resuelta el 2026-09-24 — el dueño aceptó que la demo se haga contra el backend (Swagger y `scripts/verificar-flujos.mjs`), sin esperar al frontend; definición en `docs/gestion/sprint-6.md`. El histórico se sembró y se verificó ese mismo día

El Sprint 6 («Entrega final») promete una «demo desplegada, dataset histórico cargado». `ADR-057` ya descartó
«desplegada» (el proyecto corre en local), y `docs/gestion/README.md` quedó ajustado a «demo corriendo en local
con `docker compose`». Pero el repositorio no tiene frontend hoy (`ADR-048`; se rehace en otras ramas) — así que
falta decidir con qué se hace la demo cuando llegue el momento: ¿espera a que el frontend nuevo exista y se
junte todo, como dice `ADR-048`, o se acepta una demo basada en Swagger/`curl` contra el backend mientras tanto?

Además, `scripts/sembrar-historico-cortes.mjs` existe (mayo–julio 2026, cortes y reportes ciudadanos históricos)
pero no hay evidencia registrada de que se haya corrido y verificado contra un entorno real — no se marcó como
entregado por esa razón, no por un descuido.

No se resuelve aquí a propósito: es una decisión de alcance del proyecto, no una que convenga tomar en automático.
