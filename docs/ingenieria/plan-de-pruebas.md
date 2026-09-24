# Plan de pruebas

> **Estado: PLAN, no informe.** Los documentos académicos (capítulos, anexos) se retiraron del repo
> el 2026-09-17 y se rehacen más adelante; este plan sigue vigente como estrategia de pruebas del
> proyecto, independiente de esa numeración. **Su parte de resultados sale del registro, no de la
> memoria**: se construye desde `registro-de-bugs.md` y `registro-de-implementaciones.md` cuando
> existan. Escribir esa parte ahora sería inventar datos.
>
> Este documento es la **estrategia**. El backend ya está construido (823 pruebas en verde, ver
> [`estado-del-backend.md`](estado-del-backend.md)); los resultados por RNF viven en
> `registro-de-implementaciones.md` y `matriz-trazabilidad.md`, no aquí.

---

## 1. Principio rector

**Ningún resultado se declara sin la prueba que lo sostiene.** Un "probado manualmente" no cuenta
(`docs/gestion/protocolo-de-contexto.md`, cultura del proyecto). Cada fila de la matriz de abajo tiene
un `RNF` verificable con métrica y umbral — no hay pruebas "porque sí".

---

## 2. Matriz de pruebas por tipo — trazada a `product-requirements.md`

| Tipo de prueba | RNF que verifica | Herramienta | Cuándo se ejecuta | Sprint en que se implementa |
|---|---|---|---|---|
| Unitarias `domain/` · `application/` | RNF017 (cobertura ≥ 70%) | JUnit 5 + JaCoCo | En cada PR, CI | 1 (base) → 5 (umbral exigido) |
| Arquitectura | RNF018 (falla si se viola una capa) | ArchUnit | En cada PR, CI | 1 |
| Integración backend ↔ datos | — (soporta RNF017) | Testcontainers (Mongo, Redis reales) | En cada PR, CI | 2 |
| Rendimiento del mapa | RNF001 (< 3 s en 3G simulada) | Lighthouse + throttling | Antes de cada release | 4 (ajustes 3G) — ⛔ retirado por alcance hasta que exista el frontend nuevo (`ADR-048`) |
| Rendimiento de escritura | RNF002 (confirmación < 1 s) | k6 contra `POST /api/reportes` (`scripts/carga/rnf002-registrar-reporte.js`) | Antes de cada release | 2 |
| Caché | RNF003 (TTL ≤ 60 s) | Inspección de cabeceras HTTP / Redis | Manual + smoke test en CI | 2 |
| Caos — caída de fuente externa | RNF004, RNF005, RNF006 | Apagar el colector en `docker compose`, observar cortacircuitos y cola muerta | Sprint 4, repetible | 4 |
| Salud de colectores | RNF007 | `GET /actuator/health` | Smoke test en CI | 4 |
| Datos personales | RNF008, RNF009 | Revisión de código + prueba de baja de suscripción | Manual, checklist de PR | 1 (M4), 5 (auditoría) |
| Secretos en el repo | RNF010 | `gitleaks` en CI (ya activo desde Sprint 0, `.github/workflows/secret-scan.yml`) | En cada push | 0 |
| Seguridad del panel admin | RNF011 (JWT ≤ 8 h) | Test de seguridad (expiración de token) | Sprint 3 | 3 |
| Accesibilidad | RNF012–RNF016 (contraste, teclado, táctil, responsive, no-solo-color) | `axe-core` + Lighthouse + prueba manual con teclado | Por página, antes de cada release | 1 → 5 (auditoría formal) — ⛔ retirado por alcance hasta que exista el frontend nuevo (`ADR-048`) |
| Precisión del clasificador IA | RNF019 (≥ 90% sobre conjunto dorado) | Prueba de regresión en CI contra conjunto dorado etiquetado | Cada cambio al prompt/pipeline M9 | 4 (etiquetado) → 5 (CI) |
| Arranque en máquina limpia | RNF020 (`docker compose up`, un comando) | E2E de infraestructura | Antes de cada release | 0 (compose base) → 5 (documentado en manual técnico) |
| Flujo completo de usuario | RF001–RF028 (flujos principales) | Playwright E2E | Antes de cada release | 5 — ⛔ retirado por alcance hasta que exista el frontend nuevo (`ADR-048`); mientras tanto, `scripts/carga/` y las pruebas de integración del backend |

**Sin RNF asociado, no hay fila.** Si aparece una necesidad de prueba sin requisito que la respalde, se
corrige `product-requirements.md` primero (mismo criterio que usa `registrar-implementacion`).

---

## 3. Ambientes

| Ambiente | Para qué | Cómo se levanta |
|---|---|---|
| Local | Desarrollo y pruebas unitarias/integración | `./mvnw test` |
| CI (GitHub Actions) | Puerta de calidad en cada PR — ver `.github/workflows/` | Automático en `push`/`pull_request` |
| Réplica local completa | Caos, RNF020, pruebas de carga | `docker compose up` (Dockerfile de `/backend`; el frontend se retiró, `ADR-048`) |
| Staging desplegado | — | **No existe**: el proyecto es académico y corre en local, sin hosting (`ADR-057`). La demo se hace con `docker compose` en los PC del equipo |

---

## 4. Datos de prueba

- **Conjunto dorado para M9 (ingesta con IA):** boletines reales de Acuacar etiquetados a mano
  (`origen: OFICIAL_ACUACAR`, ver `docs/ingenieria/pipeline-ingesta-datos.md`). Etiquetado es tarea del Sprint 4. RNF019 se mide contra este conjunto, no
  contra datos sintéticos.
- **Dataset histórico para la demo final:** boletines y reportes de mayo–julio 2026, tarea del
  Sprint 6.
