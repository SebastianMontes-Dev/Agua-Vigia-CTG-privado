# Cuentas y sesión

Solo el **panel** (`/api/veedor/**`) exige sesión. Las cuentas son de veedores, observadores y
administradores; el ciudadano no tiene cuenta.

## Rutas

### Públicas (no exigen token)

| Método y ruta | Para qué | Respuesta |
|---|---|---|
| `POST /api/veedor/sesion` | Iniciar sesión. | `200` con la sesión |
| `POST /api/cuentas/registro` | Pedir una cuenta. | `202`, sin cuerpo |
| `POST /api/cuentas/verificacion?token=…` | Verificar el correo. | `204` |
| `POST /api/cuentas/invitacion` | Aceptar una invitación y fijar la clave. | `204` |
| `POST /api/cuentas/restablecimiento` | Pedir restablecer la clave. | `202`, siempre |
| `POST /api/cuentas/clave` | Fijar la clave nueva con el token del correo. | `204` |
| `GET/POST /api/cuentas/enlaces/{verificar,invitacion,restablecer}` | **Páginas HTML** de cortesía para los enlaces del correo. | HTML |

### Con sesión

| Método y ruta | Permiso |
|---|---|
| `POST /api/veedor/sesion/cierre` | solo autenticado |
| `GET /api/veedor/yo` | solo autenticado |
| `POST /api/veedor/segundo-factor/alta` \| `/confirmacion` \| `/baja` | `CONFIGURAR_SEGUNDO_FACTOR` |
| `GET/POST/PATCH /api/veedor/usuarios/**`, `/auditoria` | ver [Panel del veedor](panel-veedor.md) |

## Iniciar sesión

`POST /api/veedor/sesion`

```json
{ "correo": "ana@example.com", "clave": "una clave larga y única", "codigoTotp": "123456" }
```

`codigoTotp` se **omite en el primer intento**. Respuesta `200`:

```json
{
  "token": "eyJhbGciOi…", "usuarioId": "…", "nombre": "Ana", "correo": "ana@example.com",
  "rol": "VEEDOR",
  "permisos": ["VER_PANEL", "MODERAR_REPORTES", "GESTIONAR_CORTES", "REVISAR_INGESTA", "CONFIGURAR_SEGUNDO_FACTOR"],
  "alcance": "COMPLETO"
}
```

- **Guarda el `token`** y mándalo en cada petición del panel: `Authorization: Bearer <token>`.
- **Pinta la interfaz con `permisos[]`**, no con el rol: el permiso efectivo es el del rol más lo concedido
  menos lo revocado a esa cuenta.
- El token es un JWT firmado (HS256) que **caduca a las 8 horas y no se renueva**. No hay endpoint de
  refresco: al caducar, la siguiente petición da `401` y hay que volver a iniciar sesión.
- Al recargar la página, `GET /api/veedor/yo` devuelve la cuenta actual sin pedir la clave otra vez.

### Los errores del login, y qué hacer con cada uno

| Código | `type` | Qué significa | Reacción de la interfaz |
|---|---|---|---|
| `401` | `credencial-invalida` | Correo o clave incorrectos. | «Correo o clave incorrectos». **No** distingas cuál. |
| `401` | `segundo-factor-requerido` | La clave era correcta y falta el código. | **Pide el código y reintenta** con `codigoTotp`. No lo trates como clave mala. |
| `403` | `cuenta-no-habilitada` | La cuenta existe pero no puede entrar. La propiedad `estado` dice por qué. | Mensaje según el estado (ver abajo). |
| `423` | `cuenta-bloqueada` | Demasiados intentos fallidos. La propiedad `segundosRestantes` dice cuánto. | «Espera N minutos». |
| `429` | `limite-de-peticiones-excedido` | Demasiados intentos desde esta IP. `Retry-After`. | «Espera unos minutos». |

**Freno de fuerza bruta:** 5 intentos por IP cada 5 minutos, y además 5 fallos por cuenta en 15 minutos
bloquean esa cuenta 15 minutos (`423`). Son dos frenos distintos.

## Estados de una cuenta

```
registro abierto:  PENDIENTE_VERIFICACION ─(enlace del correo)─▶ PENDIENTE_APROBACION ─(un ADMIN aprueba)─▶ ACTIVA
invitación:        INVITADA ─(la persona fija su clave)──────────────────────────────────────────────────▶ ACTIVA
                   ACTIVA ⇄ SUSPENDIDA          PENDIENTE_APROBACION ─(un ADMIN rechaza)─▶ RECHAZADA
```

| Estado | ¿Puede entrar? |
|---|---|
| `ACTIVA` | Sí. |
| `PENDIENTE_VERIFICACION` | No: falta abrir el enlace del correo. |
| `PENDIENTE_APROBACION` | No: espera a un ADMIN. |
| `INVITADA` | No: falta fijar la clave. |
| `SUSPENDIDA` / `RECHAZADA` | No. |

## Crear una cuenta

**Registro abierto**

1. `POST /api/cuentas/registro` `{ correo, nombre, clave }` → `202`.
2. La persona abre el enlace de su correo → `POST /api/cuentas/verificacion?token=…` → `204`.
3. Un ADMIN la aprueba (`PATCH /api/veedor/usuarios/{id}/aprobacion`). Hasta entonces no entra.

**Invitación**

1. Un ADMIN: `POST /api/veedor/usuarios/invitaciones` `{ correo, nombre, rol }` → `201`.
2. La persona abre el enlace → `POST /api/cuentas/invitacion` `{ token, clave }` → `204`. Queda `ACTIVA`.

