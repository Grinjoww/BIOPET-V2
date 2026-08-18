#!/bin/sh
# Backend/render-entrypoint.sh
#
# Punto de entrada usado UNICAMENTE por Render (render.yaml, dockerCommand),
# no por Docker Compose local (que usa el ENTRYPOINT normal del Dockerfile
# y ya recibe DB_URL completo desde .env).
#
# Motivo: render.yaml no puede inyectar DB_URL directamente (Render solo
# expone DB_HOST/DB_PORT/DB_NAME por separado via fromDatabase), y una
# expresion "sh -c 'export ... && exec ...'" embebida como string dentro
# de dockerCommand en el YAML termina siendo interpretada por Render como
# un unico nombre de programa (exit 127, "not found") en vez de como una
# cadena de comandos de shell. Este script evita ese problema: render.yaml
# solo referencia su ruta, sin quoting complejo.
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

exec java -jar /app/app.jar
