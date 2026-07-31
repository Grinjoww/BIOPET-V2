# Historias de Usuario — BIOPET

**Tercera Entrega (v0.9.0-rc)** — Derivadas del SRS v0.9.0-rc.

Documento generado exclusivamente a partir de los requisitos funcionales del
SRS. No se han creado requisitos, roles ni funcionalidades adicionales a los
descritos en dicho documento. Formato Connextra con criterios de aceptación
en Gherkin, conforme al bloque A.3.2 de la Guía de la Tercera Entrega.

> **Origen de este archivo:** convertido a Markdown a partir de
> `HistoriasUsuario.pdf` (HU-001 a HU-019, contenido preservado sin
> modificaciones de fondo) y ampliado con **HU-020**, nueva de esta entrega.
> A partir de ahora este `.md` es la fuente editable; el PDF se regenera
> desde aquí cuando el equipo lo necesite.

---

## HU-001 — Registro de usuario con correo único

| Campo | Contenido |
|---|---|
| **Identificador** | HU-001 |
| **Requisitos SRS asociados** | REQ-F-001, REQ-F-002 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como visitante del sistema
> quiero registrarme con mi nombre, correo y contraseña
> para obtener una cuenta con rol de dueño de mascota para usar la plataforma.

**Descripción:** cubre el alta de una cuenta nueva (REQ-F-001), asignando
siempre el rol `ROLE_DUENO` por defecto e ignorando cualquier rol enviado por
el cliente, y el rechazo explícito cuando el correo ya está registrado
(REQ-F-002). Ambos requisitos comparten el mismo flujo de entrada
(`POST /api/auth/registro`) y se agrupan en una sola historia porque
representan dos resultados del mismo caso de uso de registro.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Registro exitoso
  Given un visitante no autenticado con datos de registro válidos
  When envía POST /api/auth/registro con nombre, correo y contraseña
  Then el sistema responde 201 Created
  And el usuario queda persistido con rol ROLE_DUENO, sin importar el rol enviado

Escenario: Correo ya registrado
  Given que el correo electrónico ya existe en el sistema
  When se envía POST /api/auth/registro con ese correo
  Then el sistema responde 409 con cuerpo ProblemDetails (RFC 7807)
```

**Dependencias:** ninguna.
**Observaciones:** verificado con `AuthControllerTest` (JUnit 5 + MockMvc) y
`GlobalExceptionHandler`.

---

## HU-002 — Inicio de sesión con emisión de tokens JWT

| Campo | Contenido |
|---|---|
| **Identificador** | HU-002 |
| **Requisitos SRS asociados** | REQ-F-003 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como usuario registrado
> quiero iniciar sesión con mi correo y contraseña
> para obtener un access token y un refresh token para acceder a los recursos protegidos.

**Descripción:** el sistema autentica al usuario y emite ambos tokens
firmados en formato JWT conforme a RFC 7519, con los siete claims estándar.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Login exitoso
  Given un usuario registrado con credenciales válidas
  When envía POST /api/auth/login con correo y contraseña
  Then el sistema responde 200
  And devuelve un access token (vigencia de una hora) y un refresh token
  And ambos tokens incluyen los claims iss, sub, aud, exp, nbf, iat y jti
```

**Dependencias:** ninguna.
**Observaciones:** verificado con `AuthControllerTest` y `JwtServiceTest`.

---

## HU-003 — Renovación de sesión sin reingreso de credenciales

| Campo | Contenido |
|---|---|
| **Identificador** | HU-003 |
| **Requisitos SRS asociados** | REQ-F-004 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como usuario autenticado
> quiero renovar mi access token usando mi refresh token
> para mantener mi sesión activa sin volver a ingresar mis credenciales.

**Descripción:** al recibir un refresh token válido y no revocado, el
sistema emite un nuevo access token.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Renovación exitosa
  Given un refresh token válido y no revocado
  When se envía POST /api/auth/refresh
  Then el sistema responde 200 con un nuevo access token
