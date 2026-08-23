# BIOPET — Frontend Redesign

## Contexto del proyecto

Este repositorio es una copia local e independiente de BIOPET v1.0.0 destinada a una nueva iteración visual y funcional del frontend.

La entrega académica original ya fue cerrada.

El objetivo de este repositorio es construir una interfaz moderna, profesional y completa que represente correctamente las funcionalidades reales existentes en el backend.

## Prioridad principal

El trabajo se concentra en:

- rediseño completo del frontend Angular;
- experiencia de usuario;
- arquitectura visual;
- exposición de funcionalidades reales que actualmente no tienen interfaz;
- integración frontend-backend;
- responsive design;
- accesibilidad;
- calidad visual de producto.

El backend actual es la FUENTE DE VERDAD funcional.

## Backend

No modificar el backend salvo que exista una razón técnica imprescindible y el usuario lo apruebe explícitamente.

Antes de crear cualquier funcionalidad frontend:

1. inspeccionar Controller correspondiente;
2. inspeccionar DTO Request;
3. inspeccionar DTO Response;
4. inspeccionar Service si es necesario;
5. identificar roles/autorización;
6. identificar errores reales;
7. identificar endpoint exacto.

Nunca inventar endpoints.

Nunca inventar propiedades de DTO.

Nunca asumir contratos HTTP sin revisar el código.

Nunca implementar en frontend una operación que el backend no soporte.

## Angular

Mantener la tecnología actual del proyecto:

- Angular 17;
- TypeScript;
- componentes standalone;
- formularios reactivos cuando corresponda.

Reutilizar infraestructura existente que sea correcta antes de reemplazarla.

No introducir frameworks frontend adicionales sin justificarlo primero.

## Autenticación y seguridad

Preservar la arquitectura de seguridad existente.

Obligatorio:

- autenticación mediante cookies;
- HttpOnly;
- Secure cuando corresponda;
- SameSite según configuración existente;
- `withCredentials: true`;
- guards existentes o una evolución compatible;
- interceptores compatibles con ProblemDetail.

Prohibido:

- almacenar JWT en localStorage;
- almacenar JWT en sessionStorage;
- exponer tokens al JavaScript del navegador;
- desactivar controles de seguridad solo para facilitar el frontend.

Roles existentes que deben respetarse:

- ROLE_ADMIN
- ROLE_VETERINARIO
- ROLE_AUXILIAR
- ROLE_DUENO

La navegación y las acciones visibles deben adaptarse al rol autenticado.

## Principio fundamental de UI

BIOPET debe parecer un producto veterinario real y moderno.

No debe parecer:

- una práctica universitaria básica;
- una plantilla administrativa genérica;
- una interfaz generada automáticamente por IA;
- un dashboard Bootstrap sin identidad.

La dirección visual debe transmitir:

- salud;
- confianza;
- cuidado animal;
- precisión clínica;
- modernidad;
- calidez;
- profesionalismo.

## Evitar estética genérica de IA

Evitar por defecto:

- degradados morados genéricos;
- todas las superficies convertidas en cards;
- cards gigantes con mucho espacio vacío;
- border-radius exagerado en todo;
- sombras fuertes repetidas;
- iconos decorativos sin propósito;
- cuatro KPI ficticios como dashboard genérico;
- datos inventados;
- glassmorphism gratuito;
- exceso de badges;
- layouts idénticos a dashboards SaaS genéricos;
- texto de relleno;
- botones sin función real.

Cada decisión visual debe tener intención.

## Funcionalidad real

Cada elemento interactivo visible debe funcionar.

No crear:

- botones decorativos;
- enlaces sin destino;
- formularios que no envían datos;
- filtros falsos;
- estadísticas inventadas;
- datos mock presentados como información real.

Los mocks solo pueden utilizarse temporalmente durante desarrollo si se declaran explícitamente y se eliminan antes de considerar terminada una feature.

## Plugins / Skills

Cuando corresponda, aprovechar:

- frontend-design;
- feature-dev;
- typescript-lsp;
- security-guidance;
- skill local `biopet-ui`.

`frontend-design` debe orientar las decisiones visuales importantes.

`typescript-lsp` debe utilizarse para comprender referencias, tipos y estructura Angular antes de hacer cambios grandes.

`security-guidance` debe considerarse especialmente en autenticación, permisos, cookies, formularios y comunicación HTTP.

## Flujo obligatorio antes de implementar una feature

Antes de modificar una feature importante:

1. investigar backend;
2. investigar frontend existente;
3. identificar funcionalidad real disponible;
4. identificar roles;
5. identificar contratos de datos;
6. proponer la solución;
7. explicar qué archivos se modificarían;
8. esperar aprobación del usuario cuando se trate de un cambio grande;
9. implementar;
10. ejecutar validaciones.

No comenzar refactorizaciones masivas sin auditoría previa.

## Validación

Después de cada fase significativa:

- comprobar TypeScript;
- ejecutar build de Angular;
- comprobar rutas;
- comprobar imports;
- comprobar errores de compilación;
- revisar responsive;
- revisar estados loading;
- revisar estados empty;
- revisar estados error;
- revisar accesibilidad básica.

No declarar una fase terminada si el build está roto.

## Git

Este proyecto se trabaja actualmente solo en local.

No ejecutar:

- git add
- git commit
- git push
- git tag
- git reset
- git rebase
- git merge
- git remote add/remove/set-url

salvo autorización explícita del usuario.

El usuario manejará Git manualmente.
