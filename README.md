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

## Ejecución en 5 pasos

```bash
# 1. Clonar o descomprimir el proyecto
cd BIOPET-Entrega1B

# 2. Copiar variables de entorno
cp .env.example .env

# 3. Levantar todos los servicios
docker compose up --build -d

# 4. Verificar estado healthy
docker compose ps

# 5. Acceder a la aplicación
# Frontend: http://localhost:4200
# Swagger UI: http://localhost:8080/api/swagger-ui.html
# Actuator Health: http://localhost:8080/actuator/health
```

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