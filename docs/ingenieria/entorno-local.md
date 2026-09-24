# Entorno local — variables de `.env` y cómo probar el panel del veedor

> **Para qué sirve este archivo.** `.env` nunca se versiona (`.gitignore`), así que cada
> persona que clona el repo empieza con dos variables vacías —`JWT_SECRET` y
> `VEEDOR_PASSWORD_HASH`— y el panel del veedor responde 503 hasta configurarlas. Esto quedó
> sin resolver durante varias sesiones seguidas de integración, siempre
> pospuesto por ser "solo config, no código". Esta nota es el único lugar que hace falta leer
> para dejarlo funcionando, con una clave de desarrollo lista para copiar y pegar.
>
> **Última actualización:** 2026-08-12

---

## 1. Arrancar desde cero

```bash
cp .env.example .env
docker compose up -d --wait
```

Antes de ver nada en el mapa hay que sembrar los sectores (una vez, con Mongo ya arriba):

```bash
cd scripts && npm install && node sembrar-sectores.mjs   # 211 sectores; idempotente
```

El endpoint `GET /api/sectores` cachea su respuesta unos segundos: si alguien lo pidió *antes* de sembrar,
sigue devolviendo la lista vacía hasta que caduque la caché.

Con esto el mapa, los reportes, las suscripciones, la bitácora, las estadísticas y el
índice de cumplimiento ya funcionan — son públicos, sin token. **El panel del veedor
(`/veedor`) no**: necesita las dos variables de la sección 2.

## 2. Las dos variables que `.env.example` deja vacías, y por qué

| Variable | Para qué sirve | Si está vacía |
|---|---|---|
| `JWT_SECRET` | Firma el token de sesión del veedor (RNF011, HS256, mínimo 32 bytes) | `POST /api/veedor/sesion` responde `503` — *"El servidor no tiene configurado JWT_SECRET"* |
| `VEEDOR_PASSWORD_HASH` | Hash BCrypt de la clave del **primer administrador** — **nunca la clave en texto plano**. Desde `ADR-039` ya no es una credencial compartida: solo siembra esa primera cuenta y deja de usarse en cuanto existe alguna | Sin ella no se siembra ningún administrador y el panel queda sin acceso |
| `ADMIN_INICIAL_CORREO` | Correo con el que se crea ese primer administrador. En local, `veedor@aguavigia.local` | Sin él tampoco se siembra: el arranque lo dice en el log y sigue |
| `APP_URL_PUBLICA` | Base desde la que se arman los enlaces que salen por correo. Sin frontend (`ADR-048`) apunta a la propia API: en local, `http://localhost:8081`. En producción es **obligatoria** y no puede ser `localhost` | Los enlaces de los correos salen rotos |

Ambas se leen en `VeedorAuthController.java` (`backend/src/main/java/.../api/VeedorAuthController.java`).
Son credenciales de **desarrollo local**, no de producción: el perfil `prod` exige las suyas
propias y aborta el arranque si faltan (`ValidacionDeSecretosProd`).

## 3. La vía rápida — copiar la clave de desarrollo

Para desarrollo local, se puede usar esta misma clave. Pega esto en tu `.env`:

```bash
JWT_SECRET=jHZczrMtY+dNWbYoCFZe3ZOvDUl8j7rWqVDeEeLMfIQ=
VEEDOR_PASSWORD_HASH=$$2a$$10$$IUf9Q.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu
ADMIN_INICIAL_CORREO=veedor@aguavigia.local
APP_URL_PUBLICA=http://localhost:8081
```

Clave del veedor para entrar al panel (`/veedor`): **`AguaVigia-Dev-2026`**

⚠️ **La clave sola ya no basta.** Desde `ADR-039` la cuenta sembrada es `ADMIN`, y el rol `ADMIN`
exige segundo factor: la primera sesión solo sirve para activarlo. Sigue el §3.1.

Después de pegarlo:

```bash
docker compose up -d backend
```

