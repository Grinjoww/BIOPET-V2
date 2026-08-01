#!/usr/bin/env bash
#
# Recolecta evidencia REAL y reproducible de los seis controles OWASP
# exigidos para BIOPET (A01, A02, A03, A05, A07, A09), sin registrar
# secretos, contrasenas, JWT completos, JTI ni cookies completas.
#
# Ejecuta, en orden: comprobacion de herramientas requeridas, "mvn clean
# verify" (pruebas + cobertura JaCoCo), generacion del keystore local si
# falta (reutilizando scripts/generate-dev-keystore.sh), validacion de
# "docker compose config", levantamiento del stack Compose base + TLS,
# espera de healthchecks, y una secuencia real de peticiones HTTP contra
# el backend en ejecucion (HTTP 8080 / HTTPS 8443) para cada control OWASP.
#
# La evidencia sanitizada se guarda en docs/mediciones/sec/raw/ (NO esta
# excluida por .gitignore: los archivos que aqui se generan son evidencia
# real destinada a versionarse, salvo que el equipo decida lo contrario).
#
# Este script NO ejecuta ningun comando de git de escritura (add/commit/push),
# no borra volumenes de Docker, no ejecuta FLUSHALL ni "down -v", y no
# modifica datos mas alla de las cuentas/mascota academicas descritas abajo.
#
# Datos academicos temporales que este script puede crear via la API real
# (nunca por acceso directo a la base de datos):
#   - Dos usuarios ROLE_DUENO con correo claramente ficticio
#     (dominio "example.test"), contrasena aleatoria generada en el momento
#     y jamas impresa ni guardada.
#   - Una mascota de prueba, creada por el admin y asignada a uno de esos
#     dos duenos, eliminada logicamente (soft delete) al final del script.
# Ninguna contrasena nueva queda hardcodeada en este archivo: las
# credenciales de cuentas ya existentes (admin) se leen de variables de
# entorno con un valor por defecto igual al usuario semilla ya documentado
# en db/seed.sql / Backend/.../DataInitializer.java.
#
# Variables de entorno opcionales:
#   BASE_HTTP      URL base HTTP  (por defecto http://localhost:8080)
#   BASE_HTTPS     URL base HTTPS (por defecto https://localhost:8443)
#   ADMIN_EMAIL    Correo del admin semilla (por defecto admin@biopet.ec)
#   ADMIN_PASSWORD Contrasena del admin semilla. OBLIGATORIA: este script no
#                  trae ningun valor por defecto para una contrasena, ni
#                  siquiera la ya documentada en db/seed.sql/README.md. Si
#                  falta, el script se detiene con un mensaje de error claro.
#
# Uso:
#   scripts/security-evidence.sh
#   scripts/security-evidence.sh --stop-containers
#   scripts/security-evidence.sh --skip-build (omite "mvn clean verify")
#
# Nota: este archivo no depende de que Git preserve el bit de ejecucion.
# Si no es ejecutable: bash scripts/security-evidence.sh

set -euo pipefail

STOP_CONTAINERS=0
SKIP_BUILD=0
for arg in "$@"; do
    case "$arg" in
        --stop-containers)
            STOP_CONTAINERS=1
            ;;
        --skip-build)
            SKIP_BUILD=1
            ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RAW_DIR="$REPO_ROOT/docs/mediciones/sec/raw"

mkdir -p "$RAW_DIR"

BASE_HTTP="${BASE_HTTP:-http://localhost:8080}"
BASE_HTTPS="${BASE_HTTPS:-https://localhost:8443}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@biopet.ec}"
if [ -z "${ADMIN_PASSWORD:-}" ]; then
    echo "Error: falta la variable de entorno ADMIN_PASSWORD." >&2
    echo "Este script no trae ninguna contrasena por defecto. Exportala antes de ejecutar," >&2
    echo "usando la contrasena de la cuenta admin semilla ya documentada en db/seed.sql" >&2
    echo "y en README.md, por ejemplo:" >&2
    echo "  ADMIN_PASSWORD='...' bash scripts/security-evidence.sh" >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Utilidades
# ---------------------------------------------------------------------------

section() {
    echo ""
    echo "=== $1 ==="
}

# Archivos temporales (cookie jars, cuerpos de respuesta) a borrar siempre,
# incluso si el script termina por error ("set -e").
TMP_FILES=()
cleanup() {
    if [ "${#TMP_FILES[@]}" -gt 0 ]; then
        rm -f "${TMP_FILES[@]}" 2>/dev/null || true
    fi
}
trap cleanup EXIT

mk_tmp() {
    local f
    f="$(mktemp)"
    TMP_FILES+=("$f")
    echo "$f"
}

