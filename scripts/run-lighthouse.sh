#!/usr/bin/env bash
#
# scripts/run-lighthouse.sh
#
# Ejecuta la auditoría Lighthouse exigida por el bloque C.5 de la Guía y
# archiva los resultados crudos en docs/mediciones/lighthouse/ tal cual los
# produce la herramienta (bloque B.2: "los archivos crudos deben conservarse
# tal cual, su edición manual invalida la evidencia").
#
# Precondición: el frontend debe estar sirviendo en http://localhost:4200
# DESDE EL CONTENEDOR real (no "ng serve"), es decir: haber corrido
# `make up` o `docker compose up -d` antes de este script.
#
# Uso:
#   bash scripts/run-lighthouse.sh
#
# Corre los DOS perfiles exigidos: móvil (lighthouserc.js, perfil por
# defecto del bloque C.5) y desktop (lighthouserc.desktop.js). Mismas URLs,
# mismos umbrales, mismo numberOfRuns; solo cambia el formFactor.
#
# Salida (por cada perfil, sufijo "-mobile"/"-desktop"):
#   docs/mediciones/lighthouse/lhci-YYYYMMDD-HHMM-mobile-login-runN.json
#   docs/mediciones/lighthouse/lhci-YYYYMMDD-HHMM-mobile-mascotas-runN.json
#   docs/mediciones/lighthouse/lhci-YYYYMMDD-HHMM-desktop-login-runN.json
#   docs/mediciones/lighthouse/lhci-YYYYMMDD-HHMM-desktop-mascotas-runN.json
#   docs/mediciones/lighthouse/lhci-YYYYMMDD-HHMM.meta.txt          (fecha ISO 8601,
#       commit hash corto, version de node/lighthouse — exigido por bloque B.2)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/docs/mediciones/lighthouse"
STAMP="$(date -u +%Y%m%d-%H%M)"
ISO_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
COMMIT="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo "sin-git")"

mkdir -p "$OUT_DIR"

echo "== Verificando que el frontend responda en http://localhost:4200 =="
if ! curl -sSf -o /dev/null "http://localhost:4200/login"; then
  echo "ERROR: http://localhost:4200/login no responde."
  echo "Corre 'make up' (o 'docker compose up -d') antes de este script."
  exit 1
fi

cd "$REPO_ROOT"

# En Windows, chrome-launcher falla de forma intermitente al borrar su
# carpeta temporal tras cerrar Chrome (EPERM: normalmente el antivirus
# retiene el handle un instante). Es un fallo transitorio del entorno, no
# de la app ni de la configuracion; se reintenta el autorun completo hasta
# 3 veces antes de abortar de verdad.
run_lhci_autorun() {
  local config="$1"
  local attempt
  for attempt in 1 2 3; do
    echo "-- lhci autorun (config=$config, intento $attempt/3) --"
    if npx --yes @lhci/cli@0.14.x autorun --config="$config"; then
      return 0
    fi
    echo "-- intento $attempt fallo (posible EPERM transitorio de chrome-launcher en Windows); reintentando --"
  done
  echo "ERROR: lhci autorun (config=$config) fallo 3 veces seguidas."
  return 1
}

# Archiva los reportes crudos de un perfil ($1=nombre de perfil,
# $2=directorio .lighthouseci-* generado por ese perfil) con el nombre
# exigido por la Guía, sin modificar su contenido.
archive_profile() {
  local profile="$1"
  local raw_dir="$2"
  if [[ ! -d "$raw_dir" ]]; then
    echo "ERROR: no se encontró $raw_dir; ¿lhci autorun (perfil $profile) falló antes de generar reportes?"
    exit 1
  fi
  local i=0
  for f in "$raw_dir"/lhr-*.json; do
    [[ -e "$f" ]] || continue
    local url slug
    url=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['requestedUrl'])" "$f")
    slug=$(echo "$url" | sed -E 's#https?://[^/]+/##; s#[^a-zA-Z0-9]+#-#g')
    cp "$f" "$OUT_DIR/lhci-${STAMP}-${profile}-${slug:-root}-run${i}.json"
    i=$((i+1))
  done
}

echo "== [1/2] Ejecutando lhci autorun — perfil movil/Slow 4G (lighthouserc.js) =="
rm -rf "$REPO_ROOT/.lighthouseci"
run_lhci_autorun lighthouserc.js
echo "== Archivando resultados crudos (movil) en $OUT_DIR =="
archive_profile "mobile" "$REPO_ROOT/.lighthouseci"

echo "== [2/2] Ejecutando lhci autorun — perfil desktop (lighthouserc.desktop.js) =="
rm -rf "$REPO_ROOT/.lighthouseci-desktop"
run_lhci_autorun lighthouserc.desktop.js
echo "== Archivando resultados crudos (desktop) en $OUT_DIR =="
archive_profile "desktop" "$REPO_ROOT/.lighthouseci-desktop"

cat > "$OUT_DIR/lhci-${STAMP}.meta.txt" <<EOF
fecha_iso8601: $ISO_DATE
commit_hash_corto: $COMMIT
node_version: $(node --version)
lighthouse_cli_version: $(npx --yes @lhci/cli@0.14.x --version 2>/dev/null || echo "desconocida")
urls_auditadas: http://localhost:4200/login, http://localhost:4200/mascotas
perfiles: mobile (throttlingMethod=simulate, Slow 4G equivalente, lighthouserc.js), desktop (formFactor=desktop, throttling devtools preset, lighthouserc.desktop.js)
corridas_por_url: 3 por perfil (6 total por URL, 12 total)
EOF

echo ""
echo "Listo. Revisa $OUT_DIR y agrega los .json + .meta.txt generados a Git:"
echo "  git add docs/mediciones/lighthouse/"
echo "  git commit -m 'docs(mediciones): agrega corrida real de Lighthouse'"
