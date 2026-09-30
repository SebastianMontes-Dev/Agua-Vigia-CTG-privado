# Ensayo de sustentación en limpio — 2026-09-29

> Evidencia de los cuatro requisitos obligatorios de la entrega, ejecutados como los ejecutaría el profesor: un clon nuevo
> del repositorio (rama `docs/barrido-contradicciones`, con los PR #107–#111), **sin `.env`** y con volúmenes nuevos, en el
> PC del dueño (Windows 11, Docker Desktop, 12 hilos). Las claves del ADMIN no se copian aquí.

## 1. Un solo comando

| Paso | Resultado |
|---|---|
| `docker compose up --build` | Mongo, Redis, MailHog y backend sanos; el `sembrador` terminó con código 0 |
| Tiempo hasta `Datos listos` | **58 s** (imágenes con capas en caché; la primera construcción descarga Maven y npm) |
| Salida del sembrador | 211 sectores · 30 000 cuentas · 4 de 4 barrios cambiados por consenso real · histórico mayo–julio |
| `GET /actuator/health/readiness` · Swagger · MailHog | `UP` · 200 · 200 |
| Login del ADMIN con la clave del log | 200, alcance `ALTA_SEGUNDO_FACTOR` |
| Segundo `docker compose up` | usuarios/reportes/cortes `30001/612/120` antes y después: no duplica |

## 2. Al menos 30 000 registros en Mongo (base principal)

`docker compose run --rm sembrador verificar`: `usuarios` **30 001** (≥ 30 000 OK), `sectores` 211, **121 631 documentos** en
total entre todas las colecciones. De los usuarios, 30 000 son cuentas de demostración sintéticas.

## 3. Usuarios nuevos en vivo (faker)

`docker compose run --rm sembrador agregar-usuarios --cantidad 1000`: `aguavigia.usuarios` 30 001 → **31 001**, con tokens,
auditoría y suscripciones coherentes y tres documentos impresos tal como quedaron. `--borrar-lote` devolvió 30 001 exacto.

## 4. Flujo real con muchos usuarios a la vez

`node scripts/carga/demo.mjs --proyecto ef --usuarios 3000 --ventana 30 --conectados 2000 --registros 1500 --sin-correo --restaurar`:

| Qué | Resultado |
|---|---|
| Umbrales de k6 | **Todos cumplidos** |
| Reportes | 3 000 / 3 000 aceptados · p95 215 ms · 0 errores · 53 barrios cambiaron de estado |
| Registros de cuentas por la API | 1 500 / 1 500 aceptados · p95 256 ms · aparecen en `usuarios` como `PENDIENTE_VERIFICACION` |
| Mapa en vivo (SSE) | 2 000 / 2 000 conexiones abiertas de principio a fin · 52 000 avisos entregados |
| Otros | 61 sesiones de veedor · 31 suscripciones · 150 lecturas/s |
| Backend (pico) | ≈ 8,9 núcleos · 1 GiB (el generador comparte el PC) |
| `--restaurar` | Mongo y Redis vuelven al estado de antes (usuarios 30 001, reportes 612) |

`docker compose run --rm sembrador monitor` muestra la base creciendo cada segundo durante la carga. Cifras más grandes y sus
límites: [`docs/ingenieria/escalabilidad.md`](../../../ingenieria/escalabilidad.md).

## Pruebas automáticas

`./mvnw verify` en la misma rama: **1 109 pruebas, 0 fallos**, JaCoCo en verde (domain 91,1 %, application 97,7 %).
`cd scripts && npm test`: 5 pruebas del generador de cuentas. El CI (`contenedores-ci.yml`) repite en cada PR el arranque sin
`.env`, los mínimos, la idempotencia y el lote de faker.
