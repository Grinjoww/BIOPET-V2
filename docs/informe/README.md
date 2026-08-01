# Informe final --- Tercera Entrega de BIOPET

Este directorio contiene el código fuente LaTeX editable del informe
final académico conjunto de BIOPET (equipo BMT). El PDF compilado se
publica en `docs/informe-entrega-3.pdf` (un nivel arriba de esta carpeta),
**únicamente cuando se compiló realmente**.

## Requisitos

- Una instalación de **TeX Live** o **MiKTeX** con BibTeX clásico.
  No se usa ningún paquete experimental ni que requiera herramientas
  externas (no hay `minted`/Pygments, no hay `shell-escape`).
- Todos los paquetes usados (`babel`, `geometry`, `graphicx`, `float`,
  `booktabs`, `longtable`, `hyperref`, `fancyhdr`, `underscore`,
  `pdflscape`, `amsmath`/`amssymb`, `enumitem`) son estándar y vienen
  incluidos en cualquier instalación completa de TeX Live o se
  instalan automáticamente bajo demanda en MiKTeX.

## Compilación

### Opción recomendada: `latexmk`

```bash
cd docs/informe
latexmk -pdf -interaction=nonstopmode -halt-on-error informe-entrega-3.tex
```

### Alternativa: `pdflatex` + `bibtex` manual

```bash
cd docs/informe
pdflatex -interaction=nonstopmode informe-entrega-3.tex
bibtex informe-entrega-3
pdflatex -interaction=nonstopmode informe-entrega-3.tex
pdflatex -interaction=nonstopmode informe-entrega-3.tex
```

(Se ejecuta `pdflatex` dos veces al final para resolver referencias
cruzadas, índice y citas de forma estable, además de la primera
pasada.)

### Copiar el resultado final

```bash
cp informe-entrega-3.pdf ../informe-entrega-3.pdf
```

El PDF esperado por la Tercera Entrega vive en `docs/informe-entrega-3.pdf`
(no dentro de `docs/informe/`).

## Organización de las secciones

```
docs/informe/
├── informe-entrega-3.tex      # documento maestro (portada, preambulo, \input de cada capitulo)
├── referencias.bib            # bibliografia IEEE (BibTeX clasico, bibliographystyle{ieeetr})
├── README.md                  # este archivo
├── .gitignore                 # auxiliares de compilacion (.aux/.log/.toc/...), no el PDF final
├── secciones/
│   ├── 01-resumen-ejecutivo.tex
│   ├── 02-estado-sistema.tex
│   ├── 03-arquitectura-c4.tex
│   ├── 04-trazabilidad.tex
│   ├── 05-protocolo-experimental.tex
│   ├── 06-resultados-jaime.tex
│   ├── 07-resultados-fred.tex
│   ├── 08-resultados-zaida.tex
│   ├── 09-amenazas-validez.tex
│   ├── 10-etica.tex
│   ├── 11-credit.tex
│   ├── 12-conclusiones.tex
│   └── 13-anexos.tex
└── figuras/
    ├── compartidas/   # evidencia compartida por el equipo (ej. C4 Nivel 2)
    ├── jaime/
    ├── fred/
    └── zaida/
```

Cada capítulo del PDF corresponde a exactamente un archivo de
`secciones/`, incluido en `informe-entrega-3.tex` mediante `\input`. Para
editar el contenido de un integrante, edita **solo** el archivo de
`secciones/` que le corresponde; no es necesario tocar el documento
maestro para cambios de contenido.

## Cómo funcionan las evidencias (`\IfFileExists`)

El documento maestro define un comando auxiliar:

```latex
\newcommand{\evidencia}[3]{%
  \IfFileExists{#1}{%
    \begin{figure}[H]
      \centering
      \includegraphics[width=0.82\textwidth]{#1}
      \caption{#2}
      \label{#3}
    \end{figure}
  }{}%
}
```

