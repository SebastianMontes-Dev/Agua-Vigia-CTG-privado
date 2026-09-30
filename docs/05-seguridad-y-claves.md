# 05 · Seguridad y claves

Estado a 2026-09-29 (hora de Cartagena), tras la auditoría del backend y sus correcciones. **Aquí nunca van valores**:
las claves viven en el `.env` local (ignorado por git) o se generan al arrancar.

## 1. Cómo se protege el acceso

| Pieza | Cómo funciona |
|---|---|
| Sesión | JWT HS256 firmado con `JWT_SECRET` (≥ 32 bytes), vigencia 8 h. Cada petición con token consulta en Redis si fue revocado; si Redis cae, se deniega |
| Roles | `ADMIN`, `VEEDOR`, `OBSERVADOR`, más permisos sueltos. Cada método de `/api/veedor/**` lleva `@PreAuthorize` contra un permiso concreto |
| Segundo factor | TOTP (app de autenticación) con antirreplay: un código no se puede usar dos veces en 2 minutos |
| Bloqueo de cuenta | Por **cuenta y dirección**: 5 fallos en 15 min bloquean 15 min a quien falló, sin bloquear al titular. Además un tope global por cuenta (50 fallos) frena el ataque repartido entre muchas IP |
| Límite por IP | `/api/veedor/sesion`: 10 intentos / 5 min. **Límite conocido:** detrás de Docker Desktop todos los clientes llegan con la IP de la pasarela, así que aquí es un tope global |
| Enlaces de cuenta | Verificar, invitar y restablecer: token aleatorio, solo se guarda su hash, un solo uso. El marcado como usado es atómico (dos peticiones con el mismo enlace: gana una) |
| Rutas | **Denegadas por defecto.** Solo son públicas las de `RUTAS_PUBLICAS` en `SecurityConfig` (lectura, reportes, avisos, IoT, salud) y `/api/cuentas/**` |
| Cabeceras | `Content-Security-Policy` estricta (salvo Swagger UI), `Referrer-Policy: no-referrer`, `X-Content-Type-Options`, `X-Frame-Options: DENY`, HSTS bajo HTTPS |
| Fotos de evidencia | Lista blanca de tipo, verificación de firma de bytes, nombre UUID; **una foto por reporte, no se reemplaza** |
| Sensores IoT | Cabecera `X-IoT-Key`, comparación en tiempo constante; identificador de sensor acotado (≤ 64, alfabeto limitado), presión y coordenadas con rango |

## 2. De dónde salen las claves

| Secreto | Origen | Si falta |
|---|---|---|
| `JWT_SECRET` | Perfil `docker`: aleatorio en cada arranque (las sesiones no sobreviven a un reinicio). Perfil `prod`: obligatorio | En `prod` el backend **no arranca** (`ValidacionDeSecretos`) |
| Clave del primer ADMIN | Con `usuarios` vacía, el backend crea `admin@aguavigia.local` con clave aleatoria y la imprime **una sola vez** en el log | `scripts/restablecer-admin.mjs` (solo contra base local) |
| `IOT_KEY` | Opcional. Si se define debe medir **≥ 32 caracteres** (p. ej. `openssl rand -hex 16`) o el backend no arranca | Vacía: `/api/iot/presion` responde 503 |
| `TELEGRAM_BOT_TOKEN` | `.env` | Vacío: el canal queda apagado |
| Clave de las cuentas de demostración | **No está en el repo.** Sale de `CLAVE_DEMO` si la defines, o se genera al azar en cada siembra y se imprime una vez | Resembrar |
| Tokens de invitación/verificación sembrados | Derivados de un secreto que solo existe durante esa ejecución; no se pueden calcular desde el repo | — |

Leer la clave del ADMIN (PowerShell): `docker compose logs backend | findstr ADMINISTRADOR`. Guárdala en tu gestor de contraseñas.

## 3. Exposición de la red

Todos los puertos de `docker-compose.yml` se publican solo en `127.0.0.1` (Mongo 27017, Redis 6379, Mailhog 1025/8025, API 8081,
visor 8082). Dentro de la red interna de compose, Mongo escucha en todas sus interfaces para que el backend lo alcance; hacia la
red del aula no se ve. Mongo y Redis siguen **sin autenticación**: es aceptable solo porque no salen del equipo.

## 4. Perfiles

- `docker` (local): Swagger UI y `/v3/api-docs` públicos, CORS solo a los puertos habituales del frontend en localhost.
- `prod`: Swagger cerrado, sin orígenes CORS por defecto (`CORS_ORIGENES` explícito), `ValidacionDeSecretos` exige `JWT_SECRET`.

## 5. Pendiente o aceptado

- **Secreto TOTP en claro en Mongo.** Cifrarlo en reposo exige una clave que sobreviva a los reinicios; no hay dónde guardarla sin
  romper el arranque sin `.env`. Decisión pendiente del dueño.
- **Huella del dispositivo enviada por el cliente:** el cupo de 3 reportes por dispositivo se evade rotándola; solo lo frena el límite por IP.
- **WebP** se guarda sin recomprimir (solo se valida la firma).
- **`IOT_KEY` es una clave compartida**, no se puede rotar por sensor.
- **Historial de git:** una clave de desarrollo y una clave de demostración estuvieron publicadas en un repo público. Ya no están en
  ningún archivo vigente y se consideran expuestas (no usarlas jamás); seguirán en el historial mientras no se reescriba.
  `.gitleaks.toml` conserva la exención del valor antiguo solo para que el escaneo del historial no falle.
