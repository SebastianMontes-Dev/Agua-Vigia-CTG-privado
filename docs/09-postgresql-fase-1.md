# 09 · PostgreSQL, fase 1: copia analítica del Índice de Cumplimiento

**Estado: ejecutado el 2026-10-15 (hora de Cartagena), pasos 0 a 7 de §8 y la verificación local del 8** (`./mvnw verify` con 2142 pruebas en verde). Faltan del paso 8 el commit, el CI de GitHub y avisar a Yordy. El ADR quedó en [07 · Decisiones clave](07-decisiones-clave.md) §5 (ADR-100) y la descripción
vigente del modelo en [03 · Base de datos](03-base-de-datos.md) §4. El resto de este documento es el plan tal como se aprobó; **donde difiera de la sección siguiente, manda la siguiente**.

## 0. Lo que cambió al ejecutar

| Plan | Lo construido | Por qué |
|---|---|---|
| Imagen `postgres:17-alpine` (D2) | `postgres:17.11-alpine` | Etiqueta exacta, la última 17.x publicada ese día. Trivy sobre la imagen corre en el CI, no en local |
| Puerto `127.0.0.1:5432` | `127.0.0.1:${POSTGRES_PUERTO:-5433}` | El equipo del dueño ya tiene un PostgreSQL 18 de Windows escuchando en 5432 |
| `sector.poblacion INTEGER NOT NULL`, columnas `localidad` y `zona` | `poblacion` **nula** y sin `localidad` ni `zona` | El dominio no tiene localidad ni zona, y 27 barrios no traen población (`Sector`); un 0 sería un barrio sin habitantes |
| `analitica.copia`: conteos de barrios y cortes | Añade `pares_en_mongo` y `pares_en_sql` | §4.3 compara también los pares corte-barrio |
| Vistas: `v_par_cerrado`, `v_cumplimiento_por_sector`, `v_cumplimiento_mensual` | Añade `v_cumplimiento_global`; las sumas van en milisegundos y se truncan a segundos al final | El endpoint global necesita su vista; así coincide al segundo con Mongo y `Duration.toSeconds()` aunque haya fracciones |
| `corte_sector` | Añade `CHECK`: un barrio sin cierre no puede traer fuente ni ser provisional | Coherencia con `FilaParAnalitica` |
| Un par que apunta a un barrio inexistente rompe la clave foránea | `CopiarAnaliticaService` lo omite y la copia queda `CON_DIFERENCIAS` con el detalle | Un solo dato colgado no debe impedir copiar todo lo demás |
| `AlmacenAnaliticoPort`: `registrarInicioDeCopia`, `reemplazar`, `contar`, `cerrarCopia` | Sin `contar()`: `reemplazar` devuelve los conteos, tomados dentro de la misma transacción | §4.3 pide comparar en la misma transacción |
| `CasosDeUsoConfig` no se tocaba | Se añade `CopiarAnaliticaService` a su exclusión del escaneo | El escaneo recoge toda clase `*Service` de `application/`; sin excluirla el servicio se creaba aun con SQL apagado |
| `backend-sim` no se mencionaba | `AGUAVIGIA_SQL_HABILITADO: "false"` en `backend-sim` | Comparte el perfil `docker`; copiaría su Mongo de simulación sobre la copia real |
| Pruebas `*IT` | Se llaman `*IntegrationTest` | Surefire ejecuta `*Test`; no hay Failsafe, y un `*IT` no correría en `./mvnw verify` |
| `ArranqueSinPostgresIT` y `ArranqueConSqlDeshabilitadoTest` | `ArranqueSinPostgresIntegrationTest` y `ArranqueConSqlDeshabilitadoTest` | Ídem |
| 503: el mensaje trae la causa | El mensaje es genérico; la causa va al log | Es una ruta pública y un error de PostgreSQL revelaría el esquema |

Se añadieron además dos reglas de ArchUnit (`domain/` y `application/` sin JDBC ni Flyway; el paquete `sql` sin escribir en Mongo por los repositorios) y `CopiaAnaliticaJobTest`.

