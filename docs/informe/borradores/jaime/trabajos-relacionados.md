# Borrador — Trabajos relacionados (BIOPET, Entrega Final v1.0.0)

**Autor:** Jaime Mariscal.
**Estado:** Borrador de trabajo, listo para migración a LaTeX. **No es** el
documento maestro del informe (`docs/informe/informe-entrega-3.pdf`/`.tex`)
ni la bibliografía maestra (`docs/informe/referencias.bib`), que no se
modifican en esta fase para evitar conflicto con la integración de Zaida.
**Fecha de esta revisión:** 2026-08-17.

## Cómo se seleccionaron estos trabajos

Se auditó primero la bibliografía ya existente
(`docs/informe/referencias.bib`, 15 entradas: normas técnicas RFC,
documentación oficial de herramientas ya usadas en BIOPET, OWASP Top 10,
modelo C4, SUS de Brooke, WCAG, taxonomía CRediT). Ninguna de esas 15
entradas es un trabajo académico *relacionado* con el problema o la
metodología de BIOPET en el sentido de una sección de "Related Work": son
todas normas o documentación oficial de herramientas. Por eso se buscaron,
y se verificaron con fuentes primarias (Crossref, páginas oficiales de
editorial/conferencia), trabajos académicos realmente pertinentes al
dominio (sistemas web de gestión veterinaria) y a la metodología (pruebas
de rendimiento de aplicaciones web, evaluación empírica de seguridad JWT,
aplicación de ISO/IEC 25010) de BIOPET.

No se encontraron estudios veterinarios de alto nivel académico en
abundancia; el que sí se encontró y verificó (Rodríguez et al., 2024) es
el más cercano posible al dominio exacto de BIOPET. Los otros tres
trabajos seleccionados provienen de ingeniería de software y evaluación
de sistemas web comparables, justificando explícitamente su relación con
BIOPET en cada caso, conforme a lo permitido quando no existen estudios
específicamente veterinarios suficientes.

---

## 1. Rodríguez, Llerena, Guevara, Baren & Castro (2024) — Sistema web de código abierto para gestión veterinaria (OSCRUM)