# Redacta, en un archivo, cualquier dato que no deba versionarse:
# valor de cookies (conserva atributos), Authorization, JWT completos,
# valores de password/accessToken/refreshToken en JSON, y el VALOR de
# cualquier variable de entorno sensible tal como la expande
# "docker compose config" (formato YAML "NOMBRE: valor"), sin importar si
# el valor "parece" un valor de desarrollo o no — se redacta por el nombre
# de la variable, nunca se confia en que el valor "se vea" inofensivo.
redact_file() {
    local f="$1"
    sed -E -i \
        -e 's/^([Ss]et-[Cc]ookie: *[A-Za-z0-9_]+)=[^;]*/\1=[REDACTADO]/' \
        -e 's/^([Aa]uthorization:).*/\1 [REDACTADO]/' \
        -e 's/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/[JWT-REDACTADO]/g' \
        -e 's/Bearer [A-Za-z0-9._-]+/Bearer [REDACTADO]/g' \
        -e 's/"(password|accessToken|refreshToken|access_token|refresh_token)"[[:space:]]*:[[:space:]]*"[^"]*"/"\1":"[REDACTADO]"/g' \
        -e 's/^([[:space:]]*(DB_PASSWORD|DB_APP_PASSWORD|POSTGRES_PASSWORD|REDIS_PASSWORD|JWT_SECRET|JWT_REFRESH_SECRET|TLS_KEYSTORE_PASSWORD)):.*/\1: [REDACTADO]/' \
        "$f" 2>/dev/null || true
}

# Resuelve el binario de curl y python disponibles una sola vez.
CURL_BIN="curl"
command -v curl.exe >/dev/null 2>&1 && CURL_BIN="curl.exe" || true

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
fi

# Extrae un campo string/numerico de nivel superior de un JSON (sin jq).
json_field() {
    local file="$1" field="$2"
    "$PYTHON_BIN" - "$file" "$field" <<'PYEOF' 2>/dev/null || true
import json, sys
try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        data = json.load(fh)
    value = data.get(sys.argv[2], "")
    print(value if value is not None else "")
except Exception:
    print("")
PYEOF
}

# Contador de resultados para el codigo de salida final.
declare -A RESULTADOS
FALLOS=0

# Registra el resultado de un control HTTP puntual: compara el status real
# contra una lista de status esperados separados por coma.
check_status() {
    local id="$1" desc="$2" comando="$3" esperado_csv="$4" real="$5"
    local ok=0
    IFS=',' read -ra ESPERADOS <<< "$esperado_csv"
    for e in "${ESPERADOS[@]}"; do
        if [ "$e" = "$real" ]; then
            ok=1
        fi
    done
    echo ""
    echo "[$id] $desc"
    echo "  comando seguro : $comando"
    echo "  status esperado: $esperado_csv"
    echo "  status obtenido: $real"
    if [ "$ok" -eq 1 ]; then
        echo "  resultado      : CUMPLE"
        RESULTADOS["$id"]="CUMPLE"
    else
        echo "  resultado      : NO CUMPLE (revisar docs/mediciones/sec/A0*-*.md)"
        RESULTADOS["$id"]="NO CUMPLE"
        FALLOS=$((FALLOS + 1))
    fi
}

# Igual que check_status pero para verificaciones booleanas (presencia de
# una cabecera, de un patron, etc.) en vez de un codigo HTTP.
check_bool() {
    local id="$1" desc="$2" comando="$3" esperado="$4" real_ok="$5"
    echo ""
    echo "[$id] $desc"
    echo "  comando seguro : $comando"
    echo "  esperado       : $esperado"
    if [ "$real_ok" -eq 1 ]; then
        echo "  resultado      : CUMPLE"
        RESULTADOS["$id"]="CUMPLE"
    else
        echo "  resultado      : NO CUMPLE"
        RESULTADOS["$id"]="NO CUMPLE"
        FALLOS=$((FALLOS + 1))
    fi
}

# ---------------------------------------------------------------------------
# 1. Herramientas requeridas
# ---------------------------------------------------------------------------

section "1. Comprobando herramientas requeridas"
faltantes=()
for h in java mvn docker "$CURL_BIN" keytool openssl; do
    if command -v "$h" >/dev/null 2>&1; then
        echo "OK: $h encontrado"
    else
        echo "FALTA: $h no esta en el PATH"
        faltantes+=("$h")
    fi
done
if [ -z "$PYTHON_BIN" ]; then
    echo "FALTA: python3 o python no estan en el PATH"
    faltantes+=("python3/python")
else
    echo "OK: $PYTHON_BIN encontrado"
fi
if [ "${#faltantes[@]}" -gt 0 ]; then
    echo "Error: faltan herramientas requeridas: ${faltantes[*]}. Instalalas y vuelve a intentarlo." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# 2. mvn clean verify (pruebas + JaCoCo) — no se excluye ninguna prueba
# ---------------------------------------------------------------------------

if [ "$SKIP_BUILD" -eq 1 ]; then
    section "2. Omitido (--skip-build): mvn clean verify"
else
    section "2. Ejecutando mvn clean verify (pruebas + cobertura JaCoCo)"
    (
        cd "$REPO_ROOT/Backend"
        mvn clean verify 2>&1 | tee "$RAW_DIR/mvn-clean-verify.txt"
    )
fi

# ---------------------------------------------------------------------------
# 3. Keystore local (prerrequisito de TLS, no es en si mismo la auditoria)
# ---------------------------------------------------------------------------

section "3. Generando keystore local (solo si no existe; no se sobrescribe)"
bash "$SCRIPT_DIR/generate-dev-keystore.sh"

cd "$REPO_ROOT"

if [ ! -f "$REPO_ROOT/.env" ]; then
    cp "$REPO_ROOT/.env.example" "$REPO_ROOT/.env"
    echo "Se creo .env localmente a partir de .env.example (archivo ignorado por git, sin secretos reales)."
fi

# ---------------------------------------------------------------------------
# 4. Validacion de docker compose config
# ---------------------------------------------------------------------------

