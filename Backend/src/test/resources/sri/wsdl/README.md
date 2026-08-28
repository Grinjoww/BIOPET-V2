# WSDL oficiales de los web services offline del SRI

Copia literal, sin modificar, de los contratos publicados por el SRI en el
ambiente de PRUEBAS (CELCER):

- `RecepcionComprobantesOffline.wsdl`
  <https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline?wsdl>
- `AutorizacionComprobantesOffline.wsdl`
  <https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline?wsdl>

Descargados el 2026-08-28 (HTTP 200 en ambos). Los endpoints de PRODUCCION
publican el mismo contrato bajo `cel.sri.gob.ec`.

Son artefactos de terceros versionados **sin modificar**: no se reformatean ni
se editan. Verificacion de integridad, desde este directorio:

```
sha256sum -c SHA256SUMS.txt
```

| Archivo | Bytes | SHA-256 |
|---|---|---|
| `RecepcionComprobantesOffline.wsdl` | 4 118 | `4a71faf1c349d564b42ce74e64d0f47d4d7d532568359daafca0c16683e2236b` |
| `AutorizacionComprobantesOffline.wsdl` | 6 802 | `5fe7b3519009dca5ecb887455e3cb95936940cfe45c0ef56a06915237cce7851` |

## Por que estan versionados aqui, y solo en `test`

No se usan para generar codigo: los bindings JAXB de
`com.biopet.facturacion.sri.ws` estan escritos a mano (ver el `package-info` de
cada paquete). Estan aqui para que `SriBindingContraWsdlTest` pueda VALIDAR,
sin salir a Internet, que las respuestas simuladas de toda la suite tienen la
forma que exige el contrato oficial, y que los bindings las leen sin perder
nada.

Ese test existe por un fallo concreto que se cometio al escribir esta fase. El
esquema declara `elementFormDefault="unqualified"`, de donde se dedujo -mal-
que todos los hijos van sin namespace. Pero `comprobante`, `mensaje` y
`autorizacion` se declaran con `ref="tns:..."`, es decir, como REFERENCIAS a
elementos GLOBALES, y esos llevan siempre el namespace del esquema. La
respuesta real mezcla las dos formas.

El modo de fallo era silencioso y grave: con esos tres elementos declarados sin
namespace, JAXB no los encuentra, las listas llegan vacias y

- una DEVUELTA se persiste sin ninguno de los mensajes que explican por que;
- una respuesta AUTORIZADA se lee como "sin autorizaciones", que el contrato
  define como pendiente, de modo que NINGUNA factura llegaria jamas a
  AUTORIZADA.

Ninguna de las dos cosas produce un error. Por eso el contrato se versiona y se
comprueba contra el, en lugar de confiar en respuestas de ejemplo escritas a
mano.

Si el SRI publica una version nueva del contrato, se reemplazan estos ficheros
y el test dira si el binding sigue encajando.
