# Guion de demo — AguaVigía CTG

> **Para qué sirve.** Es el orden para presentar el sistema funcionando en local (`ADR-057`), contra el
> backend (`REC-018`, aceptada el 2026-09-24). Cada comando de las secciones 1–5 se ejecutó el 2026-09-24 contra el
> entorno descrito abajo; las salidas son las reales de esa corrida, no inventadas. El frontend nuevo (`frontend/`,
> `ADR-067`) se enseña en la sección 7; el panel del veedor (F5) aún no tiene interfaz y se muestra con Swagger.
> Duración: unos 10 minutos.

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
- **Copia de la base de la demo** (211 sectores, histórico y cuentas): la de la corrida del 2026-09-24, con 40 001 cuentas sin barrio, está en `C:\Users\sabas\Documentos\respaldos-aguavigia\aguavigia-demo-2026-09-24.archive.gz`, fuera del repo. La base de hoy tiene **30 000 cuentas completas** (barrio, segundo factor, tokens y auditoría; `scripts/sembrar-usuarios-demo.mjs`) y la demo de carga (sección 7) hace su propio respaldo en `respaldos-mongo/` (ignorado por git). Para volver a un respaldo (sustituye la base actual):
  `docker exec -i aguavigia-mongo mongorestore --archive --gzip --drop < <ruta del archivo>`. Contiene los datos de cuentas, incluido el hash del ADMIN: no la subas a git.
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
curl -s "localhost:8081/api/bitacora?sectorId=albornoz&tamano=1"
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
curl -s localhost:8081/api/cumplimiento/serie          # por mes: 2026-05 en adelante (los meses de la siembra más los que tengan cortes reales o de prueba)
curl -s -o cumplimiento.csv localhost:8081/api/cumplimiento/serie.csv
curl -s localhost:8081/api/estadisticas                # sectores más afectados, cortes por día, duración media
```

**Qué decir:** compara lo **prometido** con lo **real**, sumando duraciones (`ADR-022`), no promediando
porcentajes. El valor global se contrastó con un cálculo independiente en `mongosh` sobre los mismos
documentos y coincidió. **Los cortes históricos son sintéticos** (siembra aleatoria de mayo–julio 2026); los
reales entran por la ingesta de Acuacar (sección 5).

## 5. Ingesta de boletines reales (Acuacar)

```bash
curl -s "localhost:8081/api/bitacora?tamano=2"    # eventos CORTE_DETECTADO_POR_INGESTA con urlOriginal al boletín
```

**Qué decir:** nada llega al mapa sin verificación; el extractor debe citar la frase exacta del boletín (es una
heurística determinista, sin IA: `ADR-025`; ética de datos, `ADR-005` y `ADR-006`). El colector se identifica con su `User-Agent` y respeta `robots.txt`.

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

## 7. La ciudad entera reportando a la vez (demo de carga)

Es la parte que responde «¿y si lo usa toda la ciudad?». Un solo comando abre 30 000 conexiones en vivo, dispara 30 000
reportes en un minuto y deja ver tres cosas a la vez (`ADR-083`):

| Qué se ve | Dónde |
|---|---|
| El mapa cambiando de color barrio por barrio, por SSE, a medida que el consenso real decide | `http://localhost:5173`: `cd frontend && npm run dev` |
| El panel en vivo de k6: peticiones por segundo, latencia, errores | `http://localhost:5665` (aparece al arrancar la prueba) |
| El resumen final en la consola: reportes, latencia, conexiones, CPU y memoria, barrios que cambiaron | la terminal donde se lanzó |

```bash
node scripts/carga/demo.mjs --usuarios 30000 --ventana 60 --conectados 30000 --restaurar --esperar
```

- **Qué hace:** comprueba el stack y las 30 000 cuentas (si faltan, las siembra), hace un respaldo de Mongo, reinicia el backend
  con el perfil `carga` (sin límite de peticiones por IP: todo el tráfico sale de este PC), abre las conexiones, lanza k6 dentro
  de la red de Docker y, al terminar, devuelve el backend a su perfil normal. **`--restaurar`** deja además Mongo y Redis como
  estaban, para poder repetirlo; **`--esperar`** se detiene antes de disparar, para abrir el mapa y el panel de k6.
