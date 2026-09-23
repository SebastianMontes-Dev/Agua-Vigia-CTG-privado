# Respaldo y restauración

> Cierra el hueco señalado en la revisión de operación de 2026-08-10: no había respaldo ni
> restauración documentados para Mongo, y las fotos de M10 no sobrevivían un redespliegue (ya
> corregido con el volumen `fotos-data` en `docker-compose.yml`/`docker-compose.prod.yml`).

## 1. Qué se respalda y por qué son dos cosas distintas

| Dato | Dónde vive | Script | Por qué no basta con uno solo |
|---|---|---|---|
| Reportes, sectores, cortes, bitácora, suscripciones | Volumen `mongo-data` (Mongo) | `scripts/backup-mongo.sh` | `mongodump` no toca archivos fuera de la base de datos |
| Fotos de evidencia (M10) | Volumen `fotos-data` (filesystem del contenedor `backend`) | `scripts/backup-fotos.sh` | Los binarios no están en Mongo — solo su URL relativa (`fotoUrl`) |

Un respaldo de Mongo sin el de fotos deja `fotoUrl` apuntando a archivos que no existen tras
restaurar. Corren siempre juntos (ver §3).

## 2. Cómo funcionan

Ambos scripts pasan por `docker compose exec` contra el servicio ya corriendo — ninguno expone un
puerto extra al host ni depende de saber el nombre real del volumen Docker (que varía con
`COMPOSE_PROJECT_NAME`):

- `backup-mongo.sh` corre `mongodump --archive --gzip` dentro del contenedor `mongo` y redirige su
  salida (stdout) a un archivo en el host.
- `backup-fotos.sh` empaqueta `/app/data/fotos` con `tar` dentro del contenedor `backend`, mismo
  patrón.

Ambos podan respaldos más viejos que la retención configurada (30 días por defecto) en cada
corrida — no hace falta un cron aparte para la limpieza.

```bash
./scripts/backup-mongo.sh ./respaldos-mongo 30
./scripts/backup-fotos.sh ./respaldos-fotos 30
```

`./respaldos-mongo/` y `./respaldos-fotos/` están en `.gitignore` — nunca se comitean (son datos
de producción, potencialmente con coordenadas y confirmaciones ciudadanas).

## 3. Programación (cron)

En el host de producción, diario fuera de horas pico:

```cron
# /etc/cron.d/aguavigia-respaldos — ajustar la ruta del repo
0 4 * * * deploy cd /opt/aguavigia && ./scripts/backup-mongo.sh /var/backups/aguavigia/mongo 30 >> /var/log/aguavigia-backup.log 2>&1
15 4 * * * deploy cd /opt/aguavigia && ./scripts/backup-fotos.sh /var/backups/aguavigia/fotos 30 >> /var/log/aguavigia-backup.log 2>&1
```

`/var/backups/aguavigia/` debe vivir fuera del volumen Docker y, idealmente, sincronizarse a
almacenamiento externo (S3, bucket del proveedor, otro host) — un respaldo que solo existe en el
mismo disco que la base de datos no protege contra la falla más común: perder el disco entero.
Eso queda fuera del alcance de este script (RNF021 — bucket, aún pendiente).

## 4. Restauración

```bash
./scripts/restore-mongo.sh ./respaldos-mongo/aguavigia-mongo-20260810T040000Z.archive.gz
./scripts/restore-fotos.sh ./respaldos-fotos/aguavigia-fotos-20260810T041500Z.tar.gz
```

Ambos son **destructivos** (`mongorestore --drop` en un caso, vaciar `/app/data/fotos` en el
otro) y piden escribir `restaurar` para confirmar. Restaurar Mongo sin restaurar el respaldo de
fotos correspondiente (mismo rango de tiempo) deja `fotoUrl` huérfanas — no hay validación
automática de que ambos respaldos vengan del mismo momento, así que verificar la marca de tiempo
en el nombre del archivo antes de correr ambos scripts.

## Autenticación de Mongo

En producción Mongo exige usuario (`MONGO_INITDB_ROOT_USERNAME` y `MONGO_INITDB_ROOT_PASSWORD`). Los dos scripts leen
esas credenciales **dentro del contenedor**, donde ya están como variables de entorno: no pasan por la línea de comandos
del host. Antes de `BUG-088` ninguno de los dos funcionaba contra producción (fallaban con `Unauthorized`). El respaldo se
escribe primero a un `.parcial` y solo se renombra si `mongodump` terminó bien y `gzip -t` lo valida.

**Verificado el 2026-09-21** contra un Mongo desechable con autenticación: respaldo, restauración y `--drop`.
**Simulacro completo corrido el 2026-09-22** (§5) contra el compose de desarrollo. Sigue sin programarse el
respaldo: **nada** lo automatiza hoy — hay que crear la tarea de cron o del Programador de tareas.

## Cómo se respalda en este proyecto (local)

Al correr en el PC del equipo (`ADR-057`) no se programa una tarea automática: **se hace un respaldo a mano antes de cada
presentación o de tocar la base**, con `./scripts/backup-mongo.sh` (usa `docker-compose.prod.yml`; para el compose de
desarrollo, `COMPOSE_FILE=docker-compose.yml ./scripts/backup-mongo.sh`). Si algún día se despliega, programarlo a diario con
retención de 30 días fuera del servidor.

## 5. Simulacro de restauración

Un respaldo que nunca se restauró no es un respaldo confiable. Antes de depender de esto en
producción, y luego trimestralmente:

1. Restaurar el respaldo más reciente contra un `docker-compose.yml` local (no contra producción).
2. Levantar el backend contra esa base restaurada.
3. Verificar: `GET /api/sectores` devuelve datos, un reporte con foto conocida carga su imagen en
   `/fotos/<nombre>`, y `GET /api/bitacora` trae eventos.
4. Anotar la fecha del simulacro y cualquier hallazgo en `docs/gestion/` (bitácora de sesiones).

**Corrido el 2026-09-22** contra `docker-compose.yml` (dev), con el stack ya poblado por sembradores previos
(211 sectores, 20 001 usuarios, 117 eventos de bitácora, 1193 propuestas de ingesta, entre otras): respaldo con
`backup-mongo.sh`, restauración con `restore-mongo.sh` sobre el mismo stack, 21 534 documentos restaurados, 0
fallos. `GET /api/sectores` y `GET /api/bitacora` devolvieron 200 con el mismo contenido de antes de restaurar.
**Hallazgo:** ningún reporte ciudadano tenía `fotoUrl` en ese momento, así que el paso 3 no pudo probarse contra
una foto real — pendiente repetirlo cuando exista un reporte con evidencia fotográfica sembrada.

## 6. Qué NO cubre esto

- **Redis** (rate limiting, caché de sectores): deliberadamente sin respaldo — es estado
  desechable, se reconstruye solo (contadores de rate limit expiran por TTL, la caché de sectores
  se repuebla en la siguiente lectura).
- **Corrupción silenciosa no detectada**: los scripts no verifican la integridad del respaldo más
  allá de que `mongodump`/`tar` terminen sin error. El simulacro de §5 es la única verificación real.
- **Retención en almacenamiento externo**: `--mtime +N` solo poda el directorio local. Si se
  sincroniza a S3/bucket, ese destino necesita su propia política de ciclo de vida.
