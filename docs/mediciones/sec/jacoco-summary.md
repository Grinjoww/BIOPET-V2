# Resumen de cobertura JaCoCo — BIOPET

Este documento no es una categoría OWASP por sí misma, pero respalda la
evidencia de las Fases 8A/8B: la suite de pruebas que sustenta todos los
documentos `A0X-*.md` de esta carpeta está verificada automáticamente por
JaCoCo, no solo ejecutada manualmente.

## Ejecución verificada

```bash
cd Backend
mvn clean verify
```

Resultado real de la ejecución final académica de Unidad IV:

```text
[INFO] Tests run: 166, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] --- jacoco:0.8.12:check (check) @ biopet-backend ---
[INFO] Analyzed bundle 'biopet-backend' with 45 classes
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

- **166** pruebas ejecutadas
- **0** fallos
- **0** errores
- **0** omitidas
- **45** clases analizadas por JaCoCo
- Resultado: `BUILD SUCCESS`

## Cobertura

Cifras calculadas como `covered / (covered + missed)` sobre
`Backend/target/site/jacoco/jacoco.xml` (contadores a nivel de reporte
completo, `<report name="BIOPET Backend">`), cruzadas contra la suma de las
45 filas de `Backend/target/site/jacoco/jacoco.csv`:

| Métrica | Cubierto | No cubierto | Cobertura | Umbral |
|---|---:|---:|---:|---:|
| LINE | 843 | 121 | 87.45 % | ≥ 60 % |
| BRANCH | 155 | 73 | 67.98 % | ≥ 60 % |
| COMPLEXITY | 270 | 106 | 71.81 % | ≥ 60 % |

## Validación de umbrales

`Backend/pom.xml` configura `jacoco-maven-plugin` con una regla `BUNDLE`
(alcance de todo el módulo, no por paquete ni por clase) y tres límites
`COVEREDRATIO`, cada uno con `minimum=0.60`: uno para `LINE`, uno para
`BRANCH` y uno para `COMPLEXITY`. Las tres métricas configuradas superan
ese mínimo del 60 % en la ejecución verificada:

- **LINE**: 87.45 % ≥ 60 % — cumple, con el mayor margen de las tres.
- **BRANCH**: 67.98 % ≥ 60 % — cumple.
- **COMPLEXITY**: 71.81 % ≥ 60 % — cumple.

Estas tres cifras no son intercambiables: **cobertura de líneas** (LINE)
mide qué proporción de líneas de código se ejecutó durante las pruebas;
**cobertura de ramas** (BRANCH) mide qué proporción de las bifurcaciones
condicionales (`if`/`else`, operadores lógicos de cortocircuito, etc.) se
ejercitó en ambos sentidos; **cobertura de complejidad** (COMPLEXITY) mide
la proporción de caminos de complejidad ciclomática cubiertos. Reportar
solo la cifra de LINE (87.45 %) como "la cobertura" del proyecto sería
impreciso: BRANCH y COMPLEXITY se ubican en un rango distinto (67.98 % y
71.81 % respectivamente), ambos por encima del umbral pero con menor
margen que LINE.

Si cualquiera de las tres métricas del bundle cae por debajo de 0.60,
`jacoco:check` provoca que `mvn verify` falle al incumplirse el umbral
configurado. En esta ejecución no ocurrió.

## Interpretación

La cobertura de líneas (87.45 %) es la más alta de las tres métricas
configuradas, lo que indica que la gran mayoría del código fuente
analizado se ejecuta durante la suite de pruebas actual. La cobertura de
ramas (67.98 %) también supera el umbral mínimo configurado, aunque con un
margen menor que LINE: presenta mayor oportunidad de mejora, en particular
en bifurcaciones condicionales poco ejercitadas por los casos de prueba
existentes. La cobertura de complejidad (71.81 %) supera igualmente el
umbral configurado, en un punto intermedio entre las otras dos métricas.

En conjunto, la ejecución automatizada final de Unidad IV (166 pruebas,
0 fallos, 0 errores, `BUILD SUCCESS`) fue satisfactoria y las tres métricas
de cobertura configuradas en `jacoco:check` se cumplen con margen.

Esta cobertura es evidencia del **alcance de las pruebas automatizadas**
sobre el código fuente, no una demostración de ausencia de errores ni una
garantía de calidad absoluta: un porcentaje alto de líneas/ramas/caminos
ejecutados no implica que cada aserción cubra todos los casos límite
posibles, ni sustituye a otras formas de verificación (revisión de código,
pruebas manuales, evidencia de seguridad documentada en los demás
`A0X-*.md` de esta carpeta). Tampoco constituye, por sí sola, una
certificación de que el sistema esté listo para un entorno productivo
real.

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

## Trazabilidad

Fuentes verificadas para este documento:

- `Backend/target/site/jacoco/jacoco.xml` — contadores `LINE`/`BRANCH`/`COMPLEXITY` a nivel de reporte completo, usados para calcular los porcentajes de la tabla de cobertura.
- `Backend/target/site/jacoco/jacoco.csv` — 45 filas (una por clase analizada), sumadas para cruzar los mismos totales de `covered`/`missed` por métrica.
- `Backend/pom.xml` — configuración de `jacoco-maven-plugin` (ejecuciones `prepare-agent`, `report`, `check`) y de la regla `BUNDLE` con `minimum=0.60` para `LINE`, `BRANCH` y `COMPLEXITY`.
- `Backend/target/surefire-reports/*.txt` (17 archivos) — reportes individuales de JUnit por clase de prueba, agregados para confirmar el total de 166 pruebas, 0 fallos, 0 errores, 0 omitidas.

**`Backend/target/` no se versiona** (excluido en `.gitignore` bajo el
patrón `Backend/target/`); estos reportes se generan localmente en cada
ejecución de `mvn clean verify` y deben reproducirse, no copiarse al
repositorio.

## Reproducción

```bash
cd Backend
mvn clean verify
# HTML navegable:
start target/site/jacoco/index.html   # Windows
open target/site/jacoco/index.html    # macOS
xdg-open target/site/jacoco/index.html # Linux
```
