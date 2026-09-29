# Entorno local — arranque, variables de `.env` y acceso al panel del veedor

> **Para qué sirve este archivo.** Es el único lugar que hace falta leer para levantar el proyecto y entrar al panel.
> Desde `ADR-086` basta `docker compose up`: `.env` es opcional y solo sirve para cambiar un valor por defecto.
>
> **Última actualización:** 2026-09-29

---

## 1. Arrancar desde cero

```bash
docker compose up
```

Sin `.env`, sin Java y sin Node en el equipo. El compose construye las imágenes, levanta Mongo (replica set de un nodo),
Redis, MailHog y el backend, y el servicio `sembrador` (`scripts/sembrador.mjs`) deja la base lista, en este orden y solo
si hace falta:

| Paso | Qué siembra | Puerta (si ya está, no escribe) |
|---|---|---|
| 1 | 211 sectores con geometría y población (`sembrar-sectores.mjs`) | ≥ 211 sectores |
| 2 | 30 000 cuentas de demostración completas (`sembrar-usuarios-demo.mjs`, §7) | ≥ 30 000 cuentas con `datosDeDemostracion` |
| 3 | Reportes reales por `POST /api/reportes` hasta que el consenso cambie 4 barrios (`sembrar-demo.mjs`) | Hay reportes fuera del histórico |
| 4 | 120 cortes y 600 reportes de mayo a julio de 2026, sintéticos (`sembrar-historico-cortes.mjs`) | Hay cortes de mayo–julio |

Termina con `Datos listos: 211 sectores, 30001 usuarios, 120 cortes, 612 reportes.` (medido el 2026-09-29: 106 s desde
el `up` con las imágenes en caché). Repetir `up` no duplica nada; `docker compose down -v` borra los datos y el siguiente
`up` vuelve a sembrar. **Tras traer cambios del repositorio, `docker compose up --build`**: con las imágenes ya
construidas, `up` a secas no las rehace.

## 2. Entrar al panel del veedor

Con la base vacía, `SembradorAdminInicial` crea **una sola cuenta**, `admin@aguavigia.local` con rol `ADMIN` (`ADR-039`).
Sin `VEEDOR_PASSWORD_HASH`, su clave es aleatoria y el backend la escribe **una sola vez** en su log (`ADR-086`):

```bash
docker compose logs backend | findstr "ADMINISTRADOR"      # Windows
docker compose logs backend | grep ADMINISTRADOR           # Linux, macOS, Git Bash
```

El rol `ADMIN` exige segundo factor: la primera sesión (`POST /api/veedor/sesion`) solo sirve para darlo de alta. La
respuesta del alta trae el secreto en texto (RFC 6238); el código de 6 dígitos sale sin app con:

```bash
docker compose run --rm sembrador totp <SECRETO>
```

Si la clave se perdió o el ADMIN ya tiene un segundo factor que nadie conserva, `scripts/restablecer-admin.mjs` lo recupera
contra la base local, o se vacía `usuarios` y se reinicia el backend (se vuelve a sembrar un ADMIN; reportes, cortes y
sectores quedan intactos).

Las cuentas de demostración `ACTIVA` (`VEEDOR` y `OBSERVADOR`, nunca `ADMIN`) entran con la clave pública
`DemoAguaVigia-2026` (§7).

## 3. Variables opcionales de seguridad

`.env.example` las trae **comentadas**: una variable presente pero vacía tapa el valor por defecto.

| Variable | Por defecto (perfil `docker`) | Para fijarla |
|---|---|---|
| `JWT_SECRET` | Aleatoria en cada arranque (`${random.uuid}` ×2, de `SecureRandom`): las sesiones no sobreviven a un reinicio | `openssl rand -base64 32` |
| `VEEDOR_PASSWORD_HASH` | Clave aleatoria para el primer ADMIN, escrita una vez en el log | Hash BCrypt de tu clave con `GenerarHashVeedor` (abajo) |
| `ADMIN_INICIAL_CORREO` | `admin@aguavigia.local` | Cualquier correo |

