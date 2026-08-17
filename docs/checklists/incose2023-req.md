# Checklist INCOSE — Guide to Writing Requirements v4 (2023)

**Aplicado a:** `docs/requisitos/SRS.md` (v0.9.0-rc, base para SRS-v1.0.0) y
`docs/trazabilidad/matriz.csv`.
**Norma:** INCOSE-TP-2010-006-04, *Guide to Writing Requirements*, v4, junio
2023 (Requirements Working Group). 15 características (C1–C15) y 42 reglas
(R1–R42).
**Responsable:** Zaida Melissa Taipe Mora (Z03).
**Fecha de esta revisión:** 2026-08-16.
**Método:** revisión manual de los 38 requisitos del SRS (25 REQ-F + 13
REQ-NF) contra cada característica y regla aplicable. No se evaluaron
requisitos que no existen todavía en el documento. Los hallazgos citan el
identificador del requisito y, cuando aplica, la línea de `SRS.md`.

---

## 1. Resumen cuantitativo de partida

| Métrica | Valor | Fuente |
|---|---|---|
| Total de requisitos | 38 (25 REQ-F + 13 REQ-NF) | `SRS.md` §3 |
| Must / Should / Could | 22 / 10 / 6 | `matriz.csv` |
| Estado verificado | 25 | `matriz.csv` |
| Estado implementado (no verificado) | 5 | `matriz.csv` |
| Estado pendiente | 8 | `matriz.csv` |
| Must **no** verificados | 2 (REQ-NF-005, REQ-NF-007) | `matriz.csv` |
| Should pendientes | 3 (REQ-F-018, REQ-F-020 parcial, REQ-NF-006) | `matriz.csv` |

Estas cifras son la línea base sobre la que se aplican las características
de conjunto (C10–C15). Cerrar REQ-NF-005/007 a "verificado" y los 3 Should
pendientes es responsabilidad de Zaida (Z02), no de este checklist.

---

## 2. Características de requisitos individuales (C1–C9)

| # | Característica | Resultado | Evidencia / hallazgo |
|---|---|---|---|
| C1 | Necessary | ✅ Cumple | Cada requisito trae un campo **Rationale** que lo ata a un origen (RF-NN de la Entrega 1A, código ya implementado, o bloque de la Guía). No se detectaron requisitos "de relleno" sin rationale. |
| C2 | Appropriate | ✅ Cumple | El nivel de detalle es consistente: capa de sistema completo (no desciende a nivel de clase Java ni sube a nivel de negocio abstracto). Ejemplo: REQ-F-009 describe la regla de negocio (filtrado por propietario), no la consulta SQL concreta. |
| C3 | Unambiguous | ⚠️ Parcial | La mayoría de enunciados usan un único sujeto/verbo modal (`El sistema deberá permitir...`). Pero **REQ-F-009** y **REQ-F-021** combinan dos condiciones distintas (`ROLE_DUENO` vs. resto de roles) en una sola oración con punto y coma, lo que exige releer dos veces para separar los dos casos — ver también C5/R18. |
| C4 | Complete | ✅ Cumple para lo implementado | Los 25 requisitos verificados/implementados incluyen condición, sujeto, acción y objeto. Los 8 "pendiente" están correctamente marcados como incompletos por diseño (no se rellenó contenido inventado), lo cual es la conducta correcta ante C4 a nivel de conjunto (ver C10). |
| C5 | Singular | ⚠️ Parcial | **REQ-F-009** (línea ~omitido, sección 3.1) y **REQ-F-021** (línea 355) declaran dos capacidades distintas en un mismo enunciado (comportamiento para `DUENO` y comportamiento para el resto de roles). Recomendación de cierre: dividir en dos incisos `a)`/`b)` dentro del mismo ID, o documentar explícitamente que se acepta la excepción por tratarse de una única regla de acceso con dos ramas. |
| C6 | Feasible | ✅ Cumple | Los 25 requisitos con estado verificado/implementado ya están construidos, lo que es la prueba máxima de factibilidad. Los 8 pendientes provienen de la Entrega 1A y no comprometen restricciones técnicas ya validadas (mismo stack). |
| C7 | Verifiable | ✅ Cumple (verificados/implementados) / ⚠️ Pendiente de método (pendientes) | Cada requisito no-pendiente declara un método de verificación concreto (test JUnit, script, inspección). Los 8 pendientes no tienen método de verificación porque no tienen implementación — es correcto que no lo tengan todavía, pero deben recibir uno antes de pasar a "implementado". |
| C8 | Correct | ✅ Cumple | El campo Rationale traza cada requisito a su origen real (RF-NN, código verificado, o bloque de la Guía); no se encontraron requisitos sin trazabilidad a una fuente. |
| C9 | Conforming | ✅ Cumple | Todos usan el patrón declarado en §3 del SRS: `[condición] [sujeto] shall/deberá [acción] [objeto] [restricción]`, con estructura `El sistema deberá permitir que...`. Patrón único y consistente en los 38 requisitos. |

