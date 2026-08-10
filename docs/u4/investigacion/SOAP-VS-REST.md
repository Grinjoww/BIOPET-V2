# SOAP vs REST — Investigación Unidad IV

**Autor:** Carvajal Loor Johan Stalin
**Actividad:** Unidad IV - GA | PFC BIOPET
**Archivo:** docs/u4/investigacion/SOAP-VS-REST.md

---

## 1. Introducción

La comunicación entre sistemas distribuidos a través de la web se ha resuelto históricamente con dos enfoques dominantes: SOAP y REST. Aunque suelen presentarse como alternativas equivalentes, no lo son ni siquiera en su categoría: SOAP es un **protocolo** con especificación formal publicada por el W3C, mientras que REST es un **estilo arquitectónico** derivado de un conjunto de restricciones descritas por Roy Fielding (2000) en su tesis doctoral. Compararlos exige, por tanto, situar cada uno en su contexto de uso antes de emitir juicios de valor.

Este documento desarrolla la comparación en diez criterios, la aterriza en casos reales del entorno ecuatoriano, muestra la misma operación implementada en ambos enfoques usando el sistema BIOPET como referencia, y concluye indicando en qué escenarios conviene cada uno.

## 2. ¿Qué es SOAP?

SOAP (*Simple Object Access Protocol*) es un protocolo de intercambio de mensajes estructurados definido por el W3C. Su unidad de comunicación es el **sobre** (*Envelope*), un documento XML compuesto por una cabecera opcional (*Header*) y un cuerpo obligatorio (*Body*). La cabecera transporta metadatos de procesamiento —seguridad, transaccionalidad, enrutamiento— y el cuerpo contiene la operación invocada y sus parámetros (W3C, 2007).

La pieza que define el vínculo entre cliente y servidor es el **WSDL** (*Web Services Description Language*), un documento XML que declara de manera formal qué operaciones existen, qué tipos de datos aceptan y devuelven, y en qué dirección de red están disponibles (W3C, 2001). Ese contrato es procesable por máquinas: a partir de un WSDL, herramientas como `wsimport` o `svcutil` generan automáticamente el código cliente completo.

Alrededor de SOAP existe además la familia de especificaciones **WS-\***: WS-Security para firma y cifrado, WS-ReliableMessaging para entrega garantizada, WS-AtomicTransaction para transacciones distribuidas, entre otras. Esa extensibilidad normalizada es la razón por la que SOAP sigue vigente en entornos corporativos.

## 3. ¿Qué es REST?

REST (*Representational State Transfer*) no es un protocolo sino un conjunto de seis restricciones arquitectónicas —cliente-servidor, ausencia de estado, cacheabilidad, sistema en capas, interfaz uniforme y código bajo demanda (opcional)— formuladas por Fielding (2000) al describir los principios que hicieron escalable a la propia World Wide Web.

En la práctica, una API REST modela el dominio como **recursos** identificados por URI, sobre los que se opera mediante los métodos definidos en la semántica de HTTP: `GET` para leer, `POST` para crear, `PUT`/`PATCH` para modificar y `DELETE` para eliminar; y responde con los códigos de estado de HTTP como parte del contrato (IETF, 2022, RFC 9110). El formato de representación no está prescrito, aunque JSON se ha impuesto por su ligereza y su soporte nativo en los navegadores.

Conviene precisar que la mayoría de las APIs llamadas "REST" —incluida la de BIOPET— alcanzan el **nivel 2 del modelo de madurez de Richardson**: usan recursos y verbos HTTP correctamente, pero no implementan hipermedia (HATEOAS), que sería el nivel 3 y el único que Fielding considera REST en sentido estricto.

## 4. Comparación por criterios

### Tabla resumen

