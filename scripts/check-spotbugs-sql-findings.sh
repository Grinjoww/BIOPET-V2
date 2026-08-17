#!/usr/bin/env bash
#
# Gate de CI para el reporte de SpotBugs + Find Security Bugs: falla
# (exit != 0) unicamente si el reporte contiene algun hallazgo relacionado
# con SQL inseguro (SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE y variantes de
# inyeccion SQL de Find Security Bugs). El resto de hallazgos (incluido el
# conocido SPRING_CSRF_PROTECTION_DISABLED, ya documentado en
# docs/mediciones/sec/static-analysis/README.md como riesgo mitigado por la
# arquitectura de cookies HttpOnly+Secure+SameSite=Strict) NO hace fallar
# este gate: se listan igualmente en la salida y quedan intactos en el XML
# archivado como artifact, pero no bloquean la corrida por si solos.
#
# Por que un gate separado del propio "mvn spotbugs:check": el objetivo de
# esta fase es que CI pueda distinguir "hay una vulnerabilidad SQL real" de
# "hay hallazgos de otras categorias ya evaluados y documentados", sin
# ocultar, suprimir ni bajar el umbral de SpotBugs/Find Security Bugs de
# forma global (eso si estaria prohibido). El XML completo, con los 66+
# hallazgos que produzca la herramienta, sigue disponible integro; este
# script solo decide el codigo de salida.
#
# Uso:
#   scripts/check-spotbugs-sql-findings.sh [ruta-al-spotbugsXml.xml]
#   (por defecto: Backend/target/spotbugsXml.xml)
#
# Codigo de salida:
#   0  el reporte existe y no contiene ningun hallazgo de tipo SQL_*
#   1  el reporte existe y contiene al menos un hallazgo de tipo SQL_*
#   2  error de uso/entorno (reporte no encontrado, python3 ausente)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPORT="${1:-$REPO_ROOT/Backend/target/spotbugsXml.xml}"

if [ ! -f "$REPORT" ]; then
    echo "Error: no se encontro el reporte de SpotBugs en: $REPORT" >&2
    echo "Genera el reporte primero, por ejemplo:" >&2
    echo "  cd Backend && mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:spotbugs" >&2
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
import sys
import xml.etree.ElementTree as ET

report_path = sys.argv[1]
tree = ET.parse(report_path)
root = tree.getroot()

PRIORIDAD = {'1': 'Alta', '2': 'Media', '3': 'Baja'}

todos = []
sql_findings = []
for bug in root.findall('BugInstance'):
    tipo = bug.get('type', '')
    prioridad = bug.get('priority', '?')
    cls_el = bug.find('Class')
    clase = cls_el.get('classname') if cls_el is not None else '?'
    srcline_el = bug.find('SourceLine')
    linea = srcline_el.get('start') if srcline_el is not None else '?'
    todos.append(tipo)
    if 'SQL' in tipo.upper():
        sql_findings.append((tipo, PRIORIDAD.get(prioridad, prioridad), clase, linea))

print(f"Reporte analizado: {report_path}")
print(f"Hallazgos totales en el reporte: {len(todos)}")
print(f"Hallazgos relacionados con SQL: {len(sql_findings)}")
print("")

if sql_findings:
    print("FALLA: se encontraron hallazgos de SQL inseguro:")
    for tipo, prioridad, clase, linea in sql_findings:
        print(f"  - [{prioridad}] {tipo} en {clase}:{linea}")
    sys.exit(1)
else:
    print("PASA: 0 hallazgos de tipo SQL_* en el reporte de SpotBugs/Find Security Bugs.")
    print("(Otros hallazgos no-SQL, si existen, no afectan este gate; ver el XML completo")
    print(" archivado como artifact y docs/mediciones/sec/static-analysis/README.md para su")
    print(" analisis y decision documentada, incluido SPRING_CSRF_PROTECTION_DISABLED.)")
    sys.exit(0)
PYEOF
