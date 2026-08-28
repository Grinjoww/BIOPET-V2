# Avisos de terceros

Atribución técnica de las bibliotecas de terceros que BIOPET **redistribuye en
el artefacto de producción** (`BOOT-INF/lib/` del jar ejecutable) y que se
incorporaron a raíz del módulo de firma electrónica.

Este documento es una nota técnica de atribución, no un dictamen legal. Las
licencias se citan tal y como las **declaran** los propios artefactos en sus
metadatos POM; cuando ese dato es menos preciso que el del repositorio de
origen, ambas fuentes se anotan por separado en lugar de mezclarlas. Cualquier
uso comercial o redistribución debería revisarse con el texto completo de cada
licencia y, si procede, con asesoría legal.

Este archivo no pretende ser un inventario exhaustivo de todas las dependencias
del proyecto (Spring Boot y su ecosistema aportan muchas más, mayoritariamente
Apache-2.0). Recoge las que entraron con la Fase 6 y las que su árbol arrastra
de forma directa.

Árbol reproducible con:

```bash
cd Backend && mvn dependency:tree
```

---

## Firma electrónica XAdES

### xades4j 2.4.1

| Campo | Valor |
|---|---|
| Coordenadas | `com.googlecode.xades4j:xades4j:2.4.1` |
| Proyecto | <https://github.com/luisgoncalves/xades4j> |
| Uso en BIOPET | Producción de firmas XAdES-BES (ETSI TS 101 903 v1.3.2) sobre los comprobantes electrónicos, según exige la Ficha Técnica de Comprobantes Electrónicos Offline del SRI |
| Scope | `compile` desde la Fase 6 (antes `test`, durante el spike de la Fase 3) |

**Licencia — dos fuentes, distinta precisión.** Conviene separarlas porque no
dicen lo mismo:

1. **Lo que declara el artefacto Maven** (`xades4j-2.4.1.pom`), citado
   literalmente:

   ```xml
   <license>
       <name>GNU Lesser General Public License</name>
       <url>http://www.gnu.org/licenses/lgpl.html</url>
       <distribution>repo</distribution>
   </license>
   ```

   El POM **no indica número de versión**: dice "GNU Lesser General Public
   License" a secas y apunta a la página genérica de la LGPL.

2. **Lo que declara el repositorio upstream.** <https://github.com/luisgoncalves/xades4j>
   identifica el proyecto como **LGPL-3.0** (comprobado el 2026-08-27 en la
   ficha del repositorio, que enlaza el fichero de licencia).

Es decir: la versión 3.0 **no** proviene de los metadatos del artefacto sino del
repositorio de origen. Se anota así, y no como un "LGPL-3.0" a secas, para no
convertir una inferencia en un dato.

**Nota sobre la LGPL.** Es la única dependencia del proyecto bajo LGPL; el resto
del árbol relevante es Apache-2.0 o BSD. BIOPET la consume como biblioteca sin
modificarla y sin enlazarla estáticamente: se distribuye como un jar
independiente dentro de `BOOT-INF/lib/`, de modo que puede sustituirse por otra
compilación de la misma biblioteca. Si el proyecto llegara a distribuirse fuera
del ámbito académico, este punto conviene revisarlo explícitamente con el texto
completo de la licencia.

### Dependencias transitivas de xades4j

Ninguna se declara a mano en el `pom.xml`; todas llegan por el árbol de xades4j.

| Biblioteca | Versión | Licencia declarada | Para qué |
|---|---|---|---|
| `org.apache.santuario:xmlsec` | 4.0.4 | Apache-2.0 | XML Signature: canonicalización, cálculo y verificación de firmas |
| `org.bouncycastle:bcprov-jdk18on` | 1.84 | "Bouncy Castle Licence" según su POM (texto de estilo MIT) | Primitivas criptográficas |
| `org.bouncycastle:bcpkix-jdk18on` | 1.84 | "Bouncy Castle Licence" según su POM | PKI y certificados X.509 |
| `org.bouncycastle:bcutil-jdk18on` | 1.84 | "Bouncy Castle Licence" según su POM | Utilidades ASN.1 |
| `com.google.inject:guice` | 7.0.0 | Apache-2.0 | Inyección interna de xades4j |
| `com.google.guava:guava` | 33.4.0-jre | Apache-2.0 | Utilidades, requerida por Guice |
| `commons-codec:commons-codec` | 1.16.1 | Apache-2.0 | Base64/Hex, requerida por Santuario |
| `com.fasterxml.woodstox:woodstox-core` | 7.1.0 | Apache-2.0 | StAX, requerida por Santuario |
| `org.codehaus.woodstox:stax2-api` | 4.2.2 | BSD de 2 cláusulas | API StAX2 |

