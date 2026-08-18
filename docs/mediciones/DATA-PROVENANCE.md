# Procedencia de los datos (Data Provenance) — BIOPET

Este documento complementa a `DATA-DICTIONARY.md` (qué significa cada
variable) documentando, para cada conjunto de datos crudo bajo
`docs/mediciones/`, **de dónde viene, cómo se generó, con qué herramienta
y versión, y qué transformación (si alguna) sufrió antes de llegar al
repositorio**. Es el insumo mínimo para que el paquete de evidencia sea
reproducible y trazable (principios FAIR — Findable, Accessible,
Interoperable, Reusable), en línea con lo ya practicado en
`docs/mediciones/lighthouse/README.md` (que documenta anonimización y
`SHA256SUMS-ORIGINAL.txt`).

No se documenta aquí ningún dato nuevo: es un índice de procedencia sobre
datos que ya existen en el repositorio, verificado contra su origen real.

---

## 1. Rendimiento — `docs/mediciones/perf/`

| Campo | Detalle |
|---|---|
| Generado por | k6 (`k6 run k6/listado-mascotas.js`), 50 VUs, ~30–35 s por corrida |
| Responsable | Fred Beltrán Montiel |
| Fecha de generación | 2026-08-17 (corridas `k6-20260817T*`); corridas anteriores `k6-run{1,2,3}-{frio,caliente}.json` de fecha previa, conservadas para comparación histórica |
| Entrada | Backend real vía `https://localhost:8443` (perfil `tls`, `docker-compose.tls.yml`), no un mock ni un stub |
| Transformación aplicada | Agregación estadística (media, mediana, IC95% con distribución t de Student vía `scipy.stats.t`, percentiles) realizada por `scripts/perf-analysis.py` sobre los JSON crudos de k6, volcada a `REPORT.md` y `grafico.svg` |
| Archivo crudo sin transformar | Los `.json` individuales (`k6-20260817T*.json`) — **no se editan manualmente**; son la salida directa de k6 |
| Cadena de verificación | `REPORT.md` cita el nombre exacto de cada archivo fuente por fila de su tabla, permitiendo recalcular cualquier estadístico desde el crudo |
| Limitación declarada | Corridas ejecutadas contra una sola instancia del backend, sin réplicas (ver README.md, sección "Consideraciones para evolución y despliegue") |

## 2. Caché Redis — `docs/mediciones/redis/`

| Campo | Detalle |
|---|---|
| Generado por | `redis-cli` (`CONFIG GET maxmemory*`, `DBSIZE`, `KEYS mascotas::*`, `TTL <clave>`), ejecutado manualmente contra el contenedor Redis real |
| Responsable | Fred Beltrán Montiel |
| Entrada | Instancia Redis 7 del `docker-compose.yml`, con carga generada por la corrida k6 de referencia |
| Transformación aplicada | Ninguna — son capturas de salida de terminal (`.txt`) tal cual las devolvió `redis-cli` |
| Codificación | UTF-16 LE con terminadores CRLF (verificado con `file`); previsible si la captura se hizo desde PowerShell en Windows — no indica corrupción del dato |
| Limitación declarada | Documenta TTL y existencia de clave bajo carga, pero **no** un hit ratio medido (aciertos/total) — pendiente, ver `docs/requisitos/SRS.md` sección 7 |

## 3. Seguridad — `docs/mediciones/sec/`