| # | Criterio | SOAP | REST |
|---|---|---|---|
| 1 | Naturaleza | Protocolo con especificación W3C | Estilo arquitectónico (restricciones de diseño) |
| 2 | Formato de datos | XML obligatorio (*Envelope*) | Libre: JSON, XML, texto, binario |
| 3 | Contrato y documentación | WSDL, formal y obligatorio | OpenAPI, descriptivo y opcional |
| 4 | Transporte | HTTP, SMTP, JMS, TCP | HTTP/HTTPS exclusivamente |
| 5 | Complejidad | Alta: requiere *tooling* especializado | Baja: basta un cliente HTTP |
| 6 | Rendimiento | Menor: sobrecarga del sobre XML | Mayor: payload liviano y caché HTTP |
| 7 | Seguridad | WS-Security a nivel de mensaje | TLS + OAuth 2.0 / JWT a nivel de transporte |
| 8 | Manejo del estado | Admite estado y transacciones distribuidas | *Stateless* por restricción arquitectónica |
| 9 | Uso empresarial | Banca, seguros, gobierno, sistemas legados | Web, móvil, microservicios, SaaS |
| 10 | Integración web/móvil | Costosa: parseo XML en el cliente | Natural: JSON nativo en navegador y móvil |

### Desarrollo de cada criterio

**1. Naturaleza.** La diferencia de categoría es la que explica casi todas las demás. Al ser un protocolo, SOAP impone una estructura de mensaje que todo participante debe respetar, y esa rigidez es precisamente lo que garantiza la interoperabilidad entre plataformas heterogéneas: un cliente .NET y un servidor Java se entienden porque ambos implementan la misma especificación. REST, al ser un estilo, no impone nada verificable automáticamente; dos APIs "REST" pueden diferir profundamente en convenciones de nombres, paginación o manejo de errores, y ambas seguir siendo válidas.

**2. Formato de datos.** SOAP exige XML sin excepción. Esto aporta tipado fuerte —un XSD permite validar que un campo sea `xs:date` y no una cadena arbitraria— pero encarece cada mensaje con etiquetas de apertura y cierre, espacios de nombres y el sobre envolvente. REST no prescribe formato; en la práctica se usa JSON, cuyo tamaño es sensiblemente menor para los mismos datos y cuyo *parseo* es nativo en JavaScript.

**3. Contrato y documentación.** El WSDL es un contrato **prescriptivo**: existe antes que el código y de él se generan los clientes. En REST, OpenAPI cumple una función **descriptiva**: normalmente se genera *a partir* del código ya escrito, y nada obliga a que refleje fielmente el comportamiento real. Esa diferencia tiene una consecuencia práctica visible en BIOPET, donde el contrato OpenAPI publicado omite el mecanismo de autenticación por cookie que el sistema realmente usa —una divergencia que sería imposible en un servicio SOAP, porque el WSDL *es* el contrato ejecutable.

**4. Transporte.** SOAP es independiente del protocolo de transporte: el mismo mensaje puede viajar sobre HTTP, SMTP, JMS o una cola de mensajería corporativa, lo que resulta valioso en arquitecturas de integración empresarial (ESB) donde no todos los sistemas hablan HTTP. REST está atado a HTTP y a su semántica; fuera de HTTP, REST simplemente no tiene sentido, porque la interfaz uniforme *son* los métodos y códigos de HTTP.

**5. Complejidad.** Consumir un servicio SOAP implica interpretar un WSDL, generar *stubs*, construir un sobre correcto y manejar `SOAPFault`. Consumir una API REST requiere únicamente un cliente HTTP: `curl`, `fetch`, Postman o el propio navegador. Esa asimetría en la curva de entrada es la razón principal del desplazamiento de SOAP en el desarrollo web moderno.

**6. Rendimiento.** El sobre XML añade sobrecarga en dos frentes: el tamaño transmitido y el costo de *parseo* en ambos extremos. Pero la diferencia más determinante no es el tamaño, sino la **cacheabilidad**: en REST, un `GET` es idempotente y seguro por definición, de modo que proxies, CDN y cachés de aplicación pueden almacenar la respuesta usando la infraestructura estándar de HTTP. En SOAP, toda operación viaja por `POST`, que no es cacheable, y cualquier optimización de caché debe construirse a mano.

