# Correos y enlaces

El backend envía correo en cinco situaciones. Los enlaces de suscripción llevan a las pantallas propias del
frontend; los enlaces de cuenta siguen en el backend hasta F5.

## De dónde sale la URL de los enlaces

`aguavigia.app.url-frontend` (`APP_URL_FRONTEND` en `.env`) es la base de los enlaces de suscripción.
En desarrollo vale `http://localhost:5173`. `aguavigia.app.url-publica` (`APP_URL_PUBLICA`) sigue siendo la
base de la API y de los enlaces de cuenta hasta F5; en desarrollo, `http://localhost:8081`. En producción
ambas deben señalar orígenes públicos: el backend rechaza valores locales o sin host.

## Qué correos salen y a dónde llevan

| Correo | Cuándo | Enlace | Vigencia |
|---|---|---|---|
| Confirmar suscripción | `POST /api/suscripciones` | `…/avisos/confirmar?token=…` y baja `…/avisos/baja?token=…` | 48 h |
| Aviso de cambio de estado | Cambia el estado de un sector suscrito | Ver el sector: `…/sectores/{id}` · baja: `…/avisos/baja?token=…` | — |
| Verificar cuenta | `POST /api/cuentas/registro` | `…/api/cuentas/enlaces/verificar?token=…` | 48 h |
| Aceptar invitación | Un ADMIN invita | `…/api/cuentas/enlaces/invitacion?token=…` | 7 días |
| Restablecer clave | `POST /api/cuentas/restablecimiento` | `…/api/cuentas/enlaces/restablecer?token=…` | 30 min |

Los enlaces de cuentas son **de un solo uso**, y pedir uno nuevo **invalida los anteriores**.

`urlReportar` de `MailNotificacionAdapter` lleva a la ficha del sector en la SPA.

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

1. Apunta el correo a tu pantalla: cambia la base y la ruta que arma el adaptador correspondiente. F4 ya
   usa `APP_URL_FRONTEND` en `MailNotificacionAdapter`; `MailCuentaAdapter` se aborda en F5.
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