```bash
# VEEDOR_PASSWORD_HASH: compila y corre GenerarHashVeedor con tu clave como argumento; no sale de tu máquina.
cd backend
./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes;target/test-classes;$(cat cp.txt)" \
  com.aguavigia.ctg.infrastructure.security.GenerarHashVeedor "tu-clave-aqui"
rm cp.txt
```

⚠️ **En `.env` cada `$` del hash va duplicado (`$$2a$$10$$…`).** Docker Compose interpola `$`: con uno solo el ADMIN se
siembra con un hash truncado, el login da `401` y, como solo se siembra con la base vacía, hay que vaciar `usuarios` para
corregirlo. Se comprueba con `docker exec aguavigia-backend printenv VEEDOR_PASSWORD_HASH`.

Estas claves son de desarrollo local: el proyecto no tiene producción (`ADR-057`, `ADR-080`). **No se escriben claves ni
hashes reales en este documento**: el valor que figuró aquí hasta el 2026-09-29 queda en el historial de git y se trata como
expuesto.

## 4. Verificar que quedó bien

```bash
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"correo":"admin@aguavigia.local","clave":"<la clave del log>"}' \
  http://localhost:8081/api/veedor/sesion
```

Debe devolver `200` con un `token` y `"alcance":"ALTA_SEGUNDO_FACTOR"`. Un `401` significa que la clave no coincide con el
hash sembrado. Conteo de la base contra los mínimos de la entrega: `docker compose run --rm sembrador verificar`.

## 5. Otras variables de `.env.example`, por si hacen falta

| Variable | Para qué | Cuándo tocarla |
|---|---|---|
| `COLLECTOR_USER_AGENT` | Identifica al colector de M9 ante Acuacar/RSS — el colector se niega a llamar si viene vacío (ética de datos) | Ya trae un valor real, no suele hacer falta cambiarlo |
| `INGESTA_INTERVALO_MS` | Cada cuánto corre el ciclo de ingesta automatizada (M9), en milisegundos | Bajarlo si necesitas ver una propuesta de ingesta sin esperar 10 minutos |
| `IOT_KEY` | Clave que deben mandar los sensores IoT (M13) en `POST /api/iot/presion` | Solo si vas a probar ese endpoint — vacía, responde 503 y el resto de la app sigue igual |
| `TELEGRAM_BOT_TOKEN` | Token del bot de Telegram (RF041), el que entrega `@BotFather` | Solo para activar las alertas por Telegram; vacía, el canal queda apagado y nada más cambia. Ver `docs/ingenieria/telegram.md` |
| `CORS_ORIGENES` | Orígenes desde los que un frontend en su propio dev server puede llamar a la API (perfil `docker`), separados por comas | Por defecto 5173 (Vite), 4173 (preview E2E), 3000 y 4200; cámbialo si tu dev server usa otro puerto |
| `MONGODB_URI`, `REDIS_HOST/PORT`, `MAIL_HOST/PORT` | Ya apuntan a los servicios de `docker-compose.yml` | No tocar salvo que cambies la topología de contenedores |
| `MONGODB_URI` para un script del **host** (no un contenedor) | Mongo local es un *replica set* de un nodo (`ADR-063`, Fase 3) — sin `?directConnection=true` el driver descubre que el nodo se anuncia como `mongo:27017` (solo resuelve dentro de Docker) e intenta reconectarse ahí | Usa `mongodb://localhost:27017/?directConnection=true`, como ya traen por defecto los `scripts/sembrar-*.mjs` |

## 5.1 Comprobar que todo el entorno funciona

