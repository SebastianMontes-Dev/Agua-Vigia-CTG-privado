# AguaVigía CTG — Instrucciones del proyecto

> Este archivo lo lee el agente automáticamente al abrir el proyecto. Es la fuente de verdad sobre
> **cómo se trabaja aquí**. Si algo de este archivo contradice una suposición, gana este archivo.

---

## Qué es este proyecto

Plataforma web ciudadana de monitoreo y trazabilidad del acueducto en **Cartagena de Indias,
Colombia**. Cruza los avisos oficiales de Acuacar con reportes ciudadanos georreferenciados y publica
un **Índice de Cumplimiento** que compara la duración prometida de cada corte con la real.
**Proyecto personal**, de un solo desarrollador. Detalle en `docs/brief.md`.

**El problema que resuelve no es hidráulico, es informativo.** No reparamos tuberías; cerramos el
vacío de información que multiplica el daño. Toda decisión de alcance se juzga contra eso.

---

## Estado actual

**Sprints 0 a 6 cerrados** (el 6, «entrega final», el 2026-09-24, con `RNF027` parcial: `docs/gestion/sprint-6.md`). M1–M15 están construidos en el backend. **El frontend se retiró de `main`
(`ADR-048`; su código sigue en la etiqueta git `pre-retiro-frontend`) y se rehace en `frontend/`** con el stack de
`ADR-067`, por fases (F0–F6): `docs/ingenieria/plan-frontend.md`. **Es un proyecto académico que corre en local**
(`ADR-057`): sin hosting, dominio ni CDN. Requisito de escalabilidad: **50 000 usuarios simultáneos** (`ADR-049`,
`docs/ingenieria/escalabilidad.md`), que en local solo puede medirse a escala reducida.
`RF041` (alertas por Telegram) está construido y armado (`ADR-066`), apagado hasta tener `TELEGRAM_BOT_TOKEN`; sin probar contra Telegram real.
**929 pruebas de backend** (`./mvnw verify` del 2026-09-24: 0 fallos, 1 omitida; las de integración exigen Docker y no corren sin él).

⚠️ **`sprint-3.md` a `sprint-5.md` se reconstruyeron retroactivamente el 2026-09-22** (`REC-017`): el código ya
entregaba lo que prometían, sin que nadie lo hubiera escrito. `sprint-6.md` se abrió y cerró el 2026-09-24: la demo es local
y contra el backend, sin esperar al frontend (`ADR-057`, `REC-018` resuelta).

**8 sprints: Sprint 0 (preparación) + Sprints 1–6 + Sprint 7 (frontend nuevo, abierto el 2026-09-25). Un sprint no
cierra por calendario: cierra cuando su entregable se demuestra funcionando.** Los 8 entregables, en `docs/gestion/README.md`.

---

## Stack

**Backend** Spring Boot 3.5.16 · Java 21 · Maven · MongoDB (documentos + geoespacial `2dsphere`) ·
Redis (caché, rate limiting, ventana de consenso, pub/sub). **Sin SDK de IA**: se descartó en `ADR-025`
**Infraestructura** Docker multi-etapa + docker compose · nginx (proxy y micro-caché, `infra/nginx/`) · GitHub Actions

**Frontend** (en construcción, `ADR-067`) React 19 · Vite · TS estricto · CSS propio. Contrato: `backend/openapi.yaml`; guía: `docs/api/`.
Identidad visual: `docs/diseno/identidad.md` (`ADR-070`). **Antes de tocar la interfaz, skill `disenar-frontend`; antes del PR, `revisar-diseno`.**

---

## Arquitectura — reglas no negociables

Arquitectura Limpia (puertos y adaptadores). Las dependencias apuntan **siempre hacia adentro**.

```
com.aguavigia.ctg
├── domain/          ← Java puro. CERO imports de framework.
├── application/     ← Casos de uso. Depende solo de domain/port/out.
├── infrastructure/  ← Toda la tecnología: Mongo, Redis, correo, JWT, HTTP saliente.
└── api/             ← Controladores REST, DTOs, mappers.
```

### Regla de oro

**Si `domain/` importa algo que empiece por `org.springframework` o `com.mongodb`, la arquitectura
está rota.** No es criterio de nadie: hay un test de ArchUnit que lo verifica y la build falla.
Al proponer código, verifica mentalmente esta regla antes de escribir el import.

### Otras reglas estructurales

- Los controladores **no** contienen lógica de negocio. Traducen HTTP ↔ caso de uso y nada más.
- Nunca exponer entidades de dominio en la API. Siempre DTOs, mapeados con MapStruct.
- Un caso de uso = una clase = una acción. Si un servicio hace dos cosas, son dos servicios.
- Objetos de valor (`Coordenada`, `VentanaTiempo`, `EstadoServicio`): `record` que valida al construir.
- Errores de API en formato RFC 7807, centralizados en un `@RestControllerAdvice`.

---

## Convenciones de código

- **Idioma**: lo del dominio en **español** (`CorteAgua`, `calcularCumplimiento`); términos técnicos
  universales en inglés (`Repository`, `Controller`, `Adapter`). No mezclar en un mismo identificador.
- **Inyección de dependencias por constructor**, nunca `@Autowired` en campos.
- **Sin Lombok en `domain/`** — el dominio es Java puro y explícito. Lombok sí en `infrastructure/`.
- **Comentarios**: por defecto ninguno. Solo cuando el *porqué* no es obvio (una restricción oculta,
  un workaround con motivo). Nunca comentarios que expliquen *qué* hace el código.
- **Tests**: nombre descriptivo en español — `debeRechazarCorteConFinAnteriorAlInicio()`.

---

## Convenciones de Git