section "4. Validando docker compose config"
docker compose -f docker-compose.yml -f docker-compose.tls.yml config > "$RAW_DIR/docker-compose-config.txt" 2>&1
# "docker compose config" expande TODAS las variables de entorno definidas en
# .env (contrasenas de BD, JWT_SECRET, contrasena del keystore, etc.) en
# texto plano dentro de su salida. Se redacta el VALOR de cada variable
# sensible por nombre antes de dejar este archivo en el arbol de trabajo,
# independientemente de que el valor sea "solo de desarrollo".
redact_file "$RAW_DIR/docker-compose-config.txt"
echo "Configuracion combinada valida (guardada; valores de DB_PASSWORD/DB_APP_PASSWORD/POSTGRES_PASSWORD/JWT_SECRET/TLS_KEYSTORE_PASSWORD redactados por nombre de variable, no solo el de TLS_KEYSTORE_PASSWORD)."

# ---------------------------------------------------------------------------
# 5. Levantar el stack (no destructivo: sin down -v, sin reset-db)
# ---------------------------------------------------------------------------

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

# El frontend es una limitacion aparte: si esta unhealthy no invalida los
# controles OWASP del backend, que es lo unico que audita este script.
frontend_estado="$(docker inspect biopet-frontend --format '{{.State.Health.Status}}' 2>/dev/null || echo 'desconocido')"
if [ "$frontend_estado" != "healthy" ]; then
    echo "Nota (no bloqueante): el frontend esta en estado '$frontend_estado'. No afecta los controles OWASP del backend evaluados aqui." | tee "$RAW_DIR/frontend-limitacion.txt" >/dev/null
    echo "Nota (no bloqueante): el frontend esta en estado '$frontend_estado'. No afecta los controles OWASP del backend evaluados aqui."
fi

docker compose -f docker-compose.yml -f docker-compose.tls.yml ps > "$RAW_DIR/docker-compose-ps.txt" 2>&1

if [ "$healthy" -ne 1 ]; then
    echo "Error: el backend nunca alcanzo 'healthy'. No es seguro continuar con la evidencia OWASP." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# A01 — Broken Access Control
# ---------------------------------------------------------------------------

section "A01 — Broken Access Control"
A01_FILE="$RAW_DIR/A01-access-control.txt"
: > "$A01_FILE"
{
    echo "# A01 - Broken Access Control - evidencia real"
    echo "# Generado: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "# Commit: $(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo desconocido)"
} >> "$A01_FILE"

# 1) Peticion sin autenticacion a un endpoint protegido -> 401
body1="$(mk_tmp)"
status1="$($CURL_BIN -sS -o "$body1" -w '%{http_code}' "$BASE_HTTPS/api/usuarios/me" -k)"
{
    echo ""
    echo "## 1. Sin autenticacion -> /api/usuarios/me"
    echo "comando: curl -sS -o body.json -w '%{http_code}' $BASE_HTTPS/api/usuarios/me"
    echo "status: $status1"
    cat "$body1"
} >> "$A01_FILE"
check_status "A01.1" "Peticion sin autenticacion a endpoint protegido" \
    "curl $BASE_HTTPS/api/usuarios/me (sin cookies)" "401" "$status1"

# 2) Registrar dos duenos academicos temporales (correo claramente ficticio)
TS="$(date +%s)"
QA_EMAIL_A="qa.owasp.a01.a.${TS}.$$@example.test"
QA_EMAIL_B="qa.owasp.a01.b.${TS}.$$@example.test"
QA_PASS_A="Qa$(openssl rand -hex 6)Zz*"
QA_PASS_B="Qa$(openssl rand -hex 6)Zz*"

reg_body_a="$(mk_tmp)"
reg_status_a="$($CURL_BIN -sS -k -o "$reg_body_a" -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/auth/registro" \
    -H "Content-Type: application/json" \
    -d "{\"nombre\":\"QA OWASP Dueno A\",\"email\":\"$QA_EMAIL_A\",\"password\":\"$QA_PASS_A\",\"rol\":\"ROLE_DUENO\"}")"
QA_ID_A="$(json_field "$reg_body_a" id)"

reg_body_b="$(mk_tmp)"
reg_status_b="$($CURL_BIN -sS -k -o "$reg_body_b" -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/auth/registro" \
    -H "Content-Type: application/json" \
    -d "{\"nombre\":\"QA OWASP Dueno B\",\"email\":\"$QA_EMAIL_B\",\"password\":\"$QA_PASS_B\",\"rol\":\"ROLE_DUENO\"}")"
QA_ID_B="$(json_field "$reg_body_b" id)"

{
    echo ""
    echo "## 2. Cuentas academicas temporales creadas via POST /api/auth/registro"
    echo "Dueno A: email=$QA_EMAIL_A id=$QA_ID_A status_registro=$reg_status_a (rol forzado a ROLE_DUENO por el backend, sin importar el valor enviado)"
    echo "Dueno B: email=$QA_EMAIL_B id=$QA_ID_B status_registro=$reg_status_b"
    echo "Las contrasenas de estas cuentas son aleatorias, generadas en esta ejecucion, y no se imprimen ni se guardan en ningun archivo."
} >> "$A01_FILE"

