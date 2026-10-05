# Errores y límites

Todo error de la API es **RFC 7807** (`Content-Type: application/problem+json`). Un cliente solo necesita
un manejador de errores.

## Forma de un error

```json
{
  "type": "https://aguavigia.example/errores/limite-reportes-excedido",
  "title": "Límite de reportes excedido",
  "status": 429,
  "detail": "Ya reportaste 3 veces en 'bocagrande' en los últimos 30 minutos. Espera antes de volver a reportar.",
  "instance": "/api/reportes"
}
```

| Campo | Uso |
|---|---|
| `type` | **Identifica el error. Reacciona por este campo**, no por `title` ni `detail`, que pueden cambiar de redacción. |
| `status` | El código HTTP. |
| `title` | Resumen corto, en español. |
| `detail` | Explicación para la persona, en español. **Seguro de mostrar** en la interfaz. |
| `instance` | La ruta pedida. Viene en **todos** los errores (comprobado en 400, 401, 404 y 503). |

Propiedades extra según el error:

| Propiedad | Aparece en | Contenido |
|---|---|---|
| `errores` | `400` de validación | Lista de textos `"campo: mensaje"`, uno por campo inválido. |
| `estado` | `403 cuenta-no-habilitada` | El estado de la cuenta (`PENDIENTE_APROBACION`, `SUSPENDIDA`…). |
| `segundosRestantes` | `423 cuenta-bloqueada` | Cuánto falta para poder reintentar. |
| `metodosPermitidos` | `405` | Los verbos que sí acepta la ruta (también en la cabecera `Allow`). |
| `tiposSoportados` | `415` y `406` | Los `Content-Type` que sí acepta la ruta (415) o los formatos que produce (406). |

## Catálogo de tipos

`type` es siempre `https://aguavigia.example/errores/<slug>`. Es un identificador estable, **no una URL que
se pueda visitar**.

| Slug | Código | Significa |
|---|---|---|
| `peticion-invalida` | 400 | Datos mal formados, campo inválido, JSON ilegible o valor fuera de rango. |
| `credencial-invalida` | 401 | Correo o clave incorrectos (del panel o de un vecino), o clave de sensor IoT incorrecta. |
| `dispositivo-invalido` | 401 | Reportar o confirmar sin un `X-Dispositivo` válido: falta, no lo firmó este servidor o el dispositivo ya no existe. **Pedir otro con `POST /api/dispositivos` y reintentar una vez.** No cierra la sesión de un vecino. |
| `segundo-factor-requerido` | 401 | La clave era correcta pero falta el código TOTP. **Reintentar con `codigoTotp`.** |
| `sesion-sin-cuenta` | 401 | El token es válido pero su cuenta ya no existe. **Cerrar sesión.** |
| *(sin type propio)* | 401 | Sin token, token inválido, caducado o revocado. **Cerrar sesión.** |
| `acceso-denegado` | 403 | La sesión no tiene el permiso que exige la operación. |
| `cuenta-no-habilitada` | 403 | La cuenta no puede iniciar sesión. Ver la propiedad `estado`. |
| `enlace-invalido` | 403 | El enlace de «¿ya volvió el agua?» venció, es de otro barrio o su suscripción ya no recibe avisos. |
| `subida-no-autorizada` | 403 | Subir una foto sin `X-Subida`, o con un token ya usado, vencido o de otro reporte. Es la misma respuesta si el reporte no existe. |
| `recurso-no-encontrado` | 404 | El recurso de la URL no existe (o la ruta no existe). |
| `metodo-no-permitido` | 405 | La ruta existe, pero no con ese verbo. |
| `formato-no-aceptable` | 406 | El `Accept` pide un formato que la ruta no produce (p. ej. JSON en el `GET` de una página HTML). Trae `tiposSoportados`. |
| `conflicto-de-estado` | 409 | La petición está bien formada, pero no aplica al estado actual del recurso, o la cuenta cambió mientras se guardaba (el perfil y la verificación de barrio de un vecino). |
| `ubicacion-fuera-del-barrio` | 422 | La coordenada no cae dentro del barrio declarado por el vecino. |
| `ubicacion-imprecisa` | 422 | La lectura tiene una precisión peor que 200 m (ubicación aproximada por red). **No gasta un intento.** |
| `tipo-de-contenido-no-soportado` | 415 | El cuerpo no es JSON (o no es del tipo que la ruta acepta). |
| `formato-no-permitido` | 415 | La foto no es JPEG ni PNG (WebP, por ejemplo). |
| `archivo-demasiado-grande` | 413 | La foto pasa de 10 MB. |
| `cuenta-bloqueada` | 423 | Demasiados intentos fallidos contra esa cuenta. Ver `segundosRestantes`. |
| `limite-reportes-excedido` | 429 | La identidad (dispositivo o vecino) agotó su cupo de reportes en ese barrio (RF006). |
| `limite-de-peticiones-excedido` | 429 | Demasiadas peticiones desde la misma IP, o los 3 intentos diarios de verificar el barrio agotados. **Trae `Retry-After`.** |
| `error-interno` | 500 | Fallo inesperado. Mensaje genérico a propósito. |
| `servicio-no-disponible` | 503 | El servidor no está configurado para esa ruta (p. ej. sensores IoT sin clave). |
| `base-de-datos-no-disponible` | 503 | Mongo no responde. Reintentar. |