**Verificado en el compose real:** `analitica.copia` con una fila `OK` (211 barrios, 7 cortes y 51 pares a los dos lados); `GET /api/analitica/cumplimiento` da las mismas duraciones y el mismo porcentaje que
`GET /api/cumplimiento`; con `docker compose stop postgres` el backend sigue `UP` y respondiendo y solo la analítica da 503; al volver a arrancarlo responde 200.

## 1. Qué se quiere y qué no

**Objetivo.** Que el sistema use una base **PostgreSQL** real y funcionando (requisito del profesor), con un modelo relacional que tenga sentido
para el proyecto: el **Índice de Cumplimiento** (duración prometida vs. real de cada corte, por barrio), que es lo que AguaVigía publica.

**Reglas que no se negocian** (decididas con el dueño el 2026-10-15):

1. **MongoDB no cambia.** Ni colecciones, ni campos, ni índices, ni TTL, ni datos. No se añade ninguna escritura en Mongo.
2. **MongoDB sigue siendo la base principal y la única fuente de verdad.** Todos los endpoints que existen hoy siguen leyendo de Mongo.
3. **PostgreSQL es una copia derivada.** Se llena leyendo de Mongo; Mongo nunca lee de PostgreSQL. Si se borra entera, se reconstruye desde Mongo.
4. **Si PostgreSQL está apagado o falla, el sistema funciona igual.** Arranca, responde, está `UP`; solo deja de actualizarse la copia.
5. **No se modifica el comportamiento de nada existente.** Lo nuevo vive en clases nuevas. Los únicos archivos existentes que se tocan son los de
   configuración y cableado que lo exigen (§6), sin cambiar lo que ya hacen.
6. **Ningún dato personal en PostgreSQL**: ni correos, ni identidades de dispositivo, ni redes, ni coordenadas. Solo cortes, barrios y duraciones.

**Fuera de la fase 1** (quedan como posibles fases siguientes, ver §10): el vigilante de integridad completo, la bitácora inmutable, el respaldo
restaurable, las vistas de datos abiertos y cualquier lectura de emergencia o cola de escritura.

## 2. Arquitectura

```
                 (lee, sin cambios)                        (escribe, solo aquí)
MongoDB ──► CorteAguaRepository.listarTodos() ─┐
        └─► SectorRepository.listarTodos()  ───┼──► CopiarAnaliticaService ──► AlmacenAnaliticoPort ──► PostgreSQL
                                               │      (application/)            (domain/port/out)      (infrastructure/persistence/sql/)
                     CopiaAnaliticaJob ────────┘
                (infrastructure/, cada 5 min, EjecucionUnica)

GET /api/analitica/... ──► AnaliticaController ──► ConsultaAnaliticaPort ──► vistas de PostgreSQL
```

- **Lectura de Mongo:** se reutilizan dos métodos de solo lectura que **ya existen** (`CorteAguaRepository.listarTodos()` y
  `SectorRepository.listarTodos()`). No se toca ningún adaptador de Mongo.
- **Escritura en PostgreSQL:** un puerto nuevo, `AlmacenAnaliticoPort`, implementado en un paquete nuevo, `infrastructure/persistence/sql/`.
- **Consulta:** un puerto nuevo, `ConsultaAnaliticaPort`. El controlador depende de él directamente, porque es una lectura sin regla de negocio (ADR-015).
- **Capas:** `domain/` y `application/` no importan nada de JDBC ni de PostgreSQL. Se añade una regla ArchUnit que lo exige (§7).

## 3. Modelo relacional

Esquema `analitica` en la base `aguavigia_analitica`. Las migraciones las gestiona **Flyway** (`backend/src/main/resources/db/migration/`).

### 3.1 Tablas

