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
- **Rama donde se construyó esta bitácora:** `jaime/observaciones-1a-1b`.

## 3.2 Fuentes primarias

| Fuente | Entrega | Procedencia | Evidencia | SHA-256 |
|---|---|---|---|---|
| Captura de retroalimentación (PNG) | Entrega 1A, Semana 5 | Aula virtual SGA | [`evidencias/SGA-retroalimentacion-entrega-1A.png`](evidencias/SGA-retroalimentacion-entrega-1A.png) | `f66c9b08c7ea571bd6af825b9eb9fabc95ef0ef25d7aaa5d842bc92f11c07b7e` |
| Captura de retroalimentación (PNG) | Entrega 1B, Semana 6 | Aula virtual SGA | [`evidencias/SGA-retroalimentacion-entrega-1B.png`](evidencias/SGA-retroalimentacion-entrega-1B.png) | `1c13a5fcc2b155bcca67378b96a23575b5ef64923a6b14cdf980fcccd5d15ea7` |
| Transcripción literal (TXT) | Entrega 1A, Semana 5 | Elaborada a partir de la captura SGA | [`fuentes/SGA-retroalimentacion-entrega-1A.txt`](fuentes/SGA-retroalimentacion-entrega-1A.txt) | — (texto derivado, no es la fuente primaria) |
| Transcripción literal (TXT) | Entrega 1B, Semana 6 | Elaborada a partir de la captura SGA | [`fuentes/SGA-retroalimentacion-entrega-1B.txt`](fuentes/SGA-retroalimentacion-entrega-1B.txt) | — (texto derivado, no es la fuente primaria) |

**Metadatos técnicos de las capturas** (verificados con `Get-FileHash -Algorithm SHA256` y `System.Drawing.Image`):

