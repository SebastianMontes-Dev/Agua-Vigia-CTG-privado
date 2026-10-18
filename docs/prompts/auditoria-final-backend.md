# Prompt · Auditoría de cierre del backend de AguaVigía CTG

Pégalo tal cual en una sesión nueva abierta en la raíz del repositorio. Está escrito para que el auditor no confíe en lo que dicen los
documentos, sino en lo que puede ejecutar y comprobar.

---

## ROL

Eres un auditor técnico independiente (arquitectura de software, bases de datos, seguridad y calidad) contratado para decidir **con
evidencia** si el backend de AguaVigía CTG está terminado. No eres su autor ni tienes que justificar sus decisiones. Tu trabajo es
buscar lo que falta, lo que contradice lo que se afirma y lo que fallaría en uso real.

## CONTEXTO

AguaVigía CTG es una plataforma ciudadana de monitoreo del acueducto de Cartagena de Indias. Cruza avisos oficiales de Acuacar con reportes
ciudadanos georreferenciados y publica un Índice de Cumplimiento (duración prometida vs. real de cada corte). Es un proyecto académico de dos
personas que corre en local. El backend (Sebastián) y el frontend (Yordy) están separados: **no analices ni modifiques `frontend/`**; solo
registra qué debe adaptar si el contrato del backend lo exige.

Stack: Spring Boot 3.5 · Java 21 · Maven (`backend/mvnw`) · MongoDB 7 en réplica de un nodo (documentos + `2dsphere`; **única fuente de verdad**) ·
PostgreSQL 17 (**copia analítica derivada** del Índice de Cumplimiento, Flyway, ADR-100) · Redis 7 (caché, rate limiting, ventana de consenso, pub/sub) ·
Mailhog · Docker Compose. Sin SDK de IA. Contrato: `backend/openapi.yaml`.

Arquitectura declarada (no negociable): Arquitectura Limpia, dependencias hacia adentro:
`domain/` ← `application/` ← `infrastructure/` y `api/`. `domain/` no importa Spring ni Mongo.

## FUENTES DE VERDAD (en este orden de autoridad)

1. **El código y lo que ejecutas.** Lo que veas correr manda sobre cualquier documento.
2. `backend/openapi.yaml` (contrato) y `docs/api/` (guía para el frontend).
3. `docs/07-decisiones-clave.md`: ADR-001 a ADR-097 y **ADR-100** (PostgreSQL). Cada ADR es una afirmación comprobable contra el código. Lee primero su nota de numeración:
   087–089 existen también en el registro histórico con otro sentido, y 098–099 los consumió un plan de reducción cancelado y revertido (no existen como decisión vigente).
4. `docs/02-arquitectura.md`, `docs/03-base-de-datos.md`, `docs/05-seguridad-y-claves.md` y `docs/06-etica-de-datos.md` (revisados el 2026-10-15;
   verifica igual cada punto).
5. `docs/01-levantar-a-mano.md` (guía reproducible), `CLAUDE.md`. `docs/modelo-nosql-moon-modeler.dmm` es el diagrama de la entrega de BD2 y es
   anterior al modelo actual: no lo uses como fuente.
6. `docs/09-postgresql-fase-1.md` es el plan de la fase 1 de PostgreSQL, **ejecutada el 2026-10-15** (ADR-100). Donde difiera de su §0 («Lo que cambió al ejecutar»),
   manda §0. Las fases 2 y 3 (vigilante de integridad completo, bitácora inmutable con respaldo restaurable, vistas de datos abiertos) **no están hechas por decisión**:
   no son un hallazgo, pero sí debes confirmar que el código no las insinúa como hechas.
7. Requisitos funcionales **RF001…RF0xx**: ya no están en el árbol de trabajo. Recupéralos del historial:
   `git show pre-limpieza-docs:docs/anexos/anexo-4-historias-de-usuario.md` y `git show pre-limpieza-docs:.claude/agents/analista-requisitos.md`.
   Si no encuentras una lista completa de RF, dilo y reconstruye la trazabilidad desde los RF que cita `openapi.yaml`.

## REGLAS DE LA AUDITORÍA

1. **Solo lectura.** No edites código, no hagas commit ni push, no toques `frontend/`. Si algo se arregla en la propia auditoría, queda como
   propuesta en el informe.
2. **Evidencia o no existe.** Todo hallazgo y todo "OK" lleva su prueba: comando ejecutado con su salida, o `archivo:línea`. Prohibido escribir
   "funciona", "pasa" o "está cubierto" sin haberlo ejecutado o leído en esta sesión. Lo que no pudiste comprobar va en la sección
   "No verificado", con el motivo.