```

**Dependencias:** HU-002 (requiere haber iniciado sesión previamente).
**Observaciones:** verificado con `AuthControllerTest`.

---

## HU-004 — Cierre de sesión con revocación de token

| Campo | Contenido |
|---|---|
| **Identificador** | HU-004 |
| **Requisitos SRS asociados** | REQ-F-005 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como usuario autenticado
> quiero cerrar mi sesión
> para que mi token quede revocado de inmediato y no pueda usarse de nuevo.

**Descripción:** el sistema registra el `jti` del token en una lista negra
en Redis con TTL igual al tiempo restante de expiración (ADR-003), de modo
que el cierre de sesión sea efectivo antes de la expiración natural.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Logout efectivo
  Given un usuario autenticado con un token vigente
  When envía POST /api/auth/logout
  Then el sistema responde exitosamente y registra el jti en la lista negra de Redis
  And una solicitud posterior con el mismo token a un endpoint protegido responde 401
```

**Dependencias:** HU-002.
**Observaciones:** verificado con `AuthControllerTest` e inspección de
`TokenBlacklistService`.

---

## HU-005 — Restricción de acceso según rol autorizado

| Campo | Contenido |
|---|---|
| **Identificador** | HU-005 |
| **Requisitos SRS asociados** | REQ-F-006 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como administrador del sistema
> quiero que cada recurso protegido verifique el rol del usuario autenticado
> para evitar que usuarios sin privilegios ejecuten operaciones no autorizadas.

**Descripción:** materializa el control de acceso basado en roles (RBAC)
sobre los endpoints protegidos, usando Spring Security y anotaciones
`@PreAuthorize`. Es una historia transversal que condiciona el acceso a las
historias de gestión de mascotas (HU-007 a HU-011).

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Acceso denegado por rol no autorizado
  Given un usuario autenticado con rol ROLE_DUENO
  When intenta ejecutar POST /api/mascotas
  Then el sistema responde 403 Forbidden
```

**Dependencias:** HU-002.
**Observaciones:** verificado con pruebas de integración y auditoría OWASP
A01 (bloque C.2 de la Guía).

---

## HU-006 — Consulta del perfil propio

| Campo | Contenido |
|---|---|
| **Identificador** | HU-006 |
| **Requisitos SRS asociados** | REQ-F-007 |
| **Prioridad (MoSCoW)** | Should |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como usuario autenticado
> quiero consultar mis propios datos de perfil
> para que la interfaz muestre mi nombre y rol sin exponer el listado completo de usuarios.

**Descripción:** al recibir una solicitud autenticada a
`GET /api/usuarios/me`, el sistema devuelve los datos del perfil
correspondiente al token presentado.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Consulta exitosa del perfil
  Given un usuario autenticado con un token válido
  When envía GET /api/usuarios/me
  Then el sistema responde 200 con nombre, correo y rol del usuario autenticado
```

**Dependencias:** HU-002.
**Observaciones:** verificado por inspección de `UsuarioController` y prueba
manual con Postman (sin test automatizado formal registrado en el SRS).

---

## HU-007 — Registro de una nueva mascota

| Campo | Contenido |
|---|---|
| **Identificador** | HU-007 |
| **Requisitos SRS asociados** | REQ-F-008 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como administrador, veterinario o auxiliar
> quiero registrar una mascota con nombre, especie, raza y fecha de nacimiento
> para dejar constancia digital de la mascota asociada a su dueño.

**Descripción:** el sistema crea la mascota asociada al dueño indicado y
devuelve el registro creado, siempre que el usuario tenga uno de los roles
autorizados.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Registro exitoso de mascota
  Given un usuario autenticado con rol ADMIN, VETERINARIO o AUXILIAR
  When envía POST /api/mascotas con datos válidos
  Then el sistema responde 201 Created
  And la mascota queda vinculada al duenio_id indicado
