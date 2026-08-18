# Borrador — Estudios primarios de Zaida Taipe (BIOPET, Entrega Final v1.0.0)

**Autora:** Zaida Melissa Taipe Mora.
**Estado:** Borrador de trabajo, listo para migración a LaTeX/consolidación
PRISMA. **No es** el documento maestro del informe
(`docs/informe/informe-entrega-3.pdf`/`.tex`) ni la bibliografía maestra
(`docs/informe/referencias.bib`), que no se modifican en esta fase para
evitar conflicto con la integración final.
**Fecha de esta revisión:** 2026-08-17.
**Bloque que cierra:** insumo "Zaida" de `docs/checklists/prisma2020.md`
(sección 4, ítem 8), junto con F20 de Fred y J20 de Jaime.

## Cómo se seleccionaron estos trabajos

Búsqueda dirigida a los tres dominios sin cubrir por los estudios de
arquitectura/seguridad/rendimiento ya aportados por Fred y Jaime:
**requisitos**, **usabilidad** y **sistemas de gestión veterinaria**, que
son exactamente los tres frentes donde BIOPET reporta evidencia propia
(`docs/requisitos/SRS.md`, `docs/mediciones/sus/`, dominio del producto)
sin todavía tener contraste con literatura externa.

## Estrategia de búsqueda (para trazabilidad PRISMA)

| Campo | Detalle |
|---|---|
| Bases consultadas | IEEE Xplore, ACM Digital Library, Scopus, Google Scholar |
| Fecha de búsqueda | 2026-08-17 |
| Cadenas de búsqueda | `"requirements elicitation" AND "systematic literature review"` · `"system usability scale" AND web application` · `veterinary management system web application case study` |
| Idiomas incluidos | Español, inglés |
| Criterio de inclusión | Estudio con DOI o enlace verificable; publicado en fuente académica o revista con revisión por pares; relevante para requisitos, usabilidad o sistemas veterinarios |
| Criterio de exclusión | Blogs comerciales sin revisión por pares, páginas de venta de software, trabajos sin autor/fecha verificable |
| Resultado | 3 estudios seleccionados de un conjunto inicial de ~15 candidatos revisados |

---

## 1. Pacheco, García & Reyes (2018) — Técnicas de elicitación de requisitos

**Referencia (APA 7):** Pacheco, C., García, I., & Reyes, M. (2018).
Requirements elicitation techniques: a systematic literature review based
on the maturity of the techniques. *IET Software, 12*(4), 365–378.
https://doi.org/10.1049/iet-sen.2017.0144

- **Base de datos / verificación:** IEEE Xplore / IET Digital Library;
  también indexado en Scopus y Web of Science (verificado además contra
  Semantic Scholar, 94 citas registradas).
- **Problema que aborda:** la falta de evidencia empírica sobre qué
  técnicas de elicitación de requisitos son efectivas, más allá de la
  preferencia habitual por entrevistas sin justificación comparativa.
- **Enfoque/metodología:** revisión sistemática de literatura sobre 140
  estudios (1993–2015), respondiendo qué técnicas son "maduras" (con
  evidencia empírica de efectividad) y cuáles de ellas mejoran la
  efectividad de la elicitación.
- **Resultado o contribución principal:** identifica las entrevistas
  (particularmente estructuradas) como la técnica con mayor evidencia
  empírica de madurez y efectividad, frente a alternativas como card
  sorting, ranking o prototipado.
- **Relación con BIOPET:** el SRS de BIOPET (`docs/requisitos/SRS.md`)
  documenta requisitos ya elicitados (historias de usuario, casos de uso,
  patrón ISO/IEC/IEEE 29148) pero no documenta explícitamente qué técnica
  de elicitación se usó para llegar a ellos.
- **Diferencia/brecha que BIOPET aborda:** Pacheco et al. evalúan la
  efectividad comparativa de técnicas de elicitación en abstracto; BIOPET
  no reporta una comparación de técnicas, sino la aplicación de una
  ya elegida (entrevistas/reuniones de equipo, según lo declarado en
  `CONTRIBUTORS.md`) documentada con trazabilidad formal
  (SRS → HU → CU → `matriz.csv`). La brecha identificada para trabajo
  futuro: BIOPET podría declarar explícitamente, en una futura entrega,
  qué técnica de elicitación se usó y por qué, apoyándose en esta
  evidencia de madurez.

---

## 2. Bangor, Kortum & Miller (2008) — Evaluación empírica del System Usability Scale

**Referencia (APA 7):** Bangor, A., Kortum, P. T., & Miller, J. T. (2008).
An Empirical Evaluation of the System Usability Scale (SUS). *International
Journal of Human–Computer Interaction, 24*(6), 574–594.
https://doi.org/10.1080/10447310802205776

- **Base de datos / verificación:** ACM Digital Library / Taylor & Francis
  (indexado en Scopus).
- **Problema que aborda:** la ausencia, hasta ese momento, de un
  benchmark empírico de gran escala para interpretar qué significa un
  puntaje SUS dado (¿es 70 "bueno" o "malo"?), más allá del uso aislado
  del cuestionario.
- **Enfoque/metodología:** análisis empírico de ~10 años de datos SUS
  recolectados sobre cientos de productos y estudios de usabilidad
  distintos.
- **Resultado o contribución principal:** establece que un puntaje SUS
  medio de la industria ronda **68/100**; por encima de 68 se considera
  sobre el promedio, y por encima de 80.3 se clasifica como "excelente".
  También valida la fiabilidad del instrumento con ese volumen de datos.
- **Relación con BIOPET:** BIOPET reporta evidencia SUS propia en
  `docs/mediciones/sus/` (aplicada al frontend Angular). Este estudio es
  la fuente del benchmark contra el que se interpreta ese puntaje: sin él,
  un número SUS aislado no tiene forma de calificarse como bueno, promedio
  o deficiente.
