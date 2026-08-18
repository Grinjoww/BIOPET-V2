# Borrador — Metodología y amenazas a la validez (BIOPET, Entrega Final v1.0.0)

**Autor:** Jaime Mariscal (área de seguridad, calidad de proceso y trazabilidad).
**Estado:** Borrador de trabajo, listo para migración a LaTeX. **No es** el
documento maestro del informe (`docs/informe/informe-entrega-3.pdf`/`.tex`),
que no se modifica en esta fase.
**Fecha de esta revisión:** 2026-08-17.
**Fuente metodológica previa:** `docs/checklists/ralph2021-engineering-research.md`
(checklist Ralph et al. 2021, estándar *Engineering Research*, ya
completado ítem por ítem antes de este documento).

## Cómo usar este borrador

Este archivo consolida, en prosa académica y en formato Markdown fácilmente
migrable a `.tex`, el material metodológico que hasta ahora estaba disperso
entre `docs/checklists/ralph2021-engineering-research.md`,
`docs/informe/secciones/05-protocolo-experimental.tex` (protocolo de la
Entrega 3, con datos ya desactualizados en varios puntos — se señala
explícitamente dónde) y `docs/informe/secciones/09-amenazas-validez.tex`
(amenazas de la Entrega 3, igualmente reutilizadas pero actualizadas aquí
con la evidencia real más reciente). No duplica esas secciones sin
propósito: donde el contenido de la Entrega 3 sigue siendo válido, se cita
en vez de reescribirse; donde la evidencia cambió, se documenta el valor
actual con su fuente.

> **Nota de actualización (2026-08-17):** tras integrarse desde `main` el
> trabajo de Zaida (`docs/mediciones/sus/REPORT.md`,
> `docs/mediciones/sus/sus-raw.csv`, `scripts/analisis-sus.py`,
> `docs/etica/ETHICS.md`), la muestra SUS se amplió de n=10 a **n=18**
> participantes, con media **74.44/100** e IC 95 % **[63.33, 85.56]**. Este
> documento se reconcilió con esos valores reales; los cambios se limitan a
> actualizar cifras y su interpretación prudente (un tamaño muestral mayor
> reduce el margen del intervalo de confianza, pero no implica por sí solo
> representatividad externa) — ninguna conclusión metodológica de fondo
> cambió por este ajuste.

---

## 1. Enfoque metodológico

BIOPET corresponde, de forma predominante, a una investigación de
**ingeniería orientada al diseño y evaluación de un artefacto de software**
(*Engineering Research*, también denominada *Design Science* en la
literatura de ingeniería de software empírica). El equipo diseñó,
construyó y evaluó empíricamente un sistema real —backend Spring Boot,
frontend Angular e infraestructura Docker— frente a requisitos explícitos
(`docs/requisitos/SRS.md`), usando múltiples fuentes de evidencia técnica
ya recolectada: pruebas automatizadas y cobertura (JaCoCo), rendimiento
(k6), usabilidad (SUS), calidad web automatizada (Lighthouse), y seguridad
(OWASP, ZAP, SpotBugs/Find Security Bugs, auditoría estática de SQL
dinámico).

**Por qué *Engineering Research* es más apropiado que las alternativas
consideradas** (ya justificado con mayor detalle en
`docs/checklists/ralph2021-engineering-research.md`, sección 1, y resumido
aquí para el capítulo de metodología):

- **No es un experimento controlado.** Un experimento controlado exige
  manipular deliberadamente una variable independiente, asignar sujetos a
  grupos de tratamiento/control (idealmente al azar), y contrastar una
  hipótesis formal con un diseño que permita inferir causalidad. Las
  corridas de k6 (`docs/mediciones/perf/REPORT.md`) miden el comportamiento
  del propio sistema bajo dos condiciones observacionales (caché fría y
  caché caliente), no un experimento con grupos comparados ni asignación
  aleatoria; son mediciones de *benchmarking* del artefacto, no un diseño
  experimental.
- **No es un estudio de caso observacional tradicional.** Un estudio de
  caso, en el sentido empírico riguroso, examina un fenómeno ya existente
  dentro de un contexto organizacional real, con múltiples fuentes de
  datos cualitativas recogidas *in situ*. BIOPET es un artefacto construido
  por el propio equipo dentro de un Proyecto Fin de Curso, no un fenómeno
  observado en una veterinaria real ya operando con el sistema.
- **Sí encaja con el patrón de Engineering Research**, cuyos atributos
  esenciales (descripción del artefacto, justificación de su relevancia,
  evaluación conceptual y empírica, discusión de limitaciones) están todos
  presentes y verificados en el checklist ya completado.

**Afirmaciones que este documento evita deliberadamente**, porque la
evidencia real no las sostiene:

- No se afirma que BIOPET sea un **experimento controlado**: no hay grupos
  de control ni asignación aleatoria.
- No se afirma **validación industrial**: toda la evaluación (SUS,
  seguridad, rendimiento) se realizó en un entorno académico local, no con
  usuarios ni profesionales de una organización externa real (ver Amenazas
  a la validez externa y Limitaciones, más abajo).
- No se afirma ninguna forma de **certificación**: en particular, el marco
  ISO/IEC 25010 (`docs/arquitectura/ISO-25010.md`) se usa explícitamente
  como marco de clasificación de calidad, no como una certificación
  obtenida.