BouncyCastle se usa además en `src/test` para generar los certificados PKCS#12
ficticios de las pruebas. No se declara ninguna dependencia extra por ello: se
aprovecha la que ya arrastra xades4j.

---

## SOAP: web services offline del SRI (Fase 7)

Se declaran en el `pom.xml` tres artefactos; el resto llega por su árbol.

| Biblioteca | Versión | Alcance | Licencia declarada | Para qué |
|---|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-web-services` | 3.2.12 | compile | Apache-2.0 | Cliente SOAP tipado (`WebServiceTemplate`) |
| `org.glassfish.jaxb:jaxb-runtime` | 4.0.5 | compile | EDL-1.0 / BSD de 3 cláusulas | Runtime de binding que necesita `Jaxb2Marshaller` |
| `org.springframework.ws:spring-ws-test` | 4.0.11 | **test** | Apache-2.0 | `MockWebServiceServer`: la suite prueba el diálogo completo sin Internet |

Versiones gestionadas por `spring-boot-dependencies`; ninguna se fija a mano.

### Dependencias transitivas

| Biblioteca | Versión | Licencia declarada | Para qué |
|---|---|---|---|
| `org.springframework.ws:spring-ws-core` | 4.0.11 | Apache-2.0 | Núcleo de Spring Web Services |
| `org.springframework.ws:spring-xml` | 4.0.11 | Apache-2.0 | Utilidades XML de Spring-WS |
| `org.springframework:spring-oxm` | 6.1.15 | Apache-2.0 | Abstracción de marshalling objeto/XML |
| `com.sun.xml.messaging.saaj:saaj-impl` | 3.0.4 | EDL-1.0 | Implementación SAAJ (sobres SOAP) |
| `jakarta.xml.soap:jakarta.xml.soap-api` | 3.0.2 | EDL-1.0 | API SAAJ |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.2 | EDL-1.0 | API de binding XML |
| `org.glassfish.jaxb:jaxb-core` | 4.0.5 | EDL-1.0 | Núcleo del runtime JAXB |
| `org.glassfish.jaxb:txw2` | 4.0.5 | EDL-1.0 | Escritura XML tipada, requerida por JAXB |
| `jakarta.activation:jakarta.activation-api` | 2.1.3 | EDL-1.0 | Tipos MIME, requerida por JAXB y SAAJ |
| `org.jvnet.staxex:stax-ex` | 2.1.0 | EDL-1.0 / BSD de 2 cláusulas | Extensiones StAX, requerida por SAAJ |

Todo el árbol es Apache-2.0 o EDL-1.0 (licencia BSD de Eclipse, permisiva). No
añade ninguna dependencia copyleft: la única del proyecto sigue siendo xades4j.

**No se usa ningún generador de código a partir del WSDL.** Los bindings JAXB
están escritos a mano en `com.biopet.facturacion.sri.ws`, de modo que el build
no depende de que los servidores del SRI estén disponibles y no se añade ningún
plugin de generación al ciclo de vida.

---

## WSDL del SRI versionados (solo en `test`)

Artefactos de terceros redistribuidos, sin modificar, en
`Backend/src/test/resources/sri/wsdl/`. Su procedencia, fecha de descarga y
SHA-256 están en el `README.md` de ese directorio.

| Artefacto | Origen |
|---|---|
| `RecepcionComprobantesOffline.wsdl` | Servicio de Rentas Internas (SRI), Ecuador — ambiente de pruebas (CELCER) |
| `AutorizacionComprobantesOffline.wsdl` | Servicio de Rentas Internas (SRI), Ecuador — ambiente de pruebas (CELCER) |

No se compilan ni generan código: los usa `SriBindingContraWsdlTest` para
validar, sin red, que las respuestas simuladas de la suite tienen la forma del
contrato oficial.

---

## Esquemas XSD versionados

No son bibliotecas, pero sí artefactos de terceros redistribuidos en
`Backend/src/main/resources/sri/xsd/factura/2.1.0/`. Su procedencia, versión,
fecha de descarga y SHA-256 están documentados en el `PROVENANCE.md` de ese
directorio.

| Artefacto | Origen |
|---|---|
| `factura_V2.1.0.xsd` | Servicio de Rentas Internas (SRI), Ecuador |
| `xmldsig-core-schema.xsd` | W3C — XML-Signature Syntax and Processing (REC 2002-02-12) |