**Referencia:** Rodríguez, N., Llerena, L., Guevara, P., Baren, R., &
Castro, J. W. (2024). *Open-Source Web System for Veterinary Management:
A Case Study of OSCRUM*. En *Information Systems and Technologies*,
Lecture Notes in Networks and Systems. Springer Nature Switzerland,
pp. 320–329. DOI: [10.1007/978-3-031-45642-8_32](https://doi.org/10.1007/978-3-031-45642-8_32).

- **Problema que aborda:** la falta de herramientas digitales accesibles
  para la administración de historiales médicos y datos de mascotas en
  clínicas veterinarias, con énfasis en que la solución sea de código
  abierto y reproducible por otros equipos.
- **Enfoque/metodología:** estudio de caso sobre el desarrollo de un
  sistema web (SysVet) usando la metodología ágil OSCRUM (una variante de
  Scrum orientada a proyectos de código abierto), documentando el proceso
  de desarrollo y las decisiones tomadas durante los sprints.
- **Resultado o contribución principal:** un sistema web funcional para
  gestión veterinaria, publicado como código abierto, junto con la
  documentación del proceso ágil seguido para construirlo.
- **Relación con BIOPET:** es el trabajo más cercano en dominio
  encontrado y verificado (gestión veterinaria vía sistema web), y
  comparte con BIOPET el interés en la reproducibilidad del artefacto
  (código abierto, proceso documentado).
- **Diferencia/brecha que BIOPET aborda:** el estudio de OSCRUM documenta
  el *proceso de desarrollo ágil* del artefacto, no presenta una
  evaluación empírica multimétrica del resultado (no reporta cobertura de
  pruebas, rendimiento bajo carga, seguridad auditada con herramientas
  automatizadas, ni usabilidad medida con un instrumento estandarizado
  como SUS). BIOPET, en cambio, aporta precisamente esa evaluación
  empírica del artefacto ya construido (JaCoCo, k6, SUS, Lighthouse,
  OWASP/ZAP/SpotBugs), complementaria al enfoque de proceso de OSCRUM. No
  se afirma que BIOPET sea "mejor" que SysVet/OSCRUM: no existe una
  comparación empírica directa entre ambos sistemas; la diferencia
  señalada es de **alcance de la evidencia reportada** en cada trabajo,
  no de calidad del artefacto.

---

## 2. Putri, Hadi & Ramdani (2017) — Pruebas de rendimiento de una aplicación web académica

**Referencia:** Putri, M. A., Hadi, H. N., & Ramdani, F. (2017).
*Performance testing analysis on web application: Study case student
admission web system*. 2017 International Conference on Sustainable
Information Engineering and Technology (SIET), IEEE. DOI:
[10.1109/SIET.2017.8304099](https://doi.org/10.1109/SIET.2017.8304099).

- **Problema que aborda:** la ausencia de evidencia empírica sobre el
  comportamiento de un sistema web académico (admisión de estudiantes)
  bajo condiciones de carga, más allá de la validación funcional.
- **Enfoque/metodología:** pruebas de carga con herramientas de
  *benchmarking* técnico contra el sistema real en ejecución, midiendo
  tiempo de respuesta y comportamiento bajo distintos volúmenes de
  usuarios concurrentes; estudio de caso sobre un único sistema real, sin
  grupo de control ni diseño experimental.
- **Resultado o contribución principal:** caracterización cuantitativa
  del rendimiento del sistema evaluado (tiempos de respuesta bajo carga),
  usada para identificar el punto en que el rendimiento se degrada.
- **Relación con BIOPET:** metodológicamente análogo a la evaluación de
  rendimiento de BIOPET con k6 (`docs/mediciones/perf/REPORT.md`): ambos
  son estudios de *benchmarking* de un artefacto web real, sobre un
  entorno de desarrollo/académico, sin diseño experimental controlado, con
  percentiles/tiempos de respuesta como métrica principal.
- **Diferencia/brecha que BIOPET aborda:** el estudio de Putri et al. mide
  únicamente rendimiento; no incorpora seguridad, usabilidad, cobertura de
  pruebas ni trazabilidad de requisitos como parte de la misma evaluación.
  BIOPET amplía ese mismo tipo de evidencia de *benchmarking* de
  rendimiento con evaluación empírica adicional en otras dimensiones de
  calidad (JaCoCo, SUS, Lighthouse, OWASP/ZAP/SpotBugs), dentro del mismo
  marco de *Engineering Research*. No se afirma superioridad de BIOPET
  sobre el sistema estudiado por Putri et al.: los dominios y alcances son
  distintos y no se realizó ninguna comparación empírica directa entre
  ambos.

---

## 3. Yang et al. (2026) — Evaluación empírica de vulnerabilidades en implementaciones de JWT

**Referencia:** Yang, J., Wang, E., Chen, J., Wang, Q., Zhang, Y., Duan,
H., Xie, W., & Wang, B. (2026). *Token Time Bomb: Evaluating JWT
Implementations for Vulnerability Discovery*. Proceedings of the Network
and Distributed System Security Symposium (NDSS 2026). DOI:
[10.14722/ndss.2026.240697](https://doi.org/10.14722/ndss.2026.240697).

- **Problema que aborda:** la ausencia de un estudio sistemático de
  vulnerabilidades en implementaciones reales de JSON Web Token (JWT) a
  través de múltiples lenguajes y librerías, pese a ser el mecanismo de
  autenticación stateless más usado en aplicaciones web modernas —
  exactamente el mecanismo que usa BIOPET (`JwtService`,
  `JwtAuthenticationFilter`, ver `docs/adr/ADR-003-jwt-redis.md`).
- **Enfoque/metodología:** herramienta de pruebas automatizada (JWTeemo)
  aplicada a 43 implementaciones reales de JWT en 10 lenguajes de
  programación distintos, con divulgación responsable de los hallazgos a
  los proveedores afectados.
- **Resultado o contribución principal:** 31 vulnerabilidades
  previamente desconocidas, 20 con CVE asignado, categorizadas en 5 tipos
  (incluye confusión de formato JWT, ataque de "billion hashes" y
  denegación de servicio por compresión); hallazgos incorporados como
  recomendación al borrador de mejores prácticas JWT del IETF.
- **Relación con BIOPET:** BIOPET usa JWT como mecanismo central de
  autenticación (cookies `HttpOnly`+`Secure`+`SameSite=Strict`, ver
  `docs/mediciones/sec/A07-authentication.md`); este trabajo es la
  referencia académica más actual y directamente aplicable disponible
  sobre los riesgos reales de esa misma tecnología, útil para contrastar
  categóricamente qué tipo de vulnerabilidades JWT existen en la
  literatura frente a lo auditado en BIOPET (OWASP A07, SpotBugs/Find
  Security Bugs, ver `docs/mediciones/sec/static-analysis/README.md`).
- **Diferencia/brecha que BIOPET aborda:** Yang et al. auditan
  *implementaciones/librerías* JWT genéricas en múltiples lenguajes; no
  evalúan un sistema de aplicación concreto de principio a fin. BIOPET no
  reclama haber encontrado nuevas vulnerabilidades de JWT como
  contribución científica (eso corresponde al trabajo de Yang et al.);
  en cambio, aporta evidencia de que **una aplicación concreta** que usa
  JWT fue auditada con múltiples técnicas (análisis dinámico ZAP,
  análisis estático SpotBugs/Find Security Bugs, revisión manual OWASP) y
  no presentó los patrones de vulnerabilidad de mayor severidad
  detectables por esas herramientas. No se afirma que BIOPET esté "libre"
  de las vulnerabilidades categorizadas por Yang et al.: esa categoría de
  análisis (fuzzing dirigido de la implementación JWT subyacente,
  `jjwt`) no forma parte del alcance de la auditoría de seguridad de
  BIOPET (ver limitación ya documentada en
  `docs/mediciones/sec/static-analysis/README.md`).

---

## 4. Estdale & Georgiadou (2018) — Aplicación de los modelos de calidad ISO/IEC 25010 a un producto de software

**Referencia:** Estdale, J., & Georgiadou, E. (2018). *Applying the
ISO/IEC 25010 Quality Models to Software Product*. En *Systems, Software
and Services Process Improvement* (EuroSPI 2018), Communications in
Computer and Information Science, vol. 896. Springer, pp. 492–503. DOI:
[10.1007/978-3-319-97925-0_42](https://doi.org/10.1007/978-3-319-97925-0_42).

- **Problema que aborda:** la dificultad práctica de aplicar el modelo de
  calidad ISO/IEC 25010 a un producto de software real, más allá de la
  cita teórica de la norma, incluyendo qué evidencia concreta puede
  respaldar cada característica de calidad.
- **Enfoque/metodología:** aplicación práctica guiada del modelo
  ISO/IEC 25010 a un caso de estudio de producto de software, discutiendo
  qué características resultan más o menos fáciles de evidenciar en la
  práctica.
- **Resultado o contribución principal:** una guía razonada de cómo
  mapear características/subcaracterísticas de ISO/IEC 25010 contra
  evidencia real de un producto, y qué limitaciones surgen al hacerlo
  (características sin evidencia práctica disponible, ambigüedad de
  ciertas subcaracterísticas).
- **Relación con BIOPET:** metodológicamente paralelo al trabajo ya
  realizado en `docs/arquitectura/ISO-25010.md`, donde se mapean las
  ocho características de ISO/IEC 25010:2011 contra evidencia real de
  BIOPET (JaCoCo, k6, SUS, Lighthouse, OWASP/ZAP/SpotBugs), declarando
  honestamente `No evaluada directamente` cuando no existe evidencia.
- **Diferencia/brecha que BIOPET aborda:** Estdale & Georgiadou discuten
  la aplicación del modelo de forma general/metodológica; BIOPET aporta
  un mapeo concreto y verificable, con rutas de evidencia reales citadas
  para cada subcaracterística, sobre un artefacto de software
  efectivamente construido y evaluado. No se afirma que el mapeo de
  BIOPET sea metodológicamente superior al de Estdale & Georgiadou: son
  complementarios (uno es guía general, el otro es una aplicación
  concreta documentada).

---

## 5. Síntesis comparativa

| Trabajo | Dominio | Enfoque | Evaluación | Relación con BIOPET | Diferencia principal |
|---|---|---|---|---|---|
| Rodríguez et al. (2024) — OSCRUM | Gestión veterinaria, sistema web de código abierto | Estudio de caso de proceso ágil (OSCRUM) | No reporta evaluación empírica multimétrica del artefacto resultante | Dominio más cercano encontrado; comparte interés en reproducibilidad | Documenta *proceso*, no evalúa empíricamente el *producto* |
| Putri, Hadi & Ramdani (2017) | Sistema web académico (admisión de estudiantes) | Estudio de caso, pruebas de carga (*benchmarking*) | Rendimiento bajo carga (tiempos de respuesta) | Paralelo metodológico directo a la evaluación k6 de BIOPET | Solo rendimiento; sin seguridad, usabilidad ni cobertura |
| Yang et al. (2026) — NDSS | Seguridad de implementaciones JWT (multi-lenguaje) | Fuzzing dirigido sobre 43 implementaciones JWT reales | Vulnerabilidades descubiertas (31 nuevas, 20 con CVE) | Referencia directa sobre riesgos de la misma tecnología de autenticación que usa BIOPET | Audita librerías JWT genéricas, no una aplicación concreta de principio a fin |
| Estdale & Georgiadou (2018) | Ingeniería de calidad de software (general) | Aplicación guiada del modelo ISO/IEC 25010 a un producto | Discusión de qué características son evidenciables en la práctica | Paralelo metodológico directo a `docs/arquitectura/ISO-25010.md` | Guía general, no un mapeo concreto con rutas de evidencia verificables |

### Síntesis (no una lista)

Los cuatro trabajos comparten un patrón común con BIOPET: ninguno reclama
un diseño experimental controlado; los cuatro usan estudios de caso o
*benchmarking* técnico como método empírico, consistente con la
clasificación metodológica ya justificada para BIOPET en
`docs/checklists/ralph2021-engineering-research.md` y
`docs/informe/borradores/jaime/metodologia-y-amenazas.md`. El vacío común
que se observa entre estos trabajos, tomados individualmente, es que cada
uno evalúa **una sola dimensión de calidad a la vez**: OSCRUM documenta
proceso sin evaluación empírica del producto; Putri et al. miden
rendimiento sin seguridad ni usabilidad; Yang et al. auditan seguridad de
una tecnología (JWT) sin evaluar un sistema de aplicación completo;
Estdale & Georgiadou discuten el modelo de calidad sin aplicarlo a un
caso con evidencia verificable enlazada.

BIOPET se posiciona, dentro de este panorama, como una evaluación
**multimétrica** de un único artefacto: cobertura de pruebas (JaCoCo),
rendimiento (k6), usabilidad (SUS), calidad web (Lighthouse), y seguridad
por tres ángulos distintos (revisión manual OWASP, análisis dinámico ZAP,
análisis estático SpotBugs/Find Security Bugs), todas enmarcadas
explícitamente bajo ISO/IEC 25010 como vocabulario común
(`docs/arquitectura/ISO-25010.md`). Esta combinación es relevante
precisamente porque ninguno de los trabajos relacionados encontrados
reporta las seis dimensiones a la vez sobre el mismo artefacto — no
porque BIOPET sea metodológicamente superior a ellos (no existe
comparación empírica que lo demuestre), sino porque la amplitud de la
evidencia recolectada sobre un mismo sistema es, en sí misma, la
contribución práctica más defendible de esta evaluación: permite razonar
sobre las relaciones entre dimensiones de calidad (por ejemplo, entre
cobertura de pruebas y ausencia de hallazgos de seguridad estática) que
un estudio de una sola dimensión no puede mostrar.

---

## Referencias citadas en este documento (candidatas, no incorporadas todavía a `referencias.bib`)

Ver `docs/informe/borradores/jaime/referencias-candidatas.md` para la
ficha completa de verificación de cada una (autores, DOI, temática, lugar
sugerido de uso, estado en la bibliografía actual).
