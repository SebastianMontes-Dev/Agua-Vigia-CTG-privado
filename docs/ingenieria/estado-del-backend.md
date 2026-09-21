# Estado del backend — documento de traspaso

> **Para qué sirve.** Es el punto de entrada para quien retome el backend: qué es, en qué estado está,
> qué falta y las trampas del entorno que cuestan una hora si nadie las avisa.
>
> **Última actualización:** 2026-09-21 · **Rama:** `chore/retirar-frontend` (sin fusionar a `main`)
>
> Si algo no cuadra con el código, gana el código. Lo que **garantiza la build** es el contrato
> (`ContratoOpenApiTest`) y la [matriz de trazabilidad](matriz-trazabilidad.md); lo demás se actualiza a mano.
>
> Este documento sustituye a la versión de 2026-08-12, que narraba rondas de trabajo ya cerradas (con 462
> pruebas y 30 rutas). Ese historial vive en git, en la [bitácora de sesiones](../gestion/bitacora-sesiones.md)
> y en el [registro de bugs](../gestion/registro-de-bugs.md).

---

## 1. Qué es y qué alcance tiene

Plataforma ciudadana de monitoreo del acueducto de **Cartagena de Indias** (proyecto personal, 2026): cruza
los avisos de Acuacar con reportes ciudadanos georreferenciados y publica un **Índice de Cumplimiento**.
**No está afiliada a Aguas de Cartagena S.A. E.S.P.**

**El repositorio ya no tiene frontend** (`ADR-048`): el código anterior queda en la etiqueta git
`pre-retiro-frontend` y lo rehará otra persona a partir de [`docs/api/`](../api/README.md). Lo que hay aquí es
**backend + datos + infraestructura**.

## 2. Estado actual — verificado

| Qué | Valor | Cómo se comprobó |
|---|---|---|
| Pruebas de backend | **782** (1 solo corre a petición: regenerar el contrato) · 0 fallos | `./mvnw verify`, 2026-09-21 |
| Cobertura | JaCoCo ≥ 85 % en `domain/` y `application/` | El propio `verify` lo exige |
| Arquitectura | 5+ reglas ArchUnit en verde | `ReglaDeOroArchitectureTest` |
| API | **58 operaciones** en 17 controladores, 34 esquemas | `backend/openapi.yaml` (generado) |
| Persistencia | 10 colecciones Mongo, `2dsphere` en `sectores.geometry` | `IndicesMongo` |
| Redis | consenso, cupo RF006, rate limit, revocación de sesión, caché, pub/sub SSE, bloqueo de jobs | — |
| Jobs | ingesta cada 10 min, ventanas cada 60 s, limpieza de fotos y purga de evidencia (diarias) | `@Scheduled`, todos vía `EjecucionUnica` |
| CI | `backend-ci`, `despliegue-ci`, `secret-scan` | `.github/workflows/` |

**Requisitos:** 46 RF → 40 cumplidos, 5 descartados por decisión (RF032–036, IA) y **1 pendiente: RF041**
(webhook real de WhatsApp/Telegram, depende de credenciales de terceros). Los requisitos de interfaz quedan
**retirados por alcance** hasta que exista el frontend nuevo (`ADR-048`).

Las pruebas de integración exigen **Docker** (Testcontainers `mongo:7.0` y `redis:7-alpine`).

## 3. Arquitectura

Arquitectura Limpia (puertos y adaptadores): `domain/` (Java puro) ← `application/` ← `infrastructure/` y
`api/`. Las reglas y el porqué están en [`CLAUDE.md`](../../CLAUDE.md) y en
[`diagrama-de-componentes.md`](diagrama-de-componentes.md). Las consultas de solo lectura sin regla de negocio
van del controlador al puerto de salida (`ADR-015`): **no es un defecto**.

## 4. Qué cambió en la ronda de 2026-09-21

Ronda de pulido para el frontend nuevo y para la meta de 50 000 usuarios. Detalle en los ADR y en el registro
de bugs (`BUG-076` a `BUG-086`).

