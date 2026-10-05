# Cambios del backend que el frontend debe adaptar

El backend manda y el frontend se adapta (decisión del dueño, 2026-09-30). Cada fase añade aquí su sección: qué rompe, qué es
nuevo y qué debe cambiar el frontend. Sin periodo de compatibilidad: lo viejo se elimina.

## F0 — Base

### Rompe

| Cambio | Dónde | Qué adapta el frontend |
|---|---|---|
| `POST /api/reportes/{id}/foto` **rechaza WebP** con `400` (antes lo guardaba sin limpiar el EXIF, con la ubicación del teléfono) | `docs/api/reportes.md` | Quitar `image/webp` de `TIPOS_FOTO` (`frontend/src/api/reportes.ts`) y del texto de ayuda de `Reporte.tsx` («JPEG, PNG o WebP…» → «JPEG o PNG…») |

### Sin efecto en el contrato

- Los cortes que crea un boletín de Acuacar dejan de bloquear el retorno a «con servicio» cuando su ventana prometida vence.
  Cambia el estado que ve el mapa, no la forma de la API.

## F1 — Núcleo de estado (estado de un barrio, cierres por barrio, disputas)

El estado de un barrio lo decide **un solo resolutor** a partir de lo que afirma cada fuente (boletín de Acuacar, prensa
aprobada, corte o cierre del veedor, quórum de vecinos, sensores). Ya no hay procesos que se pisen: la forma del contrato
cambia porque ahora el barrio explica de dónde sale su estado.

### Rompe

| Cambio | Dónde | Qué adapta el frontend |
|---|---|---|
| `CorteRespuesta` **ya no trae `finReal`**. Trae `cierres[]` (`sectorId`, `hora`, `fuente`, `provisional`): un corte agrupa varios barrios y se restablece barrio por barrio | `GET/POST/PATCH /api/veedor/cortes…`, `GET /api/sectores/{id}/cortes` | Dibujar el cierre de cada barrio. Un corte `RESTABLECIDO` tiene todos sus barrios cerrados; la «hora real» del corte es la del último cierre. Un cierre `provisional: true` lo sostienen solo los vecinos (o, en teoría, sensores) y está pendiente de que el veedor lo confirme |
| `CorteRespuesta.estado` gana **`EXPIRADO`** (nadie confirmó el restablecimiento a tiempo; no cuenta en el Índice) y **`ANULADO`** (se publicó por error) | ídem | Tratarlos como cortes sin hora real. `ANULADO` trae `motivoAnulacion` |
| `GET /api/bitacora`: `tipo` gana `CORTE_EXPIRADO`, `CORTE_ANULADO` (también para un boletín anulado), `RESTABLECIMIENTO_POR_VECINOS`, `ESTADO_EN_DISPUTA`, `CONSENSO_REVERTIDO` | `docs/api/bitacora-estadisticas-cumplimiento.md` | Textos e iconos por tipo nuevo |
| `OrigenEstado` (en `origen` de sectores, `fuente` de eventos y de cierres) tiene 5 valores: `ACUACAR`, `PRENSA`, `VEEDOR`, `VECINOS`, `SENSOR`. **`SENSOR` existe en el contrato pero no se produce**: el proyecto no usa sensores físicos | varios | No asumir un conjunto cerrado de tres; no hace falta diseñar nada para `SENSOR` |
| El umbral de vecinos por barrio pasa a `clamp(ceil(población × 0,001), 3, 15)` | `respaldo.umbral` | Ninguno: es el denominador de «11 de 12»; ya no hay umbrales de 48 |

### Nuevo

- **`GET /api/sectores` y `/{id}`** ganan, siempre presentes: `origen`, `ventanaPrometida {inicio, fin}`,
  `restablecimientoPorConfirmar`, `enDisputa`, `reportesEnContra`, `respaldo {vecinos, umbral}`. Son nulos o falsos
  cuando el barrio no tiene estado. Insignias sugeridas: «Acuacar prometió hasta…», «por confirmar», «en disputa»,
  «11 de 12 vecinos». **El color no cambia por una disputa**: solo se marca.