if [ -z "$QA_ID_A" ] || [ -z "$QA_ID_B" ]; then
    echo "Error: no se pudo registrar alguna de las dos cuentas academicas temporales (status_a=$reg_status_a, status_b=$reg_status_b). Revisa $A01_FILE." >&2
    exit 1
fi

# 3) Login del admin semilla (credenciales via variables de entorno)
admin_jar="$(mk_tmp)"
admin_login_headers="$(mk_tmp)"
admin_login_status="$($CURL_BIN -sS -k -D "$admin_login_headers" -o /dev/null -w '%{http_code}' \
    -c "$admin_jar" \
    -X POST "$BASE_HTTPS/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"
if [ "$admin_login_status" != "200" ]; then
    echo "Error: no se pudo autenticar la cuenta admin semilla (status=$admin_login_status). Verifica ADMIN_EMAIL/ADMIN_PASSWORD o el estado de db/seed.sql." >&2
    exit 1
fi

# 4) Admin crea una mascota asignada al Dueno A
mascota_body="$(mk_tmp)"
mascota_status="$($CURL_BIN -sS -k -b "$admin_jar" -o "$mascota_body" -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/mascotas" \
    -H "Content-Type: application/json" \
    -d "{\"duenioId\":$QA_ID_A,\"nombre\":\"QA-OWASP-Temporal\",\"especie\":\"Perro\",\"raza\":\"Mestizo\",\"fechaNacimiento\":\"2020-01-01\"}")"
QA_MASCOTA_ID="$(json_field "$mascota_body" id)"
{
    echo ""
    echo "## 3-4. Admin autenticado crea una mascota para el Dueno A"
    echo "status_creacion_mascota=$mascota_status mascota_id=$QA_MASCOTA_ID (asignada a dueno_id=$QA_ID_A)"
} >> "$A01_FILE"

if [ -z "$QA_MASCOTA_ID" ]; then
    echo "Error: no se pudo crear la mascota de prueba para el Dueno A (status=$mascota_status). Revisa $A01_FILE." >&2
    exit 1
fi

# 5) Login del Dueno B
duenoB_jar="$(mk_tmp)"
duenoB_login_headers="$(mk_tmp)"
duenoB_login_status="$($CURL_BIN -sS -k -D "$duenoB_login_headers" -o /dev/null -w '%{http_code}' \
    -c "$duenoB_jar" \
    -X POST "$BASE_HTTPS/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$QA_EMAIL_B\",\"password\":\"$QA_PASS_B\"}")"

# 6) A01 caso 3: Dueno B intenta consultar la mascota del Dueno A -> 403 (propiedad)
ownership_body="$(mk_tmp)"
ownership_status="$($CURL_BIN -sS -k -b "$duenoB_jar" -o "$ownership_body" -w '%{http_code}' \
    "$BASE_HTTPS/api/mascotas/$QA_MASCOTA_ID")"
redact_file "$ownership_body"
{
    echo ""
    echo "## 5-6. Dueno B autenticado consulta la mascota del Dueno A (recurso ajeno)"
    echo "comando: curl -b cookiejar $BASE_HTTPS/api/mascotas/$QA_MASCOTA_ID"
    echo "status: $ownership_status"
    cat "$ownership_body"
} >> "$A01_FILE"
check_status "A01.3" "Acceso a recurso de otro propietario (IDOR)" \
    "curl -b cookiejar $BASE_HTTPS/api/mascotas/{id-de-otro-dueno}" "403" "$ownership_status"

# 7) A01 caso 2: Dueno B (autenticado, rol insuficiente) intenta crear una mascota -> 403
role_body="$(mk_tmp)"
role_status="$($CURL_BIN -sS -k -b "$duenoB_jar" -o "$role_body" -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/mascotas" \
    -H "Content-Type: application/json" \
    -d "{\"duenioId\":$QA_ID_B,\"nombre\":\"QA-OWASP-NoDebeCrearse\",\"especie\":\"Gato\",\"raza\":\"Mestizo\",\"fechaNacimiento\":\"2021-01-01\"}")"
redact_file "$role_body"
{
    echo ""
    echo "## 7. Dueno B autenticado (rol insuficiente) intenta crear una mascota"
    echo "comando: curl -b cookiejar -X POST $BASE_HTTPS/api/mascotas"
    echo "status: $role_status"
    cat "$role_body"
} >> "$A01_FILE"
check_status "A01.2" "Usuario autenticado sin privilegios suficientes" \
    "curl -b cookiejar -X POST $BASE_HTTPS/api/mascotas (rol ROLE_DUENO)" "403" "$role_status"

echo "Evidencia A01 guardada en: $A01_FILE"

# ---------------------------------------------------------------------------
# A03 — Injection (reutiliza la sesion admin ya autenticada)
# ---------------------------------------------------------------------------

section "A03 — Injection"
A03_FILE="$RAW_DIR/A03-injection.txt"
: > "$A03_FILE"
{
    echo "# A03 - Injection - evidencia real"
    echo "# Generado: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "# Endpoint y campo elegidos: POST /api/auth/login, campo 'email'."
    echo "# Interpretacion literal de 'un campo de busqueda' de la guia: el campo"
    echo "# email es el campo por el que AuthService localiza/identifica al usuario"
    echo "# a autenticar. Un payload con apariencia de inyeccion SQL no tiene forma"
    echo "# de correo electronico valida, por lo que Bean Validation (@Email sobre"
    echo "# LoginRequest.email) lo rechaza ANTES de que la peticion llegue a"
    echo "# AuthService/UsuarioRepository, con 422 y ProblemDetails real."
} >> "$A03_FILE"

