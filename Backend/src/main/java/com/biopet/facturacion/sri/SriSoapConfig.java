package com.biopet.facturacion.sri;

import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobante;
import com.biopet.facturacion.sri.ws.autorizacion.AutorizacionComprobanteResponse;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobante;
import com.biopet.facturacion.sri.ws.recepcion.ValidarComprobanteResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import org.w3c.dom.Node;

@Configuration
public class SriSoapConfig {

    private static final Logger log = LoggerFactory.getLogger(SriSoapConfig.class);

    @Bean
    public Jaxb2Marshaller sriRecepcionMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                ValidarComprobante.class,
                ValidarComprobanteResponse.class);
        return marshaller;
    }

    @Bean
    public Jaxb2Marshaller sriAutorizacionMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                AutorizacionComprobante.class,
                AutorizacionComprobanteResponse.class);
        return marshaller;
    }

    @Bean
    public WebServiceTemplate sriRecepcionWebServiceTemplate(
            Jaxb2Marshaller sriRecepcionMarshaller,
            SriSoapProperties propiedades) {

        WebServiceTemplate plantilla = plantilla(
                sriRecepcionMarshaller,
                propiedades.getRecepcionUrl(),
                propiedades);

        // Diagnóstico TEMPORAL:
        // muestra solamente nombres de elementos y namespaces.
        // Nunca imprime valores del XML.
        plantilla.setInterceptors(new ClientInterceptor[]{
                namespaceDiagnosticoInterceptor()
        });

        return plantilla;
    }

    @Bean
    public WebServiceTemplate sriAutorizacionWebServiceTemplate(
            Jaxb2Marshaller sriAutorizacionMarshaller,
            SriSoapProperties propiedades) {

        return plantilla(
                sriAutorizacionMarshaller,
                propiedades.getAutorizacionUrl(),
                propiedades);
    }

    private static WebServiceTemplate plantilla(
            Jaxb2Marshaller marshaller,
            String uri,
            SriSoapProperties propiedades) {

        WebServiceTemplate plantilla = new WebServiceTemplate();
        plantilla.setMarshaller(marshaller);
        plantilla.setUnmarshaller(marshaller);
        plantilla.setDefaultUri(uri);
        plantilla.setMessageSender(sender(propiedades));

        return plantilla;
    }

    private static WebServiceMessageSender sender(SriSoapProperties propiedades) {
        HttpUrlConnectionMessageSender sender =
                new HttpUrlConnectionMessageSender();

        sender.setConnectionTimeout(propiedades.getConnectTimeout());
        sender.setReadTimeout(propiedades.getReadTimeout());

        return sender;
    }

    private static ClientInterceptor namespaceDiagnosticoInterceptor() {
        return new ClientInterceptor() {

            @Override
            public boolean handleRequest(MessageContext messageContext) {
                return true;
            }

            @Override
            public boolean handleResponse(MessageContext messageContext) {
                diagnosticarRespuesta(messageContext);
                return true;
            }

            @Override
            public boolean handleFault(MessageContext messageContext) {
                diagnosticarRespuesta(messageContext);
                return true;
            }

            @Override
            public void afterCompletion(
                    MessageContext messageContext,
                    Exception ex) {
                // Nada que hacer.
            }
        };
    }

    private static void diagnosticarRespuesta(MessageContext messageContext) {
        try {
            if (!(messageContext.getResponse() instanceof SaajSoapMessage soap)) {
                log.info("SOAP_DIAG stage=RESPONSE_NOT_SAAJ");
                return;
            }

            log.info("SOAP_DIAG stage=RESPONSE_STRUCTURE_START");

            Node body = soap.getSaajMessage().getSOAPBody();
            recorrerElementos(body, "");

            log.info("SOAP_DIAG stage=RESPONSE_STRUCTURE_END");

        } catch (Exception e) {
            log.warn("SOAP_DIAG stage=ERROR type={}",
                    e.getClass().getSimpleName());
        }
    }

    private static void recorrerElementos(Node padre, String ruta) {
        for (Node nodo = padre.getFirstChild();
             nodo != null;
             nodo = nodo.getNextSibling()) {

            if (nodo.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nombre = nodo.getLocalName() != null
                    ? nodo.getLocalName()
                    : nodo.getNodeName();

            String namespace = nodo.getNamespaceURI();
            if (namespace == null) {
                namespace = "";
            }

            String nuevaRuta = ruta + "/" + nombre;

            log.info(
                    "SOAP_DIAG path={} namespace=[{}]",
                    nuevaRuta,
                    namespace);

            recorrerElementos(nodo, nuevaRuta);
        }
    }
}