# Checklist PRISMA 2020 — Revisión de Trabajos Relacionados (D2)

**Aplicado a:** sección de Trabajos Relacionados del informe final
(`docs/informe-final.tex`, criterio D2 de la Guía de la Entrega Final).
**Norma:** Page MJ, McKenzie JE, Bossuyt PM, et al. *The PRISMA 2020
statement: an updated guideline for reporting systematic reviews*. BMJ 2021;
372:n71. Checklist de 27 ítems en 7 secciones (Título, Resumen,
Introducción, Métodos, Resultados, Discusión, Otra información).
**Responsable:** Zaida Melissa Taipe Mora (Z14, consolidación en Z15).
**Adaptación declarada:** PRISMA 2020 fue diseñado para revisiones
sistemáticas de intervenciones sanitarias. BIOPET lo usa, como pide el plan
operativo, como marco de **rigor y transparencia** para una revisión de
trabajos relacionados de ingeniería de software (no una revisión clínica ni
un metaanálisis), por lo que varios ítems orientados a estadística clínica
(19, 20b–d, 21b–c) se marcan **No aplica** de forma explícita en vez de
forzarlos.

**Estado de este documento:** es la **plantilla estructural** lista para
recibir contenido. No contiene todavía los estudios de la revisión porque
esta depende de tres insumos que aún no existen (F20 de Fred, J20 de Jaime,
búsqueda propia de Zaida) — ver criterio D2 en el plan operativo, estado
FALTA. Ningún ítem de este documento fue completado con datos inventados.

---

## Sección 1 — Título

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 1 | Identificar el reporte como una revisión de trabajos relacionados (equivalente a "systematic review" en el contexto académico de este PFC). | ⬜ Pendiente | Título de la sección/capítulo de Trabajos Relacionados. |

## Sección 2 — Resumen

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 2 | Resumen estructurado: contexto, objetivos, criterios de elegibilidad, fuentes, método de síntesis, resultados (número de estudios), limitaciones, conclusión. | ⬜ Pendiente | Resumen bilingüe del informe (Z18), párrafo dedicado a D2. |

## Sección 3 — Introducción

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 3 | Justificar por qué se necesita esta revisión, en el contexto de lo que ya se sabe (research gap). | ⬜ Pendiente | Depende de los 6–9 estudios que aporten Fred/Jaime/Zaida. |
| 4 | Declarar explícitamente la(s) pregunta(s) u objetivo(s) de la revisión (alineado a las RQs del informe). | ⬜ Pendiente | Debe enlazarse a las RQs ya definidas en la metodología (D3). |

## Sección 4 — Métodos

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 5 | Criterios de elegibilidad (inclusión/exclusión) y cómo se agruparon los estudios para la síntesis. | ⬜ Pendiente | Debe definir: años aceptados, tipo de fuente (primaria/revisada por pares), idioma, relevancia a arquitectura/acceso a datos/rendimiento/seguridad/RE/usabilidad (los 3 bloques de F20/J20/Zaida). |
| 6 | Fuentes de información consultadas (bases de datos, registros, sitios, organizaciones, listas de referencias) y fecha de la última búsqueda en cada una. | ⬜ Pendiente | Registrar aquí cada base (IEEE Xplore, ACM DL, Scopus, Google Scholar, etc.) con fecha real de búsqueda — no reutilizar fechas de otro proyecto. |
| 7 | Estrategia de búsqueda completa para cada fuente, incluyendo filtros y límites. | ⬜ Pendiente | Cadena de búsqueda literal (términos + operadores booleanos) por fuente. |
| 8 | Proceso de selección: cuántos revisores, si fue independiente, y herramientas de automatización usadas. | ⬜ Pendiente | BIOPET: Fred filtra su bloque, Jaime el suyo, Zaida el suyo + consolida — declarar esto explícitamente como el "proceso de selección" real. |
| 9 | Proceso de extracción de datos: qué se extrajo de cada estudio y quién lo hizo. | ⬜ Pendiente | Campos mínimos: año, dominio, pila tecnológica, arquitectura, tipo de evaluación, limitaciones, diferencia con BIOPET. |
| 10a | Lista de todas las variables/campos para los que se buscó información en cada estudio. | ⬜ Pendiente | Debe coincidir con la tabla comparativa final. |
| 10b | *(No aplica en su forma clínica)* — Métodos para obtener y confirmar datos de los autores originales de cada estudio. | N/A | No aplica: los estudios se citan por su publicación, no se contacta a los autores. |
| 11 | Métodos usados para evaluar el riesgo de sesgo/calidad de cada estudio incluido y cómo se usó esa evaluación. | ⬜ Pendiente | Adaptar a "calidad de la fuente": revisada por pares vs. no, año de publicación, venue reconocido. |
| 12 | Medidas de efecto usadas para presentar o sintetizar resultados. | N/A | No aplica: no es un metaanálisis cuantitativo de efectos clínicos. |
| 13a | Métodos para determinar qué estudios son elegibles para cada síntesis. | ⬜ Pendiente | Corresponde a los criterios de la tabla comparativa (D2). |
| 13b–f | Preparación de datos, tabulación, métodos de síntesis, exploración de heterogeneidad, análisis de sensibilidad. | N/A (parcial) | Se usa una síntesis narrativa + tabla comparativa, no metaanálisis estadístico; documentar esa decisión explícitamente en vez de omitir el ítem. |
| 14 | Métodos para evaluar sesgo de publicación / certeza acumulada de la evidencia. | N/A | No aplica en revisión narrativa de ingeniería de software. |
| 15 | Métodos usados para evaluar la certeza (confianza) en el cuerpo de evidencia de cada resultado. | N/A | No aplica en este contexto. |

