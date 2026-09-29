# Anexo 5 — Manual Técnico y Casos de Prueba

Manual técnico de AguaVigía CTG para quien corre, prueba y mantiene la plataforma. **El proyecto es académico y corre solo
en local** (`ADR-057`, `ADR-080`): no hay producción, servidor, dominio, nginx, TLS ni CDN. Todo lo de este anexo se
levanta con un `docker compose` en la máquina de quien lo usa. Revisado el 2026-09-29 contra el repositorio; cada comando y
cada nombre de archivo o variable se comprobó que existe, pero **no se ejecutó nada al revisarlo**.

Repositorio: `https://github.com/SebastianMontes-Dev/Agua-Vigia-CTG-privado` (privado).

Lo retirado al pasar a «solo local» (`docker-compose.prod.yml`, `infra/nginx/`, el perfil `prod`) sigue en la etiqueta git
`pre-solo-local` (`ADR-080`); no forma parte de este manual.

## 1. Arquitectura de ejecución local

El backend, las bases y el correo de pruebas corren en contenedores con `docker-compose.yml`. El frontend no está en el
compose: se ejecuta con el servidor de desarrollo de Vite.

| Servicio | Qué es | Puerto en el host |
|---|---|---|
| `backend` (`aguavigia-backend`) | Spring Boot 3.5 · Java 21 · API REST (contenedor en `8080`) | **8081** |
| `mongo` (`aguavigia-mongo`) | MongoDB 7.0 (documentos y geoespacial `2dsphere`), como *replica set* `rs0` de un solo nodo porque las transacciones lo exigen (`ADR-063`) | 27017 |
| `mongo-init-replica` | Contenedor de un solo uso que inicia el *replica set* si aún no lo está | — |
| `redis` (`aguavigia-redis`) | Redis 7: caché, límites de peticiones, ventana del consenso, canal SSE | 6379 |
| `mailhog` (`aguavigia-mailhog`) | SMTP de pruebas: los correos de la plataforma llegan aquí y nunca salen de la máquina | 1025 (SMTP) · **8025** (interfaz web) |
| Frontend (`frontend/`) | React 19 · Vite · TypeScript estricto (`ADR-067`), con `npm run dev` | **5173** (desarrollo) · 4173 (vista previa que usan las pruebas E2E) |

Volúmenes: `mongo-data`, `redis-data` y `fotos-data` (las fotos de evidencia, `RNF021`). En desarrollo, el servidor de Vite
reenvía `/api` y `/fotos` al backend (`http://localhost:8081`, o el que diga `AGUAVIGIA_BACKEND`), de modo que la interfaz y
la API comparten origen sin nginx.

### 1.1 Los archivos de Docker Compose

- `docker-compose.yml` — **el único entorno**. Publica en el host los puertos de la tabla anterior para poder inspeccionarlos.
- `docker-compose.carga.yml` — capa opcional para la demo de carga (`ADR-083`): solo activa el perfil Spring `carga` en el
  backend (sin límite de peticiones por IP, más conexiones en vivo). **No se usa a mano ni se deja activo**: lo combina
  `scripts/carga/demo.mjs` con el compose base y, al terminar, devuelve el backend a su perfil normal.

## 2. Requisitos previos

- Docker Desktop (o Docker Engine) con Compose V2, **encendido**: las pruebas de integración lo necesitan (Testcontainers).
- JDK 21, solo si se ejecuta Maven fuera de Docker (`backend/mvnw`).
- Node 22 o superior, para el frontend (`engines` de `frontend/package.json`) y para los scripts de `scripts/`.
- Git LFS: el extracto del mapa base (PMTiles) se versiona con Git LFS (`ADR-072`).
- Sin internet la plataforma funciona con `INGESTA_MODO=local` (§3.4). El mapa base es local y no usa terceros.
- Recursos: no hay una medida para el uso normal. La demo de carga con 30 000 conexiones llevó al backend a ≈ 8 núcleos y
  ≈ 6,4 GiB en un PC de 12 hilos que además corría el generador de carga (`ADR-083`).

## 3. Puesta en marcha local

1. **Clonar el repositorio** (con Git LFS instalado):
   ```bash
   git clone https://github.com/SebastianMontes-Dev/Agua-Vigia-CTG-privado.git
   cd Agua-Vigia-CTG-privado
   ```

