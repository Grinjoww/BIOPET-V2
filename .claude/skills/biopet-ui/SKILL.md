---
name: biopet-ui
description: Audita, diseña e implementa interfaces Angular profesionales para BIOPET utilizando exclusivamente funcionalidades y contratos reales del backend.
---

# BIOPET UI Skill

## Misión

Transformar BIOPET desde una interfaz académica mínima hacia una aplicación web veterinaria moderna, coherente, usable y visualmente distintiva.

La prioridad no es simplemente "hacerlo bonito".

La prioridad es que el frontend represente correctamente todo lo que BIOPET realmente puede hacer.

## Fuente de verdad

Antes de diseñar cada módulo, inspeccionar el backend real.

Construir siempre la relación:

Backend real
→ Controller
→ Endpoint
→ DTO
→ permisos
→ Service Angular
→ modelo TypeScript
→ pantalla
→ interacción del usuario.

Nunca diseñar esa cadena al revés inventando primero la interfaz.

## Módulos que deben investigarse

Como mínimo:

- autenticación;
- usuario autenticado/perfil;
- gestión administrativa de usuarios;
- mascotas;
- detalle de mascota;
- citas;
- consultas;
- vacunas;
- información externa de especies;
- procedimientos almacenados que expongan información útil;
- funcionalidades disponibles según rol.

La existencia final de cada pantalla depende de que el backend realmente la soporte.

## Arquitectura UX deseada

Evaluar una estructura de producto que pueda incluir:

- application shell;
- sidebar/navigation;
- header contextual;
- dashboard real;
- búsqueda;
- filtros;
- tablas o listas según el contenido;
- estados de citas;
- fichas de mascota;
- información clínica;
- formularios;
- modales solo cuando tengan sentido;
- estados loading;
- skeletons;
- estados vacíos;
- errores claros;
- confirmaciones;
- feedback de operaciones.

No forzar estos patrones si otro patrón ofrece mejor UX.

## Dashboard

No construir un dashboard con estadísticas ficticias.

Primero investigar qué datos reales puede proporcionar el backend.

El dashboard debe mostrar información accionable y verdadera.

Si el backend no puede proporcionar una métrica, no inventarla.

## Mascotas

La mascota debe tratarse como una entidad central del producto.

Explorar una experiencia más rica que una tabla CRUD simple:

- identificación;
- propietario;
- especie;
- estado;
- acciones disponibles;
- vacunas relacionadas si el backend permite consultarlas;
- consultas/citas relacionadas si los endpoints existentes lo permiten.

No afirmar que existe historial clínico completo si el backend no lo implementa.

## Citas

Representar estados y acciones reales del backend.

Evaluar interfaz tipo agenda/listado/calendario únicamente si los contratos existentes permiten soportarla correctamente.

No inventar funcionalidad de calendario.

## Consultas

Diferenciar visualmente una consulta veterinaria de una cita.

Respetar permisos de veterinario/administrador definidos por backend.

## Vacunas

Mostrar información clínica de forma legible, priorizando:

- mascota;
- vacuna;
- fechas;
- estado;
- próxima aplicación cuando exista realmente en los datos.

## Usuarios

La gestión administrativa debe exponerse únicamente a roles autorizados.

No permitir autoescalamiento de privilegios desde la UI.

## Roles

La interfaz debe cambiar según las capacidades reales del usuario.

No basta con ocultar enlaces visualmente:
el backend sigue siendo responsable de seguridad.

La UI solo debe reflejar correctamente esa autorización.

## Identidad visual

Buscar una identidad propia para BIOPET.

La estética debe situarse entre:

- producto SaaS moderno;
- sistema clínico;
- gestión veterinaria;
- experiencia cálida orientada a animales.

Debe existir una dirección visual definida antes de desarrollar masivamente.

Definir conscientemente:

- tipografía;
- escala;
- espaciado;
- color;
- superficies;
- iconografía;
- jerarquía;
- estados;
- interacción;
- responsive.

## Frontend Design

Para decisiones visuales relevantes, utilizar las capacidades y principios del plugin/skill `frontend-design`.

Evitar resultados previsibles y genéricos.

Crear una dirección estética coherente para todo BIOPET antes de producir pantallas aisladas.

## Accesibilidad

Preservar y mejorar:

- labels;
- navegación por teclado;
- estados focus;
- aria cuando corresponda;
- mensajes de error asociados;
- contraste;
- tamaños interactivos adecuados;
- semántica HTML.

No sacrificar accesibilidad por estética.

## Responsive

El producto debe diseñarse conscientemente para:

- escritorio;
- tablet;
- móvil.

No considerar responsive como una corrección posterior.

## Implementación por fases

Preferir:

Fase 0 — Auditoría integral.

Fase 1 — Dirección visual + design system.

Fase 2 — Application shell / navegación.

Fase 3 — Autenticación.

Fase 4 — Dashboard.

Fase 5 — Mascotas.

Fase 6 — Citas.

Fase 7 — Consultas.

Fase 8 — Vacunas.

Fase 9 — Usuarios / perfil / integración externa.

Fase 10 — Responsive + accesibilidad + polish.

Fase 11 — Integración y validación completa.

La secuencia puede cambiar después de auditar dependencias reales.

## Regla final

No optimizar para producir muchas pantallas rápidamente.

Optimizar para producir un sistema coherente, funcional, conectado y visualmente sobresaliente.
