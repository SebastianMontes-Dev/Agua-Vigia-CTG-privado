# Estado del backend — documento de traspaso

> **Para qué sirve.** Es el punto de entrada para quien retome el backend: qué es, en qué estado está,
> qué falta y las trampas del entorno que cuestan una hora si nadie las avisa.
>
> **Última actualización:** 2026-09-29 · **Rama:** `main` hasta el PR #106 (cierre funcional del backend, #90–#101, ya fusionado)
> más los PR #107–#110 (`ADR-085`–`ADR-088`), abiertos al escribir esto
>
> Si algo no cuadra con el código, gana el código. Lo que **garantiza la build** es el contrato
> (`ContratoOpenApiTest`) y la [matriz de trazabilidad](matriz-trazabilidad.md); lo demás se actualiza a mano.
>
> Este documento sustituye a la versión de 2026-08-12, que narraba rondas de trabajo ya cerradas (con 462
> pruebas y 30 rutas). Ese historial vive en git, en la [bitácora de sesiones](../gestion/bitacora-sesiones.md)
> y en el [registro de bugs](../gestion/registro-de-bugs.md).

---

## 1. Qué es y qué alcance tiene

Plataforma ciudadana de monitoreo del acueducto de **Cartagena de Indias** (proyecto académico de dos personas, 2026: backend de Sebastian, frontend de Yordy Pardo): cruza
los avisos de Acuacar con reportes ciudadanos georreferenciados y publica un **Índice de Cumplimiento**.
**No está afiliada a Aguas de Cartagena S.A. E.S.P.**

**`main` tiene backend, datos, infraestructura y el frontend nuevo.** El frontend anterior se retiró (`ADR-048`; su código
sigue en la etiqueta git `pre-retiro-frontend`, ya también en `origin`) y se rehace en `frontend/` (React 19 + Vite,
`ADR-067`) a partir de [`docs/api/`](../api/README.md): F0, F3 y F4 están cerradas; F1 (prototipos) y F2 siguen sin cerrar (falta la
revisión visual del dueño y, en F2, los 3 s en 3G, `BUG-115`); F5, el panel del veedor, tiene avance en la rama
`feat/f5-ingreso-panel`, sin fusionar, y falta F6 (plan: [`plan-frontend.md`](plan-frontend.md); avance: `sprint-7.md`). El proyecto
corre solo en local (`ADR-057`, `ADR-080`) y se levanta con un solo `docker compose up` (`ADR-086`, sección 7).

## 2. Estado actual — verificado