### Cómo reaccionar en la interfaz

- **`401` que no es `credencial-invalida` ni `segundo-factor-requerido`**: la sesión murió. **Borra el token
  y lleva al login.** No reintentes.
- **`segundo-factor-requerido`**: no es un error de clave: pide el código y repite la misma petición.
- **`403 acceso-denegado`**: quita esa acción de la interfaz; no cierres sesión.
- **`429`**: espera `Retry-After` segundos antes de reintentar; nunca reintentes en bucle.
- **`5xx`**: muestra «algo falló, intenta de nuevo». No muestres el `detail` de un `500`.
- **`404` en `GET /api/cumplimiento…`/`400` sin cortes cerrados**: no es un fallo, es «aún no hay datos».

## Límites de velocidad (rate limiting)

Por **IP**, con ventana fija, contados en Redis. En IPv6 la «IP» es el prefijo /64 del abonado, no cada dirección. Al excederlos: `429` con la cabecera **`Retry-After`** (en
segundos) y `type: limite-de-peticiones-excedido`.

| Ruta | Límite | Ventana |
|---|---|---|
| `POST /api/veedor/sesion` | 10 | 5 min |
| `/api/veedor/segundo-factor/**` | 10 | 5 min |
| `/api/veedor/cuenta/**` | 10 | 5 min |
| `/api/reportes/**` | 30 | 1 min |
| `/api/reportes/*/foto` (además del anterior) | 10 | 10 min |
| `/api/fotos/**` | 120 | 1 min |
| `GET /api/sistema/modo` | 60 | 1 min |
| `/api/sectores/*/restablecimiento` | 10 | 10 min |
| `/api/iot/presion` | 60 | 1 min |
| `/api/cuentas/**` (incluye el registro de vecinos) | 10 | 10 min |
| `/api/vecino/sesion` | 10 | 10 min |
| `/api/dispositivos` | 10 | 1 hora |
| `/api/suscripciones/**` | 10 | 10 min |

Hay **tres frenos distintos que dan `429` o `423`**, y no son lo mismo:

1. **Por IP** (la tabla de arriba): protege contra inundaciones.
2. **Por identidad** (`limite-reportes-excedido`): 3 reportes por sector cada 30 minutos para un dispositivo y 5 para un vecino registrado.
3. **Por cuenta** (`423 cuenta-bloqueada`): 5 fallos de login en 15 minutos bloquean esa cuenta 15 minutos.

Además, **verificar el barrio** (`POST /api/vecino/verificacion-barrio`) tiene un cupo propio de **3 intentos por día y por cuenta**: al agotarlo responde `429 limite-de-peticiones-excedido` con `Retry-After`. Una lectura imprecisa (`ubicacion-imprecisa`) no gasta intento. Si Redis no responde, ese cupo falla abierto, igual que el límite por IP.