- **`GET /api/bitacora`**: cada evento gana `fuente` (quién lo sostiene) y `respaldo {vecinos, umbral}` cuando nace de un quórum.
- **Panel del veedor** (`VER_PANEL` para leer, `GESTIONAR_CORTES` / `REVISAR_INGESTA` para actuar):
  - `GET /api/veedor/disputas`: barrios en disputa, los más contradichos primero (misma forma que `SectorRespuesta`).
  - `GET /api/veedor/cortes/vencidos`: cortes con la promesa vencida sin cierre, y los que tienen un cierre provisional.
  - `PATCH /api/veedor/cortes/{id}/sectores/{sectorId}/cierre` `{horaReal}`: cierra **un** barrio.
  - `PATCH /api/veedor/cortes/{id}/sectores/{sectorId}/confirmacion` `{horaReal}`: confirma —o corrige la hora de— un cierre provisional.
  - `PATCH /api/veedor/cortes/{id}/anulacion` `{motivo}` y `PATCH /api/veedor/ingesta/propuestas/{id}/anulacion` `{motivo}`:
    deshacen un corte o un boletín publicados por error. Quedan con su motivo, en la bitácora (como corrección) y en la auditoría.
  - `PATCH /api/veedor/cortes/{id}/cierre` **se conserva** como atajo: cierra de una vez todos los barrios pendientes.
  - `POST /api/veedor/cortes` acepta `caducaEn` (opcional): el corte del veedor es el override y deja de afirmar nada a esa hora.
- Los sensores de presión (`POST /api/iot/presion`) quedan construidos pero **inactivos**: el proyecto no los usa en físico y la ruta responde `503` mientras no se configure `X-IoT-Key`. No hay nada que mostrar ni que adaptar.

### Errores

`409` al cerrar o anular lo que ya estaba cerrado o anulado; `400` si falta `motivo` o `horaReal`; `404` si el corte o la propuesta no existen.

## F2 — Identidad (dispositivo del servidor y vecino registrado)

La identidad de quien reporta ya no la inventa el cliente: la pone el servidor. Hay dos caminos: un **token de dispositivo**
(sin cuenta, como hasta ahora) o una **cuenta de vecino** (correo verificado y barrio). El servidor anota cuánto respalda cada
reporte (`verificacion`) y el quórum de un barrio exige que parte del sustento esté verificado y venga de más de una red.

### Rompe

| Cambio | Dónde | Qué adapta el frontend |
|---|---|---|
| **Se elimina `huella`** de `POST /api/reportes` y de `POST /api/reportes/{id}/confirmar`. Si llega, se ignora | `docs/api/reportes.md` | Quitar la huella local. Sin identidad el servidor responde `401 dispositivo-invalido`, así que **hoy el formulario de reporte falla hasta que mande `X-Dispositivo`** |
| `POST /api/reportes/{id}/confirmar` **ya no lleva cuerpo** (antes `{huella}`) | ídem | Enviar la petición vacía con la cabecera de identidad |
| Reportar y confirmar exigen identidad: cabecera **`X-Dispositivo`** (token de `POST /api/dispositivos`) o `Authorization: Bearer` de una sesión de vecino. Si hay las dos, manda la cuenta | ídem | Pedir el token al primer uso, guardarlo y enviarlo siempre; ante `401` con `type` `dispositivo-invalido`, pedir otro y reintentar **una** vez |
| `SolicitudReporte` gana `precisionMetros` (opcional) | `POST /api/reportes` | Enviar `coords.accuracy` del navegador junto con la coordenada. Sin `precisionMetros` la ubicación **no verifica** el reporte |
| `ReporteRespuesta` y la cola de moderación (`GET /api/veedor/reportes/pendientes`) ganan `verificacion`: `CUENTA_VERIFICADA`, `UBICACION_VERIFICADA` o `NINGUNA` | ídem | Mostrarlo al veedor como señal de cuánto respalda el servidor el reporte. No es un voto con peso: es una marca |
| La coordenada que se guarda del reporte es una **aproximación de unos 110 m** (3 decimales), no la que se envió | `coordenada` en reportes y cola de moderación | Ninguno; no prometer precisión de calle |
| El cupo por identidad es de **3 reportes por barrio cada 30 min** para un dispositivo y **5** para un vecino | `429 limite-reportes-excedido` | Ninguno de forma; los mensajes de espera pueden distinguir los dos |

### Nuevo

