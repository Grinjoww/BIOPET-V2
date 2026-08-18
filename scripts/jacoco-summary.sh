#!/usr/bin/env bash
#
# scripts/jacoco-summary.sh
#
# Recalcula e imprime la cobertura real LINE/BRANCH a partir del CSV que
# jacoco-maven-plugin genera en Backend/target/site/jacoco/jacoco.csv
# (sumando todas las filas/clases, no un valor fijo). Usado por
# "make jacoco" para dar visibilidad rapida sin volver a correr toda la
# suite de pruebas.
#
# El gate real y obligatorio (falla "mvn clean verify" si no se cumple)
# ya esta enlazado a la fase "verify" en Backend/pom.xml
# (jacoco-maven-plugin, execution "check", LINE >= 0.70, BRANCH >= 0.70).
# Este script es una verificacion adicional de ese mismo umbral sobre el
# reporte ya generado, para que "make jacoco" tambien falle (exit != 0) si
# por algun motivo el porcentaje recalculado esta por debajo del 70%.
#
# Uso:
#   scripts/jacoco-summary.sh [ruta-al-jacoco.csv]
#   (por defecto: Backend/target/site/jacoco/jacoco.csv)
#
# Codigo de salida:
#   0  el reporte existe y LINE >= 70% y BRANCH >= 70%
#   1  el reporte existe pero LINE y/o BRANCH esta por debajo de 70%
#   2  error de uso/entorno (reporte no encontrado, python3 ausente)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT="${1:-$REPO_ROOT/Backend/target/site/jacoco/jacoco.csv}"

if [ ! -f "$REPORT" ]; then
    echo "Error: no se encontro el reporte JaCoCo en: $REPORT" >&2
    echo "Ejecuta primero: make backend  (mvn clean verify)" >&2
    exit 2
fi

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
else
    echo "Error: se requiere python3 (o python) en el PATH." >&2
    exit 2
fi

"$PYTHON_BIN" - "$REPORT" <<'PYEOF'
import csv
import sys

report_path = sys.argv[1]
line_missed = line_covered = branch_missed = branch_covered = 0

with open(report_path, newline='', encoding='utf-8') as fh:
    for row in csv.DictReader(fh):
        line_missed += int(row['LINE_MISSED'])
        line_covered += int(row['LINE_COVERED'])
        branch_missed += int(row['BRANCH_MISSED'])
        branch_covered += int(row['BRANCH_COVERED'])

line_total = line_missed + line_covered
branch_total = branch_missed + branch_covered
line_pct = (line_covered / line_total * 100) if line_total else 0.0
branch_pct = (branch_covered / branch_total * 100) if branch_total else 0.0

print(f"Reporte analizado: {report_path}")
print(f"JaCoCo LINE:   {line_covered}/{line_total} = {line_pct:.1f}%")
print(f"JaCoCo BRANCH: {branch_covered}/{branch_total} = {branch_pct:.1f}%")
print("")

fallo = False
if line_pct < 70.0:
    print("FALLA: LINE por debajo del umbral 70%.")
    fallo = True
if branch_pct < 70.0:
    print("FALLA: BRANCH por debajo del umbral 70%.")
    fallo = True

if fallo:
    sys.exit(1)
else:
    print("PASA: LINE y BRANCH por encima del umbral 70%.")
    sys.exit(0)
PYEOF