## 3. Características de conjunto (C10–C15)

| # | Característica | Resultado | Evidencia / hallazgo |
|---|---|---|---|
| C10 | Complete (del conjunto) | ⚠️ Parcial, documentado honestamente | El conjunto declara explícitamente 8 requisitos pendientes (historial clínico, citas, IoT, recomendaciones, facturación, reportes, auditoría, notificaciones) en vez de omitirlos. Esto es la conducta correcta según INCOSE (mejor un conjunto que declara sus huecos que uno que aparenta estar completo). Sigue habiendo un hueco real: 2 Must no verificados (REQ-NF-005, REQ-NF-007). |
| C11 | Consistent | ✅ Cumple, con una observación | Terminología homogénea: los 4 roles (`ADMIN`, `VETERINARIO`, `AUXILIAR`, `DUENO`) se usan siempre igual, igual que los códigos HTTP (401/403/404/409/422/429). No se detectaron requisitos que se contradigan entre sí. Observación menor: la sección 4.1 documenta que la numeración `REQ-F-NNN` reemplaza identificadores previos `RF-NN`; el mapeo está centralizado en una sola tabla (correcto, evita ambigüedad histórica). |
| C12 | Feasible (del conjunto) | ✅ Cumple | El subconjunto Must (22 requisitos) ya tiene 20 de 22 verificados sobre un stack real y en ejecución (Docker Compose, 166 tests verdes). No hay indicio de que el conjunto completo exceda las restricciones de un semestre académico, porque los módulos no comprometidos se dejaron como "pendiente" en vez de forzarlos. |
| C13 | Comprehensible | ✅ Cumple | El SRS incluye glosario (§1.3), tabla de roles y perfiles (§2.4) y una sección 7 que resume honestamente lo que falta. Un lector externo (docente evaluador) puede entender el alcance sin consultar el código. |
| C14 | Able to be validated | ⚠️ Parcial | Válido para los 25 requisitos verificados (tienen test/evidencia real). Los 5 "implementado" (REQ-F-013, REQ-F-015, REQ-F-025, REQ-NF-005, REQ-NF-007) tienen evidencia parcial documentada explícitamente (por ejemplo, REQ-NF-005 aclara que Lighthouse solo auditó `/login`, no el rango completo 320–1440px) — la brecha está declarada, no oculta. |
| C15 | Correct (del conjunto) | ✅ Cumple | El conjunto es representación fiel de la Entrega 1A + código verificado; no se detectó ningún requisito que contradiga el código real (`Backend/src/main/java`) al momento de esta revisión. |

---

## 4. Reglas (R1–R42) — verificación muestral sobre los 38 requisitos

Se marcan solo las reglas con hallazgo verificable (cumplimiento o
incumplimiento real); las reglas sin relevancia para este documento (p. ej.
R33 rangos de tolerancia, no aplica a un sistema de gestión veterinaria sin
magnitudes físicas) se listan como "No aplica".