**7. Seguridad.** Este es el criterio donde la superioridad de SOAP es más clara y menos reconocida. WS-Security (OASIS, 2006) protege el **mensaje**: firma y cifra el contenido, de modo que las garantías de integridad, confidencialidad y no repudio viajan con el documento y sobreviven a cualquier intermediario. TLS, en cambio, protege el **canal**: el mensaje se descifra en cada salto, y si existe un balanceador o una pasarela intermedia, ahí el contenido está en claro. Para una API web con JWT y HTTPS —el modelo de BIOPET— esa protección de transporte es suficiente; para un comprobante con validez tributaria o una orden de transferencia interbancaria, no lo es.

**8. Manejo del estado.** REST impone la ausencia de estado como restricción: cada petición debe contener toda la información necesaria para ser procesada, lo que permite escalar horizontalmente añadiendo instancias sin sesiones compartidas. SOAP no lo prohíbe y, mediante WS-AtomicTransaction, admite transacciones distribuidas con confirmación en dos fases sobre varios servicios: algo que REST no ofrece de forma nativa y que en microservicios se resuelve con patrones compensatorios como *Saga*.

**9. Uso empresarial.** SOAP conserva presencia sólida donde hay contratos formales, auditoría y sistemas legados: banca central, seguros, telecomunicaciones y administración pública. REST domina en plataformas SaaS, APIs públicas, arquitecturas de microservicios y todo lo que consuma un cliente móvil o web. La distinción no es de antigüedad sino de requisitos: SOAP no fue reemplazado, fue confinado a los dominios donde su rigidez es una ventaja.

**10. Integración web/móvil.** Un navegador no tiene soporte nativo para SOAP: habría que construir el sobre XML a mano y parsear la respuesta con DOM. En móviles el problema se agrava, porque el mayor tamaño del payload consume datos y batería. REST con JSON es, en cambio, el camino natural: en BIOPET, el `MascotaApiService` de Angular consume `GET /api/mascotas` y recibe un objeto ya deserializado sin ninguna capa intermedia.

## 5. Casos de uso razonados en Ecuador

**a) SRI — Facturación electrónica: SOAP.** El esquema de comprobantes electrónicos del Servicio de Rentas Internas opera mediante servicios web SOAP: el contribuyente envía el comprobante en XML al servicio de recepción y luego consulta el de autorización, ambos descritos por WSDL. La elección es coherente con los requisitos, no una herencia tecnológica. El comprobante debe estar **firmado digitalmente** con certificado emitido por una entidad de certificación acreditada (formato XAdES-BES), porque el documento firmado tiene valor tributario **por sí mismo**, con independencia del canal por el que viajó. Ese es exactamente el escenario que WS-Security y la firma XML resuelven y que TLS no puede resolver: se necesita no repudio sobre el documento, no confidencialidad sobre la conexión. A ello se suma que miles de emisores heterogéneos —desde ERP corporativos hasta software de facturación de pequeños comercios— deben implementar un contrato idéntico y verificable, que es la fortaleza principal del WSDL.

**b) Integraciones bancarias y validación de identidad: SOAP.** En el sector financiero ecuatoriano, la integración entre sistemas *core* bancarios, buses de servicios empresariales (ESB) y servicios de validación de identidad sigue apoyándose mayoritariamente en servicios SOAP. Dos razones lo sostienen: la necesidad de trazabilidad y garantías transaccionales estrictas en operaciones que mueven dinero, y la convivencia obligada con sistemas legados —frecuentemente en plataformas mainframe o Java EE— cuya reescritura no se justifica económicamente. Aquí el costo de SOAP se paga a cambio de garantías que el negocio exige por regulación.

