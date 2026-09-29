# Respaldo y restauración

> El proyecto corre solo en local (`ADR-057`, `ADR-080`): no hay servidor ni tarea programada. **Se hace un respaldo a mano
> antes de cada presentación, de una prueba de carga o de tocar la base**, y se restaura si algo sale mal.

## 1. Qué se respalda y por qué son dos cosas distintas

| Dato | Dónde vive | Script | Por qué no basta con uno solo |
|---|---|---|---|
| Reportes, sectores, cortes, bitácora, suscripciones, cuentas | Volumen `mongo-data` (Mongo) | `scripts/backup-mongo.sh` | `mongodump` no toca archivos fuera de la base de datos |
| Fotos de evidencia (M10) | Volumen `fotos-data` (filesystem del contenedor `backend`) | `scripts/backup-fotos.sh` | Los binarios no están en Mongo, solo su URL relativa (`fotoUrl`) |

Un respaldo de Mongo sin el de fotos deja `fotoUrl` apuntando a archivos que no existen tras restaurar: se hacen juntos.

## 2. Cómo funcionan

Los dos scripts pasan por `docker compose exec` contra el `docker-compose.yml` ya levantado: ninguno expone un puerto extra
ni depende del nombre real del volumen (que varía con `COMPOSE_PROJECT_NAME`).

- `backup-mongo.sh` corre `mongodump --archive --gzip` dentro del contenedor `mongo` y redirige la salida a un archivo del
  host. Escribe primero a un `.parcial` y solo lo renombra si `mongodump` terminó bien y `gzip -t` lo valida.
- `backup-fotos.sh` empaqueta `/app/data/fotos` con `tar` dentro del contenedor `backend`.

```bash
./scripts/backup-mongo.sh ./respaldos-mongo
./scripts/backup-fotos.sh ./respaldos-fotos
```

`./respaldos-mongo/` y `./respaldos-fotos/` están en `.gitignore`: nunca se comitean (tienen coordenadas y datos de cuentas).
Para usar otro compose, `COMPOSE_FILE=<archivo> ./scripts/backup-mongo.sh`.

## 3. Restauración

```bash
./scripts/restore-mongo.sh ./respaldos-mongo/aguavigia-mongo-20260929T090000.archive.gz
./scripts/restore-fotos.sh ./respaldos-fotos/aguavigia-fotos-20260929T090000.tar.gz
```

Los dos son **destructivos** (`mongorestore --drop` en un caso, vaciar `/app/data/fotos` en el otro) y piden escribir
`restaurar` para confirmar. No hay validación automática de que ambos respaldos sean del mismo momento: comprobar la marca de
tiempo del nombre antes de correrlos.

## 4. Simulacro de restauración

**Corrido el 2026-09-22** contra `docker-compose.yml`, con el stack poblado por los sembradores (211 sectores, 20 001
usuarios, 117 eventos de bitácora, 1 193 propuestas de ingesta, entre otras): respaldo, restauración sobre el mismo stack,
21 534 documentos restaurados, 0 fallos. `GET /api/sectores` y `GET /api/bitacora` devolvieron 200 con el mismo contenido.
**Pendiente:** ningún reporte tenía `fotoUrl` en ese momento, así que no se probó restaurar una foto real.

## 5. Qué NO cubre esto

- **Redis** (rate limiting, caché, consenso): sin respaldo a propósito. Es estado desechable que se reconstruye solo.
- **Corrupción silenciosa**: los scripts no verifican el respaldo más allá de que `mongodump`/`tar` terminen sin error. El
  simulacro es la única verificación real.