```

**Dependencias:** HU-005 (control de acceso por rol).
**Observaciones:** verificado con `MascotaControllerTest`.

---

## HU-008 — Listado paginado de mascotas activas

| Campo | Contenido |
|---|---|
| **Identificador** | HU-008 |
| **Requisitos SRS asociados** | REQ-F-009 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como usuario autenticado de cualquier rol
> quiero consultar el listado paginado de mascotas activas
> para revisar de forma eficiente las mascotas registradas en el sistema.

**Descripción:** el sistema devuelve las mascotas activas de forma paginada,
accesible para los cuatro roles del sistema.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Listado paginado
  Given un usuario autenticado
  When envía GET /api/mascotas
  Then el sistema responde 200 con una página de resultados y metadatos de paginación
```

**Dependencias:** HU-005.
**Observaciones:** verificado con `MascotaControllerTest`. La caché Redis de
este endpoint (REQ-NF-001/002) está pendiente de evidencia empírica según el
SRS.

---

## HU-009 — Consulta de mascota por identificador

| Campo | Contenido |
|---|---|
| **Identificador** | HU-009 |
| **Requisitos SRS asociados** | REQ-F-010 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como usuario autenticado de cualquier rol
> quiero consultar el detalle completo de una mascota por su identificador
> para revisar o editar la información específica de esa mascota.

**Descripción:** complementa HU-008 para el caso de consulta puntual,
necesario antes de una edición o de mostrar el detalle en la interfaz.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Mascota existente
  Given un identificador de mascota existente
  When envía GET /api/mascotas/{id}
  Then el sistema responde 200 con los datos completos de la mascota

Escenario: Mascota inexistente
  Given un identificador de mascota inexistente
  When envía GET /api/mascotas/{id}
  Then el sistema responde 404 con cuerpo ProblemDetails
```

**Dependencias:** HU-005, HU-008.
**Observaciones:** verificado con `MascotaControllerTest` y
`RecursoNoEncontradoException`.

---

## HU-010 — Actualización de datos de una mascota

| Campo | Contenido |
|---|---|
| **Identificador** | HU-010 |
| **Requisitos SRS asociados** | REQ-F-011 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como administrador, veterinario o auxiliar
> quiero actualizar los datos de una mascota existente
> para mantener la información de la mascota al día.

**Descripción:** el sistema modifica los atributos indicados y registra la
fecha de actualización (`actualizado_en`).

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Actualización exitosa
  Given una mascota existente y un usuario con rol autorizado
  When envía PUT /api/mascotas/{id} con datos válidos
  Then el sistema responde 200 con los datos actualizados
  And el campo actualizado_en refleja la hora de la operación
```

**Dependencias:** HU-005, HU-009.
**Observaciones:** verificado con `MascotaControllerTest`.

---

## HU-011 — Baja lógica de una mascota

| Campo | Contenido |
|---|---|
| **Identificador** | HU-011 |
| **Requisitos SRS asociados** | REQ-F-012 |
| **Prioridad (MoSCoW)** | Must |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como administrador, veterinario o auxiliar
> quiero dar de baja lógicamente una mascota existente
> para retirarla del listado activo sin perder su historial ni la integridad referencial.

**Descripción:** el sistema marca el registro como inactivo (`activo =
false`) sin eliminarlo físicamente de la base de datos.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: Baja lógica exitosa
  Given una mascota existente y un usuario con rol autorizado
  When envía DELETE /api/mascotas/{id}
  Then el sistema responde 204 No Content
  And el registro pasa a activo = false
  And ya no aparece en el listado por defecto
