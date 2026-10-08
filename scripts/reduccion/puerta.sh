#!/usr/bin/env bash
# La parte de la puerta de la reducción (docs/reduccion/README.md) que no necesita Java: levanta una base limpia y compara (o guarda) el
# contrato, la forma de las respuestas, los flujos y el esquema de Mongo y Redis, siempre en el mismo orden.
#
#   PUERTA_BORRA_VOLUMENES=si scripts/reduccion/puerta.sh comparar     # contra la línea base de scripts/reduccion/linea-base/
#   PUERTA_BORRA_VOLUMENES=si scripts/reduccion/puerta.sh guardar      # reescribe esa línea base
#
# ⚠ Ejecuta `docker compose down -v`: borra los volúmenes del proyecto (Mongo, Redis y fotos). Por eso exige PUERTA_BORRA_VOLUMENES=si.
# Antes, scripts/backup-mongo.sh y scripts/backup-fotos.sh si hay algo que conservar.
#
# Variables que lee (nunca las imprime): ADMIN_CORREO (admin@aguavigia.local), ADMIN_CLAVE (obligatoria), TOTP_ARCHIVO (obligatoria: archivo
# fuera del repositorio donde se guarda el segundo factor del ADMIN; se borra al empezar porque la cuenta nace de nuevo), API_URL.
# Requiere el `.env` local descrito en docs/reduccion/R0-red-de-seguridad.md §9 (RATE_LIMIT_FACTOR=100, RATE_LIMIT_FACTOR_CUENTAS=1000,
# INGESTA_MODO=local para que la ingesta no dependa de Internet).
#
# Lo que NO hace: ./mvnw verify, el guion de simulación y la carga (ver R0 §9).
set -u

modo=${1:-}
case "$modo" in guardar | comparar) ;; *) echo "Uso: PUERTA_BORRA_VOLUMENES=si $0 guardar|comparar" >&2; exit 2 ;; esac
[ "${PUERTA_BORRA_VOLUMENES:-}" = "si" ] || { echo "Esto ejecuta 'docker compose down -v' y borra los volúmenes del proyecto. Repite con PUERTA_BORRA_VOLUMENES=si." >&2; exit 2; }
[ -n "${ADMIN_CLAVE:-}" ] || { echo "Falta ADMIN_CLAVE." >&2; exit 2; }
[ -n "${TOTP_ARCHIVO:-}" ] || { echo "Falta TOTP_ARCHIVO (un archivo fuera del repositorio)." >&2; exit 2; }

raiz=$(cd "$(dirname "$0")/../.." && pwd)
cd "$raiz" || exit 2
export ADMIN_CORREO=${ADMIN_CORREO:-admin@aguavigia.local}
# Git Bash en Windows: Node necesita la ruta con letra de unidad (C:/...), no la de MSYS (/c/...).
command -v cygpath > /dev/null && TOTP_ARCHIVO=$(cygpath -m "$TOTP_ARCHIVO")
export TOTP_ARCHIVO
api=${API_URL:-http://localhost:8081}
fallos=()

paso() { echo; echo "▶ $1"; }
corre() { # corre "nombre" comando...
  local nombre=$1; shift
  if "$@"; then echo "  ✔ $nombre"; else echo "  ✘ $nombre"; fallos+=("$nombre"); fi
}

paso "Copia de seguridad de Mongo (si hay una base en marcha) y base limpia (down -v + up)"
if [ "$(docker inspect -f '{{.State.Running}}' aguavigia-mongo 2> /dev/null)" = "true" ]; then
  # Antes de borrar: si hay algo que no se pueda regenerar, queda en respaldos-mongo/ (ignorado por git).
  ./scripts/backup-mongo.sh > /dev/null 2>&1 || {
    echo "No se pudo hacer la copia de Mongo; no borro nada. Para saltarla a propósito: PUERTA_SIN_RESPALDO=si." >&2
    [ "${PUERTA_SIN_RESPALDO:-}" = "si" ] || exit 1
  }
  echo "  copia hecha en respaldos-mongo/"
fi
docker compose --profile simulacion down -v > /dev/null 2>&1 || { echo "down -v falló" >&2; exit 1; }
rm -f "$TOTP_ARCHIVO"
docker compose up -d --build > /dev/null 2>&1 || { echo "up falló" >&2; exit 1; }
for _ in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' "$api/actuator/health/readiness")" = 200 ] && break; sleep 3; done
for _ in $(seq 1 60); do
  n=$(docker exec aguavigia-mongo mongosh --quiet aguavigia --eval 'db.usuarios.countDocuments()' 2> /dev/null | tr -d '\r')
  [ "${n:-0}" -ge 30001 ] 2> /dev/null && break
  sleep 5
done
corre "sembrador verificar (211 barrios y 30 000 cuentas)" docker compose run --rm sembrador verificar > /dev/null

paso "Contrato y forma con la base recién levantada"
corre "contrato" node scripts/reduccion/comparar-contrato.mjs "$modo"
corre "forma" node scripts/reduccion/instantanea.mjs "$modo"

paso "Flujos (deja datos de todo tipo en las bases)"
EXIGIR_COBERTURA=1 corre "verificar-flujos (todos los pasos y las 84 operaciones)" node scripts/verificar-flujos.mjs > /tmp/puerta-flujos.log 2>&1
tail -4 /tmp/puerta-flujos.log | sed 's/^/    /'

paso "Forma con datos y esquema de las bases"
INSTANTANEA_NOMBRE=forma-con-datos corre "forma con datos" node scripts/reduccion/instantanea.mjs "$modo"
corre "esquema de Mongo y Redis" node scripts/reduccion/esquema-datos.mjs "$modo"

echo
if [ ${#fallos[@]} -eq 0 ]; then echo "Puerta ($modo): TODO EN VERDE"; exit 0; fi
echo "Puerta ($modo): ${#fallos[@]} con fallo: ${fallos[*]}"; exit 1