`scripts/verificar-flujos.mjs` recorre con HTTP real los flujos que un frontend va a consumir: mapa y geometría,
CORS, suscripción con doble confirmación (lee el correo en MailHog), reporte, consenso, foto, aviso en tiempo real
(SSE), bitácora, estadísticas, y el panel (login del ADMIN con alta de TOTP, cortes, moderación, invitación de una
cuenta y cierre de sesión). Sin dependencias: usa el `fetch` de Node.

```bash
ADMIN_CLAVE='<la clave del ADMIN (§2)>' node scripts/verificar-flujos.mjs
# repetirlo: TOTP_SECRETO=<el secreto que imprimió la primera vez>
```

Pensado para **una corrida sobre una base recién creada**: deja datos de prueba y los límites de peticiones por IP
(10 suscripciones y pocos inicios de sesión por ventana) impiden repetirlo varias veces seguidas. Otro puerto o
host: `API_URL`, `MAILHOG_URL`. Resultado de la corrida de la Fase 5: [plan de pruebas §8](plan-de-pruebas.md#8-verificación-de-flujos-http-fase-5).

## 6. Datos de demostración: 30 000 cuentas completas para la presentación

Para mostrar una base grande y variada, `scripts/sembrar-usuarios-demo.mjs` siembra **30 000 cuentas** y lo que esas
cuentas dejarían en el sistema, en cuatro colecciones coherentes entre sí:

| Colección | Qué lleva (sembrado con la marca `datosDeDemostracion`) |
|---|---|
| `usuarios` | 30 000 cuentas con nombre completo **distinto**, correo de estilos y proveedores variados, **barrio real** (uno de los 211 sectores, repartido según su población), los seis estados (`ACTIVA`, `PENDIENTE_APROBACION`, `PENDIENTE_VERIFICACION`, `INVITADA`, `SUSPENDIDA`, `RECHAZADA`), los roles `OBSERVADOR` y `VEEDOR` (con algunos permisos sueltos), fechas de alta en los últimos 18 meses y, en alrededor del 40 % de los `VEEDOR` activos o suspendidos, el segundo factor (TOTP) ya dado de alta |
| `tokens_cuenta` | Un enlace **vigente** por cada cuenta `PENDIENTE_VERIFICACION` (48 h) e `INVITADA` (7 días); su token en claro es `demo-token-<id de la cuenta>` y se usa como cualquier otro (`POST /api/cuentas/verificacion?token=…`) |
| `auditoria_cuentas` | El rastro de cada cuenta: registro o invitación, verificación, aprobación o rechazo por el ADMIN, suspensión y alta del segundo factor (≈78 000 eventos) |
| `suscripciones` | Alertas por correo de las cuentas activas ligadas a su barrio: confirmadas, pendientes y canceladas |

`docker compose up` las siembra solo (§1, paso 2) después de los sectores y de que el backend cree al ADMIN: ese orden
importa, porque el ADMIN inicial solo se crea si **no existe ninguna cuenta** y el barrio de cada cuenta es un sector ya
sembrado. A mano, con Node en el equipo y el stack arriba:

```bash
cd scripts && npm install                    # solo la primera vez
node sembrar-usuarios-demo.mjs               # 30 000 cuentas en ~3 s; --cantidad, --semilla y --minimo opcionales
```

- **Comprobación final:** imprime los conteos por colección, estado, rol y barrio, y **sale con error** si `usuarios` queda por
  debajo de `--minimo` (30 000 por defecto), si alguna cuenta sembrada no tiene barrio o si su barrio no existe en `sectores`.
  Con `--cantidad` menor hay que bajar también `--minimo`.
- **Idempotente y seguro:** antes de insertar borra solo lo que él mismo sembró (marca `datosDeDemostracion`, en las cuatro
  colecciones); no toca al ADMIN ni a cuentas reales. Se niega a correr contra una base que no sea local. Si la aplicación
  ya modificó una fila sembrada (usó un token, suspendió una cuenta), esa fila pierde la marca: el script la respeta, la
  omite al resembrar y lo dice al final.
- **Determinista:** la misma semilla da las mismas cuentas, tokens, eventos y suscripciones (las fechas cuelgan de la hora
  en que se corre).
- **Entrar como una cuenta sembrada:** las `ACTIVA` (VEEDOR y OBSERVADOR, nunca ADMIN) usan la clave `DemoAguaVigia-2026`.
  Las que tienen segundo factor guardan su secreto en `secretoTotp` y piden el código
  (`docker compose run --rm sembrador totp <secreto>`); las pruebas de carga usan las que no lo tienen.
- **Cómo verlas:** como ADMIN, `GET /api/veedor/usuarios?pagina=0&tamano=200` devuelve `X-Total-Count` con las 30 000 más el
  ADMIN y las cuentas reales, y se puede filtrar con `?estado=ACTIVA` y `?barrioId=el-pozon`.
- **Comprobado el 2026-09-29** contra el backend real: 30 000 cuentas en 211 barrios (0 sin barrio, 0 con barrio inexistente),
  inicio de sesión de un `VEEDOR` sin segundo factor y de otro con él (`401` sin código, `200` con él), una verificación de correo
  con un token sembrado (`204` y la cuenta pasa a `PENDIENTE_APROBACION`) y el listado por barrio (`el-pozon`: 1 839 cuentas) en
  51 ms.

## 7. Agregar usuarios en vivo (faker)

Para mostrar dónde se guarda un usuario y cómo crece la base, `scripts/agregar-usuarios.mjs` agrega cuentas **nuevas y
distintas en cada ejecución** (faker, `ADR-087`), con las mismas reglas que las 30 000 iniciales (barrio real, estado, rol,
auditoría, tokens y suscripciones). Cada ejecución es un **lote** con nombre que se puede contar y borrar.

```bash
docker compose run --rm sembrador agregar-usuarios --cantidad 1000                    # modo directo: miles por segundo
docker compose run --rm sembrador agregar-usuarios --cantidad 200 --modo api          # registro real por la API
docker compose run --rm sembrador agregar-usuarios --borrar-lote lote-20260929-1715   # quitar un lote
```

| Opción | Por defecto | Qué hace |
|---|---|---|
| `--cantidad` | 1000 | Cuántos usuarios |
| `--modo` | `directo` | `directo` inserta en Mongo cuentas completas con la marca `lote`; `api` llama a `POST /api/cuentas/registro` por cada una (el backend cifra la clave, audita y manda el correo de verificación a MailHog; quedan en `PENDIENTE_VERIFICACION`) |
| `--lote` | `lote-<fecha-hora>` | Nombre del lote |
| `--concurrencia` | 20 | Peticiones a la vez en modo `api` |
| `--semilla` | ninguna | Reproduce la misma serie (para pruebas) |
| `--borrar-lote` | — | Borra las cuentas del lote y lo que dejaron (tokens, auditoría, suscripciones) |

Al terminar imprime el conteo de cada colección **antes → después**, tres documentos del lote tal como quedaron en
`aguavigia.usuarios` y la consulta para verlos en Mongo (`db.usuarios.find({"lote": "…"})`).

- **Límite del modo `api`:** `/api/cuentas/**` admite 10 peticiones cada 10 min por IP; el resto responde `429` y el script lo
  explica. Para registrar miles, se levanta el backend con el perfil `carga`, que quita ese límite (`ADR-083`).
- **Sin correos repetidos:** carga antes todos los correos existentes y, si otro proceso inserta el mismo entre medias, el
  índice único lo rechaza y el script genera otro.
- Con Node en el equipo también sirve `cd scripts && node agregar-usuarios.mjs …` contra `localhost:27017`.
- Pruebas del generador: `cd scripts && npm test`.

---

Documentos relacionados: [`../api/README.md`](../api/README.md) (la guía de la API) · [`credenciales-y-accesos.md`](credenciales-y-accesos.md)
(qué cuentas existen y con qué clave).
