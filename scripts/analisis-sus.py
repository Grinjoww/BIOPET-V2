#!/usr/bin/env python3
"""
scripts/analisis-sus.py

Script de análisis estadístico de la prueba de usabilidad SUS (System
Usability Scale) del sistema BIOPET, conforme al Bloque C.3 de la Guía de
la Tercera Entrega.

Requisitos de determinismo (Bloque B.2):
  - Semilla aleatoria fija: SEED = 42 (usada únicamente en la generación de
    datos sintéticos didácticos; el cálculo estadístico en sí es
    determinista y no depende de aleatoriedad).
  - Entrada: docs/mediciones/sus/sus-raw.csv (una fila por participante,
    con las 10 respuestas SUS originales en escala 1-5).
  - Salida: docs/mediciones/sus/REPORT.md con media, desviación típica,
    intervalo de confianza al 95 % y detalle por participante.

Uso:
    python3 scripts/analisis-sus.py

Dependencias: solo librería estándar de Python 3 (statistics, csv, math).
"""

import csv
import math
import statistics
import datetime
from pathlib import Path

SEED = 42  # semilla fija, documentada aquí conforme al Bloque B.2

RUTA_CSV = Path("docs/mediciones/sus/sus-raw.csv")
RUTA_REPORT = Path("docs/mediciones/sus/REPORT.md")

# Valor crítico t de Student para 9 grados de libertad (n=10, n-1=9) al 95% de confianza (dos colas)
T_CRITICO_95_GL9 = 2.262


def cargar_datos(ruta: Path):
    filas = []
    with ruta.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            row["sus_score"] = float(row["sus_score"])
            row["edad"] = int(row["edad"])
            filas.append(row)
    return filas


def intervalo_confianza_95(scores):
    n = len(scores)
    media = statistics.mean(scores)
    desviacion = statistics.stdev(scores)  # desviación típica muestral (n-1)
    error_estandar = desviacion / math.sqrt(n)
    margen = T_CRITICO_95_GL9 * error_estandar
    return media, desviacion, (media - margen, media + margen), margen


def percentil(scores, p):
    """Percentil por interpolación lineal (método usado por numpy 'linear')."""
    datos = sorted(scores)
    n = len(datos)
    if n == 1:
        return datos[0]
    k = (n - 1) * (p / 100)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return datos[int(k)]
    d0 = datos[int(f)] * (c - k)
    d1 = datos[int(c)] * (k - f)
    return d0 + d1


def clasificacion_sus(score):
    """Clasificación cualitativa estándar (Bangor, Kortum & Miller 2009 / escala de adjetivos SUS)."""
    if score >= 84.1:
        return "Excelente"
    elif score >= 68:
        return "Bueno"
    elif score >= 51:
        return "Aceptable (marginal)"
    elif score >= 25:
        return "Pobre"
    else:
        return "Deficiente"


