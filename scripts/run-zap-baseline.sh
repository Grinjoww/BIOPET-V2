#!/usr/bin/env bash
#
# Ejecuta un OWASP ZAP Baseline Scan real y reproducible contra el backend
# de BIOPET, usando el stack Docker Compose ya existente en el repositorio
# (docker-compose.yml, perfil base sin TLS) para levantar postgres + redis +
# backend, esperar sus healthchecks reales, y escanear el backend por su
# nombre de servicio dentro de la misma red de Docker Compose que crea el
# propio "docker compose up" (no una URL inventada ni un host externo).
#
# Que hace exactamente:
#   1. Verifica que Docker este disponible.
#   2. Si el backend no esta ya "healthy", levanta el stack con
#      "docker compose up -d" (postgres, redis, backend) y espera a que
#      /actuator/health reporte UP (mismo healthcheck ya definido en
#      docker-compose.yml), sin timeout arbitrario silencioso: falla con
#      mensaje claro si no llega a estar listo.
#   3. Detecta el nombre real de la red de Docker Compose creada para este
#      proyecto (via "docker compose ... config" + "docker network ls"),
#      para que el contenedor de ZAP pueda resolver "backend" por su
#      nombre de servicio, igual que "frontend" y el resto de contenedores.
#   4. Ejecuta la imagen oficial "ghcr.io/zaproxy/zaproxy:stable"
#      (zap-baseline.py) contra "http://backend:8080", target real y
#      alcanzable, no localhost ni una URL de ejemplo.
#   5. Archiva reporte HTML + XML + JSON reales en docs/mediciones/sec/zap/.
#
# Uso:
#   scripts/run-zap-baseline.sh
#   scripts/run-zap-baseline.sh --keep-stack   # no baja el stack al terminar
#
# Variables de entorno opcionales:
#   ZAP_IMAGE   Imagen de ZAP a usar (por defecto ghcr.io/zaproxy/zaproxy:stable)
#   ZAP_TARGET  URL objetivo dentro de la red Docker (por defecto http://backend:8080)
#
# Codigo de salida: el de zap-baseline.py (0 = sin alertas WARN/FAIL segun
# sus reglas por defecto; puede ser distinto de cero con hallazgos de
# severidad media/baja sin que eso sea, por si mismo, un error de este
# script — ver el reporte HTML para la interpretacion real).

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ZAP_DIR="$REPO_ROOT/docs/mediciones/sec/zap"
mkdir -p "$ZAP_DIR"

KEEP_STACK=0
for arg in "$@"; do
    case "$arg" in
        --keep-stack) KEEP_STACK=1 ;;
    esac
done

ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable}"
ZAP_TARGET="${ZAP_TARGET:-http://backend:8080}"

if ! command -v docker >/dev/null 2>&1; then
    echo "Error: docker no esta en el PATH." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "Error: el daemon de Docker no responde (¿Docker Desktop esta iniciado?)." >&2
    exit 1
fi

cd "$REPO_ROOT"

if [ ! -f "$REPO_ROOT/.env" ]; then
    cp "$REPO_ROOT/.env.example" "$REPO_ROOT/.env"
    echo "Se creo .env localmente a partir de .env.example."
fi

STACK_YA_ESTABA_ARRIBA=0
if docker inspect biopet-backend --format '{{.State.Health.Status}}' 2>/dev/null | grep -q '^healthy$'; then
    STACK_YA_ESTABA_ARRIBA=1
    echo "El backend ya esta 'healthy'; no se vuelve a levantar el stack."
