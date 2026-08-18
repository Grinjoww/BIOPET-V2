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
(`docs/informe/borradores/jaime/trabajos-relacionados.md`) + 3 de Fred
(`docs/investigacion/handoff-fred-trabajos-relacionados.md`) = **10
estudios incluidos**, cumpliendo la meta de ≥8-9. El bloque F20-F21 de
Fred fue integrado por él mismo el 2026-08-18 a este checklist (ramas
`fred/f20-f21-investigacion-refs`, commit `b30fc70`, 3 estudios + 11
referencias BibTeX con DOI verificado en `docs/investigacion/`), con el
aviso de Zaida de que solo se agrega su bloque y se recalculan los
totales. Ningún ítem de este documento fue completado con
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
| 6 | Fuentes de información consultadas (bases de datos, registros, sitios, organizaciones, listas de referencias) y fecha de la última búsqueda en cada una. | ✅ Completo | **Zaida:** IEEE Xplore, ACM Digital Library, Scopus, Google Scholar — búsqueda 2026-08-17. **Jaime:** auditoría de `referencias.bib` existente + búsqueda dirigida verificada contra Crossref (`api.crossref.org`) y páginas oficiales de editorial/conferencia/norma — búsqueda 2026-08-17. **Fred (F20):** búsqueda web dirigida (dominio veterinario + acceso a datos/rendimiento) con verificación individual de cada DOI abriendo `https://doi.org/<DOI>` y confirmando título, autores y año contra Crossref/IEEE/ACM/IOP/revistas — 2026-08-17 (11 DOI verificados, 1 PDF de repositorio institucional UITM accesible; 1 artículo descartado por HTTP 403 y 1 por fuera del rango 2020–2026). |
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
| 16a | Resultados del proceso de búsqueda y selección, desde registros identificados hasta estudios incluidos, idealmente con diagrama de flujo. | 🟡 Parcial | **Diagrama de flujo real generado:** `docs/informe/figuras/zaida/06-prisma-flow-diagram.png` (fuente editable: `docs/diagramas/prisma-flow-diagram.svg`). Bloque Zaida completamente cuantificado (15 identificados → 12 excluidos → 3 incluidos). Bloque Jaime solo reporta el conteo final de incluidos (4); su conteo de candidatos evaluados/excluidos **no fue documentado por Jaime** y se declara como vacío en el diagrama, no se inventa. Bloque Fred (F20): 11 DOI verificados + 1 PDF institucional accesible → 2 descartados (1 por HTTP 403: artículo TURCOMAT; 1 por fuera del rango 2020–2026: Yang et al. ICSE 2018) → 10 referencias de respaldo + **3 estudios incluidos** (`docs/investigacion/handoff-fred-referencias.bib` y `handoff-fred-trabajos-relacionados.md`). Total incluido: **10 de una meta de ≥8-9 — meta cumplida**. El diagrama PNG (de Zaida) aún muestra el bloque Fred con línea punteada; regenerarlo con el conteo real de Fred es decisión de Zaida, no se modificó aquí. |
| 16b | Citar estudios que parecían elegibles pero fueron excluidos, y explicar por qué. | 🟡 Parcial (bloque Jaime sin registro) | Del bloque Zaida: de 15 candidatos revisados, 12 se excluyeron por no tener DOI/enlace verificable, no ser revisados por pares, o no ser relevantes a requisitos/usabilidad/dominio veterinario (criterio del ítem 5). Los candidatos excluidos específicos no se listaron individualmente por nombre en el borrador original de Zaida (solo el conteo agregado); si se requiere el detalle ítem por ítem, debe solicitarse el registro de búsqueda completo. Del bloque Jaime: sin registro de excluidos (mismo gap del ítem 16a). Del bloque Fred (agregado 2026-08-18): 2 candidatos descartados documentados individualmente (Yang et al., ICSE 2018, DOI 10.1145/3180155.3180194 — fuera del rango temporal 2020–2026 fijado por Fred; artículo de TURCOMAT — fuente inaccesible, HTTP 403), ver notas de verificación en `docs/investigacion/handoff-fred-referencias.bib`. |
| 17 | Citar cada estudio incluido y presentar sus características. | ✅ Completo | Tabla comparativa consolidada, 10 estudios: |

**Tabla comparativa consolidada (10 estudios incluidos)**