⚠️ **Los `$` van escapados como `$$`, literal, tal como está arriba.** No es un error de
copiado: `docker compose` interpola `.env` antes de pasarlo al contenedor, y un `$` suelto
arranca una sustitución de variable. La primera vez que se generó este hash, `$2a$10$IUf9Q...`
llegó al backend como `$2a$10.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu` —le faltaba
el pedazo `$IUf9Q`, sustituido en silencio por una variable `IUf9Q` que no existe— y el login
fallaba con 401 en vez de 503, mucho más confuso de diagnosticar porque *parecía* que el
servidor sí tenía la variable configurada. Verificar que llegó bien:

```bash
docker exec aguavigia-backend printenv VEEDOR_PASSWORD_HASH
# debe imprimir exactamente: $2a$10$IUf9Q.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu
```

### 3.1 El segundo factor, sin app de autenticación

La pantalla de alta muestra un QR **y el secreto en texto** debajo («si la cámara no coopera,
escribe este código a mano»). Ese secreto es todo lo que hace falta: el TOTP es el estándar de
siempre (RFC 6238, HMAC-SHA1, 6 dígitos, franjas de 30 s), así que sirve cualquier generador —una
app de teléfono, un gestor de contraseñas de escritorio, o el script del repositorio.

```bash
node scripts/codigo-totp.mjs <EL_SECRETO_QUE_MUESTRA_LA_PANTALLA>
```

No rodea el segundo factor: calcula lo mismo que la app, sobre un secreto que la propia pantalla te
acaba de dar. Que sea el mismo código que espera el backend está anclado por los dos lados a los
vectores del apéndice B del RFC — `TotpAdapterTest` en el backend y `--autoprueba` en el script:

```bash
node scripts/codigo-totp.mjs --autoprueba
```

**Solo para cuentas de desarrollo.** Un secreto de producción tecleado en la terminal queda en el
historial del shell; para esas cuentas, una app o un gestor de contraseñas.

**Si heredaste una base donde el admin ya tiene el TOTP activado** —lo activó otra persona u otra
sesión, y nadie tiene ya ese secreto— la cuenta no se recupera: se vuelve a sembrar. El sembrador
solo actúa cuando **no queda ninguna cuenta** (`SembradorAdminInicial.sembrarSiNoHayNadie`), así que
hay que vaciar la colección entera, no solo el admin:

```bash
docker exec aguavigia-mongo mongosh aguavigia --quiet --eval "db.usuarios.deleteMany({})"
docker restart aguavigia-backend
```

Borra únicamente las cuentas del panel: reportes, boletines y cortes quedan intactos.

## 4. La vía propia — generar tu propia clave

Si prefieres generar una propia:

```bash
# JWT_SECRET — 32 bytes al azar
openssl rand -base64 32
```

```bash
# VEEDOR_PASSWORD_HASH — compila y corre GenerarHashVeedor con tu clave como argumento.
# Corre 100% local, contra las mismas clases de Spring Security del backend: nada sale de tu máquina.
cd backend
./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes;target/test-classes;$(cat cp.txt)" \
  com.aguavigia.ctg.infrastructure.security.GenerarHashVeedor "tu-clave-aqui"
rm cp.txt
```

Pega el resultado en `.env` — **recuerda escapar cada `$` del hash como `$$`** (sección 3).

## 5. Verificar que quedó bien

```bash
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"correo":"veedor@aguavigia.local","clave":"AguaVigia-Dev-2026"}' \
  http://localhost:8081/api/veedor/sesion
```

Debe devolver `{"token":"eyJ..."}`. Un `503` significa que alguna de las dos variables sigue
vacía o no llegó bien al contenedor (`docker exec aguavigia-backend printenv JWT_SECRET
VEEDOR_PASSWORD_HASH`); un `401` significa que la clave no coincide con el hash configurado.

## 6. Otras variables de `.env.example`, por si hacen falta

