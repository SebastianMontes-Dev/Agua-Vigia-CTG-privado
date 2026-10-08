# Plan del frontend

Plan por fases para que el frontend (`frontend/`, React 19 + Vite + TS) cubra **todo el contrato actual** del backend
(`backend/openapi.yaml`). Lo ejecuta Yordy con Claude Code. Propuesto el 2026-10-06. Reemplaza la tabla de
[`guia-frontend.md` §7](../diseno/guia-frontend.md#7-aplicación-por-claude-code).

Las fases se llaman **FE0–FE7** para no confundirse con las fases F0–F6 del backend ni con las R0–R9 de la
[reducción](../reduccion/README.md).

## Punto de partida (`main`, 2026-10-06)

| | Hoy |
|---|---|
| Rutas del contrato (paths, sin `/api/sim`) | 78 |
| Rutas que el frontend consume | 27 |
| `esquema.ts` (tipos generados) | Desactualizado: 58 rutas, del 2026-09-29. `npm run api:check` falla |
| Frontend CI en `main` | **Rojo por dos causas:**<br>1. `api:check`<br>2. el sembrado del job «backend real» choca con el límite de 10 `POST /api/dispositivos` por hora |
| Reportar contra el backend actual | **No funciona**: sin `X-Dispositivo`, `POST /api/reportes` responde `401 dispositivo-invalido` |
| Cambios de contrato F0–F6 adoptados | Solo `CORTE_PROGRAMADO` (F5) |

Pantallas que existen hoy:
- **Mapa y ficha:** `/`, `/sectores/:id`
- **Reporte:** la hoja sobre el mapa y `/confirmar/:id`
- **Historia pública:** `/cumplimiento`, `/estadisticas`, `/bitacora`
- **Avisos:** `/avisos`, `/avisos/confirmar`, `/avisos/baja`
- **Muestrario:** `/muestrario`

No hay panel, cuentas ni vecino. El ingreso al panel con segundo factor ya está hecho en la rama
`feat/f5-ingreso-panel` (2 commits, 81 detrás de `main`), que es la base de FE2.

## Qué no cambia mientras se trabaja

- **El contrato.** El backend está en reducción (R0–R9) y su contrato HTTP está **congelado**. Si una prueba contra el backend real falla justo después de una fusión de la reducción, es un defecto del backend: se avisa a Sebastian y no se adapta el frontend.
- **La identidad visual** ([`identidad.md`](../diseno/identidad.md), ADR-070/071), los cuatro estados ([`DESIGN.md`](../../DESIGN.md)) y la regla de los cinco segundos (ADR-079): el veredicto arriba, cifras que se lean solas, dos columnas en escritorio y el detalle detrás de una acción.
- **El stack:** React 19, Vite, TanStack Router y Query, `openapi-fetch`, react-aria, MapLibre y PMTiles, CSS propio. No se añaden librerías de interfaz.
- **Quién manda si hay choque (nota del backend, 2026-10-08).** El backend tiene prioridad sobre el frontend: es el requisito 7 de la
  [reducción](../reduccion/README.md#requisitos-del-dueño-añadidos-el-2026-10-07). Si algo de este plan choca con ella, se adapta el
  frontend. El contrato sigue congelado hasta R9; si el backend necesitara cambiarlo, lo decide Sebastian y lo avisa en
  [`cambios-para-frontend.md`](../api/cambios-para-frontend.md) antes de fusionar.
- **Las dos instancias.** El backend real (`http://localhost:8081`) arranca con las 30 000 cuentas de vecinos; la simulación
  (`http://localhost:8082`, `AGUAVIGIA_BACKEND=http://localhost:8082`) arranca sin cuentas. Para que las pruebas `e2e/real` sean repetibles,
  el backend debe correr con `INGESTA_MODO=local`: con `auto` la ingesta trae boletines de Internet cada vez y la base nunca es la misma.

## Reglas de cada fase

1. **Antes de tocar la interfaz:** la skill `disenar-frontend`. **Antes de cerrar la fase:** `revisar-diseno` sobre las capturas.
2. **Tipos solo generados.** `npm run api:sync` produce `esquema.ts`; nunca se escriben tipos de la API a mano. `api:check` en verde es obligatorio.
3. **Nada que la API no devuelva.** Cada cifra, insignia o texto sale de un campo del contrato ([`guia-frontend.md` §5](../diseno/guia-frontend.md#5-matriz-de-pantallas-y-datos)). Si una pantalla nueva no está en §5, la fase la **añade a §5 antes de construirla**, con sus datos y estados.
4. **Errores por `type`, no por texto** ([`errores-y-limites.md`](../api/errores-y-limites.md)). Cada lista tiene estados de carga, vacío y error, y cada dato muestra su frescura.
5. **Permisos:** el panel pinta solo lo que `permisos[]` permite. Un `401` con un `type` distinto de `credencial-invalida`/`segundo-factor-requerido` borra la sesión y vuelve al ingreso.
6. **Seguridad del cliente** ([`cuentas-y-sesion.md` §Notas](../api/cuentas-y-sesion.md#notas-de-seguridad-para-el-cliente)):
   - el token nunca va en la URL
   - las pantallas que reciben `?token=` lo quitan con `history.replaceState` y usan `referrer no-referrer`
   - sin scripts de terceros
7. **Escala** ([`escalabilidad-para-el-cliente.md`](../api/escalabilidad-para-el-cliente.md)):
   - el token de dispositivo se pide **una vez**
   - un solo SSE por pestaña
   - geometría cacheada un día
   - reintentos con `Retry-After`

## La puerta: lo que se comprueba al cerrar cada fase

Desde `frontend/`:

1. `npm run lint`, `npm run typecheck`, `npm run api:check`, `npm test` y `npm run build`, todos en verde.
2. `npm run test:e2e`: e2e contra la API simulada. Cada pantalla nueva trae su spec.
3. `npm run test:e2e:real` contra `docker compose up` con el backend de `main` y `RATE_LIMIT_FACTOR=100`. Cada flujo nuevo trae su spec en `e2e/real/`.
4. **Capturas** en 1440×900, 1024×768, 768×1024 y 390×844, en claro y en oscuro, más una con movimiento reducido:
   - se guardan en `frontend/test-results/capturas/<fase>/`, ignorado por git; no se commitean binarios
   - se miran
   - se pasa `revisar-diseno` y se corrige lo que encuentre
5. **Prueba de los cinco segundos:** en cada pantalla nueva, alguien que no la conoce dice qué responde sin hacer scroll. Se anota quién y el resultado.
6. **El CI de la rama, en verde**, incluido el job contra el backend real.

## Cómo se trabaja

- **Una rama por fase** desde `main`: `feat/fe<n>-<tema>`, por ejemplo `feat/fe0-contrato-al-dia`. Se fusiona a `main` con la puerta en verde. Hoy `main` recibe directo, según `CLAUDE.md`; para el frontend se usa rama más PR porque el CI del frontend necesita la rama para correr sus dos jobs.
- **Una fase por sesión** de Claude Code, `/clear` entre fases. Cada guía termina con un prompt listo para pegar. Si se usan subagentes, con Sonnet.
- **Al cerrar una fase** se marca ✅ en la tabla de abajo, con la fecha y el PR.

## Fases

| Fase | Guía | Qué entrega | Rutas del contrato que suma | Estado |
|---|---|---|---|---|
| FE0 | [Contrato al día](FE0-contrato-al-dia.md) | CI en verde; reportar funciona; se adoptan todos los cambios que **rompen** (F0–F3) | `POST /api/dispositivos` y `GET /api/fotos/{n}` | — |
| FE1 | [Lo público, completo](FE1-publico-completo.md) | Insignias del estado del barrio, calidad del Índice, Índice por corte, banner de simulación, «¿ya volvió el agua?» | `cumplimiento/calidad`, `cumplimiento/cortes/{id}`, `sistema/modo`, `sectores/{id}/restablecimiento` | — |
| FE2 | [Ingreso al panel](FE2-ingreso-al-panel.md) | Rebasar `feat/f5-ingreso-panel`: ingreso, alta de TOTP con QR, marco con permisos, seguridad de la cuenta | `veedor/sesion`, `sesion/cierre`, `yo`, `segundo-factor/*`, `cuenta/clave` | — |
| FE3 | [Moderación y cortes](FE3-moderacion-y-cortes.md) | Cola de reportes, fotos del panel, cortes (crear, cerrar, por barrio, confirmar, anular), vencidos, disputas | 14 operaciones de `veedor/reportes`, `veedor/fotos`, `veedor/cortes`, `veedor/disputas` | — |
| FE4 | [Ingesta](FE4-ingesta.md) | Propuestas (aprobar, descartar, anular), documentos fallidos, salud de los colectores | 6 operaciones de `veedor/ingesta` | — |
| FE5 | [Administración](FE5-administracion.md) | Cuentas, invitaciones y permisos, auditoría, métricas del sistema | 10 operaciones de `veedor/usuarios`, `auditoria`, `sistema/metricas` | — |
| FE6 | [Cuentas públicas y vecino](FE6-cuentas-y-vecino.md) | Solicitar cuenta, olvidé mi clave, reenviar verificación; registro, ingreso y perfil del vecino; reportar con su sesión | `cuentas/registro`, `restablecimiento`, `verificacion/reenvio`, `cuentas/vecino` y las 5 de `vecino/*` | — |
| FE7 | [Integración y entrega](FE7-integracion-y-entrega.md) | Cobertura total del contrato, accesibilidad, rendimiento, guion de la demo | — | — |

**Fuera del frontend:**
- `/api/sim/**`: la usa el simulador.
- `POST /api/iot/presion`: es para sensores, inactivo.
- `GET /api/v2/requests.json`: Open311, una API para máquinas.
- Las páginas HTML de `/api/cuentas/enlaces/*`: el backend las sirve y el frontend solo depende de que existan (ver FE6).

### Orden y dependencias

```
FE0 ──▶ FE1
  └───▶ FE2 ──▶ FE3 ──▶ FE4
                 └───▶ FE5
FE0 ──▶ FE6 (usa el marco de sesión de FE2)
todas ──▶ FE7
```

FE0 va primero porque sin él nada compila contra el esquema nuevo y reportar no funciona. FE1 y FE2 pueden ir en paralelo.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| **FE0 es grande**: regenerar el esquema rompe la compilación en muchos sitios a la vez (`finReal`, `huella`, tipos de bitácora) | Se hace en una sola rama con commits por causa. La guía de FE0 lista cada punto con su archivo |
| **La rama `feat/f5-ingreso-panel` está 81 commits atrás** y usa el esquema viejo | Se rebasa **después** de FE0. Solo `router.tsx` cambió en los dos lados. El mayor trabajo es adaptar sus tipos al esquema nuevo |
| **El backend cambia por dentro (reducción) mientras se construye el panel** | Contrato congelado. Las pruebas `e2e/real` son además una red de seguridad para Sebastian: si fallan tras una fase R, se le avisa |
| **El e2e real depende del límite de dispositivos** (10 por hora por IP) | `RATE_LIMIT_FACTOR=100` en el CI y en local (FE0). El cliente pide el token una vez y lo reutiliza |
| **Los enlaces de los correos de cuenta abren páginas HTML del backend, no la SPA** | Funcionan tal cual. Que el correo apunte a la SPA exige cambiar `MailCuentaAdapter` (backend), que queda **después de R9** y coordinado con Sebastian. FE6 lo deja opcional |
| **El panel con 30 000 cuentas sintéticas** | La lista de cuentas filtra `sintetica` y pagina por cabeceras. Nunca se cuentan como personas (frase fija de `cambios-para-frontend.md` F4) |
| **La guía §5 no define pantallas** de vecino, disputas, vencidos, métricas ni «¿ya volvió el agua?» | Cada fase añade sus filas a §5 antes de construir (regla 3) |
| **La skill `disenar-frontend` cita archivos retirados** (`docs/ingenieria/plan-frontend.md`, `docs/gestion/sprint-7.md`, skills `registrar-*`) | Su §5 ahora apunta a este README |
