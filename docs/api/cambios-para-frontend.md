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
  - `POST /api/cuentas/vecino` `{correo, nombre, clave, barrioId, consentimiento{privacidad, avisos}}` → `202` siempre, exista o
    no el correo (no revela qué correos tienen cuenta). `privacidad` debe ser `true`; `avisos` es una casilla aparte y por defecto
    falsa. `barrioId` es el slug de `GET /api/sectores`. Clave de 12 caracteres o más. `400` si algo no cumple.
  - **Verificar el correo:** el enlace del correo abre la página de cortesía (mecanismo de `correos-y-enlaces.md`) y, para un
    vecino, la cuenta queda **ACTIVA sin aprobación de nadie**. La página dice «ya puedes iniciar sesión».
  - `POST /api/vecino/sesion` `{correo, clave}` → `200 {token, usuarioId, nombre, correo, permisos}` (8 h). Una cuenta del panel
    no entra por aquí ni una de vecino por `/api/veedor/sesion`: ambos casos dan la misma `401` que una clave mala. `403` si la
    cuenta no está activa (correo sin confirmar) o está suspendida; `423` por bloqueo de la cuenta; `429` por IP.
  - `GET /api/vecino/yo` → perfil (`barrioId`, `barrioVerificado`, `barrioVerificadoEn`, `recibeAvisos`, `consentimientos[]`).
  - `PATCH /api/vecino/perfil` → cambia nombre, barrio y la casilla de avisos; solo se aplica lo que viene. Cambiar de barrio anula la verificación anterior.
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
| `limite-de-peticiones-excedido` | 429 | Tope por IP (`/api/dispositivos`, `/api/vecino/sesion`, `/api/cuentas/**`) o agotar los 3 intentos diarios de verificación | Esperar `Retry-After` segundos |

Los límites y la lista completa de errores están en `errores-y-limites.md`.
