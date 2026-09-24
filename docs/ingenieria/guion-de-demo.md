# Guion de demo — AguaVigía CTG

> **Para qué sirve.** Es el orden para presentar el sistema funcionando en local (`ADR-057`), contra el
> backend y sin frontend propio (`REC-018`, aceptada el 2026-09-24). Cada comando de las secciones 1–5 se
> ejecutó el 2026-09-24 contra el entorno descrito abajo; las salidas son las reales de esa corrida, no
> inventadas. Duración: unos 10 minutos.

**Entorno de la corrida:** `docker compose up` con Mongo, Redis, MailHog y backend sanos, 211 sectores
sembrados y el histórico de `scripts/sembrar-historico-cortes.mjs` cargado. El backend queda en
`http://localhost:8081`.

---

## 0. Antes de presentar

```bash
docker compose up -d --build --wait
cd scripts && npm install && node sembrar-sectores.mjs && node sembrar-historico-cortes.mjs && cd ..
curl -s localhost:8081/actuator/health/readiness        # {"status":"UP"}
```

- **La siembra histórica es aleatoria** (`Math.random()`): las cifras del índice cambian en cada corrida. En
  la corrida de referencia dieron 99,62 % global; **lee el valor real en pantalla, no lo cites de este archivo.**
- **Sembrar borra los cortes y reportes de mayo–julio 2026** que hubiera antes (`deleteMany` del script).
- Abre en pestañas: `http://localhost:8081/swagger-ui.html` y MailHog `http://localhost:8025`.

## 1. El problema: «¿tengo agua o no?» (mapa y estado)

```bash
curl -s localhost:8081/api/sectores/bocagrande
# {"id":"bocagrande","nombre":"BOCAGRANDE","poblacion":5583,"estado":null,"actualizadoEn":null}
curl -s localhost:8081/api/sectores/geometria | head -c 150      # GeoJSON: un Feature por sector
```

**Qué decir:** el `estado` es `null` = «sin datos». El sistema **no afirma servicio normal sin verificarlo**;
ese falso positivo es justo lo que el proyecto existe para evitar.

## 2. Consenso ciudadano en vivo (el momento fuerte)

Umbral: `max(3, ceil(población × 0,001))` vecinos distintos en 30 min. `albornoz` (853 hab.) necesita 3.
En una terminal, deja abierto el aviso en tiempo real (SSE):

```bash
curl -N localhost:8081/api/sectores/stream
```

En otra, tres reportes de dispositivos distintos (la huella son 32–128 caracteres):

```bash
for i in 1 2 3; do
  curl -s -X POST localhost:8081/api/reportes -H 'Content-Type: application/json' \
    -d "{\"tipo\":\"SIN_AGUA\",\"huella\":\"demo0$i-00000000000000000000000000\",\"sectorId\":\"albornoz\"}"
done
curl -s localhost:8081/api/sectores/albornoz
# {"id":"albornoz",...,"estado":"SIN_SERVICIO","actualizadoEn":"2026-09-24T12:20:30.694Z"}
```

**Resultado esperado:** los tres responden `201`; el SSE emite `event:sectores` (solo avisa, no trae datos);
el sector pasa a `SIN_SERVICIO`. Con menos de 3 no cambia nada.

```bash
curl -s "localhost:8081/api/bitacora?sectorId=albornoz&size=1"
# tipo CORTE_CONFIRMADO_POR_CIUDADANOS, cantidadReportesSustento 3
```

**Qué decir:** cada cambio automático queda en la bitácora pública con los reportes que lo sustentan.

### Dejar el sector como estaba (para repetir la demo)

El estado sale **por mayoría** de los reportes de la ventana de 30 min, no por el último. Con 3 «sin agua» en
la ventana, **3 de «restablecido» empatan y no cambian nada; hace falta un cuarto** (comprobado el 2026-09-24):

```bash
for i in 1 2 3 4; do
  curl -s -o /dev/null -X POST localhost:8081/api/reportes -H 'Content-Type: application/json' \
    -d "{\"tipo\":\"SERVICIO_RESTABLECIDO\",\"huella\":\"rest0$i-0000000000000000000000000\",\"sectorId\":\"albornoz\"}"
done
```

**Límite por dispositivo:** 3 reportes por huella y sector cada 30 min (`429`). Para repetir la demo en el mismo
sector cambia el prefijo de la huella, o espera a que la ventana los deje fuera.

## 3. Correo a suscriptores (MailHog)

Ensayado a mano el 2026-09-24. Usa un sector con umbral bajo (`alcibia`, 3 055 hab. → 4 reportes) y un correo
inventado; el correo nunca sale de tu máquina, llega a MailHog.

```bash
curl -s -X POST localhost:8081/api/suscripciones -H 'Content-Type: application/json' \
  -d '{"correo":"vecino@ejemplo.test","sectorIds":["alcibia"]}'      # 201, estado PENDIENTE_CONFIRMACION
```

Abre `http://localhost:8025`: llega «Confirma que quieres recibir los avisos de ALCIBIA» con el enlace
`/api/suscripciones/confirmar?token=…`. **Ese enlace abre una página con un botón; confirmar es un `POST`**
(un `GET` no confirma, para que un escáner de correo no lo haga por el vecino). Desde consola:

