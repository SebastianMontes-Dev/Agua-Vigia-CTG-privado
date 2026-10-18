# Guía para Yordy: qué hay en el backend y qué toca hacer en el frontend

Escrita por el backend el 2026-10-18 (hora de Cartagena), contrastada con `main` (`c6ff2fb`), con `backend/openapi.yaml` y con el CI de GitHub.
Es la **puerta de entrada**: te dice dónde estamos, qué existe ya en el backend, en qué orden trabajar y qué hacer en cada paso.
No duplica las guías de detalle; las enlaza. Si algo de aquí choca con el contrato, **gana `backend/openapi.yaml`**.

Si solo tienes 5 minutos: lee §1, §2 y §5.1, y arranca FE0.

## Índice

1. [Dónde estamos](#1-dónde-estamos)
2. [Cómo se trabaja entre los dos](#2-cómo-se-trabaja-entre-los-dos)
3. [Arrancar el entorno (día 0)](#3-arrancar-el-entorno-día-0)
4. [Todo lo que el backend ya hace](#4-todo-lo-que-el-backend-ya-hace)
5. [El plan, fase por fase](#5-el-plan-fase-por-fase)
6. [Reglas que no se negocian en la interfaz](#6-reglas-que-no-se-negocian-en-la-interfaz)
7. [Trampas conocidas](#7-trampas-conocidas)
8. [Decisiones abiertas](#8-decisiones-abiertas)
9. [Checklist de entrega](#9-checklist-de-entrega)
10. [Glosario](#10-glosario)

---

## 1. Dónde estamos

### 1.1 Las cifras de hoy

| | Hoy | Cómo se comprobó |
|---|---|---|
| Rutas del contrato (paths) | **81** (87 operaciones) | Conteo de `paths:` en `backend/openapi.yaml` |
| Rutas en tu `esquema.ts` generado | **58** | `frontend/src/api/generado/esquema.ts`, del 2026-09-29 |
| Rutas que faltan en tu esquema | **23** (lista en §1.3) | Diferencia entre las dos anteriores |
| Rutas que el frontend consume | **27** | [`README.md`](README.md#punto-de-partida-main-2026-10-06): el frontend no ha recibido commits desde el 2026-10-06 |
| Pantallas que existen | Mapa, ficha, reporte, confirmar, cumplimiento, estadísticas, bitácora, avisos (3), muestrario | `frontend/src/pantallas/publico` |
| Pantallas que no existen | Panel, cuentas, vecino, «¿ya volvió el agua?» | — |
| Rama tuya pendiente | `feat/f5-ingreso-panel` (2 commits, ingreso al panel con TOTP). **No se borra ni se toca**; se integra en FE2 | `git ls-remote` |

### 1.2 El Frontend CI está rojo, y desde antes de esta semana

En `main` (último en `f45d5b2`, 2026-10-15) fallan los dos jobs del Frontend CI:

| Job | Paso que falla | Causa |
|---|---|---|
| Lint, tipos, contrato, pruebas y build | **Contrato de la API sin deriva** (`npm run api:check`) | Tu `esquema.ts` es del 2026-09-29 y el contrato ya tiene 81 rutas. Se arregla con `npm run api:sync` (es el primer paso de FE0) |
| Núcleo ciudadano contra backend real | **E2E reales de mapa, reportes y avisos con Mailhog** | **Sin verificar**: no he leído su log. La causa más probable es que reportar sin `X-Dispositivo` da `401 dispositivo-invalido` (FE0 lo corrige). Confírmalo en el log antes de dar por buena esta hipótesis |

Las rutas de PostgreSQL (§4.12) añadieron 3 rutas más a la deriva, pero **no son la causa**: el CI ya estaba rojo por el esquema viejo. Lo que cambió en estos días es solo que regenerar el esquema ahora trae 3 rutas nuevas
(la lista completa está abajo).

### 1.3 Las 23 rutas que faltan en tu esquema, y en qué fase entran

| Fase | Rutas | Cuántas |
|---|---|---|
| FE0 | `POST /api/dispositivos`, `GET /api/fotos/{nombre}` | 2 |
| FE1 | `GET /api/sistema/modo`, `GET /api/cumplimiento/calidad`, `POST /api/sectores/{sectorId}/restablecimiento` | 3 |
| FE3 | `GET /api/veedor/disputas`, `GET /api/veedor/cortes/vencidos`, `PATCH /api/veedor/cortes/{id}/sectores/{sectorId}/cierre`, `PATCH …/sectores/{sectorId}/confirmacion`, `PATCH /api/veedor/cortes/{id}/anulacion`, `PATCH /api/veedor/reportes/{id}/foto/descartar`, `GET /api/veedor/fotos/{nombre}` | 7 |
| FE4 | `PATCH /api/veedor/ingesta/propuestas/{id}/anulacion` | 1 |
| FE5 | `GET /api/veedor/sistema/metricas`, `GET /api/veedor/analitica/copia` | 2 |
| FE6 | `POST /api/cuentas/vecino`, `GET /api/vecino/yo`, `PATCH /api/vecino/perfil`, `POST /api/vecino/sesion`, `POST /api/vecino/sesion/cierre`, `POST /api/vecino/verificacion-barrio` | 6 |
| Sin pantalla (FE7 las excluye) | `GET /api/analitica/cumplimiento`, `GET /api/analitica/cumplimiento/mensual` | 2 |

Suman 23. Las demás rutas que el contrato tiene y tu esquema ya conoce (por ejemplo `/api/veedor/usuarios`, `/api/veedor/auditoria`) están en el esquema viejo pero **no tienen pantalla**: entran en FE2, FE3, FE5 y FE6.

---

## 2. Cómo se trabaja entre los dos

### 2.1 Reparto

- **Backend: Sebastian. Frontend: tú.** Sebastian no toca `frontend/` salvo que se lo pidas. Tú no tocas `backend/`.
- **El contrato manda.** `backend/openapi.yaml` es la fuente de verdad. Si una prueba contra el backend real falla **sin que el contrato haya cambiado**, es un defecto del backend: se lo cuentas a Sebastian con la petición, la respuesta y el comando que la provocó. No lo parches en el frontend.
- **Los cambios de contrato se avisan** en [`docs/api/cambios-para-frontend.md`](../api/cambios-para-frontend.md) **antes** de fusionarse. Esa es la bitácora de lo que debes adaptar: se lee de arriba abajo una vez (F0 a F7) y después solo lo nuevo.

### 2.2 Ramas y revisión

- El **backend** va directo a `main` (regla de `CLAUDE.md`).
- El **frontend** usa **una rama por fase** (`feat/fe<n>-<tema>`) y un PR, porque su CI necesita la rama para correr los dos jobs. Fusiona con la puerta (§5.0) en verde.
- Una fase por sesión de Claude Code, con `/clear` entre fases. Cada guía FE termina con un prompt listo para pegar.
- Commits en español con Conventional Commits (`feat(panel): …`), un commit por causa. **Sin `Co-Authored-By` ni firmas de IA**: el workflow `autoria.yml` revisa los PR y falla si un commit lo firma una identidad de IA. Antes de abrir el PR corre `scripts/verificar-autoria.sh origin/main..HEAD`.

### 2.3 Cuándo escribirle a Sebastian

| Situación | Qué mandarle |
|---|---|
| Un endpoint responde distinto de lo que dice el contrato | La petición exacta (método, ruta, cabeceras sin tokens), la respuesta y el `type` del error |
| Necesitas un cambio de contrato (un campo, un permiso) | Qué pantalla lo exige y qué dato falta. Él decide y lo anota en `cambios-para-frontend.md` |
| El backend de `docker compose up` no arranca | Salida de `docker compose logs backend` (sin la clave del ADMIN) |
| Quieres que el correo apunte a la SPA en vez de a las páginas HTML del backend | Es el cambio de `MailCuentaAdapter` de [FE6b](FE6-cuentas-y-vecino.md#los-enlaces-de-los-correos-de-cuenta); va en una fase aparte |

---

## 3. Arrancar el entorno (día 0)

Todo esto cabe en una hora. Los comandos están en PowerShell de Windows, el entorno de todo el equipo: **un comando por línea** (el PowerShell 5.1 no tiene `&&`).

### 3.1 Backend y servicios

```powershell
git checkout main
git pull origin main
docker compose up -d --build
```

Un solo `docker compose up` levanta MongoDB (réplica de un nodo), Redis, Mailhog, PostgreSQL, el backend y un sembrador que carga los **211 barrios** (ADR-094). El backend crea además **30 000 cuentas de vecino sintéticas** (lo único inventado, declarado como tal).
No necesitas `.env`. Para ver cada servicio por separado: [`01-levantar-a-mano.md`](../01-levantar-a-mano.md).

| Qué | Dónde |
|---|---|
| API real | http://localhost:8081 (Swagger UI en `/swagger-ui.html` con el perfil `docker`) |
| Correos que envía el backend (Mailhog) | http://localhost:8025. **Nada sale a Internet**: ábrelo para ver los enlaces de verificación y de aviso |
| PostgreSQL (copia analítica) | `localhost:5433`, base `aguavigia_analitica` |
| Simulación (otra instancia, otra base) | http://localhost:8082, con `docker compose --profile simulacion up -d --build backend-sim` |
| Visor de Mongo | http://localhost:8083, con `--profile demo` |

Para que `e2e/real` sea repetible, levanta el backend con `INGESTA_MODO=local` (con `auto` la ingesta trae boletines de Internet y la base nunca es la misma):

```powershell
$env:INGESTA_MODO = "local"
docker compose up -d backend
```

### 3.2 La clave del ADMIN inicial

Sale **una sola vez** en el log del backend. No la pegues en chats ni en archivos:

```powershell
docker compose logs backend | findstr ADMINISTRADOR
```

El primer ingreso del ADMIN exige dar de alta el segundo factor (TOTP). Para generar los códigos de 6 dígitos sin teléfono: `node scripts/codigo-totp.mjs`. Para volver a empezar con un ADMIN limpio: `node scripts/restablecer-admin.mjs`.

### 3.3 Frontend

```powershell
cd frontend
npm ci
npm run dev
```

`vite.config.ts` hace de proxy: la SPA y la API comparten origen (`/api`). Para apuntar a la simulación: `$env:AGUAVIGIA_BACKEND = "http://localhost:8082"` antes de `npm run dev`.

### 3.4 Qué comprobar antes de empezar

1. `Invoke-RestMethod http://localhost:8081/api/sectores | Measure-Object` → 211 barrios.
2. `Invoke-RestMethod http://localhost:8081/api/sistema/modo` → `modo: REAL`.
3. En el frontend, `npm run lint`, `npm run typecheck`, `npm test` pasan. **`npm run api:check` va a fallar**: es lo esperado hasta FE0.

---

## 4. Todo lo que el backend ya hace

Esto es lo implementado y probado en el backend (Spring Boot 3.5, Java 21, MongoDB 7, Redis 7, PostgreSQL 17). Para cada bloque: qué hace, qué significa para tu pantalla y dónde está el detalle.
**MongoDB es la única fuente de verdad**; PostgreSQL es una copia derivada (§4.12).

### 4.1 Estado de un barrio (ADR-087)

Un **único resolutor** decide el estado de cada uno de los 211 barrios a partir de lo que afirma cada fuente: boletín de Acuacar, prensa aprobada, corte o cierre del veedor, quórum de vecinos. Nada más escribe el estado (lo exige una regla ArchUnit).

- Estados: `CON_SERVICIO`, `SIN_SERVICIO`, `PRESION_BAJA`, `CORTE_PROGRAMADO` (un aviso futuro) y **`null` = «sin datos»**. `null` **nunca** se pinta como «con servicio».
- El barrio explica de dónde sale su estado: `origen` (`ACUACAR`, `PRENSA`, `VEEDOR`, `VECINOS`, `SENSOR`; `SENSOR` existe pero no se produce), `ventanaPrometida`, `restablecimientoPorConfirmar`, `enDisputa`, `reportesEnContra`, `respaldo {vecinos, umbral}`.
- **Disputa**: si los vecinos contradicen a la fuente oficial, el barrio se marca «en disputa». **El color no cambia por una disputa**, solo se marca.
- Umbral de vecinos por barrio: `clamp(ceil(población × 0,001), 3, 15)`. «11 de 12 vecinos» es respaldo sobre umbral.
- Lo que nadie confirma **expira** (ADR-088): un corte sin cierre confirmado pasa a `EXPIRADO` y deja de contar.
- Tiempo real por **SSE** (`GET /api/sectores/stream`): una conexión por pestaña.
- Detalle: [`sectores-y-tiempo-real.md`](../api/sectores-y-tiempo-real.md). Tu fase: FE1.

### 4.2 Cortes oficiales (ADR-088)

Un corte agrupa varios barrios y **se cierra barrio por barrio**. El veedor puede registrarlo, cerrar uno o todos los barrios, confirmar un cierre provisional (el que sostienen solo vecinos), anular con motivo y atender los **vencidos** (promesa cumplida sin cierre).

- `CorteRespuesta` ya **no tiene `finReal`**: tiene `cierres[]` (`sectorId`, `hora`, `fuente`, `provisional`).
- Estados del corte: `ANUNCIADO`, `CONFIRMADO`, `RESTABLECIDO`, `EXPIRADO` (nadie lo cerró a las 72 h del fin prometido) y `ANULADO` (con `motivoAnulacion`).
- La bitácora gana `CORTE_EXPIRADO`, `CORTE_ANULADO`, `RESTABLECIMIENTO_POR_VECINOS`, `ESTADO_EN_DISPUTA`, `CONSENSO_REVERTIDO`.
- Detalle: [`panel-veedor.md`](../api/panel-veedor.md#cortes-oficiales). Tus fases: FE0 (tipos), FE3 (pantallas).

### 4.3 Reportes ciudadanos e identidad (ADR-089, ADR-090)

Se puede reportar **sin cuenta** (token de dispositivo) o **con cuenta de vecino**. El servidor pone la identidad; el cliente no inventa nada.

- `POST /api/dispositivos` entrega un token. **Se pide una vez y se guarda**; máximo 10 por hora por IP. Se manda en la cabecera `X-Dispositivo`.
- `POST /api/reportes` exige identidad y acepta `precisionMetros`. Se **eliminó `huella`**. `POST …/confirmar` va sin cuerpo.
- Cada reporte lleva `verificacion`: `CUENTA_VERIFICADA`, `UBICACION_VERIFICADA` (GPS ≤ 200 m dentro del barrio) o `NINGUNA`. No es un voto con peso: es una marca.
- **Cupo**: 3 reportes por barrio cada 30 min por dispositivo, 5 por vecino (`429 limite-reportes-excedido`).
- **Quórum**: el barrio cambia de estado cuando se alcanza el umbral, pero además un tercio del sustento debe estar verificado y venir de al menos 2 redes distintas (en local el compose lo deja en 1 red).
- **La ubicación se usa y se descarta**: la coordenada guardada es una aproximación de ~110 m.
- Detalle: [`reportes.md`](../api/reportes.md). Tus fases: FE0, FE6.

### 4.4 Fotos como evidencia (ADR-091)

- Se sube **después** del reporte con la cabecera `X-Subida` (el `subidaToken` de la respuesta: un solo uso, vence a los 10 min).
- Solo JPEG o PNG (`415 formato-no-permitido` para el resto; **WebP se rechaza**).
- Entra «en revisión»; solo se ve públicamente cuando un moderador la aprueba. Rutas: `GET /api/fotos/{nombre}` (pública, `404` si no está aprobada) y `GET /api/veedor/fotos/{nombre}` (panel, con `Authorization`, por `fetch`, no por `<img src>`).
- `fotoEstado`: `SIN_FOTO`, `EN_REVISION`, `PUBLICA`, `DESCARTADA`.
- Tus fases: FE0, FE3.

### 4.5 Ingesta de Acuacar y prensa (ADR-092)

Colectores que respetan `robots.txt` y se identifican con `User-Agent`. **Nada llega al mapa sin la frase exacta del boletín que lo respalde.**

- Un boletín fiable y con sentido se publica solo; si no, queda como **propuesta** esperando al veedor (con `motivoDeRevision`).
- Boletines con varias zonas generan un corte por zona. Los históricos (ventana terminada hace más de 72 h) quedan como historia y no mueven el mapa.
- Los avisos de aplazamiento o cancelación se reconocen y no se publican como corte nuevo.
- Detalle: [`panel-veedor.md`](../api/panel-veedor.md#revisión-de-la-ingesta). Tu fase: FE4.

### 4.6 «¿Ya volvió el agua?» (ADR-093)

El correo de aviso trae un enlace de un toque: `{urlFrontend}/sectores/{id}/restablecimiento?token=…`. **Esa pantalla la construyes tú** (FE1): un botón; el `POST` solo al pulsarlo, nunca al cargar la página (los clientes de correo abren los enlaces).
No hay cierres reales desde boletines: el restablecimiento lo sostienen vecinos, y el veedor lo confirma.
Detalle: [`correos-y-enlaces.md`](../api/correos-y-enlaces.md).

### 4.7 Índice de Cumplimiento (ADR-022)

Duración **prometida** frente a **real** de cada corte, por **par corte-barrio**; se **suman** duraciones (no se promedian porcentajes) y el porcentaje es `min(100, prometida × 100 / real)`.
- Gana `porcentajeProvisional`, `cortesSinCierreConfirmado` y `cortesAnulados`: **se publican junto al número**.
- `GET /api/cumplimiento/calidad` responde siempre, incluso cuando el Índice da `400` por no haber ni un cierre: permite decir «sin datos suficientes» **con cifras**.
- Rutas: global, por barrio, por corte, serie mensual y CSV. Detalle: [`bitacora-estadisticas-cumplimiento.md`](../api/bitacora-estadisticas-cumplimiento.md). Tu fase: FE1.

### 4.8 Cuentas, roles, permisos y segundo factor (ADR-039)

- **Panel**: roles `ADMIN`, `VEEDOR`, `OBSERVADOR` más permisos sueltos (`VER_PANEL`, `MODERAR_REPORTES`, `GESTIONAR_CORTES`, `REVISAR_INGESTA`, `GESTIONAR_USUARIOS`, `VER_AUDITORIA`, `CONFIGURAR_SEGUNDO_FACTOR`). Cada ruta `/api/veedor/**` exige un permiso concreto.
- Sesión por JWT de **8 h, sin renovación**; revocable. TOTP obligatorio para el ADMIN. Bloqueo de cuenta (`423`), límites por IP, auditoría de cada acción.
- **Vecino registrado** (rol `VECINO`): `POST /api/cuentas/vecino` (siempre `202`, no revela qué correos tienen cuenta); la clave la elige desde el enlace del correo; sesión en `/api/vecino/sesion`; perfil; verificación de barrio con la ubicación del momento (3 intentos por día).
- **Un vecino nunca entra al panel** ni una cuenta del panel entra como vecino.
- Los enlaces de los correos de cuenta abren **páginas HTML que sirve el backend** (`/api/cuentas/enlaces/*`) y protegen el token: el `GET` no lo gasta. En FE6 se usan tal cual.
- Detalle: [`cuentas-y-sesion.md`](../api/cuentas-y-sesion.md). Tus fases: FE2, FE5, FE6.

### 4.9 Avisos por correo y Telegram

Doble confirmación por correo (`/api/suscripciones`, `/confirmar`, `/cancelar`); `sectorIds` entre **1 y 211**. Telegram solo si hay bot configurado. Ya existe en tu frontend; FE0 solo añade la validación de los 211.
Detalle: [`suscripciones.md`](../api/suscripciones.md).

### 4.10 Modo del sistema y simulación (ADR-094 a 096)

- `GET /api/sistema/modo` → `{modo: "REAL" | "SIMULACION", cuentasSinteticas}`. **Con `SIMULACION` hay un banner permanente** en todas las pantallas. Las 30 000 cuentas se declaran como «cuentas sintéticas generadas por el sistema con las reglas de alta de un vecino»; **nunca** «30 000 personas se registraron».
- La simulación es una **segunda instancia** (puerto 8082, base aparte) que corre un día entero acelerado (≈ 3 min). Sirve de prueba de aceptación y de demo. Las rutas `/api/sim/**` son del simulador: el frontend no las llama.
- Detalle: [`scripts/simulacion/README.md`](../../scripts/simulacion/README.md).

### 4.11 Métricas de calibración (ADR-097)

`GET /api/veedor/sistema/metricas` (`VER_PANEL`): contadores del proceso desde `desde` (cambios de estado, disputas, quórums rechazados, reportes por nivel de verificación, fallos de colectores, tiempo hasta el cambio). Un reinicio los pone a cero: muestra `desde` siempre. No son semáforos ni umbrales.

### 4.12 PostgreSQL: la copia analítica (ADR-100, nuevo)

Para cumplir con el requisito de SQL de la materia, un job (`CopiaAnaliticaJob`) copia cada 5 minutos barrios, cortes y cierres de Mongo a un esquema relacional (`analitica.*`, con claves foráneas, restricciones y vistas) y expone el Índice en tres rutas de solo lectura.

- `GET /api/analitica/cumplimiento[?sectorId=]` y `GET /api/analitica/cumplimiento/mensual` (públicas) y `GET /api/veedor/analitica/copia` (`VER_PANEL`).
- Si PostgreSQL está apagado el backend sigue `UP` y estas tres rutas dan `503`. **Nunca suplen leyendo Mongo.**
- Para ti: un bloque en `/panel/sistema` (FE5) y nada más. Reglas completas: [`cambios-para-frontend.md` §F7](../api/cambios-para-frontend.md#f7--postgresql-la-copia-analítica-del-índice-adr-100-2026-10-15).
- Diseño del modelo: [`03-base-de-datos.md`](../03-base-de-datos.md) §4.

### 4.13 Lo que existe pero no te toca

| Qué | Por qué |
|---|---|
| `/api/sim/**` | Lo usa el simulador con `X-Sim-Key`. En la instancia real responde 404 |
| `POST /api/iot/presion` | Sensores de presión: construido pero **inactivo** (`503` sin `X-IoT-Key`) |
| `GET /api/v2/requests.json` | Open311, una API para máquinas |
| `/api/cuentas/enlaces/*` | Páginas HTML que sirve el backend |

### 4.14 Seguridad, errores y límites que tu cliente debe respetar

- Errores en **RFC 7807** (`application/problem+json`). Se decide por `type`, **nunca por el texto**. Catálogo y códigos: [`errores-y-limites.md`](../api/errores-y-limites.md).
- `429` siempre trae `Retry-After`: respétalo.
- Las listas del panel y la bitácora devuelven un arreglo y la **paginación va en cabeceras**.
- Fechas en UTC en la API; **se muestran en hora de Cartagena (UTC-5)**.
- Consumir a escala (50 000 usuarios): [`escalabilidad-para-el-cliente.md`](../api/escalabilidad-para-el-cliente.md).

---

## 5. El plan, fase por fase

### 5.0 La puerta (se repite en cada fase)

Desde `frontend/`, todo en verde antes de pedir el PR:

1. `npm run lint`, `npm run typecheck`, `npm run api:check`, `npm test`, `npm run build`.
2. `npm run test:e2e` (contra la API simulada de Playwright).
3. `npm run test:e2e:real`, con `docker compose up` y `RATE_LIMIT_FACTOR=100`.
4. Capturas en 1440×900, 1024×768, 768×1024 y 390×844, claro y oscuro, más una con movimiento reducido. Se guardan en `frontend/test-results/capturas/<fase>/` (ignorado por git).
5. Skill `revisar-diseno` sobre las capturas. Antes de tocar la interfaz, skill `disenar-frontend`.
6. Prueba de los cinco segundos en cada pantalla nueva: alguien que no la conoce dice qué responde sin hacer scroll.
7. El CI de la rama, en verde, incluidos los dos jobs.

Detalle y justificación: [`README.md`](README.md#la-puerta-lo-que-se-comprueba-al-cerrar-cada-fase).

### 5.1 Orden y dependencias

```
FE0 ──▶ FE1
  └───▶ FE2 ──▶ FE3 ──▶ FE4
                 └───▶ FE5
FE0 ──▶ FE6 (usa el marco de sesión de FE2)
todas ──▶ FE7
```

Recomendación de calendario (cada «sesión» es una sesión de Claude Code): **FE0 (2) → FE1 y FE2 en paralelo (2 + 1–2) → FE3 (2–3) → FE4 (1–2) y FE5 (2) → FE6 (2) → FE7 (2)**. Son unas 14 a 17 sesiones en total.

### 5.2 FE0 · Contrato al día — **empieza aquí**

Guía: [`FE0-contrato-al-dia.md`](FE0-contrato-al-dia.md). Rama `feat/fe0-contrato-al-dia`. Objetivo: que compile, que reportar funcione y que el CI quede en verde.

Orden de trabajo (un commit por causa):

1. `git checkout -b feat/fe0-contrato-al-dia main` y `npm run api:sync`. Aparecen errores de compilación: es lo esperado. El esquema ahora trae las 81 rutas (incluidas las 3 de PostgreSQL, que no usarás todavía).
2. **CI:** `scripts/preparar-env-ci.mjs` debe escribir `RATE_LIMIT_FACTOR=100` en el `.env` del CI. `docker-compose.yml` ya lo pasa al backend. Avisa a Sebastian: es un script compartido.
3. **Identidad del dispositivo:** crea `src/api/dispositivo.ts` (`POST /api/dispositivos` una vez, token en `localStorage` con `try/catch`, cabecera `X-Dispositivo`, ante `401 dispositivo-invalido` pide otro y reintenta **una** vez). Borra `huella.ts` y su test.
4. **Reportar:** `precisionMetros` junto a la coordenada; `/confirmar` sin cuerpo; guarda `subidaToken` y mándalo en `X-Subida` al subir la foto; quita WebP (`TIPOS_FOTO`, texto de ayuda); maneja `415 formato-no-permitido`; muestra `fotoEstado`.
5. **Proxy:** quita `/fotos` de `vite.config.ts`. Las fotos salen por `/api/fotos/{nombre}`; usa `fotoUrl` tal cual.
6. **Cortes:** `finReal` desaparece, ahora hay `cierres[]`. Actualiza `src/dominio/cortes.ts` (`estaAbierto`, `estaCerrado`, duración), los estados `EXPIRADO` y `ANULADO` y los 5 valores de `OrigenEstado`.
7. **Bitácora:** `src/dominio/historia.ts` acepta los 9 tipos; un tipo desconocido **se muestra** con texto neutro, no se descarta. Muestra `fuente` y `respaldo`.
8. **Avisos:** valida `sectorIds` ≤ 211 antes del `POST`.
9. Pruebas unitarias y e2e en la forma nueva (`cierres`, `subidaToken`, `fotoEstado`, `X-Dispositivo`).

Terminado cuando: puerta completa; `grep -rn "finReal\|huella\|image/webp" frontend/src` sin resultados (salvo comentarios); un reporte desde la interfaz llega a Mongo con su dispositivo y su foto.

### 5.3 FE1 · Lo público, completo

Guía: [`FE1-publico-completo.md`](FE1-publico-completo.md). Depende de FE0.

1. **Antes de construir**, añade a `docs/diseno/guia-frontend.md` §5.1/§5.2 las filas de las pantallas nuevas (regla 3 del plan).
2. Ficha `/sectores/:id`: insignias del estado siempre **con texto** (`origen`, `ventanaPrometida`, «por confirmar», «en disputa», «11 de 12 vecinos»); conserva `actualizadoEn` y `verificadoEn` (aviso tras 24 h).
3. Mapa: marca discreta para barrios en disputa o por confirmar, sin colores nuevos.
4. Historial de cortes de la ficha: `cierres[]` por barrio, provisional, `EXPIRADO`, `ANULADO`; Índice del corte (`409` = «todavía no se puede medir»).
5. `/cumplimiento`: calidad del dato junto al número y estado «sin datos suficientes» **con cifras** (`/api/cumplimiento/calidad`).
6. **Banner permanente** si `GET /api/sistema/modo` da `SIMULACION`.
7. Pantalla `/sectores/:id/restablecimiento?token=` (§4.6): el token sale de la URL con `history.replaceState` y `referrer no-referrer`; `201`, `403 enlace-invalido`, `429`.

### 5.4 FE2 · Ingreso al panel

Guía: [`FE2-ingreso-al-panel.md`](FE2-ingreso-al-panel.md). Depende de FE0. Casi todo está hecho en tu rama `feat/f5-ingreso-panel`.

1. `git checkout -b feat/fe2-ingreso-al-panel main` y `git cherry-pick 95962ad 5dda089`. Solo choca `router.tsx`.
2. Adapta los tipos al esquema nuevo (nada a mano).
3. Completa `/panel/seguridad` (cambiar clave, quitar TOTP salvo el ADMIN, «Cerrar todas mis sesiones» con aviso previo).
4. Token en `sessionStorage`, nunca en la URL. Un `401` con `type` distinto de `credencial-invalida` o `segundo-factor-requerido` borra la sesión.
5. Cuando se fusione, archiva la rama vieja como `archivo/yordy-<fecha>/f5-ingreso-panel` y bórrala.

### 5.5 FE3 · Moderación y cortes

Guía: [`FE3-moderacion-y-cortes.md`](FE3-moderacion-y-cortes.md). Depende de FE2.

1. Añade `/panel/disputas` y «Vencidos» a `guia-frontend.md` §5.4.
2. `/panel`: cola de reportes con `verificacion`, `senalRed` (señal, no acusación) y foto vía `GET /api/veedor/fotos/{nombre}` con `Authorization`; aprobar, descartar y descartar solo la foto.
3. `/panel/cortes`: selector de barrio obligatorio; alta con fechas en hora de Cartagena; cerrar todo, cerrar un barrio, confirmar un cierre provisional, anular con motivo; sección «Vencidos».
4. `/panel/disputas`: los barrios más contradichos primero.
5. Cada acción se oculta sin su permiso; confirmación y sin doble envío; los `409` se explican.

### 5.6 FE4 · Ingesta

Guía: [`FE4-ingesta.md`](FE4-ingesta.md). Depende de FE2.

1. `/panel/ingesta`: propuestas con cita textual, fuente con enlace, ventana y `motivoDeRevision`. **Una cita o fuente ausente se dice explícitamente; nunca se inventa evidencia** (regla 4 de ética de datos).
2. Aprobar, descartar y anular (`REVISAR_INGESTA`). Tras aprobar se refrescan los datos: aprobar no implica que el estado cambie.
3. Documentos fallidos y salud de cada colector, en texto y glifo.

### 5.7 FE5 · Administración

Guía: [`FE5-administracion.md`](FE5-administracion.md). Depende de FE2.

1. `/panel/cuentas`: lista paginada; **por defecto oculta las `sintetica: true`** (hay 30 000) con un interruptor; invitar, reenviar, aprobar, rechazar, suspender, reactivar, ajustar permisos. Autoprotección: sin botones sobre uno mismo, sin quitar al último ADMIN, `CONFIGURAR_SEGUNDO_FACTOR` irrevocable.
2. `/panel/auditoria`: solo lectura, paginada.
3. `/panel/sistema`: métricas (§4.11) con su guía de lectura, sin semáforos. **Nuevo en esta guía:** un bloque «Copia analítica» con `GET /api/veedor/analitica/copia` (§4.12): resultado en texto y glifo, conteos de Mongo frente a SQL y el `detalle`; un `503` se muestra como «La copia analítica está apagada o PostgreSQL no responde; el sistema funciona igual», no como un error de la pantalla.
4. El e2e real de esta fase también cierra la comprobación pendiente de FE3: un OBSERVADOR invitado no ve acciones de veedor.

### 5.8 FE6 · Cuentas públicas y vecino

Guía: [`FE6-cuentas-y-vecino.md`](FE6-cuentas-y-vecino.md). Depende de FE0 y del marco de sesión de FE2.

1. Añade §5.5 «Vecino registrado» a `guia-frontend.md` antes de construir.
2. `/cuenta/solicitar`, `/cuenta/olvide` (**mismo mensaje exista o no la cuenta**) y reenvío de verificación.
3. `/vecino/registro` (sin clave: llega por correo), `/vecino/ingreso`, `/vecino/perfil` (cambiar de barrio anula la verificación y se avisa antes), verificación de barrio con la ubicación del momento (3 intentos por día; errores `ubicacion-fuera-del-barrio` y `ubicacion-imprecisa`).
4. Reportar con sesión de vecino: `Authorization: Bearer` además de `X-Dispositivo`; manda la cuenta; cupo 5.
5. Los enlaces de correo siguen en las páginas HTML del backend (FE6b opcional, §8).

### 5.9 FE7 · Integración y entrega

Guía: [`FE7-integracion-y-entrega.md`](FE7-integracion-y-entrega.md). Depende de todas.

1. Script `frontend/scripts/cobertura-contrato.mjs` que lista las rutas del contrato que ningún módulo de `src/api` llama, y se suma al CI. **Exclusiones permitidas:** `/api/sim/**`, `/api/iot/presion`, `/api/v2/requests.json`, `/api/cuentas/enlaces/*`, **`/api/analitica/cumplimiento` y `/api/analitica/cumplimiento/mensual`** (§4.12), más las JSON de FE6b si no se hizo.
2. Retira lo provisional: `/muestrario` fuera de la navegación, `Pendiente.tsx` eliminado.
3. Accesibilidad: axe sin violaciones (`@axe-core/playwright`), todo con teclado, el mapa con alternativa en lista, texto al 200 %, movimiento reducido.
4. Rendimiento: 3G simulado con respuesta útil antes de 3 s (RNF001), un solo SSE por pestaña, geometría cacheada un día. Anota las cifras reales.
5. Prueba de los cinco segundos en mapa, ficha, cumplimiento y panel.
6. `docs/frontend/guion-demo.md`: ciudadano reporta con foto → el veedor modera → el consenso cambia el mapa en vivo → el corte se cierra → el Índice se mueve → un suscriptor recibe el aviso y responde «ya volvió». Con alternativa en modo simulación.
7. Documentación: README de esta carpeta con todas las fases ✅ y `cambios-para-frontend.md` con qué secciones F0 a F7 quedaron adoptadas.

---

## 6. Reglas que no se negocian en la interfaz

1. **Nada que la API no devuelva.** Cada cifra, insignia o texto sale de un campo del contrato. Si una pantalla nueva no está en `guia-frontend.md` §5, se añade **antes** de construirla.
2. **`null` es «sin datos», no «con servicio».** Afirmar servicio normal sin verificarlo es el falso positivo que el proyecto existe para evitar.
3. **Errores por `type`, no por texto.** Cada lista tiene estado de carga, vacío y error, y cada dato muestra su frescura.
4. **Permisos:** el panel pinta solo lo que `permisos[]` permite. El servidor valida siempre; ocultar un botón no es seguridad.
5. **El token nunca va en la URL.** Las pantallas que reciben `?token=` lo quitan con `history.replaceState` y usan `referrer no-referrer`. Sin scripts de terceros. Token del panel y del vecino en `sessionStorage`.
6. **Ética de datos (visible en la interfaz):** nada se muestra como verificado sin la frase exacta del boletín que lo respalda; no se inventa evidencia; una simulación nunca se presenta como real; las cuentas sintéticas no son personas.
7. **Las 30 000 cuentas sintéticas** nunca se cuentan como adopción ni como personas.
8. **Escala:** el token de dispositivo se pide una vez; un solo SSE por pestaña; geometría cacheada un día; reintentos respetando `Retry-After`.
9. **Fechas en hora de Cartagena** (UTC-5, sin horario de verano), con conversión explícita en los campos `datetime-local`.
10. **Identidad visual** ([`identidad.md`](../diseno/identidad.md)): cuatro estados, regla de los cinco segundos (ADR-079), el veredicto arriba. No se añaden librerías de interfaz.
11. **Tipos solo generados:** `npm run api:sync`; nunca se escriben tipos de la API a mano.

---

## 7. Trampas conocidas

| Trampa | Qué pasa | Qué hacer |
|---|---|---|
| Pedir un token de dispositivo en cada reporte | A los ~10 `POST /api/dispositivos` por hora desde una IP, `429` | Pídelo **una vez** y guárdalo. En CI y local, `RATE_LIMIT_FACTOR=100` |
| Reportar sin `X-Dispositivo` | `401 dispositivo-invalido` | Pide otro token y reintenta una sola vez; **no** cierres la sesión de un vecino por este error |
| Votar al cargar la pantalla «¿ya volvió?» o al abrir `/confirmar/:id` | Los clientes de correo abren los enlaces y votarían solos | El `POST` solo al pulsar el botón |
| `<img src>` para fotos del panel | No lleva `Authorization`: 401 | `fetch` con la cabecera y `URL.createObjectURL` |
| Pedir la foto de un reporte `EN_REVISION` | `404` | Muestra «en revisión» y no pidas la imagen |
| La coordenada que ves en un reporte | Es una aproximación de ~110 m | No prometas precisión de calle |
| `GET /api/cumplimiento` con `400` | No hay ni un cierre: no es un error | Pide `/api/cumplimiento/calidad` y di con cifras cuánto falta |
| Docker Desktop: todos los clientes llegan con la IP de la pasarela | Los límites por IP son un tope global en local | Es un límite conocido; no lo «arregles» en el cliente |
| Base distinta en cada ejecución de `e2e/real` | Con `INGESTA_MODO=auto` la ingesta trae boletines de Internet | Usa `INGESTA_MODO=local` |
| La lista de cuentas del panel trae 30 000 sintéticas | Una tabla enorme y lenta | Pagina por cabeceras y oculta las `sintetica: true` por defecto |
| Sesión de 8 h sin renovación | Un `401` a media tarea | Vuelve al ingreso y conserva lo que el usuario escribía si puedes |
| `/api/analitica/**` da `503` | PostgreSQL apagado o sin primera copia (la primera tarda ~1 min tras arrancar) | No es un error de la aplicación (§4.12) |
| Quórum que no cambia el mapa en local | Falta verificación o una segunda red | En local `AGUAVIGIA_CONSENSO_REDES_MINIMAS=1`; mira `respaldo` y la composición del sustento |
| La simulación y la real a la vez | Dos backends, dos bases | Mira siempre el banner y `GET /api/sistema/modo` |

---

## 8. Decisiones abiertas

| # | Decisión | Propuesta | Quién |
|---|---|---|---|
| D-A | ¿Hay pantalla para `/api/analitica/cumplimiento` y `/mensual`? | **No**: duplican `/api/cumplimiento`, van hasta 5 min atrasadas y pueden dar 503. Se excluyen en FE7 | Yordy confirma; Sebastian lo anota |
| D-B | ¿El bloque «Copia analítica» de FE5 se hace en esta entrega? | **Sí**, es pequeño y es la única ruta de PostgreSQL con valor para el panel | Yordy |
| D-C | FE6b: que los correos de cuenta apunten a la SPA en vez de a las páginas HTML del backend | **Después** de FE7 y solo si sobra tiempo: exige cambiar `MailCuentaAdapter` | Sebastian + Yordy |
| D-D | La guía `docs/diseno/guia-frontend.md` cita archivos retirados (`docs/ingenieria/plan-frontend.md`, `docs/design-decisions.md`, `docs/brief.md`) | Cuando toques §5 en FE1, limpia esos enlaces o apúntalos a este plan | Yordy |
| D-E | `docs/api/README.md` aún habla de `/fotos/…` y de `plan-frontend.md` | Es del backend: lo corrige Sebastian | Sebastian |

---

## 9. Checklist de entrega

Marca cada línea con fecha y PR cuando se cumpla.

- [ ] FE0 · contrato al día · CI en verde
- [ ] FE1 · lo público, completo
- [ ] FE2 · ingreso al panel (rama `feat/f5-ingreso-panel` archivada)
- [ ] FE3 · moderación y cortes
- [ ] FE4 · ingesta
- [ ] FE5 · administración (con el bloque «Copia analítica»)
- [ ] FE6 · cuentas públicas y vecino
- [ ] FE7 · integración y entrega
- [ ] Cobertura del contrato sin huecos fuera de las exclusiones
- [ ] axe en verde; prueba de los cinco segundos anotada
- [ ] Guion de la demo ejecutado de principio a fin sobre un `docker compose up` limpio
- [ ] Frontend CI en verde en `main` (los dos jobs)

---

## 10. Glosario

| Término | Significa |
|---|---|
| **Corte** | Anuncio de que un conjunto de barrios se queda sin agua entre dos instantes |
| **Par corte-barrio** | La unidad que mide el Índice: un corte en un barrio, con su duración prometida y real |
| **Cierre** | La hora real en que vuelve el agua a **un** barrio de un corte; puede ser `provisional` |
| **Índice de Cumplimiento** | Duración prometida frente a real: se suman duraciones y el porcentaje es `min(100, prometida × 100 / real)` |
| **Quórum** | Los reportes de vecinos que hacen falta (y con qué composición) para que un barrio cambie de estado |
| **Disputa** | Los vecinos contradicen a la fuente oficial: el barrio se marca, el color no cambia |
| **Sintética** | Cuenta de vecino que crea el propio sistema para probar el volumen; no es una persona |
| **Copia analítica** | La copia del Índice en PostgreSQL (ADR-100); derivada, atrasada hasta 5 min, opcional |
| **Veedor** | Persona del panel que modera reportes, registra cortes y revisa la ingesta |
| **ADR** | Registro de una decisión de arquitectura; están todos en [`07-decisiones-clave.md`](../07-decisiones-clave.md) |