2. **Variables de entorno.** Copiar el ejemplo y completar lo que queda vacío (`.env` nunca se versiona):
   ```bash
   cp .env.example .env
   ```

   Las variables de `.env.example` (los valores por defecto ya apuntan a los servicios del compose):

   | Variable | Para qué | Si está vacía |
   |---|---|---|
   | `JWT_SECRET` | Firma del token del panel (`RNF011`). Mínimo 32 bytes: `openssl rand -base64 32` | El inicio de sesión del panel responde 503 |
   | `VEEDOR_PASSWORD_HASH` | Hash BCrypt de la clave del **primer administrador**, nunca la clave en texto plano. Cada `$` va escrito `$$` en el `.env` | No se siembra ningún administrador |
   | `ADMIN_INICIAL_CORREO` | Correo de ese primer administrador | No se siembra ningún administrador |
   | `IOT_KEY` | Clave que deben mandar los sensores IoT (`X-IoT-Key`, `M13`) | `POST /api/iot/presion` responde 503; el resto sigue igual |
   | `TELEGRAM_BOT_TOKEN` | Token del bot de Telegram (`RF041`), que entrega `@BotFather` | El canal de Telegram queda apagado; el resto sigue igual |
   | `COLLECTOR_USER_AGENT` | Identificación del colector de ingesta (ética de datos, `CLAUDE.md`) | El colector se niega a llamar |
   | `INGESTA_MODO` · `INGESTA_INTERVALO_MS` | `en-vivo` (por defecto) o `local` (§3.4) · cada cuántos ms corre la ingesta (600 000 = 10 min) | — |
   | `APP_URL_PUBLICA` · `APP_URL_FRONTEND` | Bases de los enlaces de los correos: la API (`http://localhost:8081`) y la SPA (`http://localhost:5173`) | Enlaces rotos en los correos |
   | `CORS_ORIGENES` | Orígenes que pueden llamar a la API desde su propio servidor de desarrollo | — |
   | `SPRING_PROFILES_ACTIVE` · `SERVER_PORT` · `MONGODB_URI` · `MONGO_INITDB_DATABASE` · `REDIS_HOST` · `REDIS_PORT` · `MAIL_HOST` · `MAIL_PORT` | Topología del compose | No tocar salvo que cambie |
   | `GITHUB_PERSONAL_ACCESS_TOKEN` | Solo para el servidor MCP de GitHub de las herramientas de desarrollo (opcional) | — |

   Cómo generar el hash, el secreto y entrar al panel con el segundo factor (TOTP, `RNF025`):
   [`docs/ingenieria/entorno-local.md`](../ingenieria/entorno-local.md). Las credenciales de desarrollo viven allí y en
   [`credenciales-y-accesos.md`](../ingenieria/credenciales-y-accesos.md), no en este anexo.

3. **Levantar el stack y sembrar los sectores:**
   ```bash
   docker compose up -d --build --wait
   cd scripts && npm install && node sembrar-sectores.mjs   # 211 sectores; idempotente
   ```
   Tras traer cambios de `main`, reconstruir con `docker compose up -d --build backend`: con la imagen vieja fallaron CORS,
   la foto y el cierre de sesión (`MEMORY.md`).

   Datos opcionales de demostración (desde `scripts/`):

   | Script | Qué deja |
   |---|---|
   | `sembrar-historico-cortes.mjs` | Cortes y reportes históricos sintéticos de mayo–julio 2026 (para el Índice de Cumplimiento). **Borra** los de ese rango que hubiera |
   | `sembrar-demo.mjs` | Barrios afectados por el camino real: envía reportes a la API hasta que el consenso cambia el estado |
   | `sembrar-usuarios-demo.mjs` | 30 000 cuentas con barrio, tokens, auditoría y suscripciones (`ADR-081`) |

4. **Ingesta sin internet.** Con `INGESTA_MODO=local` en `.env`, el backend lee boletines reales de Acuacar guardados en el
   repositorio en vez de consultar a Acuacar y a la prensa (`ADR-082`). Cambiar de modo exige recrear el backend:
   `docker compose up -d backend`.

5. **Verificación de salud:**
   ```bash
   curl -s localhost:8081/actuator/health/readiness      # {"status":"UP"}
   ```
   `readiness` y `liveness` no dependen de fuentes externas ni del correo. El estado de cada colector de ingesta (última
   ejecución exitosa, ítems procesados, tasa de error, `RNF007`) sale en `/actuator/health` y, con detalle y token del panel,
   en `GET /api/veedor/ingesta/salud`.

6. **Frontend en desarrollo:**
   ```bash
   cd frontend && npm ci && npm run dev                  # http://localhost:5173
   ```

7. **Dónde mirar:** interfaz `http://localhost:5173` · API `http://localhost:8081` · Swagger
   `http://localhost:8081/swagger-ui.html` · correos de prueba `http://localhost:8025`.

8. **Detener:** `docker compose down` conserva los datos (los volúmenes). `docker compose down -v` **los borra**.

**Utilidades** (de `scripts/`): `codigo-totp.mjs` (código TOTP de una cuenta de desarrollo), `restablecer-admin.mjs`
(recupera a un ADMIN sin segundo factor, solo contra una base local), `limpiar-puertos.ps1` / `.sh` (libera procesos que
dejaron un puerto ocupado).

**Estado de la interfaz:** F2 (núcleo ciudadano), F3 (historia pública) y F4 (avisos) están construidas; **F5 (cuentas y panel
del veedor) y F6 (integración) siguen pendientes** (`docs/gestion/sprint-7.md`). El panel se opera hoy por la API con Swagger.

## 4. Pruebas y aseguramiento de calidad (QA)

La estrategia (qué se prueba, con qué y cuándo) está en
[`docs/ingenieria/plan-de-pruebas.md`](../ingenieria/plan-de-pruebas.md); el estado de cada requisito y la prueba que lo
sostiene, en [`matriz-trazabilidad.md`](../ingenieria/matriz-trazabilidad.md).

### 4.1 Backend

