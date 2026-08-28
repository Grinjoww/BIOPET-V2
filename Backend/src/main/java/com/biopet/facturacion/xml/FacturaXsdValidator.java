package com.biopet.facturacion.xml;

import com.biopet.facturacion.exception.FacturaXmlInvalidoException;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Valida un XML de factura contra el XSD oficial del SRI (Factura 2.1.0)
 * versionado en {@code src/main/resources/sri/xsd/factura/2.1.0/}.
 *
 * <h2>Sin red, ni al compilar el esquema ni al validar</h2>
 *
 * <p>El XSD del SRI importa {@code xmldsig-core-schema.xsd}, que a su vez
 * declara un DOCTYPE con un DTD externo del W3C. Sin precauciones, cargar el
 * esquema dispararia peticiones HTTP: un backend que necesita Internet para
 * validar una factura es un backend que deja de facturar cuando se cae una web
 * ajena, y ademas resuelve contra lo que haya en ese servidor ese dia.
 *
 * <p>La combinacion que se usa aqui, comprobada empiricamente:
 * <ul>
 *   <li>{@code accessExternalSchema} y {@code accessExternalDTD} vacios: ningun
 *       protocolo permitido, ni {@code http} ni {@code file};</li>
 *   <li>un {@link LSResourceResolver} propio que sirve los imports desde el
 *       classpath y devuelve contenido VACIO para todo lo demas, con lo que la
 *       peticion del DTD del W3C se neutraliza sin salir de la JVM.</li>
 * </ul>
 *
 * <h2>Defensa contra XXE en el documento a validar</h2>
 *
 * <p>El documento de entrada se parsea con un {@link XMLReader} propio que
 * prohibe {@code DOCTYPE} de raiz ({@code disallow-doctype-decl}). Eso corta de
 * un tajo toda la familia: XXE con {@code file://}, DTD externo por HTTP,
 * entidades generales o parametricas y expansiones tipo "billion laughs", porque
 * todas necesitan una declaracion DOCTYPE. Se rechaza al parsear, antes de
 * llegar a mirar el esquema.
 *
 * <p>No se toca ninguna propiedad global de la JVM: la configuracion es de estas
 * instancias y no altera el comportamiento XML del resto de la aplicacion.
 *
 * <p>El esquema se compila UNA vez (es inmutable y {@code Schema} es thread-safe)
 * y de el se crea un {@link Validator} por llamada, porque los validadores no lo
 * son.
 */
@Component
public class FacturaXsdValidator {

    /** Directorio de classpath donde viven los XSD oficiales sin modificar. */
    static final String RAIZ_XSD = "/sri/xsd/factura/2.1.0/";

    static final String XSD_FACTURA = "factura_V2.1.0.xsd";

    private static final String DISALLOW_DOCTYPE =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";

    private final Schema schema;

    public FacturaXsdValidator() {
        this.schema = compilarEsquema();
    }

    /**
     * @throws FacturaXmlInvalidoException si el XML no cumple el esquema oficial,
     *         o si trae un DOCTYPE (prohibido por seguridad).
     */
    public void validar(byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new FacturaXmlInvalidoException("El XML de la factura esta vacio.");
        }
        try {
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new ErroresEstrictos());
            validator.validate(new SAXSource(lectorSeguro(),
                    new InputSource(new ByteArrayInputStream(xml))));
        } catch (SAXParseException e) {
            throw new FacturaXmlInvalidoException(
                    "El XML no cumple el esquema oficial del SRI (linea " + e.getLineNumber()
                            + ", columna " + e.getColumnNumber() + "): " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new FacturaXmlInvalidoException(
                    "No se pudo validar el XML de la factura: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parser sin DOCTYPE y sin entidades externas. */
    private XMLReader lectorSeguro() throws SAXException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(DISALLOW_DOCTYPE, true);
            factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
            factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);

            XMLReader lector = factory.newSAXParser().getXMLReader();
            // Cinturon y tirantes: aunque disallow-doctype-decl ya aborta antes,
            // si alguna implementacion llegase a pedir una entidad externa, se
            // le entrega vacio en lugar de dejarla salir a la red o al disco.
            lector.setEntityResolver((publicId, systemId) ->
                    new InputSource(new ByteArrayInputStream(new byte[0])));
            return lector;
        } catch (Exception e) {
            throw new SAXException("No se pudo crear un parser XML seguro.", e);
        }
    }

    private Schema compilarEsquema() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setResourceResolver(new ResolutorLocal());
            factory.setErrorHandler(new ErroresEstrictos());

            byte[] principal = leerRecurso(XSD_FACTURA);
            return factory.newSchema(new StreamSource(
                    new ByteArrayInputStream(principal), RAIZ_XSD + XSD_FACTURA));
        } catch (SAXException e) {
            throw new IllegalStateException(
                    "No se pudo compilar el XSD oficial de factura 2.1.0. Revise que los esquemas "
                            + "sigan versionados en " + RAIZ_XSD, e);
        }
    }

    private static byte[] leerRecurso(String nombre) {
        try (InputStream in = FacturaXsdValidator.class.getResourceAsStream(RAIZ_XSD + nombre)) {
            if (in == null) {
                throw new IllegalStateException("No se encontro el recurso " + RAIZ_XSD + nombre);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Sirve los imports del esquema desde el classpath y nada mas. Todo lo que no
     * este versionado localmente se resuelve como contenido vacio: asi la
     * peticion del DTD externo del W3C que trae xmldsig-core-schema.xsd no sale
     * a la red, y tampoco lo haria un schemaLocation inesperado.
     */
    private static final class ResolutorLocal implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId,
                                       String systemId, String baseURI) {
            byte[] contenido = new byte[0];
            if (systemId != null) {
                String nombre = systemId.substring(systemId.lastIndexOf('/') + 1);
                if (FacturaXsdValidator.class.getResource(RAIZ_XSD + nombre) != null) {
                    contenido = leerRecurso(nombre);
                }
            }
            return new EntradaLocal(contenido, systemId, publicId);
        }
    }

    /** {@link LSInput} minimo respaldado por bytes ya leidos. */
    private static final class EntradaLocal implements LSInput {
        private InputStream byteStream;
        private String systemId;
        private String publicId;
        private String baseURI;
        private String encoding;
        private String stringData;
        private boolean certifiedText;

        private EntradaLocal(byte[] contenido, String systemId, String publicId) {
            this.byteStream = new ByteArrayInputStream(contenido);
            this.systemId = systemId;
            this.publicId = publicId;
            this.encoding = StandardCharsets.UTF_8.name();
        }

        @Override public Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(Reader characterStream) { /* no se usa */ }
        @Override public InputStream getByteStream() { return byteStream; }
        @Override public void setByteStream(InputStream byteStream) { this.byteStream = byteStream; }
        @Override public String getStringData() { return stringData; }
        @Override public void setStringData(String stringData) { this.stringData = stringData; }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String systemId) { this.systemId = systemId; }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String publicId) { this.publicId = publicId; }
        @Override public String getBaseURI() { return baseURI; }
        @Override public void setBaseURI(String baseURI) { this.baseURI = baseURI; }
        @Override public String getEncoding() { return encoding; }
        @Override public void setEncoding(String encoding) { this.encoding = encoding; }
        @Override public boolean getCertifiedText() { return certifiedText; }
        @Override public void setCertifiedText(boolean certifiedText) { this.certifiedText = certifiedText; }
    }

    /**
     * Por defecto un {@code warning} o incluso un {@code error} de validacion no
     * detiene el proceso. Para un comprobante fiscal cualquier desviacion del
     * esquema es motivo de rechazo del SRI, asi que se convierten en excepcion.
     */
    private static final class ErroresEstrictos extends DefaultHandler {
        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }
}
