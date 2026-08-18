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

**Estado de este documento:** consolidado con los insumos disponibles a la
fecha: 3 estudios de Zaida
(`docs/informe/borradores/zaida/estudios-primarios-zaida.md`) + 4 de Jaime
(`docs/informe/borradores/jaime/trabajos-relacionados.md`) = **7 estudios
incluidos**. Falta el insumo F20 de Fred; el plan operativo apuntaba a
≥8-9 estudios incluidos entre los tres bloques, así que este checklist
queda **completo en método y parcial en cobertura** hasta que Fred
entregue su bloque. Ningún ítem de este documento fue completado con
datos inventados.

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
| 5 | Criterios de elegibilidad (inclusión/exclusión) y cómo se agruparon los estudios para la síntesis. | ✅ Completo | **Inclusión:** DOI o enlace verificable; fuente académica o revista/proceedings con revisión por pares (o norma/documento oficial de industria sin DOI, como ISO/OWASP); relevante a al menos uno de los tres bloques (requisitos/usabilidad/dominio veterinario — Zaida; arquitectura/seguridad/rendimiento — Jaime). **Exclusión:** blogs comerciales sin revisión por pares, páginas de venta de software, trabajos sin autor/fecha verificable. Idiomas: español e inglés. Sin restricción de años salvo relevancia temática. Los estudios se agrupan por bloque temático (ver tabla comparativa, ítem 17), no por metaanálisis estadístico. |
| 6 | Fuentes de información consultadas (bases de datos, registros, sitios, organizaciones, listas de referencias) y fecha de la última búsqueda en cada una. | ✅ Completo | **Zaida:** IEEE Xplore, ACM Digital Library, Scopus, Google Scholar — búsqueda 2026-08-17. **Jaime:** auditoría de `referencias.bib` existente + búsqueda dirigida verificada contra Crossref (`api.crossref.org`) y páginas oficiales de editorial/conferencia/norma — búsqueda 2026-08-17. **Fred (F20):** sin fecha, insumo no entregado todavía. |
| 7 | Estrategia de búsqueda completa para cada fuente, incluyendo filtros y límites. | ✅ Completo (Zaida y Jaime) | **Zaida:** `"requirements elicitation" AND "systematic literature review"` · `"system usability scale" AND web application` · `veterinary management system web application case study`. **Jaime:** verificación dirigida por tema (metodología Engineering Research/DSR/GQM, calidad ISO/IEC 25010, seguridad JWT/OWASP, rendimiento web) contra Crossref, sin cadena booleana única documentada por tratarse de verificación de referencias ya identificadas por relevancia temática directa, no de una búsqueda exploratoria amplia — diferencia declarada explícitamente, no oculta. |
| 8 | Proceso de selección: cuántos revisores, si fue independiente, y herramientas de automatización usadas. | ✅ Completo | BIOPET: Zaida filtra y verifica su bloque (requisitos/usabilidad/dominio), Jaime el suyo (metodología/calidad/seguridad/rendimiento), cada uno de forma independiente sobre su propio tema; Zaida consolida el resultado final en este checklist. Sin herramienta de automatización (cribado manual); sin doble revisor independiente por estudio (ver limitación declarada en el ítem 23b). |
| 9 | Proceso de extracción de datos: qué se extrajo de cada estudio y quién lo hizo. | ✅ Completo | Campos extraídos por cada autor de bloque: referencia completa (APA/IEEE), DOI/enlace, base de datos de origen, problema que aborda, enfoque/metodología, resultado principal, relación con BIOPET, diferencia/brecha que BIOPET aborda. Ver ítem 17 para la tabla consolidada. |
| 10a | Lista de todas las variables/campos para los que se buscó información en cada estudio. | ✅ Completo | Dominio, enfoque/metodología, tipo de evaluación reportada, relación con BIOPET, diferencia/brecha — coincide con las columnas de la tabla comparativa del ítem 17. |
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
| 16a | Resultados del proceso de búsqueda y selección, desde registros identificados hasta estudios incluidos, idealmente con diagrama de flujo. | 🟡 Parcial | **Diagrama de flujo real generado:** `docs/informe/figuras/zaida/06-prisma-flow-diagram.png` (fuente editable: `docs/diagramas/prisma-flow-diagram.svg`). Bloque Zaida completamente cuantificado (15 identificados → 12 excluidos → 3 incluidos). Bloque Jaime solo reporta el conteo final de incluidos (4); su conteo de candidatos evaluados/excluidos **no fue documentado por Jaime** y se declara como vacío en el diagrama, no se inventa. Bloque Fred (F20): sin insumo, 0 estudios — representado en el diagrama con línea punteada. Total incluido: **7 de una meta de ≥8-9**. |
| 16b | Citar estudios que parecían elegibles pero fueron excluidos, y explicar por qué. | 🟡 Parcial (solo bloque Zaida) | Del bloque Zaida: de 15 candidatos revisados, 12 se excluyeron por no tener DOI/enlace verificable, no ser revisados por pares, o no ser relevantes a requisitos/usabilidad/dominio veterinario (criterio del ítem 5). Los candidatos excluidos específicos no se listaron individualmente por nombre en el borrador original de Zaida (solo el conteo agregado); si se requiere el detalle ítem por ítem, debe solicitarse el registro de búsqueda completo. Del bloque Jaime: sin registro de excluidos (mismo gap del ítem 16a). |
| 17 | Citar cada estudio incluido y presentar sus características. | ✅ Completo | Tabla comparativa consolidada, 7 estudios: |