- No se afirma **causalidad experimental** en ninguna medición: las
  correlaciones o mejoras observadas entre versiones del sistema se
  describen como observaciones, no como relaciones causales demostradas
  por diseño experimental.
- No se afirma que los resultados sean **universalmente generalizables**:
  cada sección de evaluación indica explícitamente el alcance real de lo
  medido (ver Diseño de evaluación y Amenazas a la validez externa).

---

## 2. Design Science Research — mapeo de las seis actividades (Peffers et al.)

Este mapeo ya fue construido, con la misma evidencia, en
`docs/checklists/ralph2021-engineering-research.md` (sección 6). Se
reproduce aquí en formato de capítulo de metodología, sin alterar ninguna
conclusión, para que quede disponible en un único borrador migrable a
LaTeX.

### 2.1. Identificación del problema y motivación — **Completada**

BIOPET parte de deficiencias concretas señaladas por retroalimentación
docente real a lo largo de tres entregas (Entrega 1A, Entrega 1B, Entrega
3), registradas con evidencia verificable en la bitácora de observaciones.
No se trata de un problema de investigación abstracto: cada observación
cita el criterio de evaluación afectado y el texto literal de la
retroalimentación recibida.

- **Evidencia:** `docs/observaciones/OBSERVACIONES.md` (15 observaciones
  registradas, con código, fuente, texto literal, criterio, y estado
  individual), `docs/requisitos/SRS.md` §1 (propósito y alcance del
  sistema).

### 2.2. Definición de objetivos de una solución — **Completada**

Los objetivos de la solución están expresados como requisitos funcionales
y no funcionales verificables, con trazabilidad explícita hacia historias
de usuario y casos de uso — no como metas vagas.

- **Evidencia:** `docs/requisitos/SRS.md` §3 (requisitos funcionales
  REQ-F y no funcionales REQ-NF), `docs/trazabilidad/matriz.csv` (matriz
  completa requisito↔historia↔caso de uso↔prueba).

### 2.3. Diseño y desarrollo — **Completada**

La arquitectura del sistema está documentada formalmente en tres niveles
del modelo C4 (contexto, contenedores, componentes del backend), y las
decisiones de diseño relevantes están registradas como Architecture
Decision Records, cada uno con alternativas consideradas y justificación
técnica. El desarrollo real (código fuente del backend y del frontend)
existe en el propio repositorio, no como una descripción de intención.

- **Evidencia:** `docs/diagrams/c4-contexto/`, `docs/diagrams/c4-contenedores/`,
  `docs/diagrams/c4-componentes-backend/`, `docs/adr/` (6 ADR:
  `ADR-002-pila-tecnologica.md`, `ADR-003-jwt-redis.md`,
  `ADR-004-postgresql.md`, `ADR-005-despliegue.md`,
  `ADR-006-autenticacion-seguridad.md`, `ADR-007-acceso-datos.md`),
  `Backend/src/`, `frontend/src/`.

### 2.4. Demostración — **Completada**

El sistema completo se levanta de punta a punta con un único comando
reproducible (`make up`, o `docker compose up --build -d` sobre
`docker-compose.yml`), y su comportamiento real se ejerce mediante
colecciones Postman con flujos completos (registro, login, CRUD de
mascotas/vacunas), cargas de k6 contra el backend en ejecución, y un
escaneo ZAP contra el contenedor real del backend — no contra una
simulación ni una maqueta.

- **Evidencia:** `Makefile`, `docker-compose.yml`, `docker-compose.tls.yml`,
  `docs/postman/BIOPET.postman_collection.json`,
  `docs/mediciones/perf/REPORT.md`, `docs/mediciones/sec/zap/README.md`.

### 2.5. Evaluación — **Completada**

La evaluación empírica del artefacto cubre múltiples dimensiones de
calidad con datos reales y reproducibles: cobertura de pruebas (JaCoCo),
rendimiento bajo carga (k6), percepción de usabilidad (SUS), calidad web
automatizada (Lighthouse), y seguridad (OWASP manual + ZAP dinámico +
SpotBugs/Find Security Bugs estático + auditoría de SQL dinámico). El
detalle método por método está en la sección "Diseño de evaluación" de
este mismo documento.

- **Evidencia:** `docs/mediciones/jacoco/`, `docs/mediciones/perf/`,
  `docs/mediciones/sus/`, `docs/mediciones/lighthouse/`,
  `docs/mediciones/sec/`, `scripts/audit-sql-dynamic.sh`,
  `scripts/validate-traceability.sh`.

### 2.6. Comunicación — **Parcialmente completada**

Existe un informe técnico completo en LaTeX (con capítulos de protocolo
experimental y amenazas a la validez), pero corresponde a la **Entrega 3**,
no a la Entrega Final: describe SUS y Lighthouse como mediciones "todavía
no ejecutadas" y cita un umbral de JaCoCo del 60 %, ambos datos ya
superados por la evidencia real actual (SUS y Lighthouse sí se
ejecutaron; el umbral de JaCoCo es ahora 70 %, cumplido con 91.80 %
LINE / 79.39 % BRANCH). La comunicación de los resultados de la Entrega
Final en un informe actualizado **todavía no se ha redactado** — este
mismo borrador es material preparatorio para esa redacción futura, no un
reemplazo de ella.