JUnit 5, Mockito y Testcontainers (`mongo:7.0` y `redis:7-alpine`) validan el dominio, la inmutabilidad de la bitácora y el
contrato de la API. **Requiere Docker encendido.**
```bash
cd backend
./mvnw -B verify
```

Cifras vigentes (`docs/ingenieria/estado-del-backend.md` §2):

| Qué | Valor |
|---|---|
| Pruebas | **1 104** |
| Cobertura JaCoCo | `domain/` **91,1 %** · `application/` **97,7 %** · total **94,1 %** |
| Umbral que exige la build | 85 % en `domain/` y `application/` (`RNF017`); por debajo, `verify` falla |
| Arquitectura | **10 reglas ArchUnit** (`RNF018`); una violación de capas rompe la build |

El contrato `backend/openapi.yaml` es un archivo generado; `ContratoOpenApiTest` falla si se desactualiza. Para regenerarlo:
`./mvnw test -Dtest=ContratoOpenApiTest -Dopenapi.regenerar=true`.

### 4.2 Frontend

Vitest para la lógica y los componentes; Playwright para los flujos completos (proyectos móvil de 360 px y escritorio de
1 280 px). Node 22+.
```bash
cd frontend
npm ci
npm run lint        # oxlint
npm run typecheck   # tsc -b
npm run api:check   # el cliente tipado sigue al contrato de backend/openapi.yaml
npm test            # Vitest
npx playwright install chromium     # la primera vez
npm run test:e2e    # Playwright con la API simulada; construye y sirve la vista previa en :4173
```

Las **E2E reales** (`npm run test:e2e:real`) corren contra el backend de verdad. Necesitan el stack levantado y sembrado,
como hace el job `integracion` de `.github/workflows/frontend-ci.yml`: `docker compose up -d --build --wait`, luego
`sembrar-sectores.mjs`, `sembrar-demo.mjs`, `sembrar-historico-cortes.mjs` y `preparar-pruebas-frontend.mjs`, todos de
`scripts/`. Ese job reserva la ingesta externa mientras corre para que no cambie los datos.

### 4.3 Verificación de flujos HTTP

`node scripts/verificar-flujos.mjs` recorre con HTTP real los flujos que consume la interfaz (mapa, suscripción con Mailhog,
reporte y consenso, foto, aviso en vivo, bitácora, estadísticas y panel). Está pensado para **una corrida sobre una base
recién creada**. Resultado de referencia (2026-09-24, 21 pasos, 0 fallos): `plan-de-pruebas.md` §8.

### 4.4 Carga

`node scripts/carga/demo.mjs --usuarios 30000 --ventana 60 --conectados 30000 --restaurar` reproduce la ciudad reportando a
la vez (`ADR-083`, opciones en `scripts/carga/README.md`): hace un respaldo de Mongo, activa el perfil `carga`, lanza k6 y el
cliente SSE dentro de la red de Docker y devuelve el sistema a su estado con `--restaurar`. Criterio local de `RNF027`:
30 000 reportes en 60 s con 30 000 conexiones en vivo, p95 de 130 a 164 ms en tres corridas. **Los 50 000 usuarios de
`RNF027` no se demuestran en un solo PC.** Cifras y límites: `docs/ingenieria/escalabilidad.md`.

### 4.5 Integración continua

En `.github/workflows/`: `backend-ci.yml` (`./mvnw verify`, con ArchUnit y JaCoCo), `frontend-ci.yml` (lint, tipos, contrato,
Vitest, E2E simuladas y E2E reales), `contenedores-ci.yml` (valida el compose local, construye la imagen y pasa Trivy),
`escaneo-de-fugas.yml` (gitleaks, `RNF010`) y `autoria.yml`.

## 5. Respaldo y restauración

Se hace **a mano** antes de una presentación, de una prueba de carga o de tocar la base; no hay tarea programada. Son dos
respaldos que van juntos: Mongo (`mongo-data`) y las fotos (`fotos-data`). Detalle, simulacro y límites:
[`docs/ingenieria/respaldo-y-restauracion.md`](../ingenieria/respaldo-y-restauracion.md).
```bash
./scripts/backup-mongo.sh ./respaldos-mongo
./scripts/backup-fotos.sh ./respaldos-fotos
```
Para restaurar (destructivo: `mongorestore --drop` y vaciado de `/app/data/fotos`; ambos piden escribir `restaurar`):
```bash
./scripts/restore-mongo.sh ./respaldos-mongo/<archivo>.archive.gz
./scripts/restore-fotos.sh ./respaldos-fotos/<archivo>.tar.gz
```
Los directorios de respaldos están en `.gitignore`: tienen datos de cuentas y coordenadas, nunca se suben a git. Redis no se
respalda a propósito. Simulacro de restauración corrido el 2026-09-22; quedó sin probar restaurar una foto real.

## 6. Casos de prueba

Un caso de prueba (CP) por requisito funcional, con el mismo número: `RF0NN → HU0NN → CP0NN` (ver
[`matriz-trazabilidad.md`](../ingenieria/matriz-trazabilidad.md) y el [Anexo 4](./anexo-4-historias-de-usuario.md)). Están
agrupados por módulo M1–M15.

**Cómo leer el estado.** Este anexo **no trae resultados de ejecución nuevos**: el estado sale de la matriz de
trazabilidad y del código. La columna *Estado* refleja el backend y la API; la columna *Interfaz nueva* dice si el caso
tiene además una pantalla en `frontend/` y qué prueba la cubre.