**c) Aplicaciones web y móviles de consumo —incluido BIOPET—: REST.** Plataformas de delivery, comercio electrónico, telemedicina, transporte y gestión clínica en Ecuador se construyen sobre APIs REST/JSON. El consumidor es un navegador o una aplicación móvil, el payload debe ser liviano por consideraciones de red y batería, el ciclo de cambios es rápido, y se aprovecha la caché de HTTP. BIOPET encaja plenamente en este perfil: un frontend Angular, autenticación con JWT, documentación con Swagger y despliegue en contenedores. Implementarlo en SOAP habría añadido complejidad sin aportar ninguna garantía que el dominio veterinario requiera.

**Observación transversal:** en la práctica, muchas instituciones ecuatorianas operan en modelo **híbrido**. Un banco o una empresa de facturación mantiene integraciones SOAP hacia el SRI y hacia sus sistemas legados, y expone simultáneamente una fachada REST hacia sus aplicaciones web y móviles. La pregunta profesional relevante rara vez es "¿SOAP o REST?", sino "¿en qué frontera de mi arquitectura va cada uno?".

## 6. Ejemplo práctico: la misma operación en SOAP y en REST

Para hacer la comparación concreta, se toma una operación real de BIOPET: **consultar los datos de la mascota con identificador 15**.

### 6.1 Enfoque SOAP

Petición:

```xml
POST /ws/mascotas HTTP/1.1
Host: biopet.ec
Content-Type: text/xml; charset=utf-8
SOAPAction: "http://biopet.ec/ws/ObtenerMascota"

<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
      <wsse:UsernameToken>
        <wsse:Username>vet@biopet.ec</wsse:Username>
        <wsse:Password>...</wsse:Password>
      </wsse:UsernameToken>
    </wsse:Security>
  </soap:Header>
  <soap:Body>
    <ObtenerMascota xmlns="http://biopet.ec/ws">
      <mascotaId>15</mascotaId>
    </ObtenerMascota>
  </soap:Body>
</soap:Envelope>
```

Respuesta:

```xml
HTTP/1.1 200 OK
Content-Type: text/xml; charset=utf-8

<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Body>
    <ObtenerMascotaResponse xmlns="http://biopet.ec/ws">
      <mascota>
        <id>15</id>
        <nombre>Luna</nombre>
        <especie>Canino</especie>
        <raza>Labrador</raza>
        <fechaNacimiento>2021-03-14</fechaNacimiento>
      </mascota>
    </ObtenerMascotaResponse>
  </soap:Body>
</soap:Envelope>
```

Si la mascota no existe, la respuesta **sigue siendo un HTTP 500** y el error viaja dentro del sobre:

```xml
<soap:Fault>
  <faultcode>soap:Client</faultcode>
  <faultstring>Mascota no encontrada: 15</faultstring>
</soap:Fault>
```

### 6.2 Enfoque REST (implementación real de BIOPET)

Petición:

```http
GET /api/mascotas/15 HTTP/1.1
Host: localhost:8080
Accept: application/json
Cookie: access_token=eyJhbGciOiJIUzI1NiJ9...
```

Respuesta:

```json
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 15,
  "duenioId": 4,
  "duenioNombre": "María Pérez",
  "nombre": "Luna",
  "especie": "Canino",
  "raza": "Labrador",
  "fechaNacimiento": "2021-03-14",
  "activo": true
}
```

Y si la mascota no existe, el código de estado **forma parte del contrato**:

```json
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "https://biopet.ec/problems/not-found",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "Mascota no encontrada: 15",
  "instance": "/api/mascotas/15"
}
```

### 6.3 Lectura comparada del ejemplo

Tres diferencias resaltan. Primera, el **volumen**: el mensaje SOAP ocupa varias veces el tamaño del JSON para transportar exactamente los mismos cinco datos. Segunda, el **manejo de errores**: SOAP devuelve 500 incluso cuando el problema es del cliente, obligando a parsear el cuerpo para saber qué ocurrió; REST usa el código de estado como primer nivel de información y, en el caso de BIOPET, complementa con `ProblemDetail` según el formato normalizado de la IETF (2023, RFC 9457, que sustituye al RFC 7807). Tercera, la **identificación del recurso**: en REST la mascota 15 tiene una URI propia y estable (`/api/mascotas/15`) que es enlazable y cacheable; en SOAP el identificador es un parámetro dentro de un cuerpo enviado siempre a la misma dirección, por lo que ninguna capa intermedia puede cachearlo.