```sql
-- V1__esquema_analitico.sql
CREATE SCHEMA IF NOT EXISTS analitica;

CREATE TABLE analitica.sector (
    id            TEXT PRIMARY KEY,                 -- el slug de Mongo (p. ej. 'albornoz')
    nombre        TEXT        NOT NULL,
    poblacion     INTEGER     NOT NULL CHECK (poblacion >= 0),
    localidad     TEXT,
    zona          TEXT,
    copiado_en    TIMESTAMPTZ NOT NULL
);

CREATE TABLE analitica.corte (
    id             TEXT PRIMARY KEY,                -- el id del corte en Mongo
    origen         TEXT        NOT NULL,            -- OFICIAL, ACUACAR, ...
    estado         TEXT        NOT NULL,            -- ANUNCIADO, RESTABLECIDO, EXPIRADO, ANULADO, ...
    causa          TEXT,
    inicio         TIMESTAMPTZ NOT NULL,
    fin_prometido  TIMESTAMPTZ NOT NULL,
    copiado_en     TIMESTAMPTZ NOT NULL,
    CHECK (fin_prometido > inicio)
);

-- Una fila por barrio afectado (par corte-barrio, D14). El cierre es opcional: un barrio sin cierre todavía no cuenta en el Índice.
CREATE TABLE analitica.corte_sector (
    corte_id            TEXT        NOT NULL REFERENCES analitica.corte(id)  ON DELETE CASCADE,
    sector_id           TEXT        NOT NULL REFERENCES analitica.sector(id),
    cierre_hora         TIMESTAMPTZ,
    cierre_fuente       TEXT,
    cierre_provisional  BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (corte_id, sector_id)
);
CREATE INDEX corte_sector_por_sector ON analitica.corte_sector (sector_id);
CREATE INDEX corte_sector_por_cierre ON analitica.corte_sector (cierre_hora) WHERE cierre_hora IS NOT NULL;

-- Una fila por cada copia: cuándo, cuánto y si los conteos cuadraron con Mongo (§4.3).
CREATE TABLE analitica.copia (
    id                 BIGSERIAL   PRIMARY KEY,
    iniciada_en        TIMESTAMPTZ NOT NULL,
    terminada_en       TIMESTAMPTZ,
    resultado          TEXT        NOT NULL CHECK (resultado IN ('EN_CURSO', 'OK', 'CON_DIFERENCIAS', 'FALLIDA')),
    sectores_en_mongo  INTEGER,
    sectores_en_sql    INTEGER,
    cortes_en_mongo    INTEGER,
    cortes_en_sql      INTEGER,
    detalle            TEXT
);
```

Qué demuestra cada parte ante el profesor: claves primarias naturales, **claves foráneas** con borrado en cascada, restricciones `CHECK`,
una tabla de relación (corte × barrio), un índice parcial y migraciones versionadas.

### 3.2 Vistas: el Índice en SQL

La fórmula tiene que ser **la misma** que la de `CalcularCumplimientoService` (ADR-022):

- Cada par corte-barrio con cierre es una fila.
- Por fila, la duración prometida es `fin_prometido − inicio` y la real es `cierre_hora − inicio`.
- Se **suman** duraciones, sin promediar porcentajes.
- El porcentaje es `min(100, prometida × 100 / real)`, o `100` si la real es 0.
- Los cortes `ANULADO` no entran.

