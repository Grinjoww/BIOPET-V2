#!/bin/sh
# frontend/docker-entrypoint.sh
#
# Genera /etc/nginx/conf.d/default.conf a partir de nginx.conf.template,
# sustituyendo UNICAMENTE ${BACKEND_HOST} y ${BACKEND_PORT} -- se pasa la
# lista explicita de variables a "envsubst" precisamente para que NO
# toque las variables propias de nginx ($host, $remote_addr,
# $proxy_add_x_forwarded_for, $scheme), que usan la misma sintaxis "$..."
# pero deben llegar intactas al archivo final que nginx carga.
#
# Defaults para Docker Compose local (mismo hostname/puerto que ya usaba
# el nginx.conf estatico anterior: el servicio "backend" del
# docker-compose.yml de este repositorio, puerto 8080). En Render,
# BACKEND_HOST/BACKEND_PORT llegan inyectados por render.yaml
# (fromService sobre el servicio real biopet-backend) y sobreescriben
# estos defaults.
set -eu

BACKEND_HOST="${BACKEND_HOST:-backend}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
export BACKEND_HOST BACKEND_PORT

envsubst '${BACKEND_HOST} ${BACKEND_PORT}' \
    < /etc/nginx/conf.d/default.conf.template \
    > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
