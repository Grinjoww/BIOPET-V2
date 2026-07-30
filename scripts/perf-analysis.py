"""
scripts/perf-analysis.py
Analiza los JSON crudos generados por k6 (--out json=...) y calcula:
media, mediana, desviacion estandar, intervalo de confianza 95% (t de Student),
percentiles p50/p90/p95/p99, tasa de error HTTP >= 500 y throughput (req/s).

Este script no genera datos aleatorios, por lo que no requiere semilla fija.
Se apoya en scipy.stats.t para el intervalo de confianza (mas apropiado que la
aproximacion normal para muestras pequenas) y en statistics/numpy para el resto.

Uso:
    python scripts/perf-analysis.py docs/mediciones/perf/k6-run1-frio.json
    python scripts/perf-analysis.py docs/mediciones/perf/*.json --report docs/mediciones/perf/REPORT.md
"""

import argparse
import glob
import json
import statistics
import sys
from pathlib import Path

import numpy as np
from scipy import stats


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


def main():
    parser = argparse.ArgumentParser(description="Analisis estadistico de corridas k6")
    parser.add_argument("archivos", nargs="+", help="Archivos JSON crudos de k6 (soporta comodines)")
    parser.add_argument("--report", help="Ruta del REPORT.md a generar", default=None)
    args = parser.parse_args()

    rutas = []
    for patron in args.archivos:
        rutas.extend(sorted(glob.glob(patron)))

    if not rutas:
        print("No se encontraron archivos para analizar.")
        sys.exit(1)

    filas_md = []
    for ruta in rutas:
        duraciones, fallos, total, timestamps = cargar_metricas(ruta)
        stats_calc = calcular_estadisticas(duraciones, fallos, total, timestamps)
        nombre = Path(ruta).name

        if stats_calc is None:
            print(f"[{nombre}] Sin datos de http_req_duration, se omite.")
            continue

        print(f"\n=== {nombre} ===")
        for k, v in stats_calc.items():
            print(f"  {k}: {v}")

        filas_md.append((nombre, stats_calc))

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
        print(f"\nReporte generado en: {args.report}")


if __name__ == "__main__":
    main()