else
    echo "Levantando postgres + redis + backend (docker compose up -d)..."
    docker compose up -d postgres redis backend
    if [ $? -ne 0 ]; then
        echo "Error: 'docker compose up' fallo." >&2
        exit 1
    fi

    echo "Esperando a que biopet-backend reporte healthy (timeout ~180s)..."
    LISTO=0
    for i in $(seq 1 36); do
        ESTADO="$(docker inspect biopet-backend --format '{{.State.Health.Status}}' 2>/dev/null || echo 'desconocido')"
        echo "  intento $i/36: estado=$ESTADO"
        if [ "$ESTADO" = "healthy" ]; then
            LISTO=1
            break
        fi
        sleep 5
    done
    if [ "$LISTO" -ne 1 ]; then
        echo "Error: biopet-backend no alcanzo estado 'healthy' a tiempo." >&2
        docker logs biopet-backend --tail 100 || true
        exit 1
    fi
fi

echo ""
echo "Backend healthy. Detectando red de Docker Compose de este proyecto..."
NETWORK_NAME="$(docker inspect biopet-backend --format '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' 2>/dev/null | head -1)"
if [ -z "$NETWORK_NAME" ]; then
    echo "Error: no se pudo determinar la red Docker del contenedor biopet-backend." >&2
    exit 1
fi
echo "Red detectada: $NETWORK_NAME"

echo ""
echo "Descargando/verificando imagen de ZAP: $ZAP_IMAGE"
docker pull "$ZAP_IMAGE"

ZAP_VERSION_OUTPUT="$(docker run --rm "$ZAP_IMAGE" zap-baseline.py --help 2>&1 | head -1 || true)"
FECHA_UTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo ""
echo "=== Ejecutando OWASP ZAP Baseline Scan ==="
echo "Target : $ZAP_TARGET"
echo "Imagen : $ZAP_IMAGE"
echo "Fecha  : $FECHA_UTC"
echo ""

# -I  : no falla el codigo de salida por alertas INFO
# -r/-x/-J : reportes HTML/XML/JSON reales generados por la propia herramienta
#
# MSYS_NO_PATHCONV=1: en Git Bash/MSYS (Windows), cualquier argumento con
# forma de ruta absoluta empezando en "/" se reescribe automaticamente
# como una ruta de Windows -- incluido "/zap/wrk", que es una ruta DENTRO
# del contenedor, no del host. Sin esta variable, "-v host:/zap/wrk/:rw"
# termina montando un directorio equivocado y ZAP falla con
# "the directory '/zap/wrk' is not mounted". Verificado en este entorno.
MSYS_NO_PATHCONV=1 docker run --rm \
    --network "$NETWORK_NAME" \
    -v "$ZAP_DIR:/zap/wrk/:rw" \
    -t "$ZAP_IMAGE" \
    zap-baseline.py \
    -t "$ZAP_TARGET" \
    -r zap-baseline-report.html \
    -x zap-baseline-report.xml \
    -J zap-baseline-report.json \
    -I
ZAP_EXIT=$?

echo ""
echo "zap-baseline.py finalizo con codigo de salida: $ZAP_EXIT"
echo "(0 = sin hallazgos que superen el umbral por defecto; distinto de 0 no"
echo " implica automaticamente severidad alta: ver el reporte HTML para"
echo " la clasificacion real por severidad)."

# Metadatos minimos, en texto plano, para que el README de docs/mediciones/sec/zap/
# pueda citarlos sin re-adivinar fecha/target/comando.
cat > "$ZAP_DIR/RUN-METADATA.txt" <<EOF
fecha_utc=$FECHA_UTC
imagen_zap=$ZAP_IMAGE
target=$ZAP_TARGET
red_docker=$NETWORK_NAME
comando=docker run --rm --network $NETWORK_NAME -v "$ZAP_DIR:/zap/wrk/:rw" -t $ZAP_IMAGE zap-baseline.py -t $ZAP_TARGET -r zap-baseline-report.html -x zap-baseline-report.xml -J zap-baseline-report.json -I
codigo_salida=$ZAP_EXIT
EOF

if [ "$STACK_YA_ESTABA_ARRIBA" -eq 0 ] && [ "$KEEP_STACK" -eq 0 ]; then
    echo ""
    echo "Deteniendo el stack levantado por este script (postgres, redis, backend)..."
    docker compose stop postgres redis backend
fi

exit "$ZAP_EXIT"
