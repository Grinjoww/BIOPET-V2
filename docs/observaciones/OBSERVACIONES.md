# Bitácora de observaciones — Entregas 1A y 1B

> Las observaciones registradas en este documento provienen **exclusivamente**
> de las dos capturas oficiales de retroalimentación publicadas por el docente
> en el aula virtual SGA (Entrega 1A, Semana 5; Entrega 1B, Semana 6). No
> fueron reconstruidas, inferidas ni adivinadas a partir del historial de Git:
> el historial de Git se usa aquí únicamente para **verificar** si cada
> observación fue corregida, no para originarlas. Las capturas PNG son la
> fuente primaria visual; las transcripciones en `docs/observaciones/fuentes/`
> son un apoyo textual para facilitar la lectura y no reemplazan a la imagen.

## 3.1 Identificación del documento

- **Proyecto:** BIOPET — Sistema Integral de Gestión Veterinaria.
- **Equipo:** BMT (Beltrán · Mariscal · Taipe).
- **Integrantes:** Beltrán Montiel Fred Adrián · Mariscal Cabrera Jaime Josué · Taipe Mora Zaida Melissa.
- **Entregas cubiertas:** Entrega 1A (Semana 5) y Entrega 1B (Semana 6).
- **Procedencia de la evidencia:** Aula virtual SGA (capturas de notificación de retroalimentación del docente, Dr. Gleiston Cicerón Guerrero Ulloa, Ph.D.).
- **Fecha de incorporación de la evidencia al repositorio:** 2026-07-31.
- **Rama donde se construyó esta bitácora:** `jaime/observaciones-1a-1b` (creación inicial); cierre de OBS-02, OBS-03, OBS-04 y OBS-05 realizado en `jaime/cierre-observaciones-1a-1b`; cierre de OBS-08 (verificación del tag `v0.1.0-entrega-1b`) realizado en `jaime/cierre-obs-08`.

## 3.2 Fuentes primarias

| Fuente | Entrega | Procedencia | Evidencia | SHA-256 |
|---|---|---|---|---|
| Captura de retroalimentación (PNG) | Entrega 1A, Semana 5 | Aula virtual SGA | [`evidencias/SGA-retroalimentacion-entrega-1A.png`](evidencias/SGA-retroalimentacion-entrega-1A.png) | `f66c9b08c7ea571bd6af825b9eb9fabc95ef0ef25d7aaa5d842bc92f11c07b7e` |
| Captura de retroalimentación (PNG) | Entrega 1B, Semana 6 | Aula virtual SGA | [`evidencias/SGA-retroalimentacion-entrega-1B.png`](evidencias/SGA-retroalimentacion-entrega-1B.png) | `1c13a5fcc2b155bcca67378b96a23575b5ef64923a6b14cdf980fcccd5d15ea7` |
| Transcripción literal (TXT) | Entrega 1A, Semana 5 | Elaborada a partir de la captura SGA | [`fuentes/SGA-retroalimentacion-entrega-1A.txt`](fuentes/SGA-retroalimentacion-entrega-1A.txt) | — (texto derivado, no es la fuente primaria) |
| Transcripción literal (TXT) | Entrega 1B, Semana 6 | Elaborada a partir de la captura SGA | [`fuentes/SGA-retroalimentacion-entrega-1B.txt`](fuentes/SGA-retroalimentacion-entrega-1B.txt) | — (texto derivado, no es la fuente primaria) |
| Exportación DER (PNG) | Entrega 1B, Semana 6 (cierre en esta rama) | pgAdmin 4, ERD Tool | [`evidencias/DER-BIOPET-pgAdmin-ERD-Tool.png`](evidencias/DER-BIOPET-pgAdmin-ERD-Tool.png) | `ab1e739b4d78c43bac7423e225ea71354fa29ea9b430471d3c18951c2464f57e` |

**Metadatos técnicos de las capturas** (verificados con `Get-FileHash -Algorithm SHA256` y lectura de cabecera PNG):

| Archivo | Tamaño | Dimensiones | SHA-256 |
|---|---|---|---|
| `SGA-retroalimentacion-entrega-1A.png` | 29 447 bytes | 909 × 380 px | `f66c9b08c7ea571bd6af825b9eb9fabc95ef0ef25d7aaa5d842bc92f11c07b7e` |
| `SGA-retroalimentacion-entrega-1B.png` | 42 487 bytes | 909 × 561 px | `1c13a5fcc2b155bcca67378b96a23575b5ef64923a6b14cdf980fcccd5d15ea7` |
| `DER-BIOPET-pgAdmin-ERD-Tool.png` | 47 196 bytes | 768 × 883 px | `ab1e739b4d78c43bac7423e225ea71354fa29ea9b430471d3c18951c2464f57e` |

Ambas imágenes se verificaron visualmente (lectura directa del PNG) contra las
transcripciones suministradas. El contenido coincide palabra por palabra, con
una única diferencia registrada en ambos archivos de fuente: las líneas
"Inicio de entrega" / "Vencimiento" de la transcripción no son visibles dentro
del recuadro de notificación capturado en el PNG (ver "Nota de verificación"
en cada `.txt`). Ninguna otra discrepancia fue encontrada.

## 3.3 Resumen de calificaciones anteriores

- **Entrega 1A:** 88.2/100, equivalente a 8.8/10.
- **Entrega 1B:** 95.00/100, equivalente a 9.50/10.

Estas notas se transcriben tal como aparecen en las capturas del SGA. No se
recalculan ni se reinterpreta la rúbrica del docente.

## 3.4 Resumen de observaciones

| Código | Entrega | Observación | Criterio original | Responsable recomendado | Estado actual | Commit de cierre |
|---|---|---|---|---|---|---|
| OBS-01 | 1A, Semana 5 | Repositorio no proporcionado o inaccesible (portada vacía, sección 9 sin URL) | A. Formato e identidad institucional / J. Repositorio Git | Jaime Mariscal (gestión del repositorio) | CERRADA | (ver evidencia en el bloque OBS-01; no hay commit único, ver justificación) |
| OBS-02 | 1A, Semana 5 | Falta RF-07 en la lista consolidada (salta RF-06 → RF-08) | D1. Completitud y consistencia del conjunto | Jaime Mariscal (requisitos) | CERRADA | Commit de cierre: pendiente, commit de esta rama |
| OBS-03 | 1A, Semana 5 | RF-WEB remapeados a RF-16/RF-17 sin matriz de trazabilidad explícita | D1. Completitud y consistencia del conjunto | Jaime Mariscal (requisitos) | CERRADA | Commit de cierre: pendiente, commit de esta rama |
| OBS-04 | 1A, Semana 5 | Ambigüedad leve en RF-10 ("recomendaciones informativas") | D3. No ambigüedad y singularidad | Jaime Mariscal (requisitos) | CERRADA | Commit de cierre: pendiente, commit de esta rama |
| OBS-05 | 1B, Semana 6 | DER entregado como `.dot`, no como exportación PNG de pgAdmin | C1. Diagramas UML, DER y diccionario | Jaime Mariscal (evidencia incorporada) | CERRADA | Commit de cierre: pendiente, commit de esta rama |
| OBS-06 | 1B, Semana 6 | Colección Postman no versionada (.json) | C5. Pruebas JUnit, Postman y métricas | Zaida Taipe / Jaime Mariscal (Postman) | CERRADA | `39a40a9`, `dcf8e16` |
| OBS-07 | 1B, Semana 6 | Workflow CI ubicado en `./workflows/ci.yml` en vez de `.github/workflows/` | C6. Docker Compose e integración | Jaime Mariscal (CI/CD) | CERRADA | `eef268c` (PR #37) |
| OBS-08 | 1B, Semana 6 | Tag `v0.1.0-entrega-1b` exigido no fue creado | C7. Repositorio Git | Jaime Mariscal (gestión del repositorio) | CERRADA | Tag anotado `v0.1.0-entrega-1b` → commit `058b1fe` |
| OBS-09 | Entrega 3 | Tag `v0.9.0-rc` no creado (última etiqueta citada, `v0.7.1`, tampoco existía en el checkout original) | C7. Repositorio Git | Jaime Mariscal (gestión del repositorio) | CERRADA | Tag anotado `v0.9.0-rc` (publicado en `origin`) → commit `ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3` |
| OBS-10 | Entrega 3 | Software no archivado en Zenodo, DOI pendiente | E.2. Archivado/citabilidad | Equipo (por definir) | ABIERTA | PENDIENTE |
| OBS-11 | Entrega 3 | Evidencia/reporte Lighthouse faltante | Calidad web automatizada | Jaime Mariscal (mediciones) | CERRADA | `9ea0ccb0b3f2a86996d2aa047f385dff7e5c1675` (verificado con `git show`, 34 archivos añadidos) |
| OBS-12 | Entrega 3 | Calidad del sistema no enmarcada en ISO/IEC 25010 | Marco de calidad del informe | Jaime Mariscal | CERRADA | `docs/arquitectura/ISO-25010.md` (nuevo); commit de esta corrección: PENDIENTE |
| OBS-13 | Entrega 3 | `CONTRIBUTORS.md` sin roles CRediT individuales | E. Reconocimiento de autoría | Equipo completo | CERRADA | `CONTRIBUTORS.md` — matriz CRediT individualizada por persona con evidencia real |
| OBS-14 | Entrega 3 | Documentación afirma ausencia de historial Git pese a 270 commits | C7. Repositorio Git | Jaime Mariscal (verificación) / Equipo (corrección) | CERRADA | `CONTRIBUTORS.md` — afirmación falsa eliminada, reemplazada por referencia real al historial Git |
| OBS-15 | Entrega 3 | Correo institucional de Jaime en commits | C7. Repositorio Git | Jaime Mariscal | CERRADA | `479961e`, `6658892`, `53d4146`, entre otros (verificados con `git log`, autor `jmariscalc@uteq.edu.ec`) |

Ver Parte 5 para el detalle completo de OBS-09 a OBS-15 (fuente: retroalimentación oficial de la Entrega 3, calificación individual de Jaime 7.80/10).

---

# Parte 4 — Observaciones oficiales

## OBS-01 — Repositorio no proporcionado o inaccesible

- **Código:** OBS-01
- **Título:** Repositorio no proporcionado o inaccesible
- **Entrega:** Entrega 1A
- **Semana:** Semana 5
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1A
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1A.png`](evidencias/SGA-retroalimentacion-entrega-1A.png)
- **Texto literal:**
  > "Repositorio: NO PROPORCIONADO (portada vacía; sección 9 sin URL). [NO VERIFICABLE — JirachinG19Stdio/APP-WEB-PFC- devuelve 404]"

  > "CRÍTICO: sin URL de repositorio (entregable obligatorio)."
- **Criterio relacionado:** A. Formato e identidad institucional (peso 3, logrado 70 % = 2.1 pts, penalizado por "URL repo vacía") y J. Repositorio Git (peso 8, logrado 0 %). Es una única deficiencia con impacto en dos criterios de la rúbrica; no se abren dos observaciones distintas para la portada vacía y para la URL inexistente, conforme a la instrucción original.
- **Impacto señalado por el docente:** "Con una URL válida y accesible se re-evalúa el criterio J y la nota puede subir hasta ~96/100."
- **Decisión del equipo:** Decisión inferida a partir de la implementación posterior — no hay ninguna acta ni commit que registre explícitamente "decidimos publicar la URL del repositorio"; se infiere de que, ya en la Entrega 1B (la entrega inmediatamente posterior), el equipo entregó y documentó una URL de repositorio accesible.
- **Nota metodológica sobre este bloque:** la observación en sí ya está acreditada de forma independiente por la captura oficial del SGA (texto literal arriba); lo que este bloque evalúa es **exclusivamente si la deficiencia fue corregida posteriormente**, no si la observación ocurrió.
- **Corrección realizada:** Sí, verificable en dos pasos independientes:
  1. **Por la propia Entrega 1B** (fuente primaria, no inferencia de Git): la captura oficial de retroalimentación de la Entrega 1B (`docs/observaciones/fuentes/SGA-retroalimentacion-entrega-1B.txt`) declara explícitamente `Repositorio: github.com/JirachinG19Stdio/PFC--VET-ENTR1B` y afirma *"La calificacion se basa en el codigo verificado directamente en el repositorio, no en las capturas del informe."* Esto es la evidencia más fuerte posible de que la deficiencia no se repitió: el propio docente confirma que accedió y calificó directamente sobre el código de un repositorio con URL válida, una sola entrega después de señalar su ausencia.
  2. **Por el repositorio actual** (Tercera Entrega, `PFC-VET-ENTR3-v0.9.0-rc`): mantiene una URL pública accesible desde su primer commit, con README documentado y con historial de commits visible de los tres integrantes.
- **Archivos involucrados:** `README.md` (contiene `git clone https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc.git` desde el commit inicial); `docs/observaciones/fuentes/SGA-retroalimentacion-entrega-1B.txt` (evidencia de que la Entrega 1B ya tenía URL).
- **Evidencia actual:**
  ```
  git remote -v
  origin  https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc.git (fetch)
  origin  https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc.git (push)

  git log --all --diff-filter=A --oneline -- README.md
  01355e2 Initial commit

  grep -n "github.com/JirachinG19Stdio" README.md
  44:git clone https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc.git

  git shortlog -sne --all
     58  Jaime Mariscal <mariscaljaime34@gmail.com>
     52  Zaida-tm18 <ztaipem@uteq.edu.ec>
     50  Fred Beltran <fbeltranm@uteq.edu.ec>
     33  Jaime Josué Mariscal Cabrera <mariscaljaime34@gmail.com>
  ```
  Los tres integrantes tienen historial de commits visible y sustancial en el repositorio actual (Jaime bajo dos identidades de Git suma 91 commits, Zaida 52, Fred 50), lo que confirma que el repositorio sucesor está activo, versionado y accesible para los tres autores, no solo para uno.
- **Commit o commits:** No se cita un commit único de "corrección", porque la deficiencia no se resolvió mediante una edición puntual dentro de un mismo repositorio, sino porque el repositorio provisto para la entrega siguiente (1B) ya tenía URL válida desde su origen. La evidencia de cierre es el propio texto de la retroalimentación de la Entrega 1B (fuente primaria SGA) más el estado continuo y verificable del repositorio actual (`git remote -v`, `README.md`, `git shortlog`).
- **Responsable:** Jaime Mariscal (gestión del repositorio y README).
- **Estado:** CERRADA
- **Justificación del estado:** Se distingue explícitamente la verificación de la observación original (ya acreditada por la captura del SGA de la Entrega 1A, independiente de este análisis) de la verificación de su corrección posterior (objeto de este bloque). La corrección posterior sí es verificable, y por partida doble: (a) la propia fuente primaria del SGA para la Entrega 1B confirma una URL de repositorio válida y usada directamente por el docente para calificar, es decir, la deficiencia no se repitió ni una entrega después; y (b) el repositorio actual de la Tercera Entrega mantiene una URL pública accesible, documentada en el README desde el primer commit, con evidencia Git suficiente (historial completo y visible de los tres integrantes). No se afirma que el repositorio actual reconstruya retroactivamente la portada exacta de la Entrega 1A; se afirma, con evidencia primaria y de Git, que la deficiencia fue corregida completamente a partir de la entrega siguiente y no ha vuelto a ocurrir.

---

## OBS-02 — Ausencia de RF-07 en la lista consolidada

- **Código:** OBS-02
- **Título:** Ausencia de RF-07 en la lista consolidada de requisitos
- **Entrega:** Entrega 1A
- **Semana:** Semana 5
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1A
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1A.png`](evidencias/SGA-retroalimentacion-entrega-1A.png)
- **Texto literal:**
  > "FALTA RF-07 en la lista consolidada (salta RF-06 -> RF-08)"
