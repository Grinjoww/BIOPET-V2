# Tendencias Web Modernas: Jamstack, PWA e IA Generativa

**Autor:** Fajardo Montes Michael Xavier
**Actividad:** Unidad IV - GA | PFC BIOPET
**Archivo:** docs/u4/investigacion/TENDENCIAS-WEB.md

---

## 1. Jamstack

Jamstack es un patrón arquitectónico para desarrollo web cuyo nombre es un acrónimo de JavaScript, APIs y Markup (contenido generado por un generador de sitios estáticos) (Wikipedia, 2026). El sitio oficial del movimiento la define como una arquitectura diseñada para hacer la web más rápida, más segura y más fácil de escalar, construida sobre herramientas y flujos de trabajo que los desarrolladores ya conocen. A diferencia de una aplicación monolítica tradicional, en Jamstack todo el frontend se pre-construye en páginas y assets estáticos altamente optimizados durante el proceso de build, que luego se sirven directamente desde una CDN, reduciendo la complejidad de mantener servidores dinámicos como infraestructura crítica.

**Ventajas principales:** al servirse contenido pre-renderizado desde una CDN se gana en velocidad de carga, menor superficie de ataque (no hay servidor dinámico expuesto en cada request) y facilidad de escalado horizontal. Además, Jamstack habilita una arquitectura *composable*, donde la lógica de negocio se consume a través de APIs de terceros o propias en lugar de vivir acoplada al backend (Jamstack.org, 2026).

**Limitaciones reales:** el propio ecosistema reconoce que un sitio Jamstack depende fuertemente de servicios de terceros para todo lo dinámico (autenticación, formularios, búsqueda, pagos), por lo que si uno de esos proveedores falla, la funcionalidad se ve directamente afectada (Umbraco, s.f.). También existe una curva de aprendizaje mayor a la esperada, porque no hay una sola forma "correcta" de armar el stack: cada proyecto combina herramientas distintas (NashTech Blog, 2023).

**Escenarios de uso recomendados:** blogs, landing pages, documentación técnica, sitios de marketing y catálogos de e-commerce que no requieren lógica de servidor compleja en cada petición. No es el enfoque más natural para sistemas altamente transaccionales con lógica de negocio pesada y datos que cambian constante por usuario, como sería un ERP o un sistema de gestión académica con múltiples roles.

## 2. Progressive Web Apps (PWA)

Según la documentación oficial de MDN Web Docs, una PWA es una aplicación construida con tecnologías de la plataforma web pero que ofrece una experiencia de usuario similar a la de una app nativa, y requiere un manifiesto web (web app manifest) con la información suficiente para que el navegador pueda instalarla.

**Instalación y experiencia tipo app:** el manifiesto define ícono, pantalla de bienvenida, color de tema y modo de visualización, lo que permite que el usuario agregue la PWA a su pantalla de inicio como si fuera una app descargada de una tienda (MDN, 2026).

**Service workers y funcionamiento offline:** el service worker es el componente central que habilita el trabajo sin conexión. Actúa como un proxy virtual entre el navegador y la red, capaz de cachear los assets de un sitio y ponerlos disponibles cuando el dispositivo del usuario está offline, ejecutándose en un hilo separado del JavaScript principal de la página. Gracias a APIs como Background Sync, una PWA puede además operar en segundo plano y sin conexión, por ejemplo permitiendo que una app de mensajería reciba mensajes aunque no esté abierta y muestre una notificación al usuario.

**Utilidad en aplicaciones modernas:** las PWA resultan especialmente valiosas en contextos con conectividad inestable (zonas rurales, redes móviles limitadas) porque garantizan que la aplicación siga siendo usable aunque se pierda la conexión, algo directamente relevante para sistemas académicos usados por estudiantes fuera del campus.

## 3. Inteligencia Artificial Generativa en el desarrollo web

El uso de IA generativa como copiloto de programación ya es mayoritario: para 2025 el 84% de los desarrolladores usaba o planeaba usar herramientas de IA, aunque paradójicamente la confianza en su output bajó al 29% frente al 40% de 2023 (Loop Studio, 2026), lo que refleja que a mayor uso, más clara se vuelve la percepción de sus límites reales.

**Usos actuales:** generación de código boilerplate, refactorización, redacción de pruebas unitarias, explicación de código heredado y generación de documentación técnica. Un estudio académico reciente que revisó 87 publicaciones sobre el tema encontró aportes de estos modelos en seis etapas del ciclo de vida del software: ingeniería de requisitos, diseño arquitectónico, implementación, aseguramiento de calidad, mantenimiento y automatización de DevOps (Arsha & Sreeji, 2026, IJRASET).

**Riesgos y límites documentados:** el reporte 2026 de Veracode sobre seguridad de código generado por IA, tras evaluar más de 100 modelos en tareas de Java, JavaScript, Python y C#, encontró que el 45% del código generado por IA introdujo una vulnerabilidad del OWASP Top 10, con Java como el lenguaje de mayor riesgo. Otro análisis independiente confirma que el código generado por IA tiene 2.74 veces más vulnerabilidades que el código escrito por humanos, y que aumentar el tamaño del modelo mejora la corrección sintáctica pero no la seguridad, que se mantiene estancada entre 45% y 55% sin importar la generación del modelo. Esto confirma que la IA no reemplaza el criterio de ingeniería: puede sugerir patrones, pero no puede sopesar una hoja de ruta a tres años contra el stack de habilidades del equipo, los requisitos de cumplimiento y los modos de falla específicos de un sistema legado.

## 4. Reflexión crítica

