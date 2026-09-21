# Sprint 0 — Preparación e infraestructura

**Abierto:** 2026-08-06 · **Cerrado:** 2026-08-08 *(entregable demostrado: el repositorio se clona,
el entorno se declara con un comando y se puede escribir código de cualquier capa sin preguntar)*

> Este archivo se creó tarde, en la auditoría del 2026-08-07, cuando el sprint ya llevaba dos días
> corriendo. Su ausencia tuvo un costo medible: la creación de `/backend` —lo único que bloqueaba el
> resto del trabajo— pasó dos sesiones sin planificar porque no hubo planning donde asignarla.

---

## 1. Objetivo del sprint

**Que el repositorio se pueda clonar, levantar el entorno con un comando y empezar a escribir código
de cualquier capa sin fricción.**

No entra funcionalidad. El Sprint 0 no se mide en requisitos entregados, sino en si el Sprint 1 puede
arrancar sin fricción. Qué se permite escribir y qué no: `ADR-009`.

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de | Estado |
|---|---|---|---|
| — | Repositorio, ramas, `.env.example`, plantillas de PR e issue | — | ✅ PR #1 |
| — | `docker-compose.yml` (Mongo + Redis + Mailhog) y 3 workflows de CI | — | ✅ PR #1 |
| — | GeoJSON de los 213 barrios, validado contra boletines reales | — | ✅ PRs #2 y #6 |
| — | **Verificar que el entorno reproducible levanta** | — *(ya no depende de nada)* | ✅ Verificado 2026-08-08 — de paso se encontró y corrigió `BUG-003` (`docker compose config -q` fallaba sin `.env`) |
| — | **Proyecto base de `/backend`**: Maven, Java 21, Spring Boot 3.4.1, paquetes vacíos de Arquitectura Limpia | — | ✅ PR #10 |
| — | Diseño del dominio de M3/M6 en `docs/ingenieria/modelo-de-dominio.md` | — | ✅ PR #4 |
| — | Esqueleto de `/frontend`: React 19 + Vite + TS + Tailwind, tokens de `DESIGN.md`, rutas vacías | — | ✅ PR #5 |
| — | Motor de contenedores instalado y `docker compose up` corriendo | — | ✅ Cerrado 2026-08-08 |
| — | Andamiaje de infraestructura: dependencias de build (`pom.xml`), `RedisConfig` y paquetes vacíos de `infrastructure/` | Dominio y puertos *(abiertos 2026-08-08)* | ✅ PR #40 — sus entregables de código (adaptador Mongo, `GET /api/sectores`, OpenAPI) son del Sprint 1 |
| — | Dockerfile multi-etapa backend y frontend, JaCoCo en CI, perfiles de Spring, `/actuator/health` | — | ✅ PRs #27 y #33 |

La columna **Depende de** es la importante: es donde se ve qué tiene que existir antes. Se escribe
con el artefacto concreto que falta, no con una intención.

### Camino crítico — resuelto

```
✅ /backend base (PR #10)  →  ✅ entorno verificado (2026-08-08)  →  dominio  →  puertos  →  infraestructura  →  frontend integrado
```

**El entorno está verificado.** El dominio y los puertos se pueden empezar sin esperar nada más.

---

## 3. Obstáculos del sprint — resumen

| Qué detuvo el avance | De qué dependía | Días | Cómo se resolvió |
|---|---|---|---|
| Protección de ramas en GitHub | Un rol de administración en el repo | 0 | Descartada como control técnico, política documentada (`ADR-010`) |
| Integrar el frontend con el entorno Docker | Motor de contenedores instalado | 1 | Cerrado 2026-08-08 — se instaló Docker Desktop y se verificó el entorno |
| Datos simulados provisionales en el frontend | Contrato OpenAPI publicado | — | Regularizados el 2026-08-08 con caducidad al cerrar el Sprint 1; retirados entonces (`sprint-1.md`) |

---

## 4. Review — qué se demostró funcionando