`\IfFileExists` es un comando nativo del núcleo de LaTeX (no requiere
ningún paquete). Si el archivo de la ruta indicada **no existe**, no se
imprime absolutamente nada: ni un hueco, ni un marcador visible, ni texto
de "captura pendiente". Si el archivo **sí existe** (porque ya copiaste tu
captura ahí), la figura aparece automáticamente con su caption y su label,
sin que sea necesario editar el texto de la sección.

Cada uso de `\evidencia{ruta}{caption}{label}` está precedido, en el
`.tex`, por un comentario interno con el formato:

```latex
% EVIDENCIA-<RESPONSABLE>-<NUMERO>
% Captura: <descripcion breve>
% Ruta esperada: figuras/<responsable>/<NN>-<slug>.png
```

Esos comentarios **no aparecen en el PDF** (son comentarios de LaTeX,
ignorados por el compilador); solo son visibles editando el `.tex`. Sirven
como índice de qué captura falta, dónde debe colocarse y con qué nombre
exacto de archivo.

## Convención de nombres de evidencias

| Responsable | Prefijo de carpeta | Ejemplo |
|---|---|---|
| Compartida (equipo) | `figuras/compartidas/` | `c4-contenedores.png` |
| Jaime | `figuras/jaime/` | `01-maven-verify.png` |
| Fred | `figuras/fred/` | `01-docker-healthy.png` |
| Zaida | `figuras/zaida/` | `01-frontend-login.png` |

El nombre de archivo debe coincidir **exactamente** (mayúsculas, guiones,
extensión `.png`) con la ruta indicada en el comentario
`% Ruta esperada: ...` que precede a cada `\evidencia{...}` en el `.tex`
correspondiente. Si el nombre no coincide, `\IfFileExists` no la
encontrará y la figura seguirá sin aparecer.

## Lista de evidencias pendientes de incorporación

### Compartidas
| # | Ruta esperada | Descripción |
|---|---|---|
| C4-L2 | `figuras/compartidas/c4-contenedores.png` | Diagrama C4 Nivel 2 (contenedores). Ya existe una imagen renderizada en `docs/diagrams/c4-contenedores/c4-contenedores.png`; solo falta copiarla a esta ruta. |

### Jaime
| # | Ruta esperada | Descripción |
|---|---|---|
| 01 | `figuras/jaime/01-maven-verify.png` | `mvn clean verify` con 109 pruebas y `BUILD SUCCESS`. |
| 02 | `figuras/jaime/02-jacoco-resumen.png` | Resumen HTML de JaCoCo. |
| 03 | `figuras/jaime/03-tls-openssl.png` | OpenSSL mostrando TLSv1.3 y `TLS_AES_256_GCM_SHA384`. |
| 04 | `figuras/jaime/04-security-headers.png` | `curl` HTTPS con HSTS y cabeceras de seguridad. |
| 05 | `figuras/jaime/05-postman-401.png` | Postman con respuesta 401 ProblemDetail. |
| 06 | `figuras/jaime/06-postman-403.png` | Postman con respuesta 403 ProblemDetail. |
| 07 | `figuras/jaime/07-rate-limit-429.png` | Sexto intento de login con 429 y `Retry-After`. |
| 08 | `figuras/jaime/08-cookie-attributes.png` | Atributos de cookies, sin exponer valores. |
| 09 | `figuras/jaime/09-auth-audit.png` | Log `AUTH_AUDIT` sin secretos. |
| 10 | `figuras/jaime/10-c4-nivel-3.png` | C4 Nivel 3 renderizado. Ya existe una imagen renderizada en `docs/diagrams/c4-componentes-backend/c4-componentes-backend.png`; solo falta copiarla a esta ruta. |

