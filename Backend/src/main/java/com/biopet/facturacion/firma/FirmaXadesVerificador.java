package com.biopet.facturacion.firma;

import com.biopet.facturacion.exception.FirmaElectronicaException;
import org.apache.xml.security.signature.XMLSignature;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Verifica criptograficamente la firma de un comprobante ya serializado.
 *
 * <p>Trabaja sobre los BYTES, volviendo a parsearlos, no sobre el DOM que quedo
 * en memoria tras firmar. Es deliberado: lo que se envia al SRI son los bytes, y
 * verificar el objeto en memoria no probaria que la serializacion conservo la
 * firma. Este verificador comprueba exactamente el artefacto que se transmite.
 *
 * <p>El certificado se toma del propio {@code ds:KeyInfo}, como haria cualquier
 * verificador externo. Eso hace que la comprobacion sea significativa: como
 * {@code KeyInfo} tambien esta firmado (tercera referencia), sustituir el
 * certificado publicado por otro invalida la firma en vez de pasar inadvertido.
 *
 * <h2>Sin relajar nada para admitir SHA-1</h2>
 *
 * <p>La validacion segura de Santuario queda ACTIVADA siempre, tambien para
 * RSA-SHA1, que es el algoritmo que exige el perfil del SRI. No hace falta
 * apagarla: Apache Santuario 4.0.4 verifica RSA-SHA1 con esa proteccion puesta.
 *
 * <p>La confusion habitual viene de que el proveedor XMLDSig del JDK
 * ({@code javax.xml.crypto.dsig}) SI bloquea SHA-1 cuando se le pide validacion
 * segura, porque lo lleva en su lista de algoritmos prohibidos. Son dos
 * implementaciones distintas con listas distintas, y aqui se usa Santuario -la
 * misma que usa xades4j para firmar-, no la del JDK. Por eso no hay ningun
 * {@code secureValidation=false}, ninguna propiedad
 * {@code org.jcp.xml.dsig.secureValidation}, ningun cambio en
 * {@code java.security} y ningun {@code System.setProperty} en todo el modulo.
 *
 * <p>Verificar que la firma es MATEMATICAMENTE correcta no dice nada sobre si el
 * certificado es de confianza. Con un certificado autofirmado de pruebas la
 * firma valida perfectamente y el SRI la rechazaria igual. La validacion de
 * cadena de confianza no es responsabilidad de esta clase.
 */
@Component
public class FirmaXadesVerificador {

    /**
     * Validacion segura de Apache Santuario, SIEMPRE activada.
     *
     * <p>Es constante y no un parametro a proposito: que sea imposible pasar
     * {@code false} desde ningun sitio es justamente la garantia que se quiere.
     */
    private static final boolean VALIDACION_SEGURA = true;

    static {
        // Santuario exige inicializacion explicita antes del primer uso.
        org.apache.xml.security.Init.init();
    }

    /**
     * @return {@code true} si la firma cubre el documento y es correcta para el
     *         certificado que el propio documento publica.
     */
    public boolean esValida(byte[] xmlFirmado) {
        if (xmlFirmado == null || xmlFirmado.length == 0) {
            return false;
        }
        try {
            Document documento = XmlFirmaSeguro.parsear(xmlFirmado);
            // Tras re-parsear, el DOM no sabe que "id"/"Id" son identificadores:
            // sin esto no resolverian ni #comprobante ni #...-SignedProperties.
            XmlFirmaSeguro.registrarAtributosId(documento);

            Element firma = (Element) documento
                    .getElementsByTagNameNS(FacturaXadesSigner.NS_DSIG, "Signature").item(0);
            if (firma == null) {
                return false;
            }

            X509Certificate certificado = certificadoDeKeyInfo(documento);
            if (certificado == null) {
                return false;
            }

            // VALIDACION SEGURA SIEMPRE ACTIVA, tambien con RSA-SHA1.
            XMLSignature xmlSignature = new XMLSignature(firma, "", VALIDACION_SEGURA);
            return xmlSignature.checkSignatureValue(certificado);
        } catch (Exception e) {
            // Una firma manipulada suele fallar como excepcion de Santuario, no
            // como "false". Para quien llama, ambos casos significan lo mismo.
            return false;
        }
    }

    /**
     * Igual que {@link #esValida(byte[])} pero fallando con un mensaje claro.
     * Lo usa el servicio antes de persistir: un comprobante cuya firma no valida
     * no debe guardarse.
     */
    public void exigirValida(byte[] xmlFirmado, Long facturaId) {
        if (!esValida(xmlFirmado)) {
            throw new FirmaElectronicaException(
                    "La firma del comprobante de la factura " + facturaId
                            + " no verifica. No se persiste un documento firmado invalido.");
        }
    }

    /** Certificado publicado en {@code ds:KeyInfo}, en base64 DER. */
    public X509Certificate certificadoDeKeyInfo(Document documento) throws Exception {
        Element x509 = (Element) documento
                .getElementsByTagNameNS(FacturaXadesSigner.NS_DSIG, "X509Certificate").item(0);
        if (x509 == null) {
            return null;
        }
        byte[] der = Base64.getMimeDecoder().decode(x509.getTextContent().trim());
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

}