```

**Dependencias:** HU-005, HU-009.
**Observaciones:** verificado con `MascotaControllerTest`.

---

## HU-012 — Registro de atención médica e historial clínico

| Campo | Contenido |
|---|---|
| **Identificador** | HU-012 |
| **Requisitos SRS asociados** | REQ-F-013 |
| **Prioridad (MoSCoW)** | Should |
| **Estado** | Pendiente |

**Historia (Connextra)**
> Como veterinario
> quiero registrar diagnóstico, tratamiento y observaciones de una atención médica
> para mantener un historial clínico cronológico de cada mascota.

**Descripción:** actualiza los requisitos heredados RF-03 y RF-04 de la
Entrega 1A. El modelo de datos (tabla `Historial_Clinico`) ya está diseñado,
pero no existe código de este módulo.

**Criterios de aceptación (Gherkin)** — *comportamiento esperado, no
implementado*
```gherkin
Escenario: Consulta de historial ordenado
  Given una mascota con atenciones médicas registradas
  When se consulta su historial clínico
  Then las atenciones se muestran ordenadas por fecha, cada una con su diagnóstico y tratamiento
```

**Dependencias:** HU-007 (requiere que la mascota exista).
**Observaciones:** sin código; se verificará por test de integración cuando
el módulo exista.

---

## HU-013 — Prescripción de medicamentos asociada a una atención

| Campo | Contenido |
|---|---|
| **Identificador** | HU-013 |
| **Requisitos SRS asociados** | REQ-F-014 |
| **Prioridad (MoSCoW)** | Could |
| **Estado** | Pendiente |

**Historia (Connextra)**
> Como veterinario
> quiero asociar uno o más medicamentos prescritos a una atención médica
> para dejar constancia del tratamiento farmacológico indicado.

**Descripción:** actualiza el requisito heredado RF-05 de la Entrega 1A.

**Criterios de aceptación (Gherkin)** — *comportamiento esperado, no
implementado*
```gherkin
Escenario: Prescripción vinculada
  Given una atención médica registrada
  When el veterinario ingresa uno o más medicamentos
  Then los medicamentos quedan vinculados a dicha atención
  And son visibles en el historial de la mascota
```

**Dependencias:** HU-012.
**Observaciones:** sin código.

---

## HU-014 — Gestión de citas veterinarias mediante calendario

| Campo | Contenido |
|---|---|
| **Identificador** | HU-014 |
| **Requisitos SRS asociados** | REQ-F-015 |
| **Prioridad (MoSCoW)** | Should |
| **Estado** | Pendiente |

**Historia (Connextra)**
> Como auxiliar o veterinario
> quiero registrar, modificar y consultar citas veterinarias en un calendario interactivo
> para organizar la agenda de atenciones de cada mascota y veterinario.

**Descripción:** actualiza el requisito heredado RF-06 de la Entrega 1A; el
modelo de datos (tabla `Cita`) ya está diseñado.

**Criterios de aceptación (Gherkin)** — *comportamiento esperado, no
implementado*
```gherkin
Escenario: Cita creada y visible
  Given una mascota y un veterinario existentes
  When se registra una cita
  Then la cita aparece en el calendario
  And puede modificarse o cancelarse posteriormente
```

**Dependencias:** HU-007.
**Observaciones:** sin código.

---

## HU-015 — Recepción de telemetría de dispositivos IoT

| Campo | Contenido |
|---|---|
| **Identificador** | HU-015 |
| **Requisitos SRS asociados** | REQ-F-016 |
| **Prioridad (MoSCoW)** | Could |
| **Estado** | Pendiente |

**Historia (Connextra)**
> Como sistema externo de rastreo (dispositivo IoT)
> quiero enviar coordenadas de ubicación de una mascota al backend
> para permitir el seguimiento preventivo de la mascota en un mapa.

**Descripción:** actualiza el requisito heredado RF-08/RF-09 de la Entrega
1A; el modelo de datos (tabla `Dispositivo_IoT`) ya está diseñado.

**Criterios de aceptación (Gherkin)** — *comportamiento esperado, no
implementado*
```gherkin
Escenario: Recepción y almacenamiento de coordenadas
  Given un dispositivo IoT asociado a una mascota
  When envía coordenadas válidas a la API
  Then el sistema las almacena junto con la fecha y hora de actualización
  And la ubicación es visualizable en un mapa
