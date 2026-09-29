#!/usr/bin/env bash
# Escala — escenario integrado (RNF027): conexiones SSE + lecturas + escrituras a la vez, contra varias
# réplicas del backend y un nginx con la micro-caché, todo dentro de la red de Docker del compose.
#
# Uso (con `docker compose up -d --wait` ya corriendo y los sectores sembrados):
#   scripts/carga/escenario-integrado.sh
#   REPLICAS=3 SSE_POR_CLIENTE=16700 scripts/carga/escenario-integrado.sh   # ~50 000 SSE
#
# Variables (todas opcionales):
#   REPLICAS=3            réplicas del backend en total (la del compose + N-1 copias)
#   CLIENTES_SSE=3        contenedores cliente SSE (cada uno tiene su IP: evita el tope de puertos efímeros)
#   SSE_POR_CLIENTE=8350  conexiones SSE por cliente (3 × 8 350 ≈ 25 000; 3 × 16 700 ≈ 50 000)
#   RAMPA_SSE=500         conexiones nuevas por segundo, por cliente
#   TASA_LECTURA=3000     peticiones/s objetivo de la lectura pública (lectura-publica.js)
#   TASA_ESCRITURA=100    reportes/s repartidos; el pico es 3× (escritura-reportes.js)
#   SALIDA=<carpeta>      dónde dejar los registros (por defecto, una carpeta temporal)
#
# Qué hace, en orden: comprueba que el límite por IP del backend esté vaciado; levanta un nginx de PRUEBA
# (copia de infra/nginx sin `limit_req`/`limit_conn`, porque todo sale de una IP); copia las réplicas
# extra del backend con la misma configuración; abre las conexiones SSE; 60 s después empieza la
# lectura y 75 s más tarde la escritura; al terminar imprime un resumen y se limpia solo.
#
# NO usar en producción ni contra una base que importe: las escrituras dejan decenas de miles de reportes
# y cambian el estado de sectores. Haz antes una copia (scripts/backup-mongo.sh) y restáurala después.
# Los números de una sola máquina sirven para comparar antes/después, no son la capacidad de un servidor:
# el generador de carga, nginx, las réplicas, Mongo y Redis se reparten los mismos núcleos.
set -euo pipefail

REPLICAS=${REPLICAS:-3}
CLIENTES_SSE=${CLIENTES_SSE:-3}
SSE_POR_CLIENTE=${SSE_POR_CLIENTE:-8350}
RAMPA_SSE=${RAMPA_SSE:-500}
TASA_LECTURA=${TASA_LECTURA:-3000}
TASA_ESCRITURA=${TASA_ESCRITURA:-100}
DURACION_SSE=${DURACION_SSE:-300}

RAIZ=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
SALIDA=${SALIDA:-$(mktemp -d)}
mkdir -p "$SALIDA"
TMP=$(mktemp -d)
BACKEND=aguavigia-backend
PROXY=carga-proxy

# En Git Bash / Windows, Docker necesita rutas de Windows y no debe traducir las de dentro del contenedor.
ruta() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
export MSYS_NO_PATHCONV=1

RED=$(docker inspect "$BACKEND" --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null) || {
    echo "No encuentro el contenedor $BACKEND: levanta el stack con 'docker compose up -d --wait'." >&2; exit 1; }
IMAGEN=$(docker inspect "$BACKEND" --format '{{.Config.Image}}')

limpiar() {
    docker rm -f "$PROXY" $(seq -f 'backend-r%g' 2 "$REPLICAS") >/dev/null 2>&1 || true
    for k in $(seq 1 "$CLIENTES_SSE"); do docker rm -f "sse-c$k" >/dev/null 2>&1 || true; done
    docker rm -f k6-lectura k6-escritura >/dev/null 2>&1 || true
    rm -rf "$TMP"
}
trap limpiar EXIT

echo "== Comprobando que el límite por IP del backend esté vaciado"
codigos=$(docker exec "$BACKEND" sh -c 'for i in $(seq 1 45); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/reportes/no-existe; done' | sort | uniq -c)
if grep -q 429 <<<"$codigos"; then
    echo "El backend responde 429: falta vaciar aguavigia.rate-limit.reglas (variable AGUAVIGIA_RATE_LIMIT_REGLAS=\"\")." >&2
    echo "Ejemplo: un docker-compose.override con  services: backend: environment: AGUAVIGIA_RATE_LIMIT_REGLAS: \"\"" >&2
    exit 1
fi

