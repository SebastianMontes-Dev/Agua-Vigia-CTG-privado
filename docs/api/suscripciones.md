# Suscripciones por correo

Alertas de cambio de estado de los sectores que la persona elija, sin cuenta y sin contraseña. Solo un
correo. **Doble confirmación** (*doble opt-in*): el correo no recibe nada hasta que su dueño lo confirme.

## Rutas

| Método y ruta | Para qué | Límite por IP |
|---|---|---|
| `POST /api/suscripciones` | Pedir avisos. `201`. | 10 / 10 min |
| `GET /api/suscripciones/confirmar?token=…` | **Página** con un botón «Confirmar» (el enlace del correo). No confirma nada. | 10 / 10 min |
| `POST /api/suscripciones/confirmar?token=…` | Confirmar de verdad (lo que hace el botón). | 10 / 10 min |
| `GET /api/suscripciones/cancelar?token=…` | **Página** con un botón «Darme de baja» (el enlace de **todo** correo). No cancela nada. | 10 / 10 min |
| `POST /api/suscripciones/cancelar?token=…` | Darse de baja de verdad. | 10 / 10 min |

## `POST /api/suscripciones`

```json
{ "correo": "ana@example.com", "sectorIds": ["bocagrande", "manga"] }
```

Respuesta `201`:

```json
{ "id": "…", "correo": "ana@example.com", "sectorIds": ["bocagrande", "manga"],
  "estado": "PENDIENTE_CONFIRMACION", "creadaEn": "2026-08-08T15:30:00Z" }
```

- Un correo puede seguir **varios sectores** en una sola suscripción.
- Si **algún** sector no existe, se rechaza **todo** con `400` (no se suscribe a medias).
- El `token` **no** viaja en la respuesta: solo llega por correo, que es lo que prueba que la dirección
  es de quien la escribió.
- **Mostrar siempre un mensaje neutro** («si la dirección es válida, te enviamos un correo»).

## Estados

```
PENDIENTE_CONFIRMACION ──confirmar──▶ CONFIRMADA ──cancelar──▶ CANCELADA
        │                                                          ▲
        └──────────────────────── cancelar ────────────────────────┘
```

- El enlace de confirmación **vence a las 48 horas**.
- Confirmar una suscripción ya confirmada es idempotente. Confirmar una **cancelada** da `409`.
- Cancelar es idempotente.

## Los enlaces del correo: el `GET` muestra, el `POST` actúa

Los enlaces de los correos son `GET`, y un antivirus o una vista previa de enlaces los abre sin que nadie los
pida. Por eso el `GET` **no cambia nada**: devuelve una página HTML con un botón, y el botón hace `POST` a la
misma ruta (`ADR-054`). Un enlace ya enviado antes de este cambio sigue funcionando: ahora abre la página con el botón.

| Llamada | Qué hace | `Accept` |
|---|---|---|
| `GET …/confirmar?token=…` o `…/cancelar?token=…` | Página con el botón; no consume el token. | Solo `text/html`. Con `application/json` responde `406`. |
| `POST …/confirmar?token=…` o `…/cancelar?token=…` (el token puede ir también como campo del formulario) | Ejecuta la acción. | `text/html` → página de resultado; `application/json` → el JSON de la suscripción (o el error RFC 7807). |

Un frontend propio puede:
1. **Dejar las páginas del backend** tal cual (cero trabajo), o
2. **Servir su propia pantalla** con un botón que llame a `POST` con `Accept: application/json`. En ese caso, el
   correo debe apuntar a esa pantalla: la base de los enlaces sale de `AGUAVIGIA_APP_URL_PUBLICA`
   (ver [Correos y enlaces](correos-y-enlaces.md)). **La llamada se hace al pulsar el botón, no al cargar la pantalla.**

Errores: `400` con `type: peticion-invalida` si el token no existe o venció; `409` si intentas confirmar
una suscripción ya cancelada.

## Qué reciben y cuándo

- **Al confirmar:** desde ese momento, un correo por cada cambio de estado de cualquiera de sus sectores.
- **Cada correo lleva** el nombre del sector, el estado nuevo, un enlace para verlo y un **enlace de baja
  en un clic** (Ley 1581 de 2012). También el correo de confirmación lo lleva.
- **La baja elimina el correo** (RNF009): el registro se conserva sin dato personal (qué sectores, cuándo),
  para las estadísticas.

## Ideas para la interfaz

- Un solo formulario: correo + selector de sectores (con búsqueda: son 211).
- Tras enviar, una pantalla de «revisa tu correo», con el aviso de que vence en 48 h.
- Nunca confirmar por el usuario ni pedirle más datos: el correo es lo único que se guarda.
