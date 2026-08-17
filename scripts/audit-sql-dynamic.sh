#!/usr/bin/env bash
#
# Audita db/procs/*.sql en busca de SQL dinamico construido de forma
# insegura (concatenacion/interpolacion de valores no confiables dentro de
# una sentencia EXECUTE de PL/pgSQL, o sintaxis ajena a PostgreSQL que
# suele indicar SQL copiado/generado para otro motor).
#
# IMPORTANTE: PostgreSQL usa EXECUTE de forma legitima dentro de PL/pgSQL
# (SQL dinamico) y tambien para ejecutar sentencias preparadas
# (`EXECUTE nombre_preparado(...)`) y en definiciones de trigger
# (`CREATE TRIGGER ... EXECUTE FUNCTION fn()`). Ninguno de esos usos es, por
# si mismo, un problema. Este script NO marca cada aparicion de la palabra
# EXECUTE como vulnerabilidad: solo marca una sentencia EXECUTE cuando,
# ademas, concatena texto con el operador "||" SIN pasar por un mecanismo
# seguro de PostgreSQL (`format()`, `quote_ident()`, `quote_literal()`,
# `quote_nullable()`) y SIN usar la clausula `USING` (paso de parametros
# real, la forma recomendada de SQL dinamico parametrizado en PL/pgSQL).
#
# Patrones que SIEMPRE se marcan, sin importar concatenacion, por ser
# sintaxis ajena a PostgreSQL (indicio de copia/generacion desde otro
# motor, ej. Oracle o SQL Server, o de una construccion sospechosa):
#   - EXECUTE IMMEDIATE   (PL/SQL de Oracle; PostgreSQL no lo soporta)
#   - sp_executesql       (T-SQL de SQL Server; PostgreSQL no lo soporta)
#
# Uso:
#   scripts/audit-sql-dynamic.sh                 # audita db/procs/*.sql
#   scripts/audit-sql-dynamic.sh archivo1.sql ... # audita archivos puntuales
#                                                  # (usado por el auto-test,
#                                                  # ver scripts/audit-sql-dynamic-selftest.sh)
#
# Codigo de salida:
#   0  ningun patron prohibido encontrado (o no hay archivos que auditar)
#   1  se encontro al menos un patron prohibido
#   2  error de uso/entorno (ej. falta python3)
#
# Determinista: mismo contenido de archivo => mismo resultado siempre. No
# depende de la fecha, del entorno de red ni de estado externo.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
else
    echo "Error: se requiere python3 (o python) en el PATH para analizar el SQL." >&2
    exit 2
fi

# Si se pasan argumentos, audita exactamente esos archivos (usado por el
# self-test contra fixtures fuera de db/procs/). Sin argumentos, audita
# todo db/procs/*.sql tal como exista en el momento de la ejecucion —
# incluyendo archivos que Fred agregue despues, sin necesidad de tocar
# este script.
if [ "$#" -gt 0 ]; then
    FILES=("$@")
else
    PROCS_DIR="$REPO_ROOT/db/procs"
    FILES=()
    if [ -d "$PROCS_DIR" ]; then
        while IFS= read -r -d '' f; do
            FILES+=("$f")
        done < <(find "$PROCS_DIR" -maxdepth 1 -type f -name '*.sql' -print0 | sort -z)
    fi
fi

if [ "${#FILES[@]}" -eq 0 ]; then
    echo "audit-sql-dynamic: no se encontraron archivos .sql para auditar (nada que reportar)."
    exit 0
fi

echo "audit-sql-dynamic: analizando ${#FILES[@]} archivo(s):"
for f in "${FILES[@]}"; do
    echo "  - $f"
done
echo ""

# El detector real vive en Python embebido (mejor soporte de regex
# multilinea que un pipeline puro de sed/awk para bloques EXECUTE ... ;
# que pueden abarcar varias lineas). La logica de decision esta comentada
# en linea para que quede legible sin salir de este archivo.
"$PYTHON_BIN" - "${FILES[@]}" <<'PYEOF'
import re
import sys

# Patrones ajenos a PostgreSQL: si aparecen, siempre es un hallazgo,
# independientemente de si hay concatenacion.
SIEMPRE_PELIGROSO = [
    (re.compile(r'EXECUTE\s+IMMEDIATE', re.IGNORECASE), "EXECUTE IMMEDIATE (sintaxis PL/SQL de Oracle; ajena a PostgreSQL)"),
    (re.compile(r'\bsp_executesql\b', re.IGNORECASE), "sp_executesql (sintaxis T-SQL de SQL Server; ajena a PostgreSQL)"),
]

# Mecanismos seguros de PostgreSQL para construir SQL dinamico: si una
# sentencia EXECUTE los usa, la concatenacion "||" dentro de esa misma
# sentencia no se marca (se asume interpolacion segura via %I/%L o
# escape explicito).
WRAPPER_SEGURO_RE = re.compile(r'\b(FORMAT|QUOTE_IDENT|QUOTE_LITERAL|QUOTE_NULLABLE)\s*\(', re.IGNORECASE)
USING_RE = re.compile(r'\bUSING\b', re.IGNORECASE)

