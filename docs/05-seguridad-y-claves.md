# 05 · Seguridad y claves

Estado a 2026-10-15 (hora de Cartagena), contrastado con el código de `main`. **Aquí nunca van valores**:
las claves viven en el `.env` local (ignorado por git) o se generan al arrancar.

## 1. Cómo se protege el acceso

| Pieza | Cómo funciona |
|---|---|
| Sesión | JWT HS256 firmado con `JWT_SECRET` (≥ 32 bytes), vigencia 8 h. Cada petición con token consulta en Redis si fue revocado; si Redis cae, se deniega |
| Roles | Panel: `ADMIN`, `VEEDOR`, `OBSERVADOR`, más permisos sueltos; cada método de `/api/veedor/**` lleva `@PreAuthorize` contra un permiso concreto. `VECINO`: cuenta ciudadana, solo su perfil (`/api/vecino/**`), nunca permisos de panel |
| Identidad de quien reporta | El servidor emite un token de dispositivo firmado (`POST /api/dispositivos`, 10 por hora por IP) que viaja en `X-Dispositivo`; la colección `dispositivos` guarda solo su SHA-256 (ADR-090). Un vecino con sesión reporta con su cuenta |
| Segundo factor | TOTP (app de autenticación) con antirreplay: un código no se puede usar dos veces en 2 minutos |
| Bloqueo de cuenta | Por **cuenta y dirección**: 5 fallos en 15 min bloquean 15 min a quien falló, sin bloquear al titular. Además un tope global por cuenta (50 fallos) frena el ataque repartido entre muchas IP |
| Límite por IP | `/api/veedor/sesion`: 10 intentos / 5 min. **Límite conocido:** detrás de Docker Desktop todos los clientes llegan con la IP de la pasarela, así que aquí es un tope global |
| Enlaces de cuenta | Verificar, invitar y restablecer: token aleatorio, solo se guarda su hash, un solo uso. El marcado como usado es atómico (dos peticiones con el mismo enlace: gana una) |
| Rutas | **Denegadas por defecto.** Son públicas solo las de `RUTAS_PUBLICAS` en `SecurityConfig` (lectura, reportes, dispositivos, suscripciones, IoT, Open311 en `/api/v2`, simulación en `/api/sim` —que solo existe en esa instancia y además pide `X-Sim-Key`—, salud y Swagger fuera de `prod`), más `/api/cuentas/**`, el inicio de sesión del panel y del vecino, `GET /api/fotos/**` y `GET /api/sistema/modo` |
| Cabeceras | `Content-Security-Policy` estricta (salvo Swagger UI), `Referrer-Policy: no-referrer`, `X-Content-Type-Options`, `X-Frame-Options: DENY`, HSTS bajo HTTPS |
| Fotos de evidencia | Lista blanca de tipo, verificación de firma de bytes, nombre UUID; **una foto por reporte, no se reemplaza** |
| Sensores IoT | Cabecera `X-IoT-Key`, comparación en tiempo constante; identificador de sensor acotado (≤ 64, alfabeto limitado), presión y coordenadas con rango |

## 2. De dónde salen las claves

| Secreto | Origen | Si falta |
|---|---|---|
| `JWT_SECRET` | Perfil `docker`: aleatorio en cada arranque (las sesiones no sobreviven a un reinicio). Perfil `prod`: obligatorio | En `prod` el backend **no arranca** (`ValidacionDeSecretos`) |
| Clave del primer ADMIN | Con `usuarios` vacía, el backend crea `admin@aguavigia.local` con clave aleatoria y la imprime **una sola vez** en el log. Si el `.env` define `VEEDOR_PASSWORD_HASH` (y `ADMIN_INICIAL_CORREO`), usa ese hash y **no imprime nada** | `scripts/restablecer-admin.mjs` (solo contra base local) |
| `IOT_KEY` | Opcional. Si se define debe medir **≥ 32 caracteres** (p. ej. `openssl rand -hex 16`) o el backend no arranca | Vacía: `/api/iot/presion` responde 503 |
| `TELEGRAM_BOT_TOKEN` | `.env` | Vacío: el canal queda apagado |
| Clave de las cuentas sintéticas de vecino | **Nadie la conoce.** El backend genera una contraseña aleatoria, guarda solo su hash BCrypt (uno, compartido) y descarta la contraseña: esas cuentas no pueden iniciar sesión (ADR-094) | — |
| Clave de `sembrar-usuarios-demo.mjs` (cuentas de panel a mano, ya fuera del arranque) | **No está en el repo.** Sale de `CLAVE_DEMO` si la defines, o se genera al azar y se imprime una vez | Volver a correrlo |

