# BIOPET — Entrega 1B

Proyecto Fin de Curso de Aplicaciones Web. Esta entrega implementa el módulo de autenticación JWT stateless y el CRUD principal de **Mascotas** para el sistema veterinario BIOPET.

## Stack

- Java 21
- Spring Boot 3.2.x
- Spring Security 6
- jjwt 0.12.x
- Spring Data JPA + Hibernate
- PostgreSQL 16
- Flyway
- Redis 7
- Angular 17+
- Docker Compose

## Ejecución reproducible con Makefile

El proyecto se levanta con un solo comando, sin necesidad de abrir IntelliJ, pgAdmin
ni ejecutar pasos manuales adicionales.

```bash
# 1. Clonar el repositorio
git clone https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc.git
cd PFC-VET-ENTR3-v0.9.0-rc

# 2. Copiar variables de entorno
cp .env.example .env

# 3. Levantar todo el sistema (postgres, redis, backend, frontend)
make up

# 4. Verificar que los 4 servicios estén healthy
docker compose ps

# 5. Acceder a la aplicación
# Frontend: http://localhost:4200
# Swagger UI: http://localhost:8080/api/swagger-ui.html
# Actuator Health: http://localhost:8080/actuator/health
```

### Objetivos disponibles del Makefile

| Comando | Función |
|---|---|
| `make up` | Levanta el sistema completo, construyendo imágenes si hace falta. |
| `make down` | Detiene los contenedores **sin borrar volúmenes** (los datos de Postgres/Redis se conservan). |
| `make test` | Ejecuta las pruebas del backend (`mvn test`). |
| `make bench` | Ejecuta las pruebas de rendimiento con k6 (pendiente de implementar). |
| `make audit` | Ejecuta la auditoría automatizada de seguridad OWASP (pendiente de implementar). |
| `make clean` | Detiene contenedores y elimina huérfanos, conservando los datos. |
| `make reset-db` | **Destructivo.** Elimina también los volúmenes (borra los datos de Postgres y Redis) para reiniciar el sistema desde cero. |

## Reproducibilidad de las imágenes (PostgreSQL y Redis)

Las imágenes de PostgreSQL y Redis están fijadas por **digest sha256** en
`docker-compose.yml`, en lugar de solo por *tag* (por ejemplo `postgres:16-alpine`).
Esto evita que una reconstrucción futura del proyecto use, sin darse cuenta, una
versión distinta de la imagen si el mantenedor del tag la actualiza silenciosamente
en Docker Hub. Con el digest fijado, `postgres:16-alpine@sha256:...` siempre resuelve
exactamente al mismo contenido binario.

### Imágenes base del build del backend

Las imágenes usadas para compilar y ejecutar el backend (`maven:3.9-eclipse-temurin-21`
y `eclipse-temurin:21-jre-alpine`) también están fijadas por digest sha256 en
`Backend/Dockerfile`, por la misma razón que Postgres y Redis en `docker-compose.yml`.

Para consultar y actualizar estos digests:

```bash
docker pull maven:3.9-eclipse-temurin-21
docker pull eclipse-temurin:21-jre-alpine
docker inspect --format='{{index .RepoDigests 0}}' maven:3.9-eclipse-temurin-21
docker inspect --format='{{index .RepoDigests 0}}' eclipse-temurin:21-jre-alpine
```

Cada comando `docker inspect` imprime la línea completa `imagen:tag@sha256:...`
lista para copiar en el `FROM` correspondiente de `Backend/Dockerfile`. Después de
actualizar, validar con:

```bash
make up
docker compose ps
```

### Cómo consultar el digest actual de una imagen

```bash
docker buildx imagetools inspect postgres:16-alpine
docker buildx imagetools inspect redis:7-alpine
```

Esto muestra el digest más reciente publicado para ese tag, sin necesidad de
descargar la imagen completa.

### Cómo actualizar un digest cuando se decida subir de versión

1. Ejecutar el comando de inspección correspondiente (arriba) y copiar el nuevo
   valor de `Digest:`.
2. Reemplazar el digest en la línea `image:` del servicio correspondiente en
   `docker-compose.yml`.
3. Validar que el archivo siga siendo sintácticamente correcto:
```bash
   docker compose config
```
4. Levantar el sistema para confirmar que arranca sin errores con la nueva imagen:
```bash
   make up
   docker compose ps
```
5. Documentar el cambio de digest en el mensaje de commit (por ejemplo:
   `chore(docker): actualiza digest de postgres:16-alpine`).

### Nota sobre `backend` y `frontend`

Las imágenes `backend` y `frontend` se construyen localmente a partir del código
del repositorio (`Dockerfile` propio), por lo que no aplica pinearlas por digest de
un registro externo: su reproducibilidad depende de que el código fuente esté
versionado, lo cual ya se cumple mediante Git.

## Pruebas automatizadas

```bash
cd backend
mvn test
```

## Endpoints principales

| Método | URL | Auth | Descripción |
|---|---|---|---|
| POST | `/api/auth/registro` | No | Registrar usuario |
| POST | `/api/auth/login` | No | Iniciar sesión y emitir tokens |
| POST | `/api/auth/logout` | JWT | Revocar JTI en Redis |
| POST | `/api/auth/refresh` | Refresh token | Emitir nuevo accessToken |
| GET | `/api/usuarios/me` | JWT | Perfil autenticado |
| GET | `/api/mascotas?page=0&size=10&sort=id,asc` | JWT | Listado paginado |
| GET | `/api/mascotas/{id}` | JWT | Buscar mascota |
| POST | `/api/mascotas` | JWT | Crear mascota |
| PUT | `/api/mascotas/{id}` | JWT | Actualizar mascota |
| DELETE | `/api/mascotas/{id}` | JWT | Soft delete |

## Credenciales de prueba

El sistema crea automáticamente un usuario administrador al arrancar. Úsalo para
probar el CRUD completo (crear/editar/borrar requieren rol ADMIN/VETERINARIO/AUXILIAR):

| Email             | Contraseña  | Rol          |
|-------------------|-------------|--------------|
| `admin@biopet.ec` | `Admin123*` | `ROLE_ADMIN` |

También puedes registrar un usuario nuevo con `/api/auth/registro`. Por seguridad,
todo registro público se asigna como `ROLE_DUENO` (el campo `rol` del JSON es
obligatorio por validación, pero el servidor siempre asigna `ROLE_DUENO`):

​```json
{
  "nombre": "Jaime Mariscal",
  "email": "jaime@biopet.com",
  "password": "ClaveSegura123*",
  "rol": "ROLE_DUENO"
}
​```

Un usuario `ROLE_DUENO` puede consultar mascotas, pero crear, actualizar o eliminar
requiere el usuario administrador de arriba.

## Actualizar los digests de las imágenes

Las imágenes de PostgreSQL y Redis están fijadas por digest sha256 para evitar
derivas silenciosas. Para actualizar a una versión más reciente:

1. `docker pull postgres:16-alpine` (o la versión que corresponda)
2. `docker inspect --format='{{index .RepoDigests 0}}' postgres:16-alpine`
3. Copiar el nuevo digest a `docker-compose.yml`