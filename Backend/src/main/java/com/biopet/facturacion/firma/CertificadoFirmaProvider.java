package com.biopet.facturacion.firma;

import com.biopet.facturacion.exception.CertificadoFirmaInvalidoException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * Carga y comprueba el material de firma desde un almacen PKCS#12.
 *
 * <h2>Donde vive el certificado</h2>
 *
 * <p>En un fichero del sistema, cuya ruta y contrasena llegan por entorno. NO se
 * guarda en PostgreSQL (una copia de seguridad de la base pasaria a contener la
 * identidad electronica de la clinica), ni en el repositorio, ni se envia al
 * frontend, ni aparece en logs.
 *
 * <p>Se lee del disco en CADA firma y no se cachea. Firmar es una operacion poco
 * frecuente, asi que el coste es irrelevante, y a cambio la clave privada no
 * queda residente en memoria entre facturas. Como efecto util, sustituir un
 * certificado caducado no obliga a reiniciar la aplicacion.
 *
 * <h2>Que se comprueba</h2>
 *
 * <p>Antes de devolver el material: que sea RSA, que llegue a 2048 bits, que la
 * clave privada corresponda de verdad al certificado, que este dentro de su
 * periodo de validez y que su {@code KeyUsage}, si lo declara, permita firmar.
 * Todo esto tambien lo verificaria el SRI o el propio xades4j mas adelante; se
 * hace aqui para que el error diga que pasa en lugar de aparecer como un fallo
 * criptografico opaco.
 */
@Component
public class CertificadoFirmaProvider {

    /** Minimo exigido por la normativa de firma electronica vigente. */
    static final int BITS_MINIMOS = 2048;

    /** Posicion de digitalSignature y nonRepudiation en el bit string KeyUsage. */
    private static final int KEY_USAGE_DIGITAL_SIGNATURE = 0;
    private static final int KEY_USAGE_NON_REPUDIATION = 1;

    private final FirmaProperties propiedades;