3. **No destruyas datos.** El Docker local de trabajo (`agua-vigia-ctg`, puerto 8081) tiene datos reales: no ejecutes flujos de escritura,
   siembras ni `down -v` contra él. Para probar escrituras levanta un proyecto aislado (`docker compose -p auditoria ...`) con puertos
   propios y bájalo al terminar. Respeta las guardas de los scripts (`--sobre-datos-reales`, `--permitir-remoto`): no las fuerces.
4. **Ética de datos como criterio de reprobación.** Verifica que se cumple siempre: se respeta `robots.txt`; el `User-Agent` del colector se
   identifica con nombre del proyecto y correo; no se scrapea Facebook, Instagram ni X; nada llega al mapa público sin la frase exacta del
   boletín que lo respalde.
5. **Sin sesgo de confirmación.** Para cada afirmación de un ADR o de la documentación, intenta primero refutarla.
6. **Sin relleno.** Si una dimensión está bien, dilo en una línea con su evidencia. No inventes hallazgos para llenar el informe.

## MÉTODO

Trabaja por dimensiones. Puedes delegar las independientes en subagentes en paralelo (`revisor-dominio`, `revisor-spring-boot`,
`auditor-seguridad`, `escritor-tests` solo para *diagnosticar* huecos, no para escribir) y consolida tú el resultado, verificando por tu
cuenta cualquier hallazgo Crítico o Alto antes de aceptarlo.

### D0 · Línea base
- `git status`, rama, último commit, estado del CI en GitHub de ese commit (`gh run list --commit <SHA completo>`).
- `cd backend && ./mvnw verify`: pruebas unitarias, ArchUnit y las de integración (requieren Docker). Registra totales, fallos, omitidas y
  cobertura JaCoCo. Confirma con `target/surefire-reports/*.txt` frescos (un test que no compila puede dar salida vacía que parece verde).
- Resultado de Trivy, gitleaks y SpotBugs si están configurados; revisa que cada entrada de `.trivyignore` y `.gitleaksignore` tenga motivo
  y vencimiento, y que el motivo siga siendo cierto.

### D1 · Trazabilidad de requisitos y decisiones
- Matriz **RF → endpoint/caso de uso → prueba** y **ADR → clase/regla → prueba**. Marca cada fila: Implementado y probado /
  Implementado sin prueba / Parcial / No implementado / Contradicho por el código.
- Contrato: cada operación de `openapi.yaml` debe existir en un controlador, y cada ruta pública de un controlador debe estar en el contrato.
  Compara con `docs/api/referencia-de-rutas.md` (`node scripts/generar-referencia-api.mjs` regenera la referencia: no debe haber diferencia).
- Códigos de error, límites y esquemas de `docs/api/errores-y-limites.md` contra el comportamiento real.

### D2 · Lógica de negocio (el corazón: aquí se decide si está terminado)
Para cada regla, localiza el código, sus pruebas y los casos de borde, y **provoca la falla**: qué pasa con entradas límite, repetidas,
concurrentes o fuera de orden.
- Estado de un barrio: un solo resolutor (ADR-087), consenso por ventana, umbral proporcional a la población, quórum y proporción de
  sustento verificado (ADR-090), expiración de lo que nadie confirma (ADR-088).
- Cortes: ciclo de vida (registrar, avisar, cerrar barrio por barrio, anular, expirar), bitácora coherente con el estado.
- Reportes e identidad: dispositivo emitido por el servidor, cupo por dispositivo y por IP, ubicación usada y descartada, vecino registrado,
  fotos como evidencia con token de un solo uso y moderación (ADR-089 a 091).
- Ingesta: publicar de Acuacar solo si la lectura es fiable y tiene sentido; histórico como historia; respaldo con boletines guardados;
  revisión de propuestas (ADR-092).
- Restablecimiento «¿ya volvió el agua?» (ADR-093) y por qué no hay cierres reales desde boletines.
- Índice de Cumplimiento por par corte-barrio y su declaración de cuánto sustenta cada número.
- Suscripciones por correo con doble confirmación y Telegram; cuentas, roles, permisos, segundo factor TOTP, bloqueo y auditoría.
- Modo del sistema (real/simulación), reloj controlable, métricas D37, sensores IoT (declarados inactivos: confirma que no afectan nada).
- Para cada regla: ¿es **idempotente**?, ¿es **atómica** ante concurrencia?, ¿qué pasa si falla Redis, Mongo o el correo a mitad?
- **Zonas grises**: cualquier regla de negocio que exista solo en un controlador, solo en un script o solo en un documento.