- **`POST /api/dispositivos`** → `201 {token}`. Público. Cada llamada crea una identidad distinta: se pide una vez y se guarda
  (`localStorage`), no en cada reporte. Máximo **10 por hora por IP** (`429 limite-de-peticiones-excedido`, con `Retry-After`).
  `X-Dispositivo` ya está permitida por CORS.
- **Vecino registrado** (rol `VECINO`; su sesión sirve en `/api/vecino/**` y, del panel, solo en `GET /api/veedor/yo` y `POST /api/veedor/sesion/cierre`, que muestran o cierran lo propio):
  - `POST /api/cuentas/vecino` `{correo, nombre, barrioId, consentimiento{privacidad, avisos}}` → `202` siempre, exista o no el
    correo (no revela qué correos tienen cuenta). **No lleva `clave`**: si un cliente viejo la manda, se ignora. `privacidad` debe
    ser `true`; `avisos` es una casilla aparte y por defecto falsa. `barrioId` es el slug de `GET /api/sectores`. `400` si algo
    no cumple.
  - **Elegir la clave y activar la cuenta:** el correo trae un enlace que abre la misma pantalla de las invitaciones
    (`GET /api/cuentas/enlaces/invitacion?token=…`, formulario de clave). Al enviarla (`POST /api/cuentas/invitacion`
    `{token, clave}` o el formulario HTML) la cuenta queda **ACTIVA sin aprobación de nadie**, con clave de 12 caracteres o más.
    Así nadie puede registrar el correo de otra persona con una clave suya. Quien perdió el correo lo pide de nuevo con
    `POST /api/cuentas/verificacion/reenvio` `{correo}` (cada 2 minutos como mucho). Mientras la cuenta no tenga clave,
    `POST /api/vecino/sesion` responde `401`, igual que con una clave mala.
  - `POST /api/vecino/sesion` `{correo, clave}` → `200 {token, usuarioId, nombre, correo, permisos}` (8 h). Una cuenta del panel
    no entra por aquí ni una de vecino por `/api/veedor/sesion`: ambos casos dan la misma `401` que una clave mala. `403` si la
    cuenta está suspendida; `423` por bloqueo de la cuenta; `429` por IP.
  - `GET /api/vecino/yo` → perfil (`barrioId`, `barrioVerificado`, `barrioVerificadoEn`, `recibeAvisos`, `consentimientos[]`).
  - `PATCH /api/vecino/perfil` → cambia nombre, barrio y la casilla de avisos; solo se aplica lo que viene. Cambiar de barrio anula la verificación anterior. `409 conflicto-de-estado` si la cuenta cambió mientras se guardaba (una suspensión, por ejemplo): reintentar.
  - `POST /api/vecino/sesion/cierre` → `204`. Revoca **todas** las sesiones vivas de la cuenta, no solo la del navegador.
  - `POST /api/vecino/verificacion-barrio` `{coordenada, precisionMetros}` → `200` con el perfil. **La coordenada se usa y se
    descarta: ni se guarda ni se audita.** Máximo **3 intentos por día** (`429 limite-de-peticiones-excedido` con `Retry-After`);
    una lectura imprecisa **no gasta intento**. Si el vecino ya estaba verificado no hace nada.
- Los reportes de un vecino con el barrio verificado y reportando en ese mismo barrio salen `CUENTA_VERIFICADA`; los de un
  dispositivo con ubicación precisa (≤ 200 m) dentro del barrio salen `UBICACION_VERIFICADA`; el resto, `NINGUNA`. **Reportar sin
  cuenta ni ubicación sigue siendo posible**: pesa igual en el conteo, pero el quórum necesita que al menos un tercio del sustento
  (mínimo 1) esté verificado y que venga de al menos 2 redes distintas.

### Errores

| `type` | Código | Cuándo | Qué hace el frontend |
|---|---|---|---|
| `dispositivo-invalido` | 401 | Reportar o confirmar sin `X-Dispositivo` válido (falta, no lo firmó este servidor o el dispositivo ya no existe) | Pedir otro con `POST /api/dispositivos` y reintentar una vez. **No** cerrar la sesión de un vecino por este error |
| `ubicacion-imprecisa` | 422 | `precisionMetros` peor que 200 m en la verificación de barrio | Pedir GPS y reintentar; no cuenta como intento |
| `ubicacion-fuera-del-barrio` | 422 | La coordenada no cae en el barrio declarado | Ofrecer corregir el barrio (`PATCH /api/vecino/perfil`) o reintentar desde casa |
| `conflicto-de-estado` | 409 | `PATCH /api/vecino/perfil` o `POST /api/vecino/verificacion-barrio` con la cuenta cambiada por otro entre la lectura y el guardado | Volver a pedir el perfil y repetir; no pierde nada |
| `limite-de-peticiones-excedido` | 429 | Tope por IP (`/api/dispositivos`, `/api/vecino/sesion`, `/api/cuentas/**`) o agotar los 3 intentos diarios de verificación | Esperar `Retry-After` segundos |

