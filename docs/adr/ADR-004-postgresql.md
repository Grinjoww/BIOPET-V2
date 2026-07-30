# ADR-004: Estrategia de base de datos reproducible con PostgreSQL

## Identificador
ADR-004

## Título
Gestor de base de datos PostgreSQL 16 y estrategia de inicialización reproducible (Flyway + scripts de arranque)

## Estado
Aceptado — implementado y verificado en la rama `fred/db-reproducible` de la Tercera Entrega (v0.9.0-rc).

## Fecha
- Decisión original (PostgreSQL como gestor): Entrega 1A/1B (heredada, no se discute aquí).
- Formalización de la estrategia de reproducibilidad e inicialización: Tercera Entrega (julio de 2026).

## Contexto
Desde la Entrega 1B, el esquema de PostgreSQL se gestiona mediante Flyway
(`Backend/src/main/resources/db/migration/V1__schema_inicial.sql`), con
`spring.jpa.hibernate.ddl-auto: validate` (Hibernate nunca modifica el esquema).
Ese mecanismo ya fue evaluado y aprobado en la Entrega 1B (criterio C3, Excelente 100%).

La Tercera Entrega (Bloque B.1 de la guía) exige, además, que el sistema pueda
reconstruirse de forma idéntica desde una clonación limpia usando únicamente Docker,
sin depender de que el backend llegue a arrancar, y que la cuenta con la que la
aplicación se conecta a la base tenga privilegios mínimos (sin DBA, OWNER ni
superusuario), separada de la cuenta que aplica las migraciones.

Antes de este ADR, un único usuario (`biopet_user`) era a la vez el dueño de la base,
quien ejecutaba Flyway, y quien usaba el backend en tiempo de ejecución — sin
separación de privilegios.

## Problema
¿Cómo lograr que Docker pueda crear el esquema completo (tablas, datos semilla, cuenta
de aplicación) sin depender de que Spring Boot/Flyway arranquen primero, sin duplicar
la fuente de verdad del esquema, y sin dejar de cumplir con el requisito de privilegios
mínimos para la cuenta de aplicación?

## Alternativas consideradas

**Alternativa A — Mover todo (esquema + seed + roles) a una migración Flyway adicional
(`V2__...`), eliminando el mecanismo de `docker-entrypoint-initdb.d`.**
Ventaja: un solo mecanismo de verdad. Desventaja: no calza con la redacción literal de
la guía, que exige archivos `db/schema.sql`/`db/seed.sql` montados en Postgres, y no
resuelve el problema de que los roles de Postgres deben poder crearse en el primer
arranque del contenedor, antes de que exista ningún backend.

**Alternativa B — Doble mecanismo con responsabilidades separadas: Flyway sigue siendo
la única fuente de verdad del esquema de tablas de dominio (`usuarios`, `mascotas`);
`docker-entrypoint-initdb.d` se usa exclusivamente para sembrar datos deterministas
(usuario admin) y crear roles de Postgres con privilegios mínimos, apoyándose en que
Spring Boot ya soporta `spring.flyway.baseline-on-migrate: true` para reconciliar
ambos mecanismos sin conflicto.**
Ventaja: cumple la letra y el espíritu de la guía; cero riesgo sobre Flyway, que ya
estaba aprobado; permite crear roles de Postgres desde el primer arranque del
contenedor, que es el único momento en que eso es posible. Desventaja: dos mecanismos
distintos coexistiendo, que deben documentarse con claridad para no confundir a un
revisor.

## Decisión adoptada
Se adopta la **Alternativa B**. Se crean tres archivos versionados en `db/`:

- `db/schema.sql`: réplica exacta del esquema que Flyway ya aplica
  (`V1__schema_inicial.sql`), usada solo para que Docker pueda levantar el esquema
  sin depender del backend.
- `db/seed.sql`: inserta el usuario `admin@biopet.ec` con hash BCrypt (costo 12),
  de forma idempotente (`WHERE NOT EXISTS`).
- `db/roles.sql`: crea el rol `biopet_app` con `LOGIN`, sin `SUPERUSER`, `CREATEDB`
  ni `CREATEROLE`, con `GRANT SELECT, INSERT, UPDATE, DELETE` únicamente sobre
  `usuarios` y `mascotas`, `USAGE`/`SELECT` sobre sus secuencias, `EXECUTE` sobre las
  funciones existentes, y `ALTER DEFAULT PRIVILEGES` para que las funciones que cree
  a futuro el propietario (`biopet_user`) hereden automáticamente `EXECUTE` para
  `biopet_app`.

Los tres archivos se montan en `docker-compose.yml` bajo
`/docker-entrypoint-initdb.d/` con prefijos `00-`, `01-`, `02-` (orden de ejecución).

El backend se reconfigura para que **Hibernate/JPA en tiempo de ejecución** use
`biopet_app` (`spring.datasource.username/password`), mientras que **Flyway** sigue
usando `biopet_user` (`spring.flyway.user/password`, propiedad soportada nativamente
por Spring Boot para separar la conexión de Flyway de la del resto de la aplicación).

Gracias a `spring.flyway.baseline-on-migrate: true` (ya presente desde la Entrega 1B,
sin modificar), Flyway detecta el esquema ya creado por `db/schema.sql` y establece un
baseline en la versión 1 en vez de reintentar ejecutar `V1__schema_inicial.sql`,
evitando el conflicto de "tabla ya existe".

## Justificación técnica
- Verificado con `psql`: `biopet_app` no tiene atributos de `Superuser`, `Create role`
  ni `Create DB`.
- Verificado con `flyway_schema_history`: una única fila `version 1`,
  `type = BASELINE`, `success = true` — sin reintento de la migración original.
- Verificado end-to-end: login (`SELECT` sobre `usuarios`) y creación de una mascota
  (`INSERT` sobre `mascotas`) funcionando correctamente con `biopet_app` conectado
  como usuario de aplicación real, no solo como rol creado sin uso.

## Consecuencias positivas
- Cumple el criterio C1/C2 de la rúbrica (separación de privilegios, arranque
  reproducible desde clonación limpia).
- No se tocó ni se puso en riesgo la migración Flyway ya evaluada en la Entrega 1B.
- La cuenta de aplicación limita el daño potencial de una futura vulnerabilidad de
  inyección o de una credencial filtrada, al no tener permisos de DDL ni de
  administración.

## Consecuencias negativas
- `db/schema.sql` es una copia manual de `V1__schema_inicial.sql`: si en el futuro se
  agrega `V2__...` en Flyway, `db/schema.sql` debe actualizarse manualmente para no
  quedar desfasado en una clonación limpia. Se documenta esta responsabilidad en el
  encabezado del propio archivo.
- La contraseña de desarrollo de `biopet_app` queda en texto plano en `db/roles.sql`
  y en `.env.example`, porque los scripts de inicialización de Postgres no pueden leer
  variables de entorno de Spring Boot. Se considera aceptable por tratarse de un valor
  exclusivamente de desarrollo, documentado igual que la contraseña del usuario admin.

## Impacto sobre el proyecto
Afecta `docker-compose.yml`, `Backend/src/main/resources/application.yml` y
`.env.example`. No afecta el módulo de autenticación (Jaime) ni el frontend (Zaida)
más allá de que ambos siguen conectándose a los mismos endpoints, sin cambios de
contrato.

## Referencias a otros documentos
- `ADR-005-despliegue.md` (estrategia de reproducibilidad de imágenes Docker).
- `docs/basedatos/CATALOGO-SP.md` (procedimientos/funciones y sus privilegios).