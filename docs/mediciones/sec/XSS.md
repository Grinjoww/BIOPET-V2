# XSS — Cross-Site Scripting

## Alcance de este documento

XSS no es una categoría OWASP Top 10:2021 independiente (está englobada en
A03 — Injection), pero esta tarea la pide como entregable propio. Se separa
aquí para que la búsqueda y la evidencia queden explícitas y verificables,
sin duplicar `A03-injection.md` (que cubre inyección SQL, no XSS).

**Resultado adelantado:** no se encontró ningún patrón de código inseguro
en el proyecto. No se agregó ningún control nuevo porque no había nada que
corregir — se documenta el estado real encontrado, tal como pide la tarea.

## Búsqueda realizada

Búsqueda exhaustiva (no muestreo) sobre **todo** `frontend/src/app`
(9 archivos `.ts`, no hay archivos `.html` de plantilla separados — los
componentes usan `template` inline):

```bash
grep -rniE "innerHTML|bypassSecurityTrust|document\.write|\beval\(|i18n|<svg|ngModel|outerHTML|dangerouslySet" frontend/src/app
```

**Resultado real y completo:**

```
./features/login.component.ts:22:        [(ngModel)]="email"
./features/login.component.ts:31:        [(ngModel)]="password"
```

Archivos escaneados (los 9 que componen `frontend/src/app`):
`app.component.ts`, `app.routes.ts`, `core/auth.guard.ts`,
`core/auth.service.ts`, `core/http-error.interceptor.ts`,
`core/problem-detail.service.ts`, `features/login.component.ts`,
`features/mascota-api.service.ts`, `features/mascotas.component.ts`.

### Interpretación de las dos únicas coincidencias

`[(ngModel)]` (`login.component.ts:22,31`) es *two-way binding* de
Angular Forms sobre `<input type="email">` e `<input type="password">` —
enlaza el valor del campo a una propiedad TypeScript primitiva
(`string`), no a una propiedad del DOM sensible como `innerHTML` o `src`.
No es un sumidero (*sink*) de XSS: Angular serializa/deserializa el valor
como texto plano en ambas direcciones.

**Cero coincidencias** para: `innerHTML`, `[innerHTML]`,
`bypassSecurityTrustHtml`, `bypassSecurityTrustScript`,
`bypassSecurityTrustUrl`, `bypassSecurityTrustResourceUrl`,
`document.write`, `eval(`, atributos `i18n`, elementos `<svg`,
`outerHTML`, `dangerouslySetInnerHTML` (este último ni siquiera existe en
Angular, se buscó por descarte). El proyecto **nunca** llama a
`DomSanitizer` en absoluto (ni para bypass ni para uso normal) — no se
importa en ningún archivo.

## Cómo Angular escapa interpolaciones por defecto

El proyecto no tiene protección XSS "artesanal" propia porque no la
necesita: usa el comportamiento por defecto de Angular, que es seguro
mientras no se invoque `bypassSecurityTrust*` (confirmado arriba: nunca se
invoca):

- **Interpolación `{{ valor }}`**: Angular siempre la trata como texto,
  nunca como HTML — no existe forma de que `{{ }}` renderice una etiqueta.
- **Property binding `[prop]="valor"`**: pasa por el `DomSanitizer`
  interno de Angular; si el valor resulta peligroso para el contexto
  (por ejemplo, una URL `javascript:` en `[href]`), Angular lo neutraliza
  o lo rechaza en vez de renderizarlo, sin intervención del desarrollador.
- El único mecanismo para *desactivar* esa protección es
  `DomSanitizer.bypassSecurityTrust*`, que este proyecto no usa (verificado
  arriba).

Fuente: `frontend/src/app/features/mascotas.component.ts` y
`login.component.ts` — ambos renderizan datos que vienen del backend
(nombre de mascota, nombre de usuario, mensajes de error) exclusivamente
vía interpolación `{{ }}` en sus `template` inline, nunca vía `innerHTML`.

