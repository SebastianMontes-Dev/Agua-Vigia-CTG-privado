# R0 · Red de seguridad

**Objetivo:** antes de mover una sola clase, dejar herramientas que digan con certeza si algo cambió por fuera. Esta fase
**no toca código de producción** (`backend/src/main`).

Riesgo: bajo. Esfuerzo: 3–4 sesiones (con los requisitos del 2026-10-07 R0 también mide la capacidad, compara las bases y completa la matriz de escenarios; ver §6, §7 y §8).

## Qué se construye

### 1. `scripts/reduccion/medir.sh`

Imprime una fila para la tabla [Avance](README.md#avance) con estas cifras:
- archivos `.java` y líneas de `backend/src/main/java`
- archivos `.java` y líneas de `backend/src/test/java`
- interfaces que quedan en `domain/port`
- mappers MapStruct

Solo usa `find` y `wc`; tiene que funcionar en macOS y en Git Bash de Windows.

### 2. `scripts/reduccion/comparar-contrato.mjs`

Uso: `guardar | comparar`. Lee `${API_URL:-http://localhost:8081}/v3/api-docs` (JSON de springdoc, público en el perfil `docker`).

1. **Normaliza** el documento:
   - ordena las claves
   - quita `servers`, `description`, `summary` y `example`, porque la redacción no es contrato
   - **conserva** rutas, métodos, `operationId`, `tags`, parámetros (nombre, `in`, `required`, esquema), `requestBody`, respuestas por código, `components.schemas` (nombres, propiedades, tipos, `required`, `enum`, `nullable`, `format`, límites) y `security`
2. Según el modo:
   - `guardar` escribe `scripts/reduccion/linea-base/api-docs.json`
   - `comparar` imprime cada diferencia como `ruta-json: antes → después` y sale con código 1 si hay alguna

Además compara las rutas con `backend/openapi.yaml`, igual que `ContratoOpenApiTest`, para avisar si el YAML versionado quedó atrás.

### 3. `scripts/reduccion/instantanea.mjs`

Uso: `guardar | comparar`. Reemplaza los valores por su **forma**, para que el resultado no dependa de ids ni de horas. Así
detecta lo que el contrato no ve, por ejemplo un campo que antes venía `null` y ahora no viene:
- `"abc"` → `"string"`
- `3` → `"number"`
- `null` → `"null"`
- un arreglo → `[forma del primer elemento]` más `vacio: true|false`

Qué recorre:
- **Todos los GET públicos**, sobre la base de un `docker compose up` recién levantado: `sectores`, `sectores/{id}`, `sectores/{id}/cortes`, `cumplimiento*`, `estadisticas`, `bitacora`, `bitacora/{id}/sustento`, `v2/requests.json`, `sistema/modo`. Se usa el primer id que devuelva cada listado.
- **Los GET del panel**, con la sesión de un ADMIN y con las mismas variables que `verificar-flujos.mjs`: `ADMIN_CORREO`, `ADMIN_CLAVE`, `TOTP_SECRETO`.
- **Las cabeceras de contrato:** presencia de `X-Total-Count`, `Link`, `Retry-After` y `Content-Type`.
- **Una muestra de errores**, de la que guarda el estado y el `type`:
  - sector inexistente (404)
  - filtro mal formado (400)
  - ruta del panel sin sesión (401)
  - método no permitido (405)
  - foto WebP (415)

`guardar` escribe `scripts/reduccion/linea-base/forma.json`. `comparar` imprime las diferencias y sale con 1 si hay alguna.

### 4. Ampliar `scripts/verificar-flujos.mjs` a los 89 endpoints

Hoy recorre ~37. Hay que añadir un registro de cobertura:
- cada `paso` declara su `método + plantilla de ruta`
- al final, el script lee las rutas de `backend/openapi.yaml` y lista las que **nadie recorrió**

R0 termina cuando la lista de no recorridas tiene solo `/api/sim/**`, que cubre el guion de simulación (`backend-sim`).

Faltan, como mínimo:
- **Público:** `GET /api/sectores/stream` (llega al menos un evento), `POST /api/sectores/{id}/restablecimiento`, `GET /api/cumplimiento/calidad`, `/serie`, `/serie.csv`, `GET /api/estadisticas/exportar.csv`, `GET` y `POST /api/suscripciones/cancelar` con token, `GET /api/fotos/{n}`.
- **Panel:**
  - cortes: `PATCH /api/veedor/cortes/{id}/sectores/{s}/cierre`, `…/confirmacion`, `…/anulacion`; `GET /api/veedor/cortes/vencidos`, `GET /api/veedor/cortes/{id}`
  - reportes: `PATCH /api/veedor/reportes/{id}/descartar`, `…/foto/descartar`; `GET /api/veedor/disputas`, `GET /api/veedor/fotos/{n}`
  - ingesta: `PATCH /api/veedor/ingesta/propuestas/{id}/aprobar|descartar|anulacion`
  - cuenta: `POST /api/veedor/segundo-factor/baja`, `POST /api/veedor/cuenta/clave`
  - usuarios: `PATCH /api/veedor/usuarios/{id}/aprobacion|rechazo|suspension|reactivacion|permisos`, `POST /api/veedor/usuarios/{id}/invitacion/reenvio`
  - sistema: `GET /api/veedor/sistema/metricas`
- **Cuentas:** `POST /api/cuentas/registro|verificacion|restablecimiento|clave`, `POST /api/cuentas/verificacion/reenvio`, los 6 `GET`/`POST` de `/api/cuentas/enlaces/*` (HTML), `POST /api/cuentas/vecino`.
- **Vecino:** `POST /api/vecino/sesion|verificacion-barrio|sesion/cierre`, `GET /api/vecino/yo`, `PATCH /api/vecino/perfil`.
- **Otros:** `POST /api/iot/presion` (con y sin clave).

Los correos se leen de Mailhog, como ya hace el script con la suscripción. Para las rutas que cambian estado se crean datos
propios con el `SUFIJO` del script; nunca se reutilizan los de la siembra.

### 5. ArchUnit de transición

En `backend/src/test/java/com/aguavigia/ctg/architecture/ReglaDeOroArchitectureTest.java`:

1. Se crea la constante `PAQUETES_NUEVOS` con estos paquetes:
   - `com.aguavigia.ctg.compartido..`
   - `..sectores..`, `..cortes..`, `..reportes..`, `..bitacora..`, `..cumplimiento..`, `..estadisticas..`
   - `..ingesta..`, `..suscripciones..`, `..cuentas..`, `..sistema..`

   Se añade a las listas permitidas de `dominioNoDebeDependerDeNadaQueNoSeaJavaODominioMismo` y de
   `applicationSoloDebeDependerDeDominioJavaYLogging`. El código viejo podrá así usar lo que ya se movió. Cada regla lleva
   un comentario: `// transición de la reducción (docs/reduccion): se retira en R9`.
2. Se **añaden ya** las reglas finales (ver [invariantes §5](invariantes.md#5-reglas-de-archunit-que-se-conservan-con-otra-forma)), con `.allowEmptyShould(true)` mientras los paquetes estén vacíos:
   - `reglasNoImportanFramework`
   - `controladoresNoTocanAlmacenes`
   - `compartidoNoDependeDeFuncionalidades`
   - `soloBitacoraCreaEventos`

   Así cada fase se valida contra ellas desde el primer día.
3. Las reglas de `@PreAuthorize` del panel y del vecino **no se tocan**.


### 6. `scripts/reduccion/esquema-datos.mjs`

Requisito 6: **MongoDB y Redis, intactos.** El contrato HTTP no ve la base, así que esta herramienta la compara directamente.

Uso: `guardar | comparar`, contra `${MONGODB_URI}` (por defecto `mongodb://localhost:27017/?directConnection=true`, base `aguavigia`) y Redis
(`REDIS_HOST`/`REDIS_PORT`, base 0). Sin dependencias nuevas: Mongo con el paquete `mongodb` que ya usan los demás scripts, y Redis hablando
el protocolo por un socket, como `scripts/simulacion/lib/redis.mjs`. **Solo lee**; no escribe nada en ninguna base.

**Mongo**, por colección:
- el nombre de la colección
- **los índices completos**, con todas sus opciones: clave, `unique`, `sparse`, `expireAfterSeconds`, `partialFilterExpression`, `2dsphere`. Se lee con `listIndexes`; cuenta el nombre del índice
- **la forma de los documentos**, con los tipos BSON distinguidos (`string`, `int`, `long`, `double`, `bool`, `date`, `objectId`, `decimal`, `binData`, `null`, `array`, `object`). Se unen todos los documentos de la colección (o una muestra grande si es enorme): un campo que unos traen y otros no sale como opcional, y uno que es `null` en unos y texto en otros sale como `null|string`. Reutiliza `unir` de `lib/forma.mjs`

**Redis**, sobre la base 0 después de correr `verificar-flujos.mjs` (que mueve consenso, cupos, limitador, revocación de sesión y caché):
- `SCAN` de todas las claves, agrupadas por patrón (los ids pasan a `{id}`)
- por patrón: el tipo (`TYPE`), y si **tiene o no caducidad** (`TTL`), sin guardar el valor del TTL, que cambia con el tiempo
- los canales de pub/sub activos (`PUBSUB CHANNELS`)

Con esto R0 **completa la tabla de claves de Redis** de [invariantes §6](invariantes.md#claves-de-redis) con lo medido.

`guardar` escribe `scripts/reduccion/linea-base/esquema-datos.json`. `comparar` imprime cada diferencia como `ruta: antes → después` y
sale con 1 si hay alguna.

**Orden para que la comparación sea justa** (cada vez, en la línea base y en cada puerta):
1. Base recién levantada → `comparar-contrato.mjs` e `instantanea.mjs`.
2. Correr `verificar-flujos.mjs` → ahora la base y Redis tienen datos de todo tipo.
3. `esquema-datos.mjs`.

**Compatibilidad con datos ya escritos.** Que la forma sea igual no basta si el código nuevo no sabe leer lo que escribió el viejo. R0 guarda
un respaldo de la base después de los flujos (`scripts/backup-mongo.sh`, fuera de git). En cada fase que toque persistencia se restaura
ese respaldo (`scripts/restore-mongo.sh`) sobre el código nuevo y se vuelven a pasar `verificar-flujos.mjs` y la instantánea.

### 7. Matriz de escenarios de negocio

Requisito 1: **ningún hueco en la lógica de negocio.** El trabajo está descrito en
[`escenarios-de-negocio.md`](escenarios-de-negocio.md#lo-que-r0-hace-con-este-archivo):
1. Llenar, para cada escenario, el test que lo cubre (clase y método).
2. Marcar **SIN TEST** los que no tengan ninguno.
3. **Escribir los tests que falten**, contra el código de hoy, y confirmar que pasan. Es la única excepción a «R0 no toca el código de pruebas»: se añaden tests, no se cambia ninguno existente.
4. Confirmar una por una las filas que Claude infirió (las que tienen «—» en el guion). Si una describe algo que el código no hace, se corrige la fila y se pregunta al dueño.
5. Contrastar la lista con los ADR de `docs/07-decisiones-clave.md` y proponer al dueño los escenarios que falten.

R0 no termina hasta que la matriz no tenga ninguna fila **SIN TEST** y el dueño haya dado el visto bueno a la lista.

### 8. Línea base de capacidad

Requisito 3: **los endpoints aguantan a todos los usuarios acordados, a la vez.** R0 mide cuánto aguanta el backend hoy, para que ninguna
fase lo empeore.

1. **Carga mixta con `scripts/carga/demo.mjs`** (perfil `carga`, `docker-compose.carga.yml`). Propuesta de partida, que el dueño ajusta:
   ```bash
   export CLAVE_VEEDORES="<una clave cualquiera válida>"   # la usan los registros; si falta, los 10 000 fallan con 400
   node scripts/carga/demo.mjs --usuarios 5000 --ventana 60 --conectados 10000 \
     --registros 10000 --tasa-registros 50 --suscripciones 5 --veedores 0 \
     --sin-correo --restaurar --sobre-datos-reales
   ```
   Equivale a 10 000 personas registrándose, 5 000 reportando, otras suscribiéndose y leyendo, con 10 000 conexiones en vivo.
   `--veedores 0` porque el ingreso de veedores necesita la clave de las cuentas de demostración, que ya no está en el repositorio.
   `--sobre-datos-reales` porque la base ya tiene los datos que dejan los flujos (el script se niega si hay reportes o cortes); `--restaurar` los devuelve.
   Los umbrales son los que ya trae `scripts/carga/flujo-ciudadano.js`: reporte p95 < 1 s, lectura p95 < 1 s, registro p95 < 2 s, fallos < 1 %.
   **Resultado de R0:** de 5 corridas, la 1.ª cumplió todos los umbrales; las demás fallaron por latencia de reportes o por colapso (ver [Avance](README.md#línea-base-de-r0-2026-10-07) y [ADR-099](../07-decisiones-clave.md#adr-099--cierre-de-r0-requisitos-del-dueño-decisiones-de-la-red-de-seguridad-y-criterio-de-capacidad)).
   Antes de lanzarla: exportar `CLAVE_VEEDORES` (sin ella los registros fallan en 1 ms con 400 y la corrida no cuenta) y medir con un solo stack de Docker en marcha.

   **Criterio de la puerta (decidido en ADR-099).** El PC es compartido y la corrida varía mucho, así que se pide:
   - Pasa si, en **hasta 3 corridas consecutivas**, **al menos una** cumple todos los umbrales de `flujo-ciudadano.js` (reporte p95 < 1 s, lectura p95 < 1 s, registro p95 < 2 s, fallos < 1 %, menos de 50 iteraciones descartadas). La serie se detiene en la primera que cumple.
   - Si las 3 fallan: se corre la misma carga contra la etiqueta `pre-reduccion` ese mismo día. La fase pasa si no queda peor que ese contraste; si queda peor, no se cierra.
   - Una corrida que colapsa (más de la mitad de las peticiones falla) cuenta como fallida y se investiga aunque la siguiente pase.
   - Se anotan todas las corridas. Una con el entorno mal puesto no cuenta, pero se anota.
   - Obligatorio al cerrar **R2, R4, R8 y R9**. **R8** además mide `POST /api/veedor/sesion` bajo carga con una clave de prueba generada (R0 no pudo: la clave de las cuentas demo ya no está en el repositorio).
2. **Cobertura de endpoints.** Hoy `flujo-ciudadano.js`, `lectura-publica.js` y `escritura-reportes.js` no tocan los 89 endpoints. R0 lista cuáles
   quedan sin carga (con el mismo registro de cobertura del [punto 4](#4-ampliar-scriptsverificar-flujosmjs-a-los-89-endpoints)) y **añade a `scripts/carga/` los escenarios k6 que falten**, hasta que todos tengan carga, salvo `/api/sim/**` y los que
   envían correo real (que usan `--sin-correo`).
3. **Anotar la línea base** en la tabla de [Avance](README.md#avance): p95 y p99 por grupo, tasa de errores, peticiones por segundo, y cuánto tardó
   el equipo en absorber los registros. Los umbrales de cada fase salen de esta línea base y los aprueba el dueño.
4. Las fases R2, R4, R8 y R9 repiten el paso 1 y no pueden quedar por debajo de la línea base. Las demás fases solo pasan el recorrido pequeño del paso 10 de la [puerta](README.md#la-puerta-lo-que-se-comprueba-al-cerrar-cada-fase).

`--restaurar` devuelve Mongo y Redis a su estado anterior, así que antes de correrlo se hace el respaldo que pide su README.

### 9. Cómo correr la puerta (lo que R0 aprendió al ejecutarla)

**Preparación, una vez** (todo local; `.env` está ignorado por git):
- Un `.env` con `INGESTA_MODO=local`, `RATE_LIMIT_FACTOR=100` y `RATE_LIMIT_FACTOR_CUENTAS=1000`. **`INGESTA_MODO=local` es obligatorio para comparar**: con `auto` el primer ciclo de ingesta (a los ~60 s de arrancar) trae boletines en vivo de Internet y publica avisos, propuestas y eventos distintos cada vez, y ninguna línea base sería reproducible. `instantanea.mjs` además espera a que termine ese primer ciclo antes de capturar. Sin lo segundo, las rutas de cuentas (registro, reenvío, verificación, restablecimiento) topan con su límite por IP a los pocos intentos y la puerta no se puede repetir seguido: `RATE_LIMIT_FACTOR` **no** las afloja. Estos topes tienen sus propias pruebas; la puerta mide lo funcional.
- Una clave de ADMIN conocida: un hash BCrypt en `VEEDOR_PASSWORD_HASH` (cada `$` va escapado como `$$` en el `.env`) y su clave en una variable de entorno `ADMIN_CLAVE`. Si el ADMIN ya se creó con otra clave, `node scripts/restablecer-admin.mjs --correo admin@aguavigia.local --clave-del-env` la fija.
- `node scripts/simulacion/preparar.mjs` genera `SIMULACION_CLAVE` para la instancia de simulación.
- Un archivo (fuera del repositorio) para el secreto del segundo factor, que se pasa como `TOTP_ARCHIVO`: el ADMIN lo da de alta la primera vez y los scripts lo reutilizan. Un código TOTP **solo vale una vez** por franja de 30 s, así que dos scripts seguidos pueden toparse; el ayudante `scripts/lib/sesion-admin.mjs` espera a la franja siguiente y reintenta.
- En Git Bash de Windows, anteponer `MSYS_NO_PATHCONV=1` a los comandos que lleven rutas que empiezan por `/` (por ejemplo `docker exec`, o argumentos como `/api/...`).

**En cada cierre de fase**, la parte de la puerta que no necesita Java es un solo comando (desde la raíz, con `ADMIN_CLAVE` y `TOTP_ARCHIVO` exportados):
```bash
PUERTA_BORRA_VOLUMENES=si scripts/reduccion/puerta.sh comparar     # `guardar` solo para reescribir las líneas base
```
Hace, en orden: `docker compose down -v` y `up -d --build`; espera las 30 000 cuentas y corre `sembrador verificar`; compara el contrato y la forma con la base
recién levantada; corre `verificar-flujos.mjs` con `EXIGIR_COBERTURA=1` (todos los pasos y las 84 operaciones); compara la forma con datos y, al final, el esquema de Mongo y Redis.
Termina con `TODO EN VERDE` o con la lista de lo que falló. Después hay que correr aparte:
```bash
(cd backend && ./mvnw verify)                                          # con Docker corriendo
docker compose --profile simulacion up -d --build backend-sim
docker compose --profile simulacion run --rm simulador iniciar --velocidad 300
```
Y la capacidad ([§8](#8-línea-base-de-capacidad)) y una pasada pequeña de `agregar-usuarios` y `demo.mjs` (tras `puerta.sh`, con la base ya cargada).

**Lo que `down -v` borra (lo hace `puerta.sh`):** los tres volúmenes del proyecto (Mongo, Redis y fotos). Antes, `scripts/backup-mongo.sh` y `scripts/backup-fotos.sh`.

**Opciones de las herramientas de R0:**
- `instantanea.mjs`: `INSTANTANEA_NOMBRE` (`forma` o `forma-con-datos`), `TOTP_ARCHIVO` y `PROBAR_LIMITE=1` (provoca un 429 para comprobar `Retry-After`; agota la cuota de dispositivos por IP durante una hora, por eso no va por defecto).
- `verificar-flujos.mjs`: `EXIGIR_COBERTURA=1` hace fallar el script si queda alguna operación del contrato sin recorrer (menos `/api/sim/**`), y `TOTP_ARCHIVO` guarda y reutiliza el segundo factor.

## Pasos

1. `git checkout refactor/reduccion-backend && git merge main`.
2. Levantar un entorno limpio:
   ```bash
   docker compose down -v
   docker compose up -d --build
   ```
   Esperar a que `backend` esté sano (ver [`01-levantar-a-mano.md`](../01-levantar-a-mano.md)). **Antes del `down -v`, respaldo** de lo que haya (`scripts/backup-mongo.sh` y `scripts/backup-fotos.sh`).
   Luego comprobar los requisitos 2 y 4:
   ```bash
   docker compose run --rm sembrador verificar        # 211 barrios y 30 000 cuentas sintéticas
   ```
   Anotar **con qué comando exacto aparecen las 30 000 cuentas y cuánto tardan** ([invariantes §6](invariantes.md#6-datos-de-arranque-y-entornos)); la base que había al empezar R0 tenía 1 usuario.
3. Escribir los scripts 1 a 3 y sus pruebas en `scripts/pruebas/` (`node --test`). Probar la normalización con dos JSON de ejemplo.
4. Guardar la línea base de contrato y forma, con la base recién levantada:
   ```bash
   node scripts/reduccion/comparar-contrato.mjs guardar
   node scripts/reduccion/instantanea.mjs guardar
   ```
   Commitear `scripts/reduccion/linea-base/`.
5. Comprobar que comparar contra sí mismo da 0 diferencias. Reiniciar `docker compose down -v && up` y volver a comparar: así se ve si algún campo depende de la siembra. Si aparece ruido, ajustar la normalización, **no** ignorar el campo.
6. Ampliar `verificar-flujos.mjs` hasta que la lista de no recorridas solo tenga `/api/sim/**`. Correrlo.
7. Escribir `esquema-datos.mjs` ([§6](#6-scriptsreduccionesquema-datosmjs)) y guardar su línea base **después** de los flujos. Hacer el respaldo de la base resultante. Comprobar que comparar contra sí mismo da 0.
8. Matriz de escenarios ([§7](#7-matriz-de-escenarios-de-negocio)): llenarla, escribir los tests que falten y obtener el visto bueno del dueño.
9. Reglas de ArchUnit de transición; `cd backend && ./mvnw verify`.
10. Línea base del guion: `docker compose --profile simulacion up -d --build backend-sim` y luego `docker compose --profile simulacion run --rm simulador iniciar --velocidad 300`. Anotar en la tabla de Avance si pasa y cuánto tarda.
11. Línea base de capacidad ([§8](#8-línea-base-de-capacidad)), con el respaldo hecho. Anotar los resultados y fijar los umbrales con el dueño.
12. Línea base de los scripts de apoyo (requisito 5): una pasada pequeña de `agregar-usuarios` (modos `directo` y `api`, con `--borrar-lote`), de `scripts/carga/demo.mjs` y del simulador. Anotar que funcionan y cuánto tardan.
13. Línea base del frontend (informativa): `cd frontend && npm run api:check`. Hoy falla porque `esquema.ts` está atrasado, y eso es trabajo de Yordy. Se anota y **no** es parte de la puerta.
14. `scripts/reduccion/medir.sh` → fila «R0» en la tabla de Avance.
15. Cerrar la fase (ver [Cómo se trabaja](README.md#cómo-se-trabaja)) y crear la etiqueta `reduccion-R0`.

## Terminado cuando

- [x] Los tres scripts de comparación existen, tienen pruebas y la comparación de la línea base contra sí misma da 0.
- [x] `esquema-datos.mjs` existe y su línea base contra sí misma da 0, en Mongo y en Redis.
- [x] `verificar-flujos.mjs` recorre todo menos `/api/sim/**`, y todo pasa.
- [x] La matriz de [escenarios de negocio](escenarios-de-negocio.md) no tiene ninguna fila **SIN TEST** y el dueño la aprobó (decisiones del 2026-10-08).
- [x] Está anotado cómo aparecen las 30 000 cuentas al arrancar, y `verificar` pasa.
- [x] La línea base de capacidad está medida y el criterio de la puerta está fijado (ADR-099).
- [x] `./mvnw verify` está en verde con las reglas de transición.
- [x] El guion de simulación pasa sobre `backend-sim`, y los scripts de apoyo funcionan.
- [x] `git diff pre-reduccion -- backend/src/main` está vacío. (Se añaden tests en `backend/src/test`; el código de producción no se toca.)


**R0 cerrada el 2026-10-08** (etiqueta `reduccion-R0`).

## Prompt para Claude Code

```
Lee docs/reduccion/README.md (incluida la sección «Requisitos del dueño»), invariantes.md, escenarios-de-negocio.md y R0-red-de-seguridad.md.
Ejecuta la fase R0 en la rama refactor/reduccion-backend. No modifiques nada bajo backend/src/main.
Construye scripts/reduccion/medir.sh, comparar-contrato.mjs, instantanea.mjs y esquema-datos.mjs con sus pruebas en scripts/pruebas,
amplía scripts/verificar-flujos.mjs con el registro de cobertura hasta que solo falte /api/sim/**,
y añade las reglas ArchUnit de transición y las finales con allowEmptyShould.
Llena la matriz de escenarios de negocio, escribe los tests que falten contra el código de hoy y dime cuáles eran SIN TEST.
Levanta el entorno con docker compose (haz respaldo antes de cualquier down -v), comprueba las 30 000 cuentas con
`docker compose run --rm sembrador verificar`, guarda las líneas base y verifica que comparar contra sí mismas da 0.
Mide la línea base de capacidad con scripts/carga/demo.mjs y deja los resultados en la tabla de Avance.
Al final ejecuta la puerta completa del README y dime qué pasó, con la salida de cada comando.
No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
