# A04 — Insecure Design

## Alcance de este documento

Cierra la observación abierta en `REPORT.md`: *"No incluye A04, A06, A08 ni
A10: no fueron objeto de fases anteriores"*. Documenta controles de diseño
**ya implementados** en el código del proyecto (fases anteriores + Unidad IV
de Jaime: Usuarios y Citas), citando archivo y línea reales. No se agregó
ningún control nuevo para esta tarea: donde se detectó un control sin
prueba automatizada dedicada, se documenta como pendiente en vez de
inventarse evidencia (ver "Limitaciones").

## 1. Separación en capas (Controller → Service → Repository)

Todo el backend sigue el mismo patrón, sin lógica de negocio en los
controladores ni acceso a datos fuera de los repositorios:

| Recurso | Controller | Service | Repository |
|---|---|---|---|
| Usuarios | `Backend/src/main/java/com/biopet/controller/UsuarioController.java` | `Backend/src/main/java/com/biopet/service/UsuarioService.java` | `Backend/src/main/java/com/biopet/repository/UsuarioRepository.java` |
| Citas | `Backend/src/main/java/com/biopet/controller/CitaController.java` | `Backend/src/main/java/com/biopet/service/CitaService.java` | `Backend/src/main/java/com/biopet/repository/CitaRepository.java` |
| Mascotas | `Backend/src/main/java/com/biopet/controller/MascotaController.java` | `Backend/src/main/java/com/biopet/service/MascotaService.java` | `Backend/src/main/java/com/biopet/repository/MascotaRepository.java` |

Los controladores de Usuarios y Citas no contienen ninguna sentencia `if`
de negocio: delegan directamente (`UsuarioController.java:33-58`,
`CitaController.java:25-49`) y su única responsabilidad es mapear
verbo HTTP + `@PreAuthorize` + `@Valid` al método de servicio correspondiente.

## 2. Autorización por rol, con granularidad distinta por operación

`@PreAuthorize` en el controlador, evaluado por Spring Security antes de
ejecutar cualquier lógica (mismo mecanismo ya documentado para Mascotas en
`A01-access-control.md`, no se repite aquí):

| Endpoint | Restricción | Fuente |
|---|---|---|
| `POST/PUT/DELETE /api/usuarios` | `hasRole('ADMIN')` — el CRUD administrativo de usuarios es exclusivo de ADMIN | `UsuarioController.java:39,46,53,59` |
| `POST /api/citas` | `hasAnyRole('ADMIN','AUXILIAR')` — VETERINARIO explícitamente excluido de creación | `CitaController.java:32` |
| `PUT /api/citas/{id}` | `hasAnyRole('ADMIN','AUXILIAR','VETERINARIO')` | `CitaController.java:39` |
| `DELETE /api/citas/{id}` | `hasRole('ADMIN')` — ni AUXILIAR ni VETERINARIO pueden eliminar citas | `CitaController.java:46` |

Este diseño aplica el principio de **mínimo privilegio por operación**, no
solo por recurso: un mismo rol (VETERINARIO) puede leer y actualizar citas
pero no crearlas ni eliminarlas.

## 3. Autorización por propiedad de datos (más allá del rol)

El rol por sí solo no basta cuando el dato pertenece a un usuario concreto.
Este control ya existe para Mascotas (`MascotaService.verificarPropiedad`,
documentado en A01) y se replicó igual en Citas:

```java
// CitaService.java:131-141
private void verificarAccesoLectura(Usuario usuario, Cita cita) {
    if (!tieneAccesoGlobal(usuario.getRol()) && !cita.getMascota().getDuenio().getId().equals(usuario.getId())) {
        throw new AccessDeniedException("No tiene permisos para acceder a esta cita.");
    }
}

private void verificarPermisoEscritura(Usuario usuario, Cita cita) {
    if (usuario.getRol() == Rol.ROLE_VETERINARIO && !cita.getVeterinario().getId().equals(usuario.getId())) {
        throw new AccessDeniedException("Solo puede modificar las citas asignadas a usted.");
    }
}
```

Dos reglas distintas conviven a propósito: un DUEÑO solo lee citas de sus
propias mascotas (`verificarAccesoLectura`); un VETERINARIO puede leer
cualquier cita pero solo puede modificar las que tiene asignadas
(`verificarPermisoEscritura`) — el rol autoriza la operación, la propiedad
del dato autoriza el registro concreto.

**Evidencia automatizada (ya existente, sin duplicar):**