```sql
-- V2__vistas_cumplimiento.sql
CREATE VIEW analitica.v_par_cerrado AS
SELECT cs.corte_id, cs.sector_id, c.inicio, c.fin_prometido, cs.cierre_hora, cs.cierre_provisional,
       EXTRACT(EPOCH FROM (c.fin_prometido - c.inicio))::BIGINT AS prometida_s,
       EXTRACT(EPOCH FROM (cs.cierre_hora  - c.inicio))::BIGINT AS real_s
FROM analitica.corte_sector cs
JOIN analitica.corte c ON c.id = cs.corte_id
WHERE c.estado <> 'ANULADO' AND cs.cierre_hora IS NOT NULL;

CREATE VIEW analitica.v_cumplimiento_por_sector AS
SELECT s.id AS sector_id, s.nombre,
       COUNT(p.*)                                   AS pares_cerrados,
       COALESCE(SUM(p.prometida_s), 0)              AS prometida_s,
       COALESCE(SUM(p.real_s), 0)                   AS real_s,
       COALESCE(SUM(p.real_s), 0) - COALESCE(SUM(p.prometida_s), 0) AS desviacion_s,
       CASE WHEN COALESCE(SUM(p.real_s), 0) = 0 THEN 100.0
            ELSE LEAST(100.0, SUM(p.prometida_s) * 100.0 / SUM(p.real_s)) END AS porcentaje_cumplimiento
FROM analitica.sector s
LEFT JOIN analitica.v_par_cerrado p ON p.sector_id = s.id
GROUP BY s.id, s.nombre;

CREATE VIEW analitica.v_cumplimiento_mensual AS
SELECT date_trunc('month', cierre_hora AT TIME ZONE 'America/Bogota') AS mes,
       COUNT(*) AS pares_cerrados, SUM(prometida_s) AS prometida_s, SUM(real_s) AS real_s,
       CASE WHEN SUM(real_s) = 0 THEN 100.0 ELSE LEAST(100.0, SUM(prometida_s) * 100.0 / SUM(real_s)) END AS porcentaje_cumplimiento
FROM analitica.v_par_cerrado
GROUP BY 1;
```

Consultas de ejemplo que la base permite y Mongo no hace tan directas (para la sustentación; no son endpoints):

```sql
-- Barrios con peor cumplimiento, con su puesto (función de ventana)
SELECT sector_id, nombre, porcentaje_cumplimiento,
       RANK() OVER (ORDER BY porcentaje_cumplimiento ASC) AS puesto
FROM analitica.v_cumplimiento_por_sector WHERE pares_cerrados > 0;

-- Mediana y percentil 90 de lo que tarda de verdad en volver el agua
SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY real_s) / 3600.0 AS mediana_horas,
       percentile_cont(0.9) WITHIN GROUP (ORDER BY real_s) / 3600.0 AS p90_horas
FROM analitica.v_par_cerrado;
```

## 4. La copia desde Mongo

### 4.1 Cuándo corre

`CopiaAnaliticaJob` (en `infrastructure/`) cada **5 minutos** (`aguavigia.sql.copia-intervalo-ms`, por defecto 300 000) y una vez al arrancar, con
1 minuto de retraso para dar tiempo a la siembra. Pasa por `EjecucionUnica`, igual que las demás tareas (ver [02](02-arquitectura.md) §4).

No se engancha al cierre de un corte. Hacerlo obligaría a tocar `GestionarCorteOficialService`, y un fallo de PostgreSQL quedaría a un paso del
camino de escritura de Mongo. Cinco minutos de retraso en una vista analítica no importan.

### 4.2 Qué hace (`CopiarAnaliticaService`)

1. Registra una fila en `analitica.copia` con `resultado = 'EN_CURSO'`.
2. Lee de Mongo todos los sectores y todos los cortes con los puertos existentes.
3. En **una sola transacción de PostgreSQL**:
   - hace **UPSERT** de cada sector (`INSERT ... ON CONFLICT (id) DO UPDATE`);
   - hace UPSERT de cada corte;
   - reemplaza sus filas de `corte_sector`.

   Repetir la copia no duplica nada (idempotente). Si algo falla, PostgreSQL vuelve atrás y queda la copia anterior entera.
4. Cuenta lo que hay en cada lado (§4.3) y cierra la fila de `analitica.copia` con `OK`, `CON_DIFERENCIAS` o `FALLIDA`, y un aviso en el log si no
   es `OK`.

**Los cierres de cada barrio** salen de `CorteAgua.cierres()`. Hay que confirmar en la implementación cómo trata el dominio los cortes anteriores a
los cierres por barrio. Esos cortes solo traen `finReal`, y la agregación de Mongo (`CorteAguaMongoAdapter.paresCerrados`) los cuenta como un
cierre a esa hora en cada barrio. La copia tiene que hacer lo mismo. Si el dominio no lo hace, la regla se replica en `CopiarAnaliticaService`, y la
prueba de paridad (§7) lo vigila.

