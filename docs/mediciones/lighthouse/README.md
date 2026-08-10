# Evidencia Lighthouse — BIOPET

## Propósito de la medición

Esta carpeta documenta la evidencia de calidad web automatizada (Lighthouse) disponible para el frontend Angular de BIOPET, sobre las categorías Performance, Accessibility, Best Practices y SEO. El objetivo es dejar constancia, de forma trazable y verificable, de qué se solicitó, qué se auditó realmente, con qué configuración, qué resultados se obtuvieron y qué umbrales se cumplieron o no.

## Procedencia de la evidencia

La evidencia corresponde a una ejecución previa realizada durante el desarrollo del PFC y fue incorporada a este repositorio para conservar su trazabilidad. Los archivos conservados en [`raw/`](raw/) conservan el **contenido técnico completo** de la salida original de Lighthouse CI (`npx @lhci/cli autorun`), generada de forma local con la configuración declarada en [`lighthouserc.js`](../../../lighthouserc.js).

Antes de su incorporación al repositorio se realizó **únicamente una anonimización de rutas locales del sistema operativo**, sustituyendo el identificador de usuario del equipo de ejecución (presente en rutas de archivo tipo `C:\Users\<usuario>\...` embebidas por la propia herramienta en trazas de error internas) por el marcador `USER_REDACTED`. Los resultados, puntajes, fechas, URLs, assertions y el resto del contenido técnico de Lighthouse **no fueron alterados**. Por esta razón, `raw/` **no es una copia byte a byte** del original: es una copia técnicamente equivalente, con esa única sustitución textual aplicada. La trazabilidad hacia el original se conserva mediante `SHA256SUMS-ORIGINAL.txt` (ver [Integridad de los archivos](#integridad-de-los-archivos)).

## Configuración utilizada

- **Motor Lighthouse:** 12.1.0 (campo `lighthouseVersion` de los propios reportes).
- **Corridas por página (`numberOfRuns`):** 3.
- **Perfil de medición:** móvil simulado (`throttlingMethod: simulate`), preset equivalente a Slow 4G + CPU 4x slowdown.
- **Auditoría omitida:** `uses-http2` (el contenedor de desarrollo sirve HTTP/1.1 vía Nginx sin TLS local).

**Umbrales configurados** (`lighthouserc.js`, nivel `error`):

```text
Performance    >= 0.80
Accessibility  >= 0.90
Best Practices >= 0.90
SEO            >= 0.90
```

## Páginas evaluadas

`lighthouserc.js` (`collect.url`) solicita dos rutas:

- `http://localhost:4200/login`
- `http://localhost:4200/mascotas`

**Importante:** la solicitud a `/mascotas` no audita la vista autenticada de Mascotas. La SPA protege esa ruta con `authGuard`, que ejecuta `GET /api/usuarios/me`; sin una cookie de sesión válida (Lighthouse no inicia sesión antes de auditar), el guard redirige de inmediato a `/login`. En consecuencia, Lighthouse termina cargando y auditando el contenido de `/login`, no el de `/mascotas`. Este comportamiento es el esperado y está documentado en el propio `lighthouserc.js`; no es un error de la medición, pero sí determina qué fue realmente auditado.

## Corridas oficiales

`manifest.json` identifica el conjunto oficial evaluado: **6 corridas**, 3 por cada solicitud.

| Solicitud | Corrida | Resultado de navegación | Performance | Accessibility | Best Practices | SEO | Reporte en `raw/` |
|---|---|---|---:|---:|---:|---:|---|
| `/login` | 1/3 | `/login` (directo) | 93 | 91 | 100 | 82 | [`raw/localhost-_login-2026_08_01_04_57_49.report.html`](raw/localhost-_login-2026_08_01_04_57_49.report.html) |
| `/login` | 2/3 | `/login` (directo) | 94 | 91 | 100 | 82 | [`raw/localhost-_login-2026_08_01_04_58_01.report.html`](raw/localhost-_login-2026_08_01_04_58_01.report.html) |
| `/login` | 3/3 (representativa) | `/login` (directo) | 94 | 91 | 100 | 82 | [`raw/localhost-_login-2026_08_01_04_58_12.report.html`](raw/localhost-_login-2026_08_01_04_58_12.report.html) |
| `/mascotas` | 1/3 (representativa) | redirige a `/login` sin sesión | 92 | 91 | 96 | 82 | [`raw/localhost-_mascotas-2026_08_01_04_58_23.report.html`](raw/localhost-_mascotas-2026_08_01_04_58_23.report.html) |
| `/mascotas` | 2/3 | redirige a `/login` sin sesión | 92 | 91 | 96 | 82 | [`raw/localhost-_mascotas-2026_08_01_04_58_34.report.html`](raw/localhost-_mascotas-2026_08_01_04_58_34.report.html) |
| `/mascotas` | 3/3 | redirige a `/login` sin sesión | 92 | 91 | 96 | 82 | [`raw/localhost-_mascotas-2026_08_01_04_58_53.report.html`](raw/localhost-_mascotas-2026_08_01_04_58_53.report.html) |

Fuente del índice y del resumen por corrida: [`raw/manifest.json`](raw/manifest.json). Las corridas de `/mascotas` y las de `/login` obtienen puntajes ligeramente distintos (92 vs. 94 en Performance, 96 vs. 100 en Best Practices) pese a terminar ambas en `/login`, porque Lighthouse mide también el tiempo/comportamiento de la navegación inicial (incluida la redirección) antes de estabilizarse en la página final.

### Corridas adicionales y archivos duplicados en `raw/`

`raw/` conserva los **31 archivos** correspondientes a `.lighthouseci/` (con la anonimización descrita arriba, sin depurar por lo demás), incluyendo dos conjuntos que no forman parte del resumen anterior:

- **2 corridas adicionales de `/login`** (`localhost-_login-2026_08_01_04_56_29.report.*` y `localhost-_login-2026_08_01_04_56_41.report.*`), no referenciadas por `raw/manifest.json` ni por `raw/assertion-results.json`; es decir, no pertenecen al conjunto oficial de 3+3 considerado para este resumen. Sus puntajes son consistentes con las demás corridas de `/login` (Performance 94, Accessibility 91, Best Practices 100, SEO 82).
- **6 archivos `lhr-<timestamp>.json`/`.html`**, que corresponden a duplicados generados por Lighthouse CI de 6 de las corridas ya conservadas bajo el nombre `localhost-_<solicitud>-<fecha>.report.*` (mismo contenido técnico, mismo `fetchTime`, mismos puntajes).
- **`raw/flags-66a0fc98-....json`**, que repite en JSON la configuración de throttling y `chromeFlags` ya declarada en `lighthouserc.js`.

Ninguno de estos archivos fue eliminado de `raw/`: la carpeta preserva evidencia cruda completa, no una selección.

## Resultados representativos

`manifest.json` marca una corrida representativa por solicitud (`isRepresentativeRun: true`). En escala 0-100:

| Solicitud | Resultado de navegación | Performance | Accessibility | Best Practices | SEO |
|---|---|---:|---:|---:|---:|
| `/login` | `/login` (directo) | 94 | 91 | 100 | 82 |
| `/mascotas` | redirige a `/login` sin sesión | 92 | 91 | 96 | 82 |

**No se afirma** "la vista Mascotas obtuvo Performance 92": esos puntajes corresponden a la ejecución iniciada sobre `/mascotas`, que Lighthouse midió después de que el guard de autenticación redirigiera el navegador a `/login` sin sesión activa.

## Validación de umbrales

| Categoría | Umbral configurado | Resultado (ambas solicitudes) | Estado |
|---|---|---|---|
| Performance | ≥ 0.80 (80) | 92–94 en las 6 corridas | **Cumplido** |
| Accessibility | ≥ 0.90 (90) | 91 en las 6 corridas | **Cumplido** |
| Best Practices | ≥ 0.90 (90) | 96–100 en las 6 corridas | **Cumplido** |
| SEO | ≥ 0.90 (90) | 82 en las 6 corridas | **No cumplido** |

`raw/assertion-results.json` registra explícitamente los dos fallos de SEO (uno por solicitud evaluada): `expected: 0.9`, `actual: 0.82`, `values: [0.82, 0.82, 0.82]`, nivel `error`. El archivo no contiene ninguna entrada para Performance, Accessibility ni Best Practices: Lighthouse CI únicamente reporta ahí las assertions que fallaron, por lo que su ausencia —junto con los puntajes reales— confirma que esas tres categorías sí cumplieron su umbral.

## Interpretación

Lighthouse verificó directamente **una** página: `/login`. Sobre `/mascotas`, lo que se midió fue el comportamiento de navegación iniciado sin autenticación —incluida la redirección del guard hacia `/login`—, no el contenido de la vista Mascotas ya autenticada. Ambas ejecuciones se completaron correctamente, sin errores de ejecución a nivel de reporte, con puntajes estables entre corridas de una misma solicitud.

De los cuatro umbrales configurados, tres se alcanzaron con margen en las 6 corridas (Performance, Accessibility, Best Practices); el de SEO no se alcanzó en ninguna de las 6. El resultado de SEO (82 frente al umbral de 90) se documenta como una **oportunidad de mejora detectada por la medición**, no como un fallo del proyecto en su conjunto: las demás dimensiones evaluadas cumplen sus objetivos, y el detalle de qué auditorías puntuales de SEO están por debajo del umbral puede revisarse directamente en los reportes HTML enlazados arriba.

**Esta evidencia no constituye todavía una auditoría Lighthouse de la vista autenticada real de Mascotas.** La ejecución asociada a `/mascotas` validó el comportamiento accesible sin sesión y la redirección del guard de autenticación, pero no evaluó el contenido que ve un usuario ya autenticado en esa pantalla. Esto no se presenta como un fallo, sino como una **limitación de alcance de esta medición específica**: para auditar la vista autenticada de Mascotas haría falta una corrida de Lighthouse con una sesión ya establecida (por ejemplo, mediante `storageState`/cookies inyectadas antes de la navegación), algo que esta ejecución no hizo.

## Alcance de la medición

Las mediciones corresponden al **2026-08-01**. Se solicitaron `/login` y `/mascotas`; solo `/login` fue auditada directamente, mientras que `/mascotas` terminó auditando la misma página `/login` por efecto de la redirección de autenticación (ver [Interpretación](#interpretación)). El frontend recibió incorporaciones posteriores a esa fecha (nuevas vistas del alcance de Unidad IV). Los resultados se conservan como evidencia de lo efectivamente auditado en la fecha indicada; ni las funcionalidades incorporadas después, ni la vista autenticada de Mascotas, formaron parte de este conjunto de medición.

## Evidencia cruda

[`raw/`](raw/) contiene el contenido técnico completo de los 31 archivos generados originalmente por Lighthouse CI, con la anonimización descrita en [Procedencia de la evidencia](#procedencia-de-la-evidencia):

- [`raw/manifest.json`](raw/manifest.json) — índice de las 6 corridas oficiales.
- [`raw/assertion-results.json`](raw/assertion-results.json) — assertions que no cumplieron su umbral (no contiene el identificador anonimizado; quedó idéntico al original).
- Los 6 pares `.html`/`.json` de las corridas oficiales, enlazados individualmente en la tabla de "Corridas oficiales".
- Los archivos adicionales descritos en "Corridas adicionales y archivos duplicados en `raw/`" (2 corridas extra de `/login`, 6 `lhr-*`, 1 `flags-*`).

Los reportes `.html` son autocontenidos (se abren directamente en un navegador) y muestran el detalle de cada auditoría individual dentro de las 4 categorías. Los `.json` son el LHR (Lighthouse Result) completo, formato máquina-legible.

## Integridad de los archivos

Esta carpeta mantiene **dos** inventarios de huellas SHA-256, con propósitos distintos:

- [`SHA256SUMS-ORIGINAL.txt`](SHA256SUMS-ORIGINAL.txt) — hash SHA-256 de cada uno de los 31 archivos **originales**, calculado directamente sobre `.lighthouseci/` (fuente local, no versionada) antes de cualquier modificación. Es la huella de la evidencia tal como se recibió.
- [`SHA256SUMS.txt`](SHA256SUMS.txt) — hash SHA-256 de cada uno de los 31 archivos **sanitizados** dentro de `raw/`, calculado después de sustituir el identificador local. Es la huella de la copia que queda versionada en este repositorio.

Ambos inventarios usan rutas relativas y el mismo formato (`<sha256>  <ruta>`), verificable con:

```bash
cd docs/mediciones/lighthouse
sha256sum -c SHA256SUMS.txt              # valida raw/ (31/31 OK)
```

Los hashes de `SHA256SUMS-ORIGINAL.txt` y `SHA256SUMS.txt` **no coinciden** para 29 de los 31 archivos (los que contenían el identificador local sustituido); coinciden exactamente para los 2 archivos que no lo contenían (`assertion-results.json` y `flags-66a0fc98-....json`), lo cual es el resultado esperado de una sanitización selectiva y no indica ningún problema de integridad.
