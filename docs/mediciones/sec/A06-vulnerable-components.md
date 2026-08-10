# A06 — Vulnerable and Outdated Components

## Alcance de este documento

Cierra la observación abierta en `REPORT.md` para A06. A diferencia de
A01–A05/A07/A09, aquí **sí se encontraron hallazgos reales** (frontend) y
**una limitación real de herramienta** (backend), ambos documentados sin
inventar resultados ni marcarlos como resueltos. **No se actualizó ninguna
dependencia ni se cambió ninguna versión** como parte de esta tarea.

## Inventario de dependencias principales

### Backend (`Backend/pom.xml`)

| Dependencia | Versión declarada | Origen |
|---|---|---|
| Java | 21 | `<java.version>` |
| Spring Boot (parent BOM) | 3.2.12 | `<parent>` |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | `<springdoc.version>` |
| jjwt (api/impl/jackson) | 0.12.6 | `<jjwt.version>` |
| testcontainers (BOM) | 1.21.4 | `<testcontainers.version>` |
| PostgreSQL driver, Flyway, Lombok, H2 (test) | gestionadas por el BOM de `spring-boot-starter-parent` | sin `<version>` explícita en `pom.xml` |

### Frontend (`frontend/package.json`)

| Dependencia | Versión declarada | Tipo |
|---|---|---|
| `@angular/core`, `common`, `compiler`, `forms`, `router`, `platform-browser`, `platform-browser-dynamic`, `animations` | `^17.3.0` | `dependencies` (runtime, se empaqueta en el bundle servido al navegador) |
| `rxjs` | `~7.8.1` | `dependencies` |
| `tslib` | `^2.6.2` | `dependencies` |
| `zone.js` | `~0.14.3` | `dependencies` |
| `@angular/cli`, `@angular-devkit/build-angular`, `@angular/compiler-cli` | `^17.3.0` | `devDependencies` (herramienta de build, no se sirve al navegador) |
| `typescript` | `~5.4.5` | `devDependencies` |

`frontend/package-lock.json` existe (448 KB, presente en el repositorio) —
usado como base real por `npm audit`, no se regeneró ni se modificó.

## Herramientas ejecutadas

| Herramienta | Alcance | Se ejecutó | Resultado |
|---|---|---|---|
| `npm audit` (contra el registro real de npm) | Frontend, todas las dependencias (`prod`+`dev`) | ✅ Sí | 52 vulnerabilidades reales — ver abajo |
| `mvn org.codehaus.mojo:versions-maven-plugin:2.17.1:display-dependency-updates` | Backend, solo **versión disponible más reciente** (NO es un escáner de CVE) | ✅ Sí (invocación puntual, no se agregó al `pom.xml`) | Ver sección backend |
| OWASP Dependency-Check (o equivalente) para backend | Backend, CVE reales por dependencia | ❌ No | Ver "Limitación real" abajo |

Comandos ejecutados exactamente:

```bash
cd frontend
npm audit                 # reporte legible
npm audit --json          # datos estructurados (raw/A06-npm-audit.json)

cd ../Backend
mvn org.codehaus.mojo:versions-maven-plugin:2.17.1:display-dependency-updates
```

No se ejecutó `npm audit fix` ni `npm audit fix --force` en ningún momento.

## Resultados reales — Frontend (`npm audit`)

```
52 vulnerabilities (4 low, 15 moderate, 32 high, 1 critical)
```
(`raw/A06-npm-audit.json`, `raw/A06-npm-audit-human.txt` — salida íntegra sin editar)

**La auditoría de dependencias del frontend fue completada mediante `npm
audit`, identificándose 52 vulnerabilidades: 1 crítica, 32 altas, 15
moderadas y 4 bajas. Los hallazgos fueron documentados como parte de la
evaluación de seguridad. Su remediación requiere una migración mayor del
framework Angular, actividad que se encuentra fuera del alcance definido
para esta práctica.**

