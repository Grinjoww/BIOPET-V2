#!/usr/bin/env bash
#
# Genera el keystore PKCS12 de desarrollo/academico para HTTPS local de BIOPET.
#
# Crea Backend/certs/biopet-dev.p12 con un certificado autofirmado (SAN:
# DNS:localhost, IP:127.0.0.1). Es idempotente: si el keystore ya existe,
# no lo sobrescribe salvo que se use --force.
#
# Este certificado es exclusivamente para entorno local/academico.
# NUNCA debe usarse en produccion.
#
# Uso:
#   scripts/generate-dev-keystore.sh
#   scripts/generate-dev-keystore.sh --force
#
# Nota: este archivo no depende de que Git preserve el bit de ejecucion en
# Windows. Si no es ejecutable, se puede invocar como: bash scripts/generate-dev-keystore.sh
# o bien: chmod +x scripts/generate-dev-keystore.sh

set -euo pipefail

ALIAS="biopet-local"
FORCE=0

for arg in "$@"; do
    case "$arg" in
        --force)
            FORCE=1
            ;;
        --alias=*)
            ALIAS="${arg#--alias=}"
            ;;
    esac
done

PASSWORD="${TLS_KEYSTORE_PASSWORD:-changeit}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CERTS_DIR="$REPO_ROOT/Backend/certs"
KEYSTORE_PATH="$CERTS_DIR/biopet-dev.p12"

if ! command -v keytool >/dev/null 2>&1; then
    echo "Error: keytool no esta disponible en el PATH. Instala un JDK 21 (incluye keytool) y vuelve a intentarlo." >&2
    exit 1
fi

mkdir -p "$CERTS_DIR"

if [ -f "$KEYSTORE_PATH" ] && [ "$FORCE" -ne 1 ]; then
    echo "El keystore ya existe: $KEYSTORE_PATH"
    echo "No se modifico nada. Usa --force para regenerarlo."
    exit 0
fi

if [ -f "$KEYSTORE_PATH" ]; then
    rm -f "$KEYSTORE_PATH"
fi

keytool -genkeypair \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 3650 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=localhost, OU=BIOPET Dev, O=BIOPET, L=Local, ST=Local, C=EC" \
    -ext "SAN=dns:localhost,ip:127.0.0.1"

echo ""
echo "Keystore de desarrollo creado: $KEYSTORE_PATH"
echo "Alias: $ALIAS"
echo "Certificado autofirmado, solo para uso local/academico. No usar en produccion."
echo ""
echo "Siguiente paso:"
echo "  docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build"
