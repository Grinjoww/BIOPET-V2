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

## Estado actual: borrador estructural

Esta es una **estructura preparada**, no un informe terminado. Los
capítulos que dependen de aportes de Carvajal Loor Johan Stalin y Fajardo
Montes Michael Xavier (investigación SOAP vs REST, investigación de
tendencias web, revisión cruzada consolidada) y las conclusiones finales
contienen marcadores `% TODO` explícitos en el `.tex` y un aviso visible
`[Pendiente de integración -- ...]` en el propio PDF, en lugar de contenido
definitivo o de relleno. **No se ha generado todavía el PDF final** de esta
entrega.

Los capítulos 1 (introducción), 2 (presentación del PFC), 3 (MVC y API
REST) y 7 (resultados) sí contienen contenido inicial redactado a partir de
evidencia real y verificable del repositorio actual de BIOPET.

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
    ├── 04-soap-vs-rest.tex           # TODO: aporte de Carvajal
    ├── 05-tendencias-web.tex         # TODO: aporte de Fajardo
    ├── 06-revision-cruzada.tex       # TODO: consolidación Carvajal + Fajardo
    ├── 07-resultados.tex             # JaCoCo, Lighthouse, SUS, seguridad — listo
    ├── 08-conclusiones.tex           # TODO: depende de 4, 5, 6 y 7
    └── 09-trabajo-futuro.tex         # TODO: depende de 6 y 8
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

**No se generó el PDF en esta tarea** (explícitamente fuera de alcance
mientras falten los aportes de Carvajal y Fajardo). Cuando se compile,
guardar el resultado como `docs/u4/informe/informe-u4-equipo-h.pdf` (no
versionado todavía).

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