- **Escalabilidad** (`ADR-049`, `ADR-053`): micro-caché de lecturas en nginx (que al medir resultó no cachear: `BUG-085`), consenso acotado a una evaluación por segundo y sector (`BUG-086`), SSE de aviso, jobs de una sola réplica,
  rate limit atómico, caché tolerante a Redis caído, hilos virtuales y pools acotados, réplicas en el compose de
  producción. Ver [`escalabilidad.md`](escalabilidad.md).
- **Brechas funcionales:** `RF007` (sector por coordenada, `ADR-050`), `RF011` y carrera del consenso
  (`ADR-051`), `RF015` y `RNF009` (baja con enlace en todo correo y correo borrado).
- **Contrato** (`ADR-052`): 404 coherente para recursos de la URL, IoT en RFC 7807, `bearerAuth` y 401/403
  declarados, `GET /api/sectores/geometria` para dibujar el mapa sin llevar el GeoJSON en el cliente.
- **Seguridad:** el alta del segundo factor ya no sustituye un TOTP confirmado sin pedir el código vigente;
  rate limit en suscripciones y en `/segundo-factor/**`.
- **Operación:** `liveness`/`readiness` sin fuentes externas ni correo; SMTP con credenciales y TLS en `prod`;
  el arranque en `prod` aborta si `APP_URL_PUBLICA` apunta a `localhost`.
- **Documentación:** [`docs/api/`](../api/README.md) completa, con la referencia de rutas **generada** desde el
  contrato y los `@PreAuthorize`.

## 5. Qué falta — lista honesta

### Para cumplir la meta de 50 000 usuarios (no está demostrada)
- **No se ha probado a esa escala.** Solo hay una medición reducida (ver [`escalabilidad.md`](escalabilidad.md)).
- **Fotos en disco local:** con réplicas en el mismo host funcionan por el volumen compartido; en hosts
  distintos hacen falta almacenamiento de objetos (S3/MinIO). El puerto `AlmacenamientoPort` ya existe; falta el adaptador.
- **`EstadoColectorRegistry` vive en memoria de cada instancia:** con réplicas, `/api/veedor/ingesta/salud`
  refleja solo lo que ejecutó esa réplica.
- **Mongo y Redis son nodos únicos** (sin replica set ni Sentinel). **No hay CDN ni TLS activo** (RNF026).
- **La observabilidad es mínima:** solo `health`; faltan métricas Prometheus (emisores SSE, pools, latencias).

### Brechas de contrato y funcionales conocidas
- `GET`/`POST` de **confirmar y cancelar suscripción** cambian estado por `GET`: un antivirus que precargue el
  enlace puede confirmar o cancelar solo (a diferencia de las páginas de cuentas, `ADR-030`).
- Sin ruta para: **reenviar** verificación o invitación, **cambiar la propia clave** con sesión, **listar reportes**
  públicamente, **cerrar cortes creados por la ingesta** (no entran al Índice de Cumplimiento).
- Aprobar una propuesta de prensa **sin ventana declarada** responde 200 pero no cambia el sector; no hay guarda
  contra resolver dos veces una propuesta.
- `RF002` (histórico por sector público) y `RNF006` (cola muerta de la ingesta) siguen parciales.
- Las confirmaciones y el consenso **no deduplican por IP**, solo por huella.

### Calidad
- **Cobertura baja en M15:** `UsuarioMongoAdapter`, `EventoAuditoriaMongoAdapter`, `RedisControlIntentosAdapter`,
  `TokenCuentaMongoAdapter` y `RedisRevocacionSesionAdapter` casi no tienen pruebas de adaptador.
- **Capas:** `ContextoHttp` e `IngestaSaludController` importan `infrastructure/` desde `api/`, y ArchUnit no lo vigila
  (ver `ADR-015`: que un controlador lea de un puerto de salida **no** es una violación). Falta una regla ArchUnit
  «todo `/api/veedor/**` lleva `@PreAuthorize`» (`RNF022`).
- **Dependabot:** #5 (jjwt 0.13) y #9 (archunit 1.5) son fusionables; #7 (Spring Boot 4.1), #11 (springdoc 3.1) y #2
  (Testcontainers 2.0) **rompen la build**: Boot 4 es una migración grande y no se mezcla con esto.

### Housekeeping pendiente del dueño
- Borrar `frontend/` **del disco** (`node_modules`, `dist`, `test-results`, ~364 MB, ignorados por git): la regla de
  permisos del proyecto bloquea `rm -rf`.