### Fred
| # | Ruta esperada | Descripción |
|---|---|---|
| 01 | `figuras/fred/01-docker-healthy.png` | Los 4 servicios en estado `healthy` tras `make up`. |
| 02 | `figuras/fred/02-postgresql-procedimiento.png` | Ejecución de `fn_resumen_mascotas_por_especie` como `biopet_app`. |
| 03 | `figuras/fred/03-k6-cold.png` | Salida de consola de una corrida de k6 en frío. |
| 04 | `figuras/fred/04-k6-warm.png` | Salida de consola de una corrida de k6 en caliente. |
| 05 | `figuras/fred/05-redis-stats.png` | `redis-cli DBSIZE` de la clave de caché. |
| 06 | `figuras/fred/06-performance-report.png` | Vista del reporte agregado de rendimiento. |

### Zaida
| # | Ruta esperada | Descripción |
|---|---|---|
| 01 | `figuras/zaida/01-frontend-login.png` | Pantalla de login del frontend. |
| 02 | `figuras/zaida/02-crud-mascotas.png` | CRUD de mascotas en el frontend. |
| 03 | `figuras/zaida/03-responsive.png` | Vista responsive del frontend. |
| 04 | `figuras/zaida/04-lighthouse.png` | Reporte Lighthouse (cuando se ejecute). |
| 05 | `figuras/zaida/05-sus-resultados.png` | Resultados agregados de SUS (cuando se ejecute). |
| 06 | `figuras/zaida/06-accesibilidad.png` | Detalle de accesibilidad automática (cuando se ejecute). |

## Instrucciones para Fred y Zaida

1. **No es necesario instalar nada nuevo para revisar el contenido**: los
   archivos `.tex` de `secciones/` son texto plano legible; puedes revisar
   tu capítulo (`07-resultados-fred.tex` o `08-resultados-zaida.tex`)
   directamente en tu editor.
2. Si detectas una cifra, ruta o afirmación de tu bloque que no coincide
   con la evidencia real (por ejemplo, un archivo renombrado o una nueva
   medición ya ejecutada), edita **solo** tu archivo de sección
   correspondiente; no es necesario ni recomendable editar
   `informe-entrega-3.tex` para eso.
3. Para agregar tus capturas de pantalla, guárdalas con el nombre exacto
   indicado en la tabla de arriba, dentro de tu carpeta
   (`figuras/fred/` o `figuras/zaida/`). No necesitas editar ningún
   `.tex`: la figura aparecerá automáticamente en la siguiente
   compilación gracias a `\IfFileExists`.
4. Si necesitas una figura adicional no listada aquí, agrega un nuevo
   bloque `% EVIDENCIA-<TU NOMBRE>-NN` + `\evidencia{...}` en tu propia
   sección, siguiendo el mismo patrón que las ya existentes.

## No escribir secretos en el informe

**Nunca** incluyas en el `.tex`, en el `.bib`, en el README ni en ninguna
captura de `figuras/`: contraseñas con valor, JSON Web Tokens completos
(`eyJ...`), valores de cookies, el encabezado `Authorization: Bearer` con
valor real, claves privadas, ni el identificador `jti` de un token real.
Cuando una captura de pantalla deba mostrar una cookie o un token, recorta
o difumina el valor antes de guardarla; el texto del informe ya está
escrito para describir únicamente los **atributos** (`HttpOnly`, `Secure`,
`SameSite`), nunca el valor.

## Añadir capturas en la fase final sin editar las secciones

El flujo previsto es:

1. Ejecutar la evidencia real (comando, request de Postman, medición).
2. Guardar la captura con el nombre exacto de la tabla de arriba, en la
   carpeta de figuras que corresponda.
3. Recompilar (`latexmk -pdf ...` o el flujo manual de `pdflatex`/`bibtex`).
4. La figura aparece automáticamente, con su caption y su label ya
   redactados: **no hace falta editar ningún archivo `.tex`** solo para
   incorporar una captura que ya tiene su bloque `\evidencia{...}`
   preparado.

Solo se necesita editar un `.tex` si el número de capturas cambia (por
ejemplo, se necesita una evidencia adicional no prevista), no para las ya
listadas en este README.
