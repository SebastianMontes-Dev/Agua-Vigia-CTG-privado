# Documentación de AguaVigía CTG

Guías por tema, escritas conforme se valida cada parte del backend y contrastadas con el código. Un dato vive en un solo archivo.
Última revisión completa: 2026-10-15 (hora de Cartagena).

| Guía | Estado |
|---|---|
| [01 · Guía paso a paso: levantar y recorrer el backend](01-levantar-a-mano.md) | Hecha; partes B a D re-ejecutadas contra `main` el 2026-10-15 |
| [02 · Arquitectura](02-arquitectura.md) | Hecha |
| [03 · Base de datos](03-base-de-datos.md) | Hecha (MongoDB, Redis y la copia analítica en PostgreSQL) |
| 04 · Flujos | Cubierto en la parte D de la guía 01 y en [`api/flujos-de-usuario.md`](api/flujos-de-usuario.md) |
| [05 · Seguridad y claves](05-seguridad-y-claves.md) | Hecha; pendientes en §5 |
| [06 · Ética de datos](06-etica-de-datos.md) | Hecha; pendientes en §3 |
| [07 · Decisiones clave](07-decisiones-clave.md) | Hecha (ADR-001 a 100; ojo con la nota de numeración) |
| 08 · Guion de la presentación | Pendiente |
| [09 · PostgreSQL, fase 1](09-postgresql-fase-1.md) | **Ejecutada y cerrada** (2026-10-15; CI verde el 2026-10-18): copia analítica del Índice de Cumplimiento; fases 2 y 3 sin hacer |
| [Guía para Yordy](frontend/guia-para-yordy.md) | **Empieza aquí si trabajas el frontend** (2026-10-18): estado de hoy, todo lo que el backend ya hace, entorno y plan fase por fase |
| [Plan del frontend (FE0–FE7)](frontend/README.md) | Plan del 2026-10-06; en curso (Yordy), actualizado el 2026-10-18 con las rutas de PostgreSQL |
| [Prompt de auditoría de cierre del backend](prompts/auditoria-final-backend.md) | Actualizado el 2026-10-18 (ADR-100 y PostgreSQL); listo para usar en una sesión nueva, aún sin ejecutar |

**Arranque único.** `docker compose up` levanta todo, carga los 211 barrios y el backend crea las cuentas sintéticas (ADR-094); la guía 01 hace lo mismo paso a paso.

**Simulación.** `docker compose --profile simulacion up -d --build` levanta una segunda instancia (API en 8082, base `aguavigia_sim`) para ensayar el sistema con un guion
acelerado; no toca la instancia real. `node scripts/simulacion/preparar.mjs` genera su clave en `.env`; luego
`docker compose --profile simulacion run --rm simulador iniciar --velocidad 300` corre el día simulado completo (≈ 3 min) y comprueba las aserciones del guion.
Detalle en [`scripts/simulacion/README.md`](../scripts/simulacion/README.md). Los sensores IoT están construidos pero **inactivos y fuera de la simulación**.

**Carga.** La prueba de carga se mide en un proyecto Docker aparte y sus cifras son de un PC compartido: ADR-097 §1 y [`scripts/carga/README.md`](../scripts/carga/README.md).

Referencia para el frontend (no se toca sin Yordy): [`api/`](api/) (contrato, flujos, errores) y [`diseno/`](diseno/).
La documentación anterior está en el historial: `git show pre-limpieza-docs:<ruta>`.

## Integrantes

- Sebastián Andrés Montes Olivera
- Yordy Pardo Pájaro
- Carlos Mario Bechara Arias
- Rafael Eduardo Sarmiento Peña
- Jose Daniel Zambrano Luna