- **Evidencia (informe existente, desactualizado en los puntos citados):**
  `docs/informe/informe-entrega-3.pdf`, `docs/informe/secciones/01-resumen-ejecutivo.tex`,
  `docs/informe/secciones/05-protocolo-experimental.tex`.
- **Evidencia (bitácora sí actualizada):** `docs/observaciones/OBSERVACIONES.md`.

---

## 3. Goal–Question–Metric (GQM)

Reproducido y ampliado desde `docs/checklists/ralph2021-engineering-research.md`
§7, con la misma disciplina: **ninguna métrica nueva se inventa aquí**;
solo se organizan mediciones que ya existen en el repositorio. Los valores
marcados como *"sujeto a actualización en cierre final"* son evidencia real
y vigente a la fecha de esta revisión, pero dependen de una regeneración
posterior a cargo de Fred (rendimiento/caché) o Zaida (SUS/Lighthouse), por
lo que no se presentan como definitivos.

### Goal

Analizar el artefacto BIOPET (backend Spring Boot + frontend Angular +
infraestructura Docker) **con el propósito de** evaluar **con respecto a**
su calidad funcional, mantenibilidad, rendimiento, usabilidad, calidad web
y seguridad, **desde el punto de vista** del equipo de desarrollo y de
evaluadores académicos, **en el contexto de** un Proyecto Fin de Curso
ejecutado en un entorno de desarrollo local con Docker.

### Questions y Metrics

| Dimensión | Pregunta | Métrica(s) | Valor de evidencia (fecha de esta revisión) | Fuente |
|---|---|---|---|---|
| Calidad funcional | ¿Los requisitos funcionales declarados están respaldados por al menos una historia, caso de uso o prueba automatizada? | Resultado de `scripts/validate-traceability.sh`; # requisitos vs. # filas en matriz | 38 requisitos, 38 filas consistentes | `docs/trazabilidad/matriz.csv`, `scripts/validate-traceability.sh` |
| Calidad funcional | ¿La suite de pruebas automatizadas del backend pasa consistentemente? | Resultado de `mvn clean verify` (pruebas totales, fallos, errores) | 189 pruebas, 0 fallos, 0 errores (evidencia de `docs/mediciones/sec/raw/mvn-clean-verify.txt`) | `docs/mediciones/sec/raw/mvn-clean-verify.txt` |
| Mantenibilidad | ¿Qué proporción del código backend se ejercita por la suite de pruebas? | JaCoCo LINE / BRANCH (%), umbral configurado | LINE 91.80 % / BRANCH 79.39 %, umbral `pom.xml` ≥ 70 % ambos | `docs/mediciones/jacoco/METRICS.md` |
| Mantenibilidad | ¿El proceso de construcción/verificación es reproducible y automatizado? | Existencia y jobs del pipeline de CI | Workflow con jobs `backend-test`, `frontend-build`, `traceability`, `sql-audit`, `security-static`, `zap-baseline` | `.github/workflows/ci.yml` (no modificado en esta fase; solo citado como evidencia) |
| Rendimiento | ¿Cuál es la latencia y tasa de error del endpoint cacheado de mascotas bajo carga moderada? | k6: p50/p90/p95/p99 (ms), tasa de error (%), throughput (req/s) | Ejemplo (corrida 3, caliente): p95 = 10.62 ms, error 0.0 %, ~92.34 req/s — **valor actual de evidencia; sujeto a actualización en cierre final** (mediciones de rendimiento a cargo de Fred) | `docs/mediciones/perf/REPORT.md` |
| Usabilidad | ¿Qué tan usable perciben participantes reales el frontend de BIOPET? | Puntaje medio SUS (0–100), n de participantes | Media 74.44/100, IC95% [63.33, 85.56], n = 18 — **valor actual de evidencia** (actualizado desde `main` con la muestra ampliada de Zaida; ver nota de actualización al inicio de este documento) | `docs/mediciones/sus/REPORT.md` |
| Calidad web | ¿El frontend cumple umbrales base de rendimiento, accesibilidad y buenas prácticas web? | Puntajes Lighthouse (Performance, Accessibility, Best Practices, SEO) | Accessibility 91/100 (cumple ≥90); SEO 82/100 (no cumple ≥90 en la corrida registrada) — **valor actual de evidencia; sujeto a actualización en cierre final** (Lighthouse a cargo de Zaida) | `docs/mediciones/lighthouse/README.md` |
| Seguridad | ¿Existen hallazgos de seguridad de severidad alta detectables por escaneo dinámico? | Alertas ZAP Baseline por `riskcode` | 0 alertas de riesgo alto, 1 informativa | `docs/mediciones/sec/zap/README.md` |
| Seguridad | ¿El análisis estático detecta patrones propensos a inyección SQL u otras vulnerabilidades? | Hallazgos SpotBugs/Find Security Bugs, total y subconjunto `SQL_*` | 66 hallazgos totales, 0 de tipo `SQL_*` | `docs/mediciones/sec/static-analysis/README.md` |
| Seguridad | ¿El SQL dinámico de los procedimientos almacenados se construye de forma segura? | Resultado de `scripts/audit-sql-dynamic.sh` (código de salida, hallazgos) | 0 hallazgos sobre `db/procs/*.sql`, `exit 0` | `scripts/audit-sql-dynamic.sh` (script no modificado en esta fase) |