- **Diferencia/brecha que BIOPET aborda:** Bangor et al. construyen el
  benchmark general de la industria; BIOPET lo usa para interpretar una
  medición concreta y verificable sobre un solo sistema real (no reporta
  un benchmark nuevo, sino que se posiciona frente al ya existente).

---

## 3. Cedeño Ochoa, Catuto Murillo & Rodas-Silva (2021) — Aplicaciones web para clínicas veterinarias

**Referencia (APA 7):** Cedeño Ochoa, A., Catuto Murillo, A., &
Rodas-Silva, J. (2021). Use of Web applications for the management of
veterinary clinics and their impact on the improvement of administrative
processes. *Ecuadorian Science Journal, 5*(3).
https://journals.gdeon.org/index.php/esj/article/view/174

- **Base de datos / verificación:** Google Scholar; revista ecuatoriana
  con revisión por pares.
- **Problema que aborda:** la mayoría de clínicas veterinarias en Ecuador
  no cuenta con herramientas tecnológicas de gestión, lo que provoca
  pérdida y duplicidad de expedientes por manejo manual/en papel.
- **Enfoque/metodología:** caso de estudio sobre el desarrollo de una
  aplicación web para gestión de clínica veterinaria, con modelo de
  proceso iterativo e incremental.
- **Resultado o contribución principal:** documenta la mejora de procesos
  administrativos (registro, consulta y control de información) al migrar
  de un manejo manual a una aplicación web centralizada.
- **Relación con BIOPET:** es el trabajo más cercano en dominio y en
  contexto país encontrado y verificado — describe exactamente el
  problema que BIOPET dice resolver (clínicas veterinarias ecuatorianas
  sin gestión digital centralizada).
- **Diferencia/brecha que BIOPET aborda:** el estudio de Cedeño Ochoa et
  al. no reporta una evaluación empírica multimétrica del sistema
  resultante (no hay cobertura de pruebas, rendimiento bajo carga,
  seguridad auditada ni SUS); BIOPET sí reporta las seis dimensiones
  (JaCoCo, k6, OWASP/ZAP/estático, SUS, Lighthouse) sobre el mismo
  artefacto, para el mismo dominio y contexto país.

---

## 4. Síntesis comparativa

| Trabajo | Dominio | Enfoque | Evaluación | Relación con BIOPET | Diferencia principal |
|---|---|---|---|---|---|
| Pacheco, García & Reyes (2018) | Ingeniería de requisitos (técnicas de elicitación) | Revisión sistemática de literatura, 140 estudios (1993–2015) | Madurez/efectividad comparada de técnicas | Contrasta la práctica de elicitación detrás del SRS de BIOPET | BIOPET no compara técnicas; aplica una y la documenta con trazabilidad formal |
| Bangor, Kortum & Miller (2008) | Usabilidad (System Usability Scale) | Análisis empírico de ~10 años de datos SUS sobre cientos de productos | Benchmark de interpretación (68 = promedio, 80.3 = excelente) | Fuente del benchmark para interpretar el SUS propio de BIOPET (`docs/mediciones/sus/`) | BIOPET usa el benchmark existente; no construye uno nuevo |
| Cedeño Ochoa, Catuto Murillo & Rodas-Silva (2021) | Gestión veterinaria, contexto Ecuador | Caso de estudio, proceso iterativo e incremental | Mejora de procesos administrativos (cualitativa) | Dominio y país más cercanos encontrados y verificados | No reporta evaluación multimétrica; BIOPET sí (JaCoCo, k6, OWASP/ZAP, SUS, Lighthouse) |

### Síntesis (no una lista)

Los tres estudios cubren, cada uno, una sola dimensión que BIOPET también
reporta pero de forma aislada: Pacheco et al. evalúan *cómo se elicitan*
los requisitos sin evaluar el producto resultante; Bangor et al. dan el
marco de interpretación de usabilidad sin aplicarlo a un sistema
concreto; Cedeño Ochoa et al. aplican el dominio exacto de BIOPET
(gestión veterinaria, Ecuador) pero sin evaluación empírica
multidimensional del artefacto. El mismo patrón que identifica el
borrador de Jaime para arquitectura/seguridad/rendimiento se repite aquí
para requisitos/usabilidad/dominio: BIOPET es, frente a los tres, la
única evidencia que junta **elicitación documentada + benchmark de
usabilidad aplicado + dominio veterinario real**, sobre un mismo sistema
evaluado con múltiples métricas, no solo la superposición de tres
dimensiones inconexas.

---

## Referencias citadas en este documento (candidatas, no incorporadas todavía a `referencias.bib`)

| # | Referencia | DOI/enlace | Estado en `docs/informe/referencias.bib` |
|---|---|---|---|
| 1 | Pacheco, García & Reyes (2018) | https://doi.org/10.1049/iet-sen.2017.0144 | No incorporada — pendiente de integración conjunta con las de Jaime y Fred |
| 2 | Bangor, Kortum & Miller (2008) | https://doi.org/10.1080/10447310802205776 | No incorporada — mismo motivo |
| 3 | Cedeño Ochoa, Catuto Murillo & Rodas-Silva (2021) | https://journals.gdeon.org/index.php/esj/article/view/174 | No incorporada — mismo motivo |

**Nota:** `referencias.bib` es la bibliografía maestra del informe y no se
modifica en esta fase para evitar conflicto de integración con Fred y
Jaime (mismo criterio ya aplicado en
`docs/informe/borradores/jaime/trabajos-relacionados.md`). La
incorporación conjunta de las tres listas de referencias candidatas
(Fred/F20, Jaime/J20, Zaida) es tarea de integración final, no de este
borrador individual.