```

**Dependencias:** HU-007.
**Observaciones:** sin código.

---

## HU-016 — Recomendaciones clínicas asistidas por IA

| Campo | Contenido |
|---|---|
| **Identificador** | HU-016 |
| **Requisitos SRS asociados** | REQ-F-017 |
| **Prioridad (MoSCoW)** | Could |
| **Estado** | Pendiente |

**Historia (Connextra)**
> Como veterinario
> quiero visualizar recomendaciones clínicas generadas automáticamente
> para apoyar la toma de decisiones clínicas con base en el historial médico.

**Descripción:** actualiza el requisito heredado RF-10 de la Entrega 1A.
Depende funcionalmente de que exista historial clínico (REQ-F-013 / HU-012).

**Criterios de aceptación (Gherkin)** — *comportamiento esperado, no
implementado*
```gherkin
Escenario: Recomendación visible
  Given una mascota con historial clínico registrado
  When el veterinario solicita una recomendación
  Then el sistema muestra una recomendación generada automáticamente por el servicio de IA
```

**Dependencias:** HU-012 (requiere historial clínico).
**Observaciones:** sin código; depende de REQ-F-013.

---

## HU-017 — Facturación digital y registro de pagos

| Campo | Contenido |
|---|---|
| **Identificador** | HU-017 |
| **Requisitos SRS asociados** | REQ-F-018 |
| **Prioridad (MoSCoW)** | Should |
| **Estado** | Pendiente |

**Historia (Connextra)**
> Como administrador o auxiliar
> quiero generar un comprobante de pago digital en PDF y registrar el método de pago
> para formalizar el cobro de un servicio veterinario.

**Descripción:** actualiza los requisitos heredados RF-11 y RF-12 de la
Entrega 1A; el modelo de datos (`Factura`, `Detalle_Factura`,
`Producto_Servicio`) ya está diseñado, incluyendo campos de integración con
el SRI ecuatoriano.

**Criterios de aceptación (Gherkin)** — *comportamiento esperado, no
implementado*
```gherkin
Escenario: Descarga de comprobante
  Given un servicio veterinario registrado
  When el usuario solicita el comprobante
  Then puede descargar un PDF válido
  And la forma de pago queda almacenada junto con el servicio
```

**Dependencias:** HU-007.
**Observaciones:** sin código.

---

## HU-018 — Generación de reportes exportables

| Campo | Contenido |
|---|---|
| **Identificador** | HU-018 |
| **Requisitos SRS asociados** | REQ-F-019 |
| **Prioridad (MoSCoW)** | Could |
| **Estado** | Pendiente |

**Historia (Connextra)**
> Como administrador
> quiero generar reportes estadísticos exportables en PDF y Excel
> para analizar la operación de la clínica fuera del sistema.

**Descripción:** actualiza el requisito heredado RF-14 de la Entrega 1A.

**Criterios de aceptación (Gherkin)** — *comportamiento esperado, no
implementado*
```gherkin
Escenario: Descarga de reportes
  Given datos suficientes en el sistema
  When el administrador solicita un reporte
  Then puede descargar correctamente el reporte en PDF y en Excel
```

**Dependencias:** ninguna directa; se beneficia de HU-007 a HU-011.
**Observaciones:** sin código.

---

## HU-019 — Auditoría de operaciones del sistema

| Campo | Contenido |
|---|---|
| **Identificador** | HU-019 |
| **Requisitos SRS asociados** | REQ-F-020 |
| **Prioridad (MoSCoW)** | Should |
| **Estado** | Parcial |

**Historia (Connextra)**
> Como administrador
> quiero que cada operación relevante quede registrada con usuario, fecha y hora
> para poder auditar la actividad del sistema.

**Descripción:** actualiza el requisito heredado RF-15 de la Entrega 1A; se
relaciona con REQ-NF-009 (logging de autenticación), ya cubierto
parcialmente.

**Criterios de aceptación (Gherkin)** — *parcialmente implementado*
```gherkin
Escenario: Registro de operación auditable
  Given una operación de creación, modificación o eliminación
  When esta se ejecuta exitosamente
  Then queda registrada con identificador de usuario, fecha y hora