    public CertificadoFirmaProvider(FirmaProperties propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * @return clave privada y certificado listos para firmar.
     * @throws CertificadoFirmaInvalidoException si falta configuracion o el
     *         material no sirve.
     */
    public MaterialFirma material() {
        String ruta = propiedades.getCertificado().getPath();
        String password = propiedades.getCertificado().getPassword();

        if (ruta == null || ruta.isBlank()) {
            throw new CertificadoFirmaInvalidoException(
                    "No hay certificado de firma configurado (SRI_CERT_PATH). "
                            + "BIOPET no puede firmar comprobantes hasta que se configure.");
        }
        if (password == null || password.isBlank()) {
            throw new CertificadoFirmaInvalidoException(
                    "No hay contrasena del certificado de firma configurada (SRI_CERT_PASSWORD).");
        }

        Path archivo = Path.of(ruta);
        if (!Files.isReadable(archivo)) {
            // Se nombra la ruta, que no es un secreto; la contrasena jamas.
            throw new CertificadoFirmaInvalidoException(
                    "No se puede leer el almacen PKCS#12 en la ruta configurada: " + archivo);
        }

        // char[] y no String: se puede limpiar en cuanto deja de hacer falta, y
        // no queda a merced del recolector como un literal inmutable.
        char[] clave = password.toCharArray();
        try {
            return cargar(archivo, clave);
        } finally {
            Arrays.fill(clave, '\0');
        }
    }

    /** Metadatos publicos del certificado configurado. Nunca la clave. */
    public InformacionCertificado informacion() {
        return material().informacion();
    }

    private MaterialFirma cargar(Path archivo, char[] password) {
        KeyStore almacen;
        try (InputStream entrada = Files.newInputStream(archivo)) {
            almacen = KeyStore.getInstance("PKCS12");
            almacen.load(entrada, password);
        } catch (IOException e) {
            // Una contrasena incorrecta llega hasta aqui como IOException con
            // causa UnrecoverableKeyException; se traduce sin filtrar nada.
            throw new CertificadoFirmaInvalidoException(
                    "No se pudo abrir el almacen PKCS#12: contrasena incorrecta o archivo danado.", e);
        } catch (GeneralSecurityException e) {
            throw new CertificadoFirmaInvalidoException(
                    "El almacen PKCS#12 no se pudo cargar.", e);
        }

        String alias = resolverAlias(almacen);

        PrivateKey privateKey;
        X509Certificate certificate;
        try {
            java.security.Key clave = almacen.getKey(alias, password);
            if (!(clave instanceof PrivateKey)) {
                throw new CertificadoFirmaInvalidoException(
                        "La entrada \"" + alias + "\" del PKCS#12 no contiene una clave privada.");
            }
            privateKey = (PrivateKey) clave;

            java.security.cert.Certificate bruto = almacen.getCertificate(alias);
            if (!(bruto instanceof X509Certificate)) {
                throw new CertificadoFirmaInvalidoException(
                        "La entrada \"" + alias + "\" del PKCS#12 no contiene un certificado X.509.");
            }
            certificate = (X509Certificate) bruto;
        } catch (GeneralSecurityException e) {
            throw new CertificadoFirmaInvalidoException(
                    "No se pudo leer la clave del PKCS#12: contrasena de la entrada incorrecta.", e);
        }

        validar(privateKey, certificate);
        return new MaterialFirma(privateKey, certificate);
    }

    /**
     * Alias configurado, o el unico que haya. Si el almacen trae varias claves y
     * no se indico cual, se exige elegir: firmar con la equivocada produciria
     * comprobantes a nombre de otro.
     */
    private String resolverAlias(KeyStore almacen) {
        String configurado = propiedades.getCertificado().getAlias();
        try {
            if (configurado != null && !configurado.isBlank()) {
                if (!almacen.isKeyEntry(configurado)) {
                    throw new CertificadoFirmaInvalidoException(
                            "El PKCS#12 no tiene ninguna entrada de clave con el alias \""
                                    + configurado + "\".");
                }
                return configurado;
            }

            List<String> candidatos = new ArrayList<>();
            Enumeration<String> alias = almacen.aliases();
            while (alias.hasMoreElements()) {
                String actual = alias.nextElement();
                if (almacen.isKeyEntry(actual)) {
                    candidatos.add(actual);
                }
            }
            if (candidatos.isEmpty()) {
                throw new CertificadoFirmaInvalidoException(
                        "El PKCS#12 no contiene ninguna entrada con clave privada.");
            }
            if (candidatos.size() > 1) {
                throw new CertificadoFirmaInvalidoException(
                        "El PKCS#12 contiene " + candidatos.size() + " claves; indique cual usar "
                                + "con SRI_CERT_ALIAS.");
            }
            return candidatos.get(0);
        } catch (GeneralSecurityException e) {
            throw new CertificadoFirmaInvalidoException("No se pudieron leer los alias del PKCS#12.", e);
        }
    }

    private void validar(PrivateKey privateKey, X509Certificate certificate) {
        if (!(privateKey instanceof RSAPrivateKey clavePrivada)
                || !(certificate.getPublicKey() instanceof RSAPublicKey clavePublica)) {
            throw new CertificadoFirmaInvalidoException(
                    "La firma del SRI exige RSA; el certificado usa "
                            + certificate.getPublicKey().getAlgorithm() + ".");
        }

        int bits = clavePublica.getModulus().bitLength();
        if (bits < BITS_MINIMOS) {
            throw new CertificadoFirmaInvalidoException(
                    "La clave RSA es de " + bits + " bits y se exigen al menos " + BITS_MINIMOS + ".");
        }

        // La pareja debe serlo de verdad: dos modulos distintos producirian una
        // firma que nadie puede verificar con el certificado publicado.
        if (!clavePrivada.getModulus().equals(clavePublica.getModulus())) {
            throw new CertificadoFirmaInvalidoException(
                    "La clave privada no corresponde al certificado del almacen.");
        }

        try {
            certificate.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new CertificadoFirmaInvalidoException(
                    "El certificado de firma caduco el " + certificate.getNotAfter() + ".", e);
        } catch (CertificateNotYetValidException e) {
            throw new CertificadoFirmaInvalidoException(
                    "El certificado de firma no es valido hasta el " + certificate.getNotBefore() + ".", e);
        }

        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage != null
                && !(bit(keyUsage, KEY_USAGE_DIGITAL_SIGNATURE) || bit(keyUsage, KEY_USAGE_NON_REPUDIATION))) {
            throw new CertificadoFirmaInvalidoException(
                    "El certificado declara un KeyUsage que no permite firmar "
                            + "(faltan digitalSignature y nonRepudiation).");
        }
    }

    private boolean bit(boolean[] keyUsage, int posicion) {
        return keyUsage.length > posicion && keyUsage[posicion];
    }
}
