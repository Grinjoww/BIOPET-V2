# Informe de la GA de Unidad IV (Equipo H) — BIOPET

Este directorio contiene el código fuente LaTeX del informe de la actividad
de **GA de la Unidad IV** de la asignatura Aplicaciones Web, sobre el PFC
**BIOPET**.

**Este informe es distinto del informe de la Tercera Entrega del PFC
original** ([`docs/informe/`](../../informe/), equipo BMT). Ese informe no
se modifica ni se sobrescribe: se usó únicamente como referencia de
plantilla (macro `\evidencia`, estilo de bibliografía, estructura de
portada). Ver [`docs/u4/informe/main.tex`](main.tex) para el detalle de qué
se reutilizó y qué se adaptó.

## Estado actual: contenido completo, pendiente de validación/compilación final

Los nueve capítulos del informe ya integran contenido definitivo, incluyendo
los aportes de investigación y revisión crítica de Carvajal Loor Johan
Stalin (SOAP vs REST) y de Fajardo Montes Michael Xavier (tendencias web),
la revisión cruzada consolidada, y las conclusiones y el trabajo futuro
derivados de ambas. No quedan marcadores `% TODO` ni
`\pendienteIntegracion{...}` activos en las secciones.

Lo que sigue pendiente es la etapa de cierre técnico: generar el PDF final
con la cadena de compilación descrita más abajo y validar el resultado
(referencias resueltas, figuras incluidas, tabla de contenidos correcta).
Esa generación **todavía no se ha realizado** en este repositorio.

## Estructura

```text
docs/u4/informe/
├── main.tex                          # documento maestro (portada, preámbulo, \input de cada capítulo)
├── referencias.bib                   # bibliografía IEEE (BibTeX clásico, bibliographystyle{ieeetr})
├── README.md                         # este archivo
└── secciones/
    ├── 01-introduccion.tex           # contexto de la GA, distinción PFC original vs Equipo H — listo
    ├── 02-presentacion-pfc.tex       # qué es BIOPET, arquitectura, stack — listo
    ├── 03-mvc-api-rest.tex           # flujo MVC, endpoints, Swagger/OpenAPI, Postman — listo
    ├── 04-soap-vs-rest.tex           # aporte de Carvajal: SOAP vs REST — listo
    ├── 05-tendencias-web.tex         # aporte de Fajardo: Jamstack, PWA, IA generativa — listo
    ├── 06-revision-cruzada.tex       # consolidación Carvajal + Fajardo — listo
    ├── 07-resultados.tex             # JaCoCo, Lighthouse, SUS, seguridad — listo
    ├── 08-conclusiones.tex           # conclusiones finales — listo
    └── 09-trabajo-futuro.tex         # trabajo futuro priorizado — listo
```

## Datos institucionales y de equipo

Reutilizados de la documentación ya existente en el repositorio (SRS,
README raíz, informe de la Tercera Entrega): Universidad Técnica Estatal de
Quevedo, Facultad de Ciencias de la Computación, Carrera de Software,
asignatura Aplicaciones Web, docente Guerrero Ulloa Gleiston Cicerón.

El informe distingue explícitamente (ver `secciones/01-introduccion.tex`,
sección "Distinción entre el equipo original del PFC y el Equipo H"):

- **Equipo original del PFC BIOPET:** Mariscal Cabrera Jaime Josué, Beltrán
  Montiel Fred Adrián, Taipe Mora Zaida Melissa.
- **Equipo H (GA de Unidad IV):** Mariscal Cabrera Jaime Josué, Carvajal
  Loor Johan Stalin, Fajardo Montes Michael Xavier.

Ningún aporte de código o funcionalidad del PFC se atribuye a Carvajal ni a
Fajardo en este informe.

## Compilación

### Opción recomendada: `latexmk`

```bash
cd docs/u4/informe
latexmk -pdf -interaction=nonstopmode -halt-on-error main.tex
```

### Alternativa: `pdflatex` + `bibtex` manual

```bash
cd docs/u4/informe
pdflatex -interaction=nonstopmode main.tex
bibtex main
pdflatex -interaction=nonstopmode main.tex
pdflatex -interaction=nonstopmode main.tex
```

**El PDF todavía no se ha generado en este repositorio.** El contenido de
los nueve capítulos ya está completo; la generación y validación del PDF
final es el siguiente paso de cierre. Cuando se compile, guardar el
resultado como `docs/u4/informe/informe-u4-equipo-h.pdf` (no versionado
todavía).

## Evidencias condicionales (`\evidencia`)

Igual que en `docs/informe/`, el documento maestro usa
`\IfFileExists` para incluir figuras solo si ya existen, sin dejar huecos
ni texto de marcador si faltan. Las figuras actualmente referenciadas ya
existen en el repositorio y no se copiaron a una carpeta nueva, para no
duplicar evidencia: se referencian con ruta relativa directamente desde
`docs/diagramas/`, `docs/diagrams/` y `docs/u4/evidencias/jaime/`.

## Bibliografía

`referencias.bib` reutiliza (con la misma cita, sin modificar el `.bib`
original) las entradas de `docs/informe/referencias.bib` que siguen siendo
válidas para este informe (OWASP Top 10, RFC 7519, RFC 7807, modelo C4,
SUS de Brooke), y agrega:

- **Fielding, R. T. (2000)** — *Architectural Styles and the Design of
  Network-based Software Architectures*, disertación doctoral, University
  of California, Irvine. Fuente primaria de REST para el capítulo 4.
- Especificación oficial **SOAP 1.2 (W3C)**, para cuando se integre el
  capítulo 4.
- Documentación oficial de **Spring Boot** y **Angular** (stack real de
  BIOPET).
- Especificación oficial **OpenAPI**.

Ninguna entrada usa un blog genérico, un DOI inventado ni una URL sin
verificar.
