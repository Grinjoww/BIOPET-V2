# Procedencia de los esquemas XSD — Factura SRI 2.1.0

Estos archivos son **artefactos de terceros versionados sin modificar**. No se
reformatean, no se corrigen y no se editan: cualquier cambio los desalinearía del
esquema contra el que el SRI valida realmente, y el objetivo de tenerlos aquí es
poder validar **sin acceso a Internet** desde el backend y desde CI.

Verificación de integridad (desde este directorio):

```
sha256sum -c SHA256SUMS.txt
```

---

## 1. `factura_V2.1.0.xsd`

| Campo | Valor |
|---|---|
| Origen | Servicio de Rentas Internas (SRI), Ecuador — fuente oficial |
| Página | <https://www.sri.gob.ec/facturacion-electronica> (sección "Esquemas XSD y XML") |
| Descarga | <https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/05546998-6f29-4870-be3b-62650f312a6c/XML%20y%20XSD%20Factura.zip> |
| Archivo dentro del ZIP | `XML y XSD Factura/factura_V2.1.0.xsd` |
| Versión del comprobante | 2.1.0 |
| Fecha de publicación del ZIP | febrero de 2022 (entradas del ZIP: 2022-02-07) |
| Fecha de descarga | 2026-08-27 |
| Tamaño | 40 439 bytes |
| SHA-256 | `5f2c37bc1a58bb40e8bbbc366cabe05d5dc199598aeea1561137370f8bd4eace` |

SHA-256 del ZIP completo tal y como lo sirvió el SRI (24 515 bytes):
`ba1ff0c4e329fe759c3f88dc75f2975780b315b6eb3d0069071b77c1f26fec03`

El ZIP oficial trae además los esquemas 1.0.0, 1.1.0 y 2.0.0 y un XML de ejemplo
por versión. **Solo se versiona el 2.1.0**, que es la versión que BIOPET emite;
incluir las otras sugeriría un soporte que no existe.

## 2. `xmldsig-core-schema.xsd`

| Campo | Valor |
|---|---|
| Origen | World Wide Web Consortium (W3C) — fuente normativa del estándar |
| Descarga | <https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/xmldsig-core-schema.xsd> |
| Estándar | XML-Signature Syntax and Processing, W3C Recommendation 12-feb-2002 |
| Espacio de nombres | `http://www.w3.org/2000/09/xmldsig#` |
| Fecha de descarga | 2026-08-27 |
| Tamaño | 10 293 bytes |
| SHA-256 | `35cf8197da812c85e40d57891b35c94187569ed474a2dac813ce5090dafcd35c` |

**Por qué está aquí y por qué no viene del SRI.** La primera instrucción del XSD
del SRI es:

```xml
<xsd:import namespace="http://www.w3.org/2000/09/xmldsig#"
            schemaLocation="xmldsig-core-schema.xsd"/>
```

El ZIP oficial **no incluye** ese archivo: el SRI da por supuesto que se resuelve
del W3C, que es su fuente normativa. Sin él, el esquema de factura ni siquiera
compila. Se versiona junto al del SRI para que la validación no dependa de la red.

Lo usa el elemento `ds:Signature`, opcional en el XSD y que esta fase **no emite**
(la firma XAdES es de una fase posterior); aun así el import debe resolverse para
que el esquema cargue.

### Nota sobre su DOCTYPE

Este archivo del W3C empieza con un `DOCTYPE` con subconjunto interno que
referencia un DTD externo (`http://www.w3.org/2001/XMLSchema.dtd`). Es del propio
W3C y no se toca. `FacturaXsdValidator` lo neutraliza: el acceso externo está
bloqueado (`accessExternalSchema` / `accessExternalDTD` vacíos) y el resolutor
local intercepta esa petición y devuelve contenido vacío, de modo que **nunca se
hace una petición de red**. Xerces no necesita ese DTD para compilar el esquema.

---

## Cómo se reobtienen

```bash
curl -L -o "XML y XSD Factura.zip" \
  "https://www.sri.gob.ec/o/sri-portlet-biblioteca-alfresco-internet/descargar/05546998-6f29-4870-be3b-62650f312a6c/XML%20y%20XSD%20Factura.zip"
unzip -j "XML y XSD Factura.zip" "XML y XSD Factura/factura_V2.1.0.xsd"

curl -L -o xmldsig-core-schema.xsd \
  "https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/xmldsig-core-schema.xsd"
```

Si algún día el SRI publica una versión nueva del comprobante, **no se sobrescribe
este directorio**: se añade uno nuevo (`.../factura/2.2.0/`) y se decide
explícitamente qué versión emite BIOPET. Los comprobantes ya emitidos se validaron
contra el esquema de su época y deben poder seguir validándose.