A03_PAYLOADS=("' OR '1'='1" "admin@biopet.com' OR '1'='1" "'; DROP TABLE usuarios; --")
a03_idx=1
for payload in "${A03_PAYLOADS[@]}"; do
    payload_json_escaped="$(printf '%s' "$payload" | sed 's/\\/\\\\/g; s/"/\\"/g')"
    inj_body="$(mk_tmp)"
    inj_status="$($CURL_BIN -sS -k -o "$inj_body" -w '%{http_code}' \
        -X POST "$BASE_HTTPS/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$payload_json_escaped\",\"password\":\"cualquierClave123\"}")"
    redact_file "$inj_body"
    {
        echo ""
        echo "## Payload en el campo email: $payload"
        echo "comando: curl -X POST $BASE_HTTPS/api/auth/login -d '{\"email\":\"<payload>\",\"password\":\"...\"}'"
        echo "status: $inj_status"
        cat "$inj_body"
    } >> "$A03_FILE"
    check_status "A03.$a03_idx" "Payload de inyeccion en campo email -> rechazo por Bean Validation" \
        "curl -X POST $BASE_HTTPS/api/auth/login (email=<payload de inyeccion>)" "422" "$inj_status"
    a03_idx=$((a03_idx + 1))
done

# Evidencia adicional (informativa, NO cuenta como verificacion pass/fail de
# la guia): el mismo tipo de payload contra un parametro Long (duenioId)
# sigue produciendo 400 (MethodArgumentTypeMismatchException, capa de
# binding de Spring MVC, anterior a Bean Validation) - un mecanismo de
# rechazo distinto, igual de seguro (sin fuga, sin alteracion de datos), pero
# que no es el caso que pide literalmente la guia. Se documenta aqui solo
# como profundidad adicional de la defensa, sin afectar el resultado de A03.
extra_body="$(mk_tmp)"
extra_status="$($CURL_BIN -sS -k -b "$admin_jar" -G -o "$extra_body" -w '%{http_code}' \
    --data-urlencode "duenioId=1 OR 1=1" \
    "$BASE_HTTPS/api/mascotas/resumen-especies")"
redact_file "$extra_body"
{
    echo ""
    echo "## Evidencia adicional (no es el caso exigido por la guia; no se evalua pass/fail): mismo tipo de payload en un parametro Long"
    echo "comando: curl -b cookiejar -G --data-urlencode \"duenioId=1 OR 1=1\" $BASE_HTTPS/api/mascotas/resumen-especies"
    echo "status: $extra_status (400 = MethodArgumentTypeMismatchException, capa de binding de Spring MVC; comportamiento seguro pero mecanismo distinto de Bean Validation)"
    cat "$extra_body"
} >> "$A03_FILE"
echo ""
echo "[A03] Evidencia adicional (informativa, no forma parte de la verificacion): duenioId Long con payload de inyeccion -> status $extra_status."

echo "Evidencia A03 guardada en: $A03_FILE"

# ---------------------------------------------------------------------------
# A02 — Cryptographic Failures
# ---------------------------------------------------------------------------