| Símbolo | Significado |
|---|---|
| ✅ | El RF está ✅ en la matriz. Si la matriz cita una prueba automatizada, se cita aquí; si no, el caso ya figuraba ✅ en la versión anterior de este anexo y se indica «matriz» |
| 🟡 | Parcial: se dice qué parte falta |
| ⏳ | **Por ejecutar**: no hay evidencia citada que permita darlo por verificado. Se anota la prueba existente, si la hay |
| ❌ | El requisito está descartado o no se cumple tal como está escrito |
| ⛔ | Solo en la columna *Interfaz nueva*: la pantalla todavía no está construida (F5 del plan del frontend) |

Ningún caso de la columna *Interfaz nueva* está verificado en este anexo: la interfaz se rehizo (`ADR-048`, `ADR-067`) y esas
pruebas (`frontend/e2e/`, `frontend/src/**/*.test.ts*`) están sin ejecutar aquí. **Sus nombres se citan porque existen en el
repositorio, no porque pasen.**

### M1 — Mapa en vivo
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP001 | Estado de todos los sectores | `GET /api/sectores` devuelve los 211 sectores, cada uno con su estado (con servicio, sin servicio, presión baja, corte programado). Un sector sin dato verificado trae `estado: null`, nunca `CON_SERVICIO`. | ✅ matriz | ⏳ F2: `e2e/mapa.spec.ts` (`debePresentarElEstadoNuloComoSinDatosYNuncaComoConServicio`) |
| CP002 | Detalle de un sector | `GET /api/sectores/{id}` devuelve estado y marca del último cambio; `GET /api/sectores/{id}/cortes` devuelve el histórico paginado, el más reciente primero. Un id inexistente responde 404 en RFC 7807. | ✅ matriz | ⏳ F2: ficha del barrio, `e2e/mapa.spec.ts` (`debeResponderElEstadoYElFinPrometidoDeUnBarrioBuscado`) |
| CP003 | Antigüedad del dato | Cada sector trae la fecha de su último cambio de estado (`actualizadoEn`) y la de su última verificación (`verificadoEn`). | ✅ `SectorMongoAdapterTest.debeDevolverLaFechaDelEstadoAlLeerElSector` | ⏳ F2: «Sin verificación reciente» a las 24 h, `e2e/mapa.spec.ts` (`debeAdvertirSinVerificacionRecienteSinCambiarElEstado`) |
| CP004 | Lista textual accesible | La interfaz ofrece una lista de todos los barrios con su estado como alternativa al mapa. Sin componente de backend propio. | ⏳ Por ejecutar: el ✅ anterior era del frontend retirado (`ADR-048`) | ⏳ F2: `e2e/mapa.spec.ts` (`debeOfrecerLaListaDeBarriosComoAlternativaAlMapa`) |

### M2 — Reporte ciudadano
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP005 | Reportar sin cuenta | `POST /api/reportes` con tipo, sector y huella de dispositivo, sin token, responde 201 con el identificador del reporte. No se guarda ningún dato personal más allá de la huella anónima (`RNF008`). | ✅ matriz | ⏳ F2: `e2e/real/ciudadano.spec.ts` (`debeReportarEnDosToquesYRecibirUn201`) |
| CP006 | Límite por dispositivo | Al superar el cupo por huella y sector en la ventana (3 en 30 min por defecto), la API responde 429 en RFC 7807 con el límite y cuándo se libera, y el reporte no se guarda. | ✅ matriz | ⏳ F2: `e2e/mapa.spec.ts` (`debeExplicarElCupoAgotadoSinReintentarSolo`) |
| CP007 | Sector inferido por coordenada | Un reporte con `coordenada` y sin `sectorId` queda asociado al sector que la contiene y la respuesta lo devuelve. Una coordenada fuera de Cartagena, o un reporte sin sector ni coordenada, responde 400. | ✅ matriz | ⏳ F2: «Usar mi ubicación», `e2e/ubicacion-y-confirmacion.spec.ts` |
| CP008 | Reporte en dos toques | Desde el mapa, el vecino toca «Reportar que no tengo agua» y confirma el tipo: el reporte queda enviado sin pasos intermedios. Sin componente de backend propio. | ⏳ Por ejecutar: el ✅ anterior era del frontend retirado (`ADR-048`) | ⏳ F2: `e2e/mapa.spec.ts` (`debeReportarEnDosToquesConLaHuellaDelDispositivo`) |

### M3 — Consenso automático
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP009 | Cambio por umbral | Al alcanzar el umbral de reportes independientes del mismo tipo en la ventana, el sector cambia de estado y la bitácora suma un evento. Con menos reportes no cambia. (Procedimiento manual: `guion-de-demo.md` §2.) | ✅ matriz | ⏳ F2: el mapa se repinta por el canal en vivo, `e2e/real/ciudadano.spec.ts` (`debeActualizarElEstadoPorConsensoSSESinRecargarLaPagina`) |
| CP010 | Estrategias de consenso | Por configuración se elige entre umbral fijo y umbral proporcional a la población; el proporcional exige más reportes en un sector populoso y recurre al fijo si el sector no tiene población. | ✅ matriz | — |
| CP011 | Reportes que sustentaron el cambio | El evento de bitácora de un cambio por consenso trae cuántos reportes lo sustentan, y `GET /api/bitacora/{id}/sustento` devuelve sus identificadores, paginados. | ✅ matriz | ⏳ F3: `e2e/bitacora.spec.ts` (`debeConsultarElSustentoSoloAlAbrirloYNoNombrarLoQueNoExiste`) |

