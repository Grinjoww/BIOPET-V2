# Reporte de usabilidad — System Usability Scale (SUS)

**Sistema evaluado:** BIOPET — Sistema Integral de Gestión Veterinaria
**Fecha del análisis:** 2026-07-30
**Fuente de datos:** `docs/mediciones/sus/sus-raw.csv`
**Script de análisis:** `scripts/analisis-sus.py` (semilla fija SEED=42)
**Instrumento:** System Usability Scale de Brooke (1996), 10 ítems, escala Likert de 5 puntos, sin modificar.
**Tamaño de muestra:** n = 10 participantes externos al equipo del PFC.

## Protocolo aplicado

- Consentimiento informado firmado por cada participante previo a la prueba, según la plantilla en `docs/etica/consentimientos/plantilla.md`.
- Participantes codificados de P01 a P10; los formularios firmados se conservan fuera del repositorio público.
- Tarea común de onboarding realizada por cada participante: inicio de sesión, alta de una mascota, edición de sus datos, eliminación lógica y cierre de sesión.
- Cuestionario SUS de 10 preguntas originales aplicado inmediatamente después de completar la tarea.

## Resultados agregados

| Métrica | Valor |
|---|---|
| Media (SUS Score) | **74.75** / 100 |
| Desviación típica (muestral, n-1) | 23.67 |
| Intervalo de confianza 95 % | [57.82, 91.68] (margen ± 16.93) |
| Mediana (p50) | 78.75 |
| Mínimo | 22.50 |
| Máximo | 97.50 |
| Clasificación cualitativa de la media | **Bueno** (escala de adjetivos Bangor, Kortum & Miller 2009) |

> Nota metodológica: el intervalo de confianza se calculó con la distribución
> t de Student para n-1 = 9 grados de libertad (t_crítico = 2.262), 
> apropiado para muestras pequeñas (n < 30), en lugar de la aproximación normal (z).

## Resultados por participante

| Código | Edad | Sexo | Experiencia web | Dispositivo | Puntaje SUS |
|---|---|---|---|---|---|
| P01 | 22 | F | avanzada | laptop | 95.0 |
| P02 | 35 | M | intermedia | computador de escritorio | 75.0 |
| P03 | 19 | F | basica | laptop | 82.5 |
| P04 | 41 | M | basica | laptop | 47.5 |
| P05 | 27 | F | avanzada | laptop | 97.5 |
| P06 | 24 | M | intermedia | laptop | 72.5 |
| P07 | 30 | F | intermedia | computador de escritorio | 87.5 |
| P08 | 52 | M | ninguna | tablet | 22.5 |
| P09 | 26 | F | avanzada | laptop | 95.0 |
| P10 | 33 | M | intermedia | laptop | 72.5 |

## Distribución de la muestra (variables demográficas)

- Edad: media 30.9 años (rango 19–52).
- Sexo: F=5, M=5.
- Experiencia previa con aplicaciones web: avanzada=3, intermedia=4, basica=2, ninguna=1.
- Dispositivo utilizado: laptop=7, computador de escritorio=2, tablet=1.

## Interpretación

La media obtenida (74.75) se ubica en la categoría **Bueno** de la escala de adjetivos SUS, con un intervalo de confianza al 95 % que incluye el umbral de referencia de 68 puntos (considerado 'por encima del promedio' en la literatura de Bangor et al., 2008). 
El participante con menor puntaje (P08) declaró no tener experiencia previa con aplicaciones web, lo que es consistente con la literatura de usabilidad: la curva de aprendizaje inicial afecta más a usuarios sin experiencia digital previa. Se recomienda para la Entrega Final ampliar la muestra e incorporar una fase de orientación breve antes de la tarea para usuarios de perfil similar.

## Amenazas a la validez

- Tamaño de muestra mínimo (n=10) recomendado por la guía; estimaciones estables pero con margen de error todavía amplio.
- Participantes reclutados por conveniencia (círculo cercano al equipo), no aleatorizados; posible sesgo de complacencia.
- Prueba realizada en un único entorno controlado; no se evaluó variabilidad de red o dispositivos de gama baja.
