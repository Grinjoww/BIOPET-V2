"""
scripts/perf-analysis.py
Analiza los JSON crudos generados por k6 (--out json=...) y calcula:
media, mediana, desviacion estandar, intervalo de confianza 95% (t de Student),
percentiles p50/p90/p95/p99, tasa de error HTTP >= 500 y throughput (req/s),
test de Wilcoxon pareado (caliente vs frio) con tamano de efecto (r de Rosenthal)
y genera un grafico vectorial SVG reproducible (Python puro, sin matplotlib).

Este script no genera datos aleatorios, por lo que no requiere semilla fija.
Se apoya en scipy.stats.t para el intervalo de confianza (mas apropiado que la
aproximacion normal para muestras pequenas), scipy.stats.wilcoxon para el test
pareado y statistics/numpy para el resto.

Uso:
    python scripts/perf-analysis.py docs/mediciones/perf/k6-run1-frio.json
    python scripts/perf-analysis.py docs/mediciones/perf/k6-*-caliente-0*.json docs/mediciones/perf/k6-*-frio-0*.json
    python scripts/perf-analysis.py docs/mediciones/perf/k6-*-{caliente,frio}-0*.json --report docs/mediciones/perf/REPORT.md --grafico docs/mediciones/perf/grafico.svg

Pareo para el Wilcoxon: cada corrida "caliente-NN" se compara con su par
"frio-NN" (mismo numero de corrida). Si no hay NN en el nombre, se usa el
patron historico k6-runN-{caliente,frio}.json. Las duraciones se parean por
indice de llegada truncando al menor tamano de muestra.
"""

import argparse
import glob
import json
import re
import statistics
import sys
from pathlib import Path

import numpy as np
from scipy import stats

# Colores del grafico SVG
COLOR_CALIENTE = "#2e7d32"
COLOR_FRIO = "#e65100"


