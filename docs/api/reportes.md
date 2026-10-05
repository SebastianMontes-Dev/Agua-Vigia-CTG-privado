# Reportes ciudadanos

Cualquier persona puede decir «aquí no hay agua» **sin registrarse**, pero siempre con una identidad que pone el servidor:
un **token de dispositivo** o la **sesión de un vecino registrado**. Cuando suficientes vecinos independientes coinciden,
el estado del sector cambia solo (consenso).

## Rutas

| Método y ruta | Para qué | Límite por IP |
|---|---|---|
| `POST /api/dispositivos` | Pedir la identidad de dispositivo (una vez). `201 {token}`. | 10/hora |
| `POST /api/reportes` | Registrar un reporte. `201`. | 30/min |
| `POST /api/reportes/{id}/foto` | Adjuntar una foto al reporte; exige `X-Subida`. | 10/10 min y 30/min |
| `GET /api/fotos/{nombre}` | Ver la foto de un reporte aprobado. Sin sesión. | — |
| `POST /api/reportes/{id}/confirmar` | Otro vecino confirma un reporte. | 30/min |

Esquemas en [`referencia-de-rutas.md`](referencia-de-rutas.md).

## Quién reporta: dispositivo o vecino

Sin identidad, `POST /api/reportes` y `/confirmar` responden `401` con `type: dispositivo-invalido`. Hay dos caminos:

- **Dispositivo (sin cuenta).** `POST /api/dispositivos` devuelve `{ "token": "…" }`. Se pide **una vez**, se guarda
  (`localStorage`) y se envía en la cabecera **`X-Dispositivo`** en cada reporte y confirmación. Cada llamada crea una identidad
  nueva con su propio cupo de reportes; por eso se limita a 10 por hora por IP y por eso el consenso exige además
  reportes verificados y de redes distintas. Pedirlo en cada reporte es un abuso del cliente. Si un reporte devuelve `401 dispositivo-invalido`, pide otro y reintenta una vez.
- **Vecino registrado.** La sesión de `POST /api/vecino/sesion` (`Authorization: Bearer`). Ver
  [`cambios-para-frontend.md`](cambios-para-frontend.md) (sección F2). Si llegan las dos cosas, **manda la cuenta**; una cuenta
  que no sirve (suspendida o del panel) no bloquea a quien también trae un token válido: vota como dispositivo.

La `huella` que antes elegía el cliente **ya no existe**: si llega en el cuerpo, se ignora.

## `POST /api/reportes`

```json
{
  "tipo": "SIN_AGUA",
  "sectorId": "bocagrande",
  "coordenada": { "latitud": 10.4012, "longitud": -75.5560 },
  "precisionMetros": 25.5
}
```

| Campo | Regla |
|---|---|
| `tipo` | Obligatorio. `SIN_AGUA`, `PRESION_BAJA` o `SERVICIO_RESTABLECIDO`. |
| `sectorId` | Opcional **si viaja `coordenada`**. |
| `coordenada` | Opcional **si viaja `sectorId`**. Lat/lon con nombre; solo si el usuario dio permiso. |
| `precisionMetros` | Opcional. `coords.accuracy` del navegador. Con una precisión de 200 m o mejor y la coordenada dentro del barrio, el reporte sale `UBICACION_VERIFICADA`. **Sin este campo la ubicación no verifica.** |

**Hace falta `sectorId` o `coordenada`** (o ambos). Con solo la coordenada, el servidor busca qué sector la
contiene (RF007). Con ambos, manda el `sectorId` declarado. **La coordenada que se guarda es una aproximación de unos 110 m** (3 decimales), no la que se envió.

Respuesta `201`:

```json
{ "id": "3b1f…", "sectorId": "bocagrande", "tipo": "SIN_AGUA",
  "timestamp": "2026-08-08T15:30:00Z", "fotoUrl": null, "fotoEstado": "SIN_FOTO", "confirmaciones": 0,
  "verificacion": "UBICACION_VERIFICADA", "subidaToken": "q7Zk…" }
```

