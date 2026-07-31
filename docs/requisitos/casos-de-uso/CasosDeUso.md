# Casos de Uso — BIOPET

**Tercera Entrega (v0.9.0-rc)** — Formato Cockburn, derivados de las
Historias de Usuario.

Cada caso de uso representa exactamente las funcionalidades descritas en el
SRS y en las Historias de Usuario (`HistoriasUsuario.md`). Los pasos de los
módulos pendientes describen el comportamiento esperado según el SRS, no
comportamiento implementado.

> **Origen de este archivo:** convertido a Markdown a partir de
> `CasosDeUso.pdf` (CU-01 a CU-19, contenido preservado sin modificaciones de
> fondo) y ampliado con **CU-20**, nuevo de esta entrega. A partir de ahora
> este `.md` es la fuente editable; el PDF se regenera desde aquí cuando el
> equipo lo necesite.

---

## CU-01 — Registrar usuario

| Campo | Contenido |
|---|---|
| **Objetivo** | Crear una cuenta de usuario nueva con rol de dueño |
| **Actor principal** | Visitante (no autenticado) |
| **Actores secundarios** | — |
| **Disparador** | El visitante envía `POST /api/auth/registro`. |
| **Precondiciones** | El visitante no posee sesión activa. |
| **Postcondiciones** | Éxito: el usuario queda persistido con rol `ROLE_DUENO`. Fallo: no se crea ningún registro. |
| **Requisitos relacionados** | REQ-F-001, REQ-F-002 |
| **Historias relacionadas** | HU-001 |
| **Estado** | Implementado y verificado |

**Descripción:** el visitante proporciona nombre, correo y contraseña para
crear una cuenta. El sistema fuerza el rol `ROLE_DUENO` y valida la
unicidad del correo.

**Flujo principal**
1. El visitante envía nombre, correo y contraseña.
2. El sistema valida el formato de los datos.
3. El sistema verifica que el correo no exista previamente.
4. El sistema crea el usuario con rol `ROLE_DUENO`, ignorando cualquier rol enviado.
5. El sistema responde `201 Created` con los datos del usuario creado.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 3a. El correo ya existe: el sistema responde `409 Conflict` con cuerpo
  `ProblemDetails` (RFC 7807) y no crea el registro.

**Reglas de negocio:** el rol enviado por el cliente en la solicitud se
ignora siempre; el rol asignado es `ROLE_DUENO` (previene escalada de
privilegios).

---

## CU-02 — Iniciar sesión

| Campo | Contenido |
|---|---|
| **Objetivo** | Autenticar al usuario y emitir tokens JWT |
| **Actor principal** | Usuario registrado |
| **Actores secundarios** | — |
| **Disparador** | El usuario envía `POST /api/auth/login`. |
| **Precondiciones** | El usuario posee una cuenta previamente registrada (CU-01). |
| **Postcondiciones** | Éxito: el usuario recibe access token y refresh token válidos. Fallo: no se emite ningún token. |
| **Requisitos relacionados** | REQ-F-003 |
| **Historias relacionadas** | HU-002 |
| **Estado** | Implementado y verificado |

**Descripción:** el usuario presenta sus credenciales; el sistema las
valida y emite un access token y un refresh token firmados conforme a RFC
7519.

**Flujo principal**
1. El usuario envía correo y contraseña.
2. El sistema valida las credenciales.
3. El sistema genera un access token (vigencia de una hora) y un refresh
   token, ambos con los siete claims estándar (`iss`, `sub`, `aud`, `exp`,
   `nbf`, `iat`, `jti`).
4. El sistema responde `200 OK` con ambos tokens.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 2a. Credenciales inválidas: el sistema rechaza la autenticación
  (comportamiento de error no detallado con código específico en el SRS).

**Reglas de negocio:** los tokens se firman digitalmente y deben incluir
los siete claims estándar de JWT.

---

## CU-03 — Renovar token de acceso