| Variable | Para qué | Cuándo tocarla |
|---|---|---|
| `COLLECTOR_USER_AGENT` | Identifica al colector de M9 ante Acuacar/RSS — el colector se niega a llamar si viene vacío (ética de datos) | Ya trae un valor real, no suele hacer falta cambiarlo |
| `INGESTA_INTERVALO_MS` | Cada cuánto corre el ciclo de ingesta automatizada (M9), en milisegundos | Bajarlo si necesitas ver una propuesta de ingesta sin esperar 10 minutos |
| `IOT_KEY` | Clave que deben mandar los sensores IoT (M13) en `POST /api/iot/presion` | Solo si vas a probar ese endpoint — vacía, responde 503 y el resto de la app sigue igual |
| `TELEGRAM_BOT_TOKEN` | Token del bot de Telegram (RF041), el que entrega `@BotFather` | Solo para activar las alertas por Telegram; vacía, el canal queda apagado y nada más cambia. Ver `docs/ingenieria/telegram.md` |
| `CORS_ORIGENES` | Orígenes desde los que un frontend en su propio dev server puede llamar a la API (perfil `docker`), separados por comas | Por defecto 5173 (Vite), 3000 y 4200; cámbialo si tu dev server usa otro puerto |
| `MONGODB_URI`, `REDIS_HOST/PORT`, `MAIL_HOST/PORT` | Ya apuntan a los servicios de `docker-compose.yml` | No tocar salvo que cambies la topología de contenedores |
| `MONGODB_URI` para un script del **host** (no un contenedor) | Mongo local es un *replica set* de un nodo (`ADR-063`, Fase 3) — sin `?directConnection=true` el driver descubre que el nodo se anuncia como `mongo:27017` (solo resuelve dentro de Docker) e intenta reconectarse ahí | Usa `mongodb://localhost:27017/?directConnection=true`, como ya traen por defecto los `scripts/sembrar-*.mjs` |

## 6.1 Comprobar que todo el entorno funciona

`scripts/verificar-flujos.mjs` recorre con HTTP real los flujos que un frontend va a consumir: mapa y geometría,
CORS, suscripción con doble confirmación (lee el correo en MailHog), reporte, consenso, foto, aviso en tiempo real
(SSE), bitácora, estadísticas, y el panel (login del ADMIN con alta de TOTP, cortes, moderación, invitación de una
cuenta y cierre de sesión). Sin dependencias: usa el `fetch` de Node.

```bash
ADMIN_CLAVE='<la clave cuyo hash pusiste en VEEDOR_PASSWORD_HASH>' node scripts/verificar-flujos.mjs
# repetirlo: TOTP_SECRETO=<el secreto que imprimió la primera vez>
```