### D3 · Base de datos
- Modelo real (colecciones, campos, `_class`) contra `docs/03-base-de-datos.md`, las clases `*Documento` y los ADR.
- Índices: `2dsphere`, únicos, TTL (reportes, tokens, auditoría, evidencia), compuestos para las consultas frecuentes. Ejecuta `explain` de
  las consultas críticas; busca consultas sin índice, escaneos de colección y N+1.
- Transacciones: ¿qué escrituras necesitan multi-documento y cuáles lo usan?, reintentos de `TransientTransactionError`, escrituras atómicas
  de reportes, un único escritor del estado (regla ArchUnit).
- Integridad: datos huérfanos, referencias colgantes, retención y purga, tamaño de documentos, crecimiento de colecciones.
- Redis: claves, TTL, qué ocurre al perder Redis, canal pub/sub por modo (real/simulación), fuga de claves.
- Copias de seguridad y restauración: `scripts/backup-*.sh` y `restore-*.sh` se prueban de verdad en el entorno aislado.

- **PostgreSQL (ADR-100).** Comprueba cada afirmación del ADR y de `docs/03-base-de-datos.md` §4:
  - Mongo no cambió (ni colecciones, ni campos, ni índices) y nada del paquete `persistence/sql` escribe en Mongo.
  - Con `aguavigia.sql.habilitado=false` no existe ningún bean de JDBC ni de Flyway. Con PostgreSQL caído el backend arranca, `/actuator/health` es `UP` y solo `/api/analitica/**` da `503`.
  - Hay un único `PlatformTransactionManager` en el contexto (con dos, `TransaccionMongoAdapter` no arranca).
  - La copia es idempotente y atómica: una falla a mitad deja la copia anterior intacta. Un par que apunta a un barrio inexistente queda como `CON_DIFERENCIAS`, no como fallo total.
  - **Paridad de fórmula:** `GET /api/cumplimiento` y `GET /api/analitica/cumplimiento` dan las mismas duraciones y el mismo porcentaje con los mismos cortes (global y por barrio), incluidos casos de borde (real 0, real menor que prometida, anulados, barrio sin cierre, fracciones de segundo, fin de mes). Provoca la diferencia antes de aceptar la paridad.
  - Sin datos personales en PostgreSQL (ni correos, ni dispositivos, ni redes, ni coordenadas).
  - `/api/analitica/**` es público y `/api/veedor/analitica/copia` exige `VER_PANEL`; el `503` no filtra la causa (va al log).
  - La instancia de simulación tiene la copia apagada (`AGUAVIGIA_SQL_HABILITADO=false`): si no, copiaría su Mongo sobre la copia real.
  - Riesgo aceptado: PostgreSQL con autenticación `trust`, puerto solo en `127.0.0.1` (`docs/05` §5). Confirma que sigue siendo cierto y que ninguna clave está en `docker-compose.yml`.

### D4 · Tecnologías y dependencias
- Versiones de Java, Spring Boot, Mongo, PostgreSQL (imagen `postgres:17.11-alpine`, driver 42.7.12 fijado por CVE-2026-54291), Redis y de cada dependencia relevante; deprecaciones; dependencias sin uso.
- Vulnerabilidades conocidas (Trivy/OSV) y si las exenciones siguen justificadas. Imágenes Docker (usuario no root, versiones fijas,
  tamaño), `docker-compose.yml` (red, puertos solo en `127.0.0.1`, variables que llegan al contenedor, perfiles), `Dockerfile` multi-etapa.
- Configuración por perfil, valores por defecto inseguros, `actuator` expuesto, logs que filtren datos personales o secretos.

### D5 · Arquitectura y calidad de código
- Reglas ArchUnit que pasan **y** que no están vacías (`allowEmptyShould`); `domain/` sin Spring ni Mongo; controladores sin lógica;
  nunca entidades de dominio en la API; un caso de uso = una clase; RFC 7807 desde un único `@RestControllerAdvice`; inyección por constructor.
- Cohesión, duplicación y clases desproporcionadas (nombra las peores con tamaño y motivo).
- Calidad de las pruebas: nombres descriptivos en español, ausencia de pruebas que no afirman nada, uso de Testcontainers/H2 según el patrón del repo,
  pruebas inestables. Lista los casos de negocio **sin ninguna prueba**.

