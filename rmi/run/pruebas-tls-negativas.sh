#!/usr/bin/env bash
set -euo pipefail

RMI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PUERTO="${1:-1101}"
NOMBRE="${2:-Subasta}"
URL="rmi://localhost:${PUERTO}/${NOMBRE}"
URL_HOST_MALO="rmi://127.0.0.2:${PUERTO}/${NOMBRE}"
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

# Keystore con la CA pero sin clave privada: el cliente no puede autenticarse.
SIN_CLAVE="$TLS_DIR/sin-clave.p12"
if [[ ! -f "$SIN_CLAVE" ]]; then
    keytool -importcert -alias ca -file "$TLS_DIR/ca/ca.crt" \
        -keystore "$SIN_CLAVE" -storetype PKCS12 \
        -storepass "$PASS" -noprompt >/dev/null
fi

# CA y cliente "rogue": el servidor no confía en esa CA.
ROGUE_DIR="$TLS_DIR/rogue"
if [[ ! -d "$ROGUE_DIR" ]]; then
    mkdir -p "$ROGUE_DIR"
    ROGUE_CA="$ROGUE_DIR/rogue-ca.p12"
    ROGUE_CA_CERT="$ROGUE_DIR/rogue-ca.crt"
    ROGUE_CLIENT="$ROGUE_DIR/rogue-client.p12"

    keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 3650 \
        -dname "CN=Rogue CA, OU=Demo, O=UDLAP, C=MX" \
        -ext bc:c -ext keyUsage=keyCertSign,cRLSign \
        -keystore "$ROGUE_CA" -storetype PKCS12 \
        -storepass "$PASS" -keypass "$PASS" >/dev/null
    keytool -exportcert -alias ca -keystore "$ROGUE_CA" \
        -storepass "$PASS" -rfc -file "$ROGUE_CA_CERT" >/dev/null

    keytool -genkeypair -alias rogue -keyalg RSA -keysize 2048 -validity 825 \
        -dname "CN=rogue, OU=Cliente, O=UDLAP, C=MX" \
        -ext SAN=dns:localhost,ip:127.0.0.1 \
        -keystore "$ROGUE_CLIENT" -storetype PKCS12 \
        -storepass "$PASS" -keypass "$PASS" >/dev/null
    keytool -certreq -alias rogue -keystore "$ROGUE_CLIENT" \
        -storepass "$PASS" -file "$ROGUE_DIR/rogue.csr" >/dev/null
    keytool -gencert -alias ca -keystore "$ROGUE_CA" \
        -storepass "$PASS" -infile "$ROGUE_DIR/rogue.csr" \
        -outfile "$ROGUE_DIR/rogue.crt" -validity 825 \
        -ext SAN=dns:localhost,ip:127.0.0.1 \
        -ext EKU=serverAuth,clientAuth -rfc >/dev/null
    keytool -importcert -alias ca -file "$ROGUE_CA_CERT" \
        -keystore "$ROGUE_CLIENT" -storepass "$PASS" -noprompt >/dev/null
    keytool -importcert -alias rogue -file "$ROGUE_DIR/rogue.crt" \
        -keystore "$ROGUE_CLIENT" -storepass "$PASS" -noprompt >/dev/null
fi
ROGUE_CLIENT="$ROGUE_DIR/rogue-client.p12"

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
TLS_SIN_CLAVE=(
    -Djavax.net.ssl.keyStore="$SIN_CLAVE"
    -Djavax.net.ssl.keyStorePassword="$PASS"
    -Djavax.net.ssl.trustStore="$TLS_DIR/truststore.p12"
    -Djavax.net.ssl.trustStorePassword="$PASS"
    -Djava.rmi.server.hostname=localhost
)
TLS_ROGUE=(
    -Djavax.net.ssl.keyStore="$ROGUE_CLIENT"
    -Djavax.net.ssl.keyStorePassword="$PASS"
    -Djavax.net.ssl.trustStore="$TLS_DIR/truststore.p12"
    -Djavax.net.ssl.trustStorePassword="$PASS"
    -Djava.rmi.server.hostname=localhost
)
TLS_VALIDO=(
    -Djavax.net.ssl.keyStore="$TLS_DIR/client/client.p12"
    -Djavax.net.ssl.keyStorePassword="$PASS"
    -Djavax.net.ssl.trustStore="$TLS_DIR/truststore.p12"
    -Djavax.net.ssl.trustStorePassword="$PASS"
    -Djava.rmi.server.hostname=localhost
)