# Una sentencia EXECUTE de PL/pgSQL, capturada desde la palabra EXECUTE
# hasta el ";" que la cierra (DOTALL para permitir que abarque varias
# lineas, como suele ocurrir con SQL dinamico multilinea real).
EXECUTE_STMT_RE = re.compile(r'\bEXECUTE\b[^;]*;', re.IGNORECASE | re.DOTALL)


def quitar_comentarios(texto):
    """Elimina comentarios de bloque /* ... */ y de linea -- para no
    disparar falsos positivos por la palabra EXECUTE (u otro patron)
    mencionada dentro de un comentario explicativo.
    Limitacion conocida y documentada: no distingue "--" dentro de un
    literal de cadena de un comentario real; en el SQL de este proyecto
    (funciones PL/pgSQL simples) ese caso no se presenta.
    """
    texto = re.sub(r'/\*.*?\*/', '', texto, flags=re.DOTALL)
    texto = re.sub(r'--[^\n]*', '', texto)
    return texto


def numero_de_linea(texto, posicion):
    return texto.count('\n', 0, posicion) + 1


def auditar_archivo(ruta):
    hallazgos = []
    with open(ruta, encoding='utf-8') as fh:
        original = fh.read()
    limpio = quitar_comentarios(original)

    # 1) Patrones ajenos a PostgreSQL (EXECUTE IMMEDIATE, sp_executesql):
    #    se buscan en el archivo completo, no solo dentro de bloques que
    #    empiecen literalmente con la palabra "EXECUTE" — sp_executesql en
    #    T-SQL real se invoca con "EXEC sp_executesql ...", no "EXECUTE".
    lineas_ya_reportadas_siempre = set()
    for patron, motivo in SIEMPRE_PELIGROSO:
        for match in patron.finditer(limpio):
            linea = numero_de_linea(limpio, match.start())
            inicio_linea = limpio.rfind('\n', 0, match.start()) + 1
            fin_linea = limpio.find('\n', match.start())
            if fin_linea == -1:
                fin_linea = len(limpio)
            fragmento = ' '.join(limpio[inicio_linea:fin_linea].split())
            if len(fragmento) > 160:
                fragmento = fragmento[:157] + '...'
            hallazgos.append((linea, fragmento, [motivo]))
            lineas_ya_reportadas_siempre.add(linea)

    # 2) Concatenacion insegura dentro de sentencias EXECUTE de PL/pgSQL
    #    (especificas de PostgreSQL: EXECUTE <expresion-texto> [USING ...]).
    for match in EXECUTE_STMT_RE.finditer(limpio):
        sentencia = match.group(0)
        linea = numero_de_linea(limpio, match.start())

        tiene_concatenacion = '||' in sentencia
        tiene_wrapper_seguro = bool(WRAPPER_SEGURO_RE.search(sentencia))
        tiene_using = bool(USING_RE.search(sentencia))

        if tiene_concatenacion and not tiene_wrapper_seguro and not tiene_using:
            motivo = (
                'EXECUTE con concatenacion "||" sin USING ni FORMAT/QUOTE_IDENT/'
                'QUOTE_LITERAL/QUOTE_NULLABLE: SQL dinamico construido de forma '
                'insegura (posible inyeccion SQL si el valor concatenado proviene '
                'de una entrada no confiable)'
            )
            fragmento = ' '.join(sentencia.split())
            if len(fragmento) > 160:
                fragmento = fragmento[:157] + '...'
            if linea in lineas_ya_reportadas_siempre:
                # Ya reportada por un patron "siempre peligroso" en la misma
                # linea (ej. EXECUTE IMMEDIATE con concatenacion): se agrega
                # el motivo adicional a ese mismo hallazgo en vez de duplicar
                # la linea como un hallazgo aparte.
                for i, (l, f, razones) in enumerate(hallazgos):
                    if l == linea:
                        razones.append(motivo)
                        hallazgos[i] = (l, f, razones)
                        break
            else:
                hallazgos.append((linea, fragmento, [motivo]))

    hallazgos.sort(key=lambda h: h[0])
    return hallazgos


def main():
    archivos = sys.argv[1:]
    total_hallazgos = 0

    for ruta in archivos:
        hallazgos = auditar_archivo(ruta)
        if not hallazgos:
            print(f"OK   {ruta}: sin patrones de SQL dinamico peligroso.")
            continue
        for linea, fragmento, razones in hallazgos:
            total_hallazgos += 1
            print(f"FAIL {ruta}:{linea}")
            print(f"     sentencia: {fragmento}")
            for r in razones:
                print(f"     motivo   : {r}")

    print("")
    if total_hallazgos == 0:
        print(f"audit-sql-dynamic: 0 hallazgos en {len(archivos)} archivo(s). PASA.")
        sys.exit(0)
    else:
        print(f"audit-sql-dynamic: {total_hallazgos} hallazgo(s) en {len(archivos)} archivo(s). FALLA.")
        sys.exit(1)


main()
PYEOF
