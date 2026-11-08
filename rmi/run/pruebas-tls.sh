#!/usr/bin/env bash
set -euo pipefail

RMI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PUERTO="${1:-1100}"
NOMBRE="${2:-Subasta}"
URL="rmi://localhost:${PUERTO}/${NOMBRE}"
PASS="changeit"
TLS_DIR="$RMI_DIR/tls"
LOG_DIR="$RMI_DIR/docs/logs/tls"

cd "$RMI_DIR"
mkdir -p "$LOG_DIR"

SERVIDOR_PID=""
limpia() {
    if [[ -n "$SERVIDOR_PID" ]]; then
        kill "$SERVIDOR_PID" 2>/dev/null || true
        wait "$SERVIDOR_PID" 2>/dev/null || true
    fi
}
trap limpia EXIT

echo "== Generando keystores si hace falta =="
./run/genera-keystores.sh

echo "== Compilando =="
javac -d bin src/*.java
javac -cp bin -d bin-test test/*.java

if (exec 3<>"/dev/tcp/127.0.0.1/${PUERTO}") 2>/dev/null; then
    echo "El puerto ${PUERTO} ya está ocupado. Usa otro puerto o detén el proceso." >&2
    exit 1
fi

TLS_SERVIDOR=(
    -Djavax.net.ssl.keyStore="$TLS_DIR/server/server.p12"
    -Djavax.net.ssl.keyStorePassword="$PASS"
    -Djavax.net.ssl.trustStore="$TLS_DIR/truststore.p12"
    -Djavax.net.ssl.trustStorePassword="$PASS"
    -Djava.rmi.server.hostname=localhost
)
TLS_CLIENTE=(
    -Djavax.net.ssl.keyStore="$TLS_DIR/client/client.p12"
    -Djavax.net.ssl.keyStorePassword="$PASS"
    -Djavax.net.ssl.trustStore="$TLS_DIR/truststore.p12"
    -Djavax.net.ssl.trustStorePassword="$PASS"
    -Djava.rmi.server.hostname=localhost
)

echo "== Arrancando servidor TLS con registro embebido en ${PUERTO} =="
java "${TLS_SERVIDOR[@]}" -cp bin ServidorMain --tls "$PUERTO" "$NOMBRE" \
    >"$LOG_DIR/servidor-tls.log" 2>&1 &
SERVIDOR_PID=$!

publicado=0
for _ in $(seq 1 100); do
    if grep -q "publicado" "$LOG_DIR/servidor-tls.log"; then
        publicado=1
        break
    fi
    if ! kill -0 "$SERVIDOR_PID" 2>/dev/null; then
        cat "$LOG_DIR/servidor-tls.log"
        echo "ServidorMain TLS terminó inesperadamente." >&2
        exit 1
    fi
    sleep 0.1
done
if [[ "$publicado" -ne 1 ]]; then
    cat "$LOG_DIR/servidor-tls.log"
    echo "El servidor TLS no se publicó a tiempo." >&2
    exit 1
fi
cat "$LOG_DIR/servidor-tls.log"

echo "== Ejecutando pruebas unitarias TLS =="
java "${TLS_CLIENTE[@]}" -cp bin:bin-test PruebasTls | tee "$LOG_DIR/pruebas-tls-unit.log"

echo "== Ejecutando integración TLS =="
set +e
java "${TLS_CLIENTE[@]}" -cp bin:bin-test PruebasIntegracion "$URL" --tls \
    >"$LOG_DIR/integracion-tls.log" 2>&1
CODIGO=$?
set -e
cat "$LOG_DIR/integracion-tls.log"
if [[ "$CODIGO" -ne 0 ]]; then
    echo "La integración TLS falló (código $CODIGO)." >&2
    exit "$CODIGO"
fi

echo "== OK: perfil TLS en verde =="
