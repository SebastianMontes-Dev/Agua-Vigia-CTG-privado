# Bitácora de sesiones — Sprint 1 (rotada)

Entradas rotadas desde `docs/gestion/bitacora-sesiones.md` el 2026-09-21 (la bitácora superó las 30 entradas; el Sprint 1 está cerrado).

### 2026-08-09 · `rediseno-local`
**Qué:** Rediseñado el shell responsive con sidebar, topbar contextual y mapa prioritario (`ADR-022`); respaldo previo guardado en `frontend_checkpoint_2026-08-09_antes_adminator.zip`.
**Sigue:** Validación visual del usuario; si no se aprueba, restaurar el checkpoint sin tocar funcionalidad.

### 2026-08-09 · `rediseno-local`
**Qué:** Corregido `BUG-041`: restaurada en ambos temas la paleta sobria de `DESIGN.md` y retirados los halos morados, sin cambios funcionales.
**Sigue:** Resolver las dependencias, exports y tipos preexistentes que impiden ejecutar build y toda la suite del frontend.

### 2026-08-09 · `codex/frontend-hardening`
**Qué:** Endurecido el frontend contra `BUG-017`, `BUG-034`–`BUG-038`: API tipada y del mismo origen,
suscripciones reales, estados sin simulación, mapa primero, rutas 404 y 23 pruebas en verde.
**Sigue:** Abrir PR; publicar los contratos de reportes, bitácora, estadísticas
y moderación antes de habilitar esas pantallas.

### 2026-08-09 · `docs/cerrar-sprint-1`
**Qué:** Reverificado el entorno con motor real (Colima, `BUG-030`, PR #74). Entregado
`POST /api/suscripciones` (M4, PR #78), `RegistrarReporteService` (PR #84) con RF006 real
(`BUG-032`, PR #89) y `BUG-033` (reportes inventados en `ListaSectores.tsx`). Cerrado formalmente el
**Sprint 1**: los 5 frentes comprometidos entregados, cobertura real medida (`domain/` 74%,
`application/` 100%), 110/110 pruebas backend y 12/12 frontend en verde (PR #100).
**Sigue:** Sprint 2 sin abrir todavía — arrastra `POST /api/reportes`, confirmación de suscripción +
baja en 1 clic y `EvaluarConsensoUseCase` (M3). Nota de proceso: varias sesiones trabajaron en
paralelo sin coordinación previa — produjo trabajo duplicado real, ver retrospectiva de `sprint-1.md` §6.

### 2026-08-08 · `docs/cierre-sprint-0-y-planning-sprint-1`
**Qué:** **Review del Sprint 0** reverificando cada entregable con su comando (entorno, dominio y
puertos, contrato OpenAPI y SPA integrada, todos abiertos) y **Planning del Sprint 1**, que arranca
con 4 de sus 5 frentes ya entregados. Hallazgo del Review: la máquina de trabajo tiene el **cliente**
de Docker pero ningún motor, así que `docker compose config -q` —el comando que definía el entorno
reproducible— solo valida YAML y nunca probó que el entorno levante; el sprint se aceptó con la
salvedad escrita, no oculta.
**Sigue:** Instalar un motor de contenedores y reverificar el entorno de verdad; retomar el PR de
las plantillas de correo de M4, que sigue sin fusionar en `feature/dockerfile-frontend-y-jacoco`.
