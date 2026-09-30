#!/bin/sh
# Libera los puertos que usa el entorno local cuando un proceso anterior quedó colgado (por ejemplo, la
# JVM hija de un `mvnw spring-boot:run` detenido). Equivale a limpiar-puertos.ps1 en Linux/macOS.
# Si el puerto lo tiene Docker no se toca: matarlo a la fuerza lo deja sin cerrar bien. Se libera con `docker compose stop`.
PUERTOS="8080 8081 8025 1025"
for puerto in $PUERTOS; do
  pid=$(lsof -ti:"$puerto" 2>/dev/null)
  if [ -n "$pid" ]; then
    if ps -o comm= -p $pid 2>/dev/null | grep -qiE 'docker|vpnkit'; then
      echo "Puerto $puerto lo usa Docker; no se toca. Usa: docker compose stop"
      continue
    fi
    echo "Liberando puerto $puerto (PID: $pid)..."
    kill -9 $pid 2>/dev/null
  else
    echo "Puerto $puerto libre."
  fi
done