echo "== nginx de prueba (copia de infra/nginx sin límites por IP)"
mkdir -p "$TMP/nginx"
sed -E '/^\s*limit_(req|conn)(_status)? /d' "$RAIZ/infra/nginx/nginx.conf" > "$TMP/nginx/nginx.conf"
cp "$RAIZ/infra/nginx/nginx-main.conf" "$RAIZ/infra/nginx/security-headers.conf" "$TMP/nginx/"
docker rm -f "$PROXY" >/dev/null 2>&1 || true
docker run -d --name "$PROXY" --network "$RED" \
    -v "$(ruta "$TMP/nginx/nginx-main.conf"):/etc/nginx/nginx.conf:ro" \
    -v "$(ruta "$TMP/nginx/nginx.conf"):/etc/nginx/conf.d/default.conf:ro" \
    -v "$(ruta "$TMP/nginx/security-headers.conf"):/etc/nginx/security-headers.conf:ro" \
    nginx:1.27-alpine >/dev/null

if [ "$REPLICAS" -gt 1 ]; then
    echo "== Réplicas extra del backend ($((REPLICAS - 1)))"
    # El entorno se copia del backend del compose. Contiene secretos del .env local: vive en una carpeta
    # temporal que se borra al salir, y nunca se imprime.
    docker inspect "$BACKEND" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -v '^$' > "$TMP/backend.env"
    chmod 600 "$TMP/backend.env"
    for n in $(seq 2 "$REPLICAS"); do
        docker rm -f "backend-r$n" >/dev/null 2>&1 || true
        docker run -d --name "backend-r$n" --network "$RED" --network-alias backend \
            --memory 4g --cpus 2 --env-file "$(ruta "$TMP/backend.env")" "$IMAGEN" >/dev/null
    done
    for n in $(seq 2 "$REPLICAS"); do
        for _ in $(seq 1 60); do
            [ "$(docker exec "backend-r$n" curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health/readiness 2>/dev/null)" = 200 ] && break
            sleep 3
        done
    done
fi

echo "== Abriendo ${CLIENTES_SSE} × ${SSE_POR_CLIENTE} conexiones SSE por nginx (rampa ${RAMPA_SSE}/s por cliente)"
for k in $(seq 1 "$CLIENTES_SSE"); do
    docker run --rm --name "sse-c$k" --network "$RED" -v "$(ruta "$RAIZ/scripts/carga"):/carga:ro" node:22-alpine \
        node /carga/sse-conexiones.mjs --url "http://$PROXY/api/sectores/stream" \
        --conexiones "$SSE_POR_CLIENTE" --rampa "$RAMPA_SSE" --duracion "$DURACION_SSE" > "$SALIDA/sse-$k.log" 2>&1 &
done

sleep 60
echo "== Lectura pública a ${TASA_LECTURA} req/s"
docker run --rm --name k6-lectura -i --network "$RED" -e BASE_URL="http://$PROXY" -e TASA="$TASA_LECTURA" \
    grafana/k6 run --quiet - < "$RAIZ/scripts/carga/lectura-publica.js" > "$SALIDA/lectura.log" 2>&1 &
sleep 75
echo "== Escritura de reportes a ${TASA_ESCRITURA}/s (pico 3×)"
docker run --rm --name k6-escritura -i --network "$RED" -e BASE_URL="http://$PROXY" -e TASA="$TASA_ESCRITURA" \
    grafana/k6 run --quiet - < "$RAIZ/scripts/carga/escritura-reportes.js" > "$SALIDA/escritura.log" 2>&1 &
wait

resumen() {
    echo; echo "--- $1"
    grep -E "p\(9[59]\)|http_req_failed\.|http_reqs|dropped_iterations" "$2" | sed 's/^ *//' | cut -c1-140 || true
}
echo; echo "================ RESUMEN (registros completos en $SALIDA) ================"
for k in $(seq 1 "$CLIENTES_SSE"); do
    echo "--- SSE cliente $k"; grep -E "abiertas_al_cierre|respuestas por estado|primer evento" "$SALIDA/sse-$k.log" | sed 's/^ *//'
done
resumen "Lectura pública" "$SALIDA/lectura.log"
resumen "Escritura de reportes" "$SALIDA/escritura.log"
echo; echo "--- nginx (avisos de capacidad)"
docker logs "$PROXY" 2>&1 | grep -E "\[(warn|error|crit)\]" | sed -E 's/^[0-9\/]+ [0-9:]+ //; s/\*[0-9]+ //' | cut -c1-120 | sort | uniq -c | sort -rn | head -5 || true
echo "(Sin salida arriba = sin avisos.)"