**Total: 10 preguntas** (2 de calidad funcional, 2 de mantenibilidad, 1 de
rendimiento, 1 de usabilidad, 1 de calidad web, 3 de seguridad), todas
respaldadas por una métrica real ya recolectada.

---

## 4. Diseño de evaluación

Para cada fuente de evidencia empírica: propósito, unidad evaluada,
procedimiento general (tal como está documentado y es reproducible),
métrica principal, evidencia y limitación conocida. Los valores numéricos
se leyeron directamente de los archivos citados, no se recalcularon ni se
estimaron.

### 4.1. Pruebas automatizadas (backend)

- **Propósito:** verificar que el comportamiento funcional del backend
  coincide con lo especificado, de forma repetible en cada cambio.
- **Unidad evaluada:** clases de controlador, servicio y seguridad del
  backend Spring Boot (`com.biopet.**`).
- **Procedimiento:** `cd Backend && mvn clean verify`, que ejecuta JUnit 5
  + MockMvc, incluidas 2 pruebas de integración reales con Testcontainers
  contra PostgreSQL.
- **Métrica principal:** número de pruebas, fallos, errores.
- **Evidencia:** 189 pruebas, 0 fallos, 0 errores
  (`docs/mediciones/sec/raw/mvn-clean-verify.txt`).
- **Limitación:** cubrir una línea/rama con una prueba no implica ausencia
  de defectos lógicos no contemplados por esa prueba (ver Amenazas a la
  validez de constructo).

### 4.2. Cobertura JaCoCo

- **Propósito:** medir qué proporción del código fuente del backend se
  ejercita realmente durante la suite de pruebas, como indicador indirecto
  de mantenibilidad.
- **Unidad evaluada:** todo el módulo `Backend` (regla `BUNDLE`, no por
  paquete ni por clase).
- **Procedimiento:** `jacoco-maven-plugin`, ejecuciones `prepare-agent`
  (fase por defecto), `report` (fase `test`), `check` (fase `verify`);
  regenerado con `mvn clean verify` y archivado con
  `scripts/archive-jacoco-evidence.sh`.
- **Métrica principal:** `COVEREDRATIO` de LINE, BRANCH, COMPLEXITY.
- **Evidencia:** LINE 91.80 %, BRANCH 79.39 %, COMPLEXITY 79.52 %; umbral
  configurado en `Backend/pom.xml` ≥ 0.70 (LINE/BRANCH) y ≥ 0.60
  (COMPLEXITY) (`docs/mediciones/jacoco/METRICS.md`).
- **Limitación:** cobertura de ejecución, no de corrección; una línea
  cubierta puede seguir conteniendo un defecto no ejercitado por la
  aserción de la prueba.

### 4.3. Rendimiento (k6)

- **Propósito:** caracterizar la latencia, el throughput y la tasa de
  error del endpoint cacheado `GET /api/mascotas` bajo carga moderada.
- **Unidad evaluada:** backend en ejecución real (HTTPS/TLS 1.3), sobre
  una única instancia, en una única máquina de desarrollo.
- **Procedimiento:** 3 corridas en frío (caché vacía) y 3 en caliente;
  entre 3 191 y 3 236 peticiones completadas por corrida; intervalos de
  confianza al 95 % calculados con la distribución t de Student.
- **Métrica principal:** p50/p90/p95/p99 (ms), tasa de error (%),
  throughput (req/s).
- **Evidencia:** tabla completa de las 6 corridas en
  `docs/mediciones/perf/REPORT.md`; ejemplo (corrida 3 caliente): p95 =
  10.62 ms, error 0.0 %, throughput ≈ 92.34 req/s.
- **Limitación:** una sola máquina, ventanas de tiempo cortas
  (~30–35 s por corrida); no se midieron tendencias en periodos
  prolongados ni con múltiples instancias concurrentes del backend.

### 4.4. Usabilidad (SUS)

- **Propósito:** medir la percepción subjetiva de usabilidad del frontend
  por parte de participantes reales, con un instrumento estandarizado.
- **Unidad evaluada:** frontend Angular, sobre una tarea de referencia
  (login, alta, edición, eliminación lógica, logout).
- **Procedimiento:** cuestionario SUS de 10 preguntas (Brooke, 1996), sin
  modificar la escala de 5 puntos; consentimiento informado por
  participante antes de la tarea.
- **Métrica principal:** puntaje SUS por participante (0–100) y su media.
- **Evidencia:** n = 18 participantes, media 74.44/100, IC 95 %
  [63.33, 85.56] (categoría "Bueno" en la escala de adjetivos SUS); el
  intervalo incluye el umbral de referencia de 68 puntos (Bangor et al.,
  2008) (`docs/mediciones/sus/REPORT.md`, `docs/mediciones/sus/sus-raw.csv`).
- **Limitación:** SUS mide percepción de usabilidad, no eficacia clínica
  ni operacional real en un contexto veterinario; aunque la muestra creció
  de n=10 a n=18 (por encima del mínimo de 15 exigido para la Entrega
  Final), sigue sin análisis de correlación entre variables demográficas y
  puntaje.