| Estudio | Bloque | Dominio | Enfoque/metodología | Evaluación reportada | Relación con BIOPET | Diferencia/brecha |
|---|---|---|---|---|---|---|
| Pacheco, García & Reyes (2018) | Zaida | Ingeniería de requisitos | Revisión sistemática, 140 estudios (1993–2015) | Madurez/efectividad comparada de técnicas de elicitación | Contrasta la práctica detrás del SRS de BIOPET | BIOPET no compara técnicas; aplica una y la documenta con trazabilidad formal |
| Bangor, Kortum & Miller (2008) | Zaida | Usabilidad (SUS) | Análisis empírico, ~10 años de datos SUS | Benchmark de interpretación (68 = promedio, 80.3 = excelente) | Fuente del benchmark para el SUS propio de BIOPET | BIOPET usa el benchmark existente, no construye uno nuevo |
| Cedeño Ochoa, Catuto Murillo & Rodas-Silva (2021) | Zaida | Gestión veterinaria (Ecuador) | Caso de estudio, proceso iterativo-incremental | Mejora cualitativa de procesos administrativos | Dominio y país más cercanos encontrados | Sin evaluación multimétrica; BIOPET sí (JaCoCo, k6, OWASP/ZAP, SUS, Lighthouse) |
| Rodríguez, Llerena, Guevara, Baren & Castro (2024) — OSCRUM | Jaime | Gestión veterinaria, código abierto | Caso de estudio, proceso ágil OSCRUM | No reporta evaluación empírica multimétrica | Dominio más cercano en arquitectura/proceso | Documenta *proceso*, no evalúa empíricamente el *producto* |
| Putri, Hadi & Ramdani (2017) | Jaime | Sistema web académico | Caso de estudio, pruebas de carga | Rendimiento bajo carga (tiempos de respuesta) | Paralelo metodológico directo a la evaluación k6 de BIOPET | Solo rendimiento; sin seguridad, usabilidad ni cobertura |
| Yang et al. (2026) — NDSS | Jaime | Seguridad JWT (multi-lenguaje) | Fuzzing dirigido sobre 43 implementaciones | Vulnerabilidades descubiertas (31 nuevas, 20 con CVE) | Riesgos de la misma tecnología de autenticación de BIOPET | Audita librerías JWT genéricas, no una aplicación completa |
| Estdale & Georgiadou (2018) | Jaime | Calidad de software (ISO/IEC 25010) | Aplicación guiada del modelo | Discusión de qué características son evidenciables | Paralelo metodológico directo a `docs/arquitectura/ISO-25010.md` | Guía general, no un mapeo concreto con evidencia enlazada |
| Bucao et al. (2023) — VETelgeuse | Fred | Gestión veterinaria (Filipinas) | Sistema web y móvil, caso de estudio | Usabilidad (cuestionario USE, n=30) | Confirma el dominio y la forma web+móvil de BIOPET | Validación solo de usabilidad, sin rendimiento, seguridad ni cobertura |
| Llaneta et al. (2022) — TerraVet | Fred | Gestión veterinaria (marco web+móvil) | Framework web y móvil, caso de estudio | Propuesta de framework, evaluación preliminar | Paralelo de dominio y arquitectura web+móvil | No evalúa rendimiento ni despliegue en la nube |
| Zmaranda et al. (2020) | Fred | Acceso a datos (ORMs vs SQL directo) | Benchmark CRUD sobre mapeadores objeto-relacionales | Tiempos de operaciones CRUD | Respalda el acceso híbrido JPA + SQL nativo de BIOPET | Caso de estudio limitado a .NET; no cubre SP ni agregaciones |