Solo lo que se pudo **mostrar corriendo**. Cada comando se corrió de nuevo el **2026-08-08** sobre la
rama principal. Dos filas quedan aceptadas **por evidencia de CI y no por verificación local**: están
marcadas como tales y el motivo se explica bajo la tabla.

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|
| — | `docker compose config -q && ls backend frontend` → sale limpio: `.env.example`, plantillas de PR/issue, 3 workflows de CI (PR #1) | ✅ Reverificado 2026-08-08, exit 0 |
| — | GeoJSON de los 213 barrios de Cartagena, contrastado contra boletines reales de Acuacar (PRs #2, #6) | ✅ |
| — | `docs/ingenieria/modelo-de-dominio.md`: diseño de dominio de M3/M6 (PR #4) | ✅ |
| — | `/frontend`: React 19 + Vite + TS + Tailwind, temas claro/oscuro, 4 rutas — `npm run build` en verde (PR #5) | ✅ Reverificado 2026-08-08 tras `npm ci`, build en 450 ms |
| — | `/backend`: Maven, Java 21, Spring Boot 3.4.1, Arquitectura Limpia vacía — `./mvnw verify` → BUILD SUCCESS (PR #10) | ✅ |
| — | **Entorno reproducible verificado** 2026-08-08 — `docker compose config -q && ls backend frontend` en verde | ✅ **con salvedad** — ver nota 1 |
| — | Población real por barrio (DANE 2018 + CORVIVIENDA) sembrada en Mongo — 211 sectores, `$geoIntersects` verificado (PR #13) | ✅ por CI y por la verificación contra Mongo real (PR #56); no se pudo reproducir en local — ver nota 1 |
| — | Dockerfiles multi-etapa (backend + frontend), JaCoCo en CI, perfiles de Spring, `/actuator/health` → `mongo: UP` (PRs #27, #33) | ✅ por CI; el `health` no se pudo reproducir en local — ver nota 1 |
| — | Andamiaje de infraestructura: dependencias de build, `RedisConfig`, paquetes vacíos — `./mvnw verify` en verde (PR #40) | ✅ |
| — | **Dominio y puertos disponibles** (PR #21) — `ls backend/.../domain/port/out` en verde, ArchUnit incluido | ✅ Reverificado 2026-08-08: 6 puertos presentes |
| — | **Contrato OpenAPI publicado** (PR #56) — `git show main:backend/openapi.yaml \| head -5` responde | ✅ Reverificado 2026-08-08: OpenAPI 3.0.1 |
| — | **SPA integrada contra la API real de sectores** (PR #67) — `cd frontend && npm run build` | ✅ Reverificado 2026-08-08 |

**Nota 1 — la máquina de trabajo no tenía motor de contenedores, y eso debilitaba el propio comando de
verificación del entorno.** Descubierto el 2026-08-08 al reverificar: `docker` estaba instalado como
**cliente** (Homebrew), sin Docker Desktop, colima ni podman. `docker compose config -q` pasa igual
porque **solo valida el YAML; no levanta nada ni habla con un daemon**. Consecuencia: el comando que
definía "entorno reproducible" nunca probó que el entorno arranque. Por eso `./mvnw clean verify`
local dio **60 pruebas, 0 fallos y 6 errores**, todos de Testcontainers por ausencia de daemon
(`SectorMongoAdapterTest`, `RedisContadorReportesAdapterTest`, `DeduplicadorRecienteTest`,
`RateLimitingInterceptorTest`, `RateLimitConfigTest`, `CacheConfigTest`), no por el código: los mismos
tests pasan en Backend CI, que sí tiene daemon (última ejecución, PR #61, en verde). Se acepta el
sprint con esta salvedad explícita en vez de ocultarla, y se corrige en el Sprint 1 (acción de la
retrospectiva).

**Lo que se demostró de más, fuera del alcance formal del Sprint 0** (evidencia de que el objetivo del
sprint —"empezar a escribir código de cualquier capa sin fricción"— ya se cumple en la práctica): se
fusionaron 6 PRs de infraestructura de Sprint 1 a Sprint 5 (`#56`–`#61`) y otros tantos de frontend
hasta el `#69`, incluida la integración real del mapa contra `GET /api/sectores` y la PWA instalable.
Sigue clasificado en `andamio`/`infra`, no `func`, según `registro-de-implementaciones.md`.

**Comprometido:** 0 requisitos *(por diseño — `ADR-009`)* · **Entregado:** 0 · **Arrastrado:** —
La cobertura de requisitos sigue en 0% hasta que se conecten casos de uso reales en `application/`,
que es exactamente lo que este Review desbloquea.

---

## 5. Métricas del sprint

| Métrica | Valor |
|---|---|
| Requisitos entregados / comprometidos | 0 / 0 — el Sprint 0 no entrega requisitos |
| PRs fusionados | **61** al cierre (2026-08-08), `gh pr list --state merged --json number --jq 'length'` |
| Bugs abiertos / cerrados | 0 / 15 — todos los registrados durante el sprint quedaron cerrados |
| Cobertura `domain/` + `application/` | `domain/` tiene 16 archivos (entidades, VOs, puertos) desde el PR #21; `application/` sigue con solo `package-info.java`. No es anomalía: lo entregado es lectura sin regla de negocio y va directo al puerto por `ADR-015`. Los casos de uso llegan en el Sprint 1 |
| Build en verde al cierre | Frontend CI ✅ · Backend CI ✅ (PR #61, último que tocó `backend/**`) · Escaneo de secretos ✅ · `npm run build` y `./mvnw verify` reverificados a mano el 2026-08-08 (ver nota 1 del §4) |

---

## 6. Retrospectiva

Cerrada en el Review del 2026-08-08.

**Qué funcionó**

1. **Detenerse en vez de inventar el insumo que faltaba.** Dos tareas del pipeline M9 no podían tocar
   la red todavía (faltaban una clave de API y un correo de contacto real) y se construyó todo lo que
   no la cruzaba —normalización, prefiltro, deduplicador— en lugar de fingir un colector. Es lo
   contrario de lo que suele pasar.
2. **Verificar antes de afirmar.** Los obstáculos se registraron con el comando y su salida real. Esa
   costumbre es la que encontró que la máquina de trabajo no tenía motor de contenedores (nota 1,
   §4): un supuesto que llevaba dos días en pie sin que nadie lo probara.
3. **Los datos simulados provisionales se regularizaron con caducidad**, en vez de quedar como mocks
   huérfanos que nadie retira.

**Qué no funcionó**

1. **La ceremonia se volvió el cuello de botella del proyecto.** El Sprint 0 estuvo tres días sin
   cerrar por una firma que faltaba, no por trabajo pendiente. Mientras tanto se entregaron 29 PRs
   de sprints posteriores que, formalmente, `ADR-009` no permitía. **El proceso quedó detrás de la
   realidad y el registro tuvo que clasificar como `andamio` lo que ya era funcionalidad.**
2. **El comando de verificación del entorno no probaba lo que prometía.** `docker compose
   config -q` valida YAML; no levanta el entorno. "Entorno reproducible" nunca se demostró
   reproduciéndolo.

**Acciones para el próximo sprint**

| Acción | Para cuándo |
|---|---|
| Instalar un motor de contenedores (Docker Desktop o colima) y **reverificar el entorno levantándolo de verdad**, con `docker compose up -d` y `./mvnw verify` completo sin errores de Testcontainers | Día 2 del Sprint 1 |
| Usar un comando de verificación del entorno que arranque los servicios, no que solo lea el YAML | Con la acción anterior |
