# Revisión Crítica #2 — PFC BIOPET

**Autor:** Fajardo Montes Michael Xavier
**Actividad:** Unidad IV - GA | PFC BIOPET
**Archivo:** docs/u4/revisiones/REVISION-FAJARDO.md
**Enfoque:** Usabilidad, organización del proyecto, mantenibilidad, escalabilidad, seguridad, experiencia del usuario, pruebas, rendimiento, documentación y despliegue.

---

## 1. Usabilidad y experiencia del usuario

**Fortaleza:** el sistema separa claramente cuatro roles con permisos distintos (`ADMIN`, `VETERINARIO`, `AUXILIAR`, `DUENO`), y el rol `DUENO` está limitado por propiedad: solo ve y gestiona sus propias mascotas, mientras que el personal de la clínica tiene alcance global. Esto refleja un modelo mental correcto para un sistema veterinario real, donde el dueño no debería ver información de otras mascotas.

**Problema (actualizado):** el sistema sí cuenta con una medición SUS real y versionada en `docs/mediciones/sus/REPORT.md`: cuestionario System Usability Scale aplicado a n = 10 participantes externos al equipo, con un puntaje medio de 74.75/100, clasificado como "Bueno" según la escala de adjetivos de Bangor, Kortum & Miller (2009). Esta evidencia, sin embargo, no está enlazada desde la sección de evidencia/limitaciones del README (a diferencia de Lighthouse, Redis o PostgreSQL, que sí tienen fila propia en esa tabla), lo que dificulta que un evaluador externo la ubique sin revisar directamente `docs/mediciones/`.

**Mejora sugerida:** enlazar el resultado SUS (`docs/mediciones/sus/REPORT.md`) desde la tabla de evidencia del README, y considerar ampliar la muestra en una futura iteración, tal como recomienda el propio reporte.

## 2. Organización del proyecto

**Fortaleza:** la estructura de carpetas es ordenada y separa con claridad las responsabilidades: `Backend/`, `frontend/`, `db/`, `database/migrations/`, `k6/`, `scripts/`, y una carpeta `docs/` extensa con ADRs, diagramas C4, evidencia de seguridad y trazabilidad de requisitos. También existe un `Makefile` que centraliza los comandos operativos (`make up`, `make test`, `make bench`, `make audit`), lo que reduce la fricción para cualquiera que se incorpore al proyecto.

**Problema:** la cantidad de documentación es alta pero está distribuida en muchas subcarpetas (`docs/adr`, `docs/diagrams`, `docs/mediciones/sec`, `docs/mediciones/redis`, `docs/mediciones/postgres`, `docs/requisitos`, `docs/trazabilidad`, `docs/basedatos`, `docs/informe`), y el README, aunque completo, no incluye un índice o mapa de navegación único que oriente a un evaluador externo sobre por dónde empezar.

**Mejora sugerida:** agregar un `docs/README.md` a modo de índice general con enlaces directos a cada subcarpeta y una frase de una línea explicando qué contiene cada una.

## 3. Mantenibilidad

**Fortaleza:** el proyecto fija las imágenes Docker de terceros por digest `sha256` (no solo por tag), lo cual es una práctica poco común en proyectos académicos y evita que una reconstrucción futura use silenciosamente una versión distinta de PostgreSQL o Redis. También hay Flyway para migraciones versionadas y ADRs documentando decisiones de arquitectura (ADR-002 a ADR-007).

**Problema:** la cuenta de administrador sembrada (`admin@biopet.ec`) tiene su contraseña definida directamente en `DataInitializer.java` y replicada en `db/seed.sql`. El propio README advierte que "antes de cualquier despliegue fuera del entorno académico, esta cuenta debe eliminarse o su contraseña debe externalizarse". Tener un secreto hardcodeado en el código fuente, aunque esté señalizado como límite conocido, es una fuente clásica de deuda técnica si el proyecto evoluciona sin que alguien recuerde ese detalle.

**Mejora sugerida:** mover la contraseña del admin sembrado a una variable de entorno (siguiendo el mismo patrón que ya usan para `JWT_SECRET` o `DB_PASSWORD` en `.env`), aunque sea con un valor de desarrollo por defecto, para no dejar ningún secreto embebido en el código versionado.

## 4. Escalabilidad

**Fortaleza:** el uso de Redis para caché de listados y para la lista negra de tokens JWT es una decisión correcta para reducir carga sobre PostgreSQL en operaciones de lectura frecuente, y el uso de `ProblemDetail` (RFC 7807) estandariza las respuestas de error de forma que un cliente puede escalar el manejo de errores sin lógica ad-hoc por endpoint.