### 4.5. Calidad web automatizada (Lighthouse)

- **Propósito:** auditar de forma automatizada rendimiento, accesibilidad,
  buenas prácticas y SEO del frontend.
- **Unidad evaluada:** rutas `/login` y `/mascotas` del frontend servido
  por el contenedor real (no `ng serve`).
- **Procedimiento:** `npx @lhci/cli autorun` con la configuración de
  `lighthouserc.js`, perfil móvil simulado (throttling por defecto), 3
  corridas por ruta.
- **Métrica principal:** puntaje 0–100 por categoría.
- **Evidencia:** Accessibility 91/100 (cumple umbral ≥90); SEO 82/100 (no
  cumple umbral ≥90 en la corrida registrada) (`docs/mediciones/lighthouse/README.md`).
- **Limitación:** Lighthouse no representa toda la experiencia de usuario
  real; sus puntajes dependen del entorno de ejecución (hardware, red,
  versión del navegador) y no son un valor absoluto reproducible en
  cualquier máquina.

### 4.6. Seguridad dinámica (OWASP ZAP Baseline)

- **Propósito:** detectar vulnerabilidades explotables mediante escaneo
  automatizado contra el backend en ejecución real.
- **Unidad evaluada:** backend Spring Boot, dentro de la red Docker
  Compose real (`http://backend:8080`).
- **Procedimiento:** `zap-baseline.py` (imagen oficial
  `ghcr.io/zaproxy/zaproxy:stable`), spider no autenticado + reglas
  pasivas; reportes HTML/XML/JSON archivados.
- **Métrica principal:** conteo de alertas por `riskcode` (0=Info,
  1=Low, 2=Medium, 3=High).
- **Evidencia:** 0 alertas de riesgo alto, 1 informativa
  (`docs/mediciones/sec/zap/README.md`).
- **Limitación:** ZAP/SpotBugs no prueban la ausencia total de
  vulnerabilidades, solo la ausencia de los patrones que sus reglas
  configuradas pueden detectar; el spider no autenticado no cubrió
  endpoints protegidos por JWT.

### 4.7. Seguridad estática (SpotBugs + Find Security Bugs)

- **Propósito:** detectar patrones de código inseguro (en particular,
  construcción insegura de SQL) mediante análisis estático del bytecode.
- **Unidad evaluada:** clases compiladas del backend (`Backend/target/classes`).
- **Procedimiento:** `mvn com.github.spotbugs:spotbugs-maven-plugin:4.10.3.0:spotbugs`,
  con el plugin Find Security Bugs habilitado, `effort=Max`,
  `threshold=Low` (máxima exhaustividad).
- **Métrica principal:** hallazgos totales, hallazgos de tipo `SQL_*`,
  severidad por hallazgo.
- **Evidencia:** 66 hallazgos totales, 0 de tipo `SQL_*`; único hallazgo
  de severidad alta (`SPRING_CSRF_PROTECTION_DISABLED`) documentado y
  analizado, no oculto (`docs/mediciones/sec/static-analysis/README.md`).
- **Limitación:** análisis estático de bytecode; no detecta
  vulnerabilidades que dependen de configuración en tiempo de ejecución no
  visible en el bytecode (ver el propio análisis del hallazgo CSRF citado
  arriba).

### 4.8. Auditoría de SQL dinámico

- **Propósito:** detectar construcción insegura de SQL dinámico
  (concatenación/interpolación) en procedimientos almacenados de
  PostgreSQL.
- **Unidad evaluada:** todos los archivos `db/procs/*.sql` existentes al
  momento de la ejecución.
- **Procedimiento:** `scripts/audit-sql-dynamic.sh`, que analiza
  sentencias `EXECUTE` de PL/pgSQL y patrones ajenos a PostgreSQL
  (`EXECUTE IMMEDIATE`, `sp_executesql`).
- **Métrica principal:** número de hallazgos, código de salida.
- **Evidencia:** 0 hallazgos, `exit 0`, verificado contra el único SP
  existente (`db/procs/fn_resumen_mascotas_por_especie.sql`).
- **Limitación:** cubre únicamente los archivos existentes en
  `db/procs/` al momento de la ejecución; nuevos procedimientos que Fred
  agregue después se auditarán automáticamente en la siguiente ejecución,
  no retroactivamente.

### 4.9. Trazabilidad de requisitos

- **Propósito:** verificar que todo requisito declarado tenga respaldo en
  al menos una historia de usuario, caso de uso o prueba automatizada.
- **Unidad evaluada:** `docs/requisitos/SRS.md` frente a
  `docs/trazabilidad/matriz.csv`.
- **Procedimiento:** `scripts/validate-traceability.sh` (validación
  cruzada de identificadores).
- **Métrica principal:** número de requisitos vs. número de filas
  consistentes en la matriz.
- **Evidencia:** 38 requisitos del SRS, 38 filas en la matriz, consistentes.
- **Limitación:** la validación confirma consistencia estructural
  (existencia de referencias cruzadas), no la calidad ni la corrección
  semántica de cada historia/caso de uso individual.

---

## 5. Amenazas a la validez

