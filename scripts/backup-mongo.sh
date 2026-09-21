#!/usr/bin/env bash
set -euo pipefail

# Respaldo de Mongo via mongodump dentro del contenedor. No expone el puerto de Mongo al host ni
# depende de un volumen extra: --archive sale por stdout de `docker compose exec` y este script lo
# redirige a un archivo comprimido en el host. Pensado para cron/Task Scheduler contra
# docker-compose.prod.yml — ver docs/ingenieria/respaldo-y-restauracion.md.
#
# Uso: ./scripts/backup-mongo.sh [directorio-de-respaldos] [dias-de-retencion]
#
# Autenticacion: en produccion Mongo arranca con usuario root (MONGO_INITDB_ROOT_USERNAME/PASSWORD).
# Las credenciales se leen DENTRO del contenedor, donde ya estan como variables de entorno: nunca pasan
# por la linea de comandos del host ni por este script. Sin ellas (compose de desarrollo) se vuelca sin
# autenticar. Antes de esto el respaldo fallaba con "Unauthorized" contra el Mongo de produccion.

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DIRECTORIO_RESPALDOS="${1:-./respaldos-mongo}"
DIAS_RETENCION="${2:-30}"
BASE_DE_DATOS="${MONGO_INITDB_DATABASE:-aguavigia}"

mkdir -p "$DIRECTORIO_RESPALDOS"

MARCA_DE_TIEMPO="$(date -u +%Y%m%dT%H%M%SZ)"
ARCHIVO="$DIRECTORIO_RESPALDOS/aguavigia-mongo-${MARCA_DE_TIEMPO}.archive.gz"
PARCIAL="$ARCHIVO.parcial"

# Se escribe a un archivo parcial y solo se renombra si mongodump terminó bien: si falla, no queda un
# archivo pequeno y corrupto con pinta de respaldo que alguien restaure el dia que haga falta.
trap 'rm -f "$PARCIAL"' EXIT

docker compose -f "$COMPOSE_FILE" exec -T mongo sh -c '
  if [ -n "${MONGO_INITDB_ROOT_USERNAME:-}" ]; then
    exec mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin --db "$1" --archive --gzip
  fi
  exec mongodump --db "$1" --archive --gzip
' sh "$BASE_DE_DATOS" > "$PARCIAL"

gzip -t "$PARCIAL"
mv "$PARCIAL" "$ARCHIVO"

echo "Respaldo de Mongo escrito en $ARCHIVO ($(du -h "$ARCHIVO" | cut -f1))"

# Retencion: borra respaldos mas viejos que DIAS_RETENCION dias.
find "$DIRECTORIO_RESPALDOS" -name 'aguavigia-mongo-*.archive.gz' -mtime "+${DIAS_RETENCION}" -print -delete