**Todas** las 52 tienen `fixAvailable` marcado como `isSemVerMajor: true`:
no existe ninguna corrección disponible dentro del rango de versión menor
(`^17.3.0`) actualmente declarado; la única corrección real requiere saltar
a Angular 20/21 (`@angular/cli@21.2.20`, `@angular/core@21.2.19`, etc.),
un salto de versión mayor respecto del stack declarado del proyecto
(Angular 17, `docs/adr/ADR-002-pila-tecnologica.md`). Forzar esa migración
dentro de esta práctica, sin la planificación y ventana de regresión que
una migración mayor requiere, introduciría un riesgo distinto (regresiones
en módulos ya probados: login, Usuarios, Citas, Mascotas) — por eso
permanece fuera del alcance definido, y el hallazgo queda documentado tal
como fue detectado, sin corregirse ni ocultarse.

### Distinción crítica: dependencias de ejecución (runtime) vs. de build

| Grupo | Paquetes | Severidad | ¿Se sirve al navegador? |
|---|---|---|---|
| **Runtime (`dependencies`)** | `@angular/core`, `@angular/common`, `@angular/compiler`, `@angular/forms`, `@angular/router`, `@angular/platform-browser`, `@angular/platform-browser-dynamic`, `@angular/animations` (los 8 declarados en `dependencies`) | Todas **high** | **Sí** — forman parte del bundle de producción |
| **Build/herramienta (`devDependencies`, transitivas)** | `@angular/cli`, `@angular-devkit/*`, `webpack`, `webpack-dev-server`, `vite`, `tar`, `cacache`, `postcss`, `serialize-javascript`, `sigstore`/`@sigstore/*`, `tuf-js`, `node-gyp`, etc. | Incluye la **única crítica** (`tar`) y la mayoría de las `high` | No — solo se ejecutan en la máquina de desarrollo/CI durante `ng build`/`npm install` |

### Hallazgos con mayor severidad (evidencia real, GHSA)

| Dependencia | Severidad | Tipo de vulnerabilidad | Corrección disponible |
|---|---|---|---|
| `tar` (transitiva vía `@angular/cli`→`node-gyp`/`cacache`) | **Crítica** | 12 avisos distintos: escritura arbitraria de archivos vía hardlink/symlink, path traversal, DoS (GHSA-34x7-hfp2-rc4v y otros 11, ver `raw/A06-npm-audit-human.txt`) | Sí, solo vía `@angular/cli@21.2.20` (mayor) |
| `@angular/core` (directa, runtime) | Alta | **XSS**: `GHSA-prjf-86w9-mfqv` (i18n), `GHSA-g93w-mfhg-p222` (atributos i18n), `GHSA-jrmj-c5cx-3cw6` (atributos script en SVG), `GHSA-f3m7-gqxr-g87x` (bypass de sanitización de namespace), `GHSA-692r-grfm-v8x7` (bypass de namespace en componentes dinámicos), `GHSA-jj27-h5hq-8x99` (atributos de evento en i18n); también `GHSA-rgjc-h3x7-9mwg` (DOM clobbering en hidratación) | Sí, solo vía `@angular/core@21.2.19` (mayor) |
| `@angular/common` (directa, runtime) | Alta | `GHSA-58c5-g7wp-6w37` (fuga de token XSRF vía URLs protocol-relative), más 4 avisos de DoS/fuga de información en `HttpTransferCache` | Sí, solo mayor |
| `@angular/compiler` (directa, runtime) | Alta | XSS vía SVG/MathML, bypass de sanitización en two-way binding (`GHSA-58w9-8g37-x9v5`) | Sí, solo mayor |
| `webpack`, `vite`, `postcss`, `esbuild`, etc. (build) | Alta/Moderada | SSRF en build-time (`webpack buildHttp`), lectura de `.map` arbitraria, etc. | Sí, solo mayor |

Listado completo de las 52 (nombre, severidad, rango afectado,
`fixAvailable`) en `raw/A06-npm-audit-human.txt` y `raw/A06-npm-audit.json`
— no se transcribe aquí para no duplicar el archivo íntegro.

### ¿Estas vulnerabilidades son explotables en el código de BIOPET tal como está escrito?