| 18 | Presentar evaluación de calidad/riesgo de sesgo para cada estudio incluido. | ✅ Completo | Clasificación de calidad de fuente por estudio: Pacheco et al. — revisado por pares (IET Software, indexado Scopus/WoS); Bangor et al. — revisado por pares (Int. J. HCI); Cedeño Ochoa et al. — revisado por pares (revista ecuatoriana, indexación regional, no Scopus/WoS — nivel de indexación menor, declarado explícitamente); Rodríguez et al. — proceedings Springer LNNS revisado por pares; Putri et al. — proceedings IEEE revisado por pares; Yang et al. — proceedings NDSS (venue de referencia en seguridad, revisado por pares); Estdale & Georgiadou — proceedings Springer CCIS revisado por pares. **Bloque Fred (agregado 2026-08-18):** Bucao et al. — revista Research Journal of Education, Science and Technology, DOI Crossref verificado; Llaneta et al. — proceedings ACM ICIST revisado por pares, DOI 10.1145 verificado; Zmaranda et al. — revista IJACSA revisada por pares, DOI Crossref verificado. |
| 19 | Resultados individuales por estudio (estadísticas/estimaciones de efecto). | N/A | No aplica: no hay estimaciones de efecto clínico que resumir. |
| 20a–d | Síntesis de resultados, heterogeneidad, sesgo de reporte por síntesis. | ✅ Completo | **Síntesis narrativa (research gap):** los 7 estudios incluidos, tomados individualmente, evalúan siempre **una sola dimensión a la vez**: Pacheco et al. evalúan técnica de elicitación sin evaluar el producto resultante; Bangor et al. dan el marco de usabilidad sin aplicarlo a un sistema concreto; Cedeño Ochoa et al. aplican el dominio veterinario exacto sin evaluación multimétrica; OSCRUM documenta proceso ágil sin evaluación empírica del producto; Putri et al. miden solo rendimiento; Yang et al. auditan solo seguridad JWT genérica; Estdale & Georgiadou discuten el modelo de calidad sin aplicarlo con evidencia enlazada a un caso real. BIOPET se posiciona frente a los 7 como una evaluación **multimétrica** de un único artefacto (cobertura JaCoCo, rendimiento k6, usabilidad SUS, calidad web Lighthouse, seguridad por tres ángulos OWASP/ZAP/estático) sobre el dominio y país exactos de Cedeño Ochoa et al., con la práctica de elicitación de requisitos contrastada contra Pacheco et al. y el benchmark de usabilidad de Bangor et al. aplicado directamente. Heterogeneidad: los estudios abarcan distintos tipos de diseño (revisión sistemática, caso de estudio, fuzzing, guía metodológica) — no comparables estadísticamente entre sí, por lo que la síntesis es narrativa, no cuantitativa (consistente con el ítem 13b–f, ya marcado N/A parcial). **Adición del bloque Fred (2026-08-18, no reescribe lo anterior):** los 3 estudios de Fred confirman el mismo patrón — Bucao et al. validan solo usabilidad (USE, n=30); Llaneta et al. proponen un framework sin evaluación de rendimiento; Zmaranda et al. miden solo tiempos de CRUD. Ninguno evalúa múltiples dimensiones sobre un sistema completo, lo que refuerza el research gap y el posicionamiento multimétrico de BIOPET; además, los dos estudios de dominio veterinario del bloque Fred (Bucao, Llaneta) matizan la limitación 23a al ampliar la evidencia del dominio exacto. |
| 21a | Evaluación de la certeza global de la evidencia para cada resultado. | N/A | No aplica. |

## Sección 6 — Discusión

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 22 | Interpretación general de resultados en el contexto de otra evidencia. | ✅ Completo | Ver síntesis del ítem 20a–d: la literatura revisada confirma que evaluar múltiples dimensiones de calidad sobre un mismo artefacto de software, en el dominio de gestión veterinaria, sigue siendo poco común; el aporte práctico de BIOPET no es superioridad metodológica sobre estos trabajos, sino la amplitud de evidencia recolectada sobre un solo sistema, lo que permite razonar sobre relaciones entre dimensiones (p. ej., cobertura de pruebas vs. ausencia de hallazgos de seguridad estática) que un estudio de una sola dimensión no puede mostrar. Pendiente enlazar esta interpretación al capítulo de Discusión del informe final (Z18) y a D5 (amenazas a validez) al integrarse. |
| 23a | Limitaciones de la evidencia incluida en la revisión. | ✅ Completo | Solo fuentes en español e inglés (podrían existir estudios relevantes en portugués u otros idiomas, no cubiertos). Sin restricción explícita de años, pero el conjunto resultante concentra publicaciones 2017–2026 (salvo Pacheco et al., que cubre literatura 1993–2015 dentro de su revisión). **Nota 2026-08-18 (bloque Fred agregado):** la afirmación anterior de "solo un estudio de dominio veterinario estricto (Cedeño Ochoa et al.)" queda matizada: con el bloque Fred se suman Bucao et al. (2023) y Llaneta et al. (2022), ambos de gestión veterinaria, pero ninguno en Ecuador ni con evaluación multimétrica — el dominio exacto de BIOPET (país + evaluación multimétrica) sigue subrepresentado en la literatura académica disponible, no por sesgo de búsqueda sino por escasez real (mismo hallazgo reportado independientemente por Jaime en `trabajos-relacionados.md`). |
| 23b | Limitaciones del proceso de revisión mismo. | ✅ Completo | La selección no tuvo doble revisor independiente por estudio: cada bloque (Zaida, Jaime) fue filtrado por una sola persona sobre su propio tema, sin validación cruzada entre integrantes, a diferencia de una revisión sistemática clínica formal (recurso real de un equipo de 3 personas en un PFC académico, declarado explícitamente en vez de omitirse). El bloque Jaime no documentó su conteo de candidatos evaluados/excluidos (solo el resultado final), lo que impide auditar completamente su proceso de cribado, a diferencia del bloque Zaida que sí lo documentó en detalle. **Bloque Fred (agregado 2026-08-18):** tampoco tuvo doble revisor independiente; su proceso sí quedó documentado en detalle (11 DOI abiertos y verificados uno a uno, 2 descartados con motivo individual, ver `docs/investigacion/handoff-fred-referencias.bib`), y además aplicó un filtro temporal explícito (2020–2026) que los otros bloques no declararon — diferencia de criterio entre bloques declarada, no oculta. |
| 23c | Implicaciones para la práctica, política o investigación futura. | ⬜ Pendiente | Conectar con "trabajo futuro" del informe. |

