# FE5 · Administración

**Objetivo:** lo que solo hace un ADMIN. Gestionar cuentas (invitar, aprobar, rechazar, suspender, reactivar, ajustar
permisos), leer la auditoría y ver las métricas del sistema.

Rama: `feat/fe5-administracion`. Depende de FE2. Esfuerzo: 2 sesiones.

Contrato: [`panel-veedor.md` §Gestión de cuentas](../api/panel-veedor.md#gestión-de-cuentas-admin),
[`cuentas-y-sesion.md`](../api/cuentas-y-sesion.md#roles-y-permisos) (roles, permisos, autoprotección), y las secciones F4
(`sintetica`) y F6 (métricas) de [`cambios-para-frontend.md`](../api/cambios-para-frontend.md).

## Pantallas

**`/panel/sistema` se añade primero a [`guia-frontend.md` §5.4](../diseno/guia-frontend.md#54-panel-del-veedor).**

| Ruta | Permiso | Qué |
|---|---|---|
| `/panel/cuentas` | `GESTIONAR_USUARIOS` | **La lista:** `GET /api/veedor/usuarios?estado=`, paginada por cabeceras.<br>- **Hay 30 000 cuentas sintéticas**: por defecto se ocultan las de `sintetica: true`, con un interruptor para verlas<br>- Nunca se cuentan como personas; frase fija en F4<br><br>**Las acciones:**<br>- invitar: `POST …/usuarios/invitaciones`, con correo, nombre y rol<br>- reenviar una invitación: `POST …/usuarios/{id}/invitacion/reenvio`<br>- aprobar con rol y permisos, rechazar, suspender, reactivar: `PATCH …/{id}/aprobacion|rechazo|suspension|reactivacion`<br>- ajustar permisos sueltos: `PATCH …/{id}/permisos` con `concedidos`/`revocados`<br><br>**Reglas:**<br>- no se puede editar el propio acceso<br>- no se ofrece quitar el último ADMIN<br>- `CONFIGURAR_SEGUNDO_FACTOR` no se puede revocar<br>- un permiso no puede estar en las dos listas<br>- cada acción se confirma con el nombre de la cuenta afectada<br>- se avisa que suspender o cambiar permisos cierra las sesiones de esa persona |
| `/panel/auditoria` | `VER_AUDITORIA` | `GET /api/veedor/auditoria`, paginada y de solo lectura: fecha, quién actuó, sobre quién y qué acción |
| `/panel/sistema` * | `VER_PANEL` | `GET /api/veedor/sistema/metricas`. Son **métricas de calibración** (ADR-097): se presentan como tabla con su unidad y su guía de lectura, sin semáforos ni umbrales inventados |

## Pruebas

- e2e simulado:
  - lista con sintéticas ocultas y visibles
  - paginación
  - invitar
  - cada acción del ciclo de una cuenta
  - autoprotección (sin botón sobre uno mismo)
  - auditoría vacía y con datos
  - métricas
- e2e real: el ADMIN invita a un OBSERVADOR y la invitación llega a Mailhog. El enlace abre la página HTML del backend, ver FE6. El observador fija su clave, entra y **no ve acciones** de veedor; esto cierra la comprobación pendiente de FE3. El ADMIN le concede `MODERAR_REPORTES` y el observador ve la acción tras volver a entrar.

## Terminado cuando

- Pasa la puerta completa.
- La lista de cuentas responde con fluidez con las 30 000 sintéticas en la base: se pagina y no se cargan todas.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md, docs/frontend/FE5-administracion.md, docs/api/panel-veedor.md
y docs/api/cuentas-y-sesion.md. En feat/fe5-administracion desde main: añade /panel/sistema a guia-frontend.md §5.4 y
construye /panel/cuentas, /panel/auditoria y /panel/sistema con las reglas de autoprotección y el tratamiento de cuentas
sintéticas. Corre la puerta completa y muéstrame el resultado. Abre el PR, no lo fusiones. Si usas subagentes, usa model sonnet.
```