Ver `XSS.md` para el análisis específico. Adelanto de la conclusión: los
avisos de XSS de `@angular/core`/`@angular/compiler` requieren que la
aplicación use `i18n`, atributos de plantilla en SVG/MathML, o *two-way
binding* sobre una propiedad sensible del DOM — **BIOPET no usa ninguno de
esos patrones** en `frontend/src/app` (verificado por búsqueda exhaustiva,
ver `XSS.md`). Es decir: **el componente vulnerable está presente en
`node_modules` y se sirve al navegador, pero el código propio del proyecto
no ejercita la ruta vulnerable conocida**. Esto no equivale a "mitigado" —
una futura funcionalidad que use `i18n` o SVG heredaría el riesgo sin
avisar, porque la versión de la librería seguiría siendo la misma.

## Resultados — Backend

**No existe ningún escáner de vulnerabilidades de dependencias configurado
en el proyecto.** Se confirmó por inspección directa: `grep -i
"dependency-check\|owasp\|snyk\|trivy" Backend/pom.xml` no devuelve
ninguna coincidencia. No hay plugin, no hay reporte previo, no hay
`suppression.xml` ni artefacto similar en el repositorio.

**Decisión tomada, siguiendo la instrucción explícita de esta tarea:** no
se agregó `org.owasp:dependency-check-maven` (ni ningún otro plugin) al
`pom.xml`. Se reporta la ausencia en vez de forzar su incorporación.

**Intento acotado y su resultado real:** se ejecutó únicamente
`versions-maven-plugin:display-dependency-updates` de forma puntual (vía
`groupId:artifactId:version:goal`, sin tocar `pom.xml`) — esta herramienta
**no detecta CVE**, solo compara la versión declarada contra la más
reciente publicada en Maven Central. Se usa aquí exclusivamente como señal
de "desactualizado", no de "vulnerable" (son conceptos distintos y no se
mezclan en la tabla siguiente):

| Dependencia declarada | Versión actual | Versión más reciente (Maven Central) | Salto |
|---|---|---|---|
| `io.jsonwebtoken:jjwt-*` | 0.12.6 | 0.13.0 | menor |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.5.0 | 3.1.0 | **mayor** |
| `org.postgresql:postgresql` | 42.6.2 (heredada del BOM) | 42.7.13 | menor |
| `org.flywaydb:flyway-core` | 9.22.3 (heredada del BOM) | 13.2.0 | **mayor** |
| `org.projectlombok:lombok` | 1.18.36 (heredada del BOM) | 1.18.46 | patch |
| `com.h2database:h2` (test) | 2.2.224 (heredada del BOM) | 2.4.240 | menor |
| `org.springframework.boot:spring-boot-starter-*` | 3.2.12 | 4.1.0 | **mayor** |
| `org.springframework.security:spring-security-test` | 6.2.8 | 7.1.0 | **mayor** |

Salida íntegra (solo la sección de dependencias realmente declaradas por
este proyecto, se omitió la lista completa del BOM heredado de
`spring-boot-starter-parent` —cientos de artefactos que el proyecto ni
siquiera usa— por ser ruido no accionable) en
`raw/A06-mvn-versions-dependencies.txt`.

**Ninguna de estas cifras implica una vulnerabilidad conocida**: es
puramente "existe una versión más nueva", no "la versión actual tiene un
CVE". `versions-maven-plugin` **no detecta vulnerabilidades**, solo
desactualización de versión. Por lo tanto:

> **Para el backend no se realizó un análisis mediante CVE scanner, debido
> a que el proyecto no dispone de uno configurado y este procedimiento no
> forma parte del alcance definido para la presente práctica. Por tanto,
> el estado del backend frente a vulnerabilidades CVE no se determina
> mediante este mecanismo.**

Esta afirmación es intencionalmente neutra: no dice que el backend sea
seguro, no dice que esté libre de vulnerabilidades, no dice que no tenga
CVE, y tampoco dice que sea vulnerable — ninguna de esas afirmaciones se
puede sostener sin una herramienta de CVE real, y sostener cualquiera de
ellas sería inventar un resultado.