```bash
curl -s -X POST "localhost:8081/api/suscripciones/confirmar?token=<el token del enlace>"    # 200, CONFIRMADA
for i in 1 2 3 4; do
  curl -s -o /dev/null -X POST localhost:8081/api/reportes -H 'Content-Type: application/json' \
    -d "{\"tipo\":\"SIN_AGUA\",\"huella\":\"ens0$i-00000000000000000000000000\",\"sectorId\":\"alcibia\"}"
done
```

**Resultado esperado:** `alcibia` pasa a `SIN_SERVICIO` y en MailHog llega **«Se fue el agua en ALCIBIA»** al
suscriptor. Para dejarlo como estaba, 5 reportes `SERVICIO_RESTABLECIDO` (mayoría: 4 «sin agua» ya están en la
ventana). Cada enlace de correo trae además `/cancelar?token=…` para darse de baja.

## 4. El diferencial: Índice de Cumplimiento

```bash
curl -s localhost:8081/api/cumplimiento
# global: duracionPrometidaSegundos / duracionRealSegundos / porcentajeCumplimiento (99,62 en la corrida)
curl -s localhost:8081/api/cumplimiento/sectores/ciudadela-11-de-noviembre
curl -s localhost:8081/api/cumplimiento/serie          # por mes: 2026-05, 2026-06, 2026-07, 2026-08
curl -s -o cumplimiento.csv localhost:8081/api/cumplimiento/serie.csv
curl -s localhost:8081/api/estadisticas                # sectores más afectados, cortes por día, duración media
```

**Qué decir:** compara lo **prometido** con lo **real**, sumando duraciones (`ADR-022`), no promediando
porcentajes. El valor global se contrastó con un cálculo independiente en `mongosh` sobre los mismos
documentos y coincidió. **Los cortes históricos son sintéticos** (siembra aleatoria de mayo–julio 2026); los
reales entran por la ingesta de Acuacar (sección 5).

## 5. Ingesta de boletines reales (Acuacar)

```bash
curl -s "localhost:8081/api/bitacora?size=2"    # eventos CORTE_DETECTADO_POR_INGESTA con urlOriginal al boletín
```

**Qué decir:** nada llega al mapa sin verificación; la IA debe citar la frase exacta del boletín (ética de
datos, `ADR-005` y `ADR-006`). El colector se identifica con su `User-Agent` y respeta `robots.txt`.

## 6. Panel del veedor

Recorrido completo verificado el 2026-09-24 con `scripts/verificar-flujos.mjs`: **21 pasos, 0 fallos**. Sus seis
pasos del panel: login del ADMIN con segundo factor (TOTP), `/api/veedor/yo` (ADMIN con 7 permisos), registrar y cerrar
un corte oficial, cola de moderación e ingesta, invitar y aceptar una cuenta con auditoría (el OBSERVADOR recibe 403
al gestionar cortes) y cerrar sesión, que revoca el token.

```powershell
$env:ADMIN_CLAVE='<la clave del ADMIN>'
$env:TOTP_SECRETO='<el secreto TOTP, si ya se dio de alta>'
node scripts/verificar-flujos.mjs
```

- **Cuenta:** `veedor@aguavigia.local` (rol ADMIN). Dónde vive la clave y el secreto: [`credenciales-y-accesos.md`](credenciales-y-accesos.md).
- **No repetir el script en seguida:** cada corrida gasta parte de los límites por IP (suscripciones: 10 por 10 min;
  login: 5 por 5 min). Un `429` no es un fallo del sistema: espera 10 minutos.
- **Para mostrarlo en vivo** usa Swagger con el token de `POST /api/veedor/sesion` (con el segundo factor ya dado de alta, el cuerpo del login lleva
  además `codigoTotp`; la primera vez, sin alta, devuelve `alcance: ALTA_SEGUNDO_FACTOR`).

---

## Lo que NO se puede mostrar (decirlo antes de que pregunten)

| Tema | Estado |
|---|---|
| **50 000 usuarios simultáneos** (`RNF027`) | No se demuestra en local (`ADR-057`). Se muestra la arquitectura preparada y las mediciones locales de `scripts/carga/`, dichas como banco local, no producción. |
| **`RF041`** webhook de WhatsApp/Telegram | Sin construir: depende de credenciales de terceros. |
| **TLS, dominio, CDN, hosting** | No existen por decisión del proyecto (`ADR-057`). |
| **Interfaz web** | No hay frontend en `main` (`ADR-048`); se demuestra la API con Swagger y `curl`. |
| **Datos históricos reales** | Los de mayo–julio son sintéticos. |

## Si algo falla en vivo

- **Backend con imagen vieja:** si CORS, la foto o «cerrar sesión» fallan tras traer cambios de `main`, reconstruye con `docker compose up -d --build backend` (el 2026-09-24 eso resolvió los tres).
- Docker Desktop apagado: `docker info` falla; ábrelo y espera a que `docker ps` muestre los cuatro contenedores sanos.
- `429` al reportar: límite por dispositivo o por IP; cambia la huella o espera (`Retry-After`).
- El sector no cambia: cuenta los reportes de la ventana de 30 min; un empate no cambia el estado.