- **Datos geoespaciales:** ya verificados y disponibles — `data/geoespacial/` (213 barrios, 184 con
  población real). Las pruebas de integración que necesiten sectores reales parten de ahí, no de
  fixtures inventados.

---

## 5. Definición de terminado para una prueba

Una prueba está **Hecha** cuando (alineado con `docs/gestion/README.md` § Definición de terminado):

1. Corre automáticamente en CI, no solo en la máquina de quien la escribió.
2. Falla de forma clara cuando el comportamiento que protege se rompe (se verifica rompiéndolo a
   propósito una vez, antes de dar la tarea por terminada).
3. Está referenciada por su nombre en `registro-de-implementaciones.md` (columna "Prueba"), no como
   "probado manualmente".

---

## 6. Lo que este documento NO es

- **No es el informe de resultados.** Cobertura real, bugs encontrados, resultados de E2E: eso se
  redacta en Sprint 5–6 desde los registros, con fecha y evidencia.
- **No fija herramientas que dependen de una decisión pendiente** (p. ej. la herramienta de prueba de
  carga para RNF002 — "k6 o similar" — se confirma cuando exista `/backend` real contra qué probar).

## 7. Siguiente paso

Cuando exista `/backend` con al menos un caso de uso (Sprint 1-2): escribir los primeros tests
unitarios reales y verificar que la fila de JaCoCo/ArchUnit de la matriz corre en CI de verdad, no solo
en el papel.

## 8. Verificación de flujos HTTP (Fase 5)

Corrida de `scripts/verificar-flujos.mjs` (2026-09-24) contra un entorno **construido desde cero**: copia del repo sin
`.env` ni datos previos, `cp .env.example .env` con `JWT_SECRET`, hash del ADMIN y correo, `docker compose up -d
--build` (Mongo como *replica set*, Redis, MailHog y API), `sembrar-sectores.mjs`, y una sola pasada del script.

| Área | Pasos | Resultado |
|---|---|---|
| Mapa | readiness, 211 sectores, geometría, sector y 404 RFC 7807, CORS (permite 5173, rechaza origen ajeno) | ✔ |
| Suscripción | alta `PENDIENTE_CONFIRMACION`, correo en MailHog, confirmación por `POST`, aviso al cambiar el sector, baja | ✔ |
| Reporte y consenso | 6 reportes de dispositivos distintos → `SIN_SERVICIO`, reporte por coordenada infiere sector, foto, confirmación, aviso SSE | ✔ (la foto falló 500 hasta corregir `BUG-101`) |
| Historia pública | bitácora paginada por cabeceras y sustento, estadísticas, CSV, cumplimiento, histórico de cortes, Open311 | ✔ |
| Panel | 401 sin token, login del ADMIN con alta de TOTP, `yo`, corte oficial (crear/cerrar), moderación, ingesta, invitación de una cuenta y su login, 403 del OBSERVADOR al gestionar cortes, auditoría, cierre de sesión revoca | ✔ |

**21 pasos, 0 fallos**, y una segunda pasada inmediata con el TOTP ya dado de alta también 21/0. Hallazgos de la fase: `BUG-101`
(volumen de fotos como `root`), CORS cerrado en el perfil `docker` (corregido, `CorsPorPerfilTest`) y la trampa del `$` del
hash en el `.env` (documentada). **No cubre** el envío real de WhatsApp/Telegram (`RF041`), TLS/proxy de producción ni la carga
de 50 000 usuarios (`ADR-057`). Un fallo de revocación de sesión visto una vez en una repetición con estado sucio
(`token` aún válido tras `cierre`) no se reprodujo en tres intentos posteriores. **Explicación probable (2026-09-24, no reproducida a
propósito):** `JwtAuthenticationFilter.sigueVigente` compara `iat` y la marca de revocación truncadas a segundos y acepta
**a propósito** que un token emitido en el mismo segundo de la revocación sobreviva (su comentario lo explica). El script hace el
login con TOTP al inicio del panel, y si todos los pasos hasta el cierre caben en ese mismo segundo, el paso «cerrar sesión revoca
el token» falla sin que el sistema esté mal. Volvió a verse el 2026-09-24 en una primera corrida, contra una imagen anterior a los
PR #48 y #49. Arreglo pendiente y barato: que el script espere algo más de un segundo antes del cierre.

### Corrida desde una copia limpia sobre `main` (2026-09-24, Sprint 6)

`git archive` de `origin/main` (`488bb6c`) en una carpeta nueva **sin `.env` ni datos**, proyecto de Docker y volúmenes nuevos
(`docker compose -p … up -d --build --wait`), `npm install` y `sembrar-sectores.mjs` en la copia, y una sola pasada de
`scripts/verificar-flujos.mjs`: **21 pasos, 0 fallos a la primera** (incluido CORS, la foto y el cierre de sesión).
- Sin `.env` en la carpeta, el backend arranca sano pero **no siembra ADMIN** y lo avisa en el log («Sin ADMIN_INICIAL_CORREO o
  VEEDOR_PASSWORD_HASH…»). `docker compose --env-file` **no basta**: alimenta la interpolación del compose (por eso llegó
  `ADMIN_INICIAL_CORREO`) pero no el `env_file: .env` del servicio, así que `JWT_SECRET` y el hash llegaron vacíos. Hace falta
  el `.env` dentro de la carpeta del compose y recrear el backend.
- La copia usó el `.env` del dueño (mismos `JWT_SECRET` y hash), no uno generado desde `.env.example`.
- El directorio de fotos del contenedor pertenece a `aguavigia` (con `BUG-101` corregido, volumen nuevo).