## CSP configurada en Spring Security (control de profundidad adicional)

Documentada en detalle en `A05-security-headers.md` — aquí solo se cita la
parte específicamente relevante a XSS, sin repetir el resto:

```java
// SecurityConfig.java:61
.contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; frame-ancestors 'none'; object-src 'none'"))
```

Verificado en código de prueba real (`SecurityHeadersTest.java:54-56`):

```java
assertFalse(csp.contains("frame-ancestors 'self'"));
assertFalse(csp.contains("unsafe-inline"));
assertFalse(csp.contains("unsafe-eval"));
```

La CSP **no** incluye `unsafe-inline` ni `unsafe-eval`: incluso si algún
componente futuro introdujera un `<script>` inline o un `eval()`, el
navegador lo bloquearía en tiempo de ejecución por política, como capa
adicional independiente del código de Angular. Esto se aplica a nivel de
backend (cabecera HTTP), por lo que protege sin importar qué frontend
consuma la API.

## Resultado

**No se encontró ninguna vulnerabilidad XSS explotable en el código propio
de BIOPET** (ni backend ni frontend), en esta revisión. Los controles que
reducen el riesgo son, en orden de profundidad:

1. Angular sanitiza por defecto y el proyecto no desactiva esa protección
   en ningún punto (`bypassSecurityTrust*`: cero usos).
2. El proyecto no manipula el DOM directamente en ningún componente
   (`innerHTML`/`outerHTML`/`document.write`: cero usos).
3. CSP sin `unsafe-inline`/`unsafe-eval`, verificada por prueba
   automatizada real (`SecurityHeadersTest`), como defensa en profundidad
   a nivel de navegador.

## Advertencia honesta — esto no es lo mismo que "0 CVE de XSS"

Ver `A06-vulnerable-components.md`: la versión de `@angular/core` y
`@angular/compiler` que usa el proyecto (`^17.3.0`, dentro del rango
vulnerable `<=19.2.25` y `<=20.3.25` respectivamente) tiene **múltiples
avisos de seguridad publicados y reales relacionados con XSS** (por
ejemplo `GHSA-jrmj-c5cx-3cw6`, XSS vía atributos de script en SVG sin
sanitizar; `GHSA-g93w-mfhg-p222`, XSS vía atributos `i18n`). Esos avisos
requieren que la aplicación use `i18n` o renderice SVG con atributos de
plantilla — patrones que, según la búsqueda de esta sección, **BIOPET no
usa en ningún componente actual**. Es decir: el riesgo de esas CVE
concretas es teórico para el código tal como existe hoy, pero la librería
vulnerable sigue presente en el bundle de producción y una funcionalidad
futura que use `i18n` o SVG heredaría el problema sin que nadie lo note a
simple vista. **No se marca como "mitigado"**: se documenta como riesgo
residual real en A06, con la corrección (mayor de Angular) explícitamente
fuera del alcance de esta tarea.

## Reproducción

```bash
grep -rniE "innerHTML|bypassSecurityTrust|document\.write|\beval\(|i18n|<svg|ngModel|outerHTML|dangerouslySet" frontend/src/app

cd Backend
mvn -Dtest=SecurityHeadersTest test
```

## Limitaciones

- Esta es una revisión estática de código fuente (grep exhaustivo + lectura
  manual de los 9 archivos), no un escaneo dinámico (DAST) ni una prueba de
  penetración real contra el frontend en ejecución.
- No se auditó el HTML generado en tiempo de build (`ng build`) ni el
  bundle final servido por `frontend/Dockerfile` — solo el código fuente en
  `frontend/src/app`.
- No se revisaron los mensajes de error del backend reflejados en el
  frontend más allá de confirmar que se interpolan como texto
  (`{{ }}`); no se probó con payloads reales contra una instancia en
  ejecución del frontend (eso requeriría levantar el stack completo,
  fuera del alcance de esta tarea, que es de análisis de código y
  configuración).