echo "== Arrancando servidor TLS en ${PUERTO} =="
java "${TLS_SERVIDOR[@]}" -cp bin ServidorMain --tls "$PUERTO" "$NOMBRE" \
    >"$LOG_DIR/servidor-tls-negativas.log" 2>&1 &
SERVIDOR_PID=$!

publicado=0
for _ in $(seq 1 100); do
    if grep -q "publicado" "$LOG_DIR/servidor-tls-negativas.log"; then
        publicado=1
        break
    fi
    if ! kill -0 "$SERVIDOR_PID" 2>/dev/null; then
        cat "$LOG_DIR/servidor-tls-negativas.log"
        echo "ServidorMain TLS terminó inesperadamente." >&2
        exit 1
    fi
    sleep 0.1
done
if [[ "$publicado" -ne 1 ]]; then
    cat "$LOG_DIR/servidor-tls-negativas.log"
    echo "El servidor TLS no se publicó a tiempo." >&2
    exit 1
fi
cat "$LOG_DIR/servidor-tls-negativas.log"

ejecuta_negativa() {
    local nombre="$1"
    local patron="$2"
    shift 2
    local log="$LOG_DIR/negativa-${nombre}.log"

    set +e
    java "$@" >"$log" 2>&1
    local codigo=$?
    set -e

    echo "--- ${nombre} (exit ${codigo}) ---"
    sed -n '1,25p' "$log"

    if [[ "$codigo" -eq 0 ]]; then
        echo "FALLO: ${nombre} fue aceptada; se esperaba un rechazo." >&2
        return 1
    fi
    if ! kill -0 "$SERVIDOR_PID" 2>/dev/null; then
        echo "FALLO: el servidor TLS murió durante ${nombre}." >&2
        return 1
    fi
    if ! grep -Eqi "$patron" "$log"; then
        echo "FALLO: ${nombre} no mostró el patrón esperado: ${patron}" >&2
        return 1
    fi
    echo "OK: ${nombre} rechazada"
}

# El servidor puede cerrar el socket antes de que el cliente lea la alerta TLS;
# en ese caso el cliente ve "Tubería rota" / "Connection reset". Sigue siendo un
# rechazo válido si el servidor sobrevive y un cliente legítimo puede conectar
# después. Por eso el patrón admite ambos casos y se comprueba al final.
echo "== 1/3: cliente sin certificado =="
ejecuta_negativa "sin-certificado-cliente" \
    "certificate_required|SSLHandshakeException|Tubería rota|Connection reset|Broken pipe" \
    "${TLS_SIN_CLAVE[@]}" -cp bin:bin-test PruebaConexionTls "$URL"

echo "== 2/3: cliente con certificado de otra CA =="
ejecuta_negativa "certificado-de-otra-ca" \
    "certificate_required|certificate_unknown|bad_certificate|SSLHandshakeException|Tubería rota|Connection reset|Broken pipe" \
    "${TLS_ROGUE[@]}" -cp bin:bin-test PruebaConexionTls "$URL"

echo "== 3/3: hostname que no está en el certificado =="
ejecuta_negativa "hostname-incorrecto" \
    "No subject alternative names|SSLHandshakeException" \
    "${TLS_VALIDO[@]}" -cp bin:bin-test PruebaConexionTls "$URL_HOST_MALO"

echo "== Comprobando que el servidor sigue aceptando un cliente válido =="
LOG_VALIDO="$LOG_DIR/negativas-cliente-valido.log"
set +e
java "${TLS_VALIDO[@]}" -cp bin:bin-test PruebaConexionTls "$URL" >"$LOG_VALIDO" 2>&1
CODIGO_VALIDO=$?
set -e
cat "$LOG_VALIDO"
if [[ "$CODIGO_VALIDO" -ne 0 ]] || ! grep -q "CONECTADO" "$LOG_VALIDO"; then
    echo "FALLO: un cliente válido no pudo conectar después de las negativas." >&2
    exit 1
fi
echo "OK: cliente válido sigue conectando"

echo "== OK: 3/3 negativas TLS rechazadas =="
