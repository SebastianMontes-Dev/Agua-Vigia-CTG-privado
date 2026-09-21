# Reportes ciudadanos

Cualquier persona puede decir «aquí no hay agua» **sin registrarse**. Cuando suficientes vecinos
independientes coinciden, el estado del sector cambia solo (consenso).

## Rutas

| Método y ruta | Para qué | Límite por IP |
|---|---|---|
| `POST /api/reportes` | Registrar un reporte. `201`. | 30/min |
| `POST /api/reportes/{id}/foto` | Adjuntar una foto al reporte. | 30/min |
| `POST /api/reportes/{id}/confirmar` | Otro vecino confirma un reporte. | 30/min |

Esquemas en [`referencia-de-rutas.md`](referencia-de-rutas.md).

## `POST /api/reportes`

```json
{
  "tipo": "SIN_AGUA",
  "huella": "a3f9c1e07b5d4a2c8e6f0b1d9c3a7e52",
  "sectorId": "bocagrande",
  "coordenada": { "latitud": 10.4012, "longitud": -75.5560 }
}
```

| Campo | Regla |
|---|---|
| `tipo` | Obligatorio. `SIN_AGUA`, `PRESION_BAJA` o `SERVICIO_RESTABLECIDO`. |
| `huella` | Obligatorio. **Entre 32 y 128 caracteres.** Ver más abajo. |
| `sectorId` | Opcional **si viaja `coordenada`**. |
| `coordenada` | Opcional **si viaja `sectorId`**. Lat/lon con nombre; solo si el usuario dio permiso. |

**Hace falta `sectorId` o `coordenada`** (o ambos). Con solo la coordenada, el servidor busca qué sector la
contiene (RF007). Con ambos, manda el `sectorId` declarado y la coordenada se guarda como dato.

Respuesta `201`:

```json
{ "id": "3b1f…", "sectorId": "bocagrande", "tipo": "SIN_AGUA",
  "timestamp": "2026-08-08T15:30:00Z", "fotoUrl": null, "confirmaciones": 0 }
```

`sectorId` en la respuesta es **siempre** el sector real, también cuando lo infirió el servidor.

### Errores propios

| Código | `type` | Cuándo |
|---|---|---|
| `400` | `peticion-invalida` | Tipo desconocido (el `detail` lista los válidos), huella fuera de 32–128, sector inexistente, **coordenada fuera de todo sector de Cartagena**, o no viaja ni sector ni coordenada. |
| `429` | `limite-reportes-excedido` | El dispositivo ya reportó 3 veces ese sector en 30 minutos (RF006). |
| `429` | `limite-de-peticiones-excedido` | Demasiadas peticiones desde la misma IP. Trae `Retry-After`. |

## La huella del dispositivo

Es lo único que permite limitar el abuso **sin pedir cuenta**. No es un dato personal ni un identificador
persistente de la persona.

- **La genera el cliente una sola vez** (por ejemplo, un UUID aleatorio más sal, hasheado con SHA-256 →
  64 caracteres hex) y la guarda en el dispositivo (`localStorage`).
- **Se reutiliza en todos los reportes y confirmaciones.** Una huella distinta en cada petición evade el
  límite por dispositivo, y el servidor no lo puede distinguir de un vecino nuevo.
- **No debe derivarse de nada identificable** (correo, teléfono, IMEI).
- Una huella que empiece por `IoT-` **no** da el cupo de sensor: eso solo lo decide la ruta de sensores
  con su clave.

## Cupo y consenso: cómo se decide un estado

Dos mecanismos distintos que el cliente no controla pero conviene entender para explicárselos al usuario.

**Cupo por dispositivo (RF006).** Cada dispositivo puede reportar **3 veces por sector cada 30 minutos**.
Frena el spam de un solo teléfono. Se cuenta por huella y sector.

**Consenso (RF009–RF011).** Cada reporte hace que se evalúe el sector (como mucho **una evaluación por segundo y
sector**: si llegan cientos de reportes a la vez, se agrupan y el resto se evalúa en el barrido siguiente; ningún
reporte se queda sin evaluar, `ADR-053`):

1. Se cuentan los **vecinos distintos** (huellas distintas) que reportaron en los últimos **30 minutos**.
   Un mismo dispositivo que reporta tres veces cuenta **una**.
2. Si ese número alcanza el **umbral del sector**, se mira qué tipo de reporte tiene **mayoría** y el
   estado pasa a: `SIN_AGUA → SIN_SERVICIO`, `PRESION_BAJA → PRESION_BAJA`,
   `SERVICIO_RESTABLECIDO → CON_SERVICIO`.
3. **Un empate entre tipos no cambia nada**: evidencia ambigua no se publica.
4. Los reportes **descartados por moderación no cuentan.**
5. Las **confirmaciones no cuentan** para el consenso.

El umbral, por defecto, es `max(3, ceil(población del sector × 0,001))`: un barrio de 12 000 personas
necesita 12 vecinos; uno pequeño, 3. Es configurable.

Cuando el consenso cambia el estado se anexa un evento a la [bitácora](bitacora-estadisticas-cumplimiento.md)
con `cantidadReportesSustento`, y los **ids** de los reportes que lo sostuvieron (RF011) se piden con `GET /api/bitacora/{id}/sustento`, para poder contrastar el
cambio con la evidencia.

> **Consecuencia para la interfaz.** Tras reportar, el estado del mapa **no cambia** hasta que otros
> vecinos coincidan, y cuando coinciden el cambio puede tardar **hasta ~2 s** en aparecer (el aviso del canal en
> vivo llega entonces). No prometas «se actualizó»: di «gracias, tu reporte cuenta junto con los de tus
> vecinos».

## `POST /api/reportes/{id}/foto`

`multipart/form-data`, con **una parte llamada `foto`**. Devuelve el reporte con `fotoUrl` relleno.

| Regla | Valor |
|---|---|
| Tipos | `image/jpeg`, `image/png`, `image/webp`. Se verifica la **firma binaria**, no solo el `Content-Type`. |
| Tamaño máximo | **10 MB** → `413` con `type: archivo-demasiado-grande`. |
| Procesado | JPEG y PNG se reescalan a un lado máximo de 1600 px (JPEG a calidad 0,75). Se descarta el EXIF, **incluida la ubicación GPS de la foto**. WebP se guarda tal cual. |
| URL resultante | Relativa: `/fotos/<uuid>.<ext>`. Se sirve del mismo origen, con caché de un día. |

Errores: `400` (tipo o firma inválidos, falta la parte `foto`), `404` (el reporte no existe), `413`.

La foto es **evidencia**, no contenido público destacado: el servidor puede borrar los binarios pasados 365
días (minimización de datos), conservando el reporte.

## `POST /api/reportes/{id}/confirmar`

```json
{ "huella": "a3f9c1e07b5d4a2c8e6f0b1d9c3a7e52" }
```

`huella` de 32 a 128 caracteres. `200` con el reporte y su `confirmaciones` actualizado. `404` si el reporte
no existe **o fue descartado por moderación** (para quien confirma es lo mismo: un reporte que ya no cuenta).

## Sensores IoT

Existe `POST /api/iot/presion` para sensores de presión de la red, autenticado con la cabecera
`X-IoT-Key`. **No es para el frontend ciudadano.** Ver [`referencia-de-rutas.md`](referencia-de-rutas.md).
