# A01 — Broken Access Control

## Control implementado

Autorización a dos niveles, ambos en código real (`Backend/src/main/java/com/biopet`):

1. **Autorización por rol** (`@PreAuthorize` en `MascotaController`):
   - `GET /api/mascotas`, `GET /api/mascotas/{id}`, `GET /api/mascotas/resumen-especies`:
     `hasAnyRole('ADMIN','VETERINARIO','AUXILIAR','DUENO')`.
   - `POST /api/mascotas`, `PUT /api/mascotas/{id}`, `DELETE /api/mascotas/{id}`:
     `hasAnyRole('ADMIN','VETERINARIO','AUXILIAR')` — `ROLE_DUENO` queda
     excluido de estas tres operaciones sin importar de quién sea la mascota.
2. **Autorización por propiedad** (`MascotaService.listar`/`buscar`/`verificarPropiedad`):
   - `tieneAccesoGlobal(rol)` es `true` para `ROLE_ADMIN`, `ROLE_VETERINARIO`
     y `ROLE_AUXILIAR` — estos roles ven y consultan cualquier mascota.
   - Para `ROLE_DUENO`, `listar()` filtra por `findAllByDuenioIdAndActivoTrue(usuario.getId(), ...)`
     y `buscar()`/`verificarPropiedad()` lanzan `AccessDeniedException` si
     `mascota.getDuenio().getId()` no coincide con el usuario autenticado.

## Afirmaciones verificadas y su fuente exacta

| Afirmación | Prueba (`Backend/src/test/java/com/biopet/MascotaControllerTest.java`) |
|---|---|
| `ROLE_DUENO` solo ve sus propias mascotas en el listado | `duenoSoloVeSusPropiasMascotasEnListado` |
| Un dueño no puede consultar una mascota ajena (403) | `duenoConsultaMascotaDeOtroDuenioDevuelve403` |
| Un dueño sí puede consultar su propia mascota por id | `duenoConsultaSuPropiaMascotaPorId` |
| `ROLE_ADMIN` conserva listado global | `adminConservaListadoGlobalDeMascotas` |
| `ROLE_ADMIN` consulta mascota de cualquier dueño | `adminConsultaMascotaDeCualquierDuenio` |
| `ROLE_VETERINARIO` consulta mascota de cualquier dueño | `veterinarioConsultaMascotaDeCualquierDuenio` |
| `ROLE_AUXILIAR` conserva acceso global al listado | `auxiliarConservaAccesoGlobalAlListado` |
| `ROLE_DUENO` no puede crear mascotas (403, aunque la petición sea válida) | `crearMascotaConRolInsuficienteDevuelveProblemDetail` |
| `ROLE_DUENO` no puede actualizar su propia mascota (403 por rol, no por propiedad) | `duenoIntentaActualizarMascotaPropiaSigueRecibiendo403PorRol` |
| `ROLE_DUENO` no puede eliminar su propia mascota (403 por rol) | `duenoIntentaEliminarMascotaPropiaSigueRecibiendo403PorRol` |
| Dos dueños con la misma paginación no comparten resultados de caché | `dosDuenosConMismaPaginaNoComparenResultadosDeCache` |

Acceso sin autenticación (401) y acceso autenticado sin permiso (403):

| Afirmación | Prueba |
|---|---|
| `GET /api/usuarios/me` sin token responde 401 ProblemDetail | `AuthControllerTest.accesoSinToken` |
| `GET /api/usuarios/me` con token inválido responde 401 | `AuthControllerTest.accesoConTokenInvalido` |
| `POST /api/mascotas` con rol insuficiente responde 403 ProblemDetail | `MascotaControllerTest.crearMascotaConRolInsuficienteDevuelveProblemDetail` |
| `GET /api/mascotas/{id}` de otro dueño responde 403 ProblemDetail | `MascotaControllerTest.duenoConsultaMascotaDeOtroDuenioDevuelve403` |

Formato real del `ProblemDetail` de 403 (verificado en el propio código de
prueba, no inventado): `type=urn:biopet:error:forbidden`,
`title="Acceso denegado"`, `status=403`, `instance=<ruta real>` — producido
por `ProblemAccessDeniedHandler` (`Backend/src/main/java/com/biopet/security/ProblemAccessDeniedHandler.java`),
no por el frontend.

## Por qué esto no depende del frontend

Toda la autorización descrita ocurre en el backend, antes de construir la
respuesta: `@PreAuthorize` se evalúa vía interceptor de método de Spring
Security, y la verificación de propiedad (`verificarPropiedad`) ocurre dentro
de `MascotaService`, en el mismo proceso que consulta la base de datos. Un
cliente que omita completamente el frontend Angular (por ejemplo, `curl`
directo contra la API) recibe exactamente los mismos 401/403 documentados
arriba — el control de acceso **no** se apoya en ocultar botones o rutas en
la interfaz. Esto se confirma porque las propias pruebas (`MascotaControllerTest`,
`AuthControllerTest`) llaman a la API vía `MockMvc`, sin ningún componente de
frontend involucrado.

## Reproducción

```bash
cd Backend
mvn -Dtest=MascotaControllerTest,AuthControllerTest test
```

## Limitaciones

- No se audita aquí el control de acceso a nivel de fila para entidades
  distintas de `Mascota`/`Usuario` porque el proyecto no tiene otras
  entidades con dueño en este alcance.
- No se verifica protección de acceso directo a objetos (IDOR) más allá de
  los casos ya cubiertos por `duenoConsultaMascotaDeOtroDuenioDevuelve403`;
  no se realizaron pruebas de enumeración masiva de IDs.