| Regla | Enfoque | Resultado | Evidencia |
|---|---|---|---|
| R1 — Structured Statements | Patrón único | ✅ Cumple | Los 38 requisitos siguen `El sistema deberá permitir/restringir...`. |
| R2 — Active Voice | Voz activa, sujeto = entidad responsable | ✅ Cumple | Sujeto siempre "El sistema"; no hay voz pasiva sin sujeto. |
| R4 — Defined Terms | Términos definidos en glosario | ✅ Cumple | §1.3 define RBAC, JWT, ORM, SP, TTL, MoSCoW, shall. |
| R7 — Vague Terms | Evitar "cualquier/cualquiera" (any) | ⚠️ Incumple parcialmente | `cualquier` aparece en REQ-F-001 (línea 192, "cualquier visitante"), REQ-F-001 (línea 194, "ignorando cualquier rol"), REQ-F-006 (línea 257, "cualquier solicitud de un rol no autorizado") y en un requisito no funcional de seguridad (línea 728, SQL injection). En los primeros tres casos el uso es aceptable en español porque acota un universal claro ("cualquier visitante" = "todo visitante", sin ambigüedad de alcance); no es el uso vago que R7 penaliza (cantidad indefinida). Se documenta como observación menor, no como defecto que bloquee el cierre. |
| R9 — Open-Ended Clauses | Evitar "etc.", "entre otros" | ✅ Cumple | No se encontraron clausulas abiertas en los enunciados normativos (sí aparecen ejemplos con "etc." en texto descriptivo de la sección 5, fuera de los enunciados `shall`, lo cual no infringe la regla). |
| R16 — Use of "Not" | Evitar negaciones | ⚠️ Uso limitado y justificado | REQ-F-023 usa "no puede escalar su propio rol" (negación de una regla de seguridad); es el patrón recomendado por la propia guía para restricciones de seguridad (más claro en negativo que en positivo), se documenta como excepción aceptable. |
| R18 — Single Thought Sentence | Un pensamiento por oración | ⚠️ Incumple en 2 de 38 | REQ-F-009 y REQ-F-021 (ver C5). Resto de requisitos cumple. |
| R24 — Pronouns | Evitar pronombres | ✅ Cumple | No se usan pronombres personales ("él/ella/este") para referirse al sistema o al usuario; siempre se repite el sustantivo ("el usuario autenticado", "el sistema"). |
| R26 — Absolutes | Evitar absolutos no alcanzables (siempre/nunca/100%) | ⚠️ Incumple en 1 de 38 | REQ-F-021 (línea 355): *"el resultado deberá restringirse **siempre** a las mascotas del propio usuario autenticado"*. Es un absoluto sobre una regla de autorización (no sobre una métrica de disponibilidad), por lo que el riesgo real es bajo, pero formalmente infringe R26. Recomendación: sustituir por "en todos los casos evaluados por `MascotaService.verificarPropiedad`" o eliminar el adverbio, ya que la condición ya es universal por el propio verbo "deberá restringirse". |
| R29 — Classification | Clasificar por aspecto del problema | ✅ Cumple | El SRS separa REQ-F (funcionales) de REQ-NF (no funcionales), y dentro de REQ-F distingue "implementados y verificados" de "pendientes heredados". |
| R30 — Unique Expression | Cada requisito una sola vez | ✅ Cumple | La tabla de la sección 4.1 confirma que no hay duplicados entre `RF-NN` original y `REQ-F-NNN` actual; cada requisito antiguo mapea a exactamente un requisito nuevo (salvo fusiones explícitas y documentadas, p. ej. RF-01+RF-02 → REQ-F-008). |
| R31 — Solution Free | No imponer implementación sin justificar | ⚠️ Excepción documentada | REQ-F-021 exige explícitamente función/procedimiento almacenado (SP) en vez de JPQL. Es una restricción de solución, pero el propio requisito remite a la razón (bloque A.2.2 de la Guía del curso, prohibición de SQL dinámico/GROUP BY vía ORM elemental), que es el uso correcto de R31: imponer solución solo cuando hay justificación explícita. |
| R34 — Measurable Performance | Metas de desempeño medibles | ✅ Cumple (REQ-NF) | REQ-NF-001 fija p95 < 200 ms (caliente) y < 500 ms (frío) con umbral numérico verificable; REQ-NF-003 fija expiración en milisegundos configurable. |
| R36 — Consistent Terms and Units | Términos y unidades consistentes | ✅ Cumple | "Baja lógica", "propietario", "activo/inactivo" se usan siempre con el mismo significado en los 38 requisitos. |
| R41 — Related Needs and Requirements | Agrupar requisitos relacionados | ✅ Cumple | Auth (001–005), Mascotas (008–012, 021), Usuarios (007, 023), y bloque pendiente (013–020, 022) están agrupados en subsecciones separadas. |
| R42 — Structured Sets | Conjunto conforme a plantilla | ✅ Cumple | El SRS completo sigue la plantilla ISO/IEC/IEEE 29148 declarada en la portada (Introducción, Descripción global, Requisitos específicos, Trazabilidad, Modelo de datos, Interfaces, Observaciones). |
| Resto de reglas (R3, R5, R6, R8, R10–R15, R17, R19–R23, R25, R27–R28, R32–R33, R35, R37–R40) | — | No aplica / sin hallazgo relevante | No se detectaron infracciones ni casos de uso relevantes para estas reglas en el corpus actual (por ejemplo, R33 "rango de valores" no aplica: BIOPET no tiene requisitos de tolerancia física/dimensional). |

