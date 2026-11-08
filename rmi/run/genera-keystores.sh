#!/usr/bin/env bash
set -euo pipefail

RMI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TLS_DIR="$RMI_DIR/tls"
PASS="changeit"
FORCE="${1:-}"

if [[ -d "$TLS_DIR" && "$FORCE" != "--force" ]]; then
    echo "== Keystores TLS ya existen en $TLS_DIR =="
    echo "   Usa '$0 --force' para regenerarlos."
    exit 0
fi

echo "== Generando CA, certificados y truststore TLS =="
rm -rf "$TLS_DIR"
mkdir -p "$TLS_DIR/ca" "$TLS_DIR/server" "$TLS_DIR/client"

crea_certificado() {
    local alias="$1"
    local cn="$2"
    local ou="$3"
    local keystore="$4"
    local csr="$5"
    local crt="$6"
    local ca_keystore="$7"
    local ca_cert="$8"
    local pass="$9"

    keytool -genkeypair \
        -alias "$alias" \
        -keyalg RSA -keysize 2048 -validity 825 \
        -dname "CN=$cn, OU=$ou, O=UDLAP, C=MX" \
        -ext SAN=dns:localhost,ip:127.0.0.1 \
        -keystore "$keystore" -storetype PKCS12 \
        -storepass "$pass" -keypass "$pass" >/dev/null

    keytool -certreq -alias "$alias" -keystore "$keystore" \
        -storepass "$pass" -file "$csr" >/dev/null

    keytool -gencert \
        -alias "ca" -keystore "$ca_keystore" \
        -storepass "$pass" -infile "$csr" -outfile "$crt" -validity 825 \
        -ext SAN=dns:localhost,ip:127.0.0.1 \
        -ext EKU=serverAuth,clientAuth -rfc >/dev/null

    keytool -importcert -alias "ca" -file "$ca_cert" \
        -keystore "$keystore" -storepass "$pass" -noprompt >/dev/null
    keytool -importcert -alias "$alias" -file "$crt" \
        -keystore "$keystore" -storepass "$pass" -noprompt >/dev/null
}

CA_ALIAS="ca"
CA_KEYSTORE="$TLS_DIR/ca/ca.p12"
CA_CERT="$TLS_DIR/ca/ca.crt"

# 1. Autoridad certificadora local (solo para la demo).
keytool -genkeypair \
    -alias "$CA_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 3650 \
    -dname "CN=Subasta CA, OU=Demo, O=UDLAP, L=Puebla, ST=Puebla, C=MX" \
    -ext bc:c -ext keyUsage=keyCertSign,cRLSign \
    -keystore "$CA_KEYSTORE" -storetype PKCS12 \
    -storepass "$PASS" -keypass "$PASS" >/dev/null

keytool -exportcert -alias "$CA_ALIAS" -keystore "$CA_KEYSTORE" \
    -storepass "$PASS" -rfc -file "$CA_CERT" >/dev/null

# 2. Certificado del servidor.
crea_certificado "server" "localhost" "Servidor" \
    "$TLS_DIR/server/server.p12" "$TLS_DIR/server/server.csr" \
    "$TLS_DIR/server/server.crt" "$CA_KEYSTORE" "$CA_CERT" "$PASS"

# 3. Certificado del cliente.
crea_certificado "client" "cliente" "Cliente" \
    "$TLS_DIR/client/client.p12" "$TLS_DIR/client/client.csr" \
    "$TLS_DIR/client/client.crt" "$CA_KEYSTORE" "$CA_CERT" "$PASS"

# 4. Truststore compartido: solo contiene la CA.
keytool -importcert -alias "$CA_ALIAS" -file "$CA_CERT" \
    -keystore "$TLS_DIR/truststore.p12" -storetype PKCS12 \
    -storepass "$PASS" -noprompt >/dev/null

echo "== Listo =="
echo "   CA:          $CA_KEYSTORE"
echo "   Servidor:    $TLS_DIR/server/server.p12"
echo "   Cliente:     $TLS_DIR/client/client.p12"
echo "   Truststore:  $TLS_DIR/truststore.p12"
echo "   Contraseña:  $PASS (solo demo)"