`sectorId` en la respuesta es **siempre** el sector real, también cuando lo infirió el servidor.

`subidaToken` solo viene en esta respuesta (la de crear): es el permiso de **un solo uso** para subir la foto de este reporte (ver más abajo). Si el cliente lo pierde, no hay forma de pedirlo otra vez; la foto es opcional.

`verificacion` lo decide el servidor: `CUENTA_VERIFICADA` (un vecino con el barrio verificado que reporta en ese barrio),
`UBICACION_VERIFICADA` (precisión ≤ 200 m y dentro del barrio) o `NINGUNA`. Un reporte sin verificar **sigue contando**, pero
el consenso exige que parte del sustento esté verificado (ver más abajo).

### Errores propios

| Código | `type` | Cuándo |
|---|---|---|
| `400` | `peticion-invalida` | Tipo desconocido (el `detail` lista los válidos), sector inexistente, **coordenada fuera de todo sector de Cartagena**, o no viaja ni sector ni coordenada. |
| `401` | `dispositivo-invalido` | Falta `X-Dispositivo` (y no hay sesión de vecino), no lo firmó este servidor o el dispositivo ya no existe. |
| `429` | `limite-reportes-excedido` | La identidad ya reportó ese sector 3 veces (dispositivo) o 5 (vecino) en 30 minutos (RF006). |
| `429` | `limite-de-peticiones-excedido` | Demasiadas peticiones desde la misma IP. Trae `Retry-After`. |

## Verificación y red

El servidor no se fía de lo que dice el cliente sobre quién es ni dónde está:

- **`CUENTA_VERIFICADA`**: el vecino probó su barrio con `POST /api/vecino/verificacion-barrio` y reporta en él.
- **`UBICACION_VERIFICADA`**: la coordenada del reporte llegó con precisión de 200 m o mejor y cae dentro del barrio reportado.
- Del origen de la petición el servidor guarda solo una **marca de red** (un HMAC que cambia cada día, hora de Cartagena). Sirve
  para contar redes distintas; no es la IP, no sale por la API y no permite seguir a nadie de un día a otro.

## Cupo y consenso: cómo se decide un estado

Dos mecanismos distintos que el cliente no controla pero conviene entender para explicárselos al usuario.

**Cupo por identidad (RF006).** Un dispositivo puede reportar **3 veces por sector cada 30 minutos** y un vecino
registrado, **5**. Frena el spam de un solo teléfono. Se cuenta por identidad y sector.

**Consenso (RF009–RF011).** Cada reporte hace que se evalúe el sector (como mucho **una evaluación por segundo y
sector**: si llegan cientos de reportes a la vez, se agrupan y el resto se evalúa en el barrido siguiente; ningún
reporte se queda sin evaluar, `ADR-053`):

1. Se cuentan las **identidades distintas** (dispositivos o cuentas) que reportaron en los últimos **30 minutos**.
   Una misma identidad que reporta tres veces cuenta **una**.
2. Si ese número alcanza el **umbral del sector**, se mira qué tipo de reporte tiene **mayoría** y el
   estado pasa a: `SIN_AGUA → SIN_SERVICIO`, `PRESION_BAJA → PRESION_BAJA`,
   `SERVICIO_RESTABLECIDO → CON_SERVICIO`. Además el sustento debe tener **composición**: al menos un tercio
   (mínimo 1) con `verificacion` distinta de `NINGUNA`, y reportes de **al menos 2 redes distintas**
   (`aguavigia.consenso.redes-minimas`). Una ráfaga desde una sola red, o de reportes que nadie verificó,
   no cambia el mapa.
3. **Un empate entre tipos no cambia nada**: evidencia ambigua no se publica.
4. Los reportes **descartados por moderación no cuentan.**
5. Las **confirmaciones no cuentan** para el consenso.

El umbral, por defecto, es `clamp(ceil(población del sector × 0,001), 3, 15)`: un barrio de 12 000 personas
necesita 12 vecinos; uno pequeño, 3 y uno enorme, 15. Es configurable.