| Campo | Contenido |
|---|---|
| **Objetivo** | Emitir un nuevo access token sin reingresar credenciales |
| **Actor principal** | Usuario autenticado |
| **Actores secundarios** | — |
| **Disparador** | El usuario envía `POST /api/auth/refresh`. |
| **Precondiciones** | El usuario posee un refresh token válido y no revocado (obtenido en CU-02). |
| **Postcondiciones** | Éxito: se emite un nuevo access token. Fallo: no se emite ningún token nuevo. |
| **Requisitos relacionados** | REQ-F-004 |
| **Historias relacionadas** | HU-003 |
| **Estado** | Implementado y verificado |

**Descripción:** el usuario presenta su refresh token vigente y no
revocado; el sistema emite un nuevo access token.

**Flujo principal**
1. El usuario envía su refresh token.
2. El sistema valida que el token sea válido y no esté en la lista negra.
3. El sistema emite un nuevo access token.
4. El sistema responde `200 OK` con el nuevo access token.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 2a. Refresh token inválido o revocado: la renovación no procede (código
  de error no detallado en el SRS).

**Reglas de negocio:** no se exige el reingreso de credenciales del usuario
para renovar el access token.

---

## CU-04 — Cerrar sesión con revocación de token

| Campo | Contenido |
|---|---|
| **Objetivo** | Invalidar el token vigente antes de su expiración natural |
| **Actor principal** | Usuario autenticado |
| **Actores secundarios** | Redis (lista negra de JTI) |
| **Disparador** | El usuario envía `POST /api/auth/logout`. |
| **Precondiciones** | El usuario posee un token vigente (obtenido en CU-02). |
| **Postcondiciones** | Éxito: el `jti` del token queda en la lista negra de Redis. Cualquier solicitud posterior con ese token a un recurso protegido responde 401. |
| **Requisitos relacionados** | REQ-F-005 |
| **Historias relacionadas** | HU-004 |
| **Estado** | Implementado y verificado |

**Descripción:** el sistema registra el identificador `jti` del token en
una lista negra en Redis con un TTL igual al tiempo restante hasta su
expiración, conforme a ADR-003.

**Flujo principal**
1. El usuario envía la solicitud de cierre de sesión con su token.
2. El sistema extrae el `jti` y el tiempo restante de expiración del token.
3. El sistema registra el `jti` en la lista negra de Redis con TTL igual
   al tiempo restante.
4. El sistema confirma el cierre de sesión.

**Flujos alternativos:** ninguno identificado en el SRS.
**Flujos de excepción:** ninguno identificado en el SRS.

**Reglas de negocio:** la revocación debe ser efectiva de inmediato, sin
esperar la expiración natural del JWT (naturaleza stateless de JWT,
ADR-003).

---

## CU-05 — Verificar autorización de acceso por rol

| Campo | Contenido |
|---|---|
| **Objetivo** | Garantizar que solo los roles autorizados accedan a un recurso protegido |
| **Actor principal** | Sistema (Spring Security) |
| **Actores secundarios** | Usuario autenticado (cualquier rol) |
| **Disparador** | Cualquier solicitud HTTP a un recurso protegido. |
| **Precondiciones** | El usuario presenta un token JWT válido y no revocado. |
| **Postcondiciones** | Éxito: la solicitud continúa hacia el caso de uso solicitado. Fallo: la solicitud se rechaza con 403 Forbidden. |
| **Requisitos relacionados** | REQ-F-006 |
| **Historias relacionadas** | HU-005 |
| **Estado** | Implementado y verificado |

**Descripción:** caso de uso transversal: antes de ejecutar la operación
solicitada sobre un recurso protegido, el sistema verifica el rol del
usuario autenticado contra los roles autorizados para ese recurso. Es
incluido implícitamente por los casos de uso de gestión de mascotas (CU-06
a CU-10).

**Flujo principal**
1. El sistema intercepta la solicitud sobre el recurso protegido.
2. El sistema obtiene el rol del usuario autenticado a partir del token.
3. El sistema compara el rol contra los roles autorizados para el recurso
   (anotación `@PreAuthorize`).
