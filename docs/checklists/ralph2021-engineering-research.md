# Checklist — Ralph et al. (2021), estándar "Engineering Research" — BIOPET

## 1. Estándar seleccionado y por qué aplica

**Estándar:** *Engineering Research* (también conocido como *Design Science*),
uno de los estándares empíricos definidos por la iniciativa ACM SIGSOFT
Empirical Standards (Ralph et al., 2021, *"Empirical Standards for Software
Engineering Research"*).

**Por qué este estándar y no otro:** antes de elegirlo se auditó la
evidencia real ya existente en el repositorio (ver sección 2). BIOPET:

- **No es** un *Controlled Experiment*: no existe manipulación de una
  variable independiente con grupos de tratamiento/control, ni asignación
  aleatoria de sujetos, ni hipótesis formal contrastada estadísticamente.
  Las corridas de k6 son mediciones de *benchmarking* del propio artefacto
  bajo condiciones fijas (frío/caliente), no un experimento con grupos
  comparados.
- **No es** un *Case Study* en el sentido de Ralph et al. (estudio en
  profundidad de un fenómeno dentro de un contexto organizacional real, con
  múltiples fuentes de datos cualitativas sobre un caso ya existente en su
  entorno natural): BIOPET es un artefacto construido por el propio equipo,
  no un fenómeno observado en una organización externa.
- **Sí es** una investigación que **diseña, construye y evalúa
  empíricamente un artefacto de ingeniería de software** (BIOPET: backend +
  frontend + infraestructura), con evidencia real de diseño (ADR, C4),
  evaluación empírica (JaCoCo, k6, SUS, Lighthouse, OWASP/ZAP/SpotBugs) y
  discusión de limitaciones (amenazas a la validez) — exactamente el
  patrón que *Engineering Research* está diseñado para evaluar.

## 2. Auditoría previa (resumen)

Se inspeccionó, antes de seleccionar el estándar:

| Área | Evidencia encontrada | Ubicación |
|---|---|---|
| Metodología / requisitos | SRS con REQ-F/REQ-NF, historias de usuario, casos de uso | `docs/requisitos/SRS.md`, `docs/requisitos/historias/`, `docs/requisitos/casos-de-uso/` |
| Trazabilidad | Matriz completa SRS↔HU↔CU↔prueba, 38 requisitos | `docs/trazabilidad/matriz.csv`, `scripts/validate-traceability.sh` |
| Diseño del artefacto | 6 ADR con alternativas consideradas; diagramas C4 (contexto, contenedores, componentes) | `docs/adr/`, `docs/diagrams/c4-*` |
| Desarrollo del artefacto | Código fuente real del backend y frontend | `Backend/src/`, `frontend/src/` |
| Evaluación de rendimiento | k6, 6 corridas, IC 95% con distribución t | `docs/mediciones/perf/REPORT.md` |
| Evaluación de usabilidad | SUS, n=18 participantes, media 74.44/100, IC95% [63.33, 85.56] | `docs/mediciones/sus/REPORT.md`, `docs/mediciones/sus/sus-raw.csv` |
| Evaluación de accesibilidad | Lighthouse, Accessibility=91/100 | `docs/mediciones/lighthouse/README.md` |
| Evaluación de seguridad | OWASP (6 categorías), ZAP Baseline, SpotBugs/Find Security Bugs | `docs/mediciones/sec/` |
| Cobertura de pruebas | JaCoCo, LINE 91.80%, BRANCH 79.39% | `docs/mediciones/jacoco/METRICS.md` |
| Auditoría SQL | `db/procs/*.sql` sin patrones peligrosos | `scripts/audit-sql-dynamic.sh` |
| Amenazas a la validez | Discusión explícita (interna, externa, de constructo, de conclusión) | `docs/informe/secciones/09-amenazas-validez.tex` |
| Informe de Entrega 3 | Documento LaTeX con protocolo experimental y resultados parciales (fecha anterior a esta fase; algunos datos ya desactualizados — ver Brechas) | `docs/informe/informe-entrega-3.pdf`, `docs/informe/secciones/` |

**Conclusión de la auditoría:** el estudio predominante de BIOPET es la
construcción y evaluación empírica de un artefacto de software (backend +
frontend + infraestructura), evaluado mediante *benchmarking* técnico
(rendimiento, cobertura, seguridad) y una encuesta de usabilidad de muestra
pequeña — no un experimento controlado ni un estudio de caso organizacional.

## 3. Alcance de este checklist

Cubre el **artefacto BIOPET completo** tal como existe en el repositorio a
la fecha de esta revisión (backend Spring Boot, frontend Angular,
infraestructura Docker Compose, y toda la evidencia empírica ya recolectada
en `docs/mediciones/`). No cubre el contenido del informe académico final de
la Entrega Final (que aún no se ha escrito — ver sección de Brechas); donde
corresponda, se distingue explícitamente entre "evidencia técnica ya
recolectada" y "redacción académica pendiente".

## 4. Fecha de revisión

2026-08-17 (creación); reconciliado el mismo día tras integrarse desde
`main` la muestra SUS ampliada de Zaida (n=10 → n=18, media 74.44/100,
IC 95 % [63.33, 85.56] — ver `docs/mediciones/sus/REPORT.md`). Solo se
actualizaron cifras de evidencia SUS y su interpretación prudente; ningún
estado de ítem cambió por esta reconciliación (el aumento de muestra no
satisface por sí solo ningún criterio adicional de Ralph et al. — ver
detalle en cada ítem afectado, más abajo).

## 5. Checklist ítem por ítem

Fuente de los ítems: estándar *Engineering Research (Design Science)* de
ACM SIGSOFT Empirical Standards (Ralph et al., 2021). Se listan los
**atributos esenciales** (obligatorios) y **atributos deseables**
(recomendados, no obligatorios).

### 5.1 Atributos esenciales

| # | Ítem (paráfrasis del estándar) | Estado | Evidencia concreta | Ubicación en el repositorio | Acción pendiente |
|---|---|---|---|---|---|
| E1 | Describe el artefacto propuesto con detalle adecuado | CUMPLE | SRS con 38 requisitos funcionales/no funcionales; 3 diagramas C4 (contexto, contenedores, componentes backend); 6 ADR con decisión y justificación técnica | `docs/requisitos/SRS.md`, `docs/diagrams/c4-contexto/`, `docs/diagrams/c4-contenedores/`, `docs/diagrams/c4-componentes-backend/`, `docs/adr/` | Ninguna |
| E2 | Justifica la necesidad, utilidad o relevancia del artefacto | CUMPLE | SRS §1 (Propósito y Alcance); resumen ejecutivo del informe describe el propósito y las mejoras respecto a la entrega anterior | `docs/requisitos/SRS.md` (sección 1), `docs/informe/secciones/01-resumen-ejecutivo.tex` | Ninguna |
| E3 | Evalúa conceptualmente el artefacto; discute fortalezas, debilidades y limitaciones | CUMPLE | Capítulo dedicado de amenazas a la validez (interna, externa, de constructo, de conclusión); limitaciones explícitas en cada documento de `docs/mediciones/sec/` (ej. TLS autofirmado, rate limiting no distribuido, ausencia de SIEM) | `docs/informe/secciones/09-amenazas-validez.tex`, `docs/mediciones/sec/*.md` (secciones "Limitaciones") | El capítulo de amenazas a la validez corresponde a la Entrega 3; requiere revisión para incorporar las mediciones nuevas de esta fase (JaCoCo 70%, ZAP, SpotBugs) — ver Brechas |
| E4 | Evalúa empíricamente el artefacto usando uno de: investigación-acción, estudio de caso, experimento controlado, simulación cuantitativa, estudio de *benchmarking*, u otro método con justificación clara | CUMPLE | El diseño real corresponde a un **estudio de benchmarking técnico** (rendimiento k6 contra umbrales, cobertura JaCoCo contra umbral, seguridad ZAP/SpotBugs contra 0 hallazgos altos) combinado con una **encuesta de usabilidad** (SUS, n=18) — ambos métodos empíricos reconocidos, con datos crudos reales | `docs/mediciones/perf/`, `docs/mediciones/jacoco/`, `docs/mediciones/sec/zap/`, `docs/mediciones/sec/static-analysis/`, `docs/mediciones/sus/` | Ninguna |
| E5 | Indica claramente cuál de esas metodologías empíricas se usó | CUMPLE | El borrador metodológico de Jaime declara explícitamente, usando la taxonomía de Ralph et al., que el diseño empírico de BIOPET es un **estudio de *benchmarking* técnico** (rendimiento k6, cobertura JaCoCo, seguridad ZAP/SpotBugs) **combinado con una encuesta de usabilidad de muestra pequeña** (SUS), y explica por qué se descartaron *controlled experiment* y *case study* como alternativas | `docs/informe/borradores/jaime/metodologia-y-amenazas.md` (sección 1, "Enfoque metodológico") | El documento es un borrador de Jaime, listo para migrar a LaTeX, pero todavía no incorporado al informe maestro (`docs/informe/secciones/05-protocolo-experimental.tex`); esa migración queda fuera del alcance de esta fase |
| E6 | Discute alternativas de estado del arte (fortalezas/debilidades), explica por qué no existen, o justifica por qué la comparación es impráctica | CUMPLE PARCIALMENTE | 5 de 6 ADR incluyen una sección explícita "Alternativas consideradas" (decisiones de tecnología/arquitectura, con ventajas/desventajas) | `docs/adr/ADR-002-pila-tecnologica.md`, `ADR-004-postgresql.md`, `ADR-005-despliegue.md`, `ADR-006-autenticacion-seguridad.md`, `ADR-007-acceso-datos.md` | Los ADR comparan alternativas de *diseño interno* (tecnologías, patrones), no alternativas de *artefactos externos* (otros sistemas de gestión veterinaria). No existe una comparación con sistemas competidores ni una justificación explícita de por qué esa comparación se omite |
| E7 | Compara empíricamente con alternativas, compara con *benchmarks*, o justifica por qué la evaluación comparativa es impráctica | CUMPLE PARCIALMENTE | Comparación real contra umbrales/*benchmarks* técnicos: JaCoCo ≥70%, Lighthouse Accessibility ≥90, k6 error 0.0%, SUS contra el umbral de referencia de 68 puntos (Bangor et al., 2008) | `docs/mediciones/jacoco/METRICS.md`, `docs/mediciones/lighthouse/README.md`, `docs/mediciones/perf/REPORT.md`, `docs/mediciones/sus/REPORT.md` | Existe comparación contra *benchmarks* (umbrales), pero no contra artefactos alternativos (otros sistemas), y no hay una justificación explícita por escrito de por qué se omite esa comparación |
| E8 | Los supuestos (si existen) son explícitos, plausibles y no se contradicen entre sí ni con los objetivos de la contribución | CUMPLE PARCIALMENTE | Supuestos documentados de forma dispersa: elección del método de medición de caché ("aislado" vs. global) justificada explícitamente; entorno de una sola máquina declarado como supuesto/limitación | `docs/informe/secciones/09-amenazas-validez.tex` (validez interna, primer y tercer punto) | No existe una sección única y consolidada de "supuestos"; están repartidos entre amenazas a la validez y contextos de ADR individuales |
| E9 | Usa notación de forma consistente (si se usa notación) | CUMPLE | Identificadores consistentes en todo el proyecto: `REQ-F-NNN`/`REQ-NF-NNN` (SRS), `HU-NNN`/`CU-NN` (historias/casos de uso), formato `ProblemDetail` (RFC 7807) uniforme en todas las respuestas de error, notación C4 estándar en los diagramas | `docs/requisitos/SRS.md`, `docs/trazabilidad/matriz.csv`, `docs/diagrams/c4-*`, `Backend/src/main/java/com/biopet/exception/GlobalExceptionHandler.java` | Ninguna |

### 5.2 Atributos deseables

| # | Ítem (paráfrasis del estándar) | Estado | Evidencia concreta | Ubicación en el repositorio | Acción pendiente |
|---|---|---|---|---|---|
| D1 | Proporciona materiales suplementarios: código fuente (si el artefacto es software) o descripción completa, y conjuntos de datos de entrada si aplica | CUMPLE | Código fuente completo del backend y frontend en el propio repositorio; datos crudos versionados de cada medición (JSON de k6, CSV de SUS, XML de JaCoCo/SpotBugs, JSON/XML de ZAP) | `Backend/src/`, `frontend/src/`, `docs/mediciones/perf/k6-run*.json`, `docs/mediciones/sus/sus-raw.csv`, `docs/mediciones/jacoco/jacoco.xml`, `docs/mediciones/sec/static-analysis/spotbugs-report.xml`, `docs/mediciones/sec/zap/zap-baseline-report.json` | Ninguna |
| D2 | Justifica cualquier elemento faltante del paquete de replicación por motivos prácticos o éticos | CUMPLE PARCIALMENTE | Se documenta explícitamente qué archivos crudos de seguridad no se versionan y por qué (`docs/mediciones/sec/raw/` conserva solo `.gitkeep` para ciertos artefactos regenerables) | `docs/informe/secciones/05-protocolo-experimental.tex` (sección "Mediciones de seguridad") | No existe una lista consolidada y única de "qué falta y por qué" para el paquete de replicación completo (backend + frontend + mediciones) |
| D3 | Discute la base teórica del artefacto | CUMPLE PARCIALMENTE | El SRS cita conformidad con ISO/IEC/IEEE 29148:2018 e INCOSE; los ADR citan referencias técnicas puntuales (JWT/RFC 7519, OWASP); ahora también `docs/arquitectura/ISO-25010.md` enmarca la calidad según ISO/IEC 25010:2011 | `docs/requisitos/SRS.md`, `docs/adr/`, `docs/arquitectura/ISO-25010.md`, `docs/informe/referencias.bib` | La base teórica se cita de forma aplicada (normas, RFC) más que como un marco teórico de investigación en ingeniería de software; no hay una sección dedicada a fundamentos teóricos del propio artefacto |
| D4 | Proporciona argumentos de corrección para contribuciones analíticas/teóricas clave (teoremas, análisis de complejidad, demostraciones matemáticas) | NO APLICA | BIOPET es un artefacto de ingeniería de software aplicada (sistema de gestión veterinaria); no reclama una contribución algorítmica, teórica o matemática que requiera demostración formal | — | Ninguna: este ítem no aplica al tipo de contribución de BIOPET |
| D5 | Incluye uno o más ejemplos ilustrativos del artefacto | CUMPLE | Colección Postman con peticiones de ejemplo reales para cada endpoint; diagrama de secuencia del flujo JWT; criterios de aceptación con ejemplos concretos en cada requisito del SRS | `docs/postman/BIOPET.postman_collection.json`, `docs/diagrams/secuencia-jwt/`, `docs/requisitos/SRS.md` | Ninguna |
| D6 | Evalúa el artefacto en un contexto relevante para la industria (p. ej. proyectos *open-source* ampliamente usados, programadores profesionales) | NO CUMPLE | Toda la evaluación (SUS, rendimiento, seguridad) se realizó en un entorno académico local, con participantes y cuentas de prueba del propio equipo/entorno académico, no con desarrolladores profesionales externos ni en un proyecto *open-source* de uso amplio | `docs/informe/secciones/09-amenazas-validez.tex` (validez externa: "entorno académico local", "posible sesgo de participantes") | Fuera del alcance realista de un PFC académico; se documenta honestamente como limitación, no se simula una evaluación industrial inexistente |

### 5.3 Resumen numérico

| Estado | Cantidad |
|---|---:|
| Total de ítems | 15 |
| CUMPLE | 8 |
| CUMPLE PARCIALMENTE | 5 |
| NO CUMPLE | 1 |
| NO APLICA | 1 |

## 6. Mapeo de BIOPET con Design Science Research (Peffers et al.)

El estándar *Engineering Research* de Ralph et al. es también conocido como
*Design Science*; por eso tiene sentido metodológico verificar si el
trabajo ya realizado en BIOPET puede mapearse, de forma defendible y sin
inventar contenido, con las seis actividades del modelo de proceso DSR de
Peffers et al. (2007). Este mapeo es preliminar y **no reemplaza ni
reescribe** el capítulo de metodología del informe académico final.

| Actividad DSR | Cómo se manifiesta en BIOPET | Evidencia | Estado |
|---|---|---|---|
| 1. Identificación del problema y motivación | Observaciones oficiales de retroalimentación docente (Entregas 1A, 1B, Entrega 3) identifican deficiencias concretas del artefacto/proceso, ya registradas y en su mayoría corregidas | `docs/observaciones/OBSERVACIONES.md` (15 observaciones, con evidencia y estado por ítem), `docs/requisitos/SRS.md` §1 | Evaluada |
| 2. Definición de objetivos de una solución | Requisitos funcionales y no funcionales explícitos, con criterios de aceptación verificables; trazabilidad completa hacia historias/casos de uso | `docs/requisitos/SRS.md` §3, `docs/trazabilidad/matriz.csv` (38 requisitos) | Evaluada |
| 3. Diseño y desarrollo | Arquitectura documentada (C4, 3 niveles), decisiones de diseño con alternativas consideradas (ADR), implementación real del backend/frontend | `docs/diagrams/c4-*`, `docs/adr/` (6 ADR), `Backend/src/`, `frontend/src/` | Evaluada |
| 4. Demostración | Sistema ejecutable de punta a punta vía Docker Compose/`Makefile`; colección Postman con flujos reales ejercitados; escaneo ZAP y cargas k6 contra el sistema realmente en ejecución (no simulado) | `Makefile`, `docker-compose.yml`, `docs/postman/`, `docs/mediciones/perf/`, `docs/mediciones/sec/zap/` | Evaluada |
| 5. Evaluación | Batería empírica real: cobertura (JaCoCo), rendimiento (k6), usabilidad (SUS), accesibilidad (Lighthouse), seguridad (OWASP/ZAP/SpotBugs), auditoría SQL estática | `docs/mediciones/jacoco/`, `docs/mediciones/perf/`, `docs/mediciones/sus/`, `docs/mediciones/lighthouse/`, `docs/mediciones/sec/`, `scripts/audit-sql-dynamic.sh` | Evaluada |
| 6. Comunicación | Informe técnico en LaTeX ya redactado para la Entrega 3, con capítulos de protocolo experimental y amenazas a la validez; bitácora de observaciones actualizada | `docs/informe/informe-entrega-3.pdf`, `docs/informe/secciones/`, `docs/observaciones/OBSERVACIONES.md` | Evaluada parcialmente — el informe existente corresponde a la Entrega 3; la comunicación de los resultados de la Entrega Final (JaCoCo 70%, ZAP, SpotBugs, SUS/Lighthouse ya ejecutados) todavía no se ha redactado en un informe actualizado |

Las seis actividades tienen manifestación real y verificable en el
repositorio; la única brecha es que la actividad 6 (comunicación) está
desactualizada respecto a la evidencia técnica más reciente — no ausente,
sino pendiente de una revisión de redacción que **no corresponde a esta
fase** (fase documental/auditoría, sin tocar el informe final).

## 7. Goal–Question–Metric (GQM) preliminar

Formulado únicamente con mediciones que **ya se obtienen realmente** en el
repositorio; ninguna métrica nueva fue inventada para esta sección. Sirve
como material reutilizable para el capítulo de metodología del informe
final; no se modifica el informe en esta fase.

### Goal

Analizar el artefacto BIOPET (backend Spring Boot + frontend Angular +
infraestructura Docker) **con el propósito de** evaluar **con respecto a**
su corrección funcional, eficiencia de desempeño, usabilidad y seguridad
**desde el punto de vista** del equipo de desarrollo y evaluadores
académicos **en el contexto de** un Proyecto Fin de Curso, ejecutado en un
entorno de desarrollo local con Docker.

### Questions

| # | Pregunta | Métrica(s) asociada(s) |
|---|---|---|
| Q1 | ¿La suite de pruebas automatizadas del backend pasa consistentemente y qué proporción del código se ejercita? | JaCoCo LINE/BRANCH, resultado de `mvn clean verify` |
| Q2 | ¿Cuál es la latencia y la tasa de error del endpoint cacheado de mascotas bajo carga moderada, en frío y en caliente? | k6: p50/p90/p95/p99, tasa de error, throughput |
| Q3 | ¿Qué tan usable perciben participantes reales al frontend de BIOPET? | Puntaje medio SUS (0–100), IC 95% |
| Q4 | ¿El frontend cumple umbrales base de accesibilidad y rendimiento web automatizado? | Puntajes Lighthouse (Performance, Accessibility, Best Practices, SEO) |
| Q5 | ¿Existen hallazgos de seguridad de severidad alta detectables por escaneo dinámico automatizado? | Alertas ZAP Baseline por nivel de riesgo (`riskcode`) |
| Q6 | ¿El análisis estático detecta patrones de código propensos a inyección SQL u otras vulnerabilidades? | Hallazgos SpotBugs/Find Security Bugs (total y subconjunto `SQL_*`) |
| Q7 | ¿El SQL dinámico de los procedimientos almacenados se construye de forma seguro (sin concatenación insegura)? | Resultado (`exit code`) y hallazgos de `scripts/audit-sql-dynamic.sh` |
| Q8 | ¿Los requisitos funcionales declarados están respaldados por al menos una historia, caso de uso o prueba? | Resultado de `scripts/validate-traceability.sh` (requisitos vs. filas de matriz) |

### Metrics (ya recolectadas)

| Métrica | Valor real más reciente | Fuente |
|---|---|---|
| Cobertura JaCoCo (LINE / BRANCH) | 91.80 % / 79.39 % | `docs/mediciones/jacoco/METRICS.md` |
| k6 — tasa de error / throughput (ejemplo, corrida 3 caliente) | 0.0 % / ~92.34 req/s | `docs/mediciones/perf/REPORT.md` |
| SUS — puntaje medio (n=18) | 74.44 / 100, IC95% [63.33, 85.56] | `docs/mediciones/sus/REPORT.md` |
| Lighthouse — Accessibility | 91 / 100 | `docs/mediciones/lighthouse/README.md` |
| ZAP Baseline — alertas de riesgo alto | 0 | `docs/mediciones/sec/zap/README.md` |
| SpotBugs/Find Security Bugs — hallazgos SQL | 0 de 66 hallazgos totales | `docs/mediciones/sec/static-analysis/README.md` |
| `audit-sql-dynamic.sh` — hallazgos sobre `db/procs/*.sql` | 0 | `scripts/audit-sql-dynamic.sh` |
| Trazabilidad — requisitos consistentes | 38 requisitos / 38 filas en matriz | `docs/trazabilidad/matriz.csv` |

## 8. Brechas para cumplimiento completo

Pendientes reales, clasificados sin asignarlos a ninguna persona:

- **Informe académico de la Entrega Final todavía no redactado.** El único
  informe LaTeX existente (`docs/informe/informe-entrega-3.pdf`/`.tex`)
  corresponde a la Entrega 3 y describe SUS/Lighthouse como "pendientes de
  ejecución" y JaCoCo con un umbral del 60 % — ambos datos ya
  desactualizados respecto a la evidencia técnica real actual (SUS y
  Lighthouse ya ejecutados; JaCoCo con umbral 70 %). Esta fase no modifica
  el informe final, conforme a su propio alcance.
- **Ítem E5 ya satisfecho** (actualizado en esta revisión):
  `docs/informe/borradores/jaime/metodologia-y-amenazas.md` declara por
  escrito, usando la taxonomía de Ralph et al., que el diseño empírico de
  BIOPET es un estudio de *benchmarking* combinado con una encuesta de
  usabilidad de muestra pequeña. Queda como pendiente distinto (no una
  brecha de E5 en sí) migrar ese borrador al informe maestro en LaTeX
  (`docs/informe/secciones/05-protocolo-experimental.tex`), fuera del
  alcance de esta fase.
- **Sin comparación empírica con artefactos alternativos** (ítems E6/E7
  parciales): la evaluación compara a BIOPET contra umbrales técnicos
  propios, no contra otros sistemas de gestión veterinaria existentes; no
  hay una justificación explícita por escrito de por qué se omite esa
  comparación.
- **Tamaño muestral de SUS ampliado a n=18** (actualización reciente,
  integrada desde `main`; supera el mínimo de 15 exigido para la Entrega
  Final, documentado en `docs/etica/ETHICS.md`), sin análisis estadístico
  más allá de media e intervalo de confianza al 95 %; no se realizó un
  análisis de correlación entre variables demográficas (edad, experiencia
  web, dispositivo) y el puntaje SUS. Un tamaño muestral mayor reduce el
  margen del intervalo de confianza, pero **no implica automáticamente
  representatividad externa**: los participantes siguen siendo reclutados
  por conveniencia (ver amenazas a la validez externa en
  `docs/informe/borradores/jaime/metodologia-y-amenazas.md`).
- **Entorno de evaluación limitado a una sola máquina de desarrollo**, sin
  entorno de *staging* o producción equivalente; ya documentado
  explícitamente como amenaza a la validez externa, pero sigue siendo una
  brecha real, no resuelta.
- **DOI/Zenodo pendiente** (ya registrado como observación abierta,
  OBS-10, fuera del alcance de esta fase): sin archivado permanente
  citable del software.
- **Evaluación fuera de contexto industrial** (ítem D6): ninguna
  evaluación se realizó con desarrolladores profesionales externos ni en
  un proyecto de código abierto de uso amplio; es una limitación inherente
  al alcance de un Proyecto Fin de Curso, no un defecto oculto.
- **Consolidación de supuestos (ítem E8) y del inventario de paquete de
  replicación (ítem D2)** dispersos en varios documentos, sin una sección
  única dedicada a cada uno.