Pensado para **una corrida sobre una base recién creada**: deja datos de prueba y los límites de peticiones por IP
(10 suscripciones y pocos inicios de sesión por ventana) impiden repetirlo varias veces seguidas. Otro puerto o
host: `API_URL`, `MAILHOG_URL`. Resultado de la corrida de la Fase 5: [plan de pruebas §8](plan-de-pruebas.md#8-verificación-de-flujos-http-fase-5).

**Trampa conocida:** en el `.env` el hash BCrypt lleva cada `# Entorno local — variables de `.env` y cómo probar el panel del veedor

> **Para qué sirve este archivo.** `.env` nunca se versiona (`.gitignore`), así que cada
> persona que clona el repo empieza con dos variables vacías —`JWT_SECRET` y
> `VEEDOR_PASSWORD_HASH`— y el panel del veedor responde 503 hasta configurarlas. Esto quedó
> sin resolver durante varias sesiones seguidas de integración, siempre
> pospuesto por ser "solo config, no código". Esta nota es el único lugar que hace falta leer
> para dejarlo funcionando, con una clave de desarrollo lista para copiar y pegar.
>
> **Última actualización:** 2026-08-12

---

## 1. Arrancar desde cero

```bash
cp .env.example .env
docker compose up -d --wait
```

Antes de ver nada en el mapa hay que sembrar los sectores (una vez, con Mongo ya arriba):

```bash
cd scripts && npm install && node sembrar-sectores.mjs   # 211 sectores; idempotente
```

El endpoint `GET /api/sectores` cachea su respuesta unos segundos: si alguien lo pidió *antes* de sembrar,
sigue devolviendo la lista vacía hasta que caduque la caché.

Con esto el mapa, los reportes, las suscripciones, la bitácora, las estadísticas y el
índice de cumplimiento ya funcionan — son públicos, sin token. **El panel del veedor
(`/veedor`) no**: necesita las dos variables de la sección 2.

## 2. Las dos variables que `.env.example` deja vacías, y por qué

| Variable | Para qué sirve | Si está vacía |
|---|---|---|
| `JWT_SECRET` | Firma el token de sesión del veedor (RNF011, HS256, mínimo 32 bytes) | `POST /api/veedor/sesion` responde `503` — *"El servidor no tiene configurado JWT_SECRET"* |
| `VEEDOR_PASSWORD_HASH` | Hash BCrypt de la clave del **primer administrador** — **nunca la clave en texto plano**. Desde `ADR-039` ya no es una credencial compartida: solo siembra esa primera cuenta y deja de usarse en cuanto existe alguna | Sin ella no se siembra ningún administrador y el panel queda sin acceso |
| `ADMIN_INICIAL_CORREO` | Correo con el que se crea ese primer administrador. En local, `veedor@aguavigia.local` | Sin él tampoco se siembra: el arranque lo dice en el log y sigue |
| `APP_URL_PUBLICA` | Base desde la que se arman los enlaces que salen por correo. Sin frontend (`ADR-048`) apunta a la propia API: en local, `http://localhost:8081`. En producción es **obligatoria** y no puede ser `localhost` | Los enlaces de los correos salen rotos |

Ambas se leen en `VeedorAuthController.java` (`backend/src/main/java/.../api/VeedorAuthController.java`).
Son credenciales de **desarrollo local**, no de producción: el perfil `prod` exige las suyas
propias y aborta el arranque si faltan (`ValidacionDeSecretosProd`).

## 3. La vía rápida — copiar la clave de desarrollo

Para desarrollo local, se puede usar esta misma clave. Pega esto en tu `.env`:

```bash
JWT_SECRET=jHZczrMtY+dNWbYoCFZe3ZOvDUl8j7rWqVDeEeLMfIQ=
VEEDOR_PASSWORD_HASH=$$2a$$10$$IUf9Q.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu
ADMIN_INICIAL_CORREO=veedor@aguavigia.local
APP_URL_PUBLICA=http://localhost:8081
```

Clave del veedor para entrar al panel (`/veedor`): **`AguaVigia-Dev-2026`**

⚠️ **La clave sola ya no basta.** Desde `ADR-039` la cuenta sembrada es `ADMIN`, y el rol `ADMIN`
exige segundo factor: la primera sesión solo sirve para activarlo. Sigue el §3.1.

Después de pegarlo:

```bash
docker compose up -d backend
```

⚠️ **Los `$` van escapados como `$$`, literal, tal como está arriba.** No es un error de
copiado: `docker compose` interpola `.env` antes de pasarlo al contenedor, y un `$` suelto
arranca una sustitución de variable. La primera vez que se generó este hash, `$2a$10$IUf9Q...`
llegó al backend como `$2a$10.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu` —le faltaba
el pedazo `$IUf9Q`, sustituido en silencio por una variable `IUf9Q` que no existe— y el login
fallaba con 401 en vez de 503, mucho más confuso de diagnosticar porque *parecía* que el
servidor sí tenía la variable configurada. Verificar que llegó bien:

```bash
docker exec aguavigia-backend printenv VEEDOR_PASSWORD_HASH
# debe imprimir exactamente: $2a$10$IUf9Q.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu
```

### 3.1 El segundo factor, sin app de autenticación

La pantalla de alta muestra un QR **y el secreto en texto** debajo («si la cámara no coopera,
escribe este código a mano»). Ese secreto es todo lo que hace falta: el TOTP es el estándar de
siempre (RFC 6238, HMAC-SHA1, 6 dígitos, franjas de 30 s), así que sirve cualquier generador —una
app de teléfono, un gestor de contraseñas de escritorio, o el script del repositorio.

```bash
node scripts/codigo-totp.mjs <EL_SECRETO_QUE_MUESTRA_LA_PANTALLA>
```

No rodea el segundo factor: calcula lo mismo que la app, sobre un secreto que la propia pantalla te
acaba de dar. Que sea el mismo código que espera el backend está anclado por los dos lados a los
vectores del apéndice B del RFC — `TotpAdapterTest` en el backend y `--autoprueba` en el script:

```bash
node scripts/codigo-totp.mjs --autoprueba
```

**Solo para cuentas de desarrollo.** Un secreto de producción tecleado en la terminal queda en el
historial del shell; para esas cuentas, una app o un gestor de contraseñas.

**Si heredaste una base donde el admin ya tiene el TOTP activado** —lo activó otra persona u otra
sesión, y nadie tiene ya ese secreto— la cuenta no se recupera: se vuelve a sembrar. El sembrador
solo actúa cuando **no queda ninguna cuenta** (`SembradorAdminInicial.sembrarSiNoHayNadie`), así que
hay que vaciar la colección entera, no solo el admin:

```bash
docker exec aguavigia-mongo mongosh aguavigia --quiet --eval "db.usuarios.deleteMany({})"
docker restart aguavigia-backend
```

Borra únicamente las cuentas del panel: reportes, boletines y cortes quedan intactos.

## 4. La vía propia — generar tu propia clave

Si prefieres generar una propia:

```bash
# JWT_SECRET — 32 bytes al azar
openssl rand -base64 32
```

```bash
# VEEDOR_PASSWORD_HASH — compila y corre GenerarHashVeedor con tu clave como argumento.
# Corre 100% local, contra las mismas clases de Spring Security del backend: nada sale de tu máquina.
cd backend
./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes;target/test-classes;$(cat cp.txt)" \
  com.aguavigia.ctg.infrastructure.security.GenerarHashVeedor "tu-clave-aqui"
rm cp.txt
```

Pega el resultado en `.env` — **recuerda escapar cada `$` del hash como `$$`** (sección 3).

## 5. Verificar que quedó bien

```bash
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"correo":"veedor@aguavigia.local","clave":"AguaVigia-Dev-2026"}' \
  http://localhost:8081/api/veedor/sesion
```

Debe devolver `{"token":"eyJ..."}`. Un `503` significa que alguna de las dos variables sigue
vacía o no llegó bien al contenedor (`docker exec aguavigia-backend printenv JWT_SECRET
VEEDOR_PASSWORD_HASH`); un `401` significa que la clave no coincide con el hash configurado.

## 6. Otras variables de `.env.example`, por si hacen falta

| Variable | Para qué | Cuándo tocarla |
|---|---|---|
| `COLLECTOR_USER_AGENT` | Identifica al colector de M9 ante Acuacar/RSS — el colector se niega a llamar si viene vacío (ética de datos) | Ya trae un valor real, no suele hacer falta cambiarlo |
| `INGESTA_INTERVALO_MS` | Cada cuánto corre el ciclo de ingesta automatizada (M9), en milisegundos | Bajarlo si necesitas ver una propuesta de ingesta sin esperar 10 minutos |
| `IOT_KEY` | Clave que deben mandar los sensores IoT (M13) en `POST /api/iot/presion` | Solo si vas a probar ese endpoint — vacía, responde 503 y el resto de la app sigue igual |
| `CORS_ORIGENES` | Orígenes desde los que un frontend en su propio dev server puede llamar a la API (perfil `docker`), separados por comas | Por defecto 5173 (Vite), 3000 y 4200; cámbialo si tu dev server usa otro puerto |
| `MONGODB_URI`, `REDIS_HOST/PORT`, `MAIL_HOST/PORT` | Ya apuntan a los servicios de `docker-compose.yml` | No tocar salvo que cambies la topología de contenedores |
| `MONGODB_URI` para un script del **host** (no un contenedor) | Mongo local es un *replica set* de un nodo (`ADR-063`, Fase 3) — sin `?directConnection=true` el driver descubre que el nodo se anuncia como `mongo:27017` (solo resuelve dentro de Docker) e intenta reconectarse ahí | Usa `mongodb://localhost:27017/?directConnection=true`, como ya traen por defecto los `scripts/sembrar-*.mjs` |

 duplicado (`$2a$10$…`), porque Docker Compose
interpola `# Entorno local — variables de `.env` y cómo probar el panel del veedor

> **Para qué sirve este archivo.** `.env` nunca se versiona (`.gitignore`), así que cada
> persona que clona el repo empieza con dos variables vacías —`JWT_SECRET` y
> `VEEDOR_PASSWORD_HASH`— y el panel del veedor responde 503 hasta configurarlas. Esto quedó
> sin resolver durante varias sesiones seguidas de integración, siempre
> pospuesto por ser "solo config, no código". Esta nota es el único lugar que hace falta leer
> para dejarlo funcionando, con una clave de desarrollo lista para copiar y pegar.
>
> **Última actualización:** 2026-08-12

---

## 1. Arrancar desde cero

```bash
cp .env.example .env
docker compose up -d --wait
```

Antes de ver nada en el mapa hay que sembrar los sectores (una vez, con Mongo ya arriba):

```bash
cd scripts && npm install && node sembrar-sectores.mjs   # 211 sectores; idempotente
```

El endpoint `GET /api/sectores` cachea su respuesta unos segundos: si alguien lo pidió *antes* de sembrar,
sigue devolviendo la lista vacía hasta que caduque la caché.

Con esto el mapa, los reportes, las suscripciones, la bitácora, las estadísticas y el
índice de cumplimiento ya funcionan — son públicos, sin token. **El panel del veedor
(`/veedor`) no**: necesita las dos variables de la sección 2.

## 2. Las dos variables que `.env.example` deja vacías, y por qué

| Variable | Para qué sirve | Si está vacía |
|---|---|---|
| `JWT_SECRET` | Firma el token de sesión del veedor (RNF011, HS256, mínimo 32 bytes) | `POST /api/veedor/sesion` responde `503` — *"El servidor no tiene configurado JWT_SECRET"* |
| `VEEDOR_PASSWORD_HASH` | Hash BCrypt de la clave del **primer administrador** — **nunca la clave en texto plano**. Desde `ADR-039` ya no es una credencial compartida: solo siembra esa primera cuenta y deja de usarse en cuanto existe alguna | Sin ella no se siembra ningún administrador y el panel queda sin acceso |
| `ADMIN_INICIAL_CORREO` | Correo con el que se crea ese primer administrador. En local, `veedor@aguavigia.local` | Sin él tampoco se siembra: el arranque lo dice en el log y sigue |
| `APP_URL_PUBLICA` | Base desde la que se arman los enlaces que salen por correo. Sin frontend (`ADR-048`) apunta a la propia API: en local, `http://localhost:8081`. En producción es **obligatoria** y no puede ser `localhost` | Los enlaces de los correos salen rotos |

Ambas se leen en `VeedorAuthController.java` (`backend/src/main/java/.../api/VeedorAuthController.java`).
Son credenciales de **desarrollo local**, no de producción: el perfil `prod` exige las suyas
propias y aborta el arranque si faltan (`ValidacionDeSecretosProd`).

## 3. La vía rápida — copiar la clave de desarrollo

Para desarrollo local, se puede usar esta misma clave. Pega esto en tu `.env`:

```bash
JWT_SECRET=jHZczrMtY+dNWbYoCFZe3ZOvDUl8j7rWqVDeEeLMfIQ=
VEEDOR_PASSWORD_HASH=$$2a$$10$$IUf9Q.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu
ADMIN_INICIAL_CORREO=veedor@aguavigia.local
APP_URL_PUBLICA=http://localhost:8081
```

Clave del veedor para entrar al panel (`/veedor`): **`AguaVigia-Dev-2026`**

⚠️ **La clave sola ya no basta.** Desde `ADR-039` la cuenta sembrada es `ADMIN`, y el rol `ADMIN`
exige segundo factor: la primera sesión solo sirve para activarlo. Sigue el §3.1.

Después de pegarlo:

```bash
docker compose up -d backend
```

⚠️ **Los `$` van escapados como `$$`, literal, tal como está arriba.** No es un error de
copiado: `docker compose` interpola `.env` antes de pasarlo al contenedor, y un `$` suelto
arranca una sustitución de variable. La primera vez que se generó este hash, `$2a$10$IUf9Q...`
llegó al backend como `$2a$10.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu` —le faltaba
el pedazo `$IUf9Q`, sustituido en silencio por una variable `IUf9Q` que no existe— y el login
fallaba con 401 en vez de 503, mucho más confuso de diagnosticar porque *parecía* que el
servidor sí tenía la variable configurada. Verificar que llegó bien:

```bash
docker exec aguavigia-backend printenv VEEDOR_PASSWORD_HASH
# debe imprimir exactamente: $2a$10$IUf9Q.qBPoWuaiCNq9PEVusG7eHYzMP4IAnUjNcl7RiMSwp46MKPu
```

### 3.1 El segundo factor, sin app de autenticación

La pantalla de alta muestra un QR **y el secreto en texto** debajo («si la cámara no coopera,
escribe este código a mano»). Ese secreto es todo lo que hace falta: el TOTP es el estándar de
siempre (RFC 6238, HMAC-SHA1, 6 dígitos, franjas de 30 s), así que sirve cualquier generador —una
app de teléfono, un gestor de contraseñas de escritorio, o el script del repositorio.

```bash
node scripts/codigo-totp.mjs <EL_SECRETO_QUE_MUESTRA_LA_PANTALLA>
```

No rodea el segundo factor: calcula lo mismo que la app, sobre un secreto que la propia pantalla te
acaba de dar. Que sea el mismo código que espera el backend está anclado por los dos lados a los
vectores del apéndice B del RFC — `TotpAdapterTest` en el backend y `--autoprueba` en el script:

```bash
node scripts/codigo-totp.mjs --autoprueba
```

**Solo para cuentas de desarrollo.** Un secreto de producción tecleado en la terminal queda en el
historial del shell; para esas cuentas, una app o un gestor de contraseñas.

**Si heredaste una base donde el admin ya tiene el TOTP activado** —lo activó otra persona u otra
sesión, y nadie tiene ya ese secreto— la cuenta no se recupera: se vuelve a sembrar. El sembrador
solo actúa cuando **no queda ninguna cuenta** (`SembradorAdminInicial.sembrarSiNoHayNadie`), así que
hay que vaciar la colección entera, no solo el admin:

```bash
docker exec aguavigia-mongo mongosh aguavigia --quiet --eval "db.usuarios.deleteMany({})"
docker restart aguavigia-backend
```

Borra únicamente las cuentas del panel: reportes, boletines y cortes quedan intactos.

## 4. La vía propia — generar tu propia clave

Si prefieres generar una propia:

```bash
# JWT_SECRET — 32 bytes al azar
openssl rand -base64 32
```

```bash
# VEEDOR_PASSWORD_HASH — compila y corre GenerarHashVeedor con tu clave como argumento.
# Corre 100% local, contra las mismas clases de Spring Security del backend: nada sale de tu máquina.
cd backend
./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes;target/test-classes;$(cat cp.txt)" \
  com.aguavigia.ctg.infrastructure.security.GenerarHashVeedor "tu-clave-aqui"
rm cp.txt
```

Pega el resultado en `.env` — **recuerda escapar cada `$` del hash como `$$`** (sección 3).

## 5. Verificar que quedó bien

```bash
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"correo":"veedor@aguavigia.local","clave":"AguaVigia-Dev-2026"}' \
  http://localhost:8081/api/veedor/sesion
```

Debe devolver `{"token":"eyJ..."}`. Un `503` significa que alguna de las dos variables sigue
vacía o no llegó bien al contenedor (`docker exec aguavigia-backend printenv JWT_SECRET
VEEDOR_PASSWORD_HASH`); un `401` significa que la clave no coincide con el hash configurado.

## 6. Otras variables de `.env.example`, por si hacen falta

| Variable | Para qué | Cuándo tocarla |
|---|---|---|
| `COLLECTOR_USER_AGENT` | Identifica al colector de M9 ante Acuacar/RSS — el colector se niega a llamar si viene vacío (ética de datos) | Ya trae un valor real, no suele hacer falta cambiarlo |
| `INGESTA_INTERVALO_MS` | Cada cuánto corre el ciclo de ingesta automatizada (M9), en milisegundos | Bajarlo si necesitas ver una propuesta de ingesta sin esperar 10 minutos |
| `IOT_KEY` | Clave que deben mandar los sensores IoT (M13) en `POST /api/iot/presion` | Solo si vas a probar ese endpoint — vacía, responde 503 y el resto de la app sigue igual |
| `CORS_ORIGENES` | Orígenes desde los que un frontend en su propio dev server puede llamar a la API (perfil `docker`), separados por comas | Por defecto 5173 (Vite), 3000 y 4200; cámbialo si tu dev server usa otro puerto |
| `MONGODB_URI`, `REDIS_HOST/PORT`, `MAIL_HOST/PORT` | Ya apuntan a los servicios de `docker-compose.yml` | No tocar salvo que cambies la topología de contenedores |
| `MONGODB_URI` para un script del **host** (no un contenedor) | Mongo local es un *replica set* de un nodo (`ADR-063`, Fase 3) — sin `?directConnection=true` el driver descubre que el nodo se anuncia como `mongo:27017` (solo resuelve dentro de Docker) e intenta reconectarse ahí | Usa `mongodb://localhost:27017/?directConnection=true`, como ya traen por defecto los `scripts/sembrar-*.mjs` |

; sin duplicarlos el ADMIN se siembra con un hash truncado y no puede entrar (y, como solo se siembra
con la base vacía, hay que borrar la colección de cuentas para corregirlo).

## 7. Datos de demostración: 20 000 cuentas para la presentación

Para mostrar una base grande y variada, `scripts/sembrar-usuarios-demo.mjs` siembra **20 000 cuentas** en la colección
`usuarios`: nombres completos **todos distintos**, correos con estilos y proveedores variados, los seis estados de cuenta
(`ACTIVA`, `PENDIENTE_APROBACION`, `PENDIENTE_VERIFICACION`, `INVITADA`, `SUSPENDIDA`, `RECHAZADA`), los roles `OBSERVADOR` y `VEEDOR`
(con algunos permisos sueltos) y fechas de alta repartidas en los últimos 18 meses.

**El orden importa**: el ADMIN inicial solo se crea si **no existe ninguna cuenta**. Primero arranca el backend con
`ADMIN_INICIAL_CORREO` y `VEEDOR_PASSWORD_HASH` (sección 2) y **después** siembra:

```bash
docker compose up -d mongo redis mailhog     # y arranca el backend con las dos variables del ADMIN
cd scripts && npm install                    # solo la primera vez
node sembrar-usuarios-demo.mjs               # 20 000 cuentas en ~3 s; --cantidad y --semilla opcionales
```

- **Idempotente y seguro:** antes de insertar borra solo lo que él mismo sembró (marca `datosDeDemostracion`); no toca al ADMIN
  ni a cuentas reales. Se niega a correr contra una base que no sea local.
- **Determinista:** la misma semilla da las mismas cuentas.
- **Entrar como una cuenta sembrada:** las `ACTIVA` (VEEDOR y OBSERVADOR, nunca ADMIN) usan la clave `DemoAguaVigia-2026`.
- **Cómo verlas:** como ADMIN, `GET /api/veedor/usuarios?pagina=0&tamano=200` devuelve `X-Total-Count: 20001` (las 20 000 más el
  ADMIN) y 101 páginas; se puede filtrar con `?estado=ACTIVA`.
- **Comprobado el 2026-09-21** contra el backend real: 20 001 cuentas, páginas y filtros en 17–58 ms, inicio de sesión de un
  VEEDOR y un OBSERVADOR sembrados, y una cuenta suspendida rechazada con 403.

---

Documentos relacionados: [`../api/README.md`](../api/README.md)
(la guía de la API para el frontend) · [`estado-del-backend.md`](estado-del-backend.md) §6.2
(por qué esto quedó pendiente tanto tiempo).