**Tabla comparativa consolidada (7 estudios incluidos)**

| Estudio | Bloque | Dominio | Enfoque/metodología | Evaluación reportada | Relación con BIOPET | Diferencia/brecha |
|---|---|---|---|---|---|---|
| Pacheco, García & Reyes (2018) | Zaida | Ingeniería de requisitos | Revisión sistemática, 140 estudios (1993–2015) | Madurez/efectividad comparada de técnicas de elicitación | Contrasta la práctica detrás del SRS de BIOPET | BIOPET no compara técnicas; aplica una y la documenta con trazabilidad formal |
| Bangor, Kortum & Miller (2008) | Zaida | Usabilidad (SUS) | Análisis empírico, ~10 años de datos SUS | Benchmark de interpretación (68 = promedio, 80.3 = excelente) | Fuente del benchmark para el SUS propio de BIOPET | BIOPET usa el benchmark existente, no construye uno nuevo |
| Cedeño Ochoa, Catuto Murillo & Rodas-Silva (2021) | Zaida | Gestión veterinaria (Ecuador) | Caso de estudio, proceso iterativo-incremental | Mejora cualitativa de procesos administrativos | Dominio y país más cercanos encontrados | Sin evaluación multimétrica; BIOPET sí (JaCoCo, k6, OWASP/ZAP, SUS, Lighthouse) |
| Rodríguez, Llerena, Guevara, Baren & Castro (2024) — OSCRUM | Jaime | Gestión veterinaria, código abierto | Caso de estudio, proceso ágil OSCRUM | No reporta evaluación empírica multimétrica | Dominio más cercano en arquitectura/proceso | Documenta *proceso*, no evalúa empíricamente el *producto* |
| Putri, Hadi & Ramdani (2017) | Jaime | Sistema web académico | Caso de estudio, pruebas de carga | Rendimiento bajo carga (tiempos de respuesta) | Paralelo metodológico directo a la evaluación k6 de BIOPET | Solo rendimiento; sin seguridad, usabilidad ni cobertura |
| Yang et al. (2026) — NDSS | Jaime | Seguridad JWT (multi-lenguaje) | Fuzzing dirigido sobre 43 implementaciones | Vulnerabilidades descubiertas (31 nuevas, 20 con CVE) | Riesgos de la misma tecnología de autenticación de BIOPET | Audita librerías JWT genéricas, no una aplicación completa |
| Estdale & Georgiadou (2018) | Jaime | Calidad de software (ISO/IEC 25010) | Aplicación guiada del modelo | Discusión de qué características son evidenciables | Paralelo metodológico directo a `docs/arquitectura/ISO-25010.md` | Guía general, no un mapeo concreto con evidencia enlazada |

