#!/usr/bin/env bash
#
# Gate de CI para el reporte JSON de OWASP ZAP Baseline: falla (exit != 0)
# unicamente si el reporte contiene al menos una alerta de riesgo ALTO
# real (riskcode="3" en el JSON de ZAP, la escala propia de la
# herramienta: 0=Informational, 1=Low, 2=Medium, 3=High).
#
# Por que un gate separado del propio codigo de salida de zap-baseline.py:
# ese script clasifica sus resultados como PASS/WARN/FAIL segun reglas de
# configuracion (por defecto, casi todo queda en WARN, y "-I" evita que un
# WARN por si solo haga fallar el paso) — una clasificacion pensada para
# uso interactivo, no directamente equivalente a "severidad alta real" en
# el sentido de OWASP. Este script lee el riskcode real de cada alerta
# para decidir el fallo de CI de forma explicita y auditable.
#
# Uso:
#   scripts/check-zap-high-severity.sh [ruta-al-zap-baseline-report.json]
#   (por defecto: docs/mediciones/sec/zap/zap-baseline-report.json)
#
# Codigo de salida:
#   0  el reporte existe y no contiene ninguna alerta de riskcode alto (3)
#   1  el reporte existe y contiene al menos una alerta de riskcode alto (3)
#   2  error de uso/entorno (reporte no encontrado, python3 ausente)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT="${1:-$REPO_ROOT/docs/mediciones/sec/zap/zap-baseline-report.json}"

if [ ! -f "$REPORT" ]; then
    echo "Error: no se encontro el reporte JSON de ZAP en: $REPORT" >&2
    echo "Ejecuta primero: scripts/run-zap-baseline.sh" >&2
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
import json
import sys

report_path = sys.argv[1]
with open(report_path, encoding='utf-8') as fh:
    data = json.load(fh)

RISK = {'0': 'Informational', '1': 'Low', '2': 'Medium', '3': 'High'}

total_alerts = 0
altas = []
for site in data.get('site', []):
    for alert in site.get('alerts', []):
        total_alerts += 1
        riskcode = alert.get('riskcode', '0')
        if riskcode == '3':
            altas.append((alert.get('alert', '?'), alert.get('count', '?'), site.get('@name', '?')))

print(f"Reporte analizado: {report_path}")
print(f"Tipos de alerta totales: {total_alerts}")
print(f"Alertas de riesgo ALTO (riskcode=3): {len(altas)}")
print("")

if altas:
    print("FALLA: se encontraron alertas de severidad alta:")
    for nombre, count, site in altas:
        print(f"  - {nombre} (x{count}) en {site}")
    sys.exit(1)
else:
    print("PASA: 0 alertas de riesgo alto en el reporte ZAP Baseline.")
    print("(Alertas de riesgo medio/bajo/informativo, si existen, no afectan este gate;")
    print(" ver el reporte HTML/XML/JSON completo archivado como artifact.)")
    sys.exit(0)
PYEOF