Cuando el consenso cambia el estado se anexa un evento a la [bitácora](bitacora-estadisticas-cumplimiento.md)
con `cantidadReportesSustento`, y los **ids** de los reportes que lo sostuvieron (RF011) se piden con `GET /api/bitacora/{id}/sustento`, para poder contrastar el
cambio con la evidencia.

> **Consecuencia para la interfaz.** Tras reportar, el estado del mapa **no cambia** hasta que otros
> vecinos coincidan, y cuando coinciden el cambio puede tardar **hasta ~2 s** en aparecer (el aviso del canal en
> vivo llega entonces). No prometas «se actualizó»: di «gracias, tu reporte cuenta junto con los de tus
> vecinos».

## `POST /api/reportes/{id}/foto`

`multipart/form-data`, con **una parte llamada `foto`**, y la cabecera **`X-Subida`** con el `subidaToken` que devolvió `POST /api/reportes`. Devuelve el reporte con `fotoUrl` y `fotoEstado` rellenos.

El token es de **un solo uso**, está atado a ese reporte y **vence a los 10 minutos**. Sin él —o con uno gastado, vencido o de otro reporte— responde `403 subida-no-autorizada`, y es la misma respuesta si el reporte no existe: no sirve para averiguar qué ids hay. El archivo se valida antes de gastar el token, así que un archivo rechazado deja reintentar.

| Regla | Valor |
|---|---|
| Tipos | `image/jpeg`, `image/png`. **WebP se rechaza con `415 formato-no-permitido`** (no se le puede quitar el EXIF): no lo ofrezcas en el selector. Se verifica la **firma binaria**, no solo el `Content-Type`. |
| Tamaño máximo | **10 MB** → `413` con `type: archivo-demasiado-grande`. |
| Procesado | JPEG y PNG se reescalan a un lado máximo de 1600 px (JPEG a calidad 0,75). Se descarta el EXIF, **incluida la ubicación GPS de la foto**. |
| URL resultante | Relativa: `/api/fotos/<uuid>.<ext>`, del mismo origen. La ruta vieja `/fotos/**` ya no existe. |
| Quién la ve | El público, solo cuando el reporte está **aprobado** y el veedor no descartó la foto. Antes responde `404`, igual que si no existiera. |

Errores: `400` (firma inválida, falta la parte `foto`), `403 subida-no-autorizada`, `409` (el reporte ya tiene foto), `413`, `415 formato-no-permitido`.

### `fotoEstado`

| Valor | Qué hacer |
|---|---|
| `SIN_FOTO` | No hay foto. |
| `EN_REVISION` | Hay foto pero el reporte espera moderación. **No pidas la imagen** (daría `404`): muestra «en revisión». |
| `PUBLICA` | Pídela en `fotoUrl`. |
| `DESCARTADA` | El veedor la retiró (o descartó el reporte). No se muestra. |

La foto **nunca cuenta como voto**: es evidencia para quien modera.

## `GET /api/fotos/{nombre}`

Sin sesión. Devuelve la imagen (`image/jpeg` o `image/png`) con `X-Content-Type-Options: nosniff` y caché de un minuto. `404` si no existe, si su reporte no está aprobado o si se descartó: es el mismo `404` a propósito.

La foto es **evidencia**, no contenido público destacado. La purga de binarios pasados 365 días (minimización de
datos, conservando el reporte) existe pero está **desactivada por defecto** (`application.yml:132`; `ADR-027`, `ADR-085`).

## `POST /api/reportes/{id}/confirmar`

**Sin cuerpo.** La identidad de quien confirma viaja en `X-Dispositivo` o en la sesión de un vecino, igual que al
reportar. `200` con el reporte y su `confirmaciones` actualizado. `401 dispositivo-invalido` si falta la identidad.
`404` si el reporte no existe **o fue descartado por moderación** (para quien confirma es lo mismo: un reporte que ya
no cuenta).

## Sensores IoT

Existe `POST /api/iot/presion` para sensores de presión de la red, autenticado con la cabecera
`X-IoT-Key`. **No es para el frontend ciudadano.** Ver [`referencia-de-rutas.md`](referencia-de-rutas.md).