Clasificadas según las cuatro categorías estándar. Cada amenaza incluye
efecto posible, mitigación ya aplicada (si existe) y riesgo residual. Se
reutiliza y actualiza el contenido ya redactado en
`docs/informe/secciones/09-amenazas-validez.tex` (Entrega 3), sin
inventar amenazas nuevas desconectadas de BIOPET.

### 5.1. Validez interna (4 amenazas)

| Amenaza | Efecto posible | Mitigación aplicada | Riesgo residual |
|---|---|---|---|
| Todas las mediciones de seguridad y rendimiento se ejecutaron en **una sola máquina de desarrollo** y contra una única instancia del backend | Interferencia de otros procesos del sistema operativo durante las corridas podría sesgar la latencia medida | Ejecución de 6 corridas de k6 (3 frío + 3 caliente) para observar variabilidad entre corridas, con intervalos de confianza al 95 % reportados por corrida | Alto: sin una segunda máquina o entorno aislado, no se puede descartar interferencia puntual del sistema operativo anfitrión |
| Configuración local dependiente del entorno de desarrollo (certificado TLS autofirmado, imágenes Docker con digest fijado localmente) | Resultados de seguridad/TLS podrían no reproducirse idénticos en otra máquina si el entorno difiere | Imágenes de terceros fijadas por digest `sha256` (no solo tag) en `docker-compose.yml`, para reducir deriva entre reconstrucciones | Medio: el certificado autofirmado en sí sigue siendo específico de este entorno académico, no de un canal de confianza real |
| El *hit ratio* de caché Redis se puede medir con dos métodos que producen cifras distintas (global vs. aislado por clave) | Elegir el método equivocado podría sobreestimar o subestimar el efecto real de la caché | Documentado explícitamente cuál método se usó y por qué (medición aislada por clave, no `INFO stats` global, que mezcla con la verificación de lista negra de JWT) | Bajo: la elección está justificada y es auditable, pero sigue siendo una decisión metodológica del equipo, no una medición neutral única |
| El orden de ejecución de las mediciones (seguridad, luego rendimiento, o viceversa, según el script usado) no está aleatorizado | Un efecto de "calentamiento" del sistema (JVM, conexiones de pool) podría influir en la corrida que se ejecuta después de otra | Las corridas de rendimiento distinguen explícitamente entre "frío" (caché vacía) y "caliente" (caché ya poblada), documentando el efecto del orden en vez de ocultarlo | Bajo: el efecto de orden dentro de rendimiento está controlado por diseño (frío/caliente etiquetado); el efecto de orden entre seguridad y rendimiento no fue estudiado explícitamente |

### 5.2. Validez externa (4 amenazas)

| Amenaza | Efecto posible | Mitigación aplicada | Riesgo residual |
|---|---|---|---|
| Toda la evaluación se realizó en un **entorno académico local**, no en un entorno de producción ni *staging* equivalente | Los resultados de rendimiento y seguridad podrían no extrapolarse a un despliegue en la nube con múltiples usuarios concurrentes reales | Ninguna evaluación se presenta como equivalente a producción; se documenta explícitamente como limitación en cada reporte de mediciones | Alto: sigue sin existir un entorno de *staging*/producción real contra el cual contrastar; **ausencia de despliegue productivo, ver Limitaciones** |
| La muestra SUS (n=18, ampliada desde n=10 de la Tercera Entrega, por encima del mínimo de 15 exigido para la Entrega Final) sigue sin ser necesariamente representativa de usuarios finales de una clínica veterinaria real | Los participantes (reclutados por conveniencia, círculo cercano al equipo según `docs/mediciones/sus/REPORT.md`) podrían tener mayor familiaridad tecnológica que el usuario final típico | Cuestionario aplicado con consentimiento informado y tarea de referencia estandarizada, reduciendo variabilidad en el procedimiento; el tamaño muestral mayor reduce el margen del intervalo de confianza pero no cambia el método de reclutamiento | Alto: sesgo de participantes previsible; **un tamaño muestral mayor no implica representatividad externa, ver Limitaciones** |
| Lighthouse depende del entorno de ejecución (hardware, red, versión del navegador) en que se corre | Los puntajes no son un valor absoluto reproducible en cualquier máquina | Documentado explícitamente que Lighthouse es una medición dependiente del entorno, no un valor universal | Medio: inherente a la herramienta, no mitigable completamente sin estandarizar el hardware de medición |
| Los usuarios de prueba del backend (cuentas académicas, admin sembrado) no son usuarios reales de una clínica veterinaria | El comportamiento del sistema frente a datos de producción reales (volumen, variedad, calidad de datos) no fue evaluado | Cuentas y datos claramente identificados como académicos/sintéticos (dominio `example.test`, `db/seed.sql`) | Alto: no evaluado con datos ni usuarios reales de un contexto veterinario operativo |

### 5.3. Validez de constructo (5 amenazas)