**Vigencia de los enlaces:** verificación 48 h · invitación 7 días · restablecer clave 30 min. Son de un solo
uso, y pedir uno nuevo **invalida los anteriores**.

**Política de clave:** entre **12 y 128 caracteres**, sin repetir demasiado los mismos caracteres, y no más
de 72 bytes en UTF-8. La interfaz debe validarlo en el cliente y mostrar el `detail` del `400` si falla.

### Recuperar la clave

`POST /api/cuentas/restablecimiento` `{ correo }` responde **siempre `202`**, exista o no la cuenta: si
respondiera distinto, cualquiera podría averiguar qué correos tienen cuenta. **Muestra siempre el mismo
mensaje.** Luego `POST /api/cuentas/clave` `{ token, clave }` → `204`, y **se cierran todas las sesiones**
de esa cuenta.

Las cuentas tienen un límite propio: `/api/cuentas/**` admite **10 peticiones por IP cada 10 minutos**.

## Segundo factor (TOTP)

Códigos de 6 dígitos de una app de autenticación (RFC 6238), franja de 30 s, se tolera la franja
anterior. **Cada código sirve una sola vez** en el login.

| Rol | Segundo factor |
|---|---|
| `ADMIN` | **Obligatorio.** No puede desactivarlo. |
| `VEEDOR`, `OBSERVADOR` | Opcional. |

### Alta (dos pasos)

```
POST /api/veedor/segundo-factor/alta          → { uri, secreto }
POST /api/veedor/segundo-factor/confirmacion  { codigo }  → sesión nueva (alcance COMPLETO)
```

1. `alta` genera el secreto **sin activarlo todavía**. `uri` es un `otpauth://…`: pínta**lo** como **código
   QR**. `secreto` es el mismo dato en texto, **por si la cámara falla**. **Se muestra una sola vez: no hay
   ruta para volver a leerlo.**
2. La persona escanea el QR y escribe el primer código en `confirmacion`. Solo entonces queda activo.

Activarlo en el primer paso convertiría un QR mal escaneado en una cuenta perdida.

**Rehacer el alta** (cambiar de teléfono) exige el **código actual** en el cuerpo:
`POST /alta { "codigo": "123456" }`. Sin él → `409`; con uno incorrecto → `401`. Es a propósito: sin esto,
quien robara un token podría sustituir el segundo factor por el suyo.

### Primer ingreso de un ADMIN

Un ADMIN sin segundo factor confirmado inicia sesión con `alcance: "ALTA_SEGUNDO_FACTOR"`. Ese token
**solo** sirve para `/api/veedor/segundo-factor/**`; cualquier otra ruta responde `403`. Flujo:

```
1. POST /api/veedor/sesion                     → { alcance: "ALTA_SEGUNDO_FACTOR", token }
2. POST /api/veedor/segundo-factor/alta        → mostrar QR
3. POST /api/veedor/segundo-factor/confirmacion { codigo }
                                               → { alcance: "COMPLETO", token nuevo }   ← guardar este
```

La respuesta del paso 3 es una sesión completa: la persona **no** vuelve a escribir su clave.

**Desactivar** (roles no ADMIN): `POST /api/veedor/segundo-factor/baja { codigo }` → `204`. Exige un
código válido, y **cierra todas las sesiones**.

Límite: `/api/veedor/segundo-factor/**` admite 10 peticiones por IP cada 5 minutos.

## Roles y permisos

| Rol | Permisos |
|---|---|
| `OBSERVADOR` | `VER_PANEL`, `CONFIGURAR_SEGUNDO_FACTOR` |
| `VEEDOR` | Los del observador + `MODERAR_REPORTES`, `GESTIONAR_CORTES`, `REVISAR_INGESTA` |
| `ADMIN` | Todos, incluidos `GESTIONAR_USUARIOS` y `VER_AUDITORIA` |

Un ADMIN puede **conceder o revocar permisos sueltos** a una cuenta. `CONFIGURAR_SEGUNDO_FACTOR` no se puede
revocar. Un mismo permiso no puede estar en `concedidos` y `revocados` a la vez (`400`).

Reglas de autoprotección del administrador: **no puede cambiar su propio acceso** ni dejar el sistema sin
ningún ADMIN activo.

## Cuándo se cierran las sesiones de una cuenta

El servidor mantiene una marca de revocación por usuario: cualquier token **emitido antes** de ella deja de
valer. La marca se pone al: cerrar sesión (cierra **todas** las sesiones de la cuenta), suspender, rechazar,
cambiar permisos o rol, restablecer la clave y desactivar el segundo factor.

En el cliente eso se ve como un `401` de golpe. **Ante un `401` con `type` distinto de
`credencial-invalida`/`segundo-factor-requerido`: borrar el token y volver al login.**

Si Redis no responde, **la sesión se rechaza** (falla cerrado): es más seguro que dejar pasar un token
posiblemente revocado.

## Notas de seguridad para el cliente

- **Dónde guardar el token:** `sessionStorage` en memoria es lo más sencillo; `localStorage` sobrevive al
  cierre de la pestaña pero es legible por cualquier script de la página. No hay cookie `HttpOnly` porque
  la API usa cabecera `Authorization`. Sea cual sea la elección, **la interfaz no debe cargar scripts de
  terceros** en el panel.
- **Nunca** pongas el token ni la clave en una URL.
- Los enlaces de restablecer y verificar llevan un `token` en la URL: la página que los reciba debe usar
  `<meta name="referrer" content="no-referrer">` para no filtrarlo.
