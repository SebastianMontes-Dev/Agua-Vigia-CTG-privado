#!/usr/bin/env bash
# Falla si algún commit del rango lo firma Claude o lleva un trailer de coautoría (CLAUDE.md § Autoría).
#
#   scripts/verificar-autoria.sh origin/main..HEAD
#
# BUG-104: GitHub agrega un Co-authored-by al squash por cada autor distinto de los commits del PR, así
# que basta un commit hecho con la identidad por defecto de una sesión en la nube para que llegue a main.
set -euo pipefail

RANGO="${1:?Uso: scripts/verificar-autoria.sh <base>..<head>}"
fallos=0

while IFS= read -r sha; do
  identidades="$(git log -1 --format='%an <%ae>%n%cn <%ce>' "$sha")"
  if grep -qiE 'claude|anthropic' <<<"$identidades"; then
    echo "::error::$sha lo firma una identidad de la IA: $(head -1 <<<"$identidades")"
    fallos=1
  fi
  if git log -1 --format='%B' "$sha" | grep -qiE '^co-authored-by:'; then
    echo "::error::$sha lleva un trailer Co-authored-by"
    fallos=1
  fi
done < <(git rev-list "$RANGO")

if [ "$fallos" -ne 0 ]; then
  echo "Reescribe la autoría antes de fusionar: git rebase <base> --exec 'git commit --amend --no-edit --reset-author' con tu identidad en git config."
  exit 1
fi
echo "Autoría correcta en $(git rev-list --count "$RANGO") commits."