### M4 — Alertas por correo
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP012 | Suscribirse solo con el correo | `POST /api/suscripciones` con un correo y uno o más sectores deja la suscripción pendiente de confirmación, manda un correo de confirmación (visible en Mailhog, `:8025`) y no envía ninguna alerta. | ✅ matriz | ⏳ F4: `e2e/avisos.spec.ts` (`debeElegirVariosBarriosYMostrarSiempreLaRespuestaNeutra`) |
| CP013 | Doble opt-in | Abrir el enlace del correo muestra una página con un botón y **no** cambia la suscripción (`GET` con `Accept: application/json` responde 406); `POST /api/suscripciones/confirmar` la pasa a confirmada (`ADR-054`). | ✅ matriz | ⏳ F4: `e2e/avisos.spec.ts` (`debePedirUnBotonAntesDeConfirmarOBajarYRetirarElTokenDeLaURL`) |
| CP014 | Aviso al cambiar el estado | Cuando un sector con suscriptores confirmados pasa a `SIN_SERVICIO`, cada uno recibe un correo. Si el envío falla, el cambio se publica igual. | ✅ matriz | ⏳ F4: `e2e/real/avisos.spec.ts` (`debeSuscribirseConfirmarYDarseDeBajaSoloDespuesDePulsarCadaBoton`) |
| CP015 | Baja sin credenciales | Todo correo lleva un enlace de baja; abrirlo muestra un botón y `POST /api/suscripciones/cancelar` cancela sin pedir clave, y el correo deja de estar almacenado (`RNF009`). | ✅ `MailNotificacionAdapterTest.debeIncluirElEnlaceDeBajaEnElAviso` | ⏳ F4: `e2e/real/avisos.spec.ts` (misma prueba que CP014) |

### M5 — Panel del veedor
El panel se ejerce hoy por la API con el token de `POST /api/veedor/sesion`. **La interfaz del panel (F5) no está construida.**

| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP016 | Registrar corte oficial | `POST /api/veedor/cortes` con token, sectores, inicio, fin prometido y causa registra el corte y anota «corte anunciado» en la bitácora. Un fin anterior al inicio responde 400 en RFC 7807. | ✅ matriz | ⛔ F5 pendiente |
| CP017 | Cerrar corte oficial | `PATCH /api/veedor/cortes/{id}/cierre` con la hora real cierra el corte y deja disponible su desviación. Un id inexistente responde 404. | ✅ matriz | ⛔ F5 pendiente |
| CP018 | Moderar reportes | `GET /api/veedor/reportes/pendientes` lista los reportes sin moderar; `PATCH …/{id}/aprobar` y `PATCH …/{id}/descartar` los resuelven. | ✅ matriz | ⛔ F5 pendiente |
| CP019 | Proteger el panel | Toda ruta bajo `/api/veedor/` (salvo `POST /api/veedor/sesion`) responde 401 sin token o con un token de más de 8 h; más de 5 inicios de sesión fallidos en 5 min desde una IP responden 429. El resto de la API es pública. | ✅ matriz | ⛔ F5 pendiente |

### M6 — Índice de Cumplimiento
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP020 | Desviación prometido vs real | `GET /api/cumplimiento/cortes/{corteId}` de un corte cerrado con fin prometido expone la duración prometida, la real y su desviación. Un corte cerrado sin fin prometido no aporta al índice. | ✅ matriz | — |
| CP021 | Índice por sector y global | `GET /api/cumplimiento` y `GET /api/cumplimiento/sectores/{sectorId}` devuelven el índice, calculado sumando duraciones (no promediando porcentajes, `ADR-022`). Sin cortes medidos, informan que no hay dato y nunca un 100 %. | ✅ matriz | ⏳ F3: `e2e/real/cumplimiento.spec.ts` |
| CP022 | Comparación prometido vs real | La interfaz muestra lo prometido y lo real juntos, en lenguaje natural («Prometieron 2 horas · Fueron 8»), no un puntaje aislado. La API ya expone ambas duraciones (CP020). | ⏳ Por ejecutar: el ✅ anterior era del frontend retirado (`ADR-048`) | ⏳ F3: `e2e/cumplimiento.spec.ts`, `src/pantallas/publico/Cumplimiento.test.tsx` |

