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
# Salida:
#   docs/mediciones/lighthouse/lhci-YYYYMMDD-HHMM-login.json        (crudo, sin tocar)
#   docs/mediciones/lighthouse/lhci-YYYYMMDD-HHMM-mascotas.json     (crudo, sin tocar)
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

echo "== Ejecutando lhci autorun (3 corridas por URL, perfil movil/Slow 4G) =="
cd "$REPO_ROOT"
npx --yes @lhci/cli@0.14.x autorun --config=lighthouserc.js

echo "== Archivando resultados crudos en $OUT_DIR =="
RAW_DIR="$REPO_ROOT/.lighthouseci"
if [[ ! -d "$RAW_DIR" ]]; then
  echo "ERROR: no se encontró $RAW_DIR; ¿lhci autorun falló antes de generar reportes?"
  exit 1
fi

# lhci nombra cada corrida como lhr-<n>.json; los copiamos con el nombre
# exigido por la Guía sin modificar su contenido.
i=0
for f in "$RAW_DIR"/lhr-*.json; do
  [[ -e "$f" ]] || continue
  url=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['requestedUrl'])" "$f")
  slug=$(echo "$url" | sed -E 's#https?://[^/]+/##; s#[^a-zA-Z0-9]+#-#g')
  cp "$f" "$OUT_DIR/lhci-${STAMP}-${slug:-root}-run${i}.json"
  i=$((i+1))
done

cat > "$OUT_DIR/lhci-${STAMP}.meta.txt" <<EOF
fecha_iso8601: $ISO_DATE
commit_hash_corto: $COMMIT
node_version: $(node --version)
lighthouse_cli_version: $(npx --yes @lhci/cli@0.14.x --version 2>/dev/null || echo "desconocida")
urls_auditadas: http://localhost:4200/login, http://localhost:4200/mascotas
perfil: mobile, throttlingMethod=simulate (Slow 4G equivalente)
corridas_por_url: 3
EOF

echo ""
echo "Listo. Revisa $OUT_DIR y agrega los .json + .meta.txt generados a Git:"
echo "  git add docs/mediciones/lighthouse/"
echo "  git commit -m 'docs(mediciones): agrega corrida real de Lighthouse'"