```

**Dependencias:** HU-002 (requiere usuario autenticado).
**Observaciones:** el logging de autenticación (login exitoso/fallido) ya
está parcialmente cubierto (REQ-NF-009); la auditoría genérica de
operaciones CRUD está pendiente.

---

## HU-020 — Resumen de mascotas por especie *(nueva, Tercera Entrega)*

| Campo | Contenido |
|---|---|
| **Identificador** | HU-020 |
| **Requisitos SRS asociados** | REQ-F-021 |
| **Prioridad (MoSCoW)** | Should |
| **Estado** | Implementada |

**Historia (Connextra)**
> Como usuario con rol ADMIN, VETERINARIO o AUXILIAR
> quiero ver un resumen de cuántas mascotas activas hay registradas por especie, opcionalmente filtrado por un dueño específico
> para dimensionar rápidamente la carga de trabajo por tipo de animal sin recorrer manualmente el listado paginado.

> Como usuario con rol DUENO
> quiero ver el resumen de mis propias mascotas agrupadas por especie
> para tener una vista rápida de cuántos animales de cada tipo tengo registrados, sin poder ver el resumen de otros dueños.

**Descripción:** nuevo requisito de la Tercera Entrega (REQ-F-021), exigido
por el bloque A.2.2 de la Guía como ejemplo de operación agregada (`GROUP
BY`) que debe implementarse obligatoriamente vía función/procedimiento
almacenado, no vía JPQL. Solo el rol `ADMIN` puede usar el parámetro
`duenioId` para filtrar por otro dueño o consultar el total global; para
cualquier otro rol, el backend fuerza siempre el filtro al id del propio
usuario autenticado.

**Criterios de aceptación (Gherkin)**
```gherkin
Escenario: ADMIN consulta el resumen global
  Given que inicio sesión con un usuario de rol ADMIN
  And no envío el parámetro duenioId
  When solicito GET /api/mascotas/resumen-especies
  Then recibo 200 OK
  And la respuesta contiene una lista de objetos {especie, total}
  And el total de cada especie corresponde a todas las mascotas activas de esa especie, sin filtrar por dueño

Escenario: ADMIN consulta el resumen de un dueño específico
  Given que inicio sesión con un usuario de rol ADMIN
  And envío duenioId=42
  When solicito GET /api/mascotas/resumen-especies?duenioId=42
  Then recibo 200 OK
  And la respuesta solo contiene el resumen de las mascotas activas del dueño con id 42

Escenario: DUENO consulta su propio resumen
  Given que inicio sesión con un usuario de rol DUENO cuyo id es 7
  When solicito GET /api/mascotas/resumen-especies
  Then recibo 200 OK
  And la respuesta solo contiene el resumen de mis propias mascotas activas
  And cualquier valor de duenioId que yo envíe es ignorado por el backend

Escenario: No hay mascotas activas para el filtro solicitado
  Given que el dueño consultado no tiene mascotas activas registradas
  When solicito el resumen por especie
  Then recibo 200 OK
  And la respuesta es una lista vacía

Escenario: Usuario no autenticado
  Given que no envío una cookie de sesión válida
  When solicito GET /api/mascotas/resumen-especies
  Then recibo 401 Unauthorized con un cuerpo ProblemDetails
```

**Dependencias:** HU-005 (control de acceso por rol), HU-007 (requiere que
existan mascotas registradas).
**Observaciones:** verificado con `ResumenEspeciesIntegrationTest`
(Testcontainers, PostgreSQL real desechable). Tipo de acceso a datos: SP
(función PL/pgSQL `fn_resumen_mascotas_por_especie`), no CRUD elemental —
ver bloque A.2.2 de la Guía.
