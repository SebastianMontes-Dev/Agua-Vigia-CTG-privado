# Sprint 1 — Mapa base y dominio core

**Abierto:** 2026-08-08 · **Cerrado:** 2026-08-09 — el mapa consume `GET /api/sectores` real, sin
un solo dato de demostración en el código (datos simulados retirados, verificado contra el código,
no contra el registro)

> **Este sprint abre con la mayor parte de su alcance ya entregada.** No es un error de planificación:
> es la consecuencia de que el Sprint 0 tardara tres días en cerrar formalmente mientras el trabajo
> seguía avanzando. La hoja de ruta original asignaba cinco frentes al Sprint 1 y cuatro ya estaban
> integrados. El planning honesto no es fingir que empiezan hoy, sino **decir qué queda y qué hay que
> limpiar**.

---

## 1. Objetivo del sprint

**Que un vecino de Cartagena abra el mapa y vea el estado real de su sector, servido por la API y sin
un solo dato de demostración en el camino.**

Hoy el mapa ya consume la API real y, cuando algo falla, cae a datos simulados **avisándolo en
pantalla**: `PaginaMapa.tsx:55-66` muestra "Sin conexión · Simulación" o "Acuacar inactivo ·
Simulación", y `PaginaBitacora.tsx:153-183` hace lo propio en rojo. Esa señalización es correcta y
respeta la ética de datos del proyecto (`CLAUDE.md`, punto 4): el mapa no afirma como verificado lo
que no lo es.

Lo que queda es retirar los datos simulados en sí. Eran andamio provisional que debía caducar al
cerrar este sprint, y ya no hay motivo para conservarlos: el contrato OpenAPI y la SPA integrada ya
existen. Un modo demo que sobrevive a lo que lo justificaba deja de ser un andamio y pasa a ser
código muerto que alguien confundirá con producción.

---

## 2. Compromisos