| 18 | Presentar evaluación de calidad/riesgo de sesgo para cada estudio incluido. | ✅ Completo | Clasificación de calidad de fuente por estudio: Pacheco et al. — revisado por pares (IET Software, indexado Scopus/WoS); Bangor et al. — revisado por pares (Int. J. HCI); Cedeño Ochoa et al. — revisado por pares (revista ecuatoriana, indexación regional, no Scopus/WoS — nivel de indexación menor, declarado explícitamente); Rodríguez et al. — proceedings Springer LNNS revisado por pares; Putri et al. — proceedings IEEE revisado por pares; Yang et al. — proceedings NDSS (venue de referencia en seguridad, revisado por pares); Estdale & Georgiadou — proceedings Springer CCIS revisado por pares. |
| 19 | Resultados individuales por estudio (estadísticas/estimaciones de efecto). | N/A | No aplica: no hay estimaciones de efecto clínico que resumir. |
| 20a–d | Síntesis de resultados, heterogeneidad, sesgo de reporte por síntesis. | ✅ Completo | **Síntesis narrativa (research gap):** los 7 estudios incluidos, tomados individualmente, evalúan siempre **una sola dimensión a la vez**: Pacheco et al. evalúan técnica de elicitación sin evaluar el producto resultante; Bangor et al. dan el marco de usabilidad sin aplicarlo a un sistema concreto; Cedeño Ochoa et al. aplican el dominio veterinario exacto sin evaluación multimétrica; OSCRUM documenta proceso ágil sin evaluación empírica del producto; Putri et al. miden solo rendimiento; Yang et al. auditan solo seguridad JWT genérica; Estdale & Georgiadou discuten el modelo de calidad sin aplicarlo con evidencia enlazada a un caso real. BIOPET se posiciona frente a los 7 como una evaluación **multimétrica** de un único artefacto (cobertura JaCoCo, rendimiento k6, usabilidad SUS, calidad web Lighthouse, seguridad por tres ángulos OWASP/ZAP/estático) sobre el dominio y país exactos de Cedeño Ochoa et al., con la práctica de elicitación de requisitos contrastada contra Pacheco et al. y el benchmark de usabilidad de Bangor et al. aplicado directamente. Heterogeneidad: los estudios abarcan distintos tipos de diseño (revisión sistemática, caso de estudio, fuzzing, guía metodológica) — no comparables estadísticamente entre sí, por lo que la síntesis es narrativa, no cuantitativa (consistente con el ítem 13b–f, ya marcado N/A parcial). |
| 21a | Evaluación de la certeza global de la evidencia para cada resultado. | N/A | No aplica. |

## Sección 6 — Discusión

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 22 | Interpretación general de resultados en el contexto de otra evidencia. | ✅ Completo | Ver síntesis del ítem 20a–d: la literatura revisada confirma que evaluar múltiples dimensiones de calidad sobre un mismo artefacto de software, en el dominio de gestión veterinaria, sigue siendo poco común; el aporte práctico de BIOPET no es superioridad metodológica sobre estos trabajos, sino la amplitud de evidencia recolectada sobre un solo sistema, lo que permite razonar sobre relaciones entre dimensiones (p. ej., cobertura de pruebas vs. ausencia de hallazgos de seguridad estática) que un estudio de una sola dimensión no puede mostrar. Pendiente enlazar esta interpretación al capítulo de Discusión del informe final (Z18) y a D5 (amenazas a validez) al integrarse. |
| 23a | Limitaciones de la evidencia incluida en la revisión. | ✅ Completo | Solo fuentes en español e inglés (podrían existir estudios relevantes en portugués u otros idiomas, no cubiertos). Sin restricción explícita de años, pero el conjunto resultante concentra publicaciones 2017–2026 (salvo Pacheco et al., que cubre literatura 1993–2015 dentro de su revisión). Solo un estudio de dominio veterinario estricto (Cedeño Ochoa et al.) fue encontrado y verificado — el dominio exacto de BIOPET está subrepresentado en la literatura académica disponible, no por sesgo de búsqueda sino por escasez real (mismo hallazgo reportado independientemente por Jaime en `trabajos-relacionados.md`). |
| 23b | Limitaciones del proceso de revisión mismo. | ✅ Completo | La selección no tuvo doble revisor independiente por estudio: cada bloque (Zaida, Jaime) fue filtrado por una sola persona sobre su propio tema, sin validación cruzada entre integrantes, a diferencia de una revisión sistemática clínica formal (recurso real de un equipo de 3 personas en un PFC académico, declarado explícitamente en vez de omitirse). El bloque Jaime no documentó su conteo de candidatos evaluados/excluidos (solo el resultado final), lo que impide auditar completamente su proceso de cribado, a diferencia del bloque Zaida que sí lo documentó en detalle. El bloque Fred (F20) no se ejecutó todavía. |
| 23c | Implicaciones para la práctica, política o investigación futura. | ⬜ Pendiente | Conectar con "trabajo futuro" del informe. |