4. Si el rol está autorizado, el sistema permite continuar hacia el caso
   de uso solicitado.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 3a. El rol no está autorizado: el sistema responde `403 Forbidden` y no
  ejecuta la operación solicitada.

**Reglas de negocio:** los cuatro roles del sistema son `ROLE_ADMIN`,
`ROLE_VETERINARIO`, `ROLE_AUXILIAR` y `ROLE_DUENO`.

---

## CU-06 — Consultar perfil propio

| Campo | Contenido |
|---|---|
| **Objetivo** | Obtener los datos de perfil del usuario autenticado |
| **Actor principal** | Usuario autenticado |
| **Actores secundarios** | — |
| **Disparador** | El usuario envía `GET /api/usuarios/me`. |
| **Precondiciones** | El usuario posee un token válido (CU-02). |
| **Postcondiciones** | Éxito: se devuelven los datos del perfil del usuario autenticado. |
| **Requisitos relacionados** | REQ-F-007 |
| **Historias relacionadas** | HU-006 |
| **Estado** | Implementado (verificación por inspección y prueba manual) |

**Descripción:** el sistema devuelve nombre, correo y rol del usuario
correspondiente al token presentado, sin exponer el listado completo de
usuarios.

**Flujo principal**
1. El usuario envía la solicitud con su token.
2. El sistema identifica al usuario a partir del token.
3. El sistema responde `200 OK` con nombre, correo y rol del usuario.

**Flujos alternativos:** ninguno identificado en el SRS.
**Flujos de excepción:** ninguno identificado en el SRS.

**Reglas de negocio:** el endpoint solo expone el perfil del propio usuario
autenticado, no el de terceros.

---

## CU-07 — Registrar mascota

| Campo | Contenido |
|---|---|
| **Objetivo** | Crear un registro de mascota asociado a un dueño |
| **Actor principal** | Administrador, Veterinario o Auxiliar |
| **Actores secundarios** | — |
| **Disparador** | El actor envía `POST /api/mascotas`. |
| **Precondiciones** | El actor está autenticado y su rol está autorizado (CU-05). El dueño (`duenio_id`) existe en el sistema. |
| **Postcondiciones** | Éxito: la mascota queda creada y vinculada al dueño indicado. |
| **Requisitos relacionados** | REQ-F-008 |
| **Historias relacionadas** | HU-007 |
| **Estado** | Implementado y verificado |

**Descripción:** el actor registra una mascota con nombre, especie, raza y
fecha de nacimiento, asociándola a un dueño existente.

**Flujo principal**
1. El actor envía nombre, especie, raza y fecha de nacimiento.
2. El sistema verifica el rol del actor (incluye CU-05).
3. El sistema valida los datos recibidos.
4. El sistema crea la mascota vinculada al `duenio_id` indicado.
5. El sistema responde `201 Created` con el registro creado.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 2a. Rol no autorizado: ver CU-05 (403 Forbidden).

**Reglas de negocio:** solo los roles `ADMIN`, `VETERINARIO` o `AUXILIAR`
pueden registrar mascotas.

---

## CU-08 — Listar mascotas activas

| Campo | Contenido |
|---|---|
| **Objetivo** | Obtener el listado paginado de mascotas activas |
| **Actor principal** | Usuario autenticado (cualquier rol) |
| **Actores secundarios** | — |
| **Disparador** | El actor envía `GET /api/mascotas`. |
| **Precondiciones** | El actor está autenticado (CU-05, sin restricción adicional de rol). |
| **Postcondiciones** | Éxito: se devuelve una página de mascotas activas con metadatos de paginación. |
| **Requisitos relacionados** | REQ-F-009 |
| **Historias relacionadas** | HU-008 |
| **Estado** | Implementado y verificado |

**Descripción:** el sistema devuelve las mascotas activas de forma
paginada, accesible para los cuatro roles del sistema.