Repo privado, de un solo desarrollador. Commits, ramas y PRs siguen las reglas de siempre en mis
proyectos, documentadas en [`CONTRIBUTING.md`](CONTRIBUTING.md) — no las repito acá para no duplicar
la fuente de verdad. Resumen: Conventional Commits en español, ramas
`tipo/slug-corto-en-espanol-kebab-case` sobre `main`, squash-merge por defecto, CI en verde antes de
mergear. No hay rama `develop` ni revisor obligatorio: el PR es recomendado para cambios no
triviales, no obligatorio.

Las fechas del proyecto se escriben en **hora local de Cartagena (UTC-5)**, no UTC.

### Autoría — regla no negociable

**El agente nunca figura como colaborador del repositorio**: ni un trailer `Co-Authored-By`, ni una
firma *"Generated with Claude Code"*, ni como autor o revisor de un PR, issue o comentario. Refuerzo
mecánico: `includeCoAuthoredBy: false` en `.claude/settings.json` y el workflow `autoria.yml`, que falla si un
commit del PR lo firma la IA (`BUG-104`); si aun así ves un trailer de coautoría en un mensaje que vas a
escribir, quítalo.

**Por qué:** la autoría del proyecto es mía. La IA es una herramienta, y que firme los commits
enturbiaría el registro de lo que realmente escribí yo. Esto **no** oculta el uso de IA: está
declarado abiertamente en este mismo archivo y en la bitácora de sesiones.

---

## Ética de datos — no negociable

Es la coherencia del proyecto, no una preferencia de estilo. Detalle en `ADR-005` y `ADR-006`.

1. **Se respeta `robots.txt` siempre**, aunque pudiéramos evadirlo: **no se disfraza el
   `User-Agent`, no se discute.** Qué medio bloquea a qué agente: `MEMORY.md`.
2. **No se scrapea Facebook, Instagram ni X.** Vía legítima y su estado: `MEMORY.md` § Restricciones.
3. **El colector se identifica siempre**: `User-Agent` con nombre del proyecto y correo de contacto.
4. **Nada llega al mapa público sin verificación.** Si la IA no puede citar la frase exacta del
   boletín que respalda su extracción, no se publica. Un corte inventado destruiría la credibilidad.

---

## Fuentes de datos

En uso y verificadas: **Acuacar** (API REST de WordPress + RSS), **Google News RSS** y **Zona Cero
RSS**. Las 18 evaluadas, con veredicto: `docs/ingenieria/auditoria-fuentes-de-datos.md`. **Antes de
afirmar que una fuente está bloqueada o disponible, verifícalo con una petición real** (skill
`verificar-fuente`): aquí ya costó caro asumir un `robots.txt` sin leerlo.

---

## Dónde está cada cosa

```
/                       CLAUDE.md · DESIGN.md · MEMORY.md · README.md · .mcp.json
.claude/                skills/ · agents/ · settings.json
docs/                   brief.md · product-requirements.md (46 RF, 27 RNF) · design-decisions.md (ADR)
docs/api/               Guía para construir el frontend: flujos, rutas, errores, escala (referencia generada)
docs/ingenieria/        Pipeline de datos, auditoría de fuentes, matriz de trazabilidad, comportamiento del sistema, escalabilidad
docs/gestion/           Sprints, bitácora, bugs e implementaciones
backend/ · frontend/ · infra/   Spring Boot · SPA en construcción · nginx del proxy
scripts/                Siembra de datos, pruebas de carga (`carga/`), generador de la referencia de la API
```

---

## Qué se registra siempre — regla del proyecto

No es opcional: es parte de la definición de terminado.

| Ocurre | Se registra en | Con la skill |
|---|---|---|
| Se fusiona un PR a `main` | `docs/gestion/registro-de-implementaciones.md` | `registrar-implementacion` |
| Se encuentra un bug (aunque se arregle en el acto) | `docs/gestion/registro-de-bugs.md` | `registrar-bug` |
| Termina una sesión de trabajo con IA | `docs/gestion/bitacora-sesiones.md` | `cerrar-sesion` |
| Se elige entre alternativas técnicas | `docs/design-decisions.md` | `registrar-decision` |
| Cambia el comportamiento del sistema | `docs/ingenieria/comportamiento-del-sistema.md`, en el mismo PR | — |
| Se verifica una fuente de datos | `docs/ingenieria/auditoria-fuentes-de-datos.md` | `verificar-fuente` |
| Avanza un compromiso del sprint (entregado o a medias) | `docs/gestion/sprint-N.md` §2 — `✅`/`🟡` al inicio del Entregable | — |

**Quien avanza, actualiza el registro — yo o la IA, sin excepción.** La Sala de control
(`dist-dashboard/index.html`, ignorado por git) **se genera sola de estas filas y nadie edita su HTML**: lo que no se registre
aquí, allá no existe. Se regenera a mano con `node scripts/generar-dashboard.mjs` y se abre en local;
ya no se publica. Detalle: `docs/gestion/README.md`.

---

## Cómo colaborar conmigo

- **Antes de tu primera sesión, lee `docs/gestion/protocolo-de-contexto.md`**: dónde vive cada dato y
  el presupuesto de líneas de los archivos permanentes. Cada línea que agregues aquí se paga en cada
  sesión de trabajo.
- **Un dato vive en un solo archivo.** Si lo encuentras duplicado, es un defecto: detalle en uno,
  puntero en el otro.
- **No repitas contexto**: lo decidido está en `docs/design-decisions.md`. Léelo antes de proponer una
  alternativa ya descartada.
- **No generes código de producción en fase de documentación** sin confirmarlo.
- **Verifica antes de afirmar.** Si dices que un endpoint funciona, pruébalo.
- Si un documento contradice a otro, **dilo en vez de elegir en silencio**.
