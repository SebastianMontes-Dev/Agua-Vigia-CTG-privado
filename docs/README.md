# Documentación de AguaVigía CTG

Guías por tema, escritas conforme se valida cada parte del backend. Un dato vive en un solo archivo.

| Guía | Estado |
|---|---|
| [01 · Guía paso a paso: levantar y recorrer el backend](01-levantar-a-mano.md) | Hecha y verificada (levantado, primer ingreso, flujos, base de datos) |
| 02 · Arquitectura | Pendiente |
| 03 · Base de datos (`modelo-nosql-moon-modeler.dmm`) | Pendiente |
| 04 · Flujos | Cubierto en la parte D de la guía 01 |
| [05 · Seguridad y claves](05-seguridad-y-claves.md) | Hecha; pendientes anotados en §5 |
| 06 · Ética de datos | Pendiente |
| [07 · Decisiones clave](07-decisiones-clave.md) | Hecha |
| 08 · Guion de la presentación | Pendiente |
| [Plan del frontend (FE0–FE7)](frontend/README.md) | Plan del 2026-10-06; en curso |
| Arranque único con `docker compose up` | Hecho; el detalle está en el ADR-094 de [07 · Decisiones clave](07-decisiones-clave.md) |
| Simulación del sistema | Hecha; guía en [`scripts/simulacion/README.md`](../scripts/simulacion/README.md) |
| Carga y cierre de F6 | Hecho; ADR-097 de [07 · Decisiones clave](07-decisiones-clave.md) |

**Simulación.** `docker compose --profile simulacion up -d --build` levanta una segunda instancia (API en 8082, base `aguavigia_sim`) para ensayar el sistema con un guion
acelerado; no toca la instancia real. `node scripts/simulacion/preparar.mjs` genera su clave en `.env`; luego
`docker compose --profile simulacion run --rm simulador iniciar --velocidad 300` corre el día simulado completo (≈3 min) y comprueba las aserciones del guion. Los sensores IoT están
construidos pero **inactivos y fuera de la simulación**.

**Carga.** La prueba de carga se mide en un proyecto Docker aparte y sus cifras son de un PC compartido (ADR-097 §1). ADR-097: cierre de F6 (carga medida, métricas de
calibración y revisiones de seguridad y de dominio).

Referencia para el frontend (no se toca sin Yordy): [`api/`](api/) (contrato, flujos, errores) y [`diseno/`](diseno/).
La documentación anterior está en el historial: `git show pre-limpieza-docs:<ruta>`.
