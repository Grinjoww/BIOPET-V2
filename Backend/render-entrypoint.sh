#!/bin/sh
# Backend/render-entrypoint.sh
#
# Punto de entrada usado UNICAMENTE por Render.
# Construye DB_URL y, si existe el secreto SRI en Base64,
# reconstruye temporalmente el PKCS#12 sin versionarlo.
#
# No imprime passwords ni ningun otro secreto.

set -eu

if [ -z "${DB_HOST:-}" ]; then
    echo "render-entrypoint: falta la variable de entorno DB_HOST" >&2
    exit 1
fi
if [ -z "${DB_PORT:-}" ]; then
    echo "render-entrypoint: falta la variable de entorno DB_PORT" >&2
    exit 1
fi
if [ -z "${DB_NAME:-}" ]; then
    echo "render-entrypoint: falta la variable de entorno DB_NAME" >&2
    exit 1
fi

export DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

# ------------------------------------------------------------
# Certificado de firma electronica SRI
# ------------------------------------------------------------

SRI_CERT_B64_FILE="${SRI_CERT_B64_FILE:-/etc/secrets/sri-cert.p12.b64}"
SRI_CERT_RUNTIME_PATH="${SRI_CERT_RUNTIME_PATH:-/tmp/biopet-sri-cert.p12}"

if [ -f "$SRI_CERT_B64_FILE" ]; then
    if [ -z "${SRI_CERT_PASSWORD:-}" ]; then
        echo "render-entrypoint: existe certificado SRI pero falta SRI_CERT_PASSWORD" >&2
        exit 1
    fi

    # Los archivos creados a partir de aqui quedan accesibles solo para
    # el usuario del proceso.
    umask 077

    if ! base64 -d "$SRI_CERT_B64_FILE" > "$SRI_CERT_RUNTIME_PATH"; then
        rm -f "$SRI_CERT_RUNTIME_PATH"
        echo "render-entrypoint: no se pudo reconstruir el certificado SRI" >&2
        exit 1
    fi

    if [ ! -s "$SRI_CERT_RUNTIME_PATH" ]; then
        rm -f "$SRI_CERT_RUNTIME_PATH"
        echo "render-entrypoint: el certificado SRI reconstruido esta vacio" >&2
        exit 1
    fi

    export SRI_CERT_PATH="$SRI_CERT_RUNTIME_PATH"

    echo "render-entrypoint: material de firma SRI disponible en runtime"
fi

exec java -jar /app/app.jar