| Amenaza | Efecto posible | Mitigación aplicada | Riesgo residual |
|---|---|---|---|
| JaCoCo mide cobertura de ejecución, no ausencia de defectos | Un alto porcentaje de cobertura podría interpretarse erróneamente como "código libre de errores" | Documentado explícitamente en cada resumen de JaCoCo que la cobertura no implica corrección | Medio: la distinción está documentada, pero el riesgo de mala interpretación por un lector externo persiste si solo lee el número aislado |
| Lighthouse no representa toda la experiencia de usuario real | Un puntaje alto de Accessibility/Performance no garantiza que un usuario real perciba el sistema como accesible o rápido en todos los escenarios | Se complementa con SUS (percepción real de usuarios) como medición independiente de usabilidad | Medio: ambas mediciones son complementarias, pero ninguna sustituye una evaluación de UX cualitativa dedicada |
| SUS mide percepción de usabilidad, no eficacia clínica ni operacional en un contexto veterinario real | Un puntaje SUS alto no implica que el sistema sea efectivo para el flujo de trabajo real de una clínica veterinaria | Se documenta expresamente el alcance del instrumento (percepción subjetiva de facilidad de uso, no desempeño operacional) | Alto: no existe ninguna medición de eficacia operacional real en el repositorio |
| ZAP/SpotBugs no prueban la ausencia total de vulnerabilidades | "0 alertas de riesgo alto" podría malinterpretarse como "sistema sin vulnerabilidades" | Cada reporte de seguridad aclara explícitamente el alcance de lo escaneado (reglas activas, spider no autenticado, `effort`/`threshold` de SpotBugs) | Alto: ninguna herramienta de análisis automatizado garantiza ausencia total de vulnerabilidades; esto es inherente a la técnica, no un defecto de esta evaluación |
| p95/p99 de k6 representan los escenarios efectivamente medidos (carga moderada, endpoint único), no todo patrón de carga posible | Los percentiles reportados no caracterizan el comportamiento del sistema bajo otros endpoints o patrones de tráfico no probados | Se documenta explícitamente qué endpoint y qué patrón de carga se midió (`GET /api/mascotas`, ~50 VUs, ~30–35 s) | Alto: no se midió comportamiento bajo otros endpoints ni patrones de carga (picos, tráfico sostenido prolongado) |

### 5.4. Validez de conclusión (4 amenazas)

| Amenaza | Efecto posible | Mitigación aplicada | Riesgo residual |
|---|---|---|---|
| El tamaño de muestra de cada corrida de k6 (~3 200 peticiones) corresponde a una única ventana de tiempo corta (~30–35 s) | Los intervalos de confianza al 95 % (distribución t de Student) son válidos para esa ventana, pero no capturan tendencias en periodos prolongados (horas/días) | IC 95 % calculado y reportado explícitamente por corrida (`docs/mediciones/perf/REPORT.md`) | Medio: el cálculo estadístico dentro de cada corrida es correcto; la ausencia de mediciones de largo plazo es una limitación de alcance, no un error estadístico |
| El tamaño muestral de SUS (n=18, ampliado desde n=10) sigue limitando la potencia estadística de cualquier comparación entre subgrupos | No es posible detectar de forma confiable diferencias pequeñas entre subgrupos demográficos con este tamaño de muestra | Se reporta la media y el IC 95 % del puntaje agregado (ahora con un margen más estrecho, ± 11.12 frente al valor previo), sin forzar comparaciones entre subgrupos que la muestra no puede sostener | Medio-Alto: n=18 mejora el margen del intervalo de confianza agregado respecto a n=10, pero sigue siendo insuficiente para dividir en subgrupos (edad, experiencia web, dispositivo) con potencia estadística adecuada |
| No existe diseño experimental (sin grupos de control ni asignación aleatoria) en ninguna de las mediciones | Cualquier mejora observada entre versiones del sistema no puede atribuirse causalmente a un cambio específico mediante estas mediciones | Este mismo documento evita explícitamente afirmar causalidad en cualquier resultado reportado | Bajo (por diseño): el riesgo se mitiga evitando la afirmación, no eliminando la limitación metodológica de fondo |
| Las 189 pruebas automatizadas y la cobertura JaCoCo se ejecutan en un único ambiente de prueba (H2 en memoria para la mayoría de pruebas, PostgreSQL real solo en 2 pruebas de integración con Testcontainers) | El comportamiento verificado con H2 podría no ser idéntico al de PostgreSQL real para funciones nativas PL/pgSQL | La función nativa `fn_resumen_mascotas_por_especie` se probó explícitamente con Testcontainers/PostgreSQL real en vez de H2, precisamente por esta amenaza conocida | Bajo para esa función específica; medio para el resto de la suite, que sigue dependiendo de H2 |

---

## 6. Limitaciones del estudio

Distintas de las amenazas a la validez (que describen riesgos
metodológicos sobre la interpretación de resultados ya obtenidos), estas
son limitaciones de alcance del estudio en su conjunto, vigentes a la
fecha de esta revisión:

- **Contexto exclusivamente académico.** BIOPET se desarrolla y evalúa
  como Proyecto Fin de Curso; ninguna medición proviene de un despliegue
  operativo real.
- **Ausencia de evaluación industrial o con usuarios profesionales
  externos.** Ninguna prueba de usabilidad, seguridad o rendimiento
  involucró desarrolladores profesionales externos ni un proyecto de
  código abierto de uso amplio (mismo hallazgo ya documentado como ítem
  D6 del checklist Ralph et al.).