def cargar_metricas(ruta_json):
    """Lee el archivo NDJSON de k6 y extrae duraciones (ms), fallos y timestamps."""
    duraciones = []
    fallos = 0
    total_peticiones = 0
    timestamps = []

    with open(ruta_json, "r", encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if not linea:
                continue
            try:
                punto = json.loads(linea)
            except json.JSONDecodeError:
                continue

            if punto.get("type") != "Point":
                continue

            metrica = punto.get("metric")
            data = punto.get("data", {})

            if metrica == "http_req_duration":
                duraciones.append(data.get("value"))
                timestamps.append(data.get("time"))
            elif metrica == "http_reqs":
                total_peticiones += 1
            elif metrica == "http_req_failed" and data.get("value") == 1:
                fallos += 1

    return duraciones, fallos, total_peticiones, timestamps


def calcular_estadisticas(duraciones, fallos, total_peticiones, timestamps):
    n = len(duraciones)
    if n == 0:
        return None

    media = statistics.mean(duraciones)
    mediana = statistics.median(duraciones)
    desviacion = statistics.stdev(duraciones) if n > 1 else 0.0

    # Intervalo de confianza 95% usando distribucion t de Student (scipy)
    error_estandar = desviacion / (n ** 0.5) if n > 1 else 0.0
    ic_bajo, ic_alto = stats.t.interval(
        confidence=0.95, df=n - 1, loc=media, scale=error_estandar
    ) if n > 1 else (media, media)

    arr = np.array(duraciones)
    p50 = np.percentile(arr, 50)
    p90 = np.percentile(arr, 90)
    p95 = np.percentile(arr, 95)
    p99 = np.percentile(arr, 99)

    tasa_error = (fallos / total_peticiones * 100) if total_peticiones else 0.0

    if timestamps:
        # timestamps de k6 vienen en formato ISO 8601; calculamos el rango cubierto.
        from datetime import datetime
        t_min = min(datetime.fromisoformat(t.replace("Z", "+00:00")) for t in timestamps)
        t_max = max(datetime.fromisoformat(t.replace("Z", "+00:00")) for t in timestamps)
        duracion_s = max((t_max - t_min).total_seconds(), 1)
    else:
        duracion_s = 1

    throughput = total_peticiones / duracion_s

    return {
        "n": n,
        "media_ms": round(media, 2),
        "mediana_ms": round(mediana, 2),
        "desviacion_ms": round(desviacion, 2),
        "ic95_bajo_ms": round(ic_bajo, 2),
        "ic95_alto_ms": round(ic_alto, 2),
        "p50_ms": round(p50, 2),
        "p90_ms": round(p90, 2),
        "p95_ms": round(p95, 2),
        "p99_ms": round(p99, 2),
        "tasa_error_pct": round(tasa_error, 2),
        "throughput_rps": round(throughput, 2),
        "total_peticiones": total_peticiones,
    }


def clasificar_ruta(ruta):
    """Devuelve (clave_base, numero, modo) a partir del nombre del archivo.

    Soporta el esquema oficial k6-<fecha>-<entorno>-<version>-<modo>-<NN>.json
    y el historico k6-runN-{caliente,frio}.json.
    """
    nombre = Path(ruta).name
    m = re.match(r"^(?P<base>.*?)-(?P<modo>caliente|frio)(?:-(?P<nn>\d+))?\.json$", nombre)
    if not m:
        return None
    base = m.group("base")
    modo = m.group("modo")
    nn = m.group("nn")
    if nn is None:
        # patron historico: k6-runN-caliente.json -> base "k6-runN"
        rm = re.search(r"run(\d+)$", base)
        nn = rm.group(1) if rm else "0"
    if re.search(r"run(\d+)$", base):
        # el numero ya esta embebido en el base historico; no duplicarlo
        return base, "", modo
    # esquema oficial: k6-<YYYYMMDDTHHMMSS>-<entorno>-<version>-<modo>-<NN>.json
    # el pareo caliente/frio usa el numero de corrida (NN), ignorando la fecha
    # del inicio de cada corrida (que difiere entre pares).
    resto = re.sub(r"^k6-\d{8}T\d{6}-", "", base)
    return resto, nn, modo


def wilcoxon_pareado(duraciones_a, duraciones_b):
    """Wilcoxon de rangos con signo sobre muestras pareadas por indice.

    Devuelve (W, p, r) donde r es el tamano de efecto de Rosenthal
    (r = Z / sqrt(n_pares)). Si las muestras son identicas o n < 3,
    devuelve (None, None, None) por falta de pares utiles.
    """
    n_pares = min(len(duraciones_a), len(duraciones_b))
    if n_pares < 3:
        return None, None, None

    a = np.array(duraciones_a[:n_pares])
    b = np.array(duraciones_b[:n_pares])
    diferencias = b - a

    if not np.any(diferencias):
        return None, None, None

    resultado = stats.wilcoxon(diferencias, zero_method="wilcox", method="approx")
    W = float(resultado.statistic)
    p = float(resultado.pvalue)
    Z = float(resultado.zstatistic)
    r = Z / (n_pares ** 0.5)
    return round(W, 2), round(p, 6), round(r, 4)


def generar_svg(ruta_svg, grupos):
    """Genera un grafico de barras SVG reproducible (sin matplotlib).

    grupos: lista de dicts con {nombre, caliente: stats, frio: stats}
    donde cada stats es el dict de calcular_estadisticas.
    """
    W, H = 900, 460
    margen_izq, margen_der, margen_sup, margen_inf = 70, 30, 60, 60
    ancho_grafico = W - margen_izq - margen_der
    alto_grafico = H - margen_sup - margen_inf

    metricas = ["p50_ms", "p90_ms", "p95_ms", "p99_ms"]
    etiquetas = ["p50", "p90", "p95", "p99"]

    # valor maximo para la escala del eje Y
    max_val = 0.0
    for g in grupos:
        for modo in ("caliente", "frio"):
            s = g[modo]
            if s:
                max_val = max(max_val, s["p99_ms"], s["media_ms"] + s["desviacion_ms"])
    escala = max_val * 1.15 if max_val > 0 else 1.0

    def y_de(valor):
        return margen_sup + alto_grafico - (valor / escala) * alto_grafico

    lineas = []
    lineas.append(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
        f'viewBox="0 0 {W} {H}">'
    )
    lineas.append(
        f'<text x="{W / 2}" y="30" text-anchor="middle" font-family="sans-serif" '
        f'font-size="18" font-weight="bold">BIOPET - latencia por percentil '
        f'(caliente vs frio)</text>'
    )

    # eje Y: 5 divisiones
    for i in range(6):
        valor = escala * i / 5
        y = y_de(valor)
        lineas.append(
            f'<line x1="{margen_izq}" y1="{y}" x2="{W - margen_der}" y2="{y}" '
            f'stroke="#ccc" stroke-width="1"/>'
        )
        lineas.append(
            f'<text x="{margen_izq - 8}" y="{y + 4}" text-anchor="end" '
            f'font-family="sans-serif" font-size="11">{valor:.0f}</text>'
        )

    # barras por grupo de percentil, promediando las corridas de cada modo
    n_metrica = len(metricas)
    paso = ancho_grafico / n_metrica
    ancho_barra = min(paso * 0.28, 40.0)

    for i, metrica in enumerate(metricas):
        cx = margen_izq + paso * (i + 0.5)
        for j, modo in enumerate(("caliente", "frio")):
            valores = [g[modo][metrica] for g in grupos if g[modo]]
            if not valores:
                continue
            media = sum(valores) / len(valores)
            color = COLOR_CALIENTE if modo == "caliente" else COLOR_FRIO
            x = cx - ancho_barra + j * ancho_barra
            y = y_de(media)
            altura = margen_sup + alto_grafico - y
            lineas.append(
                f'<rect x="{x:.1f}" y="{y:.1f}" width="{ancho_barra:.1f}" '
                f'height="{altura:.1f}" fill="{color}" opacity="0.85"/>'
            )
            lineas.append(
                f'<text x="{x + ancho_barra / 2:.1f}" y="{y - 5:.1f}" text-anchor="middle" '
                f'font-family="sans-serif" font-size="10">{media:.1f}</text>'
            )
        lineas.append(
            f'<text x="{cx}" y="{margen_sup + alto_grafico + 20}" text-anchor="middle" '
            f'font-family="sans-serif" font-size="12" font-weight="bold">{etiquetas[i]}</text>'
        )

    # leyenda
    ly = margen_sup + alto_grafico + 40
    lineas.append(
        f'<rect x="{margen_izq}" y="{ly}" width="14" height="14" fill="{COLOR_CALIENTE}"/>'
    )
    lineas.append(
        f'<text x="{margen_izq + 20}" y="{ly + 12}" font-family="sans-serif" '
        f'font-size="12">caliente</text>'
    )
    lineas.append(
        f'<rect x="{margen_izq + 110}" y="{ly}" width="14" height="14" fill="{COLOR_FRIO}"/>'
    )
    lineas.append(
        f'<text x="{margen_izq + 130}" y="{ly + 12}" font-family="sans-serif" '
        f'font-size="12">frio</text>'
    )
    lineas.append(
        f'<text x="{W - margen_der}" y="{ly + 12}" text-anchor="end" '
        f'font-family="sans-serif" font-size="10" fill="#666">'
        f'latencia en ms (promedio entre corridas del mismo modo); grafico generado '
        f'por scripts/perf-analysis.py</text>'
    )

    lineas.append("</svg>")

    with open(ruta_svg, "w", encoding="utf-8") as f:
        f.write("\n".join(lineas))
    return len(grupos) * len(metricas) * 2


def main():
    parser = argparse.ArgumentParser(description="Analisis estadistico de corridas k6")
    parser.add_argument("archivos", nargs="+", help="Archivos JSON crudos de k6 (soporta comodines)")
    parser.add_argument("--report", help="Ruta del REPORT.md a generar", default=None)
    parser.add_argument("--grafico", help="Ruta del grafico SVG a generar", default=None)
    args = parser.parse_args()

    rutas = []
    for patron in args.archivos:
        rutas.extend(sorted(glob.glob(patron)))

    if not rutas:
        print("No se encontraron archivos para analizar.")
        sys.exit(1)

    # agrupar por (base, numero) y clasificar modo
    pares = {}
    for ruta in rutas:
        clas = clasificar_ruta(ruta)
        if clas is None:
            print(f"Advertencia: {Path(ruta).name} no sigue el esquema esperado, se omite.")
            continue
        base, nn, modo = clas
        clave = (base, nn)
        if clave not in pares:
            pares[clave] = {"caliente": None, "frio": None}
        pares[clave][modo] = ruta

    if not pares:
        print("Ningun archivo reconocido con el esquema k6-...-{caliente,frio}-NN.json")
        sys.exit(1)

    filas_md = []
    corridas = []

    for clave, modos in sorted(pares.items()):
        base, nn = clave
        stats_modos = {}
        duraciones_modos = {}
        for modo in ("caliente", "frio"):
            ruta = modos[modo]
            if ruta is None:
                stats_modos[modo] = None
                duraciones_modos[modo] = []
                continue
            duraciones, fallos, total, timestamps = cargar_metricas(ruta)
            calc = calcular_estadisticas(duraciones, fallos, total, timestamps)
            stats_modos[modo] = calc
            duraciones_modos[modo] = duraciones
            nombre = Path(ruta).name

            if calc is None:
                print(f"[{nombre}] Sin datos de http_req_duration, se omite.")
                continue

            print(f"\n=== {nombre} ===")
            for k, v in calc.items():
                print(f"  {k}: {v}")

            filas_md.append((nombre, calc))

        corridas.append(
            {
                "clave": f"{base}-{nn}" if nn else base,
                "caliente": stats_modos["caliente"],
                "frio": stats_modos["frio"],
                "duraciones_caliente": duraciones_modos["caliente"],
                "duraciones_frio": duraciones_modos["frio"],
            }
        )

    # Wilcoxon pareado por corrida y agregado
    filas_wilcoxon = []
    for c in corridas:
        if not c["caliente"] or not c["frio"]:
            continue
        W, p, r = wilcoxon_pareado(
            c["duraciones_caliente"],
            c["duraciones_frio"],
        )
        if W is None:
            continue
        filas_wilcoxon.append((c["clave"], W, p, r))

    if filas_wilcoxon:
        print("\n=== Wilcoxon pareado (caliente vs frio, por corrida) ===")
        for clave, W, p, r in filas_wilcoxon:
            print(f"  {clave}: W={W}, p={p}, r={r}")

    if args.grafico:
        n_barras = generar_svg(args.grafico, corridas)
        print(f"\nGrafico SVG generado en: {args.grafico} ({n_barras} barras)")

    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            f.write("# Reporte de rendimiento — BIOPET (k6)\n\n")
            f.write(
                "Metodo de intervalo de confianza: distribucion t de Student (scipy.stats.t), "
                "apropiada para el tamano de muestra de estas corridas.\n\n"
            )
            f.write(
                "| Corrida | n | Media (ms) | Mediana (ms) | DE (ms) | IC95% (ms) | p50 | p90 | p95 | p99 | Error (%) | Throughput (req/s) |\n"
            )
            f.write("|---|---|---|---|---|---|---|---|---|---|---|---|\n")
            for nombre, s in filas_md:
                f.write(
                    f"| {nombre} | {s['n']} | {s['media_ms']} | {s['mediana_ms']} | {s['desviacion_ms']} | "
                    f"[{s['ic95_bajo_ms']}, {s['ic95_alto_ms']}] | {s['p50_ms']} | {s['p90_ms']} | "
                    f"{s['p95_ms']} | {s['p99_ms']} | {s['tasa_error_pct']} | {s['throughput_rps']} |\n"
                )

            if filas_wilcoxon:
                f.write("\n## Wilcoxon pareado (caliente vs frio)\n\n")
                f.write(
                    "Pareo por indice de llegada (truncando al menor tamano de muestra). "
                    "Tamano de efecto r de Rosenthal (r = Z / sqrt(n)).\n\n"
                )
                f.write("| Par | W | p | r |\n|---|---|---|---|\n")
                for clave, W, p, r in filas_wilcoxon:
                    f.write(f"| {clave} | {W} | {p} | {r} |\n")

            if args.grafico:
                f.write(f"\n## Grafico\n\n![Latencia por percentil]({Path(args.grafico).name})\n")

        print(f"\nReporte generado en: {args.report}")


if __name__ == "__main__":
    main()