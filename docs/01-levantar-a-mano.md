# 01 · Levantar AguaVigía a mano, servicio por servicio

Objetivo: arrancar el sistema de cero explicando qué hace cada pieza y cómo se comprueba que está viva.
Todo lo de esta guía se ejecutó el 2026-09-29 (hora de Cartagena) desde una instalación limpia: sin `.env` y sin volúmenes.
Requisitos: Docker Desktop. En PowerShell antiguo no existe `&&`: un comando por línea.

## 0. Partir de cero

```powershell
cd C:\Users\sabas\Documentos\Agua-Vigia-CTG
docker compose --profile demo --profile carga down -v
```

Borra contenedores **y volúmenes** (`mongo-data`, `redis-data`, `fotos-data`) solo de este proyecto. No hace falta `.env`:
el backend genera su secreto de sesión y la clave del primer ADMIN al arrancar.

## 1. MongoDB — la base de datos

```powershell
docker compose up -d mongo
```

Arranca con `--replSet rs0` pero **sin réplica iniciada todavía**. Comprobación (esperar a `healthy`, ~15 s):

```powershell
docker exec aguavigia-mongo mongosh --quiet --eval "db.adminCommand('ping')"
```

Resultado: `{ ok: 1 }`. Si se pregunta `rs.status()` responde `NotYetInitialized`.

## 2. Réplica de un solo nodo

```powershell
docker compose up mongo-init-replica
```

Contenedor de un solo uso: ejecuta `rs.initiate`. **Por qué:** sin conjunto de réplicas Mongo no admite transacciones
multi-documento, que el backend necesita. Salida esperada: `Replica set iniciado` y código de salida 0. Comprobación:

```powershell
docker exec aguavigia-mongo mongosh --quiet --eval "rs.status().members[0].stateStr"
```

Resultado: `PRIMARY` (conjunto `rs0`).

## 3. Redis y Mailhog

```powershell
docker compose up -d redis mailhog
docker exec aguavigia-redis redis-cli ping
```

- **Redis** (`:6379`): caché, límite de peticiones, ventana de consenso de reportes y pub/sub. Responde `PONG`.
- **Mailhog** (SMTP `:1025`, interfaz http://localhost:8025): captura los correos que envía el backend; nada sale a Internet.

## 4. Backend

```powershell
docker compose up -d --build backend
```

Compila la imagen (Docker multi-etapa) y arranca Spring Boot con el perfil `docker`. Tarda unos minutos la primera vez.
La API queda en **http://localhost:8081** (el contenedor escucha en 8080).

```powershell
curl.exe http://localhost:8081/actuator/health/readiness
```

Resultado: `{"status":"UP"}`.

### La clave del primer ADMIN

Con la colección `usuarios` vacía, al arrancar el backend crea `admin@aguavigia.local` con una clave **aleatoria que se
imprime una sola vez** en el log (`SembradorAdminInicial`). Para leerla:

```powershell
docker compose logs backend | findstr ADMINISTRADOR
```

Guárdala en tu gestor de contraseñas; no está en ningún archivo del repo. El ADMIN debe dar de alta un segundo factor (TOTP)
en su primer inicio de sesión. Detalle en `05-seguridad-y-claves.md`.

## 5. Datos base: los 211 barrios

La base arranca con 1 ADMIN y 0 barrios. Los barrios (geometrías y población) se siembran con el contenedor `sembrador`,
pidiéndole **solo** ese paso:

```powershell
docker compose run --rm --build sembrador sembrar-sectores
```

Resultado en Mongo (`aguavigia`): `sectores: 211`, `usuarios: 1`. `docker compose up` a secas tampoco siembra nada: el sembrador está bajo el perfil `siembra`. Solo con
`docker compose --profile siembra up` correría su paso `inicial`, que además crea 30 000 cuentas de demostración, cortes
históricos y reportes.

## 6. Estado final

| Servicio | Contenedor | Puerto | Comprobación |
|---|---|---|---|
| MongoDB 7 (`rs0`) | `aguavigia-mongo` | 27017 | `ping` → `ok: 1`; réplica `PRIMARY` |
| Redis 7 | `aguavigia-redis` | 6379 | `PONG` |
| Mailhog | `aguavigia-mailhog` | 1025 / 8025 | UI responde 200 |
| Backend | `aguavigia-backend` | 8081 | `readiness` → `UP` |

Colecciones tras el arranque: `usuarios`, `sectores`, `cortes`, `reportes`, `suscripciones`, `suscripciones_telegram`,
`tokens_cuenta`, `auditoria_cuentas`, `eventos_bitacora`, `propuestas_ingesta`, `documentos_fallidos`.

## 7. Apagar

```powershell
docker compose stop
```

Conserva los datos (volúmenes). Para empezar otra vez de cero, el `down -v` del paso 0.