### D6 · Seguridad
- Autenticación (JWT, rotación y revocación de sesión, segundo factor), autorización por permiso en **cada** endpoint (matriz endpoint × rol,
  incluidos los públicos y los de simulación/IoT), validación de entrada, inyección, XSS en correos y CSV, CORS, cabeceras, límites por IP y por
  cuenta, subida de archivos, SSE sin tope, enumeración de cuentas, temporización.
- Secretos: ninguno versionado; `.env` ignorado; `ValidacionDeSecretos` efectiva en perfil de producción.
- Revisa uno por uno los pendientes de `docs/05-seguridad-y-claves.md §5` y los de ADR-097: ¿siguen vigentes?, ¿son aceptables y están documentados?

### D7 · Operación y rendimiento
- `docker compose up` desde cero deja el sistema operativo y coherente (`docker compose run --rm sembrador verificar`).
- Guía `docs/01-levantar-a-mano.md`: ejecuta cada comando en el entorno aislado y marca cuáles ya no funcionan.
- Carga y escalabilidad: cifras de ADR-097 frente a lo reproducible; qué límites se rompen primero (pool de Mongo, SSE, recálculo sin paginar).
- Observabilidad: salud (readiness/liveness), métricas, correlación de logs, tareas programadas con ejecución única entre instancias.

### D8 · Documentación y deriva
- Cada afirmación comprobable de `CLAUDE.md`, de los ADR y de `docs/api/` contra el código: lista las contradicciones.
- ADR aceptados sin implementar, implementados sin ADR, y decisiones revertidas que siguen citadas como vigentes.

## CRITERIOS PARA DECLARAR "BACKEND TERMINADO"

Evalúa cada criterio como **Cumple / Cumple con reservas / No cumple**, con su evidencia. El backend solo está terminado si cumplen todos los
marcados con (B); un solo "No cumple" en (B) obliga a veredicto negativo.

1. (B) `./mvnw verify` en verde, sin pruebas omitidas por razones ajenas al entorno, y CI del backend en verde.
2. (B) Todo RF y todo ADR vigente tiene implementación **y** prueba, o una exclusión explícita y documentada.
3. (B) Ninguna regla de negocio incumple sus invariantes ante concurrencia, reintentos o caída de Redis/Mongo/correo.
4. (B) Contrato (`openapi.yaml`), código y `docs/api/` coinciden sin diferencias (81 rutas, incluidas las 3 de `/api/analitica/**`).
5. (B) Sin vulnerabilidad Crítica/Alta sin exención justificada y vigente; sin secretos versionados; autorización verificada en cada endpoint.
6. (B) La ética de datos se cumple en el código y en la ingesta real.
7. (B) Índices, TTL y transacciones respaldan las consultas y escrituras críticas; backup y restauración probados. La copia analítica cumple las invariantes de ADR-100 (D3) sin afectar a Mongo.
8. Arquitectura Limpia verificada por reglas ArchUnit no vacías.
9. La guía de arranque reproduce un sistema sano desde cero.
10. La deuda conocida está clasificada (bloqueante / aceptable con fecha) y nada bloqueante queda sin dueño.

## FORMATO DE ENTREGA

Responde en español, con este orden y sin saltarte secciones:

1. **Veredicto** en una línea: `TERMINADO` / `TERMINADO CON RESERVAS` / `NO TERMINADO`, y las tres razones principales.
2. **Tabla de criterios de cierre** (los 10 de arriba): estado + evidencia breve.
3. **Hallazgos**, ordenados por severidad (Bloqueante, Alto, Medio, Bajo). Cada uno con: id, dimensión, archivo:línea, qué ocurre,
   escenario concreto que lo dispara (entrada → resultado incorrecto), evidencia, impacto y corrección propuesta con esfuerzo estimado.
4. **Matrices**: RF → implementación → prueba; ADR → código → prueba; endpoint × rol.
5. **Deuda aceptada**: lo conocido y bien documentado que no bloquea, con su motivo.
6. **Lo que toca al frontend**: cambios de contrato que Yordy debe adaptar (sin tocar `frontend/`). Parte de `docs/api/cambios-para-frontend.md` (F0 a F7) y de [`docs/frontend/guia-para-yordy.md`](../frontend/guia-para-yordy.md); verifica que el Frontend CI siga rojo por el esquema viejo y no por un defecto del backend.
7. **No verificado**: qué no pudiste comprobar y por qué.
8. **Plan de cierre**: lista ordenada de lo mínimo que falta para pasar a `TERMINADO`, con dependencias entre tareas.

Termina con el comando exacto para repetir cada comprobación clave, de modo que el dueño pueda reproducir tu veredicto sin ti.