- **Criterio relacionado:** D1 — Completitud y consistencia del conjunto (dentro del bloque D. Ingeniería de requisitos ISO/IEC/IEEE 29148, peso 6/24, logrado 78 % = 4.7 pts).
- **Impacto señalado por el docente:** Contribuyó, junto con OBS-03, a que D1 fuera el subcriterio más bajo del bloque D (78 % frente a 88-95 % de los demás subcriterios).
- **Decisión del equipo:** Investigar el origen exacto de RF-07 antes de reconstruirlo, para no inventar contenido. Se encontró que `docs/diagrams/c4-contexto/C4-L1-contexto.md` (documento ya existente en el repositorio, no creado para este cierre) ya identificaba explícitamente a RF-07 como el "Servicio de Correos" de la Entrega 1A: *"Servicio de Correos → mencionado en la Entrega 1A (RF-07 original), sin requisito REQ-F formal propio todavía en el SRS de la Tercera Entrega."* Con esa base verificable, se decidió formalizarlo como requisito `REQ-F-022` (no `REQ-F-007`, ya ocupado por una funcionalidad distinta, "Consulta del perfil propio", para no duplicar identificadores).
- **Corrección realizada:** Se agregó `REQ-F-022` ("Notificaciones al usuario por correo electrónico") a `docs/requisitos/SRS.md`, con enunciado en patrón "El sistema deberá...", entradas, resultado esperado, tres criterios de aceptación verificables, rationale citando expresamente RF-07 y `C4-L1-contexto.md`, y trazabilidad hacia una historia y un caso de uso nuevos (`HU-021` en `docs/requisitos/historias/HistoriasUsuario.md`, `CU-21` en `docs/requisitos/casos-de-uso/CasosDeUso.md`), ambos también agregados en este cierre. Prioridad `Could`, estado `pendiente` (no implementado; es una recuperación formal del requisito, no una implementación de código). Se agregó una entrada en `docs/requisitos/CHANGELOG-REQ.md` documentando el cierre, y la fila `REQ-F-022,funcional,Could,HU-021,CU-21,,,,,,pendiente` en `docs/trazabilidad/matriz.csv`, cerrando el ciclo completo de trazabilidad.
- **Archivos involucrados:** `docs/requisitos/SRS.md`, `docs/requisitos/historias/HistoriasUsuario.md`, `docs/requisitos/casos-de-uso/CasosDeUso.md`, `docs/requisitos/CHANGELOG-REQ.md`, `docs/trazabilidad/matriz.csv`.
- **Evidencia actual:**
  ```
  grep -n "RF-07" docs/requisitos/SRS.md
  394: (nota de cierre) ... corresponde a RF-07 ...
  398: | REQ-F-022 | Enviar notificaciones al usuario por correo electrónico ... | Could | RF-07 | HU-021 / CU-21 | pendiente |
  ~505-509: bloque completo REQ-F-022 con Rationale citando RF-07 y C4-L1-contexto.md

  grep -n "^## HU-021" docs/requisitos/historias/HistoriasUsuario.md
  (presente)

  grep -n "^## CU-21" docs/requisitos/casos-de-uso/CasosDeUso.md
  (presente)

  grep -n "^REQ-F-022," docs/trazabilidad/matriz.csv
  REQ-F-022,funcional,Could,HU-021,CU-21,,,,,,pendiente

  bash scripts/validate-traceability.sh
  VALIDACION OK: 35 requisitos del SRS, 35 filas en matriz.csv, 21 historias
  y 21 casos de uso consistentes entre sí.
  ```
- **Commit o commits:** Commit de cierre: pendiente, commit de esta rama (`jaime/cierre-observaciones-1a-1b`). No se inventa un hash porque el cambio aún no está commiteado en el momento de escribir este documento.
- **Responsable:** Jaime Mariscal (cierre de requisitos en esta rama); Zaida Taipe permanece como responsable original de la consolidación de requisitos.
- **Estado:** CERRADA
- **Justificación del estado:** RF-07 ya no es un vacío sin explicar: se identificó con evidencia verificable ya presente en el repositorio (`C4-L1-contexto.md`), se formalizó como `REQ-F-022` sin duplicar `REQ-F-007`, y se le dotó de enunciado, criterio de aceptación verificable y trazabilidad completa de extremo a extremo (SRS → HU-021 → CU-21 → `matriz.csv`), verificada además por `scripts/validate-traceability.sh` (VALIDACION OK, sin errores).

---

## OBS-03 — Remapeo de RF-WEB sin matriz explícita

