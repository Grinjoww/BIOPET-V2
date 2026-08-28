package com.biopet.facturacion.sri;

import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobante;
import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobanteResponse;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobante;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobanteResponse;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * Plantillas SOAP equivalentes a las que publica {@code SriSoapConfig}, pero
 * construidas a mano para las pruebas que no levantan contexto de Spring.
 *
 * <p>Se replican los mismos marshallers a proposito: si algun dia se cambiara
 * el binding en la configuracion productiva y no aqui, los tests dejarian de
 * probar lo que se despliega. Por eso las clases enlazadas son EXACTAMENTE las
 * mismas y estan escritas una sola vez en cada lado, sin trucos.
 */
final class SriSoapTestFixture {

    /** URI cualquiera: {@code MockWebServiceServer} no abre ninguna conexion real. */
    static final String URI_RECEPCION = "https://localhost/RecepcionComprobantesOffline";

    static final String URI_AUTORIZACION = "https://localhost/AutorizacionComprobantesOffline";

    private SriSoapTestFixture() {
    }

    static WebServiceTemplate plantillaRecepcion() {
        return plantilla(URI_RECEPCION,
                ValidarComprobante.class, ValidarComprobanteResponse.class);
    }

    static WebServiceTemplate plantillaAutorizacion() {
        return plantilla(URI_AUTORIZACION,
                AutorizacionComprobante.class, AutorizacionComprobanteResponse.class);
    }

    private static WebServiceTemplate plantilla(String uri, Class<?>... clases) {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(clases);
        try {
            marshaller.afterPropertiesSet();
        } catch (Exception e) {
            // Si el binding JAXB no se puede construir, no hay nada que probar.
            throw new IllegalStateException("No se pudo preparar el marshaller de prueba.", e);
        }

        WebServiceTemplate plantilla = new WebServiceTemplate();
        plantilla.setMarshaller(marshaller);
        plantilla.setUnmarshaller(marshaller);
        plantilla.setDefaultUri(uri);
        return plantilla;
    }
}