| Qué | Valor | Cómo se comprobó |
|---|---|---|
| Pruebas de backend | **1 109** (1 solo corre a petición: regenerar el contrato) · 0 fallos · 0 errores. Incluye las 5 de `ADR-086`/`ADR-088` (`SembradorAdminInicialTest` +3, `CorreoDeCuentaDescartadoAdapterTest` +2). Más 5 de los scripts de datos (`cd scripts && npm test`) | `./mvnw verify` con Docker en la máquina del dueño, 2026-09-29, rama `docs/barrido-contradicciones` (con #107–#110) |
| Cobertura | JaCoCo ≥ 85 % en `domain/` y `application/`; real **91,1 %** en `domain/`, **97,7 %** en `application/` y **94,1 %** en todo el backend (instrucciones) | El propio `verify` lo exige; cifras de `target/site/jacoco/jacoco.csv`, 2026-09-29 |
| Arquitectura | 10 reglas ArchUnit en verde, incluidas «`api` no depende de `infrastructure`» y «toda ruta de `/api/veedor/**` lleva `@PreAuthorize`» | `ReglaDeOroArchitectureTest` |
| API | **64 operaciones** en 21 controladores, 37 esquemas | `backend/openapi.yaml` (generado) |
| Persistencia | 13 colecciones Mongo, `2dsphere` en `sectores.geometry` | anotaciones `@Document` de `infrastructure/persistence`, `IndicesMongo` |
| Redis | consenso, cupo RF006, rate limit, revocación de sesión, caché, pub/sub SSE, bloqueo de jobs | — |
| Jobs | ingesta cada 10 min, ventanas cada 60 s, limpieza de fotos (diaria) y purga de evidencia (apagada a propósito, `ADR-027`, `ADR-085`) | `@Scheduled`, todos vía `EjecucionUnica` |
| CI | `backend-ci`, `frontend-ci`, `contenedores-ci`, `escaneo-de-fugas` (gitleaks) y `autoria` | `.github/workflows/` |

**Requisitos:** 46 RF → 39 ✅ y 7 🟡 en la [matriz](matriz-trazabilidad.md), contados fila por fila el 2026-09-29. Los 7 parciales:
RF004, RF008 y RF022 tienen el backend cumplido y la interfaz en F2/F3 sin cerrar (`ADR-067`); RF032, RF034 y RF036 quedan
parciales porque el proyecto no usa IA (`ADR-025`: extractor heurístico con confianza, cita y cola de revisión); RF041
(Telegram) está armado sin conectar (falta el bot real, `ADR-066`). Hasta el 2026-09-29 la cuenta daba 42 porque RF004, RF008 y
RF022 conservaban el ✅ del frontend retirado.

Las pruebas de integración exigen **Docker** (Testcontainers `mongo:7.0` y `redis:7-alpine`).

## 3. Arquitectura

Arquitectura Limpia (puertos y adaptadores): `domain/` (Java puro) ← `application/` ← `infrastructure/` y
`api/`. Las reglas y el porqué están en [`CLAUDE.md`](../../CLAUDE.md) y en
[`diagrama-de-componentes.md`](diagrama-de-componentes.md). Las consultas de solo lectura sin regla de negocio
van del controlador al puerto de salida (`ADR-015`): **no es un defecto**.

## 4. Qué cambió en las últimas rondas

### Arranque de un solo comando y demo ampliada (2026-09-29, PR #107–#110, abiertos)

Detalle y cifras medidas en cada ADR; arranque en [`entorno-local.md`](entorno-local.md).

- `ADR-085`: la auditoría de código sin uso no encontró nada que borrar salvo un método; Open311, IoT y la purga se conservan.
- `ADR-086`: `docker compose up` sin `.env`: secretos autogenerados en el perfil `docker` y servicio `sembrador` que deja la base lista.
- `ADR-087`: `docker compose run --rm sembrador agregar-usuarios --cantidad N` agrega cuentas con faker, borrables por lote.
- `ADR-088`: la demo de carga suma registro masivo por la API (`--registros`) y el visor `docker compose run --rm sembrador monitor`.

### Cierre funcional del backend (2026-09-29, PR #90–#101)

Decisión del dueño: proyecto solo local (`ADR-080`; lo de producción queda en la etiqueta `pre-solo-local`) y backend cerrado para presentarlo. Detalle en `docs/gestion/registro-de-implementaciones.md`.

- **Solo local:** se retiran nginx, `docker-compose.prod.yml` y el perfil `prod`; un solo `docker-compose.yml` (`ADR-080`).
- **Cuentas:** barrio opcional en la cuenta con filtro por barrio (`ADR-081`); `RNF024` cumplido y medido con tiempo constante mínimo de 100 ms en el registro y el restablecimiento de clave (`BUG-123`).
- **Ingesta sin internet:** `INGESTA_MODO=local` lee 13 boletines reales de Acuacar guardados (`ADR-082`); una propuesta de ingesta ya resuelta responde `409` y no se resuelve dos veces.
- **Datos de demo:** `scripts/sembrar-usuarios-demo.mjs` deja 30 000 cuentas completas (barrio, segundo factor, tokens, auditoría); desde `ADR-086` lo corre el `sembrador`.
- **Demo de carga** (`ADR-083`): `node scripts/carga/demo.mjs`; 30 000 reportes con 30 000 conexiones SSE, p95 de 130 a 164 ms medido en un PC de 12 hilos compartido con el generador. Los 50 000 de `RNF027` **no** se demuestran en un PC (detalle y límites en [`escalabilidad.md`](escalabilidad.md)).
- **Contrato:** el `openapi.yaml` ya no expone rutas de prueba (`BUG-121`); son 64 operaciones.
- **Calidad:** pruebas de adaptadores y dos reglas ArchUnit nuevas (`ADR-084`); cifras en la sección 2.

### Ronda de 2026-09-21 (histórico)

Ronda de pulido para el frontend nuevo y para la meta de 50 000 usuarios; **lo que menciona de nginx, réplicas y producción
quedó sin efecto con `ADR-080`**. Detalle en los ADR y en el registro de bugs (`BUG-076` a `BUG-088`).

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
- **Contrato para el frontend** (`ADR-054`, `ADR-055`, `ADR-056`): confirmar y cancelar suscripción por `POST` (el `GET` solo
  muestra un botón), la bitácora entrega el conteo de reportes de sustento y sus ids aparte, `poblacion` en los sectores,
  histórico público de cortes por sector (`RF002`), cambio de clave con sesión y reenvío de verificación e invitación.
- **Respaldos** (`BUG-088`): `backup-mongo.sh` y `restore-mongo.sh` fallaban contra el Mongo de producción (sin credenciales); corregidos y comprobados con un Mongo desechable con autenticación. **Sigue sin programarse** el respaldo ni se ha hecho el simulacro completo.
- **Alcance** (`ADR-057`, `ADR-058`): proyecto académico que corre en local, sin CDN, hosting ni servicios gestionados; reportes con retención de 12 meses (índice TTL) y eventos permanentes; CORS abierto solo en `dev` para los servidores locales del frontend (5173, 3000, 4200).
- **Documentación:** [`docs/api/`](../api/README.md) completa, con la referencia de rutas **generada** desde el
  contrato y los `@PreAuthorize`.

## 5. Qué falta — lista honesta

### Para cumplir la meta de 50 000 usuarios (no está demostrada)
> Es referencia para un despliegue futuro, no pendiente del proyecto: es académico y corre en local (`ADR-057`).

- **No se ha probado a esa escala, y en un PC no se puede.** El techo medido es 30 000 reportes con 30 000 conexiones en vivo a la vez (`ADR-083`); con la base ya cargada el p95 llegó a 788 ms y esa variabilidad no se aisló (ver [`escalabilidad.md`](escalabilidad.md)).
- **Fotos en disco local:** con réplicas en el mismo host funcionan por el volumen compartido; en hosts
  distintos hacen falta almacenamiento de objetos (S3/MinIO). El puerto `AlmacenamientoPort` ya existe; falta el adaptador.
- **`EstadoColectorRegistry` vive en memoria de cada instancia:** con réplicas, `/api/veedor/ingesta/salud`
  refleja solo lo que ejecutó esa réplica.
- **Mongo y Redis son nodos únicos** (sin replica set ni Sentinel). **No hay CDN ni TLS activo** (RNF026).
- **La observabilidad es mínima:** solo `health`; faltan métricas Prometheus (emisores SSE, pools, latencias).

### Brechas de contrato y funcionales conocidas
- Sin ruta para **listar reportes** públicamente (a propósito, por privacidad).
- Los cortes nacidos de la ingesta **sí se pueden cerrar** con `PATCH /api/veedor/cortes/{id}/cierre`: el servicio busca el corte por id sin mirar su origen (`GestionarCorteOficialServiceTest.debeCerrarTambienUnCorteNacidoDeLaIngesta`). Lo que sigue pendiente es que la interfaz del panel (F5) lo ofrezca.
- Aprobar una propuesta de prensa **sin ventana declarada** responde 200 pero no cambia el sector. Resolver dos veces una propuesta ya responde `409` (#96).
- Las confirmaciones y el consenso **no deduplican por IP**, solo por huella.

### Calidad
- **Pruebas de adaptador (cerrado el 2026-09-29):** `UsuarioMongoAdapter`, `EventoAuditoriaMongoAdapter`, `TokenCuentaMongoAdapter`,
  `RedisControlIntentosAdapter` y `RedisRevocacionSesionAdapter` (con Testcontainers), más las de `SembradorAdminInicial`,
  `BCryptCifradorClaveAdapter`, `GeneradorSecretosSeguroAdapter`, `TelegramSondeoJob`, `TelegramDesactivadoAdapter`,
  `LectorDeVentanaDeclarada`, `NormalizadorDeNombres`, `AliasDeBarrios` y los tres servicios de consulta pública.
- **Capas (cerrado el 2026-09-29):** ningún controlador importa ya `infrastructure/`. `SesionAutenticada` pasó a `domain/`;
  lo que faltaba lo declaran los puertos `DocumentosFallidosPort`, `SaludDeColectoresPort` y `CanalEnVivoPort<C>`. Dos reglas
  ArchUnit lo vigilan: `apiNoDebeDependerDeInfrastructure` y `todaRutaDelPanelDebeExigirUnPermiso` (`RNF022`; excepciones
  justificadas: `/api/veedor/sesion`, `/sesion/cierre` y `/yo`).
- **Dependabot** (`ADR-059`): Spring Boot 4 y springdoc 3 quedan ignorados a propósito. Testcontainers sigue en 1.21.x: la migración a 2.0 (#2) se cerró sin fusionar y es una tarea pendiente, no un descuido.
- **Google News RSS tiene `Disallow: /` en su `robots.txt`.** La auditoría de fuentes lo justifica como agregador legítimo (`auditoria-fuentes-de-datos.md`); falta que el dueño lo confirme (`RF036`).
- **Un código de salida 127 esporádico de `demo.mjs`** al devolver el backend a su perfil (1 de ≥5 corridas) no se reprodujo ni se explicó.

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
docker compose up                       # todo el stack, sembrado y sin .env (API en :8081, ADR-086)
docker compose up --build               # tras traer cambios: up a secas reutiliza las imágenes viejas
```

Clave del primer ADMIN, puertas del sembrador y utilidades (`agregar-usuarios`, `monitor`, `totp`): [`entorno-local.md`](entorno-local.md).

**Regenerar el contrato OpenAPI** (sin levantar la app; `ContratoOpenApiTest` falla si se olvida):

```bash
cd backend && ./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true
```

**Regenerar la referencia de rutas** de `docs/api/` (después de lo anterior):

```bash
node scripts/generar-referencia-api.mjs
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
| `scripts/carga/` | Pruebas de carga (k6 y cliente SSE) |
| `scripts/sembrador.mjs` | Servicio `sembrador` del compose: siembra inicial y utilidades (`ADR-086`–`ADR-088`) |
| `data/geoespacial/` | GeoJSON canónico (213 filas; se siembran 211 sectores) y población |

## 9. Si retomas el trabajo, empieza por aquí

1. Lee [`CLAUDE.md`](../../CLAUDE.md) y [`docs/api/README.md`](../api/README.md).
2. Levanta todo con `docker compose up` (sección 7), corre `./mvnw -B verify` y confirma el estado de la sección 2 antes de tocar nada.
3. **Antes de cambiar el contrato de la API, pregunta:** quien construya el frontend lo estará usando.
4. Un cambio de comportamiento se documenta en `comportamiento-del-sistema.md` **en el mismo cambio**, y se
   registra con las skills del proyecto (`registrar-decision`, `registrar-bug`, `registrar-implementacion`).

La secuencia de consolidación previa a la integración está en [plan-validacion-backend.md](plan-validacion-backend.md).
