# Integración frontend-backend

> **Estado (2026-09-29).** El frontend anterior se retiró de `main` (`ADR-048`; código en la etiqueta git
> `pre-retiro-frontend`) y el nuevo se rehace en `frontend/` (React 19 + Vite, `ADR-067`), por fases F0–F6
> (`plan-frontend.md`). Este documento describe cómo se conecta **el frontend nuevo**. Qué hace cada ruta y
> sus reglas: `docs/api/`; el contrato exacto: `backend/openapi.yaml`.

El frontend consume siempre la API real del backend — no hay modo simulación. Vite hace de proxy de `/api` y
`/fotos` hacia el backend, así que para el navegador es el mismo origen y no hace falta CORS
(`frontend/vite.config.ts:5-17`). El proyecto corre solo en local, sin nginx (`ADR-080`).

## Arrancar contra un backend local

| Paso | Qué | Detalle |
|---|---|---|
| 1 | Levantar el backend | `docker compose up` (API en `http://localhost:8081`) o `cd backend && ./mvnw spring-boot:run` |
| 2 | Si el backend no está en `:8081` | Variable de entorno `AGUAVIGIA_BACKEND` al arrancar Vite; por defecto `http://localhost:8081` (`frontend/vite.config.ts:5`) |
| 3 | `cd frontend && npm run dev` | SPA en `http://localhost:5173`; `/api/*` y `/fotos/*` van al backend por el proxy |

Con `docker compose up` el backend genera su secreto de sesión y la clave del primer ADMIN (`ADR-086`); fuera de
Docker, ver `entorno-local.md`.

## Cliente tipado

Los tipos salen de `backend/openapi.yaml` y viven en `frontend/src/api/generado/esquema.ts`. `npm run api:sync` los
regenera; `npm run api:check` falla si están desfasados del contrato y corre en el CI del frontend
(`.github/workflows/frontend-ci.yml:51`). Solo compara el YAML con los tipos, no contra un backend vivo: el YAML a su
vez lo protege `ContratoOpenApiTest` en el backend.

## Endpoints que consume hoy el frontend nuevo

Pantallas públicas (`frontend/src/pantallas/publico/`). Leído de `frontend/src` (sin contar `generado/`) el 2026-09-29.

| Área | Rutas |
|---|---|
| Mapa y sector | `GET /api/sectores`, `GET /api/sectores/geometria`, `GET /api/sectores/stream` (SSE), `GET /api/sectores/{id}`, `GET /api/sectores/{sectorId}/cortes` |
| Reportes | `POST /api/reportes`, `POST /api/reportes/{id}/foto`, `POST /api/reportes/{id}/confirmar` (enlace compartido `/confirmar/:id`) |
| Avisos por correo | `POST /api/suscripciones`; `POST /api/suscripciones/confirmar` y `/cancelar` con `Accept: application/json`, al pulsar el botón |
| Bitácora | `GET /api/bitacora`, `GET /api/bitacora/{id}/sustento` |
| Estadísticas | `GET /api/estadisticas`, `GET /api/estadisticas/exportar.csv` |
| Cumplimiento | `GET /api/cumplimiento`, `/sectores/{sectorId}`, `/serie`, `/serie.csv` |

El panel del veedor (sesión, moderación, cortes, ingesta, cuentas) es de F5 y todavía no consume ninguna ruta `/api/veedor/*`.

### Los enlaces del correo llevan a la SPA

Los correos de suscripción apuntan a `${aguavigia.app.url-frontend}/avisos/confirmar?token=…` y `…/avisos/baja?token=…`
(`MailNotificacionAdapter.java:66,71`); el aviso de cambio de estado enlaza a `…/sectores/{id}` (`:110`). La pantalla
retira el token de la URL y solo actúa al pulsar el botón (`ADR-054`). Las páginas `GET /api/suscripciones/confirmar`
y `/cancelar` del backend siguen existiendo, pero ya no son el enlace del correo. Los enlaces de cuentas siguen en
el backend (`/api/cuentas/enlaces/*`) hasta F5: `docs/api/correos-y-enlaces.md`.

**Deliberadamente sin conectar** (son para otros consumidores, no para la SPA): `POST /api/iot/presion` (sensores
empujando telemetría) y `GET /api/v2/requests.json` (Open311, formato estándar para sistemas cívicos de terceros).