**Cortes borrados en Mongo:** hoy no se borran (se anulan). Si algún día se borraran, la copia los quitaría de PostgreSQL al no encontrarlos
(`DELETE ... WHERE id NOT IN (...)` dentro de la misma transacción).

### 4.3 La comparación barata

Al final de cada copia, en la misma transacción, se compara:

| Qué | Mongo | PostgreSQL | Si no coincide |
|---|---|---|---|
| Barrios | `sectores` leídos | `COUNT(*)` de `analitica.sector` | `CON_DIFERENCIAS` |
| Cortes | cortes leídos | `COUNT(*)` de `analitica.corte` | `CON_DIFERENCIAS` |
| Pares corte-barrio | suma de `sectoresAfectados` | `COUNT(*)` de `analitica.corte_sector` | `CON_DIFERENCIAS` |

No es el vigilante completo de la fase 2, que buscaría referencias colgantes y sumas de control. Sí es una primera señal: queda registrada en
`analitica.copia` y en el log.

## 5. Endpoints nuevos (solo lectura)

| Ruta | Acceso | Qué devuelve |
|---|---|---|
| `GET /api/analitica/cumplimiento` | Público, como `/api/cumplimiento` | El Índice global desde la vista SQL, con `copiadoEn` (fecha de la última copia `OK`) |
| `GET /api/analitica/cumplimiento?sectorId=…` | Público | El de un barrio |
| `GET /api/analitica/cumplimiento/mensual` | Público | La serie por mes |
| `GET /api/veedor/analitica/copia` | Panel, permiso `VER_PANEL` | Las últimas copias: resultado, conteos y diferencias |

- Con la copia apagada o PostgreSQL caído, responden **503** con un `ProblemDetail` (RFC 7807) del tipo `servicio-no-disponible`, el que ya usa el
  backend (`ManejadorGlobalDeErrores`); el adaptador lanza una excepción de dominio y el manejador único la traduce. Nunca leen de Mongo como
  alternativa, porque mezclar fuentes ocultaría el problema.
- Se documentan en `backend/openapi.yaml` y se regenera `docs/api/referencia-de-rutas.md`. **El frontend no tiene que adaptar nada**: son rutas
  nuevas y opcionales. Se avisa a Yordy de que existen.

## 6. Cambios por archivo

**Nuevos:**

| Archivo | Contenido |
|---|---|
| `domain/port/out/AlmacenAnaliticoPort.java` | `registrarInicioDeCopia`, `reemplazar(sectores, cortes)`, `contar()`, `cerrarCopia(...)` |
| `domain/port/out/ConsultaAnaliticaPort.java` | Índice global, por barrio, mensual; últimas copias |
| `domain/` (records) | `FilaCorteAnalitica`, `ResultadoDeCopia` y lo que haga falta, en Java puro |
| `application/CopiarAnaliticaService.java` | §4.2 |
| `infrastructure/persistence/sql/SqlConfig.java` | `DataSource` y `JdbcClient`, **solo si** `aguavigia.sql.habilitado=true`. Ningún `PlatformTransactionManager` como bean (§6.1) |
| `infrastructure/persistence/sql/AlmacenAnaliticoPostgresAdapter.java` | UPSERT y conteos con `JdbcClient`, con su gestor de transacciones privado; ejecuta las migraciones de Flyway en la primera copia con conexión |
| `infrastructure/persistence/sql/ConsultaAnaliticaPostgresAdapter.java` | Lectura de las vistas |
| `infrastructure/persistence/sql/CopiaAnaliticaJob.java` | §4.1 |
| `api/AnaliticaController.java` y sus DTO | §5 |
| `resources/db/migration/V1__esquema_analitico.sql`, `V2__vistas_cumplimiento.sql` | §3 |
| Pruebas | §7 |

**Existentes que se tocan** (solo configuración y cableado; ninguno cambia lo que ya hace):

