# ADR-005: Estrategia de despliegue reproducible con Docker

## Identificador
ADR-005

## Título
Reproducibilidad del despliegue: Makefile, pinning de imágenes por digest sha256 y arranque en un solo comando

## Estado
Aceptado — implementado y verificado en las ramas `fred/reproducibilidad-docker` y
`fred/pin-digests-docker` de la Tercera Entrega (v0.9.0-rc).

## Fecha
Tercera Entrega (julio de 2026).

## Contexto
La guía de la Tercera Entrega (Bloque B) exige que el proyecto pueda reconstruirse de
forma idéntica por un tercero, desde una clonación limpia, sin intervención manual, y
que las imágenes de terceros usadas por el sistema no cambien silenciosamente entre
reconstrucciones. Además, la retroalimentación de la Entrega 1B señaló que el pipeline
de CI estaba en `./workflows/ci.yml` en lugar de `.github/workflows/`, por lo que no
se ejecutaba automáticamente.

Antes de esta decisión, el proyecto se levantaba manualmente con
`docker compose up --build -d`, sin un punto de entrada único documentado, y las
imágenes de Postgres, Redis, Maven y Eclipse Temurin se referenciaban solo por *tag*
(por ejemplo `postgres:16-alpine`), que Docker Hub puede reconstruir y reemplazar sin
cambiar el nombre del tag.

## Problema
¿Cómo garantizar que cualquier persona (docente, compañero, revisor externo) obtenga
exactamente el mismo entorno al clonar el repositorio, sin pasos manuales, y sin que
una actualización silenciosa de una imagen de terceros cambie el comportamiento del
sistema entre una ejecución y otra?

## Alternativas consideradas

**Alternativa A — Documentar únicamente los comandos de Docker Compose en el README,
sin un Makefile.**
Ventaja: cero archivos nuevos. Desventaja: no ofrece un punto de entrada único y
memorizable (`make up`), y no resuelve el problema del pinning de imágenes.

**Alternativa B — Makefile con objetivos estándar (`up`, `down`, `test`, `bench`,
`audit`, `clean`, `reset-db`) + pinning de imágenes por digest sha256 en
`docker-compose.yml` y en `Backend/Dockerfile`.**
Ventaja: cumple exactamente lo que pide la guía; separa explícitamente operaciones
seguras (`down`, `clean`, que preservan datos) de operaciones destructivas
(`reset-db`, que borra volúmenes), reduciendo el riesgo de pérdida accidental de
datos durante el desarrollo diario.

## Decisión adoptada
Se adopta la **Alternativa B**.

Se crea un `Makefile` en la raíz del repositorio con los objetivos:
`up` (`docker compose up --build -d`), `down` (`docker compose down`, sin `-v`),
`test` (`cd Backend && mvn test`), `bench` y `audit` (mensajes informativos mientras
no existan los scripts de k6/auditoría OWASP reales), `clean` (limpia contenedores y
huérfanos preservando volúmenes) y `reset-db` (el único objetivo destructivo,
`docker compose down -v`, con advertencia explícita impresa antes de ejecutarse).

Se fijan por digest sha256, en vez de por tag: `postgres:16-alpine`,
`redis:7-alpine` (en `docker-compose.yml`), y `maven:3.9-eclipse-temurin-21`,
`eclipse-temurin:21-jre-alpine` (en `Backend/Dockerfile`). Los digests se obtuvieron
directamente con `docker pull` + `docker inspect --format='{{index .RepoDigests 0}}'`
contra el registro real en el momento de la implementación, no se inventaron ni se
copiaron de memoria.

Se documenta en el README el procedimiento exacto para consultar y actualizar estos
digests en el futuro (`docker buildx imagetools inspect` para las imágenes de
`docker-compose.yml`; `docker inspect` tras `docker pull` para las del `Dockerfile`),
junto con el paso de validación obligatorio (`docker compose config` + `make up`)
antes de dar por buena cualquier actualización de digest.

## Justificación técnica
- Verificado con `docker compose ps`: los 4 servicios (`postgres`, `redis`, `backend`,
  `frontend`) llegan a estado `healthy`/`started` tras `make reset-db && make up` desde
  un volumen completamente vacío, sin ningún paso manual adicional (ni IntelliJ, ni
  pgAdmin).
- Verificado que `make down` no elimina el volumen `postgres_data` (los datos
  persisten entre reinicios), mientras que `make reset-db` sí lo hace y lo advierte
  explícitamente antes de ejecutarlo.

## Consecuencias positivas
- Cumple el criterio C2 de la rúbrica (arranque en un solo comando desde clonación
  limpia) y refuerza la reproducibilidad exigida por el Bloque B.1.
- Reduce el riesgo de pérdida accidental de datos de desarrollo, al separar
  claramente comandos seguros de comandos destructivos.
- Las imágenes de terceros no pueden cambiar de contenido sin que el equipo lo note
  y lo decida explícitamente (un cambio de digest requiere una edición intencional
  del archivo, no ocurre solo).

## Consecuencias negativas
- Los digests deben actualizarse manualmente cuando el equipo decida adoptar una
  versión más reciente de una imagen base; si no se hace, el proyecto queda anclado
  a una versión específica indefinidamente (se considera aceptable, ya que es
  exactamente el comportamiento buscado por la guía).
- `make` no viene instalado por defecto en Windows; se documenta en el README como
  requisito de entorno (Chocolatey, winget, o WSL).

## Impacto sobre el proyecto
Afecta `Makefile` (nuevo), `docker-compose.yml`, `Backend/Dockerfile` y `README.md`.
No afecta código de aplicación (backend/frontend) ni requiere cambios de Jaime o
Zaida, más allá de que ahora usan `make up` en vez de `docker compose up` directo.

## Referencias a otros documentos
- `ADR-004-postgresql.md` (estrategia de base de datos reproducible).