# FE6 · Cuentas públicas y vecino

**Objetivo:** las dos formas de tener cuenta desde la parte pública:
- **solicitar acceso al panel**, con recuperación de clave
- **ser vecino registrado**: registrarse con su barrio, entrar, verificar el barrio con la ubicación del momento y gestionar su perfil

Cuando el vecino tiene sesión, sus reportes se envían con ella.

Rama: `feat/fe6-cuentas-y-vecino`. Depende de FE0 y del marco de sesión de FE2. Esfuerzo: 2 sesiones.

Contrato: [`cuentas-y-sesion.md`](../api/cuentas-y-sesion.md) (crear una cuenta, recuperar la clave, reenviar la
verificación, vecino registrado), F2 de [`cambios-para-frontend.md`](../api/cambios-para-frontend.md) y
[`correos-y-enlaces.md`](../api/correos-y-enlaces.md).

## Los enlaces de los correos de cuenta

Los correos de verificar, aceptar invitación (también la del vecino, que elige su clave ahí) y restablecer clave **llevan a
páginas HTML del backend** (`/api/cuentas/enlaces/*`). Esas páginas funcionan y protegen el token: el `GET` no lo gasta.
**En esta fase se usan tal cual.**

Para que el correo lleve a pantallas de la SPA (`/cuenta/verificar`, `/cuenta/invitacion`, `/cuenta/restablecer`, ya
diseñadas en la guía §5.3), el backend tiene que cambiar `MailCuentaAdapter`. Es un cambio de comportamiento, así que va
**después de la reducción (R9)** y se coordina con Sebastian. Queda como **FE6b opcional**: las tres pantallas, que
llaman a `POST /api/cuentas/verificacion`, `/invitacion` y `/clave`.

## Pantallas

**Las rutas del vecino se añaden primero a [`guia-frontend.md` §5](../diseno/guia-frontend.md#5-matriz-de-pantallas-y-datos)**, en una sección §5.5 «Vecino registrado».

| Ruta | Qué | Contrato |
|---|---|---|
| `/cuenta/solicitar` | Nombre, correo y clave → `POST /api/cuentas/registro`. Mensaje neutro: explica que habrá verificación por correo y luego aprobación de un ADMIN, sin prometer acceso | §5.3 de la guía |
| `/cuenta/olvide` | Correo → `POST /api/cuentas/restablecimiento`. **El mismo mensaje** exista o no la cuenta | ídem |
| Reenviar verificación | Desde el ingreso del panel o de `/cuenta/solicitar`: `POST /api/cuentas/verificacion/reenvio`, con respuesta neutra | ídem |
| `/vecino/registro` * | Correo, nombre, **barrio** (el mismo selector buscable de avisos) y consentimiento. **Sin clave**: llega por correo → `POST /api/cuentas/vecino`. Explica que el enlace del correo sirve para elegir la clave | «Vecino registrado» |
| `/vecino/ingreso` * | `POST /api/vecino/sesion`. Una cuenta del panel recibe la misma `401` que una clave mala; el mensaje no distingue | ídem |
| `/vecino/perfil` * | Perfil propio: nombre, barrio y casilla de avisos (`GET /api/vecino/yo`, `PATCH /api/vecino/perfil`). Cambiar de barrio **anula la verificación** y se avisa antes. «Cerrar todas mis sesiones»: `POST /api/vecino/sesion/cierre` | ídem |
| Verificar mi barrio * | Botón en el perfil. Pide la ubicación **en ese momento**: no se guarda y hay 3 intentos al día. `POST /api/vecino/verificacion-barrio`. Errores 422 por `type`: `ubicacion-fuera-del-barrio`, `ubicacion-imprecisa` | ídem |
| Reportar con sesión de vecino | Con sesión, `POST /api/reportes` y `/confirmar` van con `Authorization: Bearer`, además de `X-Dispositivo`: **manda la cuenta**. El cupo es de 5 en vez de 3. La pantalla del reporte dice «Reportas como [nombre], vecino de [barrio]» | [`reportes.md` §Quién reporta](../api/reportes.md#quién-reporta-dispositivo-o-vecino) |

**El vecino nunca entra al panel**, y las sesiones de vecino y del panel no se mezclan en la interfaz. El token del vecino
también va en `sessionStorage`.

## Pruebas

- e2e simulado: solicitar, olvidé mi clave (mensaje idéntico), registro de vecino, ingreso, perfil, cambio de barrio con aviso, verificación (éxito, fuera del barrio, imprecisa, tercer intento), reportar con sesión.
- e2e real: registro de vecino → enlace en Mailhog → página HTML del backend para elegir la clave → ingreso en la SPA → reportar → el reporte llega con `CUENTA_VERIFICADA` tras verificar el barrio con una geolocalización simulada por Playwright dentro del polígono.

## Terminado cuando

- Pasa la puerta completa.
- Ninguna pantalla deja ver si un correo tiene cuenta.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md, docs/frontend/FE6-cuentas-y-vecino.md, docs/api/cuentas-y-sesion.md
y docs/api/correos-y-enlaces.md. En feat/fe6-cuentas-y-vecino desde main: añade §5.5 «Vecino registrado» a guia-frontend.md
y construye /cuenta/solicitar, /cuenta/olvide, el reenvío, /vecino/registro, /vecino/ingreso, /vecino/perfil con verificación
de barrio, y el reporte con sesión de vecino. Los enlaces de correo de cuenta siguen en las páginas HTML del backend.
Corre la puerta completa y muéstrame el resultado. Abre el PR, no lo fusiones. Si usas subagentes, usa model sonnet.
```