| Archivo | Cambio |
|---|---|
| `backend/pom.xml` | `spring-boot-starter-jdbc`, driver `org.postgresql:postgresql`, `flyway-core` + `flyway-database-postgresql`; en pruebas, `org.testcontainers:postgresql` |
| `resources/application.yml` | Bloque `aguavigia.sql` (`habilitado: false` por defecto, URL, usuario, intervalo) y exclusión de las autoconfiguraciones de `DataSource`, JDBC y Flyway (§6.1) |
| `resources/application-docker.yml` | `aguavigia.sql.habilitado: true` y la URL `jdbc:postgresql://postgres:5432/aguavigia_analitica` |
| `infrastructure/config/SecurityConfig.java` | Añadir `/api/analitica/**` a `RUTAS_PUBLICAS` (las de `/api/veedor/**` ya exigen sesión) |
| `api/error/ManejadorGlobalDeErrores.java` | Traducir la excepción de dominio «analítica no disponible» a 503 `servicio-no-disponible` (un método más; los existentes no cambian) |
| `docker-compose.yml` | Servicio `postgres` y volumen `postgres-data` (§6.2) |
| `.env.example` | Variables de PostgreSQL, comentadas, con su explicación |
| `backend/openapi.yaml` | Las cuatro rutas de §5 |
| `ReglaDeOroArchitectureTest.java` | La regla nueva de §7 |
| Documentación | §9 |

### 6.1 Que PostgreSQL no pueda tumbar el backend (la parte delicada)

Añadir JDBC a un proyecto que hoy solo tiene Mongo tiene tres trampas. El plan las cierra así:

1. **Un segundo gestor de transacciones.**
   - **Problema:** hoy el único `PlatformTransactionManager` es `MongoTransactionManager` (`MongoTransaccionConfig`), y `TransaccionMongoAdapter`
     lo recibe **por tipo** en su constructor. Comprobado el 2026-10-15: si aparece un segundo bean de ese tipo, Spring no sabe cuál inyectar y el
     backend no arranca.
   - **Solución, sin tocar nada de Mongo:**
     - el gestor de SQL (`JdbcTransactionManager`) **no se registra como bean**: lo crea `AlmacenAnaliticoPostgresAdapter` para sí mismo, junto con
       su `TransactionTemplate`;
     - así sigue habiendo un solo `PlatformTransactionManager` en el contexto;
     - una prueba confirma que el contexto arranca con SQL habilitado y que `TransaccionPort` sigue usando el de Mongo.
2. **El arranque con PostgreSQL apagado.**
   - **Problema:** la autoconfiguración de Spring intenta conectar al arrancar, y Flyway migra al arrancar. Con PostgreSQL caído, el backend no arrancaría.
   - **Solución:**
     - se excluyen esas autoconfiguraciones;
     - `SqlConfig` crea el pool con `initializationFailTimeout = -1`, para que no falle si no hay conexión;
     - Flyway se ejecuta **desde el job**, en la primera copia que consiga conexión, no al arrancar.
3. **El chequeo de salud.**
   - **Problema:** el indicador de base de datos de Actuator marcaría `DOWN` toda la aplicación si PostgreSQL cae, y el `healthcheck` del compose y
     `--wait` fallarían.
   - **Solución:** `management.health.db.enabled: false`. El estado de la copia se ve en `GET /api/veedor/analitica/copia`, no en `/actuator/health`.

Con `aguavigia.sql.habilitado=false` (el valor por defecto de `application.yml`, y lo que usan todas las pruebas existentes) no se crea **ningún**
bean de SQL: el backend es exactamente el de hoy.

### 6.2 Docker Compose

```yaml
  postgres:
    image: postgres:17-alpine            # fijar la etiqueta exacta (y comprobarla en Trivy) al ejecutar
    container_name: aguavigia-postgres
    environment:
      POSTGRES_DB: aguavigia_analitica
      POSTGRES_USER: aguavigia
      POSTGRES_HOST_AUTH_METHOD: trust   # D1: solo local, como Mongo y Redis
    ports:
      - "127.0.0.1:5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aguavigia -d aguavigia_analitica"]
      interval: 5s
      retries: 10
    networks:
      - aguavigia
```

