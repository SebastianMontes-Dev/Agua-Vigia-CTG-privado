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