**Problema:** el propio README es honesto en admitir dos límites de escalabilidad reales: el rate limiting de login es en memoria y por instancia del backend (`ConcurrentHashMap`, "estado por instancia... no distribuido"), y todo el sistema fue evaluado como una sola instancia sin réplicas. Esto significa que si BIOPET se desplegara con más de un contenedor de backend detrás de un balanceador, el límite de intentos fallidos de login dejaría de funcionar correctamente, porque cada instancia llevaría su propio contador.

**Mejora sugerida:** migrar el `LoginRateLimiterService` de memoria local a Redis (que el proyecto ya usa), usando claves con TTL por IP, de modo que el límite de intentos sea compartido entre instancias si el sistema llega a escalar horizontalmente.

## 5. Seguridad

**Fortaleza:** el manejo de tokens es sólido para un proyecto académico: JWT firmado con HMAC-SHA256, entregado en cookies `HttpOnly`, `Secure` y `SameSite=Strict`, sin uso de `localStorage` en el frontend Angular, lo cual reduce directamente el riesgo de robo de token vía XSS. El flujo de refresh tampoco recibe el token en el body, sino que lo lee de la cookie, y el logout revoca el token en Redis de forma idempotente.

**Problema:** el certificado TLS es autofirmado y "exclusivamente académico", según el propio README, y el sistema corre por defecto en HTTP simple (puerto 8080) salvo que se active manualmente el perfil `tls`. Además, no existe integración con un SIEM ni centralización de los logs de auditoría (`AUTH_AUDIT`), que quedan "locales al proceso/contenedor".

