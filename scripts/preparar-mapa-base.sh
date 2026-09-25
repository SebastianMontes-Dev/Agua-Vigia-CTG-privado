#!/usr/bin/env bash
# Prepara los archivos del mapa base local (ADR-067, ADR-068) en frontend/public/mapa/.
#
#   scripts/preparar-mapa-base.sh glifos     Noto Sans Regular y Medium, rangos 0-255, 256-511 y 8192-8447
#   scripts/preparar-mapa-base.sh pmtiles    Extracto de Cartagena de un build diario de Protomaps
#
# El extracto necesita el CLI de go-pmtiles (go install github.com/protomaps/go-pmtiles@latest) y acceso a
# build.protomaps.com. Variables: PMTILES_BUILD (AAAAMMDD, por defecto el de ayer), PMTILES_BBOX, PMTILES_MAXZOOM.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DESTINO="$RAIZ/frontend/public/mapa"

# Commit fijo de protomaps/basemaps-assets: los glifos versionados no cambian solos si el repositorio de origen cambia.
ASSETS_COMMIT="028c18f713baecad011301ff7a69acc39bcc2ae7"
ASSETS_URL="https://raw.githubusercontent.com/protomaps/basemaps-assets/$ASSETS_COMMIT/fonts"
FAMILIAS=("Noto Sans Regular" "Noto Sans Medium")
RANGOS=("0-255" "256-511" "8192-8447")

# El bbox del plan (-75.70,10.25,-75.40,10.55) dejaba fuera 17 de los 213 barrios de
# data/geoespacial/barrios-cartagena.geojson (Barú, Bayunca, Arroyo Grande, Punta Canoa…). Este cubre todos menos
# el archipiélago de San Bernardo e Isla Fuerte, a más de 50 km: allí el polígono se pinta sobre el fondo liso.
BBOX="${PMTILES_BBOX:--75.76,10.13,-75.31,10.69}"
MAXZOOM="${PMTILES_MAXZOOM:-15}"

glifos() {
  for familia in "${FAMILIAS[@]}"; do
    mkdir -p "$DESTINO/glifos/$familia"
    for rango in "${RANGOS[@]}"; do
      curl -fsS -o "$DESTINO/glifos/$familia/$rango.pbf" "$ASSETS_URL/${familia// /%20}/$rango.pbf"
    done
  done
  curl -fsS -o "$DESTINO/glifos/OFL.txt" "$ASSETS_URL/OFL.txt"
  du -sh "$DESTINO/glifos"
}

pmtiles_extracto() {
  # `go install` deja el binario como go-pmtiles; las descargas de GitHub, como pmtiles.
  local cli
  cli="$(command -v pmtiles || command -v go-pmtiles)" \
    || { echo "Falta el CLI pmtiles: go install github.com/protomaps/go-pmtiles@latest" >&2; exit 1; }
  local build="${PMTILES_BUILD:-$(date -u -d yesterday +%Y%m%d 2>/dev/null || date -u -v-1d +%Y%m%d)}"
  mkdir -p "$DESTINO"
  "$cli" extract "https://build.protomaps.com/$build.pmtiles" "$DESTINO/cartagena.pmtiles" \
    --bbox="$BBOX" --maxzoom="$MAXZOOM"
  echo "Build $build · bbox $BBOX · zoom máximo $MAXZOOM"
  du -h "$DESTINO/cartagena.pmtiles"
}

case "${1:-}" in
  glifos) glifos ;;
  pmtiles) pmtiles_extracto ;;
  *) echo "Uso: $0 glifos|pmtiles" >&2; exit 2 ;;
esac
