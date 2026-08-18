#!/bin/sh
# frontend/docker-entrypoint.sh
#
# Genera /etc/nginx/conf.d/default.conf a partir de nginx.conf.template,
# sustituyendo UNICAMENTE ${BACKEND_URL} -- se pasa la lista explicita de
# variables a "envsubst" precisamente para que NO toque las variables
# propias de nginx ($host, $remote_addr, $proxy_add_x_forwarded_for,
# $scheme), que usan la misma sintaxis "$..." pero deben llegar intactas
# al archivo final que nginx carga.
#
# Default para Docker Compose local: "http://backend:8080" (el servicio
# "backend" del docker-compose.yml de este repositorio). En Render,
# BACKEND_URL llega inyectado por render.yaml con la URL HTTPS PUBLICA
# real del backend ya desplegado (no el hostname privado: verificado en
# un deploy real que Web Services Free de Render no pueden recibir
# trafico por la red privada, solo enviarlo) y sobreescribe este default.
set -eu

BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
export BACKEND_URL

envsubst '${BACKEND_URL}' \
    < /etc/nginx/conf.d/default.conf.template \
    > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