Leer la clave del ADMIN (PowerShell): `docker compose logs backend | findstr ADMINISTRADOR`. Guárdala en tu gestor de contraseñas.

## 3. Exposición de la red

Todos los puertos de `docker-compose.yml` se publican solo en `127.0.0.1` (Mongo 27017, Redis 6379, PostgreSQL 5433, Mailhog 1025/8025, API 8081,
API de la simulación 8082 —solo con el perfil `simulacion`—, visor 8083). Dentro de la red interna de compose, Mongo escucha en todas sus interfaces para que el backend lo alcance; hacia la
red del aula no se ve. Mongo y Redis siguen **sin autenticación**: es aceptable solo porque no salen del equipo. PostgreSQL (copia analítica, ADR-100) recibe el mismo trato, ver §5.

## 4. Perfiles

- `docker` (local): Swagger UI y `/v3/api-docs` públicos, CORS solo a los puertos habituales del frontend en localhost.
- `prod`: Swagger cerrado, sin orígenes CORS por defecto (`CORS_ORIGENES` explícito), `ValidacionDeSecretos` exige `JWT_SECRET`.

## 5. Pendiente o aceptado

- **PostgreSQL sin contraseña (`trust`), riesgo aceptado (D1, decidido el 2026-10-15).** Un arranque sin `.env` (ADR-086) choca con que PostgreSQL pida una clave al crearse, y no se pueden
  versionar secretos. Se trata como Mongo y Redis: puerto solo en `127.0.0.1` y red interna de compose, y la base no guarda datos personales (solo barrios, cortes y duraciones). Descartado:
  `POSTGRES_PASSWORD` obligatorio en `.env`, que rompe el `docker compose up` sin configuración. Nunca se escribe una clave en `docker-compose.yml`.

- **Secreto TOTP en claro en Mongo.** Cifrarlo en reposo exige una clave que sobreviva a los reinicios; no hay dónde guardarla sin
  romper el arranque sin `.env`. Decisión pendiente del dueño.
- **Identidades de dispositivo:** ya no las elige el cliente (ADR-090), pero alguien puede pedir hasta 10 por hora desde una IP y repartir reportes entre ellas.
  Lo frenan el límite por IP y la composición del quórum (un tercio del sustento verificado por ubicación y un mínimo de redes distintas, que el compose
  local deja en 1 con `AGUAVIGIA_CONSENSO_REDES_MINIMAS`); la reputación por dispositivo
  no se construyó (ADR-097 §3).
- **Pendientes de la revisión de F6** (ADR-097): endpoints públicos sin límite propio, SSE sin tope por IP y Trivy solo sobre el backend.
- **`IOT_KEY` es una clave compartida**, no se puede rotar por sensor.
- **Historial de git:** una clave de desarrollo y una clave de demostración estuvieron publicadas en un repo público. Ya no están en
  ningún archivo vigente y se consideran expuestas (no usarlas jamás); seguirán en el historial mientras no se reescriba.
  El valor ya no está ni en `.gitleaks.toml`: el escaneo del historial lo exime con cuatro huellas de commit y línea en `.gitleaksignore`, que no lo contienen (ADR-097 §5).
