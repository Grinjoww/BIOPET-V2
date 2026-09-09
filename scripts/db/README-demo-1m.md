# Base de demostración BIOPET-V2 con más de 1.000.000 de registros

Procedimiento reproducible para levantar, en cualquier máquina, una base
PostgreSQL de **demostración** poblada con más de un millón de filas sintéticas
compatibles con el esquema **actual** de BIOPET-V2.

| Archivo | Qué hace |
|---|---|
| `cargar-demo-1m.sql` | Genera la carga (escribe). Lleva guarda de nombre de base de datos. |
| `verificar-demo-1m.sql` | Cuenta, valida integridad y muestra el `TOTAL_GENERAL`. Solo lectura. |

---

## 1. Qué NO hacen estos scripts

Son scripts **manuales y versionados en Git**, no migraciones Flyway. Viven
fuera de `Backend/src/main/resources/db/migration/` deliberadamente: no se
registran en `flyway_schema_history`, no se ejecutan al arrancar el backend y no
modifican el esquema.

- **No tocan la facturación electrónica ni el SRI.** No escriben en `facturas`,
  `factura_detalles`, `factura_documentos`, `factura_eventos_sri`,
  `factura_pagos`, `secuencial_emision`, `emisor_fiscal`, `punto_emision`,
  `tarifa_impuesto`, `concepto_facturable` ni `datos_facturacion`. La carga solo
  **lee** `facturas` y `datos_facturacion` (un `count(*)`) como guarda previa a
  la purga, para no romper nunca una clave foránea fiscal.
- **No modifican ninguna migración existente** (V1–V11 al momento de escribir
  esto: dominio, facturación, respaldos, auditoría e índice de rendimiento
  sobre `citas`) ni añaden una nueva.
- **No crean índices nuevos.** El trabajo posterior de optimización debe partir
  de `EXPLAIN ANALYZE` medido sobre esta carga.
- **No usan `DROP DATABASE`, `DROP SCHEMA` ni `TRUNCATE`.**

---

## 2. Distribución de registros

```
usuarios        2.000   (200 veterinarios + 1.800 dueños)
mascotas       10.000   (~5,5 mascotas por dueño)
citas         400.000   (40 citas por mascota)
consultas     300.000   (30 consultas por mascota)
vacunas       300.000   (30 vacunas por mascota)
------------------------------------------------
TOTAL       1.012.000   > 1.000.000  (margen del 1,2 %)
```

**Por qué así.** No se genera un millón de usuarios: eso produciría una base
irreal (una veterinaria no tiene un millón de clientes) y además dejaría las
tablas transaccionales vacías, que es justamente donde interesa medir paginación
y planes de ejecución. El volumen se concentra en `citas`, `consultas` y
`vacunas`, con cardinalidades padre-hijo verosímiles.

Para cambiar el volumen se editan los seis `\set` del bloque *PARÁMETROS DE
VOLUMEN* al principio de `cargar-demo-1m.sql` (manteniendo
`n_duenios = n_usuarios - n_veterinarios`).

### Cómo se generan los datos

Todo es **set-based**: cinco sentencias `INSERT INTO ... SELECT ... FROM
generate_series(...)`, sin un solo `INSERT` fila a fila y sin bucles PL/pgSQL.

Los datos son **sintéticos y deterministas** — la misma ejecución produce
siempre el mismo contenido:

- `usuario000001@demo.biopet.local`, `Usuario Demo 000001`
- `Mascota Demo 000001`, `Cita demo 0000001 - Control general`
- Ningún dato personal real.
- `password_hash` lleva un valor **inválido como BCrypt**: ninguna de las 2.000
  cuentas sintéticas puede iniciar sesión. Es relleno, no una credencial.

El reparto de claves foráneas usa aritmética modular con multiplicadores
coprimos con su módulo (hash multiplicativo de Knuth), no `random()`. Eso da
distribución uniforme, reproducible en cualquier máquina y —usando
multiplicadores distintos por columna— **descorrelacionada**: cada mascota no
ve siempre al mismo veterinario, que es lo que ocurriría con un simple `i % n`.

Los `id` **no se insertan de forma explícita**: se dejan a los `BIGSERIAL`. Así
la carga nunca colisiona con filas preexistentes y las secuencias quedan
correctas por construcción, sin necesidad de `setval`. El script de verificación
lo comprueba de todos modos (sección D).

---

## 3. Seguridad: por qué no puede correr contra producción

`cargar-demo-1m.sql` aborta **antes de escribir nada** si el nombre de la base
no es de demostración. Dos barreras redundantes:

1. **Lista blanca** — solo `biopet_db_1m_v2`.
2. **Lista negra** — `biopet_db`, `BiopetABD`, `postgres`, `template1`, incluso
   si alguien ampliara la lista blanca sin pensarlo.

