#!/usr/bin/env bash
#
# scripts/validate-traceability.sh
#
# Exigido por el bloque A.3.3 de la Guía de la Tercera Entrega:
#   "El pipeline de CI ejecuta un script de validación
#   (scripts/validate-traceability.sh) que rechaza commits en los que se
#   agregue un requisito sin correspondencia en al menos una historia, un
#   caso de uso o una prueba."
#
# Qué valida (todo contra archivos reales del repo, nada hardcodeado):
#   1. Todo REQ-F-/REQ-NF- declarado en docs/requisitos/SRS.md tiene una fila
#      en docs/trazabilidad/matriz.csv (y viceversa: la matriz no inventa
#      requisitos que el SRS no declara).
#   2. Cada fila de la matriz tiene al menos historia_usuario, caso_de_uso o
#      prueba_automatizada rellenos (no puede ir un requisito sin ningún
#      tipo de respaldo).
#   3. Toda referencia HU-NNN / CU-NN en la matriz apunta a una historia o
#      caso de uso que realmente existe en HistoriasUsuario.md/CasosDeUso.md
#      (esto es justamente lo que falló con HU-021/CU-021 antes de esta
#      corrección: la matriz señalaba historias que ya no existían).
#   4. No hay identificadores de requisito duplicados en la matriz.
#
# Salida: "VALIDACION OK" y exit 0 si todo pasa; lista de errores y exit 1
# si algo falla, para que el pipeline de CI (.github/workflows/) rechace el
# commit.
#
# Requiere python3 (preinstalado en los runners ubuntu-latest de GitHub
# Actions; en local, cualquier Python 3 sirve). No usa librerías externas.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRS="$REPO_ROOT/docs/requisitos/SRS.md"
MATRIZ="$REPO_ROOT/docs/trazabilidad/matriz.csv"
HISTORIAS="$REPO_ROOT/docs/requisitos/historias/HistoriasUsuario.md"
CASOS="$REPO_ROOT/docs/requisitos/casos-de-uso/CasosDeUso.md"

for f in "$SRS" "$MATRIZ" "$HISTORIAS" "$CASOS"; do
  if [[ ! -f "$f" ]]; then
    echo "VALIDACION FALLIDA: no existe el archivo requerido: $f"
    exit 1
  fi
done

if ! command -v python3 >/dev/null 2>&1; then
  echo "VALIDACION FALLIDA: se requiere python3 para ejecutar este script."
  exit 1
fi

python3 - "$SRS" "$MATRIZ" "$HISTORIAS" "$CASOS" <<'PYEOF'
import csv
import re
import sys

srs_path, matriz_path, historias_path, casos_path = sys.argv[1:5]
errores = []

# --- 1. Requisitos declarados en el SRS -------------------------------
srs_text = open(srs_path, encoding="utf-8").read()

# Cubre tanto los bloques detallados ("**REQ-F-001 — ...**") como las filas
# de la tabla de pendientes ("| REQ-F-013 | ...") y cualquier REQ-NF-NNN.
ids_srs = set(re.findall(r"REQ-(?:F|NF)-\d{3}", srs_text))

if not ids_srs:
    errores.append("No se encontró ningún REQ-F-/REQ-NF- en el SRS. "
                    "¿Cambió el formato de los encabezados?")

# --- 2. Filas de la matriz ---------------------------------------------
with open(matriz_path, newline="", encoding="utf-8") as f:
    filas = list(csv.DictReader(f))

columnas_esperadas = {
    "id_requisito", "tipo", "prioridad_moscow", "historia_usuario",
    "caso_de_uso", "modulo_codigo", "endpoint_api", "prueba_automatizada",
    "tipo_acceso", "evidencia_empirica", "estado",
}
if filas:
    faltantes = columnas_esperadas - set(filas[0].keys())
    if faltantes:
        errores.append(f"matriz.csv no tiene las columnas obligatorias: "
                        f"{sorted(faltantes)}")

ids_matriz = [fila["id_requisito"].strip() for fila in filas]

# 2a. Duplicados
vistos = set()
for rid in ids_matriz:
    if rid in vistos:
        errores.append(f"Id de requisito duplicado en matriz.csv: {rid}")
    vistos.add(rid)
