#!/bin/sh
# db/init-procs.sh
# El entrypoint oficial de postgres solo ejecuta archivos *.sh / *.sql /
# *.sql.gz directamente en /docker-entrypoint-initdb.d/ (ignora subdirectorios).
# Este script aplica, en orden alfabetico, los procedimientos almacenados de
# db/procs (montado en /docker-entrypoint-initdb.d/03-procs). El orden natural
# del glob deja a zz_grants_biopet_app.sql al final (z > f > s), que es el
# orden requerido: primero las rutinas y luego los grants.
set -e

for f in /docker-entrypoint-initdb.d/03-procs/*.sql; do
    echo "running $f"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$f"
done