**Flujo principal**
1. El actor solicita el listado, opcionalmente con parámetros de paginación.
2. El sistema consulta las mascotas activas.
3. El sistema responde `200 OK` con la página de resultados y sus
   metadatos.

**Flujos alternativos:** el resultado puede servirse desde caché Redis
cuando corresponda (comportamiento de caché descrito en REQ-NF-001/002,
pendiente de evidencia empírica).
**Flujos de excepción:** ninguno identificado en el SRS.

**Reglas de negocio:** solo se listan mascotas con `activo = true`.

---

## CU-09 — Consultar mascota por identificador

| Campo | Contenido |
|---|---|
| **Objetivo** | Obtener el detalle completo de una mascota específica |
| **Actor principal** | Usuario autenticado (cualquier rol) |
| **Actores secundarios** | — |
| **Disparador** | El actor envía `GET /api/mascotas/{id}`. |
| **Precondiciones** | El actor está autenticado. |
| **Postcondiciones** | Éxito: se devuelven los datos completos de la mascota solicitada. |
| **Requisitos relacionados** | REQ-F-010 |
| **Historias relacionadas** | HU-009 |
| **Estado** | Implementado y verificado |

**Descripción:** el sistema devuelve los datos completos de una mascota a
partir de su identificador.

**Flujo principal**
1. El actor envía el identificador de la mascota.
2. El sistema busca la mascota por su clave primaria.
3. El sistema responde `200 OK` con los datos completos de la mascota.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 2a. La mascota no existe: el sistema responde `404 Not Found` con cuerpo
  `ProblemDetails`.

**Reglas de negocio:** ninguna adicional a la existencia del registro.

---

## CU-10 — Actualizar mascota

| Campo | Contenido |
|---|---|
| **Objetivo** | Modificar los atributos de una mascota existente |
| **Actor principal** | Administrador, Veterinario o Auxiliar |
| **Actores secundarios** | — |
| **Disparador** | El actor envía `PUT /api/mascotas/{id}`. |
| **Precondiciones** | La mascota existe (CU-09). El rol del actor está autorizado (CU-05). |
| **Postcondiciones** | Éxito: los atributos quedan actualizados y `actualizado_en` refleja la hora de la operación. |
| **Requisitos relacionados** | REQ-F-011 |
| **Historias relacionadas** | HU-010 |
| **Estado** | Implementado y verificado |

**Descripción:** el sistema modifica los atributos indicados de una
mascota existente y registra la fecha de actualización.

**Flujo principal**
1. El actor envía los datos a actualizar.
2. El sistema verifica el rol del actor (incluye CU-05).
3. El sistema valida los datos recibidos.
4. El sistema actualiza los atributos indicados y el campo
   `actualizado_en`.
5. El sistema responde `200 OK` con los datos actualizados.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 2a. Rol no autorizado: ver CU-05.
- 1a. La mascota no existe: ver CU-09 (404 con `ProblemDetails`).

**Reglas de negocio:** solo los roles `ADMIN`, `VETERINARIO` o `AUXILIAR`
pueden actualizar mascotas.

---

## CU-11 — Dar de baja lógica a una mascota

| Campo | Contenido |
|---|---|
| **Objetivo** | Marcar una mascota existente como inactiva |
| **Actor principal** | Administrador, Veterinario o Auxiliar |
| **Actores secundarios** | — |
| **Disparador** | El actor envía `DELETE /api/mascotas/{id}`. |
| **Precondiciones** | La mascota existe (CU-09). El rol del actor está autorizado (CU-05). |
| **Postcondiciones** | Éxito: el registro pasa a `activo = false` y deja de aparecer en el listado por defecto (CU-08). |
| **Requisitos relacionados** | REQ-F-012 |
| **Historias relacionadas** | HU-011 |
| **Estado** | Implementado y verificado |

**Descripción:** el sistema marca el registro como inactivo (`activo =
false`) sin eliminarlo físicamente, preservando el historial e integridad
referencial de futuros módulos.