### M7 — Estadísticas
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP023 | Sectores más afectados, duración y frecuencia | `GET /api/estadisticas` devuelve el ranking de sectores, la duración promedio y la frecuencia mensual de cortes; sin cortes, cada métrica se presenta como «sin dato». | ✅ `EstadisticasMongoAdapterTest` | ⏳ F3: `e2e/estadisticas.spec.ts` |
| CP024 | Evolución del índice | `GET /api/cumplimiento/serie` devuelve el índice mes a mes. | ✅ `SerieMensualCumplimientoTest` | ⏳ F3: `e2e/cumplimiento.spec.ts` (`debeMostrarElMesAMesSinRellenarMesesAusentes`) |
| CP025 | Exportar CSV | `GET /api/estadisticas/exportar.csv` y `GET /api/cumplimiento/serie.csv` descargan un CSV con las mismas cifras que la pantalla. | ✅ `EscritorCsvTest` | ⏳ F3: `e2e/estadisticas.spec.ts` (`debeGuardarTablasYCsvDetrasDeVerDatos`), `e2e/real/estadisticas.spec.ts` |

### M8 — Bitácora pública
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP026 | Registro de eventos | Un corte anunciado por el veedor o un cambio de estado por consenso suma un evento a la bitácora. | ✅ matriz | — |
| CP027 | Acceso público | `GET /api/bitacora` sin token devuelve los eventos paginados y admite `sectorId`, `tipo`, `desde` y `hasta` sobre todo el historial; un tipo desconocido o un rango invertido responde 400. | ✅ matriz | ⏳ F3: `e2e/bitacora.spec.ts` |
| CP028 | Inmutabilidad | No existe operación de edición ni de borrado sobre un evento de bitácora, ni en la API (`backend/openapi.yaml`) ni en el puerto de salida del dominio. | ✅ matriz | — |

### M9 — Ingesta automática (heurística determinista, sin IA)
El SDK de IA se descartó (`ADR-025`): la ingesta usa `PrefiltroDeterminista` y `HeuristicaExtractor`, y lo que deduce de la
prensa entra como propuesta a una cola de revisión del veedor (`ADR-028`). La matriz marca **RF032–RF036 como ❌
Descartado**. Para cada uno se dice qué parte se cumple de forma heurística y qué parte no. Las pruebas que se citan
**existen en el repositorio, pero la matriz no las cita para estos RF**, por eso la parte heurística queda ⏳.

| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP029 | Consumo de la API de Acuacar | El colector lee los boletines de la API pública de WordPress de Acuacar, avanza su marca de lectura y no retrocede si no hay novedades. En modo `local` (`ADR-082`) lee boletines guardados en el repositorio, sin tocar la red. | ✅ `AcuacarApiCollectorTest` (modo local: `IngestaLocalDeExtremoAExtremoTest` existe; la matriz no la cita) | — |
| CP030 | Prensa por RSS | El colector procesa los feeds de prensa configurados (Google News, Zona Cero, Caracol Radio y W Radio) con un `User-Agent` que nombra al proyecto y da un correo. | ✅ `RssCollectorTest` | — |
| CP031 | Descarte de duplicados | Un aviso cuyo contenido normalizado ya se vio, por su hash, no crea documento ni propuesta nuevos. | ✅ `DeduplicadorRecienteTest`, `PipelineOrquestadorTest` | — |
| CP032 | Clasificación y extracción | El prefiltro descarta lo que no contiene sus palabras clave y el extractor decide si el texto habla de una interrupción (nombra un barrio y menciona suspensión, presión baja o restablecimiento), y extrae barrios, ventana prometida, causa y tipo; lo que no logra leer lo declara como faltante. **No es IA** (`RF032` la pedía). | ❌ RF descartado como IA (matriz, `ADR-025`) · parte heurística ⏳ por ejecutar: `PrefiltroDeterministaTest`, `HeuristicaExtractorTest` (`debeLeerLosBarriosDeLaEnumeracionYNoLaFraseDeResumen`, `debeLeerLaVentanaPrometidaEnHoraDeCartagena`, `debeDeclararLosCamposQueNoSupoLeerEnVezDeInventarlos`) | — |
| CP033 | Confianza y cita textual | Toda extracción trae una confianza graduada por la evidencia (0,85 con enumeración y horario, 0,75 con enumeración sin horario, 0,45 con mención suelta) y la cita del fragmento del boletín que la sustenta; ambas se guardan en la propuesta. La confianza es una regla, no la probabilidad de un modelo. | ❌ RF descartado como IA · parte heurística ⏳ por ejecutar: `HeuristicaExtractorTest` (`debeGraduarLaConfianzaSegunLaEvidenciaEncontrada`, `debeBajarLaConfianzaCuandoLaEnumeracionNoTraeHorario`, `laCitaTextualDebeMostrarLaListaDeBarriosYNoLaFraseDeResumen`) | — |
| CP034 | Cita literal | La cita es un fragmento literal del boletín (salvo los «…» del recorte). **No existe** un verificador que rechace en ejecución una cita que no esté en el documento: se garantiza por construcción, porque el extractor la recorta del propio texto. | ❌ Rechazo automático no implementado · literalidad ⏳ por ejecutar: `HeuristicaExtractorTest.laCitaTextualDebeSerLiteralDelBoletin` | — |
| CP035 | Revisión humana | Lo que la ingesta deduce de la prensa queda como propuesta pendiente en `GET /api/veedor/ingesta/propuestas` y el mapa no cambia; aprobarla lo publica y anota la bitácora, descartarla la cierra, y resolverla al revés responde 409. **No hay banda de «confianza intermedia»**: toda propuesta de prensa va a la cola. El boletín oficial de Acuacar se publica sin revisión (`ADR-034`). | ❌ RF descartado tal como está escrito (matriz) · cola de revisión ⏳ por ejecutar: `IngestaRevisionControllerTest`, `RevisarPropuestaIngestaServiceTest` (la matriz da por cerrado este hueco el 2026-08-11, sin citar prueba) | ⛔ F5 pendiente |
| CP036 | Fuentes que bloquean a la IA | Una fuente cuyo `robots.txt` bloquea a los agentes de IA no se incorpora a los colectores (su cobertura llega vía Google News). La regla se cumple por **curación de las fuentes**, con una petición real (`verificar-fuente`, `auditoria-fuentes-de-datos.md`); el backend **no** consulta `robots.txt` al ejecutar. | ❌ RF descartado (matriz); la regla ética sigue vigente (`ADR-005`) · verificación de la auditoría de fuentes ⏳ por ejecutar | — |

