#!/usr/bin/env bash
#
# Recolecta evidencia de seguridad OWASP de BIOPET de forma reproducible,
# sin registrar secretos, contrasenas, JWT ni cookies completas.
#
# Ejecuta, en orden: comprobacion de herramientas requeridas, "mvn clean
# verify" (pruebas + cobertura JaCoCo), generacion del keystore local si
# falta (reutilizando scripts/generate-dev-keystore.sh), validacion de
# "docker compose config", levantamiento del stack Compose base + TLS, y
# consultas HTTP 8080 / HTTPS 8443 guardando SOLO cabeceras y codigos de
# estado (nunca cuerpos de login, cookies Set-Cookie completas ni JWT) en
# docs/mediciones/sec/raw/ (carpeta ignorada por git salvo .gitkeep).
#
# Este script NO ejecuta ningun comando de git de escritura (add/commit/push).
# No imprime ni guarda contrasenas ni tokens en ningun momento.
#
# Uso:
#   scripts/security-evidence.sh
#   scripts/security-evidence.sh --stop-containers
#
# Nota: este archivo no depende de que Git preserve el bit de ejecucion.
# Si no es ejecutable: bash scripts/security-evidence.sh

set -euo pipefail

STOP_CONTAINERS=0
for arg in "$@"; do
    case "$arg" in
        --stop-containers)
            STOP_CONTAINERS=1
            ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RAW_DIR="$REPO_ROOT/docs/mediciones/sec/raw"

mkdir -p "$RAW_DIR"

section() {
    echo ""
    echo "=== $1 ==="
}

# Guarda solo cabeceras + codigo de estado HTTP de una URL. Nunca guarda
# el cuerpo de la respuesta ni una cabecera Set-Cookie completa.
save_headers_only() {
    local url="$1"
    local out_file="$2"
    local insecure_flag="${3:-}"
    local tmp_body
    tmp_body="$(mktemp)"

    if [ "$insecure_flag" = "insecure" ]; then
        curl.exe -sSk -D "$out_file" -o "$tmp_body" -w "HTTP_STATUS=%{http_code}\n" "$url" >/dev/null 2>&1 \
            || curl -sSk -D "$out_file" -o "$tmp_body" -w "HTTP_STATUS=%{http_code}\n" "$url" >/dev/null
    else
        curl.exe -sS -D "$out_file" -o "$tmp_body" -w "HTTP_STATUS=%{http_code}\n" "$url" >/dev/null 2>&1 \
            || curl -sS -D "$out_file" -o "$tmp_body" -w "HTTP_STATUS=%{http_code}\n" "$url" >/dev/null
    fi
    rm -f "$tmp_body"

    if [ -f "$out_file" ]; then
        # Redaccion defensiva: si alguna respuesta llegara a incluir Set-Cookie,
        # nunca se guarda el valor completo de la cookie.
        sed -i.bak -E 's/^[Ss]et-[Cc]ookie:.*/Set-Cookie: [REDACTADO - no se guarda el valor]/' "$out_file" 2>/dev/null \
            || true
        rm -f "${out_file}.bak"
    fi
}

section "1. Comprobando herramientas requeridas"
faltantes=()
for h in java mvn docker curl keytool; do
    if command -v "$h" >/dev/null 2>&1; then
        echo "OK: $h encontrado"
    else
        echo "FALTA: $h no esta en el PATH"
        faltantes+=("$h")
    fi
done
if [ "${#faltantes[@]}" -gt 0 ]; then
    echo "Error: faltan herramientas requeridas: ${faltantes[*]}. Instalalas y vuelve a intentarlo." >&2
    exit 1
fi

section "2. Ejecutando mvn clean verify (pruebas + cobertura JaCoCo)"
(
    cd "$REPO_ROOT/Backend"
    mvn clean verify 2>&1 | tee "$RAW_DIR/mvn-clean-verify.txt"
)

section "3. Generando keystore local (solo si no existe; no se sobrescribe)"
bash "$SCRIPT_DIR/generate-dev-keystore.sh"

cd "$REPO_ROOT"

if [ ! -f "$REPO_ROOT/.env" ]; then
    cp "$REPO_ROOT/.env.example" "$REPO_ROOT/.env"
    echo "Se creo .env localmente a partir de .env.example (archivo ignorado por git, sin secretos reales)."
fi

section "4. Validando docker compose config"
docker compose -f docker-compose.yml -f docker-compose.tls.yml config > "$RAW_DIR/docker-compose-config.txt" 2>&1
echo "Configuracion combinada valida (guardada, sin valores de TLS_KEYSTORE_PASSWORD reales mas alla del valor por defecto documentado)."

section "5. Levantando el stack Compose base + TLS"
docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d

section "6. Esperando a que el backend este 'healthy'"
max_intentos=30
intento=0
healthy=0
while [ "$intento" -lt "$max_intentos" ]; do
    estado="$(docker inspect biopet-backend --format '{{.State.Health.Status}}' 2>/dev/null || echo '')"
    if [ "$estado" = "healthy" ]; then
        healthy=1
        break
    fi
    sleep 2
    intento=$((intento + 1))
done
if [ "$healthy" -eq 1 ]; then
    echo "Backend healthy."
else
    echo "Advertencia: el backend no alcanzo estado 'healthy' en el tiempo esperado. Verifica manualmente: docker compose -f docker-compose.yml -f docker-compose.tls.yml ps" >&2
fi

section "7. Consultando HTTP 8080 (solo cabeceras y estado)"
save_headers_only "http://localhost:8080/actuator/health" "$RAW_DIR/http-8080-headers.txt"

section "8. Consultando HTTPS 8443 (solo cabeceras y estado)"
save_headers_only "https://localhost:8443/actuator/health" "$RAW_DIR/https-8443-headers.txt" "insecure"

docker compose -f docker-compose.yml -f docker-compose.tls.yml ps > "$RAW_DIR/docker-compose-ps.txt" 2>&1

echo "Cabeceras y estados guardados en docs/mediciones/sec/raw/ (sin cuerpos de respuesta, sin cookies completas, sin JWT)."

section "Verificaciones que requieren inspeccion manual"
echo "- Protocolo TLS y cipher exactos: 'openssl s_client -connect localhost:8443 -tls1_3' (no se automatiza aqui para no depender de que openssl este instalado)."
echo "- SAN del certificado: 'openssl s_client -connect localhost:8443 -tls1_3 2>/dev/null | openssl x509 -noout -text'."
echo "- Login real con credenciales de prueba: este script NO ejecuta ningun login, para no tener que manejar ni guardar cookies o tokens. Usa Postman o curl manualmente si necesitas esa evidencia puntual, y no la copies a un archivo versionado."
echo "- Estado visual detallado de los contenedores: 'docker compose -f docker-compose.yml -f docker-compose.tls.yml ps' (ya guardado en docs/mediciones/sec/raw/docker-compose-ps.txt, pero conviene revisarlo visualmente)."

if [ "$STOP_CONTAINERS" -eq 1 ]; then
    section "Apagando contenedores (solicitado con --stop-containers)"
    docker compose -f docker-compose.yml -f docker-compose.tls.yml down
else
    echo ""
    echo "Los contenedores siguen activos (por defecto no se apagan)."
    echo "Para apagarlos: scripts/security-evidence.sh --stop-containers"
    echo "o manualmente:  docker compose -f docker-compose.yml -f docker-compose.tls.yml down"
fi

echo ""
echo "Evidencia generada en: docs/mediciones/sec/raw/"
echo "Este script no ejecuta git add, git commit ni git push."
