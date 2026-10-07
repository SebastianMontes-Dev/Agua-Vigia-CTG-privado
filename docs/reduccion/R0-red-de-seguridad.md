# R0 · Red de seguridad

**Objetivo:** antes de mover una sola clase, dejar herramientas que digan con certeza si algo cambió por fuera. Esta fase
**no toca código de producción** (`backend/src/main`).

Riesgo: bajo. Esfuerzo: 1–2 sesiones.

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

## Pasos

1. `git checkout refactor/reduccion-backend && git merge main`.
2. Levantar un entorno limpio:
   ```bash
   docker compose down -v
   docker compose up -d --build
   ```
   Esperar a que `backend` esté sano (ver [`01-levantar-a-mano.md`](../01-levantar-a-mano.md)).
3. Escribir los scripts 1 a 3 y sus pruebas en `scripts/pruebas/` (`node --test`). Probar la normalización con dos JSON de ejemplo.
4. Guardar la línea base:
   ```bash
   node scripts/reduccion/comparar-contrato.mjs guardar
   node scripts/reduccion/instantanea.mjs guardar
   ```
   Commitear `scripts/reduccion/linea-base/`.
5. Comprobar que comparar contra sí mismo da 0 diferencias. Reiniciar `docker compose down -v && up` y volver a comparar: así se ve si algún campo depende de la siembra. Si aparece ruido, ajustar la normalización, **no** ignorar el campo.
6. Ampliar `verificar-flujos.mjs` hasta que la lista de no recorridas solo tenga `/api/sim/**`.
7. Reglas de ArchUnit de transición; `cd backend && ./mvnw verify`.
8. Línea base del guion: `docker compose --profile simulacion up -d --build backend-sim` y luego `docker compose --profile simulacion run --rm simulador iniciar --velocidad 300`. Anotar en la tabla de Avance si pasa y cuánto tarda.
9. Línea base del frontend (informativa): `cd frontend && npm run api:check`. Hoy falla porque `esquema.ts` está atrasado, y eso es trabajo de Yordy. Se anota y **no** es parte de la puerta.
10. `scripts/reduccion/medir.sh` → fila «R0» en la tabla de Avance.
11. Cerrar la fase (ver [Cómo se trabaja](README.md#cómo-se-trabaja)) y crear la etiqueta `reduccion-R0`.

## Terminado cuando

- Los tres scripts existen, tienen pruebas y la comparación de la línea base contra sí misma da 0.
- `verificar-flujos.mjs` recorre todo menos `/api/sim/**`, y todo pasa.
- `./mvnw verify` está en verde con las reglas de transición.
- `git diff pre-reduccion -- backend/src/main` está vacío.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R0-red-de-seguridad.md.
Ejecuta la fase R0 en la rama refactor/reduccion-backend. No modifiques nada bajo backend/src/main.
Construye scripts/reduccion/medir.sh, comparar-contrato.mjs e instantanea.mjs con sus pruebas en scripts/pruebas,
amplía scripts/verificar-flujos.mjs con el registro de cobertura hasta que solo falte /api/sim/**,
y añade las reglas ArchUnit de transición y las finales con allowEmptyShould.
Levanta el entorno con docker compose, guarda la línea base y verifica que comparar contra sí misma da 0.
Al final ejecuta la puerta completa del README y dime qué pasó, con la salida de cada comando.
No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