- Fusionar la rama y cerrar el Sprint 2 en `docs/gestion/sprint-2.md`.

## 6. Trampas del entorno (esto ahorra una hora)

Encontradas en la máquina de desarrollo (Windows 11, Docker Desktop, Git Bash).

1. **El puerto 8080 puede estar ocupado.** El compose de desarrollo publica el backend en **8081**.
2. **Detener un proceso en segundo plano mata el wrapper de Maven, no la JVM hija:** el arranque siguiente falla
   con «port already in use». `scripts/limpiar-puertos.ps1` lo resuelve.
3. **Docker Desktop puede estar parado.** Síntoma: Testcontainers falla con «Could not find a valid Docker
   environment» y `docker ps` no conecta. `docker desktop start` lo arranca.
4. **`docker compose up --build` puede fallar en `dependency:go-offline`** con una descarga de Maven Central caída
   («could not transfer artifact»). Es transitorio; reintentar.
5. **`@SpringBootTest(classes = X)` con `@TestConfiguration` no aísla:** Spring encuentra `CtgApplication` y levanta
   todo, con el scheduler llamando a acuacar.com. Usar `@Configuration`.
6. **Las etiquetas de `aquasecurity/trivy-action` llevan prefijo `v`** (`@v0.36.0`).
7. **Un código de salida de fondo puede ser el de un `echo`, no el de Maven.** Al lanzar `./mvnw … ; echo "exit=$?"`
   en segundo plano, el aviso de finalización dice «exit code 0» aunque la build haya fallado: leer el log.
8. **Hay contenedores de otros proyectos** en el mismo Docker (`pgadmin4`, `my-database`): no tocarlos.

## 7. Comandos

```bash
cd backend && ./mvnw -B verify          # build completa (Docker abierto)
docker compose up -d --build --wait     # stack de desarrollo (API en :8081)
```

**Regenerar el contrato OpenAPI** (sin levantar la app; `ContratoOpenApiTest` falla si se olvida):

```bash
cd backend && ./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true
```

**Regenerar la referencia de rutas** de `docs/api/` (después de lo anterior):

```bash
node scripts/generar-referencia-api.mjs
```

**Comprobar que producción publica solo el puerto 80 del proxy:**

```bash
docker compose -f docker-compose.prod.yml config | grep -A2 "published"
```

**Pruebas de carga:** ver [`scripts/carga/README.md`](../../scripts/carga/README.md).
Mailhog (correos de prueba): `http://localhost:8025` · Swagger: `http://localhost:8081/swagger-ui.html`.

## 8. Dónde está cada cosa

| Ruta | Qué es |
|---|---|
| `backend/openapi.yaml` | Contrato. **Generado, no escrito a mano.** |
| `docs/api/` | Guía para construir el frontend (referencia de rutas generada) |
| `docs/ingenieria/escalabilidad.md` | Arquitectura y estado de la meta de 50 000 usuarios |
| `docs/ingenieria/comportamiento-del-sistema.md` | Comportamiento por capacidad (Cuando / Entonces) |
| `docs/ingenieria/matriz-trazabilidad.md` | RF → prueba que lo sostiene |
| `docs/design-decisions.md` | ADR: qué se decidió y qué se descartó |
| `infra/nginx/` | Proxy de producción: micro-caché, límites, cabeceras |
| `scripts/carga/` | Pruebas de carga (k6 y cliente SSE) |
| `data/geoespacial/` | GeoJSON canónico (213 filas; se siembran 211 sectores) y población |

## 9. Si retomas el trabajo, empieza por aquí

1. Lee [`CLAUDE.md`](../../CLAUDE.md) y [`docs/api/README.md`](../api/README.md).
2. Corre `./mvnw -B verify` y confirma el estado de la sección 2 antes de tocar nada.
3. **Antes de cambiar el contrato de la API, pregunta:** quien construya el frontend lo estará usando.
4. Un cambio de comportamiento se documenta en `comportamiento-del-sistema.md` **en el mismo cambio**, y se
   registra con las skills del proyecto (`registrar-decision`, `registrar-bug`, `registrar-implementacion`).