section "A02 — Cryptographic Failures"
A02_FILE="$RAW_DIR/A02-tls.txt"
: > "$A02_FILE"
{
    echo "# A02 - Cryptographic Failures - evidencia real"
    echo "# Generado: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >> "$A02_FILE"

tls_raw="$(mk_tmp)"
echo | openssl s_client -connect localhost:8443 -tls1_3 > "$tls_raw" 2>&1 || true
tls_protocol_line="$(grep -m1 -E 'Protocol *:' "$tls_raw" || true)"
tls_cipher_line="$(grep -m1 -E 'Cipher *:' "$tls_raw" || true)"
{
    echo ""
    echo "## openssl s_client -connect localhost:8443 -tls1_3"
    echo "$tls_protocol_line"
    echo "$tls_cipher_line"
} >> "$A02_FILE"

tls_ok=0
echo "$tls_protocol_line" | grep -q "TLSv1.3" && tls_ok=1 || true
check_bool "A02.1" "HTTPS 8443 negocia TLS 1.3" \
    "openssl s_client -connect localhost:8443 -tls1_3" "protocolo TLSv1.3" "$tls_ok"

cipher_ok=0
echo "$tls_cipher_line" | grep -qE "TLS_AES_256_GCM_SHA384|TLS_CHACHA20_POLY1305_SHA256|TLS_AES_128_GCM_SHA256" && cipher_ok=1 || true
check_bool "A02.2" "Cipher negociado es AEAD" \
    "openssl s_client -connect localhost:8443 -tls1_3" "suite AEAD (TLS_AES_*/CHACHA20)" "$cipher_ok"

# Certificado: subject/SAN, sin exponer clave privada (s_client no la envia)
cert_pem="$(mk_tmp)"
openssl s_client -connect localhost:8443 -tls1_3 </dev/null 2>/dev/null | openssl x509 -noout -text > "$cert_pem" 2>&1 || true
{
    echo ""
    echo "## Certificado servido (subject/SAN, autofirmado academico)"
    grep -E "Subject:|DNS:|IP Address:|Issuer:" "$cert_pem" || echo "(no se pudo extraer subject/SAN; ver limitaciones)"
} >> "$A02_FILE"

http_headers_8080="$(mk_tmp)"
$CURL_BIN -sS -D "$http_headers_8080" -o /dev/null "$BASE_HTTP/actuator/health" || true
redact_file "$http_headers_8080"
{
    echo ""
    echo "## HTTP 8080 (conector interno, sin TLS) - cabeceras"
    cat "$http_headers_8080"
} >> "$A02_FILE"

echo "Evidencia A02 guardada en: $A02_FILE"

# ---------------------------------------------------------------------------
# A05 — Security Misconfiguration
# ---------------------------------------------------------------------------

section "A05 — Security Misconfiguration"
A05_FILE="$RAW_DIR/A05-security-headers.txt"
: > "$A05_FILE"
{
    echo "# A05 - Security Misconfiguration - evidencia real"
    echo "# Generado: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >> "$A05_FILE"

headers_https="$(mk_tmp)"
# No usar "-I" (HEAD) junto con "-X POST -d": curl no garantiza un resultado
# consistente con esa combinacion contra un endpoint que solo acepta POST.
# Se usa el mismo patron -D/-o ya usado con exito en el resto del script.
$CURL_BIN -sS -k -D "$headers_https" -o /dev/null \
    -X POST "$BASE_HTTPS/api/auth/login" \
    -H "Content-Type: application/json" -d '{}' || true
redact_file "$headers_https"
{
    echo ""
    echo "## curl -D headers.txt -X POST $BASE_HTTPS/api/auth/login (endpoint publico)"
    cat "$headers_https"
} >> "$A05_FILE"

headers_http="$(mk_tmp)"
$CURL_BIN -sS -I "$BASE_HTTP/actuator/health" > "$headers_http" 2>&1 || true
redact_file "$headers_http"
{
    echo ""
    echo "## curl -I $BASE_HTTP/actuator/health (sin TLS, para contrastar HSTS)"
    cat "$headers_http"
} >> "$A05_FILE"

check_header() {
    local id="$1" desc="$2" file="$3" patron="$4"
    local ok=0
    grep -qiE "$patron" "$file" && ok=1 || true
    check_bool "$id" "$desc" "curl -I (ver $A05_FILE)" "cabecera presente" "$ok"
}

check_header "A05.1" "Content-Security-Policy presente" "$headers_https" "^content-security-policy:"
check_header "A05.2" "X-Frame-Options: DENY presente" "$headers_https" "^x-frame-options:\s*deny"
check_header "A05.3" "X-Content-Type-Options: nosniff presente" "$headers_https" "^x-content-type-options:\s*nosniff"
check_header "A05.4" "Referrer-Policy presente" "$headers_https" "^referrer-policy:"
check_header "A05.5" "Strict-Transport-Security presente en HTTPS" "$headers_https" "^strict-transport-security:"

hsts_ausente_en_http=0
grep -qi "^strict-transport-security:" "$headers_http" || hsts_ausente_en_http=1
check_bool "A05.6" "Strict-Transport-Security ausente en HTTP (comportamiento correcto)" \
    "curl -I $BASE_HTTP/actuator/health" "cabecera ausente" "$hsts_ausente_en_http"

server_leak=0
grep -qiE "^server:.*(apache|nginx|tomcat/[0-9])" "$headers_https" && server_leak=1 || true
sin_fuga_version_servidor=1
[ "$server_leak" -eq 1 ] && sin_fuga_version_servidor=0 || true
check_bool "A05.7" "Sin version de servidor expuesta en cabeceras" \
    "curl -D headers.txt -X POST $BASE_HTTPS/api/auth/login" "sin cabecera Server: <software>/<version>" "$sin_fuga_version_servidor"

echo "Evidencia A05 guardada en: $A05_FILE"

# ---------------------------------------------------------------------------
# A07 — Identification and Authentication Failures
# ---------------------------------------------------------------------------

section "A07 — Identification and Authentication Failures"
A07_FILE="$RAW_DIR/A07-auth-rate-limit.txt"
: > "$A07_FILE"
{
    echo "# A07 - Identification and Authentication Failures - evidencia real"
    echo "# Generado: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
} >> "$A07_FILE"

# 1) Atributos de la cookie de sesion (sin imprimir el valor completo)
{
    echo ""
    echo "## Atributos de cookies en el login del Dueno B (valor redactado)"
    cat "$duenoB_login_headers"
} >> "$A07_FILE"
redact_file "$A07_FILE"

cookie_attrs_ok=0
(grep -qi "HttpOnly" "$duenoB_login_headers" && grep -qi "Secure" "$duenoB_login_headers" && grep -qi "SameSite=Strict" "$duenoB_login_headers") && cookie_attrs_ok=1 || true
check_bool "A07.cookies" "Cookies con HttpOnly + Secure + SameSite=Strict" \
    "curl -D headers.txt (login real, ver $A07_FILE)" "los tres atributos presentes" "$cookie_attrs_ok"

# 2) Refresh (usa la cookie de refresh del Dueno B)
refresh_headers="$(mk_tmp)"
refresh_status="$($CURL_BIN -sS -k -b "$duenoB_jar" -c "$duenoB_jar" -D "$refresh_headers" -o /dev/null -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/auth/refresh")"
redact_file "$refresh_headers"
{
    echo ""
    echo "## Refresh con cookie valida"
    echo "status: $refresh_status"
    cat "$refresh_headers"
} >> "$A07_FILE"
check_status "A07.refresh" "Refresh con cookie de refresh valida" \
    "curl -b/-c cookiejar -X POST $BASE_HTTPS/api/auth/refresh" "200" "$refresh_status"

# 3) Copia de la cookie ANTES del logout, para probar la blacklist despues
backup_jar="$(mk_tmp)"
cp "$duenoB_jar" "$backup_jar"

# 4) Logout (revoca ambos tokens en Redis y limpia las cookies del cliente)
logout_status="$($CURL_BIN -sS -k -b "$duenoB_jar" -c "$duenoB_jar" -o /dev/null -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/auth/logout")"
{
    echo ""
    echo "## Logout"
    echo "status: $logout_status"
} >> "$A07_FILE"
check_status "A07.logout" "Logout revoca la sesion" \
    "curl -b/-c cookiejar -X POST $BASE_HTTPS/api/auth/logout" "204" "$logout_status"