- **Código:** OBS-03
- **Título:** RF-WEB remapeados a RF-16/RF-17 sin matriz de trazabilidad explícita
- **Entrega:** Entrega 1A
- **Semana:** Semana 5
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1A
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1A.png`](evidencias/SGA-retroalimentacion-entrega-1A.png)
- **Texto literal:**
  > "los RF-WEB se remapean a RF-16/RF-17 sin matriz de trazabilidad explícita en esta entrega"
- **Criterio relacionado:** D1 — Completitud y consistencia del conjunto (peso 6/24, logrado 78 % = 4.7 pts, compartido con OBS-02).
- **Impacto señalado por el docente:** Mismo subcriterio D1 citado en OBS-02; el docente lo describe como "defecto del conjunto" (9.4.3 completitud/consistencia).
- **Decisión del equipo:** Consolidar en una tabla explícita, dentro del propio SRS, el vínculo que hasta ahora solo existía disperso en el campo Rationale de cada requisito individual, sin crear un archivo nuevo fuera del alcance autorizado (`docs/requisitos/`, `docs/observaciones/`, `docs/informe/`).
- **Corrección realizada:** Se agregó la sección **"4.1. Trazabilidad histórica: identificadores originales → identificadores actuales (cierre de OBS-03)"** en `docs/requisitos/SRS.md`, con una tabla estructurada de columnas exactamente `Identificador anterior (Entrega 1A) | Identificador actual | Descripción | Caso de uso | Historia | Estado`, que cubre los 16 requisitos funcionales con origen en la Entrega 1A (incluyendo explícitamente `RF-16/RF-WEB-01 → REQ-F-003`, `RF-17/RF-WEB-04 → REQ-F-005` y `RF-13/RF-WEB-02 → REQ-F-006`, los tres casos `RF-WEB` que la observación señalaba) y declara aparte los cinco requisitos sin origen en la Entrega 1A (para no sugerir un origen histórico inexistente). También incorpora la fila `RF-07 → REQ-F-022`, cerrando en el mismo lugar OBS-02 y OBS-03.
- **Archivos involucrados:** `docs/requisitos/SRS.md` (nueva sección 4.1; también líneas 222, 246, 259 con las citas narrativas originales, que se conservan sin cambios), `docs/requisitos/CHANGELOG-REQ.md`, `docs/trazabilidad/matriz.csv`.
- **Evidencia actual:**
  ```
  grep -n "^### 4.1" docs/requisitos/SRS.md
  (presente: "4.1. Trazabilidad histórica: identificadores originales...")

  grep -n "RF-WEB" docs/requisitos/SRS.md
  (aparece en la nueva tabla 4.1 y en las citas Rationale ya existentes)

  grep -E "^REQ-F-(003|005|006)," docs/trazabilidad/matriz.csv
  REQ-F-003,funcional,Must,HU-002,CU-02,...   (RF-16/RF-WEB-01)
  REQ-F-005,funcional,Must,HU-004,CU-04,...   (RF-17/RF-WEB-04)
  REQ-F-006,funcional,Must,HU-005,CU-05,...   (RF-13/RF-WEB-02)
  → las tres filas ya existían en matriz.csv con la HU/CU correctas y
    coinciden exactamente con la sección 4.1 del SRS; no requirieron cambio.

  bash scripts/validate-traceability.sh
  VALIDACION OK: 35 requisitos del SRS, 35 filas en matriz.csv, 21 historias
  y 21 casos de uso consistentes entre sí.
  ```
- **Commit o commits:** Commit de cierre: pendiente, commit de esta rama (`jaime/cierre-observaciones-1a-1b`).
- **Responsable:** Jaime Mariscal (cierre en esta rama); Zaida Taipe permanece como responsable original de la consolidación de requisitos (`a1f83a1`).
- **Estado:** CERRADA
- **Justificación del estado:** La observación pedía literalmente "una tabla de trazabilidad con: identificador anterior, identificador actual, descripción, caso de uso, historia y estado"; esa tabla ahora existe, explícita y estructurada, dentro del SRS, y cubre no solo `RF-16/RF-17` sino la totalidad de los requisitos funcionales con origen en la Entrega 1A. Las tres filas correspondientes a `RF-WEB-01/02/04` en `docs/trazabilidad/matriz.csv` (`REQ-F-003`, `REQ-F-005`, `REQ-F-006`) ya tenían la historia y el caso de uso correctos, verificados contra la sección 4.1 del SRS; y la fila que faltaba en la matriz (`REQ-F-022`, cierre de OBS-02) se agregó en este mismo cierre. `scripts/validate-traceability.sh` confirma la consistencia completa (VALIDACION OK, sin errores).

---

## OBS-04 — Ambigüedad leve en RF-10

- **Código:** OBS-04
- **Título:** Ambigüedad leve en la redacción de RF-10 ("recomendaciones informativas")
- **Entrega:** Entrega 1A
- **Semana:** Semana 5
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1A
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1A.png`](evidencias/SGA-retroalimentacion-entrega-1A.png)
- **Texto literal:**
  > "leve 'recomendaciones informativas' (RF-10)"
- **Criterio relacionado:** D3 — No ambigüedad y singularidad (peso 6/24, logrado 88 % = 5.3 pts — el subcriterio D3 en general fue bueno; el docente lo señala explícitamente como una observación **leve**, no como un defecto grave).
- **Impacto señalado por el docente:** Ninguno cuantificado aparte; D3 obtuvo 88 %, el segundo mejor subcriterio de D. El propio texto la califica de "leve", por lo que no se exagera su gravedad en este registro.
- **Decisión del equipo:** Reformular el enunciado de `REQ-F-017` en patrón "El sistema deberá..." con entradas, resultado esperado y criterios de aceptación verificables, sin ampliar el alcance original (sigue `Could`, sigue `pendiente`, sigue dependiendo de `REQ-F-013`).
- **Corrección realizada:** Se reemplazó la fila de una sola línea de `REQ-F-017` en `docs/requisitos/SRS.md` por un bloque completo (Tipo, Prioridad, Enunciado, Entradas, Resultado esperado, tres Criterios de aceptación verificables, Rationale, Verificación, Trazabilidad, Estado), siguiendo exactamente el mismo formato que ya usan los requisitos implementados (`REQ-F-001` a `REQ-F-012`). El nuevo enunciado es: *"Al recibir una solicitud de recomendaciones para una mascota con historial clínico registrado, el sistema deberá generar, mediante un servicio de IA externo, una lista de recomendaciones de cuidado en texto (...) y deberá devolver cada recomendación acompañada de la advertencia explícita 'informativa, no sustituye diagnóstico veterinario'."* Los tres criterios de aceptación cubren el caso con historial clínico, el caso sin historial clínico (lista vacía, no error) y la aclaración de que el proveedor de IA es una decisión de arquitectura pendiente. No se agregó ninguna funcionalidad nueva: sigue siendo prioridad `Could`, estado `pendiente`, dependiente de `REQ-F-013` (historial clínico, no implementado).
- **Archivos involucrados:** `docs/requisitos/SRS.md`, `docs/requisitos/CHANGELOG-REQ.md`.
- **Evidencia actual:**
  ```
  grep -n "REQ-F-017" docs/requisitos/SRS.md
  394: (fila-resumen, remite al bloque detallado)
  ~455-480: bloque completo "REQ-F-017 — Generación de recomendaciones
             clínicas informativas a partir del historial médico" con
             Enunciado, Entradas, Resultado esperado y 3 Criterios de
             aceptación verificables.
  ```
- **Commit o commits:** Commit de cierre: pendiente, commit de esta rama (`jaime/cierre-observaciones-1a-1b`).
- **Responsable:** Jaime Mariscal (cierre en esta rama); Zaida Taipe permanece como responsable original de la consolidación de requisitos.
- **Estado:** CERRADA
- **Justificación del estado:** La observación era explícitamente "leve" y pedía eliminar la ambigüedad de la redacción, no implementar la funcionalidad. El nuevo enunciado usa "El sistema deberá...", especifica entradas, resultado esperado y tres criterios de aceptación verificables, cumpliendo las características INCOSE de *Unambiguous* y *Verifiable* sobre el enunciado, sin fingir que el requisito ya está implementado (permanece `pendiente`).

---

## OBS-05 — DER no exportado desde pgAdmin como PNG

- **Código:** OBS-05
- **Título:** El DER se entrega como `.dot`, no como exportación PNG de pgAdmin
- **Entrega:** Entrega 1B
- **Semana:** Semana 6
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1B
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1B.png`](evidencias/SGA-retroalimentacion-entrega-1B.png)
- **Texto literal:**
  > "El DER se entrega como .dot, no como exportacion PNG de pgAdmin."

  > "Exportar el DER desde pgAdmin 4 (ERD Tool) como PNG de alta resolucion para el informe final."
- **Criterio relacionado:** C1 — Diagramas UML, DER y diccionario (peso 10 %, logrado 90 % = "Bueno").
- **Impacto señalado por el docente:** Explica por qué C1 quedó en "Bueno (90 %)" en vez de "Excelente".
- **Decisión del equipo:** Incorporar la exportación real solicitada por el docente y documentarla explícitamente, sin eliminar ni reemplazar el diagrama Graphviz existente (ambos artefactos cumplen propósitos distintos: uno es una vista de diseño mantenida a mano, el otro es evidencia de que el esquema real de PostgreSQL fue modelado en pgAdmin).
- **Corrección realizada:** Jaime incorporó manualmente el archivo
  `docs/observaciones/evidencias/DER-BIOPET-pgAdmin-ERD-Tool.png`, exportado
  desde **pgAdmin 4, herramienta ERD Tool**. Se verificó su validez técnica
  antes de documentarlo:
  ```
  firma de archivo PNG (8 primeros bytes): 89 50 4E 47 0D 0A 1A 0A → válida
  tamaño: 47196 bytes
  dimensiones (chunk IHDR): 768 × 883 px
  ```
  Se documentó en `docs/requisitos/SRS.md` (sección 5, "Modelo de datos"),
  distinguiendo explícitamente los dos artefactos DER: `der-biopet.png`
  (renderizado desde `der-biopet.dot`, Graphviz — es un diagrama dibujado,
  no una exportación de una herramienta de base de datos) frente a
  `DER-BIOPET-pgAdmin-ERD-Tool.png` (exportación real de pgAdmin 4 ERD Tool
  sobre el esquema real de PostgreSQL, que es lo que pidió el docente). Se
  enlazó también desde `docs/informe/secciones/13-anexos.tex` (listado de
  documentos de referencia del informe técnico).
- **Archivos involucrados:** `docs/observaciones/evidencias/DER-BIOPET-pgAdmin-ERD-Tool.png` (ya incorporado por Jaime antes de este cierre, sin modificar), `docs/requisitos/SRS.md`, `docs/informe/secciones/13-anexos.tex`.
- **Evidencia actual:**
  ```
  git status --short
  ?? docs/observaciones/evidencias/DER-BIOPET-pgAdmin-ERD-Tool.png

  grep -n "pgAdmin-ERD-Tool" docs/requisitos/SRS.md docs/informe/secciones/13-anexos.tex
  (presente en ambos archivos, con enlace relativo funcional desde el SRS)
  ```
- **Commit o commits:** Commit de cierre: pendiente, commit de esta rama (`jaime/cierre-observaciones-1a-1b`).
- **Responsable:** Jaime Mariscal (incorporación de la evidencia y documentación); Fred Beltrán permanece como autor histórico del DER Graphviz original.
- **Estado:** CERRADA
- **Justificación del estado:** Existe ahora, verificado como PNG válido, el artefacto que el docente pidió explícitamente ("Exportar el DER desde pgAdmin 4 (ERD Tool) como PNG de alta resolución"), diferenciado sin ambigüedad del renderizado Graphviz preexistente, y enlazado desde la documentación de requisitos y desde el informe técnico, además de desde esta misma bitácora (sección 3.2).

---

## OBS-06 — Colección Postman no versionada

- **Código:** OBS-06
- **Título:** La colección Postman no estaba versionada como `.json`
- **Entrega:** Entrega 1B
- **Semana:** Semana 6
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1B
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1B.png`](evidencias/SGA-retroalimentacion-entrega-1B.png)
- **Texto literal:**
  > "la coleccion Postman no esta versionada (.json)."

  > "versionar la coleccion Postman (.json)."
- **Criterio relacionado:** C5 — Pruebas JUnit, Postman y métricas (peso 12 %, logrado 85 % = "Bueno").
- **Impacto señalado por el docente:** El texto aclara que las 5 pruebas JUnit sí "cumplen el mínimo"; el único señalamiento de C5 es la colección Postman no versionada.
- **Decisión del equipo:** Decisión inferida a partir de la implementación posterior: versionar el archivo `.json` de la colección dentro de `docs/postman/`.
- **Corrección realizada:** Se agregó `docs/postman/BIOPET_Entrega1B.postman_collection.json` (400 líneas) el mismo día del lote inicial, y posteriormente se reemplazó por una colección más completa y actualizada para autenticación por cookies (`docs/postman/BIOPET.postman_collection.json`, 2782 líneas), junto con un archivo de entorno y un `README.md` propio de la carpeta.
- **Archivos involucrados:** `docs/postman/BIOPET.postman_collection.json`, `docs/postman/BIOPET-Local.postman_environment.json`, `docs/postman/README.md`.
- **Evidencia actual:**
  ```
  git show --stat 39a40a9
   docs/postman/BIOPET_Entrega1B.postman_collection.json | 400 +++++++++++
   1 file changed, 400 insertions(+)

  git show --name-status 39a40a9
  A  docs/postman/BIOPET_Entrega1B.postman_collection.json

  git show --stat dcf8e16
   docs/postman/BIOPET-Local.postman_environment.json |  109 +
   docs/postman/BIOPET.postman_collection.json         | 2782 ++++++++++
   docs/postman/BIOPET_Entrega1B.postman_collection.json |  400 ---
   docs/postman/README.md                               |  189 +
   4 files changed, 3080 insertions(+), 400 deletions(-)

  git show --name-status dcf8e16
  A  docs/postman/BIOPET-Local.postman_environment.json
  A  docs/postman/BIOPET.postman_collection.json
  D  docs/postman/BIOPET_Entrega1B.postman_collection.json
  A  docs/postman/README.md
  ```
  Validación adicional: `docs/postman/BIOPET.postman_collection.json` es JSON válido (verificado con `JSON.parse`).
