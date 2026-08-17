# Borrador — Referencias candidatas de Jaime (BIOPET, Entrega Final v1.0.0)

**Autor:** Jaime Mariscal.
**Estado:** Borrador de trabajo. **No modifica** `docs/informe/referencias.bib`
(bibliografía maestra) ni el informe LaTeX maestro, para evitar conflicto
con la integración final de Zaida. Este archivo es una propuesta que debe
incorporarse manualmente (o revisarse en conjunto) antes del cierre del
informe.
**Fecha de esta revisión:** 2026-08-17.
**Método de verificación:** cada referencia se contrastó contra Crossref
(`api.crossref.org`), la página oficial del editor/conferencia, o la
página oficial de la norma (ISO). Ningún DOI fue inventado; donde un dato
no pudo verificarse con certeza, se indica explícitamente como tal en vez
de completarse por intuición.

## Auditoría previa (resumen)

`docs/informe/referencias.bib` (bibliografía maestra, estilo BibTeX
clásico + `\bibliographystyle{ieeetr}`) tiene **15 entradas**, todas
normas técnicas (RFC 7519, RFC 7807), documentación oficial de
herramientas ya usadas en BIOPET (Spring Security, Spring Boot, JaCoCo,
PostgreSQL, Redis, k6, Lighthouse), o referencias ampliamente reconocidas
citadas por nombre estándar (OWASP Top 10:2021, modelo C4 de Simon Brown,
SUS de Brooke 1996, ISO 9241-11:2018, WCAG 2.1, taxonomía CRediT). No se
encontró ninguna duplicidad interna ni entrada con datos incompletos: el
propio archivo ya documenta, en comentarios junto a cada entrada, cuándo
un dato no pudo reverificarse en línea (ver `sus-brooke1996`). **Ninguna
de las 15 entradas existentes cubre metodología de investigación
(Ralph et al., Peffers et al., GQM) ni trabajos académicos relacionados
con el dominio o la metodología de BIOPET** — esa es la brecha que este
documento y `trabajos-relacionados.md` proponen cerrar.

`docs/u4/informe/referencias.bib` es la bibliografía de la Unidad IV del
Equipo H (biblatex/apa), fuera del alcance de esta fase (no es del área
de Jaime en esta entrega ni corresponde al informe de la Entrega Final).

## Total propuesto

**12 referencias**: **11 nuevas** (no presentes en ninguna bibliografía
del repositorio) + **1 ya existente** (OWASP Top 10:2021, reafirmada aquí
por completitud del área de seguridad de Jaime, no duplicada). No se
agregaron referencias adicionales solo para completar un número: cada una
respalda directamente una afirmación metodológica o de evidencia ya
presente en el trabajo de Jaime.

## Distribución por temática

| Temática | Cantidad |
|---|---:|
| Metodología (Engineering Research / DSR / GQM) | 4 |
| Calidad de producto software | 3 |
| Seguridad web | 2 |
| Rendimiento | 2 |
| Arquitectura/dominio (trabajos relacionados) | 1 |

---

## A. Metodología (Engineering Research / Design Science / GQM)

### A1. Ralph et al. (2021) — Empirical Standards for Software Engineering Research

- **Referencia completa:** Ralph, P., bin Ali, N., Baltes, S., Bianculli,
  D., Diaz, J., Dittrich, Y., Ernst, N., Felderer, M., Feldt, R., Filieri,
  A., de França, B. B. N., Furia, C. A., Gay, G., Gold, N., Graziotin, D.,
  He, P., Hoda, R., Juristo, N., Kitchenham, B., Lenarduzzi, V., Martínez,
  J., Melegati, J., Mendez, D., Menzies, T., Molleri, J., Pfahl, D.,
  Robbes, R., Russo, D., Saarimäki, N., Sarro, F., Taibi, D., Siegmund,
  J., Spinellis, D., Staron, M., Stol, K., Storey, M.-A., Tamburri, D.,
  Torchiano, M., Treude, C., Turhan, B., Vegas, S., & Wang, X. (2021).
  *Empirical Standards for Software Engineering Research* (≈40
  coautores; lista abreviada, ver el propio arXiv para la lista
  completa). arXiv:2010.03525 [cs.SE].
