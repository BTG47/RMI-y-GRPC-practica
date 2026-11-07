#!/usr/bin/env bash
set -euo pipefail

RMI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PUERTO="${1:-1099}"
NOMBRE="${2:-Subasta}"
URL="rmi://localhost:${PUERTO}/${NOMBRE}"

cd "$RMI_DIR"

SERVIDOR_PID=""
REGISTRO_PID=""
LOG_SERVIDOR="$(mktemp)"
LOG_REGISTRO="$(mktemp)"

limpia() {
    if [[ -n "$SERVIDOR_PID" ]]; then
        kill "$SERVIDOR_PID" 2>/dev/null || true
        wait "$SERVIDOR_PID" 2>/dev/null || true
    fi
    if [[ -n "$REGISTRO_PID" ]]; then
        kill "$REGISTRO_PID" 2>/dev/null || true
        wait "$REGISTRO_PID" 2>/dev/null || true
    fi
    rm -f "$LOG_SERVIDOR" "$LOG_REGISTRO"
}
trap limpia EXIT

puerto_abierto() {
    (exec 3<>"/dev/tcp/127.0.0.1/${PUERTO}") 2>/dev/null
}

echo "== Compilando =="
javac -d bin src/*.java
javac -cp bin -d bin-test test/*.java

if puerto_abierto; then
    echo "== Reutilizando el registro RMI del puerto ${PUERTO} =="
else
    echo "== Arrancando rmiregistry en el puerto ${PUERTO} =="
    (cd bin && exec rmiregistry "$PUERTO") >"$LOG_REGISTRO" 2>&1 &
    REGISTRO_PID=$!
    for _ in $(seq 1 50); do
        puerto_abierto && break
        if ! kill -0 "$REGISTRO_PID" 2>/dev/null; then
            cat "$LOG_REGISTRO"
            echo "rmiregistry terminó inesperadamente." >&2
            exit 1
        fi
        sleep 0.1
    done
    if ! puerto_abierto; then
        cat "$LOG_REGISTRO"
        echo "rmiregistry no abrió el puerto ${PUERTO}." >&2
        exit 1
    fi
fi

echo "== Arrancando el servidor =="
java -cp bin ServidorMain "$PUERTO" "$NOMBRE" >"$LOG_SERVIDOR" 2>&1 &
SERVIDOR_PID=$!

publicado=0
for _ in $(seq 1 100); do
    if grep -q "publicado" "$LOG_SERVIDOR"; then
        publicado=1
        break
    fi
    if ! kill -0 "$SERVIDOR_PID" 2>/dev/null; then
        cat "$LOG_SERVIDOR"
        echo "ServidorMain terminó inesperadamente." >&2
        exit 1
    fi
    sleep 0.1
done
if [[ "$publicado" -ne 1 ]]; then
    cat "$LOG_SERVIDOR"
    echo "El servidor no se publicó a tiempo." >&2
    exit 1
fi
cat "$LOG_SERVIDOR"

echo "== Ejecutando pruebas de integración =="
java -cp bin:bin-test PruebasIntegracion "$URL"