## Sección 5 — Resultados

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 16a | Resultados del proceso de búsqueda y selección, desde registros identificados hasta estudios incluidos, idealmente con diagrama de flujo. | ⬜ Pendiente | **Diagrama de flujo PRISMA obligatorio** (identificados → duplicados eliminados → cribados → excluidos → texto completo evaluado → excluidos con motivo → incluidos). Meta del plan: ≥9 candidatos verificados, mínimo 8 incluidos. |
| 16b | Citar estudios que parecían elegibles pero fueron excluidos, y explicar por qué. | ⬜ Pendiente | Tabla de exclusiones con motivo (no solo el conteo). |
| 17 | Citar cada estudio incluido y presentar sus características. | ⬜ Pendiente | Tabla comparativa (año, dominio, pila, arquitectura, evaluación, limitaciones, diferencia con BIOPET) — exigida también por el plan operativo. |
| 18 | Presentar evaluación de calidad/riesgo de sesgo para cada estudio incluido. | ⬜ Pendiente | Columna de calidad de fuente en la tabla comparativa. |
| 19 | Resultados individuales por estudio (estadísticas/estimaciones de efecto). | N/A | No aplica: no hay estimaciones de efecto clínico que resumir. |
| 20a–d | Síntesis de resultados, heterogeneidad, sesgo de reporte por síntesis. | ⬜ Parcial | Se traduce en la síntesis narrativa del **research gap**: qué patrón se repite entre los estudios incluidos y qué no cubren, que es exactamente lo que BIOPET resuelve distinto. |
| 21a | Evaluación de la certeza global de la evidencia para cada resultado. | N/A | No aplica. |

## Sección 6 — Discusión

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 22 | Interpretación general de resultados en el contexto de otra evidencia. | ⬜ Pendiente | Capítulo de Discusión del informe final (Z18), enlazado a D5 (amenazas a validez). |
| 23a | Limitaciones de la evidencia incluida en la revisión. | ⬜ Pendiente | P. ej., si solo se cubrieron fuentes en inglés/español, o solo los últimos N años. |
| 23b | Limitaciones del proceso de revisión mismo. | ⬜ Pendiente | Declarar que la selección no tuvo doble revisor independiente por estudio (recurso real del equipo), a diferencia de una revisión clínica formal. |
| 23c | Implicaciones para la práctica, política o investigación futura. | ⬜ Pendiente | Conectar con "trabajo futuro" del informe. |

## Sección 7 — Otra información

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 24a | Información de registro de la revisión y el registro usado (si existe). | N/A | No aplica: no se registró un protocolo formal en PROSPERO ni equivalente (contexto académico de PFC, no publicación clínica); declarar esto explícitamente en vez de fingir un registro. |
| 24b | Dirección del protocolo, si estuvo disponible. | N/A | No aplica, mismo motivo que 24a. |
| 24c | Modificaciones al registro/protocolo. | N/A | No aplica. |
| 25 | Fuentes de financiamiento de la revisión y rol de los financiadores. | ⬜ Trivial | Declarar "sin financiamiento externo, trabajo académico" (ya está registrado así en CONTRIBUTORS.md para Funding acquisition). |
| 26 | Conflictos de interés de los autores de la revisión. | ⬜ Trivial | Declarar "sin conflictos de interés conocidos". |
| 27 | Disponibilidad de datos, código y otros materiales usados en la revisión. | ⬜ Pendiente | Enlazar a `docs/refs.bib` y a la tabla comparativa una vez publicadas. |

---

## Resumen de estado

| Estado | Cantidad de ítems |
|---|---|
| Pendiente de contenido (requiere los 3 bloques de trabajos relacionados) | 17 |
| No aplica, justificado (revisión narrativa de ingeniería, no metaanálisis clínico) | 8 |
| Trivial (declaración corta, sin dependencia de literatura) | 2 |
| **Total** | **27** |

## Condición de cierre

Este checklist pasa de "plantilla" a "completo" cuando:

1. Fred (F20) y Jaime (J20) entreguen sus 3 estudios verificados cada uno.
2. Zaida verifique 3 estudios propios de requisitos/usabilidad/sistemas
   veterinarios.
3. Se documenten los estudios evaluados y descartados con motivo (ítem 16b),
   no solo los incluidos.
4. Se genere el diagrama de flujo PRISMA real (ítem 16a) con los conteos
   reales de esta búsqueda — nunca con números de ejemplo.

Ningún ítem de este documento debe cerrarse con estudios inventados,
fuentes no verificables, ni conteos de ejemplo copiados de otra revisión.