**Flujo principal**
1. El actor solicita la baja de la mascota.
2. El sistema verifica el rol del actor (incluye CU-05).
3. El sistema marca la mascota como inactiva.
4. El sistema responde `204 No Content`.

**Flujos alternativos:** ninguno identificado en el SRS.

**Flujos de excepción**
- 2a. Rol no autorizado: ver CU-05.
- 1a. La mascota no existe: ver CU-09.

**Reglas de negocio:** la eliminación es siempre lógica; el registro nunca
se elimina físicamente de la base de datos.

---

## CU-12 — Registrar atención médica e historial clínico

| Campo | Contenido |
|---|---|
| **Objetivo** | Almacenar una atención médica en el historial de la mascota |
| **Actor principal** | Veterinario |
| **Actores secundarios** | — |
| **Disparador** | El veterinario registra una atención médica (interfaz no implementada). |
| **Precondiciones** | La mascota existe (CU-07). |
| **Postcondiciones** | Éxito esperado: la atención queda asociada cronológicamente al historial clínico de la mascota. |
| **Requisitos relacionados** | REQ-F-013 |
| **Historias relacionadas** | HU-012 |
| **Estado** | Pendiente — diseño de datos disponible, sin código |

**Descripción:** el sistema almacena diagnóstico, tratamiento y
observaciones de una atención médica, asociándolos cronológicamente al
historial clínico de la mascota. Módulo pendiente de implementación; el
modelo de datos (tabla `Historial_Clinico`) ya está diseñado desde la
Entrega 1A.

**Flujo principal**
1. El veterinario ingresa diagnóstico, tratamiento y observaciones.
2. El sistema asocia la atención a la mascota y a la fecha de registro.
3. El sistema almacena la atención en el historial clínico.

**Flujos alternativos:** no especificados en el SRS (módulo pendiente).
**Flujos de excepción:** no especificados en el SRS (módulo pendiente).

**Reglas de negocio:** las atenciones se muestran ordenadas
cronológicamente al consultar el historial.

---

## CU-13 — Prescribir medicamentos

| Campo | Contenido |
|---|---|
| **Objetivo** | Asociar medicamentos a una atención médica |
| **Actor principal** | Veterinario |
| **Actores secundarios** | — |
| **Disparador** | El veterinario registra la prescripción al registrar una atención (interfaz no implementada). |
| **Precondiciones** | Existe una atención médica registrada (CU-12). |
| **Postcondiciones** | Éxito esperado: los medicamentos quedan vinculados a la atención y visibles en el historial. |
| **Requisitos relacionados** | REQ-F-014 |
| **Historias relacionadas** | HU-013 |
| **Estado** | Pendiente |

**Descripción:** el sistema permite asociar uno o más medicamentos
prescritos a una atención médica registrada. Módulo pendiente de
implementación.

**Flujo principal**
1. El veterinario ingresa uno o más medicamentos para la atención.
2. El sistema vincula los medicamentos a la atención.
3. Los medicamentos quedan visibles en el historial de la mascota.

**Flujos alternativos:** no especificados en el SRS (módulo pendiente).
**Flujos de excepción:** no especificados en el SRS (módulo pendiente).

**Reglas de negocio:** una atención puede tener uno o más medicamentos
asociados.

---

## CU-14 — Gestionar citas veterinarias

| Campo | Contenido |
|---|---|
| **Objetivo** | Registrar, modificar y consultar citas mediante calendario |
| **Actor principal** | Auxiliar o Veterinario |
| **Actores secundarios** | Dueño de mascota (consulta) |
| **Disparador** | El actor registra, modifica o consulta una cita (interfaz no implementada). |
| **Precondiciones** | La mascota existe (CU-07). |
| **Postcondiciones** | Éxito esperado: la cita queda visible en el calendario y puede modificarse o cancelarse. |
| **Requisitos relacionados** | REQ-F-015 |
| **Historias relacionadas** | HU-014 |
| **Estado** | Pendiente — diseño de datos disponible, sin código |