| Prueba (`CitaControllerTest.java`) | Qué demuestra |
|---|---|
| `duenoConsultaCitaDeSuPropiaMascota` | Lectura permitida sobre dato propio |
| `duenoNoPuedeConsultarCitaDeMascotaAjena` | 403 sobre dato ajeno, aun autenticado |
| `duenoSoloVeSusPropiasCitasEnListado` | El filtro de propiedad aplica también al listado paginado, no solo al detalle |
| `veterinarioActualizaSuPropiaCitaDevuelve200` | Escritura permitida sobre cita asignada |
| `veterinarioNoPuedeActualizarCitaDeOtroVeterinarioDevuelve403` | 403 sobre cita de otro veterinario, mismo rol |

## 4. Prevención de escalación de privilegios (Usuario)

Requisito explícito de la Unidad IV: un usuario no debe poder ampliar sus
propios privilegios editándose a sí mismo.

```java
// UsuarioService.java:66-74
public UsuarioResponse actualizar(Long id, UsuarioRequest request, String emailAutenticado) {
    Usuario usuario = usuarioRepository.findByIdAndActivoTrue(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));

    Usuario autenticado = usuarioRepository.findByEmailAndActivoTrue(emailAutenticado)
            .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + emailAutenticado));
    if (autenticado.getId().equals(usuario.getId()) && request.rol() != usuario.getRol()) {
        throw new AccessDeniedException("No puede modificar su propio rol.");
    }
```

Nótese que el `@PreAuthorize` del controlador ya restringe todo el endpoint
a `ROLE_ADMIN` (sección 2); esta comprobación adicional cubre el caso en
que un ADMIN se edita **a sí mismo** y podría, por error o intención,
degradar su propio rol y perder acceso administrativo — defensa en
profundidad, no solo control de acceso de borde.

**Evidencia automatizada (ya existente):** `UsuarioControllerTest.adminNoPuedeEscalarSuPropioRolDevuelve403`
— verifica el 403 y, además, que el rol en base de datos **no cambió**
tras el intento.

## 5. Validez de la entidad asignada (Citas → Veterinario)

Una cita no puede quedar asociada a un usuario que no sea realmente un
veterinario activo, incluso si el ID existe:

```java
// CitaService.java:116-125
private Usuario resolverVeterinario(Long veterinarioId) {
    Usuario veterinario = usuarioRepository.findById(veterinarioId)
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new RecursoNoEncontradoException("Veterinario no encontrado: " + veterinarioId));
    if (veterinario.getRol() != Rol.ROLE_VETERINARIO) {
        throw new IllegalArgumentException(
                "El usuario asignado como veterinario debe tener rol ROLE_VETERINARIO: " + veterinarioId);
    }
    return veterinario;
}
```

Mismo patrón de diseño que `MascotaService.resolverDuenio` (verifica que el
`duenioId` recibido sea un usuario activo con `ROLE_DUENO`) — una regla de
integridad de dominio que la base de datos no puede expresar por sí sola
(la FK solo garantiza que el `id` existe en `usuarios`, no que tenga el rol
correcto), así que se aplica explícitamente en la capa de servicio.

**Evidencia automatizada (ya existente):**
`CitaControllerTest.crearCitaConUsuarioNoVeterinarioAsignadoDevuelve400`
(un AUXILIAR real asignado como veterinario → 400, no 500 ni aceptado
silenciosamente) y `crearCitaConVeterinarioInexistenteDevuelve404`.

## 6. Invariantes de servidor que ignoran el valor enviado por el cliente

Dos casos reales en el proyecto donde el servidor descarta deliberadamente
un campo del request en lugar de confiar en él, porque el cliente no debe
controlar una decisión de seguridad/negocio:

| Campo enviado | Dónde se ignora | Valor forzado |
|---|---|---|
| `RegistroRequest.rol()` | `AuthService.registrar()` (`AuthService.java:56-61`, construcción de `Usuario` sin leer `request.rol()`) | Siempre `Rol.ROLE_DUENO` (`Usuario.prePersist`, `Usuario.java:44-51`) |
| `CitaRequest.estado()` en `POST /api/citas` | `CitaService.crear()` (`CitaService.java:65-78`) | Siempre `EstadoCita.PROGRAMADA` |

El segundo caso se agregó deliberadamente en la Unidad IV siguiendo el
mismo patrón ya establecido por el primero (documentado en HU-001): el
campo existe en el DTO porque `PUT` sí lo necesita (para cancelar/completar
una cita existente), pero `POST` nunca debe permitir que un cliente cree
una cita ya "COMPLETADA" sin que ocurriera la atención.

