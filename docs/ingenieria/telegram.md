# Alertas por Telegram (RF041)

> **Estado (2026-09-24):** construido y **armado, pero apagado**. Se activa poniendo `TELEGRAM_BOT_TOKEN` y reiniciando el
> backend. Está probado contra un servidor HTTP falso que imita la API de Telegram y contra un Mongo real; **no se ha
> probado contra Telegram real**, porque todavía no existe el bot. Decisión y alternativas: `ADR-066`.

## Qué hace

Una persona le escribe al bot desde un chat privado y sigue uno o varios sectores. Cuando un sector cambia de estado (por
consenso ciudadano, por un corte oficial o por la ingesta), el bot le avisa a cada chat que lo sigue:

> AguaVigía: el sector MANGA ahora está sin servicio.

No hay registro ni cuenta: el propio mensaje al bot es la confirmación. El sistema guarda solo el **id del chat** (un dato
personal) mientras siga al menos un sector; la baja lo borra.

| Comando | Qué hace |
|---|---|
| `/suscribir <sector>` | Sigue un sector (hasta 10 por chat). Acepta el nombre como aparece en el mapa, sin importar mayúsculas ni tildes |
| `/baja <sector>` | Deja de seguir ese sector |
| `/baja` | Deja de seguir todo y borra el chat de la base |
| `/estado <sector>` | Dice cómo está el sector ahora («sin datos verificados todavía» si nadie lo ha verificado) |
| `/mis` | Lista los sectores que sigue |
| `/ayuda`, `/start` | Explica los comandos y qué se guarda |

Solo atiende **chats privados**: los grupos se ignoran.

## Cómo conectarlo al bot real

1. En Telegram, abre una conversación con **`@BotFather`** y envía `/newbot`. Elige un nombre y un usuario que termine en `bot`.
   BotFather te devuelve un **token** (parece `123456789:AA…`). Es una credencial: no la publiques ni la subas a git.
2. Opcional, para que el menú del bot muestre los comandos: a `@BotFather` envíale `/setcommands`, elige tu bot y pega:
   ```
   suscribir - Seguir un sector (ej.: /suscribir Bocagrande)
   baja - Dejar de seguir un sector, o todos
   estado - Ver cómo está un sector
   mis - Ver los sectores que sigues
   ayuda - Qué puedo hacer
   ```
3. Pon el token en el `.env` de la raíz, sin comillas ni espacios:
   ```
   TELEGRAM_BOT_TOKEN=<el token>
   ```
4. Recrea el backend para que lea el `.env`:
   ```powershell
   docker compose up -d --force-recreate backend
   ```
5. Comprueba el log. Con token **no** debe aparecer «Telegram desactivado»:
   ```powershell
   docker logs aguavigia-backend --tail 50
   ```
6. En Telegram, busca tu bot, escribe `/start` y luego `/suscribir Bocagrande`. Debería responder en unos 3 segundos.
7. Para provocar un aviso, reporta un corte: son 6 reportes distintos de `SIN_AGUA` en `bocagrande` (umbral
   `max(3, ceil(población × 0,001))`), o los pasos de `guion-de-demo.md` §2 con un sector de umbral bajo. Al cambiar el estado
   te llega el mensaje.

## Cómo funciona por dentro

- **Recibe por sondeo, no por webhook.** Cada 3 segundos el backend pregunta a Telegram por mensajes nuevos (`getUpdates`). Un
  webhook exigiría una URL pública con HTTPS, imposible en local (`ADR-057`). Consecuencia: la respuesta tarda unos segundos.
- **Solo una instancia del backend puede sondear el mismo bot.** Dos consumidores reciben el error 409 de Telegram. Hoy hay una
  sola réplica.
- **El sondeo recuerda su posición solo en memoria.** Tras un reinicio Telegram reenvía lo que no se confirmó (hasta 24 horas), y
  los comandos son idempotentes, así que repetirlos no hace daño.
- **Sin token no pasa nada:** el canal queda apagado, no envía ni recibe, y el resto de la plataforma funciona igual.
- **Un chat que bloquea al bot se da de baja solo.** Un fallo pasajero de Telegram no da de baja a nadie.
- El token viaja en la URL de la API de Telegram. El código nunca lo escribe en un log ni en un mensaje de error.

## Si algo falla al conectar

| Síntoma | Causa probable |
|---|---|
| El log dice `Telegram respondió 401` | Token mal copiado, o revocado |
| El log dice `Telegram respondió 409` | Otro proceso está leyendo el mismo bot, o el bot tiene un webhook activo: se quita con `https://api.telegram.org/bot<token>/deleteWebhook` |
| El bot no contesta y el log no dice nada | El backend no leyó el `.env`: recréalo con el comando del paso 4 |
| No llegan avisos | Ese sector no cambió de estado, o el chat no lo sigue (`/mis`) |

## Lo que sigue sin verificarse

Todo lo anterior está probado contra un servidor falso. Lo que **solo se sabrá al conectar el bot real**: que los mensajes de
respuesta se vean como esperas, que el formato de las actualizaciones de Telegram coincida con el que imita la prueba y que los
tiempos de respuesta sean aceptables. Cuando lo conectes, anota el resultado aquí y actualiza `ADR-066`.