| RF/RNF | Entregable | Depende de |
|---|---|---|
| RF012–RF014 | ✅ Entregado — `POST /api/suscripciones` con DTOs y envío de correo asíncrono (`@Async` + `JavaMailSender`) contra Mailhog, probado extremo a extremo contra Mailhog real | Dominio y puertos ✅ · plantillas HTML ya listas, sin fusionar · PR #78 |
| RF001–RF004 | ✅ Entregado — datos de demostración retirados de `useDatosEnVivo.ts`, `PaginaVeedor.tsx` y `PaginaBitacora.tsx` (commits, PR #85); `BUG-033` (S1, `ListaSectores.tsx`) encontrado y cerrado en el mismo frente | Contrato OpenAPI ✅ · SPA integrada ✅ |
| RF005–RF007 | ✅ Entregado — `RegistrarReporteService` en `application/`, primer caso de uso real del proyecto. `BUG-032` (RF006 no cubierto pese al comentario) encontrado por otra sesión y cerrado en el mismo frente | Dominio y puertos ✅ · Review del Sprint 0 ✅ · PR #84 |
| RNF | ✅ Entregado — motor de contenedores instalado, **entorno reproducible reverificado levantando el entorno de verdad**, comando de verificación corregido (`docker compose up -d --wait && ./mvnw clean verify`) | — *(no depende de nada)* · PR #74 |
| RF029–RF031, RF036 | ✅ Entregado, más allá de lo comprometido — `AcuacarApiCollector` y `RssCollector` (M9), desbloqueados al confirmarse el correo de contacto del colector. La capa de IA (RF032–RF035) se descartó (`ADR-025`) | Correo de contacto del colector ✅ · PR #98 |

La columna **Depende de** es la importante: es donde se ve qué tiene que existir antes. Se escribe
con el artefacto concreto que falta, no con una intención.

### Ya entregado antes de abrir el sprint

| Frente | Dónde | Estado |
|---|---|---|
| Script de siembra del GeoJSON en Mongo | PR #13 | ✅ 211 sectores |
| Entidades, VOs, puertos y ArchUnit | PR #21 | ✅ Dominio y puertos listos |
| Adaptador Mongo, `GET /api/sectores`, OpenAPI | PR #56 | ✅ Contrato OpenAPI publicado |
| Mapa Leaflet y lista accesible, integrados contra la API real | PRs #12, #67 | ✅ SPA integrada |

**Cuatro de los cinco frentes del sprint ya estaban cerrados.** Lo que quedaba era el frente de
suscripciones por correo y la limpieza de los datos simulados.

---

## 3. Obstáculos del sprint — resumen

| Qué detuvo el avance | De qué dependía | Días | Cómo se resolvió |
|---|---|---|---|
| Arranque de la lógica de consenso y reportes | Cierre formal del Sprint 0 | 3 | Cerrado 2026-08-08 con el Review del Sprint 0 (`sprint-0.md` §4) |
| Colectores de ingesta M9 | Un correo real de contacto para el `User-Agent` | 0 | Cerrado 2026-08-08 — se configuró el correo de contacto en `.env.example` |

---

## 4. Review — qué se demostró funcionando

| RF/RNF | Qué se demostró | ¿Aceptado? |
|---|---|---|
| RF001–RF004 | Mapa y lista accesible consumiendo `GET /api/sectores` real; `SECTORES_MOCK`/`MOCK_EVENTOS` retirados de todo el árbol de frontend, verificado leyendo el código, no el registro | ✅ |
| RF012, RF013 (parcial) | `POST /api/suscripciones` → 201 → correo de doble opt-in recibido en Mailhog real, con asunto, sector y token correctos. Confirmación del token (`GET /api/suscripciones/confirmar`) y baja en 1 clic (RF015) no se hicieron — quedan para Sprint 2, tal como estaba planeado | 🟡 Parcial |
| RF005–RF008 (parcial) | `RegistrarReporteService` real en `application/`, con RF006 funcionando (límite de reportes por dispositivo, `429` vía `LimiteReportesExcedidoException`). Sin `POST /api/reportes`: la API queda cerrada a propósito hasta Sprint 2 (el contrato OpenAPI solo cubría `/api/sectores`) | 🟡 Parcial |
| RF029–RF031, RF036 | `AcuacarApiCollector` y `RssCollector` reales (M9), sobre la deduplicación y el prefiltro que ya existían. RF032–RF035 (clasificación con IA) se descartaron (`ADR-025`) | ✅ |
| RNF010, RNF011, RNF017, RNF018, RNF020 | Verificados contra el código: `gitleaks` activo en CI, JWT expira a las 8h exactas, cobertura `domain/` 74,3% y `application/` 100% (≥70% exigido), `ArchUnit` falla la build ante una violación real, `docker compose up -d --wait` levanta los 5 servicios y pasa el build completo con Testcontainers | ✅ |

**Comprometido:** 5 frentes de trabajo · **Entregado:** 5/5, dos con alcance reducido a propósito
(M2 y M4 completos solo hasta donde el contrato publicado permitía avanzar)
· **Arrastrado al Sprint 2:** `POST /api/reportes`, confirmación de suscripción + baja en 1 clic,
`EvaluarConsensoUseCase` (M3).

---

## 5. Métricas del sprint

| Métrica | Valor |
|---|---|
| Requisitos entregados / comprometidos | 5/5 frentes comprometidos entregados; 10/36 RF funcionales de punta a punta (28%, `registro-de-implementaciones.md` § Estado de cobertura) |
| PRs fusionados | 25 durante el sprint (#74–#99, uno cerrado sin fusionar por choque con trabajo paralelo) |
| Bugs abiertos / cerrados | 11 abiertos / 21 cerrados (33 registrados en total, `BUG-034` es el siguiente número) |
| Cobertura `domain/` + `application/` | `domain/` 74,3% · `application/` 100% — ambas superan el 70% que exige `RNF017` *(al abrir: `application/` solo tenía `package-info.java`, 0%)* |
| Build en verde al cierre | ✅ Sí — backend 110/110 pruebas (Testcontainers real, Colima), ArchUnit incluido · frontend `npm run build` y `npm test` (12/12) en verde |

---

## 6. Retrospectiva

**Qué funcionó**

1. Adelantar un frente atrasado en vez de esperar evitó que `application/` y el retiro de mocks se
   quedaran detenidos — dos casos de uso reales y RF006 real salieron el mismo día en que se
   identificó el atraso.
2. Verificar contra el código en vez de contra el registro encontró trabajo ya hecho que la
   documentación no reflejaba (dos excepciones temporales ya resueltas sin actualizar la tabla) y
   trabajo declarado que no existía (el javadoc de `RegistrarReporteService` decía que RF006 estaba
   cubierto; no lo estaba).
3. Testcontainers real (Colima, `BUG-030`) hizo que el build local coincidiera con el de CI —
   ninguna sorpresa al fusionar en todo el sprint.

**Qué no funcionó**

1. Varias sesiones de IA trabajando en el mismo repositorio sin coordinarse entre sí produjeron
   trabajo duplicado real: el retiro de mocks del frontend se hizo dos veces (PR #85 y el PR #86,
   cerrado sin fusionar por el choque) y casi colisionó la numeración de `BUG-032`.
2. La tabla "Estado de cobertura de requisitos" llevaba dos sprints en 0% sin que nadie la llenara:
   un dato que existía en el código pero no en el registro no contaba como avance para nadie que
   mirara la Sala de control.

**Acciones para el próximo sprint**

| Acción | Para cuándo |
|---|---|
| Revisar issues/PRs recientes antes de tomar una tarea, para evitar trabajo duplicado entre sesiones | Desde ya, Sprint 2 |
| Actualizar "Estado de cobertura de requisitos" en cada review de sprint, no solo cuando alguien se acuerda | Cada cierre de sprint |
