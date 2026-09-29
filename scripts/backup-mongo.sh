#!/usr/bin/env bash
set -euo pipefail

# Respaldo manual de Mongo via mongodump dentro del contenedor del compose local, pensado para
# correrlo antes de una demo o de una prueba de carga. --archive sale por stdout de
# `docker compose exec` y este script lo redirige a un archivo comprimido en el host.
# Ver docs/ingenieria/respaldo-y-restauracion.md.
#
# Uso: ./scripts/backup-mongo.sh [directorio-de-respaldos]

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
DIRECTORIO_RESPALDOS="${1:-./respaldos-mongo}"
BASE_DE_DATOS="${MONGO_INITDB_DATABASE:-aguavigia}"

mkdir -p "$DIRECTORIO_RESPALDOS"

MARCA_DE_TIEMPO="$(date +%Y%m%dT%H%M%S)"
ARCHIVO="$DIRECTORIO_RESPALDOS/aguavigia-mongo-${MARCA_DE_TIEMPO}.archive.gz"
PARCIAL="$ARCHIVO.parcial"

# Se escribe a un archivo parcial y solo se renombra si mongodump terminó bien: si falla, no queda un
# archivo pequeno y corrupto con pinta de respaldo que alguien restaure el dia que haga falta.
trap 'rm -f "$PARCIAL"' EXIT

docker compose -f "$COMPOSE_FILE" exec -T mongo mongodump --db "$BASE_DE_DATOS" --archive --gzip > "$PARCIAL"

gzip -t "$PARCIAL"
mv "$PARCIAL" "$ARCHIVO"

echo "Respaldo de Mongo escrito en $ARCHIVO ($(du -h "$ARCHIVO" | cut -f1))"