- **Commit o commits:** `39a40a9` (versión inicial), `dcf8e16` (actualización a autenticación por cookies).
- **Responsable:** Zaida Taipe (colección inicial) y Jaime Mariscal (actualización).
- **Estado:** CERRADA
- **Justificación del estado:** La observación original solo exigía versionar el archivo `.json`; ese archivo existe, está versionado en Git y es JSON válido. No se exige aquí evidencia de ejecución con Newman ni de un reporte de corrida, porque la observación original no lo pedía (tal como indica la instrucción de no añadir requisitos posteriores para impedir el cierre).

---

## OBS-07 — Workflow CI ubicado fuera de `.github/workflows`

- **Código:** OBS-07
- **Título:** El pipeline CI estaba en `./workflows/ci.yml` en lugar de `.github/workflows/`
- **Entrega:** Entrega 1B
- **Semana:** Semana 6
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1B
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1B.png`](evidencias/SGA-retroalimentacion-entrega-1B.png)
- **Texto literal:**
  > "El pipeline CI esta en ./workflows/ci.yml en lugar de ./.github/workflows/, por lo que no se ejecuta automaticamente en GitHub."
- **Criterio relacionado:** C6 — Docker Compose e integración (peso 10 %, logrado 90 % = "Bueno").
- **Impacto señalado por el docente:** Es la única razón dada para que C6 no fuera "Excelente" (los 4 servicios con healthchecks y README ya se calificaron bien).
- **Decisión del equipo:** Decisión inferida a partir de la implementación posterior — mover el archivo a la ruta estándar de GitHub Actions.
- **Corrección realizada:** Se creó `.github/workflows/ci.yml` (49 líneas, con jobs `backend-test`, `frontend-build` y `traceability`) y se eliminó `workflows/ci.yml` (27 líneas). También se corrigió una inconsistencia de mayúsculas en `.gitignore` (`backend/target/` → `Backend/target/`) en el mismo commit.
- **Archivos involucrados:** `.github/workflows/ci.yml`, `workflows/ci.yml` (eliminado), `.gitignore`.
- **Evidencia actual:**
  ```
  git show --stat eef268c
   .github/workflows/ci.yml | 49 ++++++++++++++++++++++++++++++++++++++++++++++++
   .gitignore               |  2 +-
   workflows/ci.yml         | 27 --------------------------
   3 files changed, 50 insertions(+), 28 deletions(-)

  git show --name-status eef268c
  A  .github/workflows/ci.yml
  M  .gitignore
  D  workflows/ci.yml
  ```
  El commit forma parte del PR #37 (`a41727f — Merge pull request #37 from JirachinG19Stdio/jaime/fix-ci-github-actions`). Confirmado también que `workflows/ci.yml` ya no existe en el árbol actual y que `.github/workflows/ci.yml` sí existe.