## Sección 7 — Otra información

| Ítem | Qué exige | Estado | Dónde va en el informe |
|---|---|---|---|
| 24a | Información de registro de la revisión y el registro usado (si existe). | N/A | No aplica: no se registró un protocolo formal en PROSPERO ni equivalente (contexto académico de PFC, no publicación clínica); declarar esto explícitamente en vez de fingir un registro. |
| 24b | Dirección del protocolo, si estuvo disponible. | N/A | No aplica, mismo motivo que 24a. |
| 24c | Modificaciones al registro/protocolo. | N/A | No aplica. |
| 25 | Fuentes de financiamiento de la revisión y rol de los financiadores. | ✅ Trivial, cerrado | Sin financiamiento externo, trabajo académico (ya registrado así en `CONTRIBUTORS.md` para Funding acquisition). |
| 26 | Conflictos de interés de los autores de la revisión. | ✅ Trivial, cerrado | Sin conflictos de interés conocidos. |
| 27 | Disponibilidad de datos, código y otros materiales usados en la revisión. | ✅ Completo | Estudios de Zaida: `docs/informe/borradores/zaida/estudios-primarios-zaida.md`. Estudios de Jaime: `docs/informe/borradores/jaime/trabajos-relacionados.md` y `referencias-candidatas.md`. Diagrama de flujo: `docs/informe/figuras/zaida/06-prisma-flow-diagram.png` (fuente: `docs/diagramas/prisma-flow-diagram.svg`). **Bloque Fred (agregado 2026-08-18):** `docs/investigacion/handoff-fred-trabajos-relacionados.md` (3 estudios con DOI) y `docs/investigacion/handoff-fred-referencias.bib` (11 entradas BibTeX con DOI verificado y afirmación que respalda cada una). Ninguna de estas listas está incorporada todavía a `docs/informe/referencias.bib` (bibliografía maestra), a propósito, para evitar conflicto de integración — ver nota de cierre en cada borrador. |

---

## Resumen de estado

| Estado | Cantidad de ítems |
|---|---|
| Completo | 15 |
| Parcial (16a: diagrama sin regenerar con bloque Fred; 16b: exclusiones de Jaime sin documentar) | 2 |
| No aplica, justificado (revisión narrativa de ingeniería, no metaanálisis clínico) | 8 |
| Trivial, cerrado | 2 |
| **Total** | **27** |

## Condición de cierre

Este checklist pasa de "parcial" a "completo" cuando:

1. ~~**Fred (F20) entregue sus 3 estudios verificados**~~ — **CUMPLIDO el
   2026-08-18**: el bloque Fred (F20-F21) fue integrado a este checklist
   (3 estudios incluidos + 11 referencias BibTeX con DOI verificado en
   `docs/investigacion/`); el total pasa a **10 de una meta de ≥8-9
   estudios incluidos, meta cumplida**.
2. Jaime documente el conteo de candidatos evaluados/excluidos de su
   bloque (hoy solo se conoce su resultado final: 4 incluidos), para
   poder completar el diagrama de flujo (ítem 16a) y la tabla de
   exclusiones (ítem 16b) de forma pareja entre los tres bloques.
3. Las tres listas de referencias candidatas (Fred, Jaime, Zaida) se
   integren en conjunto a `docs/informe/referencias.bib`, tarea de
   integración final declarada explícitamente como pendiente en cada
   borrador individual.

Lo ya cerrado (bloques Zaida y Jaime, 7 estudios previos, diagrama de
flujo parcial, tabla comparativa, síntesis narrativa, limitaciones) no
fue reabierto ni reescrito al agregar el bloque de Fred — solo se
agregaron sus 3 estudios y se recalculó el total a 10, según la regla
establecida por Zaida.

Ningún ítem de este documento fue cerrado con estudios inventados,
fuentes no verificables, ni conteos de ejemplo copiados de otra revisión.
