# FE0 · Contrato al día

**Objetivo:** que el frontend vuelva a compilar y a funcionar contra el backend de hoy. Se regenera el esquema, se adoptan
todos los cambios que **rompen** (F0–F3 de [`cambios-para-frontend.md`](../api/cambios-para-frontend.md)) y el Frontend CI
queda en verde.

Rama: `feat/fe0-contrato-al-dia`. Esfuerzo: 2 sesiones. Sin pantallas nuevas.

## 1. Destrabar el CI

| Causa | Arreglo |
|---|---|
| `api:check` falla: `src/api/generado/esquema.ts` es del 2026-09-29 | `npm run api:sync`, y luego arreglar todo lo que deje de compilar (§2) |
| El job «backend real» falla al sembrar: `POST /api/dispositivos` → `429` tras unos 4 barrios (límite de 10 por hora por IP) | `scripts/preparar-env-ci.mjs` escribe `RATE_LIMIT_FACTOR=100` en el `.env` del CI. `docker-compose.yml` ya lo pasa al backend (`RATE_LIMIT_FACTOR: ${RATE_LIMIT_FACTOR:-1}`). **Se avisa a Sebastian**: es un script compartido y su `verificar-flujos.mjs` recomienda el mismo valor |

## 2. Cambios que rompen, uno por uno

Las evidencias son de `main` del 2026-10-06.

### Reportar (F0, F2, F3)

| Qué | Dónde hoy | Qué hacer |
|---|---|---|
| **Identidad del dispositivo** | `src/api/reportes.ts:53` y `:80` llaman a `obtenerHuella()`; `src/api/huella.ts` | Crear `src/api/dispositivo.ts`:<br>- `POST /api/dispositivos` **una vez**; guardar el token en `localStorage`, con `try/catch`<br>- enviarlo en `X-Dispositivo` en `POST /api/reportes` y `/confirmar`<br>- ante `401 dispositivo-invalido`, pedir otro token y **reintentar una vez**<br><br>Borrar `huella.ts` y su test. `/confirmar` va **sin cuerpo** |
| **Precisión de la ubicación** | sin uso | Si el vecino concede la ubicación, enviar `precisionMetros` (de `GeolocationPosition.coords.accuracy`, redondeado) junto a la `coordenada` |
| **Token de subida de la foto** | `enviarFoto` (`reportes.ts:104-111`) no lo manda | Guardar `subidaToken` de la respuesta de `POST /api/reportes` y enviarlo en `X-Subida` en `POST /api/reportes/{id}/foto` |
| **WebP** | `reportes.ts:99` (`TIPOS_FOTO`); `Reporte.tsx:74` («JPEG, PNG o WebP») | Quitar `image/webp` y cambiar el texto a «JPEG o PNG» |
| **415 `formato-no-permitido`** | `interpretarFoto` solo maneja 413 y 400 | Mensaje propio: «Esa imagen no sirve; usa una foto JPEG o PNG» |
| **`fotoEstado`** | sin uso | Tras subir la foto, mostrar el estado que devuelve: pendiente de revisión, aprobada o descartada. Detalle en [`reportes.md` §fotoEstado](../api/reportes.md#fotoestado) |
| **Proxy `/fotos`** | `vite.config.ts:9` (`['/api', '/fotos']`) | Quitar `/fotos`. Las fotos se sirven en `/api/fotos/{nombre}` |

### Cortes y bitácora (F1)

| Qué | Dónde hoy | Qué hacer |
|---|---|---|
| **`finReal` ya no existe; ahora hay `cierres[]`** | `src/dominio/cortes.ts:18`, `:22`, `:42` (`estaAbierto`, `estaCerrado`, duración) | Un corte cierra **barrio por barrio**:<br>- la hora real del corte en un barrio es su cierre en `cierres[]`<br>- la del corte entero es el último cierre<br>- un cierre `provisional: true` lo sostienen solo vecinos: se rotula «por confirmar»<br><br>Tests de dominio con cortes de varios barrios y cierres parciales |
| **Estados `EXPIRADO` y `ANULADO`** | no existen en `src/` | Tratarlos como cortes **sin hora real**:<br>- `EXPIRADO`: «nadie confirmó el restablecimiento; no cuenta en el Índice»<br>- `ANULADO`: «publicado por error», con `motivoAnulacion` |
| **`OrigenEstado`, 5 valores** | `cortes.ts:7-11` mapea 3 valores viejos | `ACUACAR`, `PRENSA`, `VEEDOR`, `VECINOS`, `SENSOR`. `SENSOR` existe pero no se produce: basta un texto genérico |
| **Tipos nuevos de bitácora** | `src/dominio/historia.ts:1-6` tiene 4; `esTipoBitacora` rechaza el resto | Añadir `CORTE_EXPIRADO`, `CORTE_ANULADO`, `RESTABLECIMIENTO_POR_VECINOS`, `ESTADO_EN_DISPUTA` y `CONSENSO_REVERTIDO`, con su texto y su glifo. Un tipo desconocido se **muestra** con un texto neutro, no se descarta |
| **`fuente` y `respaldo` en eventos** | sin uso | Mostrar de dónde sale cada evento, con el mismo texto que el origen del estado |

### Avisos (F6)

| Qué | Dónde hoy | Qué hacer |
|---|---|---|
| **`sectorIds` máximo 211** | `Avisos.tsx` no valida al enviar | Validar antes del `POST` y mostrar un error por `type` si el servidor responde `400` |

## 3. Pruebas

- Unitarias: `dispositivo.test.ts` (pide una vez, reintenta una vez ante 401, sobrevive sin `localStorage`), `cortes.test.ts` con `cierres[]`, `historia.test.ts` con los 9 tipos.
- `e2e/api-simulada.ts`: las respuestas simuladas pasan a la forma nueva (`cierres`, `subidaToken`, `fotoEstado`, `X-Dispositivo`).
- `e2e/real/ciudadano.spec.ts`: reportar, subir foto, confirmar, todo contra el backend real.

## Terminado cuando

- Pasa la [puerta](README.md#la-puerta-lo-que-se-comprueba-al-cerrar-cada-fase), incluidos los **dos jobs** del Frontend CI.
- `grep -rn "finReal\|huella\|image/webp" frontend/src` no devuelve nada, salvo comentarios que expliquen el cambio.
- Un reporte desde la interfaz, con el backend de `docker compose up`, llega a Mongo con su dispositivo y su foto.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md y docs/frontend/FE0-contrato-al-dia.md, y las secciones
F0–F3 y F6 de docs/api/cambios-para-frontend.md y docs/api/reportes.md.
En una rama feat/fe0-contrato-al-dia desde main: corre npm run api:sync, adopta cada cambio de la guía en el orden
de sus tablas (un commit por causa) y arregla el CI (RATE_LIMIT_FACTOR=100 en scripts/preparar-env-ci.mjs).
No escribas tipos de API a mano. Corre la puerta completa del README (lint, typecheck, api:check, test, build,
e2e simulado y real con docker compose) y muéstrame la salida. Abre el PR cuando todo esté en verde, no lo fusiones.
Si usas subagentes, usa model sonnet.
```