El backend **no** declara `depends_on: postgres`. Si PostgreSQL tarda o no arranca, el backend sigue igual y el job lo reintenta. `docker compose up` a
secas levanta también `postgres`, así que se mantiene un solo `up` (ADR-094).

## 7. Pruebas

Siguen el patrón del repo: JUnit 5, nombres en español y Testcontainers para la integración. Las de integración exigen Docker, como las de Mongo.

| Prueba | Qué demuestra |
|---|---|
| `CopiarAnaliticaServiceTest` (unitaria, con dobles de los puertos) | Arma bien los pares corte-barrio, también los cortes antiguos que solo traen `finReal`; marca `CON_DIFERENCIAS` si los conteos no cuadran; marca `FALLIDA` y no propaga si el almacén falla |
| `AlmacenAnaliticoPostgresAdapterIT` (Testcontainers PostgreSQL) | Flyway aplica V1 y V2; copiar dos veces deja los mismos conteos (idempotencia); una clave foránea rota se rechaza; un fallo a mitad no deja nada a medias |
| `VistasDeCumplimientoIT` | La vista da el mismo porcentaje que `CalcularCumplimientoService` en casos de borde: real 0 → 100; real < prometida → 100; real > prometida; anulados fuera; barrio sin cierre fuera |
| `ParidadMongoPostgresIT` (Mongo y PostgreSQL en Testcontainers) | Con los mismos cortes, `GET /api/cumplimiento` y `GET /api/analitica/cumplimiento` devuelven las mismas duraciones y el mismo porcentaje, global y por barrio |
| `ArranqueSinPostgresIT` | Con `habilitado=true` y PostgreSQL inalcanzable, el contexto arranca, `/actuator/health/readiness` es `UP`, `/api/cumplimiento` responde y `/api/analitica/cumplimiento` da 503 |
| `ArranqueConSqlDeshabilitadoTest` | Con `habilitado=false` no existe ningún bean de JDBC ni de Flyway |
| `AnaliticaControllerTest` (MockMvc) | Rutas, 503 con `ProblemDetail`, `VER_PANEL` en `/api/veedor/analitica/copia` |
| Regla ArchUnit nueva | Ni `domain/` ni `application/` importan `java.sql`, `javax.sql`, `org.springframework.jdbc` ni `org.flywaydb` |

## 8. Orden de ejecución y puerta de cada paso

Cada paso termina con su verificación antes de seguir. Un commit por paso, directo a `main`, solo cuando el dueño lo pida.

| # | Paso | Puerta (lo que debe verse) |
|---|---|---|
| 0 | Fijar la etiqueta de la imagen de PostgreSQL (D2; D1 ya está decidida) | Etiqueta escrita en §6.2 |
| 1 | Dependencias en `pom.xml` y `SqlConfig` condicional, sin nada más | `./mvnw verify` en verde con `habilitado=false`, mismo número de pruebas que antes más las nuevas; Trivy sin críticas nuevas |
| 2 | Servicio `postgres` en el compose | `docker compose up -d postgres` sano; el resto del sistema arranca igual |
| 3 | Migraciones V1 y V2 y el adaptador de escritura | `AlmacenAnaliticoPostgresAdapterIT` y `VistasDeCumplimientoIT` en verde |
| 4 | `CopiarAnaliticaService` y `CopiaAnaliticaJob` | Prueba unitaria en verde; en el compose, una fila `OK` en `analitica.copia` con 211 barrios |
| 5 | Puerto de consulta, controlador, contrato y seguridad | `AnaliticaControllerTest` y `ParidadMongoPostgresIT` en verde; referencia de rutas regenerada |
| 6 | Pruebas de resistencia | `ArranqueSinPostgresIT` en verde; a mano: `docker compose stop postgres` y el sistema sigue respondiendo |
| 7 | Documentación (§9) y ADR | Revisada contra lo construido |
| 8 | Cierre | `./mvnw verify` completo; CI de GitHub en verde; recorrido de la guía 01 sin cambios de resultado; aviso a Yordy |

