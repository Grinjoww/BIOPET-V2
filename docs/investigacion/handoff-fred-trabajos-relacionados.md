# Handoff de trabajos relacionados (F20) — bloques de Fred

> **Para Zaida (Z12)**: trabajos relacionados verificados para la sección
> "Estado del arte / Trabajos relacionados" del informe. NO toco
> `refs.bib` ni `docs/informe-final.tex`. Cada trabajo incluye fuentes
> reales (DOI/URL) para que puedas revisarlos.
> Fecha: 2026-08-17. Rama: `fred/f20-f21-investigacion-refs`.

---

## Trabajo 1 — VETelgeuse (Bucao et al., 2023)

| Campo | Detalle |
|---|---|
| Año | 2023 |
| Dominio | Gestión de clínicas veterinarias de pequeña y mediana escala |
| Pila tecnológica | Aplicación web + móvil (gestión de citas, pacientes y expedientes) |
| Arquitectura | Cliente–servidor web y móvil, con funcionalidad compartida |
| Tipo de evaluación | Cuestionario USE con usuarios reales (usabilidad/aceptación, n=30 según el estudio) |
| Limitaciones | Enfoque en pequeñas/medianas clínicas; evaluación centrada en usabilidad, no en rendimiento |
| Diferencia con BIOPET | BIOPET es un monolito Spring Boot + PostgreSQL + Redis con acceso a datos híbrido (JPA + SQL nativo en SP), y su validación incluye pruebas de rendimiento con k6, no solo usabilidad |

**Fuente real**: Bucao, W., Quinones, Ma. A. A., Abong, R. A., Penuela, C., &
Sun, K. J. (2023). VETelgeuse: A Mobile and Web-Based Management System for
Small and Medium-Scale Veterinary Clinics. *Research Journal of Education,
Science and Technology*, 3(1). https://doi.org/10.63179/rjest.v3i1.55

---

## Trabajo 2 — TerraVet (Llaneta et al., 2022)

| Campo | Detalle |
|---|---|
| Año | 2022 |
| Dominio | Comunicación entre dueños de mascotas y clínicas veterinarias |
| Pila tecnológica | Framework de aplicación web y móvil (caso de uso: mascotas + clínica) |
| Arquitectura | Aplicación web y móvil integradas como framework |
| Tipo de evaluación | Publicado en conferencia (ICIST 2022, ACM); propuesta de framework, evaluación preliminar |
| Limitaciones | Orientado a conectar dueño–clínica; sin mediciones de rendimiento ni despliegue en la nube |
| Diferencia con BIOPET | BIOPET cubre el ciclo completo de la clínica (consultas, fichas, reportes) con reglas de negocio en PostgreSQL (SP) y despliegue contenerizado en Render |

**Fuente real**: Llaneta, J. C. E., Guelas, C. J. D., Labanan, R. M., Mercado,
J. S., & Sasis, R. L. (2022). TerraVet: A Mobile and Web Application Framework
for Pet Owners and Veterinary Clinic. *Proceedings of the 4th International
Conference on Intelligent Science and Technology (ICIST)*, 19–24.
https://doi.org/10.1145/3568923.3568927

---

## Trabajo 3 — CRUD con ORMs .NET vs SQL directo (Zmaranda et al., 2020)

| Campo | Detalle |
|---|---|
| Año | 2020 |
| Dominio | Acceso a datos relacionales: rendimiento de operaciones CRUD |
| Pila tecnológica | .NET, mapeadores objeto-relacional (ORM) |
| Arquitectura | Capa de persistencia comparando ORMs contra acceso directo a datos |
| Tipo de evaluación | Benchmark de casos de uso CRUD (crear/leer/actualizar/borrar) midiendo tiempos |
| Limitaciones | Caso de estudio limitado a una pila (.NET); no considera SQL complejo ni agregaciones |
| Diferencia con BIOPET | BIOPET aplica un acceso híbrido: JPA (Spring Data) para CRUD simple y SQL nativo en procedimientos almacenados para consultas complejas (F01–F05), con medición real de rendimiento (F06–F07) |

**Fuente real**: Zmaranda, D., Pop-Fele, L.-L., Gyorödi, C., Gyorödi, R., &
Pecherle, G. (2020). Performance Comparison of CRUD Methods using NET Object
Relational Mappers: A Case Study. *International Journal of Advanced Computer
Science and Applications*, 11(1). https://doi.org/10.14569/IJACSA.2020.0110107

---

## Notas para Zaida

- Los 3 trabajos fueron elegidos por cercanía al dominio (2 veterinarios) y
  por la decisión de arquitectura de datos (1 sobre rendimiento de ORMs vs
  SQL directo, que respalda el acceso híbrido de BIOPET).
- Las fuentes son DOI reales verificados por Fred el 2026-08-17 (se abrió
  cada DOI y se confirmó título, autores y año).
- La lista completa de referencias para citar (F21) está en
  `handoff-fred-referencias.bib` en esta misma carpeta.