| Campo | Detalle |
|---|---|
| Generado por | `scripts/security-evidence.sh` (o `.ps1` en Windows), que combina: `curl`/`curl.exe` y `openssl s_client` contra el stack Docker real, `mvn clean verify` (suite JUnit real), y `docker compose logs backend` para los logs `AUTH_AUDIT` |
| Responsable | Jaime Mariscal Cabrera |
| Fecha de generación | Desde 2026-07-31 (evidencia HTTP real), consolidada en la Tercera Entrega |
| Entrada | Backend real vía TLS (`https://localhost:8443`), no mocks; peticiones HTTP reales para cada uno de los 6 controles OWASP documentados (A01, A02, A03, A05, A07, A09) |
| Transformación aplicada | Ninguna sobre los archivos individuales de `raw/` (`A01-access-control.txt`, etc. — salida cruda de `curl`/`openssl`); los documentos `.md` (`A01-access-control.md`, etc.) narran e interpretan esa salida cruda, citándola |
| Cadena de verificación | Cada afirmación en los `.md` de esta carpeta enlaza al archivo crudo correspondiente en `raw/` |
| Nota de versionado | `docs/mediciones/sec/raw/` no está excluida por `.gitignore` (solo `.gitkeep`); el contenido es evidencia real destinada a versionarse, no una salida local descartable |

## 4. Cobertura JaCoCo — `docs/mediciones/sec/jacoco-summary.md`

| Campo | Detalle |
|---|---|
| Generado por | `mvn clean verify` (fase `verify`, plugin `jacoco-maven-plugin`, regla `BUNDLE` con umbral ≥60% en LINE/BRANCH/COMPLEXITY) |
| Responsable | Jaime Mariscal Cabrera |
| Entrada | Suite de 166 pruebas JUnit 5 + MockMvc reales (`Backend/src/test/java/com/biopet/**`) |
| Transformación aplicada | Ninguna manual — `jacoco-summary.md` resume el reporte HTML/XML que genera JaCoCo en `Backend/target/site/jacoco/`, no versionado por ser artefacto regenerable en cada build |
| Reproducibilidad | Se regenera localmente ejecutando `cd Backend && mvn clean verify`; el resultado citado (87.45% LINE, 67.98% BRANCH, 71.81% COMPLEXITY) corresponde a la corrida más reciente documentada |

## 5. Usabilidad SUS — `docs/mediciones/sus/`

| Campo | Detalle |
|---|---|
| Generado por | Instrumento SUS (Brooke, 1996) aplicado directamente a 18 participantes externos al equipo (P01–P18), según `docs/mediciones/sus/instrumento-sus.md` |
| Responsable | Zaida Taipe Mora |
| Fecha de recolección | Muestra inicial P01–P10 (Tercera Entrega), ampliada con P11–P18 (Entrega Final) para cumplir n≥15 |
| Entrada | Respuestas Likert 1–5 de cada participante a los 10 ítems estándar del SUS, sobre el frontend Angular real de BIOPET |
| Transformación aplicada | Cálculo del puntaje agregado por participante (`sus_score`, método estándar de Brooke: suma de contribuciones × 2.5), realizado por `scripts/analisis-sus.py` sobre `sus-raw.csv`, volcado a `REPORT.md` |
| Archivo crudo sin transformar | `docs/mediciones/sus/sus-raw.csv` — respuestas individuales anonimizadas (código de participante, no nombre) |
| Consentimiento/anonimización | Participantes identificados solo por código (P01–P18); sin datos que permitan reidentificación individual, más allá de edad/sexo/experiencia declarados de forma agregada |

## 6. Lighthouse (calidad web) — `docs/mediciones/lighthouse/`