La guarda es un bloque `DO ... RAISE EXCEPTION` **del lado del servidor**, no un
`\if` de psql, para que siga siendo efectiva aunque el archivo se ejecute desde
pgAdmin, DBeaver o cualquier cliente que ignore los meta-comandos `\`.

### Idempotencia

El script es re-ejecutable. Antes de generar, purga la carga DEMO anterior
mediante un `DELETE` acotado (nunca `TRUNCATE`). Las filas DEMO se identifican
por una convención que no puede colisionar con datos reales:

```sql
email LIKE '%@demo.biopet.local'
```

El TLD `.local` está reservado para uso interno (mDNS, RFC 6762), así que ningún
correo real pertenece a ese dominio. A partir de ahí, una mascota es DEMO si
cuelga de un usuario DEMO, y una cita/consulta/vacuna es DEMO si cuelga de una
mascota DEMO o de un veterinario DEMO. **Cualquier fila ajena que exista en la
base de demo sobrevive intacta.**

Toda la carga va en **una sola transacción**: o queda completa y coherente, o no
queda nada.

> **Al re-ejecutar:** la segunda pasada es bastante más lenta (~98 s frente a
> ~34 s) porque antes de generar borra el millón de filas anteriores, y deja
> tuplas muertas que casi duplican el tamaño en disco. Si a continuación vas a
> medir con `EXPLAIN ANALYZE`, compacta primero para no medir espacio muerto:
>
> ```bash
> for t in usuarios mascotas citas consultas vacunas; do
>   psql -U postgres -d biopet_db_1m_v2 -c "VACUUM (FULL, ANALYZE) $t;"
> done
> ```
>
> (Cada `VACUUM` debe ir en su propio `-c`: no puede ejecutarse dentro de un
> bloque de transacción, y `psql -c` con varias sentencias abre una.)

---

## 4. Procedimiento reproducible (máquina nueva, clonando desde GitHub)

> No hay contraseñas ni volcados binarios en el repositorio. Las credenciales
> salen de tu `.env` local, que está en `.gitignore`. Copia `.env.example` a
> `.env` y ajústalo.

### Paso 1 — Levantar PostgreSQL

**Opción A — Docker (reutiliza el `docker-compose.yml` que ya existe en el
repo; no se añade una segunda configuración):**

```bash
cp .env.example .env          # y edita los valores
docker compose up -d postgres redis
```

**Opción B — PostgreSQL instalado localmente:** basta con que el servidor esté
escuchando en `localhost:5432`.

> **Redis es obligatorio para los endpoints autenticados.** `TokenBlacklistService`
> consulta Redis en *cada* petición con JWT, así que sin él toda ruta protegida
> responde `401` (la excepción se enmascara como redirección a `/error`).
> Levántalo aunque uses PostgreSQL nativo — sin arrancar el `postgres` del
> compose, que chocaría con el puerto 5432 del servidor local:
>
> ```bash
> docker compose up -d redis
> ```

### Paso 2 — Crear la base de demostración vacía

Con el rol propietario del proyecto (`biopet_user` en Docker; el superusuario
`postgres` en una instalación local):

```bash
# Docker
docker compose exec postgres psql -U biopet_user -d postgres \
  -c "CREATE DATABASE biopet_db_1m_v2;"

# Local
psql -h localhost -p 5432 -U postgres -d postgres \
  -c "CREATE DATABASE biopet_db_1m_v2 OWNER biopet_user;"
```

La base de trabajo habitual (`biopet_db`) **no se toca**.

### Paso 3 — Arrancar el backend para que Flyway aplique todas las migraciones

Flyway es la única fuente de evolución del esquema. Se apunta el backend a la
base de demo **sin modificar ningún archivo de configuración**, solo variables
de entorno:

```bash
cd Backend
# PG_DUMP_PATH: solo si esta maquina tiene varias versiones de PostgreSQL
# instaladas (ver .env.example) -pg_dump debe coincidir con la version del
# SERVIDOR o el modulo de respaldos queda FALLIDO al intentar un respaldo real.
DB_URL=jdbc:postgresql://localhost:5432/biopet_db_1m_v2 \
DB_USER=biopet_user     DB_PASSWORD=...      \
DB_APP_USER=biopet_app  DB_APP_PASSWORD=...  \
JWT_SECRET=...                               \
PG_DUMP_PATH=...                             \
./mvnw spring-boot:run
```

En PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/biopet_db_1m_v2"
# ...resto de variables...
.\mvnw.cmd spring-boot:run
```

Cuando el log muestre que Flyway aplicó todas las migraciones (V1–V11 al
momento de escribir esto) y el backend esté `UP`, se puede detener (Ctrl+C).
El esquema ya está creado.

> El callback `afterMigrate.sql` concede automáticamente los privilegios de
> `biopet_app` sobre las tablas nuevas. Si el rol `biopet_app` no existe en esta
> instancia, el callback es un no-op seguro; créalo con `db/roles-bootstrap.sql`.

### Paso 4 — Ejecutar la carga

```bash
psql -h localhost -p 5432 -U biopet_user -d biopet_db_1m_v2 \
     -f scripts/db/cargar-demo-1m.sql
```

El script informa del tiempo de generación y ejecuta `ANALYZE` únicamente sobre
las cinco tablas afectadas.

### Paso 5 — Verificar

```bash
psql -h localhost -p 5432 -U biopet_user -d biopet_db_1m_v2 \
     -f scripts/db/verificar-demo-1m.sql
```

Comprueba en la salida:

- **A** — conteo por tabla y `TOTAL_GENERAL`.
- **A.1** — `CUMPLIDO (> 1.000.000)`.
- **C / C.1** — integridad referencial y restricciones `CHECK`: todo a cero.
- **D** — secuencias `OK`.
- **F** — tablas fiscales, para dejar constancia de que la carga no las tocó.
- **G.1** — `FLYWAY OK (V1..V11 aplicadas)`.

### Paso 6 — Arrancar BIOPET-V2 contra la base de demostración

Las mismas variables de entorno del paso 3. Para la presentación conviene
ejercitar los endpoints paginados, que es donde se nota el volumen:

```
GET /api/citas?page=0&size=20
GET /api/consultas?page=0&size=20
GET /api/vacunas?page=0&size=20
GET /api/mascotas?page=0&size=20
```

---

## 5. Volver a la base de trabajo normal

Basta con arrancar el backend sin las variables de entorno del paso 3: vuelve al
`DB_URL` por defecto (`biopet_db`). La base de demo puede eliminarse cuando ya
no haga falta:

```sql
DROP DATABASE biopet_db_1m_v2;   -- ejecutar A MANO, nunca desde un script
```