# 5) Reintento con la cookie ANTIGUA (respaldada antes del logout): debe
#    fallar porque el JTI ya esta en la blacklist de Redis. Nunca se imprime
#    el contenido de backup_jar, solo se usa para la peticion.
replay_status="$($CURL_BIN -sS -k -b "$backup_jar" -o /dev/null -w '%{http_code}' \
    "$BASE_HTTPS/api/usuarios/me")"
{
    echo ""
    echo "## Reintento con la cookie de access previa al logout (prueba de blacklist Redis)"
    echo "status: $replay_status"
} >> "$A07_FILE"
check_status "A07.blacklist" "Blacklist Redis rechaza un token revocado tras logout" \
    "curl -b cookiejar-anterior-al-logout $BASE_HTTPS/api/usuarios/me" "401" "$replay_status"

# 6) Rate limiting: 5 fallos -> 401, 6to -> 429 con Retry-After. Se usa una
#    cuenta ficticia inexistente porque el bloqueo es por IP, no por cuenta,
#    y asi no se interfiere con ninguna cuenta real ni se necesita una
#    contrasena real equivocada de una cuenta que exista.
QA_RL_EMAIL="qa.owasp.a07.ratelimit.${TS}.$$@example.test"
QA_RL_WRONG_PASS="ClaveIncorrecta$(openssl rand -hex 4)*"

for intento in 1 2 3 4 5; do
    fail_status="$($CURL_BIN -sS -k -o /dev/null -w '%{http_code}' \
        -X POST "$BASE_HTTPS/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"$QA_RL_EMAIL\",\"password\":\"$QA_RL_WRONG_PASS\"}")"
    {
        echo ""
        echo "## Intento fallido de login #$intento"
        echo "status: $fail_status"
    } >> "$A07_FILE"
    check_status "A07.attempt$intento" "Intento fallido de login #$intento" \
        "curl -X POST $BASE_HTTPS/api/auth/login (credenciales invalidas)" "401" "$fail_status"
done

sexto_headers="$(mk_tmp)"
sexto_status="$($CURL_BIN -sS -k -D "$sexto_headers" -o /dev/null -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$QA_RL_EMAIL\",\"password\":\"$QA_RL_WRONG_PASS\"}")"
retry_after_val="$(grep -i "^Retry-After:" "$sexto_headers" | tr -d '\r' || true)"
{
    echo ""
    echo "## Sexto intento fallido consecutivo (misma IP)"
    echo "status: $sexto_status"
    echo "$retry_after_val"
} >> "$A07_FILE"
check_status "A07.sexto" "Sexto intento fallido -> bloqueo" \
    "curl -X POST $BASE_HTTPS/api/auth/login (6to intento, misma IP)" "429" "$sexto_status"

retry_after_ok=0
[ -n "$retry_after_val" ] && retry_after_ok=1 || true
check_bool "A07.retryafter" "Cabecera Retry-After presente en el 429" \
    "curl -D headers.txt (ver $A07_FILE)" "cabecera Retry-After numerica" "$retry_after_ok"

{
    echo ""
    echo "## Limitacion documentada"
    echo "No se espera la ventana de bloqueo de 15 minutos (security.rate-limit.login.block-duration)"
    echo "para demostrar un login exitoso posterior desde la misma IP: excede el alcance de una"
    echo "ejecucion automatizada de este script. Esta IP queda bloqueada por ~15 minutos tras esta"
    echo "corrida para nuevos intentos de login desde este mismo entorno."
} >> "$A07_FILE"
echo ""
echo "[A07] Limitacion: no se espero la ventana de bloqueo de 15 min para probar un login exitoso posterior desde la misma IP (documentado en $A07_FILE)."

echo "Evidencia A07 guardada en: $A07_FILE"

# ---------------------------------------------------------------------------
# Limpieza de datos academicos creados (antes de leer los logs de A09)
# ---------------------------------------------------------------------------

section "Limpieza de datos academicos temporales"
cleanup_status="$($CURL_BIN -sS -k -b "$admin_jar" -o /dev/null -w '%{http_code}' \
    -X DELETE "$BASE_HTTPS/api/mascotas/$QA_MASCOTA_ID")"