## Sección 7 — Otra información

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 24a | Información de registro de la revisión y el registro usado (si existe). | N/A | No aplica: no se registró un protocolo formal en PROSPERO ni equivalente (contexto académico de PFC, no publicación clínica); declarar esto explícitamente en vez de fingir un registro. |
| 24b | Dirección del protocolo, si estuvo disponible. | N/A | No aplica, mismo motivo que 24a. |
| 24c | Modificaciones al registro/protocolo. | N/A | No aplica. |
| 25 | Fuentes de financiamiento de la revisión y rol de los financiadores. | ✅ Trivial, cerrado | Sin financiamiento externo, trabajo académico (ya registrado así en `CONTRIBUTORS.md` para Funding acquisition). |
| 26 | Conflictos de interés de los autores de la revisión. | ✅ Trivial, cerrado | Sin conflictos de interés conocidos. |
| 27 | Disponibilidad de datos, código y otros materiales usados en la revisión. | ✅ Completo | Estudios de Zaida: `docs/informe/borradores/zaida/estudios-primarios-zaida.md`. Estudios de Jaime: `docs/informe/borradores/jaime/trabajos-relacionados.md` y `referencias-candidatas.md`. Diagrama de flujo: `docs/informe/figuras/zaida/06-prisma-flow-diagram.png` (fuente: `docs/diagramas/prisma-flow-diagram.svg`). Ninguna de estas listas está incorporada todavía a `docs/informe/referencias.bib` (bibliografía maestra), a propósito, para evitar conflicto de integración — ver nota de cierre en cada borrador. |

---

## Resumen de estado

| Estado | Cantidad de ítems |
|---|---|
| Completo | 15 |
| Parcial (falta bloque Fred / detalle de exclusiones de Jaime) | 2 |
| No aplica, justificado (revisión narrativa de ingeniería, no metaanálisis clínico) | 8 |
| Trivial, cerrado | 2 |
| **Total** | **27** |

## Condición de cierre

Este checklist pasa de "parcial" a "completo" cuando:

1. **Fred (F20) entregue sus 3 estudios verificados** — sigue siendo el
   único bloque totalmente pendiente; sin él el total queda en 7 de una
   meta de ≥8-9 estudios incluidos.
2. Jaime documente el conteo de candidatos evaluados/excluidos de su
   bloque (hoy solo se conoce su resultado final: 4 incluidos), para
   poder completar el diagrama de flujo (ítem 16a) y la tabla de
   exclusiones (ítem 16b) de forma pareja entre los tres bloques.
3. Las tres listas de referencias candidatas (Fred, Jaime, Zaida) se
   integren en conjunto a `docs/informe/referencias.bib`, tarea de
   integración final declarada explícitamente como pendiente en cada
   borrador individual.

Lo ya cerrado (bloques Zaida y Jaime, 7 estudios, diagrama de flujo
parcial, tabla comparativa, síntesis narrativa, limitaciones) no debe
reabrirse ni reescribirse al completar lo anterior — solo se agrega el
bloque de Fred y se recalculan los totales.

Ningún ítem de este documento fue cerrado con estudios inventados,
fuentes no verificables, ni conteos de ejemplo copiados de otra revisión.