**Descripción:** el sistema permite registrar, modificar y consultar citas
veterinarias mediante un calendario interactivo, asociando cada cita a una
mascota y a un veterinario. Módulo pendiente de implementación; el modelo
de datos (tabla `Cita`) ya está diseñado.

**Flujo principal**
1. El actor registra una cita indicando mascota, veterinario, fecha y hora.
2. El sistema almacena la cita.
3. La cita aparece en el calendario interactivo.

**Flujos alternativos:** modificación de una cita existente y cancelación
de una cita existente (no detalladas en el SRS más allá de su mención).
**Flujos de excepción:** no especificados en el SRS (módulo pendiente).

**Reglas de negocio:** cada cita se asocia a exactamente una mascota y a un
veterinario.

---

## CU-15 — Recibir telemetría de dispositivos IoT

| Campo | Contenido |
|---|---|
| **Objetivo** | Almacenar coordenadas de ubicación enviadas por un dispositivo de rastreo |
| **Actor principal** | Dispositivo de rastreo IoT |
| **Actores secundarios** | Dueño de mascota (visualización en mapa) |
| **Disparador** | El dispositivo IoT envía coordenadas a la API (endpoint no implementado). |
| **Precondiciones** | El dispositivo está asociado a una mascota existente. |
| **Postcondiciones** | Éxito esperado: las coordenadas quedan almacenadas junto con fecha y hora, y son visualizables en un mapa. |
| **Requisitos relacionados** | REQ-F-016 |
| **Historias relacionadas** | HU-015 |
| **Estado** | Pendiente — diseño de datos disponible, sin código |

**Descripción:** el backend expone una API para recibir datos de ubicación
enviados por dispositivos de rastreo asociados a las mascotas. Módulo
pendiente de implementación; el modelo de datos (tabla `Dispositivo_IoT`)
ya está diseñado.

**Flujo principal**
1. El dispositivo envía coordenadas válidas junto con su identificador.
2. El sistema almacena las coordenadas con fecha y hora de actualización.
3. El sistema deja la ubicación disponible para su visualización en un
   mapa.

**Flujos alternativos:** no especificados en el SRS (módulo pendiente).
**Flujos de excepción:** no especificados en el SRS (módulo pendiente).

**Reglas de negocio:** las coordenadas deben ser válidas para ser
almacenadas.

---

## CU-16 — Generar recomendaciones clínicas asistidas por IA

| Campo | Contenido |
|---|---|
| **Objetivo** | Producir recomendaciones informativas a partir del historial médico |
| **Actor principal** | Veterinario |
| **Actores secundarios** | Servicio externo de Inteligencia Artificial |
| **Disparador** | El veterinario solicita una recomendación (interfaz no implementada). |
| **Precondiciones** | La mascota posee historial clínico registrado (CU-12). |
| **Postcondiciones** | Éxito esperado: el veterinario visualiza una recomendación generada automáticamente. |
| **Requisitos relacionados** | REQ-F-017 |
| **Historias relacionadas** | HU-016 |
| **Estado** | Pendiente — depende de REQ-F-013 |

**Descripción:** el sistema genera recomendaciones clínicas informativas
basadas en los datos del historial médico de la mascota, mediante un
servicio de IA. Módulo pendiente de implementación; depende
funcionalmente de que exista historial clínico (CU-12).

**Flujo principal**
1. El veterinario solicita una recomendación para una mascota.
2. El sistema envía los datos del historial al servicio de IA.
3. El sistema muestra la recomendación generada.

**Flujos alternativos:** no especificados en el SRS (módulo pendiente).
**Flujos de excepción:** no especificados en el SRS (módulo pendiente).

**Reglas de negocio:** la recomendación es informativa y depende de la
existencia de historial clínico previo.

---

## CU-17 — Generar factura digital y registrar pago