| Archivo | Tamaño | Dimensiones | SHA-256 |
|---|---|---|---|
| `SGA-retroalimentacion-entrega-1A.png` | 29 447 bytes | 909 × 380 px | `f66c9b08c7ea571bd6af825b9eb9fabc95ef0ef25d7aaa5d842bc92f11c07b7e` |
| `SGA-retroalimentacion-entrega-1B.png` | 42 487 bytes | 909 × 561 px | `1c13a5fcc2b155bcca67378b96a23575b5ef64923a6b14cdf980fcccd5d15ea7` |

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
| OBS-02 | 1A, Semana 5 | Falta RF-07 en la lista consolidada (salta RF-06 → RF-08) | D1. Completitud y consistencia del conjunto | Zaida Taipe (requisitos) | ABIERTA | — |
| OBS-03 | 1A, Semana 5 | RF-WEB remapeados a RF-16/RF-17 sin matriz de trazabilidad explícita | D1. Completitud y consistencia del conjunto | Zaida Taipe (requisitos) | CERRADA PARCIALMENTE | `a1f83a1` |
| OBS-04 | 1A, Semana 5 | Ambigüedad leve en RF-10 ("recomendaciones informativas") | D3. No ambigüedad y singularidad | Zaida Taipe (requisitos) | ABIERTA | — |
| OBS-05 | 1B, Semana 6 | DER entregado como `.dot`, no como exportación PNG de pgAdmin | C1. Diagramas UML, DER y diccionario | Fred Beltrán (modelo de datos) | ABIERTA | — |
| OBS-06 | 1B, Semana 6 | Colección Postman no versionada (.json) | C5. Pruebas JUnit, Postman y métricas | Zaida Taipe / Jaime Mariscal (Postman) | CERRADA | `39a40a9`, `dcf8e16` |
| OBS-07 | 1B, Semana 6 | Workflow CI ubicado en `./workflows/ci.yml` en vez de `.github/workflows/` | C6. Docker Compose e integración | Jaime Mariscal (CI/CD) | CERRADA | `eef268c` (PR #37) |
| OBS-08 | 1B, Semana 6 | Tag `v0.1.0-entrega-1b` exigido no fue creado | C7. Repositorio Git | Jaime Mariscal (gestión del repositorio) | ABIERTA | — |

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
- **Decisión del equipo:** No se encontró ninguna decisión documentada (ni en commits, ni en `CAMBIOS-SRS.md`, ni en `CHANGELOG-REQ.md`) que aborde específicamente el vacío original entre RF-06 y RF-08.
- **Corrección realizada:** Ninguna verificable. El SRS actual (`docs/requisitos/SRS.md`, creado en `a1f83a1`) usa un esquema de numeración completamente nuevo (`REQ-F-001` a `REQ-F-021`), sin discontinuidades, pero la tabla de "Origen (Entrega 1A)" de los requisitos heredados (líneas 388-397 de `SRS.md`) **nunca cita "RF-07"** como origen de ningún requisito nuevo. Existe un `REQ-F-007` ("Consulta del perfil propio"), pero es una funcionalidad distinta y nueva —documentada en `docs/requisitos/cambios/CAMBIOS-SRS.md` como agregada porque "está presente en el código pero no documentada como requisito independiente en el SRS original"— y coincide con el número 007 por pura casualidad de la renumeración, no porque explique o reemplace al RF-07 faltante.
- **Archivos involucrados:** `docs/requisitos/SRS.md`, `docs/requisitos/cambios/CAMBIOS-SRS.md`, `docs/requisitos/CHANGELOG-REQ.md`.
- **Evidencia actual:**
  ```
  grep -n "RF-07" docs/requisitos/SRS.md docs/requisitos/cambios/CAMBIOS-SRS.md \
       docs/trazabilidad/matriz.csv docs/requisitos/CHANGELOG-REQ.md
  (sin resultados)
  ```
- **Commit o commits:** Ninguno. No se cita ningún hash porque no existe un commit que corrija esta observación puntual.
- **Responsable:** Zaida Taipe (autora de la consolidación de requisitos en `a1f83a1`, `39568cb`, `731ebb4`).
- **Estado:** ABIERTA
- **Justificación del estado:** El vacío original de numeración nunca fue explicado ni el requisito faltante fue reconstruido o justificado como "no aplica"; el equipo simplemente adoptó un esquema de numeración distinto que no referencia el RF-07 original en ningún punto del repositorio actual. No se marca CERRADA solo porque hoy existan requisitos con otra numeración, tal como exige la instrucción.

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
- **Decisión del equipo:** Decisión inferida a partir de la implementación posterior — no existe un documento que declare explícitamente "decidimos resolver el remapeo RF-WEB así"; se infiere de que el nuevo SRS sí incorpora, como texto narrativo, el linaje de cada requisito heredado.
- **Corrección realizada:** Parcial. El SRS actual (`docs/requisitos/SRS.md`, commit `a1f83a1`) sí documenta, en el campo **Rationale** de varios requisitos, el origen histórico con los antiguos códigos `RF-WEB`, por ejemplo:
  - `REQ-F-0xx` (emisión de tokens JWT): *"Rationale: heredado de RF-16/RF-WEB-01 de la Entrega 1A."*
  - `REQ-F-0xx` (control de acceso sin token → 401): *"Rationale: heredado de RF-17/RF-WEB-04 de la Entrega 1A."*
  - `REQ-F-006` (RBAC): *"Rationale: heredado de RF-13/RF-WEB-02 de la Entrega 1A."*

  Esto es un vínculo verificable y citable entre los códigos antiguos `RF-WEB-01/02/04` y los nuevos `REQ-F`, pero **no toma la forma de una matriz de trazabilidad explícita** (tabla estructurada). La matriz formal creada en el mismo commit (`docs/trazabilidad/matriz.csv`) tiene columnas `id_requisito, tipo, prioridad_moscow, historia_usuario, caso_de_uso, modulo_codigo, endpoint_api, prueba_automatizada, tipo_acceso, evidencia_empirica, estado` — **ninguna columna referencia los códigos `RF-WEB` originales**. Por tanto, el vínculo existe solo como texto narrativo disperso en el SRS, no como la matriz explícita que el docente señaló como ausente.
- **Archivos involucrados:** `docs/requisitos/SRS.md` (líneas 222, 246, 259), `docs/trazabilidad/matriz.csv`.
- **Evidencia actual:**
  ```
  git show --stat a1f83a1
   docs/requisitos/SRS.md | 637 ++++++++++++++++++
   docs/trazabilidad/matriz.csv | 35 +
   ... (10 files changed, 2134 insertions(+), 2 deletions(-))

  git show --name-status a1f83a1
  A  docs/requisitos/SRS.md
  A  docs/trazabilidad/matriz.csv
  ```
- **Commit o commits:** `a1f83a1` (creación de `SRS.md` con las referencias narrativas RF-WEB → REQ-F, y de `matriz.csv`, que no incluye esas referencias).
- **Responsable:** Zaida Taipe.
- **Estado:** CERRADA PARCIALMENTE
- **Justificación del estado:** Existe evidencia verificable de una relación documentada entre los antiguos `RF-WEB` y los nuevos `REQ-F` (en el campo Rationale del SRS), lo que constituye una mejora real y trazable. Sin embargo, no es la "matriz de trazabilidad explícita" que la observación pide literalmente — la matriz CSV formal no contiene esa columna de origen. No se afirma que el script de trazabilidad actual (`scripts/validate-traceability.sh`) corrija automáticamente el remapeo histórico, porque no existe esa relación documentada dentro del propio CSV.

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
- **Decisión del equipo:** No hay decisión documentada específica sobre reescribir RF-10.
- **Corrección realizada:** Ninguna verificable en cuanto a la redacción. Según la tabla de origen del SRS actual (`docs/requisitos/SRS.md`, líneas 388-394), RF-10 corresponde al nuevo `REQ-F-017` — *"Generar recomendaciones clínicas informativas basadas en el historial médico, vía servicio de IA"* — con prioridad `Could` y estado `pendiente`. El requisito conserva la misma idea general ("recomendaciones informativas") y **no tiene un "Enunciado" detallado ni criterio de aceptación medible** en el SRS (a diferencia de los requisitos ya implementados REQ-F-001 a REQ-F-012, que sí tienen bloques completos de Enunciado/Rationale/Verificación). Solo aparece en la tabla-resumen de pendientes, sin texto adicional que reduzca la ambigüedad original.
- **Archivos involucrados:** `docs/requisitos/SRS.md` (tabla de pendientes, fila `REQ-F-017`).
- **Evidencia actual:**
  ```
  grep -n "REQ-F-017" docs/requisitos/SRS.md
  394: | REQ-F-017 | Generar recomendaciones clínicas informativas basadas en el
       historial médico, vía servicio de IA. | Could | RF-10 | HU-016 / CU-16 | pendiente |
  ```
  No existe ningún otro bloque en `SRS.md` con "Enunciado", "Rationale" o "Verificación" para `REQ-F-017`.
- **Commit o commits:** Ninguno corrige específicamente la ambigüedad (el requisito se trasladó sin reescritura en `a1f83a1`, junto con el resto de la tabla de pendientes).
- **Responsable:** Zaida Taipe.
- **Estado:** ABIERTA
- **Justificación del estado:** El requisito fue renumerado y clasificado (con historia de usuario y caso de uso asociados), pero no se le añadió un criterio de aceptación verificable que resuelva la ambigüedad señalada por el docente. Se mantiene como observación **leve**, consistente con la calificación original (D3 = 88 %, la más alta después de D2 dentro del bloque D).

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
- **Decisión del equipo:** No se encontró una decisión documentada.
- **Corrección realizada:** Ninguna. Búsqueda exhaustiva en todo `docs/` de archivos DER/ERD:
  ```
  find docs -iname "*der*" -o -iname "*ERD*"
  docs/diagrams/der-biopet
  docs/diagrams/der-biopet/der-biopet.dot
  docs/diagrams/der-biopet/der-biopet.png
  ```
  Solo existen `der-biopet.dot` (fuente Graphviz) y `der-biopet.png` (renderizado **desde Graphviz**, no desde pgAdmin). Se distingue explícitamente:
  1. PNG renderizado desde Graphviz `.dot` → **es lo único que existe**.
  2. Exportación real del ERD Tool de pgAdmin → **no existe ningún archivo con esa procedencia** en ningún punto del historial.

  La única mención de "pgadmin" en todo `docs/` está en `docs/adr/ADR-005-despliegue.md`, y no se refiere a una exportación de diagrama, sino a la ejecución reproducible de la base de datos.
- **Archivos involucrados:** `docs/diagrams/der-biopet/der-biopet.dot`, `docs/diagrams/der-biopet/der-biopet.png`.
- **Evidencia actual:**
  ```
  git log --all --oneline -- "docs/diagrams/der-biopet/*"
  8093b89 Add files via upload
  0a784a0 Add der-biopet.dot diagram file
  ```
  Ambos commits son del lote inicial (2026-06-20, Entrega 1B); ningún commit posterior sustituye o añade una exportación de pgAdmin.
- **Commit o commits:** No aplica — no existe corrección.
- **Responsable:** Fred Beltrán (autor histórico del DER).
- **Estado:** ABIERTA
- **Justificación del estado:** Conforme a la instrucción explícita, no se marca CERRADA porque solo existe la imagen Graphviz; no hay evidencia de una exportación desde pgAdmin 4 ERD Tool en ningún commit del repositorio.

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
- **Decisión del equipo:** No verificable — no se creó el tag en ningún momento posterior.
- **Corrección realizada:** Ninguna.
- **Archivos involucrados:** No aplica (es un objeto de Git, no un archivo).
- **Evidencia actual:**
  ```
  git tag --list
  (sin salida)

  git tag --list "v0.1.0-entrega-1b"
  (sin salida)
  ```
- **Commit o commits:** No aplica — el tag no existe.
- **Responsable:** Jaime Mariscal (gestión del repositorio).
- **Estado:** ABIERTA
- **Justificación del estado:** Se confirmó explícitamente, mediante `git tag --list`, que no existe ningún tag en el repositorio (ni `v0.1.0-entrega-1b` ni ningún otro). **No se creó el tag como parte de esta tarea**, conforme a la restricción explícita de no crear tags. Se conserva exactamente el nombre solicitado por el docente (`v0.1.0-entrega-1b`), sin sustituirlo por `v0.7.0`, `v0.7.1` ni `v0.9.0-rc` (ver Parte 8).

---

# Parte 6 — Recomendaciones adicionales del docente

Las siguientes recomendaciones fueron registradas textualmente en la sección
"MEJORAS PARA SU APRENDIZAJE" de la retroalimentación de la Entrega 1B, con la
precisión explícita del docente: **"no afectan esta nota"**. Esa precisión se
conserva aquí sin ocultarla.

- Mover el pipeline a `.github/workflows/` para que GitHub Actions lo ejecute en cada push → corresponde a **OBS-07** (CERRADA).
- Crear el tag anotado `v0.1.0-entrega-1b` sobre el commit de entrega → corresponde a **OBS-08** (ABIERTA).
- Versionar la colección Postman (`.json`) → corresponde a **OBS-06** (CERRADA).
- Exportar el DER desde pgAdmin 4 (ERD Tool) como PNG de alta resolución para el informe final → corresponde a **OBS-05** (ABIERTA).
- Mantener la participación equilibrada del equipo (advertencia de la Entrega 1B: *"El historial evidencia aportes de Beltrán, Mariscal y Taipe. Mantener este equilibrio en la Entrega 2."* — explícitamente **"no afecta esta nota"**, es una advertencia preventiva, no una observación con corrección de código asociada).

---

# Parte 7 — Fortalezas reconocidas por el docente

### Entrega 1A

- Descripción y alcance del sistema completos.
- Arquitectura C4 Nivel 1 y Nivel 2.
- MER y DDL de PostgreSQL con evidencia de ejecución en pgAdmin.
- Wireframes.
- Cronograma (semanas 5-17) y roles del equipo.
- Referencias académicas verificadas.
- Calidad alta de redacción conforme a ISO/IEC/IEEE 29148 (patrón "deberá", subcriterio D2 = 95 %, "EL MEJOR" según el propio texto del docente).

### Entrega 1B

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
| `v0.1.0-entrega-1b` | Retroalimentación oficial de la Entrega 1B (SGA, Semana 6) — texto literal citado en OBS-08 | No | — | No creado (OBS-08 ABIERTA) | Bajo si se crea sobre el commit correcto de cierre de la Entrega 1B; pero ese commit no ha sido identificado con certeza porque el repositorio evaluado originalmente (`PFC--VET-ENTR1B`) no es este repositorio. |
| `v0.7.0` | Guía de la Tercera Entrega (v0.9.0-rc), no la retroalimentación del SGA | No | `058b1fe` (candidato, ver justificación abajo) | No creado | Medio: `058b1fe` es del repositorio sucesor (`PFC-VET-ENTR3-v0.9.0-rc`), no del repositorio original de la Entrega 1B; etiquetarlo como "v0.7.0" documenta el estado heredado en *este* árbol, no el commit exacto que el docente evaluó. |
| `v0.7.1` | Guía de la Tercera Entrega — cierre formal de la aplicación de observaciones de 1A/1B | No | — | No aplica todavía | Alto si se creara ahora: de las 8 observaciones, solo 2 están CERRADAS, 1 CERRADA PARCIALMENTE, 4 ABIERTAS y 1 NO VERIFICABLE. Crear `v0.7.1` hoy etiquetaría un cierre que no existe. |
| `v0.9.0-rc` | Guía de la Tercera Entrega — tag final de esta entrega | No | — | No aplica todavía | Debe ser el último tag en crearse, después de `v0.7.1`, y solo cuando el resto del trabajo de la Tercera Entrega (bloques A-F de la Guía) esté cerrado, no solo el Bloque 0 de observaciones. |

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
- **Por qué `v0.7.1` requiere primero cerrar las observaciones:** por definición, `v0.7.1` marca el cierre de la aplicación de observaciones de 1A/1B; crearlo con 6 de 8 observaciones sin cerrar por completo invalidaría el propósito del tag.
- **Por qué `v0.9.0-rc` debe crearse al final:** es el tag objetivo de toda la Tercera Entrega (bloques 0 y A-F de la guía), no solo del Bloque 0 de observaciones aquí auditado.

**No se creó ningún tag como parte de esta tarea.**

---

# Parte 9 — Indicadores finales

## Estado global

- **Total de observaciones:** 8
- **CERRADAS:** 3 (OBS-01, OBS-06, OBS-07)
- **CERRADAS PARCIALMENTE:** 1 (OBS-03)
- **ABIERTAS:** 4 (OBS-02, OBS-04, OBS-05, OBS-08)
- **NO VERIFICABLES:** 0

**Porcentaje real de cierre** (solo CERRADA cuenta como cierre completo):

```
porcentaje de cierre = observaciones CERRADAS / 8 × 100
                      = 3 / 8 × 100
                      = 37.5 %
```

## Observaciones que aún bloquean `v0.7.1`

- **OBS-02** (ABIERTA) — falta investigar y documentar qué era RF-07 o por qué se omite.
- **OBS-03** (CERRADA PARCIALMENTE) — falta una columna de origen histórico en `docs/trazabilidad/matriz.csv` que referencie los antiguos `RF-WEB`.
- **OBS-04** (ABIERTA) — falta redactar un enunciado con criterio de aceptación verificable para `REQ-F-017`.
- **OBS-05** (ABIERTA) — falta exportar el DER desde pgAdmin 4 (ERD Tool) como PNG.
- **OBS-08** (ABIERTA) — falta crear el tag `v0.1.0-entrega-1b` sobre el commit correspondiente al cierre de la Entrega 1B.

## Acciones concretas pendientes

1. Exportar el DER desde pgAdmin 4 (ERD Tool) como PNG de alta resolución y versionarlo junto al `.dot` existente (OBS-05). — *Responsable recomendado: Fred Beltrán.*
2. Decidir y documentar qué ocurrió con el requisito RF-07 original (¿se descartó, se fusionó, se reformuló?) y dejar esa decisión trazable en `CHANGELOG-REQ.md` (OBS-02). — *Responsable recomendado: Zaida Taipe.*
3. Añadir al `SRS.md` (o a `matriz.csv`) una columna/tabla explícita que mapee los antiguos `RF-WEB-01/02/04` a los `REQ-F` actuales, no solo el texto narrativo disperso en Rationale (OBS-03). — *Responsable recomendado: Zaida Taipe.*
4. Redactar un enunciado con criterio de aceptación verificable para `REQ-F-017` (antiguo RF-10) (OBS-04). — *Responsable recomendado: Zaida Taipe.*
5. Crear el tag anotado `v0.1.0-entrega-1b` sobre el commit que el equipo determine como cierre real de la Entrega 1B (OBS-08). — *Responsable recomendado: Jaime Mariscal.* **No se ejecuta en esta tarea.**

OBS-01 queda CERRADA y no requiere acción adicional. Solo después de que las
cinco acciones anteriores queden verificablemente cerradas correspondería
evaluar la creación de `v0.7.1`.

---

## Trazabilidad de este documento

- Elaborado en la rama `jaime/observaciones-1a-1b`.
- Fuentes primarias: capturas oficiales del aula virtual SGA (ver sección 3.2).
- Verificado contra el historial real de Git mediante `git show --stat` / `git show --name-status` para cada commit citado.
- No contiene observaciones inventadas ni hashes inexistentes.
