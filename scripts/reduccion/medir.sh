#!/usr/bin/env sh
# Mide el tamaño del backend para la tabla «Avance» de docs/reduccion/README.md.
# Solo usa find, grep y wc: funciona igual en macOS y en Git Bash de Windows.
#
#   scripts/reduccion/medir.sh [etiqueta]     # etiqueta: nombre de la fila (por defecto, la rama actual)

set -eu

raiz=$(cd "$(dirname "$0")/../.." && pwd)
main="$raiz/backend/src/main/java"
test="$raiz/backend/src/test/java"

# Archivos .java bajo un directorio, uno por línea (vacío si el directorio no existe).
archivos() { [ -d "$1" ] && find "$1" -name '*.java' -type f || true; }

# Líneas sumadas de todos los .java bajo un directorio.
lineas() {
  archivos "$1" | while IFS= read -r f; do cat "$f"; done | wc -l | tr -d ' '
}

contar() { archivos "$1" | wc -l | tr -d ' '; }

# Interfaces declaradas en domain/port (entrada y salida).
interfaces_de_puerto() {
  archivos "$main/com/aguavigia/ctg/domain/port" \
    | while IFS= read -r f; do grep -lE '^(public )?(sealed )?interface ' "$f" || true; done \
    | wc -l | tr -d ' '
}

# Mappers MapStruct (@Mapper).
mappers() {
  archivos "$main" \
    | while IFS= read -r f; do grep -lE '^@Mapper\b' "$f" || true; done \
    | wc -l | tr -d ' '
}

etiqueta=${1:-$(git -C "$raiz" rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')}
fecha=$(date +%Y-%m-%d)

echo "| Fase | Fecha | .java main | Líneas main | .java test | Líneas test | Puerta |"
echo "|---|---|---|---|---|---|---|"
printf '| %s | %s | %s | %s | %s | %s | — |\n' \
  "$etiqueta" "$fecha" "$(contar "$main")" "$(lineas "$main")" "$(contar "$test")" "$(lineas "$test")"
echo
echo "Interfaces en domain/port: $(interfaces_de_puerto)"
echo "Mappers MapStruct:         $(mappers)"