| Campo | Contenido |
|---|---|
| **Objetivo** | Emitir un comprobante de pago en PDF y registrar el método de pago |
| **Actor principal** | Administrador o Auxiliar |
| **Actores secundarios** | — |
| **Disparador** | El actor registra el cobro de un servicio veterinario (interfaz no implementada). |
| **Precondiciones** | Existe un servicio veterinario registrado. |
| **Postcondiciones** | Éxito esperado: el usuario puede descargar un PDF válido y la forma de pago queda almacenada. |
| **Requisitos relacionados** | REQ-F-018 |
| **Historias relacionadas** | HU-017 |
| **Estado** | Pendiente — diseño de datos disponible, sin código |

**Descripción:** el sistema genera comprobantes de pago digitales en
formato PDF y permite registrar el método de pago asociado a un servicio
veterinario. Módulo pendiente de implementación; el modelo de datos
(`Factura`, `Detalle_Factura`, `Producto_Servicio`) ya está diseñado,
incluyendo campos de integración con el SRI ecuatoriano.

**Flujo principal**
1. El actor registra el servicio veterinario prestado y el método de pago.
2. El sistema genera el comprobante en formato PDF.
3. El sistema almacena la forma de pago junto con el servicio.

**Flujos alternativos:** no especificados en el SRS (módulo pendiente).
**Flujos de excepción:** no especificados en el SRS (módulo pendiente).

**Reglas de negocio:** el comprobante incluye campos de integración con el
SRI ecuatoriano, conforme al diseño de datos de la Entrega 1A.

---

## CU-18 — Generar reportes exportables

| Campo | Contenido |
|---|---|
| **Objetivo** | Producir reportes estadísticos descargables en PDF y Excel |
| **Actor principal** | Administrador |
| **Actores secundarios** | — |
| **Disparador** | El administrador solicita un reporte (interfaz no implementada). |
| **Precondiciones** | Existen datos suficientes en el sistema para generar el reporte. |
| **Postcondiciones** | Éxito esperado: el administrador descarga correctamente el reporte en ambos formatos. |
| **Requisitos relacionados** | REQ-F-019 |
| **Historias relacionadas** | HU-018 |
| **Estado** | Pendiente |

**Descripción:** el sistema genera reportes estadísticos exportables en
formato PDF y Excel. Módulo pendiente de implementación.

**Flujo principal**
1. El administrador solicita un reporte estadístico.
2. El sistema genera el reporte en PDF y en Excel.
3. El administrador descarga el reporte generado.

**Flujos alternativos:** no especificados en el SRS (módulo pendiente).
**Flujos de excepción:** no especificados en el SRS (módulo pendiente).

**Reglas de negocio:** ninguna adicional especificada en el SRS.

---

## CU-19 — Auditar operaciones del sistema

| Campo | Contenido |
|---|---|
| **Objetivo** | Registrar las acciones relevantes realizadas por los usuarios |
| **Actor principal** | Sistema |
| **Actores secundarios** | Administrador (consulta de auditoría) |
| **Disparador** | Se ejecuta una operación relevante (creación, modificación, eliminación) o un evento de autenticación. |
| **Precondiciones** | Existe un usuario autenticado ejecutando la operación (para el caso de operaciones CRUD). |
| **Postcondiciones** | Éxito esperado: la operación queda registrada con identificador de usuario, fecha y hora. |
| **Requisitos relacionados** | REQ-F-020 |
| **Historias relacionadas** | HU-019 |
| **Estado** | Parcialmente pendiente |

**Descripción:** el sistema registra las acciones relevantes realizadas
por los usuarios, incluyendo usuario, fecha y hora de cada operación. El
logging de autenticación (login exitoso/fallido, REQ-NF-009) ya está
parcialmente cubierto; la auditoría genérica de operaciones CRUD está
pendiente.

**Flujo principal**
1. El sistema detecta la ejecución de una operación relevante.
2. El sistema registra usuario, fecha y hora de la operación.
3. El registro queda disponible para su consulta posterior.

