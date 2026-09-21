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
| `tiposSoportados` | `415` | Los `Content-Type` que sí acepta la ruta. |

## Catálogo de tipos

`type` es siempre `https://aguavigia.example/errores/<slug>`. Es un identificador estable, **no una URL que
se pueda visitar**.

| Slug | Código | Significa |
|---|---|---|
| `peticion-invalida` | 400 | Datos mal formados, campo inválido, JSON ilegible o valor fuera de rango. |
| `credencial-invalida` | 401 | Correo o clave incorrectos, o clave de sensor IoT incorrecta. |
| `segundo-factor-requerido` | 401 | La clave era correcta pero falta el código TOTP. **Reintentar con `codigoTotp`.** |
| `sesion-sin-cuenta` | 401 | El token es válido pero su cuenta ya no existe. **Cerrar sesión.** |
| *(sin type propio)* | 401 | Sin token, token inválido, caducado o revocado. **Cerrar sesión.** |
| `acceso-denegado` | 403 | La sesión no tiene el permiso que exige la operación. |
| `cuenta-no-habilitada` | 403 | La cuenta no puede iniciar sesión. Ver la propiedad `estado`. |
| `recurso-no-encontrado` | 404 | El recurso de la URL no existe (o la ruta no existe). |
| `metodo-no-permitido` | 405 | La ruta existe, pero no con ese verbo. |
| `conflicto-de-estado` | 409 | La petición está bien formada, pero no aplica al estado actual del recurso. |
| `tipo-de-contenido-no-soportado` | 415 | El cuerpo no es JSON (o no es del tipo que la ruta acepta). |
| `archivo-demasiado-grande` | 413 | La foto pasa de 10 MB. |
| `cuenta-bloqueada` | 423 | Demasiados intentos fallidos contra esa cuenta. Ver `segundosRestantes`. |
| `limite-reportes-excedido` | 429 | El dispositivo agotó su cupo (RF006). |
| `limite-de-peticiones-excedido` | 429 | Demasiadas peticiones desde la misma IP. **Trae `Retry-After`.** |
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

Por **IP**, con ventana fija, contados en Redis. Al excederlos: `429` con la cabecera **`Retry-After`** (en
segundos) y `type: limite-de-peticiones-excedido`.

| Ruta | Límite | Ventana |
|---|---|---|
| `POST /api/veedor/sesion` | 5 | 5 min |
| `/api/veedor/segundo-factor/**` | 10 | 5 min |
| `/api/reportes/**` | 30 | 1 min |
| `/api/iot/presion` | 60 | 1 min |
| `/api/cuentas/**` | 10 | 10 min |
| `/api/suscripciones/**` | 10 | 10 min |

Además, el proxy de producción limita a **30 peticiones por segundo por IP** (con ráfaga de 60) en toda la
API, y a **20 conexiones SSE por IP**.

Hay **tres frenos distintos que dan `429` o `423`**, y no son lo mismo:

1. **Por IP** (la tabla de arriba): protege contra inundaciones.
2. **Por dispositivo** (`limite-reportes-excedido`): 3 reportes por sector cada 30 minutos.
3. **Por cuenta** (`423 cuenta-bloqueada`): 5 fallos de login en 15 minutos bloquean esa cuenta 15 minutos.

> **NAT y barrios enteros.** Un edificio o una antena móvil entera sale por una sola IP. Los límites son
> holgados a propósito para no castigar a un barrio sin agua que reporta a la vez; el límite fino por
> persona es el de dispositivo.

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

Rutas paginadas: `GET /api/bitacora`, `/api/veedor/reportes/pendientes`, `/api/veedor/ingesta/propuestas`,
`/api/veedor/usuarios` y `/api/veedor/auditoria`.

Se envía `Access-Control-Expose-Headers` con esas cabeceras, para que un navegador en otro origen pueda
leerlas.

## CORS

**Cerrado por defecto**: `aguavigia.cors.origenes-permitidos` está vacío en todos los perfiles y, vacío, no
se emite ninguna cabecera CORS. En producción el frontend y la API van **detrás del mismo proxy**, así que el
navegador nunca hace una petición cruzada.

Un frontend en **otro origen** (un dev server local, un hosting estático aparte) tiene dos caminos:

1. **Declarar el origen**: `aguavigia.cors.origenes-permitidos: [http://localhost:3000]` en
   `application-dev.yml` (o `AGUAVIGIA_CORS_ORIGENES_PERMITIDOS` en el entorno).
2. **Servirlo detrás del mismo proxy** que la API (recomendado en producción).

Si se habilita, **`Retry-After` no se expone** a JavaScript (solo las cabeceras de paginación): quien
necesite leerlo desde otro origen debe añadirlo a la configuración de CORS.

## Otras cabeceras

- **`X-Cache-Status`** (solo tras el proxy, en lecturas públicas): `HIT`, `MISS`, `STALE`, `BYPASS`.
  Depuración; no contar con ella.
- **`Cache-Control`**: las lecturas públicas llevan `public, max-age=5, stale-while-revalidate=30`; la API
  privada y las escrituras, `no-store`.
- **`Retry-After`**: en `429`, en segundos.
- **`Allow`**: en `405`.