### M10 — Evidencia Multimedia (Fase 2)
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP037 | Adjuntar fotografía | `POST /api/reportes/{id}/foto` con un `.jpg` o `.png` guarda la imagen comprimida y sin metadatos EXIF en el volumen `fotos-data`, asociada al reporte; una imagen demasiado grande responde 413 en RFC 7807. | ✅ matriz (`CompresorDeImagenesTest` para la compresión y el EXIF, `RNF021`) | ⏳ F2: `src/pantallas/publico/Reporte.tsx` maneja la foto; sin E2E propio |

### M11 — Validación Comunitaria Rápida (Fase 2)
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP038 | Confirmar con un clic | `POST /api/reportes/{id}/confirmar` suma una confirmación al conteo `confirmaciones` del reporte; el mismo dispositivo, o el que lo envió, no suma dos veces (200 sin cambios). No reevalúa el consenso (`BUG-114`). | ✅ matriz | ⏳ F2: `e2e/ubicacion-y-confirmacion.spec.ts` (`debeConfirmarUnReporteSoloAlTocarElBoton`) |

### M12 — API Abierta Open311 (Fase 2)
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP039 | Exposición Open311 | `GET /api/v2/requests.json` devuelve, en el formato del estándar, un `service_request` por **sector** cuyo estado no es `CON_SERVICIO`, sin `lat`/`long` ni huella de dispositivo (`ADR-026`, `RNF008`). | ✅ `Open311ControllerTest` | — |

### M13 — Integración IoT Pasiva (Fase 2)
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP040 | Alerta de presión IoT | `POST /api/iot/presion` con `X-IoT-Key` válida y `presionPsi` por debajo del umbral (15 psi por defecto) registra un reporte de `PRESION_BAJA` que cuenta para el consenso. Sin clave o con una inválida responde 401; sin `IOT_KEY` en el servidor, 503. Cupo propio, separado del ciudadano. Se prueba con peticiones: **no hay sensores instalados**. | ✅ matriz (`IotControllerTest` existe; la matriz no la cita) | — |

### M14 — Alertas Push Instantáneas (Fase 2)
| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP041 | Suscripción y aviso por Telegram | Con `TELEGRAM_BOT_TOKEN`, `/suscribir <sector>` en un chat privado lo suscribe y el bot lo confirma; al cambiar el estado del sector, el chat recibe el aviso; `/baja` borra el identificador del chat. Sin token, el canal queda apagado y el resto funciona igual. WhatsApp queda fuera. | 🟡 Parcial (matriz): construido y armado (`ADR-066`), probado contra un servidor HTTP falso y un Mongo real (`ProcesarMensajeTelegramServiceTest`, `TelegramApiAdapterTest`, `TelegramDesactivadoAdapterTest`, `TelegramSondeoJobTest`, `SuscripcionTelegramMongoAdapterTest` existen; la matriz no cita ninguna). **Falta probarlo contra Telegram real**: no hay bot ni token | ⏳ F4: la vista de avisos menciona Telegram (`src/pantallas/publico/Avisos.tsx`) |

### M15 — Cuentas y permisos del panel (Fase 2)
Los nombres de método de esta sección se comprobaron por lectura del código el 2026-09-29 (existen todos); **no se
ejecutaron**. La interfaz (F5) no está construida: se ejercen por la API.

