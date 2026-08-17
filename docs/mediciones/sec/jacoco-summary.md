# Resumen de cobertura JaCoCo — BIOPET

Este documento no es una categoría OWASP por sí misma, pero respalda la
evidencia de las Fases 8A/8B: la suite de pruebas que sustenta todos los
documentos `A0X-*.md` de esta carpeta está verificada automáticamente por
JaCoCo, no solo ejecutada manualmente.

## Ejecución verificada (Entrega Final, v1.0.0)

```bash
cd Backend
mvn clean verify
```

Resultado real de la ejecución final de la Entrega Final (Fase 1, Jaime
Mariscal), con Docker activo para los dos tests de Testcontainers:

```text
[INFO] Tests run: 189, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] --- jacoco:0.8.12:check (check) @ biopet-backend ---
[INFO] Analyzed bundle 'biopet-backend' with 46 classes
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

- **189** pruebas ejecutadas
- **0** fallos
- **0** errores
- **0** omitidas
- Resultado: `BUILD SUCCESS`

## Cobertura actual (real, Entrega Final)

Cifras calculadas como `covered / (covered + missed)` sobre
`Backend/target/site/jacoco/jacoco.xml` (contadores a nivel de reporte
completo, `<report name="BIOPET Backend">`), archivadas también en
[`docs/mediciones/jacoco/METRICS.md`](../jacoco/METRICS.md):

| Métrica | Cubierto | No cubierto | Cobertura | Umbral (`pom.xml`) |
|---|---:|---:|---:|---:|
| LINE | 885 | 79 | **91.80 %** | ≥ 0.70 |
| BRANCH | 181 | 47 | **79.39 %** | ≥ 0.70 |
| COMPLEXITY | — | — | ver `docs/mediciones/jacoco/METRICS.md` | ≥ 0.60 |

**Estas cifras son las vigentes para la Entrega Final (`v1.0.0`).** No
reemplazan silenciosamente ninguna medición anterior: la cobertura de la
Tercera Entrega (`v0.9.0-rc`) se conserva a continuación, marcada
explícitamente como **baseline histórico**, para trazabilidad de la mejora
lograda en esta fase.

### Baseline histórico — Tercera Entrega (`v0.9.0-rc`, ya superado)

| Métrica | Cubierto | No cubierto | Cobertura | Umbral vigente en ese momento |
|---|---:|---:|---:|---:|
| LINE | 843 | 121 | 87.45 % | ≥ 0.60 |
| BRANCH | 155 | 73 | 67.98 % | ≥ 0.60 |
| COMPLEXITY | 270 | 106 | 71.81 % | ≥ 0.60 |

Estas cifras del baseline **ya no describen el estado actual del código**;
se conservan únicamente como referencia histórica de la Tercera Entrega. El
umbral BRANCH de la Tercera Entrega (67.98 %) estaba, de hecho, por debajo
del 70 % exigido para la Entrega Final; ese vacío quedó cerrado en esta
fase (79.39 % actual).

## Validación de umbrales

`Backend/pom.xml` configura `jacoco-maven-plugin` con una regla `BUNDLE`
(alcance de todo el módulo, no por paquete ni por clase) y tres límites
`COVEREDRATIO`:

- **LINE**: mínimo `0.70` (elevado desde `0.60` en esta fase, tras confirmar que la suite ampliada lo cumple con margen).
- **BRANCH**: mínimo `0.70` (elevado desde `0.60` en esta fase, por la misma razón).
- **COMPLEXITY**: mínimo `0.60` (sin cambios; no exigido a 0.70 por el alcance de esta tarea).

Las tres métricas configuradas superan su mínimo vigente en la ejecución
verificada:

- **LINE**: 91.80 % ≥ 70 % — cumple, con amplio margen.
- **BRANCH**: 79.39 % ≥ 70 % — cumple, con margen.
- **COMPLEXITY**: ver `docs/mediciones/jacoco/METRICS.md` (también ≥ 60 %).

Si cualquiera de las tres métricas del bundle cae por debajo de su mínimo,
`jacoco:check` provoca que `mvn verify` falle al incumplirse el umbral
configurado. En esta ejecución no ocurrió: `mvn clean verify` finalizó con
`BUILD SUCCESS`.

## Cobertura por capa (dominio, servicios, controladores)

Calculada sumando los contadores `LINE`/`BRANCH` por paquete dentro de
`Backend/target/site/jacoco/jacoco.xml` (elemento `<package>`), no
estimada:

| Capa | Paquete | LINE cubierto/total | LINE % | BRANCH cubierto/total | BRANCH % |
|---|---|---:|---:|---:|---:|
| Dominio | `com.biopet.entity` | 41/41 | 100.00 % | 24/24 | 100.00 % |
| Servicios | `com.biopet.service` | 392/438 | 89.50 % | 73/102 | 71.57 % |
| Controladores | `com.biopet.controller` | 68/75 | 90.67 % | 0/0 | N/A (sin ramas condicionales en esta capa) |

Las tres capas cumplen el objetivo de ≥ 70 % LINE/BRANCH de la Entrega
Final. La capa de controladores no tiene ninguna rama condicional propia
(delega en servicios y usa anotaciones declarativas de seguridad, sin
`if`/`else` en el propio controlador), por lo que su BRANCH se reporta como
N/A en lugar de forzar un porcentaje sobre un denominador cero.

## Interpretación

La cobertura de líneas (91.80 %) y de ramas (79.39 %) superan ambas el
umbral del 70 % exigido para la Entrega Final, con margen. La capa de
dominio (`entity`) pasó de 50 % a 100 % de BRANCH en esta fase, al agregar
pruebas que ejercitan explícitamente ambas ramas de cada callback
`@PrePersist` (campo nulo vs. campo ya asignado). Las capas de servicios y
controladores ya cumplían el umbral antes de esta fase y no requirieron
pruebas adicionales.

Esta cobertura es evidencia del **alcance de las pruebas automatizadas**
sobre el código fuente, no una demostración de ausencia de errores ni una
garantía de calidad absoluta: un porcentaje alto de líneas/ramas ejecutadas
no implica que cada aserción cubra todos los casos límite posibles, ni
sustituye a otras formas de verificación (revisión de código, pruebas
manuales, evidencia de seguridad documentada en los demás `A0X-*.md` de
esta carpeta). Tampoco constituye, por sí sola, una certificación de que el
sistema esté listo para un entorno productivo real.

## Exclusiones aplicadas y justificación

| Clase excluida | Justificación |
|---|---|
| `com/biopet/BiopetApplication.class` | Clase `main()` estándar de Spring Boot, sin lógica propia |
| `com/biopet/dto/**` | Records puros, solo portan datos y anotaciones de validación |
| `com/biopet/entity/Rol.class` | Enum sin comportamiento |
| `com/biopet/repository/ResumenEspecie.class` | Interfaz de proyección, solo getters sin implementación |
| `com/biopet/config/OpenApiConfig.class` | Solo construye un bean `OpenAPI` encadenando builders, sin ninguna rama condicional |

Sin cambios respecto a la Tercera Entrega: no se agregó ni se retiró
ninguna exclusión en esta fase, conforme a la restricción de no excluir
clases legítimas para inflar JaCoCo.

Explícitamente **no excluidas** (a pesar de estar en paquetes `config`/`entity`):
`Usuario`/`Mascota`/`Cita`/`Consulta`/`Vacuna` (tienen lógica real en
`@PrePersist`/`@PreUpdate`, ahora con ambas ramas cubiertas),
`SecurityConfig`, `TomcatDualConnectorConfig`, `DataInitializer` (contiene
una decisión real: `if (!repo.existsByEmail(...))`), y todo `security/**`,
`service/**`, `controller/**`, `exception/GlobalExceptionHandler`/`ProblemDetailFactory`.

## Trazabilidad

Fuentes verificadas para este documento:

- `Backend/target/site/jacoco/jacoco.xml` — contadores `LINE`/`BRANCH`/`COMPLEXITY` a nivel de reporte completo y por paquete, usados para calcular los porcentajes globales y por capa.
- `Backend/target/site/jacoco/jacoco.csv` — filas por clase analizada, usadas para identificar las clases con ramas sin cubrir antes de esta fase.
- `Backend/pom.xml` — configuración de `jacoco-maven-plugin` (ejecuciones `prepare-agent`, `report`, `check`) y de la regla `BUNDLE` con `minimum=0.70` para `LINE` y `BRANCH`, `minimum=0.60` para `COMPLEXITY`.
- `Backend/target/surefire-reports/*.txt` — reportes individuales de JUnit por clase de prueba, agregados para confirmar el total de 189 pruebas, 0 fallos, 0 errores, 0 omitidas.
- [`docs/mediciones/jacoco/METRICS.md`](../jacoco/METRICS.md) y [`docs/mediciones/jacoco/jacoco.xml`](../jacoco/jacoco.xml) — copia archivada de esta misma ejecución, generada con `scripts/archive-jacoco-evidence.sh`.

**`Backend/target/` no se versiona** (excluido en `.gitignore` bajo el
patrón `Backend/target/`); estos reportes se generan localmente en cada
ejecución de `mvn clean verify` y deben reproducirse, no copiarse
manualmente al repositorio (para eso existe
`scripts/archive-jacoco-evidence.sh`, que sí archiva una copia reproducible
en `docs/mediciones/jacoco/`).

## Reproducción

```bash
cd Backend
mvn clean verify
cd ..
bash scripts/archive-jacoco-evidence.sh   # archiva jacoco.xml/csv/html en docs/mediciones/jacoco/
# HTML navegable:
start Backend/target/site/jacoco/index.html   # Windows
open Backend/target/site/jacoco/index.html    # macOS
xdg-open Backend/target/site/jacoco/index.html # Linux
```