echo "Eliminacion logica (soft delete) de la mascota de prueba $QA_MASCOTA_ID: status=$cleanup_status"
echo "Nota: no existe endpoint DELETE /api/usuarios; las dos cuentas academicas ($QA_EMAIL_A, $QA_EMAIL_B)"
echo "y la cuenta ficticia de rate limiting ($QA_RL_EMAIL, nunca llego a existir realmente) quedan"
echo "como registros residuales claramente identificables por su dominio 'example.test'."

admin_logout_status="$($CURL_BIN -sS -k -b "$admin_jar" -o /dev/null -w '%{http_code}' \
    -X POST "$BASE_HTTPS/api/auth/logout")"
echo "Logout de la sesion admin: status=$admin_logout_status"

# ---------------------------------------------------------------------------
# A09 — Security Logging and Monitoring Failures
# ---------------------------------------------------------------------------

section "A09 — Security Logging and Monitoring Failures"
A09_FILE="$RAW_DIR/A09-audit-logs.txt"
: > "$A09_FILE"
{
    echo "# A09 - Security Logging and Monitoring Failures - evidencia real"
    echo "# Generado: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "# Fuente: docker compose logs backend (salida estandar del contenedor)"
} >> "$A09_FILE"

full_logs="$(mk_tmp)"
docker compose -f docker-compose.yml -f docker-compose.tls.yml logs backend > "$full_logs" 2>&1 || true

audit_lines="$(mk_tmp)"
grep "AUTH_AUDIT" "$full_logs" > "$audit_lines" || true
redact_file "$audit_lines"

{
    echo ""
    echo "## Ultimas lineas AUTH_AUDIT generadas por esta ejecucion (una muestra por tipo de evento)"
} >> "$A09_FILE"
for evento in LOGIN_SUCCESS LOGIN_FAILURE LOGIN_RATE_LIMITED REFRESH_SUCCESS REFRESH_FAILURE LOGOUT_SUCCESS TOKEN_REVOKED; do
    linea="$(grep "event=$evento" "$audit_lines" | tail -n 1 || true)"
    if [ -n "$linea" ]; then
        echo "$linea" >> "$A09_FILE"
    else
        echo "(sin eventos $evento capturados en esta ejecucion)" >> "$A09_FILE"
    fi
done

eventos_encontrados=0
for evento in LOGIN_SUCCESS LOGIN_FAILURE LOGIN_RATE_LIMITED; do
    grep -q "event=$evento" "$audit_lines" && eventos_encontrados=$((eventos_encontrados + 1)) || true
done
a09_ok=0
[ "$eventos_encontrados" -ge 2 ] && a09_ok=1 || true
check_bool "A09.1" "Eventos AUTH_AUDIT con timestamp/event/result/ip/subject" \
    "docker compose logs backend | grep AUTH_AUDIT" ">=2 de {LOGIN_SUCCESS,LOGIN_FAILURE,LOGIN_RATE_LIMITED}" "$a09_ok"

sin_secretos=1
grep -qiE "password|authorization:|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\." "$audit_lines" && sin_secretos=0 || true
check_bool "A09.2" "Los logs de auditoria no contienen password/JWT/Authorization" \
    "grep -iE 'password|authorization|eyJ' sobre el log ya redactado" "sin coincidencias" "$sin_secretos"

echo "Evidencia A09 guardada en: $A09_FILE"

# ---------------------------------------------------------------------------
# Resumen final
# ---------------------------------------------------------------------------

section "Resumen de los seis controles OWASP"
TOTAL_VERIFICACIONES=0
for c in A01.1 A01.2 A01.3 \
         A02.1 A02.2 \
         A03.1 A03.2 A03.3 \
         A05.1 A05.2 A05.3 A05.4 A05.5 A05.6 A05.7 \
         A07.cookies A07.refresh A07.logout A07.blacklist \
         A07.attempt1 A07.attempt2 A07.attempt3 A07.attempt4 A07.attempt5 \
         A07.sexto A07.retryafter \
         A09.1 A09.2; do
    if [ -n "${RESULTADOS[$c]+x}" ]; then
        echo "  $c: ${RESULTADOS[$c]}"
        TOTAL_VERIFICACIONES=$((TOTAL_VERIFICACIONES + 1))
    else
        echo "  $c: (no se registro resultado — revisar script)"
    fi
done
echo ""
echo "Total: $((TOTAL_VERIFICACIONES - FALLOS)) de $TOTAL_VERIFICACIONES verificaciones puntuales CUMPLE."

echo ""
echo "Archivos de evidencia generados en docs/mediciones/sec/raw/:"
ls -1 "$RAW_DIR" | grep -v '^\.gitkeep$' || true

echo ""
echo "Verificaciones adicionales que siguen requiriendo inspeccion manual (no automatizadas aqui):"
echo "- Revision humana de los archivos .txt generados para confirmar ausencia total de secretos"
echo "  antes de decidir versionarlos (este script ya aplica redaccion automatica defensiva)."

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
echo "Este script no ejecuta git add, git commit ni git push."

if [ "$FALLOS" -gt 0 ]; then
    echo ""
    echo "RESULTADO FINAL: $FALLOS de $TOTAL_VERIFICACIONES verificacion(es) no cumplieron el status/condicion esperada." >&2
    exit 1
fi

echo ""
echo "RESULTADO FINAL: $TOTAL_VERIFICACIONES/$TOTAL_VERIFICACIONES verificaciones OWASP cumplieron el status/condicion esperada."
exit 0