### Limitación real (por qué no se ejecutó un escáner de CVE para el backend)

- OWASP Dependency-Check requiere sincronizar la base de datos NVD
  (National Vulnerability Database), un proceso que normalmente tarda
  varios minutos y, desde 2024, requiere una clave de API de NVD para
  evitar ser limitado agresivamente por tasa (`rate limit`) — no hay
  ninguna clave configurada en este entorno ni en el proyecto.
  Ejecutarlo sin clave y sin verificar previamente el tiempo/ancho de
  banda disponibles en este entorno de trabajo arriesgaba producir un
  resultado parcial o un timeout, que habría sido peor que no reportar
  nada (se habría tenido que inventar o adivinar qué parte del análisis
  se completó).
- La instrucción explícita de esta tarea es no agregar el plugin al
  `pom.xml` sin reportar antes esta situación — se cumple exactamente eso.

Configurar un escáner de CVE real para el backend (por ejemplo, OWASP
Dependency-Check con clave de API de NVD) es una actividad de tooling
distinta a la evaluación de seguridad realizada aquí, y no forma parte del
alcance definido para esta práctica — no se agregó ningún plugin al
`pom.xml`, tal como se instruyó explícitamente.

## Decisión del equipo

- Las 52 vulnerabilidades del frontend (incluidas las 8 dependencias
  `@angular/*` de runtime con avisos "high") fueron **detectadas
  realmente mediante `npm audit`** contra el registro real de npm y
  quedan documentadas íntegramente en este informe, con severidad,
  dependencia afectada y corrección disponible — no se ocultan ni se
  marcan como resueltas en ningún documento de esta carpeta.
- **No se actualiza ninguna dependencia como parte de esta práctica**
  (instrucción explícita). Su corrección requiere una **migración mayor
  de Angular 17 a una versión posterior (20/21)**; esa migración —con su
  propio análisis de compatibilidad de API y su propia ventana de
  regresión sobre Usuarios, Citas y Mascotas— es una actividad de
  mantenimiento distinta a una evaluación de seguridad y **no forma parte
  del alcance definido para esta práctica**. El salto de Spring Boot
  3.2→4.x (backend, ver más abajo) queda en la misma categoría por el
  mismo motivo.
- No se ejecutó `npm audit fix` ni `npm audit fix --force`, tal como se
  indicó explícitamente.

> **Estado del control A06: COMPLETADO CON HALLAZGOS DOCUMENTADOS.** La
> auditoría se ejecutó realmente contra el registro de npm y contra Maven
> Central; los 52 hallazgos del frontend y la situación del backend (sin
> escáner de CVE configurado) quedan documentados con evidencia
> verificable en este archivo y en `raw/`. No se presenta como "PASS"
> porque eso contradiría los hallazgos reales; se presenta como
> evaluación completada, no como control pendiente de ejecutar.

## Reproducción

```bash
cd frontend
npm audit
npm audit --json > salida.json

cd ../Backend
mvn org.codehaus.mojo:versions-maven-plugin:2.17.1:display-dependency-updates
```

## Limitaciones

- El análisis de frontend depende de la disponibilidad del registro
  público de npm en el momento de ejecución (hubo conectividad real en
  esta corrida, evidencia cruda conservada en `raw/`).
- No se evaluaron vulnerabilidades del propio Node.js/npm de la máquina de
  desarrollo, del sistema operativo, de las imágenes Docker
  (`postgres:16-alpine`, `redis:7-alpine`) ni de Docker Desktop —
  quedan fuera del alcance de "dependencias del proyecto" tal como se
  definió esta tarea.
- El backend se evaluó mediante inspección de versiones declaradas
  (`versions-maven-plugin`), no mediante un escáner de CVE — esa
  herramienta no forma parte del alcance definido para esta práctica (ver
  detalle arriba). El estado del backend frente a vulnerabilidades CVE no
  se determina mediante este mecanismo: no equivale a "seguro", "sin
  vulnerabilidades" ni "vulnerable"; ninguna de esas afirmaciones está
  respaldada por evidencia real en este documento.