## 9. Documentación que se actualiza al ejecutar

- [07 · Decisiones clave](07-decisiones-clave.md): el ADR-100 de §12, como aceptado.
- [03 · Base de datos](03-base-de-datos.md): la §4 pasa de «planificado» a la descripción real.
- [02 · Arquitectura](02-arquitectura.md): el paquete `persistence/sql`, el job y el módulo nuevo.
- [05 · Seguridad y claves](05-seguridad-y-claves.md): el puerto 5432 en `127.0.0.1` y el riesgo aceptado de D1.
- [01 · Guía paso a paso](01-levantar-a-mano.md): un paso para levantar `postgres` y una consulta a la vista.
- `CLAUDE.md`: PostgreSQL en el stack, como copia analítica.
- Este documento pasa a estado «ejecutado».

## 10. Lo que queda para fases siguientes

| Fase | Contenido | Por qué no ahora |
|---|---|---|
| 2 · Vigilante | Comparar Mongo y PostgreSQL a fondo (sumas de control, referencias colgantes) y avisar | La comparación barata de §4.3 da la primera señal; el vigilante completo duplica trabajo si la fase 1 cambia |
| 3 · Bitácora inmutable y respaldo | Copia de eventos y auditoría sin `UPDATE`/`DELETE`, y restauración probada | Más datos copiados, más privacidad que revisar |
| Datos abiertos | Vistas de solo lectura con un usuario de BD propio | Sale casi gratis sobre la fase 1, pero nadie la pide |

## 11. Decisiones abiertas

**D1 · Autenticación de PostgreSQL. Decidida por el dueño el 2026-10-15: `trust`.**
- **El conflicto:** el arranque sin `.env` (ADR-086) choca con que PostgreSQL necesita una contraseña al crearse. Además, no se pueden versionar secretos.
- **Decisión:** `POSTGRES_HOST_AUTH_METHOD: trust`, con el puerto solo en `127.0.0.1` y la red interna de compose. Es el mismo trato que ya
  reciben Mongo y Redis (sin autenticación, solo locales; [05](05-seguridad-y-claves.md) §3), y la base no guarda datos personales.
  Al ejecutar, se anota como riesgo aceptado en `docs/05`.
- **Descartada:** `POSTGRES_PASSWORD` obligatorio en el `.env`. Es más estricto, pero rompe el `docker compose up` sin configuración.
- **Lo que no se hace en ningún caso:** escribir una contraseña en `docker-compose.yml`.

**D2 · Versión de PostgreSQL.** Se propone `postgres:17-alpine`. Al ejecutar se fija la etiqueta exacta disponible y se comprueba con Trivy.

## 12. ADR propuesto (se copia a `docs/07` al ejecutar)

> **ADR-100 — PostgreSQL como copia analítica del Índice de Cumplimiento; MongoDB sigue siendo la única fuente de verdad**
>
> *Contexto.* La materia exige usar SQL. MongoDB es y seguirá siendo la base principal (ADR-003); nadie quiere migrar datos ni tocar lo que funciona.
> *Decisión.* PostgreSQL guarda una copia derivada y sin datos personales de barrios, cortes y cierres por barrio, que llena cada 5 minutos un job
> que lee de Mongo con los puertos existentes. Expone el Índice en vistas SQL y en rutas nuevas de solo lectura. Si cae, el sistema sigue igual.
> *Gana.* Un modelo relacional real (claves foráneas, restricciones, vistas, funciones de ventana) sobre el dato que el proyecto publica; una
> primera señal de diferencias entre bases.
> *Pierde.* Un servicio más que operar; la vista puede ir hasta 5 minutos por detrás de Mongo; la fórmula del Índice existe dos veces (Java y SQL),
> y por eso hay una prueba de paridad.
> *Se revierte* quitando el servicio, el paquete `persistence/sql` y las rutas `/api/analitica/**`; Mongo no se entera.
>
> Los números 098 y 099 quedaron consumidos por el plan de reducción cancelado (ver la nota de numeración de `docs/07`).