Ninguna de estas tres tendencias debería adoptarse "porque está de moda". Jamstack tiene sentido cuando el proyecto realmente se beneficia de contenido pre-renderizado y bajo acoplamiento con el backend; forzarlo en un sistema con lógica transaccional compleja solo agrega fricción. Una PWA aporta valor real cuando el usuario objetivo enfrenta conectividad inestable o necesita acceso rápido tipo app; si el sistema siempre se usa con buena conexión desde un navegador de escritorio, el esfuerzo de implementar service workers puede no justificarse. Y la IA generativa, según la evidencia citada arriba, acelera tareas repetitivas pero introduce riesgo de seguridad medible si el código que produce se integra sin revisión humana y sin pruebas automatizadas. La pregunta correcta no es "¿usamos esta tecnología?" sino "¿qué problema concreto de este proyecto resuelve, y a qué costo?".

## 5. Aplicación a BIOPET

BIOPET es un sistema web de gestión veterinaria construido como SPA en Angular 17.3 sobre un backend Spring Boot 3.2.12/Java 21, con PostgreSQL 16, Redis 7 para caché, autenticación JWT entregada por cookies `HttpOnly`/`Secure`, documentación OpenAPI/Swagger y cuatro roles reales (`ADMIN`, `VETERINARIO`, `AUXILIAR`, `DUENO`) con control de acceso por rol y por propiedad sobre el recurso `mascotas` (repositorio del PFC, README, 2026).

**Jamstack no es el mejor encaje aquí.** El sistema es intrínsecamente transaccional: cada rol ve datos distintos (un `DUENO` solo sus propias mascotas), la autenticación depende de cookies de sesión validadas en cada request contra Redis, y hay operaciones de escritura constantes (alta, edición, baja lógica de mascotas). Pre-renderizar contenido estático y servirlo desde una CDN no aporta valor cuando casi todo el contenido es dinámico y depende del usuario autenticado; forzar Jamstack aquí solo movería complejidad del backend al cliente sin beneficio real.

**PWA es la tendencia con mayor valor concreto para BIOPET.** El frontend ya es una SPA en Angular, que es precisamente el punto de partida técnico más simple para convertir una aplicación en PWA (agregar manifest + service worker, sin rehacer arquitectura). El valor no es teórico: en un sistema veterinario, el personal (`VETERINARIO`/`AUXILIAR`) puede necesitar consultar el historial de una mascota durante una visita a domicilio o en zonas de la clínica con señal débil, y un `DUENO` puede querer revisar los datos de su mascota o recibir un recordatorio de vacuna sin conexión estable. Un service worker permitiría cachear el listado y detalle de mascotas ya consultado para lectura offline, y una futura API de notificaciones push podría usarse para recordatorios de citas o vacunas, algo directamente relevante en un contexto como Ecuador donde la conectividad móvil no siempre es constante fuera de zonas urbanas.

**IA generativa: valor limitado y con riesgo real en este proyecto.** Podría ayudar en tareas ya cerradas del ciclo de vida (generar casos de prueba adicionales, documentación de la API a partir del código, resúmenes de commits), pero BIOPET maneja datos sensibles de salud animal y control de acceso fino por rol y propiedad (`MascotaService.verificarPropiedad`), exactamente el tipo de lógica de autorización donde la evidencia citada en la Sección 3 muestra que el código generado por IA falla con más frecuencia en seguridad. Usar IA generativa sin revisión humana estricta en los módulos de autenticación/autorización de BIOPET sería el peor escenario posible según los datos de Veracode citados arriba.

**Conclusión:** de las tres tendencias, **PWA es la que aporta más valor real a BIOPET**, porque resuelve un problema concreto del dominio (acceso confiable a información de mascotas con conectividad intermitente) aprovechando que el frontend Angular ya tiene la base técnica necesaria, sin tocar la arquitectura de seguridad ya implementada del backend.

## Fuentes

1. Jamstack.org. (2026). *What is the Jamstack?* https://jamstack.org/what-is-jamstack/
2. Umbraco. (s.f.). *What is Jamstack? JavaScript, APIs, and Markup (JAM)*. https://umbraco.com/knowledge-base/jamstack/
3. NashTech Blog. (2023). *JAMstack architecture*. https://blog.nashtechglobal.com/jamstack-architecture/
4. Cloudflare. (2026). *What is JAMstack?* https://www.cloudflare.com/learning/performance/what-is-jamstack/
5. MDN Web Docs. (2026). *What is a progressive web app?* https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/What_is_a_progressive_web_app
6. MDN Web Docs. (2025). *js13kGames: Making the PWA work offline with service workers*. https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Tutorials/js13kGames/Offline_Service_workers
7. Loop Studio. (2026). *The State of AI in Software Development 2026 (Report)*. https://loopstudio.dev/the-state-of-ai-in-software-development/
8. Veracode. (2026). *2026 GenAI Code Security Report: AI Is Writing More of Your Code but Security Hasn't Caught Up*. https://www.veracode.com/blog/2026-genai-code-security-report-ai-risk/
9. Modall. (2026). *AI in Software Development: 25+ Trends & Statistics (2026)*. https://modall.ca/blog/ai-in-software-development-trends-statistics
10. Arsha, C. H., & Sreeji, K. B. (2026). *Generative AI in Software Engineering*. International Journal for Research in Applied Science & Engineering Technology (IJRASET). https://doi.org/10.22214/IJRASET.2026.77834
11. Mariscal Cabrera, J. J., Beltrán Montiel, F. A., & Taipe Mora, Z. M. (2026). *BIOPET — Repositorio del PFC* [README, código fuente]. GitHub. https://github.com/Grinjoww/PE-U4-GA-EQUIPO-H

---

*(Palabras del cuerpo del texto, sin contar título, fuentes ni metadatos: ~950 palabras — cumple el mínimo de 400 palabras solicitado.)*