**Flujos alternativos:** el registro de eventos de autenticación (login
exitoso/fallido con IP, timestamp y `sub` del JWT) ya opera parcialmente,
conforme a REQ-NF-009.
**Flujos de excepción:** no especificados en el SRS para la auditoría
genérica de operaciones CRUD (módulo pendiente).

**Reglas de negocio:** toda operación de creación, modificación o
eliminación relevante debe quedar trazada a un usuario, fecha y hora.

---

## CU-20 — Consultar resumen de mascotas por especie *(nuevo, Tercera Entrega)*

| Campo | Contenido |
|---|---|
| **Objetivo** | Obtener el total de mascotas activas agrupadas por especie, con o sin filtro de dueño según el rol |
| **Actor principal** | Usuario autenticado (`ADMIN`, `VETERINARIO`, `AUXILIAR` o `DUENO`) |
| **Actores secundarios** | — |
| **Disparador** | El usuario hace clic en "Ver resumen por especie" en la pantalla de Mascotas, o invoca directamente `GET /api/mascotas/resumen-especies`. |
| **Precondiciones** | El usuario tiene una sesión activa (CU-02). |
| **Postcondiciones** | Éxito: el usuario recibe el conteo de mascotas activas agrupado por especie, acotado a su alcance de datos según el rol. |
| **Requisitos relacionados** | REQ-F-021 |
| **Historias relacionadas** | HU-020 |
| **Estado** | Implementado y verificado |

**Descripción:** el usuario autenticado abre la pantalla de Mascotas y
pulsa "Ver resumen por especie". El sistema consulta al backend, que
invoca una función almacenada en PostgreSQL para agrupar y contar las
mascotas activas por especie. Si el usuario es `ADMIN`, puede ver el
resumen global o filtrarlo por un dueño específico; cualquier otro rol
autenticado ve únicamente el resumen de sus propias mascotas (forzado por
el backend, no por el frontend).

**Flujo principal**
1. El usuario hace clic en "Ver resumen por especie".
2. El sistema (frontend) invoca `GET /api/mascotas/resumen-especies`,
   incluyendo la cookie de sesión (`withCredentials`).
3. El backend valida la sesión y determina el rol del usuario autenticado
   (incluye CU-05).
4. El backend determina el `duenioId` efectivo: si el rol es `ADMIN`, usa
   el `duenioId` recibido en el query param (o `null` para resumen
   global); para cualquier otro rol, ignora el `duenioId` recibido y usa
   el id del propio usuario autenticado.
5. El backend invoca la función PostgreSQL
   `fn_resumen_mascotas_por_especie` con el `duenioId` efectivo.
6. La función devuelve, para cada especie con al menos una mascota activa
   que cumpla el filtro, el nombre de la especie y el total.
7. El backend responde `200 OK` con la lista de objetos `{especie,
   total}`.
8. El frontend renderiza una tabla con columnas "Especie" y "Total".

**Flujos alternativos:** ninguno adicional a los descritos.

**Flujos de excepción**
- 3a. El usuario no tiene sesión válida: el sistema responde `401
  Unauthorized` con `ProblemDetails` (ver CU-02/CU-03 para el flujo de
  refresh automático desde el frontend).
- 5a. No existen mascotas activas que cumplan el filtro efectivo: la
  función devuelve un conjunto vacío; el sistema responde `200 OK` con
  `[]`, no es un error.
- *a. Error de comunicación con la base de datos: el sistema responde
  `500 Internal Server Error` con `ProblemDetails` genérico.

**Reglas de negocio:** solo el rol `ADMIN` puede forzar `duenioId` a un
valor distinto del propio; para el resto de roles, el backend siempre
sobrescribe el filtro con el id del usuario autenticado, sin importar el
valor recibido en la petición.

**Tipo de acceso a datos:** SP — función PL/pgSQL con `GROUP BY`, prohibida
como CRUD elemental por el bloque A.2.1 de la Guía.