| ID | Descripción | Resultado esperado | Estado | Interfaz nueva |
|---|---|---|---|---|
| CP042 | Alta por solicitud, verificación y aprobación | `POST /api/cuentas/registro` deja la cuenta en `PENDIENTE_VERIFICACION`; `POST /api/cuentas/verificacion` la pasa a `PENDIENTE_APROBACION`; `PATCH /api/veedor/usuarios/{id}/aprobacion` la deja `ACTIVA` con los permisos del rol asignado. Ninguno de los tres pasos por sí solo concede acceso al panel. El `barrioId` es opcional y uno inexistente responde 400 (`ADR-081`). | ✅ (`UsuarioTest.quienSeRegistraNaceSinPoderEntrarYSinPermisosUtiles`, `UsuarioTest.aprobarDebeActivarLaCuentaConLosPermisosQueSeLeAsignan`, `AltaYRecuperacionDeCuentaTest.registrarseDebeCrearLaCuentaPendienteDeVerificacionYMandarElEnlace`, `AltaYRecuperacionDeCuentaTest.verificarElCorreoDebeDejarLaCuentaEsperandoAprobacion`) · barrio ⏳: hay pruebas en `CuentaPublicaControllerTest` y `UsuarioMongoAdapterTest`, no citadas en la matriz | ⛔ F5 pendiente |
| CP043 | Invitación con rol ya asignado | `POST /api/veedor/usuarios/invitaciones` crea la cuenta en `INVITADA`; al fijar la clave con `POST /api/cuentas/invitacion` la cuenta queda `ACTIVA` con el rol de la invitación, sin pasar por aprobación. | ✅ (`UsuarioTest.unaCuentaInvitadaNaceSinClaveYConSuRolYaDecidido`, `UsuarioTest.aceptarLaInvitacionDebeDejarLaCuentaActivaSinOtraAprobacion`, `GestionDeCuentasDelPanelTest.invitarDebeCrearLaCuentaYMandarElEnlace`, `AltaYRecuperacionDeCuentaTest.aceptarLaInvitacionDebeDejarLaCuentaActiva`) | ⛔ F5 pendiente |
| CP044 | Aprobar, rechazar, suspender, reactivar y ajustar permisos por persona | El administrador mueve una cuenta entre esos estados (`…/aprobacion`, `…/rechazo`, `…/suspension`, `…/reactivacion`) y concede o revoca un permiso suelto (`…/permisos`) desde `AdminUsuariosController`; suspender o cambiar permisos invalida las sesiones vivas (`RNF023`); ningún administrador actúa sobre su propia cuenta ni deja el sistema sin un `ADMIN` activo. | ✅ (`AdministrarCuentaServiceTest.aprobarDebeActivarLaCuentaYAvisarPorCorreo`, `.suspenderDebeRevocarLasSesionesVivasDelAfectado`, `.rechazarDebeRevocarLasSesionesVivasDelAfectado`, `.reactivarNoDebeRevocarSesiones`, `.debeAplicarLosAjustesDePermisosPorPersona`, `.unAdminNoDebePoderSuspenderseASiMismo`, `.noDebePoderSuspenderseAlUnicoAdministradorActivo`, `.noDebePoderDespromoverseAlUnicoAdministradorActivo`) | ⛔ F5 pendiente |
| CP045 | Bitácora de auditoría inmutable | Todo cambio de acceso queda en `auditoria_cuentas` con autor, destinatario, instante e IP; `GET /api/veedor/auditoria` (permiso `VER_AUDITORIA`) la devuelve paginada, sin endpoint de edición ni borrado. | ✅ (`GestionDeCuentasDelPanelTest.debeRegistrarQuienLeHizoQueAQuien`, `.unaAccionDelSistemaDebeQuedarRegistradaSinAutor`, `.unFalloAlAuditarNoDebeTumbarLaOperacion`, `.debeDevolverLaAuditoriaPaginada`, `AdministrarCuentaServiceTest.aprobarDebeQuedarRegistradoEnLaAuditoria`) | ⛔ F5 pendiente |
| CP046 | Restablecer clave con enlace de un solo uso | `POST /api/cuentas/restablecimiento` responde igual, y tarda lo mismo, exista o no el correo (`RNF024`); `POST /api/cuentas/clave` cambia la clave y cierra todas las sesiones abiertas; reutilizar el mismo enlace se rechaza. | ✅ (`AltaYRecuperacionDeCuentaTest.restablecerLaClaveDebeRevocarTodasLasSesiones`, `.pedirRestablecimientoDeUnCorreoInexistenteNoDebeFallarNiMandarNada`, `.pedirRestablecimientoDeUnaCuentaRealDebeMandarElEnlace`, `GestionDeCuentasDelPanelTest.consumirDebeRechazarUnEnlaceYaUsado`, `.emitirDebeInvalidarLosEnlacesVivosDelMismoTipo`) | ⛔ F5 pendiente |

> **Nota sobre RNF022–RNF025** (autorización por permiso concreto, revocación inmediata de sesiones, no revelar existencia de
> correos, TOTP obligatorio para `ADMIN`): se verifican transversalmente con los CP042–CP046 y con
> `AutenticarUsuarioServiceTest` (`debeEmitirSesionConElCodigoCorrecto`, `debeRechazarUnCodigoCorrectoQueYaSeUso`) y
> `UsuarioTest` (`elSegundoFactorNoDebeExigirseHastaQueSeConfirma`, `unAdminSinSegundoFactorDebeTenerQueCompletarSuAlta`,
> `unAdminNoDebePoderDesactivarSuSegundoFactor`), no con un CP propio: no se numeran CP047+ para no inventar un caso que no
> agregue un escenario nuevo.

### Resumen del estado de los casos

| Estado | Casos |
|---|---|
| ✅ | CP001–CP003, CP005–CP007, CP009–CP021, CP023–CP031, CP037–CP040, CP042–CP046 |
| ⏳ Por ejecutar | CP004, CP008, CP022 (solo tienen sentido en la interfaz nueva) · la parte heurística de CP032–CP036 |
| ❌ | CP032–CP036 tal como están escritos (requisito descartado, `ADR-025`); en CP034, además, el rechazo automático no existe |
| 🟡 | CP041 (Telegram armado, sin probar contra Telegram real) |