Los límites y la lista completa de errores están en `errores-y-limites.md`.

## F3 — Fotos, ingesta y «¿ya volvió el agua?»

Las fotos dejan de ser un directorio público: la sube solo quien creó el reporte, y se ve cuando el reporte se aprueba. La ingesta
de Acuacar ya no publica todo sola, y llega un enlace de un toque para confirmar que volvió el agua.

### Rompe

| Cambio | Qué adapta el frontend |
|---|---|
| **`/fotos/**` desaparece.** Las fotos salen por `GET /api/fotos/{nombre}`. `fotoUrl` ya viene con la ruta nueva, también en reportes viejos | Quitar `/fotos` del proxy de Vite (el proxy de `/api` ya cubre la ruta nueva) y no armar rutas a mano: usar `fotoUrl` |
| **`POST /api/reportes/{id}/foto` exige la cabecera `X-Subida`** con el `subidaToken` que devuelve `POST /api/reportes`. Sin él, `403 subida-no-autorizada` | Guardar `subidaToken` al crear el reporte y mandarlo al subir la foto. Es de un solo uso y vence a los 10 min; no hay forma de pedirlo otra vez. `X-Subida` ya está permitida por CORS |
| **WebP se rechaza con `415 formato-no-permitido`** (antes `400`) | No ofrecer WebP en el selector (`accept="image/jpeg,image/png"`) y reaccionar por el `type` |
| `GET /api/fotos/{nombre}` responde `404` mientras el reporte no esté aprobado | No pidas la imagen si `fotoEstado` es `EN_REVISION`: muestra «en revisión» |
| `PropuestaIngestaRespuesta` gana `motivoDeRevision` | Mostrarlo a quien revisa la cola de ingesta |

### Nuevo

- `ReporteRespuesta` gana **`fotoEstado`** (`SIN_FOTO`, `EN_REVISION`, `PUBLICA`, `DESCARTADA`) y, solo al crear, **`subidaToken`**.
- La cola de moderación (`ReporteModeracionRespuesta`) gana `fotoEstado` y `fotoUrl` (ruta del panel `/api/veedor/fotos/{nombre}`, que exige sesión con `VER_PANEL`: pídela con `fetch` y la cabecera `Authorization`, no con un `<img src>`).
- `PATCH /api/veedor/reportes/{id}/foto/descartar` (`MODERAR_REPORTES`): retira solo la foto.
- **`POST /api/sectores/{sectorId}/restablecimiento?token=…`** → `201` con el reporte. Es lo que llama la pantalla «¿ya volvió el agua?» a la que lleva el
  correo de aviso: `{urlFrontend}/sectores/{id}/restablecimiento?token=…`. **Hay que construir esa pantalla** (un botón; no votes al cargar la página).
  Detalle en `correos-y-enlaces.md`.
- El correo de aviso de un barrio sin servicio o con presión baja trae el enlace anterior.

### Errores

| `type` | Código | Cuándo | Qué hace el frontend |
|---|---|---|---|
| `subida-no-autorizada` | 403 | Falta `X-Subida`, ya se usó, venció o es de otro reporte (también si el reporte no existe) | Decir que la foto ya no se puede subir; no reintentar |
| `formato-no-permitido` | 415 | La foto no es JPEG ni PNG | Pedir otra imagen |
| `enlace-invalido` | 403 | El enlace de «¿ya volvió?» venció, es de otro barrio o la suscripción ya no está confirmada | Mostrar «este enlace ya no sirve» y llevar al barrio |

### Lo que cambia sin tocar el contrato

- Acuacar ya no publica todo sola: un boletín poco fiable o sin sentido espera al veedor (con `motivoDeRevision`). Un boletín con
  **varias zonas** (cada una con su horario) genera un corte por zona.