- **Todo es parametrizable:** `--usuarios 10000`, `--ventana 30`, `--conectados 10000`, `--focos 12` (barrios con avería masiva
  que se encienden uno tras otro) y `--lectores`, `--veedores`, `--suscripciones` por segundo. Con `--conectados 0` no abre el
  canal en vivo. El informe HTML de k6 queda en `resultados/<fecha>/`.
- **Qué dijo la corrida de referencia (2026-09-29, tres veces, desde el mismo punto de partida):** 30 000 reportes aceptados,
  p95 de un reporte entre 130 y 164 ms (el umbral de `RNF002` es 1 s), como mucho un `503` suelto por espera de Mongo, 30 000 conexiones abiertas, ≈ 1,6 millones de avisos
  entregados. **Lee las cifras reales en pantalla, no las cites de este archivo.**
- **Dilo antes de que pregunten:** el generador de carga y el backend comparten este PC (12 hilos); el backend llegó a ≈ 8
  núcleos y ≈ 6,4 GiB. Son cifras de un banco local, no de un servidor. Los 50 000 de `RNF027` no se demuestran aquí
  (`docs/ingenieria/escalabilidad.md`).
- **Ensayar sin ensuciar:** cada corrida cambia el estado de los barrios y deja decenas de miles de reportes. Con `--restaurar`
  todo vuelve a como estaba. Sin él, una segunda corrida seguida encuentra pocos barrios que puedan cambiar y el script lo
  avisa; para volver atrás a mano, `./scripts/restore-mongo.sh` con el archivo que imprimió al empezar.
- **Si algo se queda a medias** (Ctrl+C, Docker reiniciado): `docker rm -f $(docker ps -aq --filter name=aguavigia-carga-)` y
  `docker compose up -d --force-recreate backend` para volver al perfil normal.

---

## Lo que NO se puede mostrar (decirlo antes de que pregunten)

| Tema | Estado |
|---|---|
| **50 000 usuarios simultáneos** (`RNF027`) | No se demuestran en un PC (`ADR-057`). Sí se demuestran **30 000** a la vez con la demo de la sección 7, dicho como banco local compartido con el generador, no producción. |
| **`RF041`** alertas por Telegram | Construido y armado, pero **apagado**: falta el bot real (`TELEGRAM_BOT_TOKEN`, ver `telegram.md`). No se muestra en vivo hasta conectarlo. WhatsApp no existe. |
| **IoT (`RF040`)** | Solución que se implementaría en físico: el endpoint `POST /api/iot/presion` existe y está probado, pero no hay sensores instalados. Para probarlo en local hay que dar valor a `IOT_KEY` en `.env` y mandarlo en `X-IoT-Key`; vacía, responde 503. |
| **Confirmar un reporte (`RF038`) no mueve el mapa** | Decisión mantenida (`BUG-114`): la confirmación suma al conteo `confirmaciones` del reporte, una vez por dispositivo, pero no entra al consenso; solo los reportes originales cuentan. |
| **TLS, dominio, CDN, hosting** | No existen por decisión del proyecto (`ADR-057`). |
| **Interfaz web completa** | El frontend nuevo (`ADR-067`) cubre el mapa, la historia pública y los avisos (F2–F4); el panel del veedor y las cuentas (F5) aún no tienen pantalla y se demuestran con Swagger y `curl`. |
| **Datos históricos reales** | Los de mayo–julio son sintéticos. |

## Si algo falla en vivo

- **Backend con imagen vieja:** si CORS, la foto o «cerrar sesión» fallan tras traer cambios de `main`, reconstruye con `docker compose up -d --build backend` (el 2026-09-24 eso resolvió los tres).
- Docker Desktop apagado: `docker info` falla; ábrelo y espera a que `docker ps` muestre los cuatro contenedores sanos.
- `429` al reportar: límite por dispositivo o por IP; cambia la huella o espera (`Retry-After`).
- El sector no cambia: cuenta los reportes de la ventana de 30 min; un empate no cambia el estado.