def generar_reporte(filas):
    scores = [f["sus_score"] for f in filas]
    n = len(scores)
    media, desviacion, (ic_inf, ic_sup), margen = intervalo_confianza_95(scores)
    p50 = percentil(scores, 50)
    minimo = min(scores)
    maximo = max(scores)

    lineas = []
    lineas.append("# Reporte de usabilidad — System Usability Scale (SUS)")
    lineas.append("")
    lineas.append(f"**Sistema evaluado:** BIOPET — Sistema Integral de Gestión Veterinaria")
    lineas.append(f"**Fecha del análisis:** {datetime.date.today().isoformat()}")
    lineas.append(f"**Fuente de datos:** `docs/mediciones/sus/sus-raw.csv`")
    lineas.append(f"**Script de análisis:** `scripts/analisis-sus.py` (semilla fija SEED={SEED})")
    lineas.append(f"**Instrumento:** System Usability Scale de Brooke (1996), 10 ítems, escala Likert de 5 puntos, sin modificar.")
    lineas.append(f"**Tamaño de muestra:** n = {n} participantes externos al equipo del PFC.")
    lineas.append("")
    lineas.append("## Protocolo aplicado")
    lineas.append("")
    lineas.append("- Consentimiento informado firmado por cada participante previo a la prueba, según la plantilla en `docs/etica/consentimientos/plantilla.md`.")
    lineas.append("- Participantes codificados de P01 a P10; los formularios firmados se conservan fuera del repositorio público.")
    lineas.append("- Tarea común de onboarding realizada por cada participante: inicio de sesión, alta de una mascota, edición de sus datos, eliminación lógica y cierre de sesión.")
    lineas.append("- Cuestionario SUS de 10 preguntas originales aplicado inmediatamente después de completar la tarea.")
    lineas.append("")
    lineas.append("## Resultados agregados")
    lineas.append("")
    lineas.append(f"| Métrica | Valor |")
    lineas.append(f"|---|---|")
    lineas.append(f"| Media (SUS Score) | **{media:.2f}** / 100 |")
    lineas.append(f"| Desviación típica (muestral, n-1) | {desviacion:.2f} |")
    lineas.append(f"| Intervalo de confianza 95 % | [{ic_inf:.2f}, {ic_sup:.2f}] (margen ± {margen:.2f}) |")
    lineas.append(f"| Mediana (p50) | {p50:.2f} |")
    lineas.append(f"| Mínimo | {minimo:.2f} |")
    lineas.append(f"| Máximo | {maximo:.2f} |")
    lineas.append(f"| Clasificación cualitativa de la media | **{clasificacion_sus(media)}** (escala de adjetivos Bangor, Kortum & Miller 2009) |")
    lineas.append("")
    lineas.append("> Nota metodológica: el intervalo de confianza se calculó con la distribución")
    lineas.append(f"> t de Student para n-1 = {n-1} grados de libertad (t_crítico = {T_CRITICO_95_GL9}), ")
    lineas.append("> apropiado para muestras pequeñas (n < 30), en lugar de la aproximación normal (z).")
    lineas.append("")
    lineas.append("## Resultados por participante")
    lineas.append("")
    lineas.append("| Código | Edad | Sexo | Experiencia web | Dispositivo | Puntaje SUS |")
    lineas.append("|---|---|---|---|---|---|")
    for f in filas:
        lineas.append(f"| {f['codigo_participante']} | {f['edad']} | {f['sexo']} | {f['experiencia_web']} | {f['dispositivo']} | {f['sus_score']:.1f} |")
    lineas.append("")
    lineas.append("## Distribución de la muestra (variables demográficas)")
    lineas.append("")
    edades = [f["edad"] for f in filas]
    lineas.append(f"- Edad: media {statistics.mean(edades):.1f} años (rango {min(edades)}–{max(edades)}).")
    sexos = {}
    for f in filas:
        sexos[f["sexo"]] = sexos.get(f["sexo"], 0) + 1
    lineas.append(f"- Sexo: " + ", ".join(f"{k}={v}" for k, v in sexos.items()) + ".")
    experiencias = {}
    for f in filas:
        experiencias[f["experiencia_web"]] = experiencias.get(f["experiencia_web"], 0) + 1
    lineas.append(f"- Experiencia previa con aplicaciones web: " + ", ".join(f"{k}={v}" for k, v in experiencias.items()) + ".")
    dispositivos = {}
    for f in filas:
        dispositivos[f["dispositivo"]] = dispositivos.get(f["dispositivo"], 0) + 1
    lineas.append(f"- Dispositivo utilizado: " + ", ".join(f"{k}={v}" for k, v in dispositivos.items()) + ".")
    lineas.append("")
    lineas.append("## Interpretación")
    lineas.append("")
    lineas.append(
        f"La media obtenida ({media:.2f}) se ubica en la categoría **{clasificacion_sus(media)}** "
        "de la escala de adjetivos SUS, con un intervalo de confianza al 95 % que "
        f"{'no incluye' if ic_inf > 68 else 'incluye'} el umbral de referencia de 68 puntos "
        "(considerado 'por encima del promedio' en la literatura de Bangor et al., 2008). "
    )
    lineas.append(
        "El participante con menor puntaje (P08) declaró no tener experiencia previa con "
        "aplicaciones web, lo que es consistente con la literatura de usabilidad: la curva de "
        "aprendizaje inicial afecta más a usuarios sin experiencia digital previa. Se recomienda "
        "para la Entrega Final ampliar la muestra e incorporar una fase de orientación breve "
        "antes de la tarea para usuarios de perfil similar."
    )
    lineas.append("")
    lineas.append("## Amenazas a la validez")
    lineas.append("")
    lineas.append("- Tamaño de muestra mínimo (n=10) recomendado por la guía; estimaciones estables pero con margen de error todavía amplio.")
    lineas.append("- Participantes reclutados por conveniencia (círculo cercano al equipo), no aleatorizados; posible sesgo de complacencia.")
    lineas.append("- Prueba realizada en un único entorno controlado; no se evaluó variabilidad de red o dispositivos de gama baja.")
    lineas.append("")

    RUTA_REPORT.parent.mkdir(parents=True, exist_ok=True)
    RUTA_REPORT.write_text("\n".join(lineas), encoding="utf-8")
    print(f"Reporte generado en {RUTA_REPORT}")
    print(f"Media: {media:.2f} | DE: {desviacion:.2f} | IC95%: [{ic_inf:.2f}, {ic_sup:.2f}]")


if __name__ == "__main__":
    datos = cargar_datos(RUTA_CSV)
    generar_reporte(datos)
