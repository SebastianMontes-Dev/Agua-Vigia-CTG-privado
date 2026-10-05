# Correos y enlaces

El backend envía correo en seis situaciones. Los enlaces de suscripción llevan a las pantallas propias del
frontend; los enlaces de cuenta siguen en el backend hasta F5.

## De dónde sale la URL de los enlaces

`aguavigia.app.url-frontend` (`APP_URL_FRONTEND` en `.env`) es la base de los enlaces de suscripción.
En desarrollo vale `http://localhost:5173`. `aguavigia.app.url-publica` (`APP_URL_PUBLICA`) sigue siendo la
base de la API y de los enlaces de cuenta hasta F5; en desarrollo, `http://localhost:8081`. El proyecto corre solo
en local (`ADR-080`).

## Qué correos salen y a dónde llevan

| Correo | Cuándo | Enlace | Vigencia |
|---|---|---|---|
| Confirmar suscripción | `POST /api/suscripciones` | `…/avisos/confirmar?token=…` y baja `…/avisos/baja?token=…` | 48 h |
| Aviso de cambio de estado | Cambia el estado de un sector suscrito | Ver el sector: `…/sectores/{id}` · baja: `…/avisos/baja?token=…` · **si el barrio quedó sin servicio o con presión baja:** «Sí, ya volvió el agua» → `…/sectores/{id}/restablecimiento?token=…` (pantalla del frontend) | 24 h el enlace de restablecimiento |
| Verificar cuenta | `POST /api/cuentas/registro` | `…/api/cuentas/enlaces/verificar?token=…` | 48 h |
| Aceptar invitación | Un ADMIN invita | `…/api/cuentas/enlaces/invitacion?token=…` | 7 días |
| Restablecer clave | `POST /api/cuentas/restablecimiento` | `…/api/cuentas/enlaces/restablecer?token=…` | 30 min |
| Aviso de cambio de acceso | Un ADMIN aprueba, rechaza, suspende, reactiva o cambia permisos; cambia la clave; alguien intenta registrarse con un correo que ya tiene cuenta (`MailCuentaAdapter.avisarCambioDeAcceso`, llamado desde `AdministrarCuentaService`, `CambiarClaveService`, `RestablecerClaveService` y `RegistrarUsuarioService.java:79`) | Sin enlace: solo informa | — |

Los enlaces de cuentas son **de un solo uso**, y pedir uno nuevo **invalida los anteriores**.

En la demo de carga con `--sin-correo` (`scripts/carga/demo.mjs`, `docker-compose.carga.yml:12`) el backend arranca con
`aguavigia.correo.cuentas-habilitado=false` y los **cuatro correos de cuentas** (verificar, invitación, restablecer y aviso de cambio de acceso) **se descartan** (`CorreoDeCuentaDescartadoAdapter`,
`ADR-088`): las cuentas se crean igual, pero una invitación hecha así no se puede aceptar. Los correos de suscripción no
pasan por ahí.

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

## «¿Ya volvió el agua?» con un toque

El aviso de un barrio **sin servicio** o con **presión baja** trae un enlace firmado a una pantalla **del frontend**:
`{urlFrontend}/sectores/{id}/restablecimiento?token=…`. Esa pantalla muestra un botón y, al pulsarlo, llama a
`POST /api/sectores/{id}/restablecimiento?token=…` (sin sesión ni `X-Dispositivo`). Debe ser un botón y no la carga de la página:
algunos clientes de correo abren los enlaces por su cuenta y no deben votar.

- El token lo firma el servidor y lleva barrio, suscripción y vencimiento (24 h, `aguavigia.restablecimiento.horas-vigencia-enlace`).
  No es de un solo uso: pulsarlo otra vez vuelve a pasar por el cupo de reportes (3 por barrio cada 30 min), no suma votos.
- Registra un reporte `SERVICIO_RESTABLECIDO` de esa suscripción: **un toque no cambia el estado solo**; cuenta como un voto más
  para el quórum de restablecimiento (el de cada barrio es más bajo que el de una avería, ver `reportes.md`).
- `201` con el reporte · `403 enlace-invalido` si venció, es de otro barrio o la suscripción ya no está confirmada (mismo error
  en todos los casos) · `429` si agotó el cupo · `400` si falta el token.
