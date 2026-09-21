# Correos y enlaces

El backend envía correo en cuatro situaciones. **Cada enlace de esos correos apunta a una ruta del propio
backend**, porque el backend ya no tiene un frontend al que redirigir. Un frontend nuevo puede dejar esas
páginas tal cual, o sustituirlas por las suyas.

## De dónde sale la URL de los enlaces

`AGUAVIGIA_APP_URL_PUBLICA` (variable `APP_URL_PUBLICA` en el `.env`). En desarrollo vale por defecto
`http://localhost:8080`; **en producción es obligatoria y el backend se niega a arrancar si apunta a
`localhost`**, para no enviar enlaces que solo funcionan en la máquina del desarrollador.

## Qué correos salen y a dónde llevan

| Correo | Cuándo | Enlace | Vigencia |
|---|---|---|---|
| Confirmar suscripción | `POST /api/suscripciones` | `…/api/suscripciones/confirmar?token=…` y baja `…/cancelar?token=…` | 48 h |
| Aviso de cambio de estado | Cambia el estado de un sector suscrito | Ver el sector: `…/api/sectores/{id}` · baja: `…/api/suscripciones/cancelar?token=…` | — |
| Verificar cuenta | `POST /api/cuentas/registro` | `…/api/cuentas/enlaces/verificar?token=…` | 48 h |
| Aceptar invitación | Un ADMIN invita | `…/api/cuentas/enlaces/invitacion?token=…` | 7 días |
| Restablecer clave | `POST /api/cuentas/restablecimiento` | `…/api/cuentas/enlaces/restablecer?token=…` | 30 min |

Los enlaces de cuentas son **de un solo uso**, y pedir uno nuevo **invalida los anteriores**.

> **El enlace «ver el sector» del aviso de estado apunta hoy a la API JSON** (`/api/sectores/{id}`), porque
> no hay una pantalla a la que llevar. **Un frontend nuevo debería cambiarlo** para que apunte a su pantalla
> de sector: es el marcador `urlReportar` que arma `MailNotificacionAdapter`.

## Las páginas de cortesía de las cuentas

`/api/cuentas/enlaces/{verificar,invitacion,restablecer}` responden **HTML** a un navegador:

| Ruta | `GET` | `POST` (form) |
|---|---|---|
| `…/verificar?token=` | Pantalla con un botón «Confirmar mi correo». | Verifica y muestra el resultado. |
| `…/invitacion?token=` | Formulario para fijar la clave. | Acepta la invitación con `token` y `clave`. |
| `…/restablecer?token=` | Formulario para fijar la clave nueva. | Cambia la clave con `token` y `clave`. |

**El `GET` nunca consume el token**: solo muestra la pantalla. Es a propósito, porque los antivirus de
correo y las vistas previas abren los enlaces, y un `GET` que gastara el token dejaría al usuario con un
enlace «ya usado» antes de haberlo visto. El token solo se gasta con el `POST`.

Las páginas llevan `Cache-Control: no-store` y `<meta name="referrer" content="no-referrer">`, para que el
token no quede en cachés ni se filtre por la cabecera `Referer`.

### Si el frontend nuevo sirve sus propias pantallas

1. Apunta el correo a tu pantalla: cambia la base de los enlaces (`AGUAVIGIA_APP_URL_PUBLICA`) **y** la ruta
   que arma cada adaptador (`MailCuentaAdapter`, `MailNotificacionAdapter`).
2. Tu pantalla lee el `token` de la URL y llama a la API JSON equivalente:

| Pantalla | Llamada |
|---|---|
| Verificar correo | `POST /api/cuentas/verificacion?token=…` |
| Aceptar invitación | `POST /api/cuentas/invitacion` `{ token, clave }` |
| Restablecer clave | `POST /api/cuentas/clave` `{ token, clave }` |
| Confirmar suscripción | `POST /api/suscripciones/confirmar?token=…` con `Accept: application/json`, **al pulsar un botón** |
| Darse de baja | `POST /api/suscripciones/cancelar?token=…` con `Accept: application/json`, **al pulsar un botón** |

3. **Quita el `token` de la URL** después de leerlo (`history.replaceState`) y no cargues scripts de
   terceros en esas pantallas.

## Probar los correos en desarrollo

Todo correo que envíe el backend en desarrollo aparece en **Mailhog**: `http://localhost:8025`. No sale
nada a Internet.
