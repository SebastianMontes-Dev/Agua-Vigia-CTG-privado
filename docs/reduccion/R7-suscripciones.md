# R7 · Suscripciones

**Objetivo:** mover a `suscripciones/` los avisos. Son las suscripciones por correo (doble opt-in, confirmar, cancelar),
las notificaciones cuando cambia un sector y Telegram, que está apagado sin `TELEGRAM_BOT_TOKEN` (ADR-066).

Riesgo: medio. Choca con la rama `feat/f4-avisos` de Yordy (ver abajo). Esfuerzo: 1–2 sesiones.

## Antes de empezar: decidir con Yordy

La rama `feat/f4-avisos` toca backend que esta fase mueve:
- `infrastructure/mail/MailNotificacionAdapter.java`
- `ValidacionDeUrlPublicaProd.java` (nuevo, exige la URL del frontend en el perfil `prod`)
- `application-dev.yml`, `application-docker.yml`, `docker-compose.yml`, `.env.example`
- sus tests

Opciones:
1. **Recomendada:** Yordy rebasa `feat/f4-avisos` sobre `main` y fusiona solo esos cambios de backend **antes** de R7. Luego R7 los mueve como cualquier otra clase.
2. Se descartan y Yordy los rehace sobre la estructura nueva.

No se empieza R7 sin esta decisión anotada aquí:

> Decisión: _pendiente_

## Estructura destino

```
suscripciones/
├── Suscripcion.java               @Document("suscripciones") + SuscripcionId, EstadoSuscripcion
├── SuscripcionAlmacen.java        SuscripcionRepository + adaptador + repo Spring
├── SuscripcionService.java        Suscribirse, confirmar, cancelar (antes 3 puertos + 3 servicios)
├── NotificacionService.java       NotificarSuscripcionesService + NotificacionPort + MailNotificacionAdapter
├── NotificarSuscripcionesListener.java   (@Async @EventListener de SectorActualizadoEvent, igual)
├── SuscripcionController.java     mismas rutas, @Tag y @Operation
├── SuscripcionDtos.java           SolicitudSuscripcion (con @Size(min=1,max=211)), SuscripcionRespuesta
├── EstadoServicioLegible.java
└── telegram/
    ├── SuscripcionTelegram.java   @Document("suscripciones_telegram") + ChatTelegramId, MensajeTelegram, su almacén
    ├── TelegramService.java       AtenderTelegram + ProcesarMensajeTelegram + EnviarAlertaPush
    ├── EnvioTelegram.java · RecepcionTelegram.java   interfaces: 2 implementaciones reales cada una
    ├── TelegramApiAdapter · TelegramDesactivadoAdapter
    ├── TelegramSondeoJob          (3 s, igual)
    └── AlertaPushSectorListener   (@Async @EventListener, igual)
```

**Puertos de entrada que se absorben (6):** `AtenderTelegram`, `ProcesarMensajeTelegram`, `CancelarSuscripcion`,
`ConfirmarSuscripcion`, `Suscribirse` y `EnviarAlertaPush`. `SuscripcionApiMapper` también cae.

## Lo delicado

- **Los enlaces de los correos** (confirmar y baja) y las plantillas de `resources/plantillas-correo/` no cambian. Lo comprueba `verificar-flujos.mjs` leyendo Mailhog.
- **Los listeners siguen siendo `@Async`**, porque un correo lento no puede frenar el recálculo de un sector.
- **Telegram queda apagado:** sin token se usa `TelegramDesactivadoAdapter`, igual que hoy.

## Terminado cuando

- La puerta está en verde, con el recorrido suscribirse → confirmar por enlace → cambio de sector → correo → baja por enlace.
- `verificar-datos.mjs` muestra las colecciones de suscripciones sin cambios.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R7-suscripciones.md.
Comprueba que la «Decisión» sobre feat/f4-avisos esté anotada; si está pendiente, detente y pregúntame.
Ejecuta R7 en refactor/reduccion-backend (antes: git merge main) según la estructura destino. Listeners siguen @Async,
enlaces y plantillas de correo idénticos, Telegram desactivado sin token. Tests: mismos casos y aserciones.
Corre la puerta completa y muéstrame la salida. No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