**Evidencia automatizada (ya existente):**
`CitaControllerTest.crearCitaForzandoEstadoDistintoIgnoraElEstadoEnviado`
— envía `"estado":"COMPLETADA"` en el `POST` y verifica que la respuesta
persistida es `"PROGRAMADA"`.

## 7. Baja lógica exclusiva (nunca `DELETE` físico)

`Usuario`, `Mascota` y `Cita` comparten el mismo patrón: el campo `activo`
se apaga (`eliminar()` en cada service), nunca se ejecuta un `DELETE` SQL
sobre la fila, y toda consulta de lectura filtra explícitamente por
`activo = true` (`findAllByActivoTrue`, `findByIdAndActivoTrue` en los tres
repositorios). Esto preserva el historial (una cita cancelada/eliminada
sigue siendo trazable en la base de datos) y evita que un `id` reutilizado
apunte accidentalmente a un registro distinto.

## 8. Manejo centralizado y consistente de errores

Un único `@RestControllerAdvice` (`Backend/src/main/java/com/biopet/exception/GlobalExceptionHandler.java`)
traduce **todas** las excepciones de dominio a `ProblemDetail` (RFC 7807),
para los tres recursos por igual — Usuarios y Citas no definen su propio
manejo de errores, reutilizan exactamente el mismo:

| Excepción | Handler | Status |
|---|---|---|
| `RecursoNoEncontradoException` | `GlobalExceptionHandler.java:29-32` | 404 |
| `EmailDuplicadoException` | `GlobalExceptionHandler.java:24-27` | 409 |
| `IllegalArgumentException` | `GlobalExceptionHandler.java:61-64` | 400 |
| `MethodArgumentNotValidException` (Bean Validation) | `GlobalExceptionHandler.java:39-59` | 422, con detalle de campos en `errors` |
| `AccessDeniedException` (Spring Security, no capturada aquí) | `ProblemAccessDeniedHandler` | 403 |

Un diseño inseguro típico es que cada módulo nuevo invente su propio
formato de error (a veces filtrando detalles internos por accidente). Aquí
`UsuarioService` y `CitaService` lanzan las mismas excepciones de dominio
que `MascotaService` ya usaba, y el `ProblemType` (`ProblemType.java`) es un
enum cerrado — no hay forma de introducir un nuevo tipo de error sin
tocar ese archivo central, lo que evita inconsistencias.

## 9. Bean Validation como primera línea de defensa

Todos los DTO de entrada nuevos (`UsuarioRequest`, `CitaRequest`) usan
`jakarta.validation` igual que los ya existentes (`MascotaRequest`,
`RegistroRequest`): `@NotNull`, `@NotBlank`, `@Size`, `@Email`. Las
peticiones inválidas nunca llegan a la capa de servicio — Spring las
rechaza con 422 antes de ejecutar una sola línea de `UsuarioService`/`CitaService`.

## Limitaciones (gaps reales detectados durante esta revisión, no resueltos aquí)

- **`RegistroRequest.rol()` (sección 6, primer caso) no tiene una prueba
  automatizada dedicada** que verifique explícitamente que el rol
  persistido es `ROLE_DUENO` cuando el cliente envía `"rol":"ROLE_ADMIN"`
  en `POST /api/auth/registro`. El comportamiento existe y se verificó por
  inspección directa del código (`AuthService.java:56-61`), y
  `HistoriasUsuario.md` (HU-001) lo documenta como implementado, pero no se
  agregó la prueba en esta tarea porque requeriría tocar el módulo Auth
  (`AuthControllerTest.java` o un test nuevo bajo el mismo alcance),
  explícitamente fuera de este bloque. **Recomendación para el equipo:**
  agregar `AuthControllerTest.registroIgnoraRolEnviadoYPersisteRoleDueno()`
  en un bloque de trabajo que sí tenga permiso de tocar Auth.
- No se revisó A04 para los módulos de Consultas (Fred) ni Vacunas (Zaida)
  porque no existe código de esos módulos todavía en esta rama.
- Esta revisión es de diseño (inspección de código + pruebas existentes),
  no un pentest dinámico; no sustituye una revisión de amenazas formal
  (STRIDE/threat modeling) que no fue parte del alcance de esta tarea.

## Reproducción

```bash
cd Backend
mvn -Dtest=UsuarioControllerTest,CitaControllerTest test
```