- **Tamaño muestral de SUS todavía moderado (n=18, ampliado desde n=10 de
  la Tercera Entrega).** Supera el mínimo de 15 exigido para la Entrega
  Final y redujo el margen del intervalo de confianza agregado, pero
  sigue siendo insuficiente para análisis de subgrupos con potencia
  estadística adecuada; un tamaño muestral mayor no implica, por sí solo,
  representatividad externa de la muestra (los participantes siguen
  siendo reclutados por conveniencia, ver Amenazas a la validez externa).
- **Entorno de ejecución único**, sin *staging* ni réplica de producción
  para contrastar mediciones de rendimiento y seguridad.
- **Sin comparación con sistemas de gestión veterinaria alternativos.**
  Toda comparación realizada es contra umbrales técnicos propios (JaCoCo,
  Lighthouse, k6, SUS de referencia), no contra artefactos competidores
  reales.
- **Despliegue en producción todavía en cierre.** No existe, a la fecha de
  esta revisión, un entorno productivo desplegado; esto afecta la
  reproducibilidad de cualquier medición futura que dependa de ese
  entorno, por lo que se registra aquí (no como observación administrativa
  aislada, sino porque condiciona directamente qué se puede o no
  reproducir).
- **DOI y archivado permanente todavía en cierre.** Sin un identificador
  citable permanente (Zenodo), la referencia reproducible al artefacto
  sigue dependiendo del propio repositorio Git, lo que también afecta la
  comunicación/reproducibilidad a largo plazo del estudio, no solo un
  trámite administrativo.

No se listan aquí pendientes puramente administrativos (por ejemplo,
asignación de roles CRediT en `CONTRIBUTORS.md`) que no afectan la
reproducibilidad ni la comunicación científica del estudio; esos quedan
fuera del alcance de esta sección, conforme a la instrucción de esta
fase.

---

## 7. Paquete de replicación

Sin crear releases ni archivar en Zenodo (fuera del alcance de esta fase),
se identifica aquí qué elementos ya existentes en el repositorio
constituirían el paquete de replicación del estudio, y qué falta para
completarlo de cara a v1.0.0.

### 7.1. Disponible actualmente

| Elemento | Ubicación | Nota |
|---|---|---|
| Código fuente completo (backend + frontend) | `Backend/src/`, `frontend/src/` | Versionado en Git, sin partes faltantes conocidas |
| Infraestructura reproducible | `docker-compose.yml`, `docker-compose.tls.yml`, `Makefile` | Arranque de punta a punta con un comando, imágenes de terceros fijadas por digest |
| Scripts de medición y auditoría | `scripts/` (`archive-jacoco-evidence.sh`, `run-zap-baseline.sh`, `audit-sql-dynamic.sh`, `validate-traceability.sh`, `perf-analysis.py`, `analisis-sus.py`, `run-lighthouse.sh`, `security-evidence.sh`/`.ps1`, `check-spotbugs-sql-findings.sh`, `check-zap-high-severity.sh`) | Todos versionados; permiten regenerar la evidencia, no solo consultarla |
| Datos crudos de mediciones | `docs/mediciones/perf/k6-run*.json`, `docs/mediciones/sus/sus-raw.csv`, `docs/mediciones/jacoco/jacoco.xml`, `docs/mediciones/sec/static-analysis/spotbugs-report.xml`, `docs/mediciones/sec/zap/zap-baseline-report.{html,xml,json}` | Datos crudos, no solo resúmenes agregados |
| Documentación de requisitos y trazabilidad | `docs/requisitos/`, `docs/trazabilidad/matriz.csv` | Completa y validada automáticamente |
| Documentación de arquitectura | `docs/adr/`, `docs/diagrams/`, `docs/arquitectura/ISO-25010.md` | Decisiones y diagramas versionados |
| Bitácora de observaciones | `docs/observaciones/OBSERVACIONES.md` | Historial completo de retroalimentación y su resolución, con evidencia |
| Checklist metodológico | `docs/checklists/ralph2021-engineering-research.md` | Este mismo borrador se apoya en él |
| Pipeline de integración continua | `.github/workflows/ci.yml` | Reproduce en CI varias de las verificaciones citadas arriba (no modificado en esta fase; solo citado como evidencia) |
| Configuración externalizada | `.env.example` | Variables de entorno documentadas, sin secretos reales |

### 7.2. Pendiente para v1.0.0

- **Informe académico final actualizado**, que incorpore los valores
  reales ya recolectados (JaCoCo 70 %, SUS, Lighthouse, ZAP, SpotBugs) en
  reemplazo de las cifras/estado de la Entrega 3.
- **DOI de archivado permanente (Zenodo).** No se inventa ni se reserva
  aquí; sigue pendiente como observación ya registrada (OBS-10).
- **Entorno de producción o *staging*** contra el cual repetir las
  mediciones de rendimiento y seguridad fuera del entorno académico local.
- **Actualización de mediciones a cargo de Fred (rendimiento/caché) y
  Zaida (SUS/Lighthouse)** si se regeneran las corridas de k6/Lighthouse
  en el cierre final. La muestra SUS ya se amplió de n=10 a n=18 (cambio
  integrado desde `main` y reflejado en este borrador); este documento
  cita el valor de evidencia vigente hoy (2026-08-17), no necesariamente
  el valor definitivo de cierre.