- **Commit o commits:** `eef268c` (PR #37).
- **Responsable:** Jaime Mariscal.
- **Estado:** CERRADA
- **Justificación del estado:** La corrección es exactamente la solicitada por el docente: el workflow ahora reside en `.github/workflows/ci.yml`, la ruta antigua fue eliminada, y el hash citado existe y fue verificado con `git show`.

---

## OBS-08 — Tag exigido de Entrega 1B no creado

- **Código:** OBS-08
- **Título:** No se creó el tag `v0.1.0-entrega-1b` exigido para marcar la entrega
- **Entrega:** Entrega 1B
- **Semana:** Semana 6
- **Fuente primaria:** Captura SGA de retroalimentación, Entrega 1B
- **Evidencia visual:** [`evidencias/SGA-retroalimentacion-entrega-1B.png`](evidencias/SGA-retroalimentacion-entrega-1B.png)
- **Texto literal:**
  > "no se creo el tag v0.1.0-entrega-1b exigido para marcar la entrega."

  > "Crear el tag anotado v0.1.0-entrega-1b sobre el commit de entrega"
- **Criterio relacionado:** C7 — Repositorio Git (peso 10 %, logrado 85 % = "Bueno", junto con la falta de tag como único señalamiento explícito de este criterio).
- **Impacto señalado por el docente:** Es la razón explícita dada para que C7 no fuera "Excelente" pese a los commits de los tres integrantes y el uso de Conventional Commits.
- **Decisión del equipo:** Crear el tag anotado exactamente con el nombre solicitado por el docente (`v0.1.0-entrega-1b`, sin sustituirlo por `v0.7.0`, `v0.7.1` ni `v0.9.0-rc`), sobre el commit `058b1fe` — el mismo commit que este documento ya había identificado en la Parte 8 como candidato verificado a "fotografía de la Entrega 1B" (último commit del lote inicial del 2026-06-20, el que agrega `PFC_Entrega1B_BMT.pdf`).
- **Corrección realizada:** Se creó el tag anotado `v0.1.0-entrega-1b` sobre el commit `058b1fe`, marcando formalmente el cierre de la Entrega 1B tal como pedía la retroalimentación oficial del SGA: *"Crear el tag anotado v0.1.0-entrega-1b sobre el commit de entrega"*.
- **Archivos involucrados:** No aplica (es un objeto de Git, no un archivo).
- **Evidencia actual:**
  ```
  git tag --list "v0.1.0-entrega-1b"
  v0.1.0-entrega-1b

  git show --no-patch --decorate v0.1.0-entrega-1b
  tag v0.1.0-entrega-1b
  Tagger: Jaime Mariscal <mariscaljaime34@gmail.com>
  Date:   Fri Jul 31 21:56:14 2026 -0500

  Entrega 1B: autenticacion JWT y acceso a datos

  commit 058b1fef728900916fc293fabd0fa7ddb723ba83 (tag: v0.1.0-entrega-1b)
  Author: Fred Beltran <fbeltranm@uteq.edu.ec>
  Date:   Sat Jun 20 12:04:48 2026 -0500

      Add files via upload

  git rev-parse v0.1.0-entrega-1b^{}
  058b1fef728900916fc293fabd0fa7ddb723ba83
  ```
  Nombre exacto del tag: `v0.1.0-entrega-1b` (tag **anotado**, no ligero — tiene tagger, fecha y mensaje propios). Commit objetivo: `058b1fe` (`058b1fef728900916fc293fabd0fa7ddb723ba83`). Comando de verificación: `git rev-parse v0.1.0-entrega-1b^{}`. Propósito indicado por la retroalimentación del SGA: *"no se creo el tag v0.1.0-entrega-1b exigido para marcar la entrega"* / *"Crear el tag anotado v0.1.0-entrega-1b sobre el commit de entrega"* (criterio C7, Repositorio Git).
- **Commit o commits:** `058b1fe` (commit objetivo del tag; no es un commit de corrección de código, es el commit de entrega que el tag marca).
- **Responsable:** Jaime Mariscal (gestión del repositorio).
- **Estado:** CERRADA
- **Justificación del estado:** El tag anotado `v0.1.0-entrega-1b` existe, apunta exactamente al commit `058b1fe` (verificado con `git rev-parse v0.1.0-entrega-1b^{}`), y conserva el nombre exacto solicitado por el docente sin sustituirlo por ninguno de los tags de la Tercera Entrega (`v0.7.0`, `v0.7.1`, `v0.9.0-rc`), tal como exigía la instrucción original.

---

# Parte 5 — Observaciones oficiales de la Entrega 3

## 5.1 Identificación de esta captura de retroalimentación

- **Entrega evaluada:** Entrega 3 (`PFC-VET-ENTR3-v0.9.0-rc`, según el nombre de repositorio citado en la propia retroalimentación de la Entrega 1B, sección 3.2).
- **Calificación individual de Jaime Mariscal en Entrega 3:** 7.80/10.
- **Procedencia de la evidencia:** a diferencia de OBS-01 a OBS-08 (que provienen de capturas PNG del aula virtual SGA, ver sección 3.2), las observaciones OBS-09 a OBS-15 fueron transmitidas como retroalimentación oficial de la Entrega 3 en el enunciado de la tarea de la Entrega Final, sin un archivo de captura adjunto en este ciclo de trabajo. Se registran aquí íntegramente y sin reformular su contenido, siguiendo el mismo formato de trazabilidad que el resto de este documento. **No existe, al momento de escribir este bloque, un archivo PNG ni SHA-256 asociado a esta fuente** (a diferencia de `evidencias/SGA-retroalimentacion-entrega-1A.png` y `-1B.png`); si posteriormente se incorpora la captura oficial correspondiente, debe enlazarse aquí y en la tabla de la sección 3.2.
- **Fecha de incorporación de este bloque al repositorio:** 2026-08-16.
- **Rama donde se registran estas observaciones:** `jaime-final`.
- **Alcance de este bloque:** exclusivamente las tareas asignadas a Jaime Mariscal para la Fase 1 de la Entrega Final `v1.0.0` (observaciones de repositorio/versionado/Git, cobertura JaCoCo). No se abordan aquí Zenodo/DOI más allá de dejar constancia del pendiente, ni SpotBugs, ZAP, CI, Makefile, README ni Lighthouse, que quedan fuera del alcance de esta tarea.

## OBS-09 — Tag `v0.9.0-rc` exigido no fue creado

- **Código:** OBS-09
- **Título:** No existía el tag `v0.9.0-rc`; la última etiqueta encontrada era `v0.7.1`
- **Entrega:** Entrega 3
- **Fuente:** Retroalimentación oficial de la Entrega 3 (transmitida en el enunciado de la tarea de la Entrega Final, sin captura PNG adjunta en este ciclo; ver 5.1)
- **Texto de la observación original:** "No existía el tag `v0.9.0-rc`; la última etiqueta era `v0.7.1`." La observación señalaba explícitamente una **ausencia**: en el momento de esa retroalimentación, el tag `v0.9.0-rc` no existía en el repositorio. Este bloque documenta la corrección de esa ausencia, no afirma en ningún momento que el tag haya existido ya en la fecha original de la Entrega 3 — precisamente lo contrario es lo que la observación señalaba.
- **Criterio relacionado:** C7 — Repositorio Git / gestión de versiones (mismo criterio de OBS-08).
- **Verificación histórica (previa a la corrección, `jaime-final`, 2026-08-16):**
  ```
  git tag --list
  (sin salida — no existía ningún tag en este repositorio)
  ```
  En ese momento no existía **ningún** tag en este checkout, confirmando la observación original.
- **Corrección realizada (posterior, evidencia real verificada en esta misma tarea):** Se reconstruyó y publicó el tag anotado `v0.9.0-rc` **como corrección de trazabilidad histórica**, apuntando exactamente al commit real que cierra la Entrega 3, sin reescribir ni alterar ningún commit existente — el tag es un objeto Git nuevo (una referencia), no una modificación del historial. El tag fue creado *después* de la fecha original de la Entrega 3, en esta fase de cierre de observaciones; no se afirma que haya existido antes.
  ```
  git show --no-patch --decorate v0.9.0-rc
  tag v0.9.0-rc
  Tagger: Jaime Mariscal <jmariscalc@uteq.edu.ec>
  Date:   Sun Aug 16 23:22:06 2026 -0500

  Tercera Entrega del PFC BIOPET

  commit ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3 (tag: v0.9.0-rc)
  Author: Jaime Josué Mariscal Cabrera <mariscaljaime34@gmail.com>
  Date:   Fri Jul 31 23:59:50 2026 -0500

      Add files via upload

  git rev-parse v0.9.0-rc^{}
  ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3

  git show --stat ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3
  docs/informe/informe-entrega-3.pdf | Bin 0 -> 559898 bytes
  1 file changed, 0 insertions(+), 0 deletions(-)

  git ls-remote --tags origin
  6512e8cbf78de4930c91f5c4e30a031742875994	refs/tags/v0.9.0-rc
  ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3	refs/tags/v0.9.0-rc^{}
  ```
  El commit objetivo (`ffd3c07`, hash completo `ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3`) corresponde al cierre histórico real de la Entrega 3: incorpora `docs/informe/informe-entrega-3.pdf` (fechado `Fri Jul 31 23:59:50 2026`, autor Jaime Josué Mariscal Cabrera), el mismo commit que ya existía en el historial del repositorio antes de esta corrección — el tag solo lo señala retroactivamente, no crea contenido nuevo ni reescribe ese commit. `git ls-remote --tags origin` confirma que el tag ya está **publicado en `origin`**, no solo local.
- **Archivos involucrados:** Ninguno de contenido (es un objeto de Git — un tag anotado —, no un archivo del árbol de trabajo); este bloque actualiza únicamente `docs/observaciones/OBSERVACIONES.md`.
- **Commit o commits:** Commit objetivo del tag: `ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3` (preexistente, no modificado; el tag apunta a él, no lo crea ni lo altera). Tag: `v0.9.0-rc`, publicado en `origin`.
- **Responsable:** Jaime Mariscal (gestión del repositorio).
- **Estado:** CERRADA
- **Justificación del estado:** El tag anotado `v0.9.0-rc` existe, apunta exactamente al commit histórico real de cierre de la Entrega 3 (`ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3`, verificado con `git rev-parse v0.9.0-rc^{}`), fue publicado en `origin` (verificado con `git ls-remote --tags origin`), y no implicó reescribir ningún commit — es una reconstrucción retrospectiva de trazabilidad histórica, creada en esta fase, no una afirmación de que el tag existiera en la fecha original de la Entrega 3. La ausencia señalada por la observación original queda subsanada.

---

## OBS-10 — Software no archivado en Zenodo, DOI pendiente

- **Código:** OBS-10
- **Título:** No se archivó el software en Zenodo y el DOI seguía pendiente
- **Entrega:** Entrega 3
- **Fuente:** Retroalimentación oficial de la Entrega 3 (ver 5.1)
- **Texto de la observación:** "No se archivó el software en Zenodo y el DOI seguía pendiente."
- **Criterio relacionado:** Bloque E.2 de la Guía de la Tercera Entrega (archivado permanente / citabilidad del software), referenciado también en `docs/VERSIONING.md` ("para eso se usa el DOI de Zenodo asociado al tag correspondiente").
- **Verificación en este repositorio:**
  ```
  grep -rln "zenodo\|DOI" . --include="*.md"
  docs/adr/ADR-002-pila-tecnologica.md
  docs/requisitos/cambios/CAMBIOS-SRS.md
  docs/u4/informe/README.md
  docs/VERSIONING.md
  ```
  Ningún archivo del repositorio declara un DOI real ni un registro de Zenodo existente; las menciones encontradas son referencias a la política/plan de uso de Zenodo, no evidencia de un archivado ya realizado.
- **Decisión del equipo para esta fase:** No corresponde a Jaime publicar en Zenodo dentro de esta tarea (acción explícitamente fuera de alcance de la Fase 1). Se documenta el pendiente sin inventar un DOI.
- **Corrección realizada:** Ninguna (fuera de alcance de esta fase, por instrucción explícita: "NO publiques en Zenodo", "NO inventes DOI").
- **Archivos involucrados:** Ninguno modificado en esta fase.
- **Commit o commits:** PENDIENTE.
- **Responsable:** Por definir en el equipo (gestión de publicación/archivado).
- **Estado:** ABIERTA
- **Justificación del estado:** El propio repositorio confirma la ausencia de un DOI real; no se realizó ninguna acción de archivado ni se fabricó un identificador, conforme a la restricción explícita de esta tarea.

---

## OBS-11 — Evidencia/reporte Lighthouse faltante

- **Código:** OBS-11
- **Título:** Faltaba la evidencia/reporte Lighthouse
- **Entrega:** Entrega 3
- **Fuente:** Retroalimentación oficial de la Entrega 3 (ver 5.1)
- **Texto de la observación histórica:** "Faltaba la evidencia/reporte Lighthouse." La deficiencia señalada por Entrega 3 era, específicamente, la **ausencia** de un reporte/evidencia Lighthouse en el repositorio — no la calidad de un resultado ya existente.
- **Criterio relacionado:** Calidad web automatizada del frontend (bloque de mediciones de la Guía).
- **Verificación de la resolución, con `git show`:**
  ```
  git show --stat 9ea0ccb
  commit 9ea0ccb0b3f2a86996d2aa047f385dff7e5c1675
  Author: Jaime Mariscal <mariscaljaime34@gmail.com>
  Date:   Mon Aug 10 16:29:52 2026 -0500

      docs(lighthouse): incorporar evidencia y actualizar estado final académico

   36 files changed, 79825 insertions(+), 14 deletions(-)
   docs/mediciones/lighthouse/README.md               |  122 +
   docs/mediciones/lighthouse/SHA256SUMS-ORIGINAL.txt |   31 +
   docs/mediciones/lighthouse/SHA256SUMS.txt          |   31 +
   docs/mediciones/lighthouse/raw/assertion-results.json | 34 +
   docs/mediciones/lighthouse/raw/manifest.json       |   74 +
   ... (24 archivos .html/.json adicionales bajo raw/, todos con estado "A")

  git show --name-status 9ea0ccb | grep "^A"
  A  docs/mediciones/lighthouse/README.md
  A  docs/mediciones/lighthouse/SHA256SUMS-ORIGINAL.txt
  A  docs/mediciones/lighthouse/SHA256SUMS.txt
  A  docs/mediciones/lighthouse/raw/... (31 archivos, todos "A" = añadidos, ninguno preexistía)
  ```
  `git show` confirma sin ambigüedad que el commit `9ea0ccb0b3f2a86996d2aa047f385dff7e5c1675` **crea** (estado `A`, no `M`) los 34 archivos de `docs/mediciones/lighthouse/` — README, dos inventarios SHA-256 y 31 archivos crudos (`manifest.json`, `assertion-results.json`, reportes `.html`/`.json`) — donde antes de ese commit no existía ningún archivo bajo esa ruta. Es decir, el commit demuestra realmente la incorporación completa de la evidencia Lighthouse que Entrega 3 señaló como ausente.
- **Decisión del equipo para esta fase:** La deficiencia original ("faltaba la evidencia/reporte Lighthouse") queda subsanada: el reporte existe, está versionado y es verificable con `git show`. Se cierra la observación histórica. La calidad puntual de ese reporte (umbral SEO no alcanzado, corridas realizadas en un único perfil) es un asunto distinto, que se documenta a continuación como nota independiente, no como condición para reabrir esta observación ni como una observación CERRADA PARCIALMENTE.
- **Corrección realizada:** Ninguna en esta tarea (la evidencia ya existía, incorporada por el commit `9ea0ccb`, anterior a esta fase). Esta tarea solo verificó y confirmó la resolución con `git show`.
- **Archivos involucrados (ya existentes, no modificados en esta tarea):** `docs/mediciones/lighthouse/README.md`, `docs/mediciones/lighthouse/SHA256SUMS.txt`, `docs/mediciones/lighthouse/SHA256SUMS-ORIGINAL.txt`, `docs/mediciones/lighthouse/raw/*` (31 archivos), `lighthouserc.js`.
- **Commit o commits:** `9ea0ccb0b3f2a86996d2aa047f385dff7e5c1675` (verificado con `git show --stat` y `git show --name-status`, confirmando que los 34 archivos fueron añadidos, no modificados).
- **Responsable:** Jaime Mariscal (evidencia de mediciones).
- **Estado:** CERRADA
- **Justificación del estado:** `git show` confirma, con estado `A` para los 34 archivos involucrados, que el commit `9ea0ccb` incorporó realmente la evidencia Lighthouse completa (README + 31 archivos crudos + 2 inventarios SHA-256) que Entrega 3 señaló como faltante. La observación, tal como fue formulada ("faltaba la evidencia/reporte"), queda cerrada porque esa ausencia específica ya no existe. No se usa un estado ambiguo tipo "CERRADA PARCIALMENTE": el asunto pendiente sobre el umbral SEO se registra como nota independiente inmediatamente abajo, sin condicionar el cierre de esta observación.

> **Nota independiente — pendiente para la Entrega Final (no reabre OBS-11):**
> La evidencia Lighthouse archivada corresponde a una ejecución del **2026-08-01**, en un único perfil de medición (móvil simulado, `throttlingMethod: simulate`), sobre dos rutas (`/login` y `/mascotas`, esta última terminando en `/login` por el guard de autenticación — ver `docs/mediciones/lighthouse/README.md`, sección "Páginas evaluadas"). Para la Entrega Final todavía deben realizarse: (1) las corridas definitivas en **ambos** perfiles mobile y desktop (`lighthouserc.js` solo define el perfil móvil simulado actualmente), y (2) la corrección del umbral SEO, que se mantiene en 0.82 frente al mínimo configurado de 0.90 en las 6 corridas registradas (`raw/assertion-results.json`). Esta nota es un pendiente de trabajo futuro, explícitamente fuera del alcance de esta Fase 1 ("Detente aquí. No continúes con ... Lighthouse"); no se ejecutó ninguna corrida nueva de Lighthouse en esta tarea.

---

## OBS-12 — Calidad del sistema no enmarcada en ISO/IEC 25010

- **Código:** OBS-12
- **Título:** La calidad del sistema no estaba enmarcada correctamente en ISO/IEC 25010
- **Entrega:** Entrega 3
- **Fuente:** Retroalimentación oficial de la Entrega 3 (ver 5.1)
- **Texto de la observación original:** "La calidad del sistema no estaba enmarcada correctamente en ISO/IEC 25010."
- **Criterio relacionado:** Marco de calidad del software del informe técnico / Unidad IV.
- **Verificación previa a la corrección (Fase 1):**
  ```
  grep -rln "25010" . --include="*.md"
  (sin resultados)
  ```
  Ningún documento del repositorio mencionaba explícitamente ISO/IEC 25010 ni mapeaba las características de calidad contra la evidencia empírica ya recolectada (JaCoCo, k6, SUS, OWASP, Lighthouse).
- **Corrección realizada (esta fase):** Se creó
  [`docs/arquitectura/ISO-25010.md`](../arquitectura/ISO-25010.md), un
  documento nuevo que **estructura y clasifica** (no certifica) la
  evidencia empírica ya existente en el repositorio usando las ocho
  características de ISO/IEC 25010:2011 (adecuación funcional, eficiencia
  de desempeño, compatibilidad, usabilidad, fiabilidad, seguridad,
  mantenibilidad, portabilidad) como marco de referencia. El documento:
  - Enlaza evidencia real ya existente por subcaracterística (JaCoCo,
    k6, SUS, Lighthouse Accessibility, OWASP/ZAP/SpotBugs, ADRs, healthchecks
    de Docker Compose, `.env.example`), sin calcular ni inventar ninguna
    métrica nueva.
  - Declara explícitamente **`No evaluada directamente`** para las
    subcaracterísticas sin evidencia dedicada en el repositorio
    (Compatibilidad completa; capacidad/*stress test* de Eficiencia de
    desempeño; reusabilidad de Mantenibilidad; capacidad de ser
    reemplazado de Portabilidad), en vez de forzar un resultado.
  - Aclara explícitamente, en su propia cabecera, que **BIOPET no está
    certificado** por ISO/IEC 25010 y que la norma se usa únicamente como
    marco de clasificación.
  - Fija la edición utilizada (**ISO/IEC 25010:2011**) por coherencia con
    que ninguna otra parte del repositorio cita una edición distinta de
    esta norma en particular (no confundir con ISO/IEC/IEEE 29148:2018,
    usada para el SRS, que es una norma distinta).
- **Verificación posterior (esta fase):**
  ```
  git grep -i "25010" -- '*.md'
  docs/arquitectura/ISO-25010.md   (múltiples líneas, el documento nuevo)
  docs/observaciones/OBSERVACIONES.md   (referencias a esta observación)
  ```
  Cada ruta de evidencia citada en `docs/arquitectura/ISO-25010.md` fue
  verificada con `[ -e "<ruta>" ]` contra el árbol de trabajo real antes de
  cerrar esta observación: todas las rutas citadas existen.
- **Archivos involucrados:** `docs/arquitectura/ISO-25010.md` (nuevo).
- **Commit o commits:** PENDIENTE (el commit de esta corrección lo realizará Jaime manualmente).
- **Responsable:** Jaime Mariscal.
- **Estado:** CERRADA
- **Justificación del estado:** Existe ahora una sección documental clara
  (`docs/arquitectura/ISO-25010.md`) que relaciona las propiedades y
  mediciones reales de BIOPET con las características de calidad de
  ISO/IEC 25010, sin afirmar certificación y sin inventar métricas — toda
  cifra citada corresponde a un archivo real ya existente en el
  repositorio, verificado antes de este cierre.

---

## OBS-13 — `CONTRIBUTORS.md` no asigna roles CRediT individualmente

- **Código:** OBS-13
- **Título:** `CONTRIBUTORS.md` no asignaba roles CRediT de forma individual
- **Entrega:** Entrega 3
- **Fuente:** Retroalimentación oficial de la Entrega 3 (ver 5.1)
- **Texto de la observación:** "`CONTRIBUTORS.md` no asignaba roles CRediT individualmente."
- **Criterio relacionado:** Reconocimiento de autoría / CRediT (bloque E de la Guía).
- **Verificación previa (Fase 1/4):** `CONTRIBUTORS.md` confirmaba la observación en su propio texto: la matriz de roles CRediT se aplicaba "a nivel de equipo" y no por persona, con la justificación explícita de que "el repositorio provisto no incluye historial de Git (`.git/`) ni metadatos de autoría por commit que permitan atribuir de forma verificable un rol a una persona específica" — justificación que, a su vez, contradecía el estado real del repositorio (ver OBS-14).
- **Corrección realizada (esta fase, con autorización explícita para editar `CONTRIBUTORS.md`):** Se reescribió la "Matriz de roles CRediT" de `CONTRIBUTORS.md` para asignar cada uno de los 14 roles **por persona** (Fred Beltrán, Jaime Mariscal, Zaida Taipe), basándose exclusivamente en evidencia real verificable: autoría de commits (`git shortlog -sne --all`, `git log --all --author=<correo> --name-only`), nombres de ramas de trabajo (`fred/f01-f05-sp-acceso-datos`, `fred/f06-f07-rendimiento-k6`, `fred/f08-f11-produccion-despliegue`, `fred/f17-f19-fair-software-provenance`, `jaime/data-dictionary`, `jaime/fix-ci-github-actions`, `jaime/observaciones-1a-1b`, `zaida/mascotas-crud-accesible`, `zaida/problemdetail-409-accesibilidad-login`, entre otras) y archivos concretos del repositorio. Donde no se encontró evidencia diferenciada de que una persona ejerció un rol especifico, la celda se dejó en blanco (`—`) para esa persona en vez de marcarla por defecto — no se atribuyó ningún trabajo sin sustento. Se agregó además el correo institucional de cada integrante (`fbeltranm@uteq.edu.ec`, `jmariscalc@uteq.edu.ec`, `ztaipem@uteq.edu.ec`) junto a su nombre en la sección "Equipo". Johan Carvajal y Michael Fajardo, revisores cruzados históricos de Unidad IV (ver `README.md`, sección "Autoría e historial del repositorio"), no se incluyeron como autores, consistente con `git shortlog` (2-3 commits cada uno, fuera del equipo de PFC).
- **Archivos involucrados:** `CONTRIBUTORS.md`.
- **Commit o commits:** PENDIENTE (cambio realizado en el árbol de trabajo de esta fase; no se ejecutó ningún commit).
- **Responsable:** Equipo completo (Fred Beltrán, Jaime Mariscal, Zaida Taipe); Jaime coordina el cierre.
- **Estado:** CERRADA
- **Justificación del estado:** `CONTRIBUTORS.md` ya no asigna los 14 roles CRediT de forma colectiva e indiferenciada: cada rol se asigna ahora por persona, con evidencia real y verificable (commits, ramas, archivos) citada explícitamente por celda, y sin inventar ni exagerar ninguna atribución. La observación pedía exactamente eso ("asignar roles CRediT individualmente"), y ya está hecho.

---

## OBS-14 — Documentación afirmaba ausencia de historial Git pese a más de 200 commits

- **Código:** OBS-14
- **Título:** La documentación afirmaba que no existía historial Git pese a existir más de 200 commits
- **Entrega:** Entrega 3
- **Fuente:** Retroalimentación oficial de la Entrega 3 (ver 5.1)
- **Texto de la observación original:** "La documentación afirmaba que no existía historial Git pese a existir más de 200 commits."
- **Criterio relacionado:** C7 — Repositorio Git; consistencia interna de la documentación.
- **Búsqueda global previa (Fase 1/4)** (`git grep`, sobre todo el árbol versionado):
  ```
  git grep -niE "no incluye historial|no existe historial|sin historial de (git|commits)|no hay historial|no posee historial|carece de historial"
  CONTRIBUTORS.md:18:repositorio provisto no incluye historial de Git (`.git/`) ni metadatos de
  ```
  Esa búsqueda confirmó que la **única** afirmación contradictoria real en
  todo el repositorio era `CONTRIBUTORS.md:18` (`README.md:115` ya
  afirmaba correctamente lo contrario — que el historial se conserva — y
  no requería corrección).
- **Corrección realizada (esta fase, con autorización explícita para editar `CONTRIBUTORS.md`):** Se eliminó de `CONTRIBUTORS.md` la frase "el repositorio provisto no incluye historial de Git (`.git/`) ni metadatos de autoría por commit que permitan atribuir de forma verificable un rol a una persona específica". La nueva "Matriz de roles CRediT (individual)" del archivo declara en su lugar, correctamente, que el repositorio **sí** conserva su historial de Git completo, y cita el propio mecanismo real de verificación usado (`git shortlog -sne --all`, `git log --all --author=...`) — sin exagerar la autoría individual más allá de lo que esa evidencia realmente sustenta (ver cierre de OBS-13, mismo archivo).
- **Verificación posterior a la corrección (esta fase, evidencia real):**
  ```
  git grep -niE "no incluye historial|no existe historial|sin historial de (git|commits)|no hay historial|no posee historial|carece de historial" -- CONTRIBUTORS.md
  (sin resultados)

  git rev-list --count HEAD
  314

  git log --merges --oneline | wc -l
  73

  git tag --list
  v0.1.0-entrega-1b
  v0.7.0
  v0.7.1
  v0.9.0-rc
  ```
  El historial creció de 270 a 314 commits (62 a 73 merges) entre la
  verificación de la Fase 1/4 y este cierre, por el trabajo real posterior
  del equipo — consistente con que el repositorio nunca dejó de tener
  historial de Git, exactamente lo contrario de lo que afirmaba la frase
  ya eliminada.
- **Archivos involucrados:** `CONTRIBUTORS.md`.
- **Commit o commits:** PENDIENTE (cambio realizado en el árbol de trabajo de esta fase; no se ejecutó ningún commit).
- **Responsable:** Jaime Mariscal (verificación); corrección de
  `CONTRIBUTORS.md` realizada por el equipo completo junto con OBS-13.
- **Estado:** CERRADA
- **Justificación del estado:** La única afirmación falsa detectada en todo el repositorio (búsqueda global `git grep`, no solo el archivo ya señalado) vivía en `CONTRIBUTORS.md:18` y ya fue eliminada; el archivo ahora refleja correctamente que el historial de Git existe y se usó como evidencia real para individualizar los roles CRediT (OBS-13). No se reescribió el historial de Git ni se usó `.mailmap`; la corrección es puramente documental, sobre el único archivo que contenía la afirmación errónea.

---

## OBS-15 — Correo institucional de Jaime para trazabilidad de commits

- **Código:** OBS-15
- **Título:** Se pidió a Jaime utilizar su correo institucional en los commits para trazabilidad
- **Entrega:** Entrega 3
- **Fuente:** Retroalimentación oficial de la Entrega 3 (ver 5.1)
- **Texto de la observación original:** "Se pidió a Jaime utilizar su correo institucional en los commits para trazabilidad."
- **Criterio relacionado:** C7 — Repositorio Git / trazabilidad de autoría.
- **Verificación histórica (Fase 1, previa a la corrección):** en ese
  momento, `user.email` ya estaba configurado como
  `jmariscalc@uteq.edu.ec`, pero ningún commit del historial usaba
  todavía esa dirección; los commits recientes de Jaime seguían firmados
  con `mariscaljaime34@gmail.com`.
- **Verificación actual (esta fase, evidencia real):**
  ```
  git log --author="Jaime Mariscal" --format="%h %ae %ad %s" --date=short -10
  479961e jmariscalc@uteq.edu.ec 2026-08-16 ci: evitar ejecuciones duplicadas en ramas de trabajo
  6658892 jmariscalc@uteq.edu.ec 2026-08-16 Merge pull request #2 from Grinjoww/jaime-final
  53d4146 jmariscalc@uteq.edu.ec 2026-08-16 docs: cerrar OBS-09 con tag historico v0.9.0-rc
  c5f7c7c jmariscalc@uteq.edu.ec 2026-08-16 Merge pull request #1 from Grinjoww/jaime-final
  deaf381 jmariscalc@uteq.edu.ec 2026-08-16 ci: habilitar validacion en ramas del equipo
  6e8f366 jmariscalc@uteq.edu.ec 2026-08-16 ci: integrar gates de seguridad y calidad
  bb43baa jmariscalc@uteq.edu.ec 2026-08-16 docs: archivar evidencias OWASP ZAP y analisis estatico
  e2a4935 jmariscalc@uteq.edu.ec 2026-08-16 security: agregar auditoria estatica y validacion SQL
  9a1afce jmariscalc@uteq.edu.ec 2026-08-16 test: elevar cobertura JaCoCo de branches al 79.39%
  5a23cec jmariscalc@uteq.edu.ec 2026-08-16 docs: registrar observaciones de Entrega 3 OBS-09 a OBS-15

  git log -1 --format="%H%n%an <%ae>%n%ad" --date=iso 479961e
  479961e59507f9bb88bd40c62c3a2b10f246709b
  Jaime Mariscal <jmariscalc@uteq.edu.ec>
  2026-08-16 23:37:16 -0500
  ```
  Existen ahora, de forma real y verificable, **al menos diez commits
  reales de la Entrega Final** firmados con `Jaime Mariscal
  <jmariscalc@uteq.edu.ec>`, incluidos dos merges de pull request
  (`6658892`, `c5f7c7c`), confirmando que el correo institucional no solo
  está configurado localmente sino que ya se usa en autoría real de
  commits. Hashes citados arriba: `479961e`, `6658892`, `53d4146`,
  `c5f7c7c`, `deaf381`, `6e8f366`, `bb43baa`, `e2a4935`, `9a1afce`,
  `5a23cec`.
- **Aclaración sobre el historial previo (no se reescribió nada):** los
  commits anteriores a esta corrección, firmados con
  `mariscaljaime34@gmail.com`, **conservan su autor original sin
  modificación**. No se ejecutó ningún `git commit --amend`, `git rebase`
  ni reescritura de historial, ni se usó `.mailmap` para enmascarar u
  ocultar esa autoría histórica: ambos correos (`mariscaljaime34@gmail.com`
  en commits antiguos, `jmariscalc@uteq.edu.ec` en commits recientes de
  esta Entrega Final) coexisten visibles en `git log`, tal como Git los
  registró en su momento.
- **Decisión del equipo para esta fase:** Verificar con `git log` (lectura
  únicamente, sin escritura) que ya existen commits reales con el correo
  institucional, y cerrar la observación con esa evidencia.
- **Corrección realizada:** Ninguna en esta tarea puntual (no se creó
  ningún commit en esta fase); la corrección ya existía en el historial
  antes de iniciar esta tarea, generada por commits reales previos de la
  Entrega Final.
- **Archivos involucrados:** Ninguno (evidencia de historial Git, no de
  archivos del árbol de trabajo).
- **Commit o commits:** Evidencia de cierre: `479961e`, `6658892`,
  `53d4146`, `c5f7c7c`, `deaf381`, `6e8f366`, `bb43baa`, `e2a4935`,
  `9a1afce`, `5a23cec` (ya existentes, autoría real verificada con
  `git log`). Commit de esta actualización documental: PENDIENTE.
- **Responsable:** Jaime Mariscal.
- **Estado:** CERRADA
- **Justificación del estado:** `git log` confirma, con hashes reales y
  verificables, que existen múltiples commits de la Entrega Final
  firmados con `Jaime Mariscal <jmariscalc@uteq.edu.ec>`, incluidos merges
  de pull request. Los commits históricos anteriores conservan su autor
  original sin alteración; no se reescribió el historial ni se usó
  `.mailmap` para ocultar la evidencia previa. La observación pedía
  trazabilidad real en los commits, y esa trazabilidad ya existe.

---

## 5.2 Resumen de OBS-09 a OBS-15

| Código | Observación | Estado | Depende de |
|---|---|---|---|
| OBS-09 | Tag `v0.9.0-rc` no creado (ni `v0.7.1` existía en el checkout original) | CERRADA | Reconstruido y publicado en `origin` → commit `ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3` |
| OBS-10 | Software no archivado en Zenodo, DOI pendiente | ABIERTA | Publicación manual en Zenodo (fuera de alcance de esta tarea) |
| OBS-11 | Evidencia Lighthouse faltante | CERRADA | Verificada con `git show` sobre `9ea0ccb`; ver nota independiente sobre corridas mobile/desktop y umbral SEO pendientes |
| OBS-12 | Calidad no enmarcada en ISO/IEC 25010 | CERRADA | `docs/arquitectura/ISO-25010.md` (nuevo), rutas de evidencia verificadas |
| OBS-13 | `CONTRIBUTORS.md` sin roles CRediT individuales | CERRADA | `CONTRIBUTORS.md` — matriz CRediT individualizada por persona con evidencia real |
| OBS-14 | Documentación afirma ausencia de historial Git pese a 270 commits | CERRADA | `CONTRIBUTORS.md` — afirmación falsa eliminada |
| OBS-15 | Correo institucional de Jaime en commits | CERRADA | Commits reales verificados con `git log` (`jmariscalc@uteq.edu.ec`) |

De estas siete observaciones, **OBS-09, OBS-11, OBS-12 y OBS-15** se
marcan **CERRADA**. OBS-09: el tag anotado `v0.9.0-rc` fue reconstruido
retrospectivamente sobre el commit histórico real de cierre de la Entrega 3
(`ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3`, verificado con
`git rev-parse v0.9.0-rc^{}`), sin reescribir ningún commit, y está
publicado en `origin` (verificado con `git ls-remote --tags origin`); no se
afirma que el tag existiera en la fecha original de la Entrega 3 — la
observación señalaba precisamente esa ausencia, y la corrección es
retroactiva, creada en esta fase. OBS-11: `git show` confirma que el commit
`9ea0ccb` añadió (no modificó) los 34 archivos de evidencia Lighthouse,
subsanando la ausencia señalada por Entrega 3. OBS-12: se creó
`docs/arquitectura/ISO-25010.md`, que estructura la evidencia real ya
existente (JaCoCo, k6, SUS, Lighthouse, OWASP/ZAP/SpotBugs, ADRs) contra
las ocho características de ISO/IEC 25010:2011, sin afirmar certificación
y sin inventar métricas; todas las rutas citadas fueron verificadas contra
el árbol de trabajo real. OBS-15: `git log` confirma múltiples commits
reales de la Entrega Final firmados `Jaime Mariscal
<jmariscalc@uteq.edu.ec>`, incluidos dos merges de pull request, sin
reescribir el historial previo. No se usa el estado ambiguo "CERRADA
PARCIALMENTE" en ninguno de los casos; el pendiente sobre corridas
mobile/desktop y el umbral SEO de OBS-11 se registra como nota
independiente dentro de su propio bloque, sin condicionar su cierre.
OBS-13 y OBS-14 (`CONTRIBUTORS.md` sin roles CRediT individuales /
afirmación falsa sobre ausencia de historial Git) fueron cerradas en una
fase posterior, una vez autorizada explícitamente la edición de
`CONTRIBUTORS.md` — ver el detalle en sus propios bloques. Solo OBS-10
permanece ABIERTA: requiere publicar en Zenodo, fuera de alcance de este
repositorio (acción manual del dueño del repositorio).

---

# Parte 6 — Recomendaciones adicionales del docente

Las siguientes recomendaciones fueron registradas textualmente en la sección
"MEJORAS PARA SU APRENDIZAJE" de la retroalimentación de la Entrega 1B, con la
precisión explícita del docente: **"no afectan esta nota"**. Esa precisión se
conserva aquí sin ocultarla.

- Mover el pipeline a `.github/workflows/` para que GitHub Actions lo ejecute en cada push → corresponde a **OBS-07** (CERRADA).
- Crear el tag anotado `v0.1.0-entrega-1b` sobre el commit de entrega → corresponde a **OBS-08** (ABIERTA).
- Versionar la colección Postman (`.json`) → corresponde a **OBS-06** (CERRADA).
- Exportar el DER desde pgAdmin 4 (ERD Tool) como PNG de alta resolución para el informe final → corresponde a **OBS-05** (CERRADA).
- Mantener la participación equilibrada del equipo (advertencia de la Entrega 1B: *"El historial evidencia aportes de Beltrán, Mariscal y Taipe. Mantener este equilibrio en la Entrega 2."* — explícitamente **"no afecta esta nota"**, es una advertencia preventiva, no una observación con corrección de código asociada).

---

# Parte 7 — Fortalezas reconocidas por el docente

## Entrega 1A

- Descripción y alcance del sistema completos.
- Arquitectura C4 Nivel 1 y Nivel 2.
- MER y DDL de PostgreSQL con evidencia de ejecución en pgAdmin.
- Wireframes.
- Cronograma (semanas 5-17) y roles del equipo.
- Referencias académicas verificadas.
- Calidad alta de redacción conforme a ISO/IEC/IEEE 29148 (patrón "deberá", subcriterio D2 = 95 %, "EL MEJOR" según el propio texto del docente).

## Entrega 1B

- Autenticación JWT completa (registro, login, logout, refresh).
- Redis real (`StringRedisTemplate`), no simulado.
- Blacklist de tokens con TTL igual a la expiración del token.
- CRUD completo de la entidad Mascota.
- Migración Flyway V1 real, con trigger, y cero concatenación SQL.
- Controles OWASP (BCrypt costo 12, JWT 1h + refresh 7d, cabeceras incluida CSP, CORS, `@PreAuthorize` + `@EnableMethodSecurity`).
- Participación equilibrada de los tres integrantes.
- Informe técnico completo, con conclusiones por objetivo y referencias APA/IEEE.

No se asigna ningún estado de cierre a las fortalezas: son reconocimientos del
docente, no deficiencias a resolver.

---

# Parte 8 — Estado de tags históricos

| Tag | Fuente que lo exige | Existe | Commit candidato | Estado | Riesgo |
|---|---|---|---|---|---|
| `v0.1.0-entrega-1b` | Retroalimentación oficial de la Entrega 1B (SGA, Semana 6) — texto literal citado en OBS-08 | **Sí** | `058b1fe` (`058b1fef728900916fc293fabd0fa7ddb723ba83`) | Creado — tag anotado (OBS-08 CERRADA) | Cerrado: verificado con `git rev-parse v0.1.0-entrega-1b^{}` = `058b1fe`, el mismo commit que este documento ya había identificado como candidato a "fotografía de la Entrega 1B". |
| `v0.7.0` | Guía de la Tercera Entrega (v0.9.0-rc), no la retroalimentación del SGA | No | `058b1fe` (candidato, ver justificación abajo) | No creado | Medio: `058b1fe` es del repositorio sucesor (`PFC-VET-ENTR3-v0.9.0-rc`), no del repositorio original de la Entrega 1B; etiquetarlo como "v0.7.0" documenta el estado heredado en *este* árbol, no el commit exacto que el docente evaluó. Nótese que `058b1fe` ya tiene el tag `v0.1.0-entrega-1b`: un mismo commit puede llevar varios tags con propósitos distintos, pero `v0.7.0` sigue sin crearse porque responde a una fuente diferente (la Guía, no la retroalimentación del SGA). |
| `v0.7.1` | Guía de la Tercera Entrega — cierre formal de la aplicación de observaciones de 1A/1B | No | — | No aplica todavía | Bajo: las 8 observaciones de esta bitácora (OBS-01 a OBS-08) están CERRADAS (100 %). Las observaciones de las Entregas 1A y 1B ya no bloquean el cierre formal del Bloque 0; crear `v0.7.1` es ahora una decisión de alcance del equipo, no una limitación de evidencia. |
| `v0.9.0-rc` | Retroalimentación oficial de la Entrega 3 (OBS-09) y Guía de la Tercera Entrega — tag final de esta entrega | **Sí** | `ffd3c07` (`ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3`) | Creado — tag anotado, publicado en `origin` (OBS-09 CERRADA) | Cerrado: verificado con `git rev-parse v0.9.0-rc^{}` = `ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3`, el commit histórico real que incorpora `docs/informe/informe-entrega-3.pdf` (cierre de la Entrega 3). Reconstruido retrospectivamente en esta fase, sin reescribir el commit; no existía en la fecha original de la Entrega 3, tal como señalaba la observación. |

**`v0.1.0-entrega-1b`, `v0.7.0`, `v0.7.1` y `v0.9.0-rc` no son equivalentes ni intercambiables.** Cada uno responde a una fuente y a un propósito distinto: el primero es un nombre exigido explícitamente por el docente sobre el commit de cierre de la Entrega 1B; los otros tres provienen de la guía de la Tercera Entrega y marcan hitos distintos del proyecto sucesor.

**Reverificación del candidato `058b1fe`** (fotografía de Entrega 1B en *este* repositorio):

```
git show --stat 058b1fe
commit 058b1fef728900916fc293fabd0fa7ddb723ba83
Author: Fred Beltran <fbeltranm@uteq.edu.ec>
Date:   Sat Jun 20 12:04:48 2026 -0500

    Add files via upload

 PFC_Entrega1B_BMT.pdf | Bin 0 -> 1730568 bytes
 1 file changed, 0 insertions(+), 0 deletions(-)

git show --name-status 058b1fe
A  PFC_Entrega1B_BMT.pdf
```

- **Por qué es candidato:** es el último commit del lote inicial fechado 2026-06-20 (el mismo día en que se subió todo el contenido de la Entrega 1B), y es además el commit que agrega el propio informe técnico `PFC_Entrega1B_BMT.pdf`. Todo el trabajo posterior salta a 2026-07-29 y corresponde inequívocamente a la Tercera Entrega (Makefile, digests, ProblemDetail, claims JWT, etc.).
- **Por qué no debe crearse automáticamente:** este repositorio (`PFC-VET-ENTR3-v0.9.0-rc`) no es el repositorio `PFC--VET-ENTR1B` que el docente efectivamente evaluó (URL distinta, citada en la propia captura de retroalimentación). Etiquetar `058b1fe` como `v0.7.0` en este árbol documenta razonablemente el estado heredado, pero no reconstruye con certeza absoluta el commit exacto calificado por el docente en el repositorio original.
- **Por qué `v0.7.1` ya no está bloqueado por falta de cierre:** por definición, `v0.7.1` marca el cierre de la aplicación de observaciones de 1A/1B; con las 8 observaciones (OBS-01 a OBS-08) CERRADAS, el propósito del tag ya está satisfecho en cuanto a evidencia. Su creación queda como decisión de alcance del equipo (por ejemplo, coordinarla con el resto del trabajo de la Tercera Entrega), no como algo pendiente de esta bitácora.
- **Por qué `v0.9.0-rc` se reconstruyó como corrección de trazabilidad histórica (OBS-09):** la retroalimentación oficial de la Entrega 3 señaló explícitamente que este tag no existía. Se reconstruyó apuntando al commit real que ya cerraba la Entrega 3 en el historial (`ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3`, que incorpora `docs/informe/informe-entrega-3.pdf`), sin reescribir ese commit ni ningún otro; el tag es un objeto nuevo, creado en esta fase, no una reconstrucción de un estado que existiera en la fecha original de la Entrega 3. Ver el bloque OBS-09 (Parte 5) para el detalle completo de la evidencia (`git show --no-patch --decorate`, `git rev-parse`, `git ls-remote --tags origin`).

**No se creó ningún tag `v0.7.0` ni `v0.7.1` como parte de esta tarea.** El
tag `v0.1.0-entrega-1b` fue creado directamente por Jaime Mariscal (ver
evidencia en el bloque OBS-08); el tag `v0.9.0-rc` fue reconstruido y
publicado como corrección de trazabilidad histórica documentada en OBS-09
(ver arriba), también por Jaime Mariscal. Ninguno de los dos implicó
reescribir un commit existente.

---

# Parte 9 — Indicadores finales

## Estado global

- **Total de observaciones:** 8
- **CERRADAS:** 8 (OBS-01, OBS-02, OBS-03, OBS-04, OBS-05, OBS-06, OBS-07, OBS-08)
- **CERRADAS PARCIALMENTE:** 0
- **ABIERTAS:** 0
- **NO VERIFICABLES:** 0

**Porcentaje real de cierre** (solo CERRADA cuenta como cierre completo):

```
porcentaje de cierre = observaciones CERRADAS / 8 × 100
                      = 8 / 8 × 100
                      = 100 %
```

## Estado global — Entrega 3 (OBS-09 a OBS-15)

- **Total de observaciones de Entrega 3:** 7
- **CERRADAS:** 6 (OBS-09 — tag `v0.9.0-rc` reconstruido y publicado en `origin` sobre el commit histórico real `ffd3c073d026a1b1d9dbdf5f53c1316df5388fc3`; OBS-11 — evidencia Lighthouse verificada con `git show` sobre `9ea0ccb`, ver nota independiente sobre corridas mobile/desktop y umbral SEO pendientes; OBS-12 — marco ISO/IEC 25010 documentado en `docs/arquitectura/ISO-25010.md`, evidencia real verificada; OBS-13 — `CONTRIBUTORS.md` con roles CRediT individualizados por persona con evidencia real; OBS-14 — afirmación falsa sobre ausencia de historial Git eliminada de `CONTRIBUTORS.md`; OBS-15 — commits reales con `jmariscalc@uteq.edu.ec` verificados con `git log`)
- **CERRADAS PARCIALMENTE:** 0 (estado no utilizado en esta actualización)
- **ABIERTAS:** 1 (OBS-10)
- **NO VERIFICABLES:** 0

Solo OBS-10 queda sin cerrar: depende de publicar en Zenodo, una acción
manual de archivado fuera del alcance de cambios en este repositorio. Ver
Parte 5.2 para el detalle por observación.

## Estado global combinado (OBS-01 a OBS-15)

- **Total de observaciones registradas en este documento:** 15
- **CERRADAS:** 14 (OBS-01 a OBS-08, Entregas 1A/1B; más OBS-09, OBS-11, OBS-12, OBS-13, OBS-14 y OBS-15, Entrega 3)
- **CERRADAS PARCIALMENTE:** 0
- **ABIERTAS:** 1 (OBS-10)

## Observaciones que aún bloquean `v0.7.1`

Ninguna. Las 8 observaciones (OBS-01 a OBS-08) de las Entregas 1A y 1B están
**CERRADAS**. Las observaciones ya no bloquean el cierre formal del Bloque 0
de la Guía de la Tercera Entrega; la creación de `v0.7.1` queda como
decisión de alcance del equipo, no como una limitación de evidencia
pendiente en esta bitácora.

## Acciones concretas pendientes

Ninguna acción pendiente derivada de las observaciones de las Entregas 1A y
1B. El Bloque 0 de la Guía de la Tercera Entrega queda con evidencia
completa (100 % de cierre).

Pendientes derivados de la Entrega 3 (OBS-09 a OBS-15). OBS-09, OBS-12,
OBS-13, OBS-14 y OBS-15 ya no aparecen en esta lista: el tag `v0.9.0-rc`
fue reconstruido y publicado en `origin` (OBS-09, ver Parte 5); el marco
ISO/IEC 25010 quedó documentado en `docs/arquitectura/ISO-25010.md`
(OBS-12, ver Parte 5); `CONTRIBUTORS.md` ya asigna roles CRediT
individualizados por persona con evidencia real y ya no afirma la
ausencia de historial Git (OBS-13 y OBS-14, ver sus bloques arriba); y
existen commits reales con el correo institucional, verificados con
`git log` (OBS-15, ver Parte 5) — las cinco quedan **CERRADA**. Decidir si
se reconstruye también `v0.7.1` sigue siendo una decisión de alcance del
equipo, no una limitación de evidencia (ver Parte 8). El único pendiente
de Entrega 3 que continúa explícitamente fuera del alcance de cambios en
este repositorio:

- Archivar el software en Zenodo y obtener el DOI — OBS-10 (acción manual del dueño del repositorio, no requiere ni admite cambios de código/documentación adicionales para cerrarse).

Nota de calidad independiente, no ligada a ninguna observación abierta:
ejecutar las corridas Lighthouse definitivas en perfil mobile y desktop, y
corregir el umbral SEO (82/100 frente a ≥ 90) — OBS-11 en sí ya está
CERRADA; esto es trabajo de calidad pendiente, no una condición de cierre
de ninguna observación de esta bitácora.

## Evidencia JaCoCo de esta fase (Entrega Final)

Como parte de esta misma tarea (fuera de la bitácora de observaciones en
sentido estricto, pero relacionado con OBS-14 por tratarse también de
evidencia técnica verificable) se ejecutó `mvn clean verify` en `Backend/` y
se archivó la evidencia real bajo `docs/mediciones/jacoco/` (ver
`docs/mediciones/jacoco/METRICS.md` y `docs/mediciones/sec/jacoco-summary.md`,
ambos ya actualizados con las cifras vigentes). Cobertura final verificada:
LINE 91.80 % (885/964), BRANCH 79.39 % (181/228) — el valor de BRANCH subió
de 74.12 % a 79.39 % en una corrección posterior de esta misma fase, al
cubrir la capa de dominio (`com.biopet.entity`), que estaba en 50 % de
BRANCH y quedó en 100 % — con 189 pruebas en verde (0 fallos, 0 errores).
El umbral de `jacoco:check` en `Backend/pom.xml` se elevó de `0.60` a `0.70`
para LINE y BRANCH tras confirmar que la suite ampliada lo cumple con
margen en las tres capas relevantes (dominio, servicios, controladores).

## Verificación de trazabilidad end-to-end

`docs/trazabilidad/matriz.csv` incluye la fila de `REQ-F-022` (HU-021/CU-21,
cierre de OBS-02) y las filas de `REQ-F-003`, `REQ-F-005` y `REQ-F-006`
(RF-WEB-01/02/04, cierre de OBS-03) ya estaban correctas. El validador
`scripts/validate-traceability.sh` confirma la consistencia completa:

```
$ bash scripts/validate-traceability.sh
VALIDACION OK: 35 requisitos del SRS, 35 filas en matriz.csv, 21 historias
y 21 casos de uso consistentes entre sí.
```

OBS-01 a OBS-08 quedan CERRADAS, sin limitaciones pendientes de trazabilidad.
El tag `v0.1.0-entrega-1b` (OBS-08) ya existe sobre el commit `058b1fe`. El
Bloque 0 de la Guía de la Tercera Entrega queda al 100 % de cierre; las
observaciones de las Entregas 1A y 1B ya no bloquean la creación de
`v0.7.1`.

---

## Trazabilidad de este documento

- Elaborado en la rama `jaime/observaciones-1a-1b`; cerrado (OBS-02, OBS-03, OBS-04, OBS-05) en la rama `jaime/cierre-observaciones-1a-1b`; cerrado (OBS-08) en la rama `jaime/cierre-obs-08`.
- Fuentes primarias: capturas oficiales del aula virtual SGA (ver sección 3.2).
- Verificado contra el historial real de Git mediante `git show --stat` / `git show --name-status` para cada commit citado.
- No contiene observaciones inventadas ni hashes inexistentes.