| Campo | Detalle |
|---|---|
| Generado por | `npx @lhci/cli autorun` (motor Lighthouse 12.1.0), vía `scripts/run-lighthouse.sh`, contra el frontend servido por el contenedor Docker real (`http://localhost:4200`), nunca contra `ng serve` |
| Responsable | Zaida Taipe Mora |
| Fecha de generación | 2026-08-01, 6 corridas oficiales (3 por cada una de las 2 URLs auditadas) |
| Entrada | `/login` y `/mascotas` (esta última redirige a `/login` sin sesión activa, por diseño del `authGuard` — ver `README.md` de esta carpeta) |
| Transformación aplicada | **Única transformación declarada:** sustitución textual del identificador de usuario del sistema operativo en rutas locales embebidas por la herramienta (`C:\Users\<usuario>\...` → `USER_REDACTED`), documentada explícitamente en `README.md`. Puntajes, fechas, URLs y assertions **no fueron alterados** |
| Verificación de integridad | `SHA256SUMS-ORIGINAL.txt` (hash del original antes de anonimizar) y `SHA256SUMS.txt` (hash de los archivos ya en el repositorio), permitiendo confirmar que la única diferencia es la sustitución declarada |
| Archivos crudos | `raw/*.report.html`, `raw/*.report.json`, `raw/manifest.json`, `raw/assertion-results.json` (31 archivos totales, incluyendo 2 corridas adicionales de `/login` no incluidas en el resumen oficial de 3+3, y duplicados `lhr-<timestamp>.*` con el mismo contenido) |
| Cambios posteriores no cubiertos | Ninguno — la re-corrida del 2026-08-18 (`lhci-20260818-0538-*.json`, 12 archivos, perfil móvil y desktop) ya captura el efecto de los dos fixes de SEO (`meta description`, `robots.txt`) aplicados el 2026-08-17. SEO pasó de 82 a 100 en las 12 corridas. |

## 7. Diccionario de datos vs. procedencia — relación entre ambos documentos

`DATA-DICTIONARY.md` responde **qué significa cada variable**;
`DATA-PROVENANCE.md` (este documento) responde **de dónde salió cada
archivo y qué le pasó antes de llegar al repositorio**. Ningún archivo
crudo mencionado aquí fue editado manualmente más allá de las
transformaciones explícitamente declaradas arriba (agregación estadística
documentada, o anonimización textual puntual en el caso de Lighthouse);
editar un crudo sin declararlo invalidaría la evidencia, siguiendo el
mismo criterio ya aplicado en `docs/mediciones/lighthouse/README.md`.

## 8. Procedencia de la re-corrida Lighthouse (2026-08-18)

| Campo | Detalle |
|---|---|
| Generado por | `scripts/run-lighthouse.sh` + `lighthouserc.desktop.js` (nuevo, perfil desktop agregado junto al móvil existente) |
| Responsable | Equipo (corrida ejecutada tras los fixes de `frontend/src/index.html` y `frontend/public/robots.txt`) |
| Fecha de generación | 2026-08-18, lote `lhci-20260818-0538-*` |
| Entrada | Mismo contenedor Docker real (`http://localhost:4200`), rutas `/login` y `/mascotas`, ahora en dos perfiles (móvil simulado y desktop) × 2 rutas × 3 corridas = 12 archivos JSON |
| Transformación aplicada | Ninguna — JSON crudo de `@lhci/cli` sin editar |
| Resultado | SEO 100/100 en las 12 corridas (antes 82/100); confirma que la causa raíz identificada en la corrida del 2026-08-01 (meta description + robots.txt) era correcta y completa |
| Archivo de configuración nuevo | `lighthouserc.desktop.js` — no existía en la corrida anterior; se agregó junto con la re-ejecución |

## 9. Pendientes de procedencia (no se inventa lo que falta)

- **Hit ratio de Redis:** no hay todavía una captura de `INFO stats`
  (`keyspace_hits`/`keyspace_misses`) con procedencia documentada; solo
  existe evidencia de TTL y `DBSIZE`. Sigue siendo la única brecha real
  de procedencia de datos de medición en este documento.
- **PRISMA (trabajos relacionados):** CERRADO. Los 10 estudios primarios
  (3 de Zaida, 4 de Jaime, 3 de Fred) tienen procedencia documentada:
  Zaida en `docs/informe/borradores/zaida/estudios-primarios-zaida.md`,
  Jaime en `docs/informe/borradores/jaime/trabajos-relacionados.md` y
  `referencias-candidatas.md`, Fred en
  `docs/investigacion/handoff-fred-trabajos-relacionados.md` y
  `handoff-fred-referencias.bib` (11 DOI verificados uno a uno,
  2026-08-17). Las 24 referencias nuevas de los tres bloques ya están
  integradas en `docs/informe/referencias.bib` (39 entradas totales).
