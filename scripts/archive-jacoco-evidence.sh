#!/usr/bin/env bash
# Archiva la evidencia real de cobertura JaCoCo (Entrega Final) desde
# Backend/target/site/jacoco/ hacia docs/mediciones/jacoco/, de forma
# reproducible. No genera el reporte por sí mismo: ejecuta primero
# `mvn clean verify` en Backend/.
#
# Uso:
#   bash scripts/archive-jacoco-evidence.sh
#
# Requiere que Backend/target/site/jacoco/jacoco.xml y jacoco.csv ya
# existan (es decir, que `mvn clean verify` se haya ejecutado antes).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$REPO_ROOT/Backend/target/site/jacoco"
DEST="$REPO_ROOT/docs/mediciones/jacoco"

if [ ! -f "$SRC/jacoco.xml" ] || [ ! -f "$SRC/jacoco.csv" ]; then
  echo "ERROR: no se encontró $SRC/jacoco.xml o jacoco.csv." >&2
  echo "Ejecuta primero: cd Backend && mvn clean verify" >&2
  exit 1
fi

rm -rf "$DEST/html"
mkdir -p "$DEST/html"

# Reporte XML (fuente de los contadores LINE/BRANCH/COMPLEXITY reales)
cp "$SRC/jacoco.xml" "$DEST/jacoco.xml"
cp "$SRC/jacoco.csv" "$DEST/jacoco.csv"

# Reporte HTML navegable completo (index.html + por-paquete + recursos)
cp -r "$SRC/." "$DEST/html/"

# Métricas reales extraídas del propio jacoco.xml (no inventadas a mano)
LINE_MISSED=$(grep -o '<counter type="LINE"[^/]*/>' "$SRC/jacoco.xml" | tail -1 | grep -o 'missed="[0-9]*"' | grep -o '[0-9]*')
LINE_COVERED=$(grep -o '<counter type="LINE"[^/]*/>' "$SRC/jacoco.xml" | tail -1 | grep -o 'covered="[0-9]*"' | grep -o '[0-9]*')
BRANCH_MISSED=$(grep -o '<counter type="BRANCH"[^/]*/>' "$SRC/jacoco.xml" | tail -1 | grep -o 'missed="[0-9]*"' | grep -o '[0-9]*')
BRANCH_COVERED=$(grep -o '<counter type="BRANCH"[^/]*/>' "$SRC/jacoco.xml" | tail -1 | grep -o 'covered="[0-9]*"' | grep -o '[0-9]*')
COMPLEXITY_MISSED=$(grep -o '<counter type="COMPLEXITY"[^/]*/>' "$SRC/jacoco.xml" | tail -1 | grep -o 'missed="[0-9]*"' | grep -o '[0-9]*')
COMPLEXITY_COVERED=$(grep -o '<counter type="COMPLEXITY"[^/]*/>' "$SRC/jacoco.xml" | tail -1 | grep -o 'covered="[0-9]*"' | grep -o '[0-9]*')

LINE_PCT=$(awk -v c="$LINE_COVERED" -v m="$LINE_MISSED" 'BEGIN{printf "%.2f", (c/(c+m))*100}')
BRANCH_PCT=$(awk -v c="$BRANCH_COVERED" -v m="$BRANCH_MISSED" 'BEGIN{printf "%.2f", (c/(c+m))*100}')
COMPLEXITY_PCT=$(awk -v c="$COMPLEXITY_COVERED" -v m="$COMPLEXITY_MISSED" 'BEGIN{printf "%.2f", (c/(c+m))*100}')

cat > "$DEST/METRICS.md" <<EOF
# Métricas JaCoCo archivadas — Entrega Final v1.0.0

Generado automáticamente por \`scripts/archive-jacoco-evidence.sh\` a partir
de \`Backend/target/site/jacoco/jacoco.xml\` (no editado a mano).

| Métrica | Cubierto | No cubierto | Cobertura | Umbral \`pom.xml\` |
|---|---:|---:|---:|---:|
| LINE | $LINE_COVERED | $LINE_MISSED | ${LINE_PCT} % | 0.70 |
| BRANCH | $BRANCH_COVERED | $BRANCH_MISSED | ${BRANCH_PCT} % | 0.70 |
| COMPLEXITY | $COMPLEXITY_COVERED | $COMPLEXITY_MISSED | ${COMPLEXITY_PCT} % | 0.60 |

Reproducción:

\`\`\`bash
cd Backend
mvn clean verify
cd ..
bash scripts/archive-jacoco-evidence.sh
\`\`\`

Archivos:

- \`jacoco.xml\` — reporte XML completo generado por JaCoCo.
- \`jacoco.csv\` — reporte CSV por clase generado por JaCoCo.
- \`html/index.html\` — reporte HTML navegable completo (con desglose por paquete y clase).
EOF

echo "Evidencia JaCoCo archivada en: $DEST"
echo "LINE: ${LINE_PCT}%  BRANCH: ${BRANCH_PCT}%  COMPLEXITY: ${COMPLEXITY_PCT}%"