- **DOI/enlace oficial:** [10.48550/arXiv.2010.03525](https://doi.org/10.48550/arXiv.2010.03525)
  (preprint arXiv; es la iniciativa oficial de ACM SIGSOFT — repositorio
  vivo en [www2.sigsoft.org/EmpiricalStandards](https://www2.sigsoft.org/EmpiricalStandards/)).
- **Temática:** metodología — fundamento directo de
  `docs/checklists/ralph2021-engineering-research.md` (el estándar
  *Engineering Research* usado allí proviene de este trabajo).
- **Lugar sugerido de uso:** capítulo de metodología/protocolo
  experimental del informe final, al justificar por qué se eligió
  *Engineering Research* como estándar empírico.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Fuente oficial` (documento
  técnico primario de la iniciativa ACM SIGSOFT; preprint arXiv, no se
  encontró una versión de acta de conferencia con DOI distinto al de
  arXiv).

### A2. Peffers, Tuunanen, Rothenberger & Chatterjee (2007) — Design Science Research Methodology

- **Referencia completa:** Peffers, K., Tuunanen, T., Rothenberger, M.
  A., & Chatterjee, S. (2007). *A Design Science Research Methodology for
  Information Systems Research*. Journal of Management Information
  Systems, 24(3), 45–77.
- **DOI/enlace oficial:** [10.2753/MIS0742-1222240302](https://doi.org/10.2753/MIS0742-1222240302)
  (verificado vía Crossref).
- **Temática:** metodología — fundamento directo de la sección "Design
  Science Research" del checklist y del mapeo de las 6 actividades DSR en
  `docs/informe/borradores/jaime/metodologia-y-amenazas.md`.
- **Lugar sugerido de uso:** capítulo de metodología, sección de mapeo
  DSR (actualmente cita "Peffers et al. (2007)" sin referencia
  bibliográfica formal).
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Artículo revisado por pares`
  (Journal of Management Information Systems, revista académica
  arbitrada).

### A3. van Solingen, Basili, Caldiera & Rombach (2002) — Goal Question Metric (GQM) Approach

- **Referencia completa:** van Solingen, R., Basili, V., Caldiera, G., &
  Rombach, H. D. (2002). *Goal Question Metric (GQM) Approach*. En
  Encyclopedia of Software Engineering. Wiley.
- **DOI/enlace oficial:** [10.1002/0471028959.sof142](https://doi.org/10.1002/0471028959.sof142)
  (verificado vía Crossref; es la revisión/actualización citable del
  trabajo original de Basili, Caldiera & Rombach, 1994, *Goal Question
  Metric Paradigm*, Encyclopedia of Software Engineering, vol. 1,
  pp. 528–532, sin DOI propio verificable de forma independiente).
- **Temática:** metodología — fundamento directo de la sección GQM ya
  redactada en `docs/checklists/ralph2021-engineering-research.md` §7 y
  `docs/informe/borradores/jaime/metodologia-y-amenazas.md` §3, que
  actualmente no cita ninguna fuente bibliográfica para el propio término
  "Goal-Question-Metric".
- **Lugar sugerido de uso:** capítulo de metodología, sección GQM.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Capítulo de obra de
  referencia revisada` (Encyclopedia of Software Engineering, Wiley).

### A4. Hasselbring (2021) — Benchmarking as Empirical Standard in Software Engineering Research

- **Referencia completa:** Hasselbring, W. (2021). *Benchmarking as
  Empirical Standard in Software Engineering Research*. Proceedings of
  the 25th International Conference on Evaluation and Assessment in
  Software Engineering (EASE 2021), pp. 365–372. ACM.
- **DOI/enlace oficial:** [10.1145/3463274.3463361](https://doi.org/10.1145/3463274.3463361)
  (verificado vía Crossref).
- **Temática:** metodología — respalda directamente la clasificación de
  BIOPET como "estudio de *benchmarking* técnico" ya usada en el
  checklist Ralph et al. (ítem E4/E5) y en el borrador de metodología;
  hasta ahora esa clasificación no citaba ninguna fuente que defina
  formalmente el *benchmarking* como estándar empírico reconocido.
- **Lugar sugerido de uso:** capítulo de metodología, junto a la
  justificación de por qué el diseño de evaluación de BIOPET corresponde
  a un estudio de *benchmarking*.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Proceedings ACM/IEEE` (ACM,
  EASE 2021).

---

## B. Calidad de producto software

### B1. ISO/IEC (2011) — ISO/IEC 25010:2011

- **Referencia completa:** International Organization for
  Standardization / International Electrotechnical Commission. (2011).
  *ISO/IEC 25010:2011 — Systems and software engineering — Systems and
  software Quality Requirements and Evaluation (SQuaRE) — System and
  software quality models*. ISO.
- **DOI/enlace oficial:** sin DOI público (las normas ISO no siempre lo
  tienen); página oficial verificada:
  [https://www.iso.org/standard/35733.html](https://www.iso.org/standard/35733.html).
- **Temática:** calidad — es la norma que `docs/arquitectura/ISO-25010.md`
  usa como marco de referencia (edición 2011, ya fijada y justificada en
  ese documento), pero que **no tiene todavía una entrada formal en
  `referencias.bib`**.
- **Lugar sugerido de uso:** capítulo de arquitectura/calidad del informe
  final, al citar el marco ISO/IEC 25010.
- **¿Ya existe en la bibliografía actual?** No (aunque se menciona en
  prosa dentro de `docs/arquitectura/ISO-25010.md`, sin entrada BibTeX).
- **Clasificación de calidad de fuente:** `Estándar internacional`.

### B2. Estdale & Georgiadou (2018) — Aplicación de ISO/IEC 25010 a un producto de software

- **Referencia completa:** Estdale, J., & Georgiadou, E. (2018).
  *Applying the ISO/IEC 25010 Quality Models to Software Product*. En
  Systems, Software and Services Process Improvement (EuroSPI 2018),
  Communications in Computer and Information Science, vol. 896. Springer,
  pp. 492–503.
- **DOI/enlace oficial:** [10.1007/978-3-319-97925-0_42](https://doi.org/10.1007/978-3-319-97925-0_42)
  (verificado vía Crossref).
- **Temática:** calidad — trabajo relacionado metodológico directo (ver
  `trabajos-relacionados.md`, sección 4).
- **Lugar sugerido de uso:** capítulo de trabajos relacionados y/o de
  arquitectura/calidad, junto a `docs/arquitectura/ISO-25010.md`.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Proceedings` (Springer CCIS,
  EuroSPI, revisado por pares).

### B3. Bangor, Kortum & Miller (2008) — Empirical Evaluation of the System Usability Scale

- **Referencia completa:** Bangor, A., Kortum, P. T., & Miller, J. T.
  (2008). *An Empirical Evaluation of the System Usability Scale*.
  International Journal of Human–Computer Interaction, 24(6), 574–594.
- **DOI/enlace oficial:** [10.1080/10447310802205776](https://doi.org/10.1080/10447310802205776)
  (verificado vía Crossref).
- **Temática:** calidad/usabilidad — ya se cita "Bangor et al., 2008"
  como fuente del umbral de referencia de 68 puntos SUS en
  `docs/arquitectura/ISO-25010.md`,
  `docs/checklists/ralph2021-engineering-research.md` y
  `docs/informe/borradores/jaime/metodologia-y-amenazas.md`, **sin que
  exista todavía una entrada bibliográfica formal** para esa cita — es la
  brecha bibliográfica más directa y ya detectada por el propio trabajo
  previo de Jaime.
- **Lugar sugerido de uso:** capítulo de resultados de usabilidad (SUS),
  junto a `sus-brooke1996` ya existente.
- **¿Ya existe en la bibliografía actual?** No (solo se cita en prosa
  como "Bangor et al., 2008", sin entrada BibTeX).
- **Clasificación de calidad de fuente:** `Artículo revisado por pares`
  (International Journal of Human–Computer Interaction).

---

## C. Seguridad web

### C1. Yang et al. (2026) — Token Time Bomb: vulnerabilidades en implementaciones JWT

- **Referencia completa:** Yang, J., Wang, E., Chen, J., Wang, Q., Zhang,
  Y., Duan, H., Xie, W., & Wang, B. (2026). *Token Time Bomb: Evaluating
  JWT Implementations for Vulnerability Discovery*. Proceedings of the
  Network and Distributed System Security Symposium (NDSS 2026).
- **DOI/enlace oficial:** [10.14722/ndss.2026.240697](https://doi.org/10.14722/ndss.2026.240697)
  (verificado; NDSS Symposium, 23–27 de febrero de 2026, San Diego, CA).
- **Temática:** seguridad — trabajo relacionado directo (ver
  `trabajos-relacionados.md`, sección 3); aporta valor académico real más
  allá de la documentación técnica de JWT/cookies ya citada
  (`rfc7519` en el bib actual), al ser un estudio empírico de
  vulnerabilidades reales en implementaciones JWT.
- **Lugar sugerido de uso:** capítulo de resultados de seguridad de
  Jaime, al contextualizar la auditoría JWT de BIOPET frente al estado
  del arte de vulnerabilidades conocidas de esa tecnología.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Proceedings` (revisado por
  pares, NDSS Symposium — venue de referencia en seguridad de sistemas).

### C2. OWASP Foundation (2021) — OWASP Top 10:2021

- **Referencia completa:** OWASP Foundation. (2021). *OWASP Top 10:2021
  — The Ten Most Critical Web Application Security Risks*.
- **DOI/enlace oficial:** sin DOI (documento de la industria, no
  académico); URL oficial: [https://owasp.org/Top10/](https://owasp.org/Top10/).
- **Temática:** seguridad — ya es la base de las seis categorías OWASP
  auditadas por Jaime (`docs/mediciones/sec/owasp/README.md`).
- **Lugar sugerido de uso:** capítulo de resultados de seguridad de
  Jaime (ya se usa; se reafirma aquí por completitud del área, sin
  proponer cambio alguno a la entrada existente `owasp-top10`).
- **¿Ya existe en la bibliografía actual?** **Sí** (`owasp-top10` en
  `docs/informe/referencias.bib`, sin cambios propuestos).
- **Clasificación de calidad de fuente:** `Fuente oficial` (documento de
  la industria, OWASP Foundation).

---

## D. Rendimiento

### D1. Putri, Hadi & Ramdani (2017) — Pruebas de rendimiento de un sistema web académico

- **Referencia completa:** Putri, M. A., Hadi, H. N., & Ramdani, F.
  (2017). *Performance testing analysis on web application: Study case
  student admission web system*. 2017 International Conference on
  Sustainable Information Engineering and Technology (SIET). IEEE.
- **DOI/enlace oficial:** [10.1109/SIET.2017.8304099](https://doi.org/10.1109/SIET.2017.8304099)
  (verificado vía Crossref).
- **Temática:** rendimiento — trabajo relacionado directo (ver
  `trabajos-relacionados.md`, sección 2).
- **Lugar sugerido de uso:** capítulo de trabajos relacionados y/o de
  resultados de rendimiento.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Proceedings IEEE`.

### D2. Rempel (2015) — Estándares de rendimiento de páginas web en aplicaciones de negocio

- **Referencia completa:** Rempel, G. (2015). *Defining Standards for
  Web Page Performance in Business Applications*. Proceedings of the 6th
  ACM/SPEC International Conference on Performance Engineering (ICPE
  '15), pp. 245–252. ACM.
- **DOI/enlace oficial:** [10.1145/2668930.2688056](https://doi.org/10.1145/2668930.2688056)
  (verificado vía Crossref).
- **Temática:** rendimiento — referencia de apoyo para interpretar los
  umbrales de percentiles (p95/p99) ya reportados en
  `docs/mediciones/perf/REPORT.md`, actualmente sin ningún marco de
  referencia citado para "qué percentil es razonable" en una aplicación
  web.
- **Lugar sugerido de uso:** capítulo de resultados de rendimiento,
  al interpretar los valores de p95/p99 de k6.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Proceedings ACM` (ACM/SPEC
  ICPE, venue de referencia en ingeniería de rendimiento).

---

## E. Arquitectura/dominio (trabajos relacionados)

### E1. Rodríguez, Llerena, Guevara, Baren & Castro (2024) — OSCRUM: sistema web de gestión veterinaria

- **Referencia completa:** Rodríguez, N., Llerena, L., Guevara, P.,
  Baren, R., & Castro, J. W. (2024). *Open-Source Web System for
  Veterinary Management: A Case Study of OSCRUM*. En Information Systems
  and Technologies, Lecture Notes in Networks and Systems. Springer
  Nature Switzerland, pp. 320–329.
- **DOI/enlace oficial:** [10.1007/978-3-031-45642-8_32](https://doi.org/10.1007/978-3-031-45642-8_32)
  (verificado vía Crossref).
- **Temática:** arquitectura/dominio — único trabajo académico verificado
  específicamente sobre gestión veterinaria vía sistema web (ver
  `trabajos-relacionados.md`, sección 1).
- **Lugar sugerido de uso:** capítulo de trabajos relacionados, como el
  trabajo de dominio más cercano a BIOPET.
- **¿Ya existe en la bibliografía actual?** No.
- **Clasificación de calidad de fuente:** `Proceedings` (Springer LNNS,
  revisado por pares).

---

## Resumen de verificación

| # | Referencia | DOI verificado | Fuente de verificación |
|---|---|---|---|
| A1 | Ralph et al. (2021) | Sí (arXiv) | arXiv.org (página propia del preprint) |
| A2 | Peffers et al. (2007) | Sí | Crossref (`api.crossref.org`) |
| A3 | van Solingen et al. (2002) | Sí | Crossref (`api.crossref.org`) |
| A4 | Hasselbring (2021) | Sí | Crossref (`api.crossref.org`) |
| B1 | ISO/IEC 25010:2011 | No aplica (norma sin DOI público) | Página oficial ISO (`iso.org/standard/35733.html`) |
| B2 | Estdale & Georgiadou (2018) | Sí | Crossref (`api.crossref.org`) |
| B3 | Bangor, Kortum & Miller (2008) | Sí | Crossref (`api.crossref.org`) |
| C1 | Yang et al. (2026) | Sí | Página oficial NDSS Symposium + búsqueda cruzada |
| C2 | OWASP Top 10:2021 | No aplica (documento de industria sin DOI) | Ya verificada previamente (entrada existente) |
| D1 | Putri, Hadi & Ramdani (2017) | Sí | Crossref (`api.crossref.org`) |
| D2 | Rempel (2015) | Sí | Crossref (`api.crossref.org`) |
| E1 | Rodríguez et al. (2024) | Sí | Crossref (`api.crossref.org`) |

No se incluyó ninguna referencia cuya existencia no pudiera verificarse.
Ningún DOI fue inventado: los dos casos sin DOI (ISO/IEC 25010:2011 y
OWASP Top 10:2021) son fuentes oficiales sin DOI público, verificadas
contra su página oficial en su lugar, tal como ya lo hace la práctica
existente en `docs/informe/referencias.bib` para sus propias entradas
`iso9241-11` y `owasp-top10`.