> **Instancias de simulación y de carga.** Los topes de la tabla son los de la instancia real. `RATE_LIMIT_FACTOR` los multiplica (por
> defecto 1; nunca los aprieta; tope 1000) para quien necesite crear cientos de dispositivos desde un solo equipo, pero **no mueve** los
> de ingreso, segundo factor, cambio de clave, altas ni correos (`/api/veedor/sesion`, `/api/vecino/sesion`, `/api/cuentas/**`,
> `/api/suscripciones/**`, `/api/veedor/segundo-factor/**`, `/api/veedor/cuenta/**`): esos solo los mueve `RATE_LIMIT_FACTOR_CUENTAS`,
> pensado para la simulación, y el backend avisa en el log al arrancar si alguno vale más de 1. No es algo que el cliente deba contemplar.

> **NAT y barrios enteros.** Un edificio o una antena móvil entera sale por una sola IP. Los límites son
> holgados a propósito para no castigar a un barrio sin agua que reporta a la vez; el límite fino por
> persona es el de identidad.

Si Redis no responde, **el límite por IP se salta** (no se le niega servicio a nadie por un problema de
infraestructura), pero **la sesión del panel se rechaza** (falla cerrado).

## Paginación

Las listas paginadas devuelven **un arreglo JSON** (no un objeto envoltorio) y ponen la paginación en
**cabeceras**:

| Cabecera | Contenido |
|---|---|
| `X-Total-Count` | Total de elementos. |
| `X-Total-Pages` | Total de páginas. |
| `X-Page` | Página actual (**empieza en 0**). |
| `X-Page-Size` | Tamaño efectivo. |
| `Link` | `<…?pagina=N&tamano=M>; rel="next"`, **solo si hay página siguiente**. |

Parámetros: `?pagina=0&tamano=50`. Tamaño por defecto **50**, máximo **200** (un valor mayor se recorta, no
falla). Una página negativa se trata como la primera.

Rutas paginadas: `GET /api/bitacora`, `/api/bitacora/{id}/sustento`, `/api/sectores/{id}/cortes`, `/api/veedor/reportes/pendientes`, `/api/veedor/ingesta/propuestas`,
`/api/veedor/usuarios` y `/api/veedor/auditoria`.

Se envía `Access-Control-Expose-Headers` con esas cabeceras, para que un navegador en otro origen pueda
leerlas.

## CORS

**Cerrado por defecto**: `aguavigia.cors.origenes-permitidos` está vacío y, vacío, no se emite ninguna cabecera
CORS. El frontend de `frontend/` pasa por el proxy de Vite (mismo origen), así que no hace peticiones cruzadas.

**Abierto en local**: los perfiles `dev` (backend desde el IDE) y `docker` (el de `docker compose up`, con la API
en `http://localhost:8081`) dejan pasar cuatro orígenes: `http://localhost:5173` (Vite), `http://localhost:4173`
(`vite preview`, el de las pruebas E2E), `http://localhost:3000` (React/Next) y `http://localhost:4200` (Angular)
(`application-dev.yml:19-23`, `application-docker.yml:24`). Con Docker se cambia con `CORS_ORIGENES` en el `.env`
(lista separada por comas, sustituye a los cuatro). Un origen no permitido recibe `403` en el preflight.

Un frontend en **otro origen** (un dev server local, un hosting estático aparte) tiene dos caminos:

1. **Declarar el origen**: en local, `CORS_ORIGENES=http://localhost:8000` en el `.env` (perfil `docker`) o
   `aguavigia.cors.origenes-permitidos` en `application-dev.yml` (o `AGUAVIGIA_CORS_ORIGENES_PERMITIDOS` en el
   entorno).
2. **Pasar por un proxy del mismo origen**, como hace el de Vite en `frontend/vite.config.ts`.

Si se habilita, **`Retry-After` no se expone** a JavaScript (solo las cabeceras de paginación): quien
necesite leerlo desde otro origen debe añadirlo a la configuración de CORS.

## Otras cabeceras

- **`Cache-Control`**: `no-cache, no-store` en toda la API (valor por defecto de Spring Security), salvo
  `GET /api/sectores/geometria`, que lleva `public, max-age=86400`. La caché de las lecturas públicas vive en el
  servidor (Redis); en el cliente, la pone su propia capa de datos (TanStack Query en `frontend/`).
- **`Retry-After`**: en `429`, en segundos.
- **`Allow`**: en `405`.
