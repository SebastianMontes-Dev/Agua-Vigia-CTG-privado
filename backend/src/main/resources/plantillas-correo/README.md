# Plantillas de correo

El HTML de los correos. Se ven abriendo el archivo en el navegador.

| Archivo | Cuándo se envía | Lo envía |
|---|---|---|
| `confirmar-suscripcion.html` | Al pedir avisos de un sector: doble opt-in, antes de guardar nada | `MailNotificacionAdapter` |
| `cambio-de-estado.html` | Cuando cambia el estado del servicio de un sector suscrito | `MailNotificacionAdapter` |
| `cuenta-con-enlace.html` | Verificar el correo, aceptar una invitación o restablecer la clave (un botón con el enlace) | `MailCuentaAdapter` |
| `cuenta-aviso.html` | Avisos de cuenta sin enlace (por ejemplo, «tu clave cambió») | `MailCuentaAdapter` |

## Marcadores

Van como `{{nombre}}` y los sustituye `PlantillaCorreo` con `String.replace`, no un motor de
plantillas: con marcadores fijos, Thymeleaf sería abstracción prematura.

- **`confirmar-suscripcion.html`**: `nombreSector` · `urlConfirmacion` · `urlBaja` · `horasVigencia`
- **`cambio-de-estado.html`**: `nombreSector` · `estadoTitular` · `estadoEtiqueta` · `estadoDetalle` ·
  `estadoColorFondo` · `estadoColorBorde` · `estadoColorTexto` · `actualizadoLegible` · `urlReportar` · `urlBaja`
- **`cuenta-con-enlace.html`**: `nombre` · `titulo` · `mensaje` · `preencabezado` · `textoBoton` · `urlAccion` · `vigencia`
- **`cuenta-aviso.html`**: `nombre` · `titulo` · `mensaje`

## Decisiones que ya están tomadas aquí

**`urlBaja` va en todo correo de suscripción**, incluido el de confirmación, no solo en un centro de
preferencias. Es la baja en un clic que exige la Ley 1581/2012 (`RF015`) y es criterio de terminado.

**Los enlaces de los correos son `GET` que solo muestran un botón** (`ADR-054`): un antivirus o una vista
previa de enlaces los abre sin que nadie lo pida, así que la acción ocurre al pulsar, con un `POST`.

**Tablas y estilos en línea** porque los clientes de correo no aplican CSS moderno. El `<style>` del
encabezado lleva solo lo que puede perderse sin romper nada: los ajustes de pantalla angosta y el tema
oscuro. Lo esencial va en línea, en cada etiqueta.

**Tema oscuro incluido**, con la paleta de `DESIGN.md`. Los clientes que no lo soportan se quedan en
la paleta clara sin degradarse.

**Sin webfonts.** Misma regla que `DESIGN.md` §4: pilas de fuentes del sistema.

## Historial

Existió un `aviso-corte.html` pensado para avisar de un corte anunciado con la cita textual del boletín
(`ADR-006`). Nunca se cargó desde código: el aviso real es `cambio-de-estado.html`. Se borró el 2026-09-21
a petición del dueño; sigue en el historial de git.

Para probar el envío: Mailhog ya está en `docker-compose.yml` (SMTP en `1025`, interfaz en
`http://localhost:8025`).