## 7. Conclusión: cuándo conviene cada enfoque

No existe un ganador absoluto, y presentarlo como tal sería un error de análisis. La elección debe derivarse de los requisitos del dominio.

**Conviene SOAP cuando** el contrato debe ser formal y verificable por máquinas antes de escribir código; cuando se exige **no repudio** o firma a nivel de mensaje, y no basta con proteger el canal; cuando hay transacciones distribuidas con garantías ACID entre varios servicios; cuando el transporte no es necesariamente HTTP; o cuando hay que integrarse con sistemas legados que ya lo exponen. El caso del SRI reúne cuatro de estas cinco condiciones, y por eso SOAP sigue siendo ahí la decisión correcta, no una deuda técnica.

**Conviene REST cuando** el consumidor principal es un navegador o una aplicación móvil; cuando se prioriza la velocidad de desarrollo y una curva de adopción baja para terceros; cuando se quiere aprovechar la caché de HTTP; cuando el sistema debe escalar horizontalmente sin estado compartido; o cuando la API es pública y debe ser consumible con herramientas comunes.

**Aplicación a BIOPET.** REST es la elección adecuada para este PFC, y puede justificarse en términos del propio sistema. El consumidor es un frontend Angular que se beneficia de JSON nativo; la autenticación con JWT y cookies `HttpOnly` encaja en el modelo *stateless* que REST exige; la paginación se resuelve con `Pageable` sobre parámetros de consulta HTTP; la caché en Redis se apoya en que los `GET` son seguros e idempotentes; y la documentación automática con Swagger reduce el costo de mantener el contrato. Ninguno de los requisitos del dominio veterinario —agendar citas, registrar consultas, controlar vacunas— exige no repudio, transaccionalidad distribuida ni transporte fuera de HTTP.

El matiz que sí vale la pena señalar es que la ventaja del contrato formal de SOAP tiene un costo visible en BIOPET: como se documentó en la revisión crítica, el OpenAPI publicado no declara el esquema de autenticación por cookie ni los códigos de error que el sistema implementa. Ese tipo de divergencia entre contrato y comportamiento es el precio estructural de trabajar con un contrato descriptivo en lugar de prescriptivo, y la forma de pagarlo en REST es con disciplina explícita: anotar la API, versionarla y probar el contrato, no confiar en que la generación automática lo resuelva.

## 8. Referencias

- Fielding, R. T. (2000). *Architectural Styles and the Design of Network-based Software Architectures* [Tesis doctoral, University of California, Irvine]. https://ics.uci.edu/~fielding/pubs/dissertation/top.htm
- Internet Engineering Task Force. (2022). *RFC 9110: HTTP Semantics*. https://www.rfc-editor.org/rfc/rfc9110
- Internet Engineering Task Force. (2023). *RFC 9457: Problem Details for HTTP APIs*. https://www.rfc-editor.org/rfc/rfc9457
- OASIS. (2006). *Web Services Security: SOAP Message Security 1.1*. https://docs.oasis-open.org/wss/v1.1/
- OpenAPI Initiative. (2021). *OpenAPI Specification v3.1.0*. https://spec.openapis.org/oas/v3.1.0
- Servicio de Rentas Internas del Ecuador. (s.f.). *Ficha técnica de comprobantes electrónicos*. https://www.sri.gob.ec/facturacion-electronica
- World Wide Web Consortium. (2001). *Web Services Description Language (WSDL) 1.1*. https://www.w3.org/TR/wsdl
- World Wide Web Consortium. (2007). *SOAP Version 1.2 Part 1: Messaging Framework (Second Edition)*. https://www.w3.org/TR/soap12-part1/
