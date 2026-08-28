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

## Esquemas XSD versionados

No son bibliotecas, pero sí artefactos de terceros redistribuidos en
`Backend/src/main/resources/sri/xsd/factura/2.1.0/`. Su procedencia, versión,
fecha de descarga y SHA-256 están documentados en el `PROVENANCE.md` de ese
directorio.

| Artefacto | Origen |
|---|---|
| `factura_V2.1.0.xsd` | Servicio de Rentas Internas (SRI), Ecuador |
| `xmldsig-core-schema.xsd` | W3C — XML-Signature Syntax and Processing (REC 2002-02-12) |