**Mejora sugerida:** documentar explícitamente en el README un plan de migración a TLS con certificado válido (por ejemplo, Let's Encrypt) como paso obligatorio antes de cualquier entorno más allá del académico, y evaluar al menos el envío de logs de auditoría a un archivo centralizado o a un servicio como Loki/ELK como prueba de concepto, aunque sea mínima.

## 6. Pruebas

**Fortaleza:** la cobertura de pruebas es alta y verificable: 166 pruebas ejecutadas, 0 fallos, 0 errores, 45 clases analizadas, 87.45% de cobertura LINE y 67.98% de cobertura BRANCH según JaCoCo (`docs/mediciones/sec/jacoco-summary.md`), con un umbral automático (`jacoco:check`) que falla el build si la cobertura baja del 60% en LINE, BRANCH y COMPLEXITY. Esto es evidencia real, no una afirmación sin respaldo.

**Problema:** la cobertura de BRANCH (67.98%) sigue siendo notablemente más baja que la de LINE (87.45%), lo que suele indicar que muchas ramas condicionales (casos de error, validaciones, combinaciones de rol) no están cubiertas aunque las líneas sí se ejecuten. Por otro lado, no hay evidencia en el README de pruebas end-to-end del frontend Angular, solo de pruebas backend con JUnit.

**Mejora sugerida:** revisar el reporte de JaCoCo por clase para identificar qué ramas específicas quedan sin cubrir (probablemente en `MascotaService.verificarPropiedad` y en el manejo de códigos 401/403/429), y agregar al menos pruebas E2E básicas del frontend con Cypress o Playwright que cubran el flujo de login y el control de acceso por rol desde la interfaz.

## 7. Rendimiento

**Fortaleza:** existe un objetivo `make bench` que corre benchmarks con k6 contra el endpoint de listado de mascotas, y el README documenta que las 6 corridas oficiales (frío/caliente) están reportadas en `docs/mediciones/perf/REPORT.md`, lo cual demuestra una cultura de medición real en lugar de asumir que el sistema "es rápido".

**Problema (actualizado):** el objetivo `make lighthouse` ya se ejecutó y sus resultados están versionados en `docs/mediciones/lighthouse/`: 6 corridas oficiales del 2026-08-01 (3 por solicitud) sobre `/login` y `/mascotas`. Performance, Accessibility y Best Practices cumplieron los umbrales configurados (≥80, ≥90 y ≥90 respectivamente), pero SEO obtuvo 82 frente al umbral configurado de 90, por lo que ese criterio no se cumple. Además, la corrida sobre `/mascotas` no audita la vista autenticada de Mascotas: el `authGuard` redirige de inmediato a `/login` sin una cookie de sesión válida, por lo que Lighthouse termina auditando el contenido de `/login` en ambos casos. La evidencia cubre por tanto la pantalla de login, no la experiencia real de la vista de Mascotas ya autenticada.

**Mejora sugerida:** cerrar la brecha de SEO (82 vs. umbral 90) documentada en la propia medición, y ejecutar una corrida adicional de Lighthouse con una sesión autenticada activa (por ejemplo, inyectando la cookie `access_token` antes de la auditoría) para obtener evidencia real de la vista de Mascotas, no solo de la redirección a `/login`.

## 8. Documentación

**Fortaleza:** la documentación técnica es inusualmente completa para un proyecto académico: ADRs numerados, diagramas C4 en dos niveles (contenedores y componentes del backend), diagrama de secuencia del flujo MVC completo (desde `MascotaApiService.listar()` en Angular hasta la serialización JSON de vuelta), colección Postman de 40 requests, diccionario de datos y matriz de trazabilidad de requisitos.

**Problema:** toda esta documentación está en español técnico denso y asume que el lector ya conoce Spring Security, Redis y JWT en profundidad. No hay una guía de onboarding pensada para alguien que llega al proyecto por primera vez (como el propio grupo GA) que explique, en lenguaje más simple, "cómo está armado esto" antes de entrar a los ADRs.

**Mejora sugerida:** agregar una sección corta de "Arquitectura en 5 minutos" al inicio del README o en un documento aparte, con una explicación de alto nivel antes de remitir a los ADRs y diagramas C4 detallados.

## 9. Despliegue

**Fortaleza:** el despliegue está completamente contenedorizado con Docker Compose, con un flujo de arranque documentado y reproducible (`git clone` → `.env` → `make up` → verificar servicios `healthy`), sin pasos manuales adicionales como configurar IntelliJ o pgAdmin, lo cual reduce mucho la fricción de poner el sistema en marcha desde cero.

**Problema:** el propio README es explícito en que la versión actual es una release candidate (`v0.9.0-rc`) y que "no debe desplegarse tal cual en producción", listando como razones el certificado autofirmado, las credenciales de desarrollo y la falta de evaluación en un entorno equivalente a producción. Es una limitación honesta, pero también significa que el "despliegue" documentado es únicamente para entorno local/académico, no un despliegue real verificado.

**Mejora sugerida:** documentar, aunque sea a nivel de plan (sin necesidad de implementarlo completo para esta entrega), qué cambiaría concretamente para pasar de `docker-compose.yml` local a un entorno de staging real: gestor de secretos, certificado válido, health checks para orquestador, y al menos un borrador de pipeline de CI/CD más allá del workflow que ya existe en `.github/workflows`.

## Cierre

**Fortalezas principales:** modelo de autenticación por cookies bien diseñado (sin `localStorage`, con revocación en Redis), cobertura de pruebas alta y verificable con umbral automático, documentación técnica extensa con trazabilidad real (ADRs, C4, matriz de requisitos), y honestidad del propio equipo al declarar explícitamente sus limitaciones en el README en lugar de ocultarlas.

**Debilidades identificadas:** las mediciones SUS y Lighthouse ya están ejecutadas y versionadas, pero no están enlazadas de forma visible desde la tabla de evidencia del README, y la corrida de Lighthouse sobre `/mascotas` no audita realmente la vista autenticada (redirige a `/login`); el rate limiting de login no escala a múltiples instancias; hay un secreto (contraseña del admin sembrado) hardcodeado en el código fuente; y la cobertura de ramas condicionales sigue siendo notablemente menor que la de líneas, lo que sugiere casos borde sin probar.

**Mejoras recomendadas:** enlazar desde el README las mediciones de usabilidad (SUS) y de SEO (Lighthouse, 82 vs. umbral 90) que ya están versionadas pero poco visibles, y obtener evidencia Lighthouse real de la vista autenticada de Mascotas; migrar el rate limiter a Redis; externalizar la contraseña del admin sembrado a variable de entorno; y reforzar la cobertura de pruebas específicamente en las ramas de autorización por rol y propiedad.

**Valoración general:** BIOPET es un PFC técnicamente sólido, con una madurez de ingeniería (ADRs, pruebas automatizadas con umbral, digest-pinning de imágenes, manejo de errores estandarizado) que va más allá de lo típico en un proyecto de curso. Su mayor fortaleza no es la ausencia de problemas, sino que el propio equipo los documenta con honestidad en vez de esconderlos — lo cual facilita exactamente el tipo de revisión cruzada que pide esta actividad. Las mejoras señaladas aquí son en su mayoría de cierre de brechas ya identificadas por el propio equipo, no descubrimientos de fallas ocultas, lo que habla bien de la calidad general del proyecto.