---

## 5. Hallazgos consolidados y acciones de cierre

| # | Hallazgo | Regla/Característica | Severidad | Acción propuesta | Responsable |
|---|---|---|---|---|---|
| H1 | REQ-F-009 y REQ-F-021 combinan dos condiciones en un solo enunciado | C5, R18 | Menor | Dividir en incisos a)/b) al redactar SRS-v1.0.0, o dejar como excepción documentada (regla de acceso con dos ramas complementarias) | Zaida (Z01) |
| H2 | REQ-F-021 usa el absoluto "siempre" | R26 | Menor | Reformular sin adverbio absoluto | Zaida (Z01) |
| H3 | REQ-NF-005 y REQ-NF-007 son Must no verificados | C7/C14 a nivel Must | **Alta (bloquea G6)** | REQ-NF-005 depende de Lighthouse desktop + vista autenticada real (tarea de Zaida, Z09); REQ-NF-007 depende de mantener el servicio 30 días en producción (tarea de Fred, F08–F11) | Zaida + Fred |
| H4 | REQ-F-018, REQ-F-020 y REQ-NF-006 son Should pendientes/parciales | C10 | Media | REQ-F-018 (facturación) y REQ-NF-006 (prueba manual exploratoria de frontend) requieren decisión de alcance: implementar antes del corte o degradar formalmente a "Could" con justificación en el SRS; REQ-F-020 (auditoría) ya está parcial, falta cerrar su verificación | Zaida coordina; Jaime aporta auditoría (REQ-F-020 usa `AuthenticationAuditServiceTest`) |

## 6. Conclusión

De las 15 características INCOSE, **9 cumplen sin observación**, **5 cumplen
con una observación menor documentada** y **1 (C10, completitud del
conjunto) queda condicionada** al cierre de los dos Must no verificados
(H3), que es la brecha real y ya conocida del plan operativo (P0/D0R). No se
identificaron requisitos fabricados, duplicados sin trazar, ni absolutos de
disponibilidad/seguridad que comprometan la evaluación de la Entrega Final.
Este checklist debe volver a ejecutarse (o al menos revisar H1–H4) después
de publicar `SRS-v1.0.0` (Z01) y antes del gate G6.
