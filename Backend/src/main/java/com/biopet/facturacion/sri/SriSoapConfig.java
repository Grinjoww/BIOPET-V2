package com.biopet.facturacion.sri;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobante;
import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobanteResponse;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobante;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobanteResponse;

/**
 * Cablea los dos clientes SOAP del SRI.
 *
 * <h2>Un marshaller y una plantilla por servicio</h2>
 *
 * <p>Se podria usar un solo {@link Jaxb2Marshaller} con las cuatro clases, ya
 * que los namespaces son distintos y no colisionan. Se hacen dos a proposito:
 * cada plantilla queda ligada a su endpoint Y a su contrato, de modo que
 * mandar por error una peticion de autorizacion al endpoint de recepcion no
 * compila, en lugar de fallar en produccion.
 *
 * <h2>Timeouts</h2>
 *
 * <p>Los dos senders se construyen con los timeouts de
 * {@link SriSoapProperties}. {@link HttpUrlConnectionMessageSender} basta: no
 * hace falta traer Apache HttpClient solo para poner dos timeouts, y menos
 * dependencia significa menos superficie que mantener. Si en el futuro hicieran
 * falta pool de conexiones o reintentos de transporte, este es el unico punto a
 * cambiar.
 */
@Configuration
public class SriSoapConfig {

    @Bean
    public Jaxb2Marshaller sriRecepcionMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(ValidarComprobante.class, ValidarComprobanteResponse.class);
        return marshaller;
    }

    @Bean
    public Jaxb2Marshaller sriAutorizacionMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                AutorizacionComprobante.class, AutorizacionComprobanteResponse.class);
        return marshaller;
    }

    @Bean
    public WebServiceTemplate sriRecepcionWebServiceTemplate(Jaxb2Marshaller sriRecepcionMarshaller,
                                                             SriSoapProperties propiedades) {
        return plantilla(sriRecepcionMarshaller, propiedades.getRecepcionUrl(), propiedades);
    }

    @Bean
    public WebServiceTemplate sriAutorizacionWebServiceTemplate(
            Jaxb2Marshaller sriAutorizacionMarshaller, SriSoapProperties propiedades) {
        return plantilla(sriAutorizacionMarshaller, propiedades.getAutorizacionUrl(), propiedades);
    }

    private static WebServiceTemplate plantilla(Jaxb2Marshaller marshaller, String uri,
                                                SriSoapProperties propiedades) {
        WebServiceTemplate plantilla = new WebServiceTemplate();
        plantilla.setMarshaller(marshaller);
        plantilla.setUnmarshaller(marshaller);
        plantilla.setDefaultUri(uri);
        plantilla.setMessageSender(sender(propiedades));
        // checkConnectionForFault sigue activo (es el default): un SOAP Fault
        // llega como SoapFaultClientException y no como una respuesta vacia que
        // habria que adivinar.
        return plantilla;
    }

    private static WebServiceMessageSender sender(SriSoapProperties propiedades) {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(propiedades.getConnectTimeout());
        sender.setReadTimeout(propiedades.getReadTimeout());
        return sender;
    }
}