ids_matriz_set = set(ids_matriz)

# 2b. Todo requisito del SRS debe estar en la matriz
faltan_en_matriz = ids_srs - ids_matriz_set
for rid in sorted(faltan_en_matriz):
    errores.append(f"{rid} está en el SRS pero no tiene fila en matriz.csv")

# 2c. La matriz no debe inventar requisitos que el SRS no declara
sobran_en_matriz = ids_matriz_set - ids_srs
for rid in sorted(sobran_en_matriz):
    errores.append(f"{rid} está en matriz.csv pero no existe en el SRS "
                    f"(¿requisito eliminado o typo en el id?)")

# --- 3. Cada fila necesita al menos historia, caso de uso o prueba -----
for fila in filas:
    rid = fila["id_requisito"].strip()
    hu = fila.get("historia_usuario", "").strip()
    cu = fila.get("caso_de_uso", "").strip()
    prueba = fila.get("prueba_automatizada", "").strip()
    if not (hu or cu or prueba):
        errores.append(
            f"{rid} no tiene historia_usuario, caso_de_uso ni "
            f"prueba_automatizada: un requisito no puede quedar sin "
            f"ningún respaldo (bloque A.3.3 de la Guía)."
        )

# --- 4. Las HU/CU referenciadas deben existir realmente ----------------
historias_texto = open(historias_path, encoding="utf-8").read()
casos_texto = open(casos_path, encoding="utf-8").read()

hu_declaradas = set(re.findall(r"^## (HU-\d{3})", historias_texto, re.MULTILINE))
cu_declarados = set(re.findall(r"^## (CU-\d{2,3})", casos_texto, re.MULTILINE))

if not hu_declaradas:
    errores.append("No se encontró ninguna historia '## HU-NNN' en "
                    "HistoriasUsuario.md. ¿Cambió el formato de encabezados?")
if not cu_declarados:
    errores.append("No se encontró ningún caso de uso '## CU-NN' en "
                    "CasosDeUso.md. ¿Cambió el formato de encabezados?")

for fila in filas:
    rid = fila["id_requisito"].strip()
    hu = fila.get("historia_usuario", "").strip()
    cu = fila.get("caso_de_uso", "").strip()

    if hu:
        for hu_id in [h.strip() for h in hu.split(",") if h.strip()]:
            if hu_id not in hu_declaradas:
                errores.append(
                    f"{rid} referencia {hu_id} en matriz.csv, pero esa "
                    f"historia no existe en HistoriasUsuario.md "
                    f"(¿se renombró o se borró?)."
                )
    if cu:
        for cu_id in [c.strip() for c in cu.split(",") if c.strip()]:
            if cu_id not in cu_declarados:
                errores.append(
                    f"{rid} referencia {cu_id} en matriz.csv, pero ese "
                    f"caso de uso no existe en CasosDeUso.md "
                    f"(¿se renombró o se borró?)."
                )

# --- 5. Cobertura 100% de requisitos Must vigentes ----------------------
must_srs = set(re.findall(r"REQ-(?:F|NF)-\d{3}(?=[^\n]*\bMust\b)", srs_text))
# Nota: el patrón anterior es deliberadamente conservador (solo dispara
# sobre la misma línea donde aparece "Must"), así que se complementa con
# una verificación directa en la matriz: todo REQ presente con
# prioridad_moscow=Must debe tener estado no vacío.
for fila in filas:
    if fila.get("prioridad_moscow", "").strip() == "Must" and not fila.get("estado", "").strip():
        errores.append(
            f"{fila['id_requisito']} es prioridad Must pero no tiene "
            f"'estado' declarado en matriz.csv."
        )

# --- Resultado ------------------------------------------------------------
if errores:
    print("VALIDACION FALLIDA:\n")
    for e in errores:
        print(f"  - {e}")
    print(f"\nTotal: {len(errores)} error(es).")
    sys.exit(1)

print(
    f"VALIDACION OK: {len(ids_srs)} requisitos del SRS, "
    f"{len(filas)} filas en matriz.csv, "
    f"{len(hu_declaradas)} historias y {len(cu_declarados)} casos de uso "
    f"consistentes entre sí."
)
PYEOF
