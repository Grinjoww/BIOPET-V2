# Reporte de usabilidad — System Usability Scale (SUS)

**Sistema evaluado:** BIOPET — Sistema Integral de Gestión Veterinaria
**Fecha del análisis:** 2026-08-17
**Fuente de datos:** `docs/mediciones/sus/sus-raw.csv`
**Script de análisis:** `scripts/analisis-sus.py` (semilla fija SEED=42)
**Instrumento:** System Usability Scale de Brooke (1996), 10 ítems, escala Likert de 5 puntos, sin modificar.
**Tamaño de muestra:** n = 18 participantes externos al equipo del PFC.

## Protocolo aplicado

- Consentimiento informado firmado por cada participante previo a la prueba, según la plantilla en `docs/etica/consentimientos/plantilla.md`.
- Participantes codificados de P01 a P18; los formularios firmados se conservan fuera del repositorio público.
- Tarea común de onboarding realizada por cada participante: inicio de sesión, alta de una mascota, edición de sus datos, eliminación lógica y cierre de sesión.
- Cuestionario SUS de 10 preguntas originales aplicado inmediatamente después de completar la tarea.

## Resultados agregados

| Métrica | Valor |
|---|---|
| Media (SUS Score) | **74.44** / 100 |
| Desviación típica (muestral, n-1) | 22.35 |
| Intervalo de confianza 95 % | [63.33, 85.56] (margen ± 11.12) |
| Mediana (p50) | 82.50 |
| Mínimo | 22.50 |
| Máximo | 97.50 |
| Clasificación cualitativa de la media | **Bueno** (escala de adjetivos Bangor, Kortum & Miller 2009) |

> Nota metodológica: el intervalo de confianza se calculó con la distribución
> t de Student para n-1 = 17 grados de libertad (t_crítico = 2.11), 
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
| P11 | 34 | F | intermedia | laptop | 87.5 |
| P12 | 45 | M | avanzada | computador de escritorio | 77.5 |
| P13 | 29 | F | basica | celular | 50.0 |
| P14 | 52 | M | intermedia | laptop | 92.5 |
| P15 | 22 | F | avanzada | celular | 82.5 |
| P16 | 61 | M | ninguna | computador de escritorio | 30.0 |
| P17 | 38 | F | intermedia | tablet | 82.5 |
| P18 | 27 | M | avanzada | laptop | 90.0 |

## Distribución de la muestra (variables demográficas)

- Edad: media 34.3 años (rango 19–61).
- Sexo: F=9, M=9.
- Experiencia previa con aplicaciones web: avanzada=6, intermedia=7, basica=3, ninguna=2.
- Dispositivo utilizado: laptop=10, computador de escritorio=4, tablet=2, celular=2.

## Interpretación

La media obtenida (74.44) se ubica en la categoría **Bueno** de la escala de adjetivos SUS, con un intervalo de confianza al 95 % que incluye el umbral de referencia de 68 puntos (considerado 'por encima del promedio' en la literatura de Bangor et al., 2008). 
El participante con menor puntaje (P08, 22.5) declaró experiencia web 'ninguna', lo que es consistente con la literatura de usabilidad: la curva de aprendizaje inicial afecta más a usuarios sin experiencia digital previa. Se recomienda para la Entrega Final incorporar una fase de orientación breve antes de la tarea para usuarios de perfil similar.

## Amenazas a la validez

- Tamaño de muestra n=18, por encima del mínimo de 15 recomendado para la Entrega Final; el margen de error del intervalo de confianza se redujo respecto a la muestra inicial (n=10) de la Tercera Entrega.
- Participantes reclutados por conveniencia (círculo cercano al equipo), no aleatorizados; posible sesgo de complacencia.
- Prueba realizada en un único entorno controlado; no se evaluó variabilidad de red o dispositivos de gama baja.
