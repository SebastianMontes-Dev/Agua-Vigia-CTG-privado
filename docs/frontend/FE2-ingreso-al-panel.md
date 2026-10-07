# FE2 · Ingreso al panel

**Objetivo:** que un veedor o un ADMIN entre al panel con su clave y su segundo factor y vea un marco que solo le ofrece lo
que sus permisos permiten. También gestiona la seguridad de su cuenta.

Rama: `feat/fe2-ingreso-al-panel`. Depende de FE0. Esfuerzo: 1–2 sesiones, porque la mayor parte ya está hecha en
`feat/f5-ingreso-panel`.

## Punto de partida: la rama `feat/f5-ingreso-panel`

Tiene 2 commits, va 81 commits detrás de `main` y está hecha contra el esquema viejo. Trae:
- **Pantallas:** `Ingreso`, `SegundoFactor`, `MarcoPanel`, `PendientePanel`
- **Módulos:** `api/panel.ts`, `app/panel.ts`, `Redirigir.tsx`, `dominio/permisos.ts`, `dominio/mensajes-panel.ts`
- **Componente:** `CodigoQr`
- **e2e:** `panel.spec.ts`
- **Dependencia nueva:** `qrcode`. Es local, sin servicios externos; el QR se dibuja en el navegador

Pasos:
1. `git checkout -b feat/fe2-ingreso-al-panel main`, y luego `git cherry-pick 95962ad 5dda089`, los dos commits de la rama. Solo `router.tsx` choca.
2. Ajustar los tipos al `esquema.ts` de FE0. Nada se escribe a mano.
3. Cuando el PR se fusione, archivar la rama vieja como `archivo/yordy-<fecha>/f5-ingreso-panel` y borrarla.

## Pantallas

Las filas ya están en [`guia-frontend.md` §5.3/§5.4](../diseno/guia-frontend.md#53-cuentas-y-segundo-factor). Contrato en
[`cuentas-y-sesion.md`](../api/cuentas-y-sesion.md).

| Ruta | Qué | Contrato |
|---|---|---|
| `/panel/ingreso` | Correo, clave y, si hace falta, código TOTP. Errores por `type`:<br>- `credencial-invalida` (sin decir qué campo)<br>- `segundo-factor-requerido` → pide el código<br>- `cuenta-no-habilitada` (403)<br>- `cuenta-bloqueada` (423, espera real)<br>- `429` con `Retry-After` | `POST /api/veedor/sesion` |
| `/panel/segundo-factor` | El primer ingreso de un ADMIN llega con `alcance: ALTA_SEGUNDO_FACTOR`: alta (QR local, el secreto se muestra **una sola vez**), luego un código de 6 dígitos, luego la sesión completa | `POST /api/veedor/segundo-factor/alta`, `/confirmacion` |
| Marco del panel | Navegación según `permisos[]`. Al recargar, `GET /api/veedor/yo`. Ante un `401` con `type` distinto de `credencial-invalida`/`segundo-factor-requerido`, se borra la sesión y se vuelve al ingreso. Ante un `403` inesperado, se refrescan los permisos sin cerrar la sesión | `GET /api/veedor/yo` |
| `/panel/seguridad` | Cambiar la clave, desactivar TOTP (el ADMIN no puede), «Cerrar todas mis sesiones». **Antes de actuar se avisa** que se cierran todas las sesiones | `POST /api/veedor/cuenta/clave`, `/segundo-factor/baja`, `/sesion/cierre` |

**Dónde vive el token:** en `sessionStorage`, nunca en la URL. El panel no carga scripts de terceros.

## Pruebas

- e2e simulado:
  - ingreso con y sin TOTP
  - primer ingreso de un ADMIN con alta
  - cada error de ingreso por `type`
  - sesión revocada (401) que vuelve al ingreso
  - un observador no ve acciones de veedor
- e2e real:
  - el ADMIN inicial de `docker compose up` (su clave sale del log del backend, ADR-086) da de alta el TOTP con `scripts/codigo-totp.mjs`
  - entra, cambia su clave y cierra sesión

  Para repetirlo con una base limpia: `scripts/restablecer-admin.mjs`.

## Terminado cuando

- Pasa la puerta completa.
- Se archivó y borró `feat/f5-ingreso-panel`.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md, docs/frontend/FE2-ingreso-al-panel.md y docs/api/cuentas-y-sesion.md.
Crea feat/fe2-ingreso-al-panel desde main y trae los dos commits de feat/f5-ingreso-panel (cherry-pick 95962ad 5dda089),
resolviendo el conflicto de router.tsx y adaptando los tipos al esquema generado. Completa /panel/seguridad.
Corre la puerta completa y muéstrame el resultado. Abre el PR, no lo fusiones. Si usas subagentes, usa model sonnet.
```
