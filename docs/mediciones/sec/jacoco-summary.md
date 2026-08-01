# Resumen de cobertura JaCoCo

Este documento no es una categoría OWASP por sí misma, pero respalda la
evidencia de las Fases 8A/8B: la suite de pruebas que sustenta todos los
documentos `A0X-*.md` de esta carpeta está verificada automáticamente por
JaCoCo, no solo ejecutada manualmente.

## Comando

```bash
cd Backend
mvn clean verify
```

## Resultado real (reejecutado el 2026-08-01)

```
[INFO] Tests run: 109, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] --- jacoco:0.8.12:check (check) @ biopet-backend ---
[INFO] Analyzed bundle 'biopet-backend' with 26 classes
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

El total sube de 108 a 109 respecto de la evidencia anterior (2026-07-31):
se reemplazó la aserción ambigua de `SqlInjectionSecurityTest.loginConEmailDeInyeccionNoAutentica`
(antes aceptaba 401 **o** 422 sin resolver cuál ocurría realmente) por una
aserción exacta de 422, y se añadió `loginConPayloadLiteralDeLaGuiaDevuelve422`
con el payload literal exigido por la guía para el control A03 — ver
`A03-injection.md`. La cobertura no cambia porque ambas pruebas ejercitan
rutas de código ya cubiertas.

## Cobertura global (calculada como `covered / (covered + missed)` sobre `target/site/jacoco/jacoco.xml`)

| Métrica | Cobertura | Umbral automático |
|---|---|---|
| LINE | **95.87 %** | ≥ 60 % |
| BRANCH | **76.87 %** | ≥ 60 % |
| COMPLEXITY | **80.00 %** | ≥ 60 % |

El umbral del 60 % se verifica automáticamente mediante la ejecución
`check` de `jacoco-maven-plugin` (`Backend/pom.xml`), con una regla
`BUNDLE` (alcance de todo el módulo, no por paquete ni por clase) y tres
límites `COVEREDRATIO` (`LINE`, `BRANCH`, `COMPLEXITY`), cada uno con
`minimum=0.60`. Si cualquiera de las tres cae por debajo de 0.60,
`mvn verify` falla el build con un listado de las clases que no cumplen la
regla.

## Ubicación de los reportes locales

- `Backend/target/jacoco.exec` (datos crudos binarios de ejecución)
- `Backend/target/site/jacoco/index.html` (reporte navegable)
- `Backend/target/site/jacoco/jacoco.xml` (reporte máquina-legible, usado
  para calcular las cifras de este documento)

**`Backend/target/` no se versiona** (excluido en `.gitignore` bajo el
patrón `Backend/target/`); estos reportes se generan localmente en cada
ejecución de `mvn clean verify` y deben reproducirse, no copiarse al
repositorio.

## Exclusiones aplicadas y justificación

| Clase excluida | Justificación |
|---|---|
| `com/biopet/BiopetApplication.class` | Clase `main()` estándar de Spring Boot, sin lógica propia |
| `com/biopet/dto/**` | Records puros, solo portan datos y anotaciones de validación |
| `com/biopet/entity/Rol.class` | Enum sin comportamiento |
| `com/biopet/repository/ResumenEspecie.class` | Interfaz de proyección, solo getters sin implementación |
| `com/biopet/config/OpenApiConfig.class` | Solo construye un bean `OpenAPI` encadenando builders, sin ninguna rama condicional |

Explícitamente **no excluidas** (a pesar de estar en paquetes `config`/`entity`):
`Usuario`/`Mascota` (tienen lógica real en `@PrePersist`/`@PreUpdate`),
`SecurityConfig`, `TomcatDualConnectorConfig`, `DataInitializer` (contiene
una decisión real: `if (!repo.existsByEmail(...))`), y todo `security/**`,
`service/**`, `controller/**`, `exception/GlobalExceptionHandler`/`ProblemDetailFactory`.

## Reproducción

```bash
cd Backend
mvn clean verify
# HTML navegable:
start target/site/jacoco/index.html   # Windows
open target/site/jacoco/index.html    # macOS
xdg-open target/site/jacoco/index.html # Linux
```