- Los boletines de **histórico** (ventana terminada hace más de 72 h) no mueven el mapa: quedan como corte `EXPIRADO` y evento `CORTE_EXPIRADO` en la bitácora, con la fecha del hecho.
- Un boletín de «servicio restablecido» **de hace más de 72 h** ya no fija `CON_SERVICIO`: el barrio vuelve a «sin datos».
- Un aviso de aplazamiento o cancelación se reconoce y **no** se publica como corte nuevo (hoy se registra en el log; un flujo de anulación desde la cola llega con la simulación).

## F4 — Datos, modo del sistema y calidad del Índice

Un solo `docker compose up` deja la base lista, y la API ahora dice qué es real y qué es sintético. No hay nada que **rompa**; todo
lo siguiente es nuevo o añade campos.

### Nuevo

- **`GET /api/sistema/modo`** → `{ "modo": "REAL" | "SIMULACION", "cuentasSinteticas": 30000 }`. Público, sin sesión. Pídelo al abrir
  la aplicación (se recuerda un minuto en el servidor).
  - `modo`: **muestra un banner permanente si es `SIMULACION`**. Una simulación nunca se presenta como real. En la instancia real es
    `REAL` y no hace falta mostrar nada.
  - `cuentasSinteticas`: cuántas cuentas de vecino creó el propio sistema para probar el volumen. **No son personas.** Si la
    interfaz habla de ellas, la frase exacta es «cuentas sintéticas generadas por el sistema con las reglas de alta de un vecino»;
    nunca «30 000 personas se registraron» ni se cuentan como adopción. Con 0 no hay nada que decir.
- **`GET /api/cumplimiento/calidad?sectorId=`** → `{ cierresMedidos, cierresProvisionales, porcentajeProvisional, cortesSinCierreConfirmado,
  cortesAnulados }`. **Responde siempre**, incluso cuando `GET /api/cumplimiento` da `400` por no haber un solo cierre: es lo que permite
  mostrar «sin datos suficientes» **con cifras** («12 cortes vencidos sin cierre confirmado») en vez de una pantalla vacía.
- `IndiceCumplimientoRespuesta` (global, por sector y por corte) gana tres campos, siempre presentes:
  - `porcentajeProvisional` (0 a 100): de los cierres que sostienen el Índice, cuántos solo los sostienen vecinos o sensores y un veedor o
    un boletín aún puede corregir. **Publícalo junto al porcentaje**: el número es tan sólido como esto.
  - `cortesSinCierreConfirmado`: cortes cuya ventana prometida ya terminó y en los que algún barrio no tiene cierre (incluye los que
    expiraron). No cuentan a favor ni en contra de Acuacar: se declaran, no se esconden.
  - `cortesAnulados`: cortes publicados por error y retirados; no entran al Índice.
- `GET /api/veedor/reportes/pendientes`: cada reporte gana **`senalRed`** (booleano): verdadero si viene de una red (resumen diario de la IP, que
  no sale por la API) que ya envió una ráfaga de reportes a ese barrio (por defecto 5 en 30 minutos). **No bloquea nada**: es dónde mirar
  primero. Es una señal para el veedor, no una acusación: una sala o una antena móvil comparten red.
- `GET /api/veedor/usuarios` (`UsuarioRespuesta`): gana **`sintetica`** (booleano). Con 30 000 cuentas sintéticas en la base, el listado del
  administrador las incluye (las más nuevas primero: una cuenta que se registre de verdad aparece arriba); `sintetica: true` permite
  distinguirlas y ocultarlas. Ninguna puede iniciar sesión: su correo es de un dominio reservado (`.invalid`) y nadie conoce su clave.

### Cambia sin tocar la forma

- **El Índice se mide por par corte-barrio**, no por corte: un corte que agrupa veinte barrios y los restablece a horas distintas aporta
  veinte mediciones, cada una con su duración real (y la prometida del corte). `cantidadCortes` de la serie mensual cuenta esos pares. Los
  cierres de los cortes anteriores a F1, que solo traían `finReal`, se leen igual.
- La cola de moderación sigue siendo la misma lista paginada; solo suma `senalRed`.
- Lo que se siembra al arrancar ya no incluye reportes, cortes ni estados de barrio inventados: el mapa arranca en «sin datos» salvo los
  barrios con un boletín real de Acuacar vigente. La interfaz no debe suponer que hay datos de ejemplo.
