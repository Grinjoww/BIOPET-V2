package com.biopet.facturacion.spike;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * SPIKE FASE 3 - Certificado PKCS#12 EXCLUSIVO PARA PRUEBAS.
 *
 * <p><b>Nunca</b> se usa ni se solicita un certificado real. Cada ejecucion
 * genera en caliente un par RSA 2048 y un X.509 autofirmado con identidad
 * ficticia ({@code CN=BIOPET XADES TEST,O=BIOPET TEST,C=EC}), validez corta y
 * una contrasena de prueba escrita en claro aqui a proposito: no protege nada
 * real.
 *
 * <p>El .p12 se escribe en {@code target/tmp}, que ya esta fuera del control de
 * versiones (target/ esta en .gitignore), y el test lo borra al terminar.
 *
 * <p>Un certificado autofirmado sirve para probar la mecanica criptografica de
 * la firma. NO es una firma electronica reconocida: el SRI la rechazaria. Esa
 * distincion es central en las conclusiones del spike.
 */
final class CertificadoPruebaP12 {

    static final String ALIAS = "biopet-spike";
    static final char[] PASSWORD = "spike-password-ficticia".toCharArray();
    static final String SUBJECT = "CN=BIOPET XADES TEST,O=BIOPET TEST,C=EC";
    static final int BITS = 2048;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CertificadoPruebaP12() {
    }

    /** Material del certificado de prueba, ya cargado desde el PKCS#12. */
    record Material(PrivateKey privateKey, X509Certificate certificate, Path archivo) {
    }

    /**
     * Genera un PKCS#12 nuevo en {@code destino} y devuelve su material.
     *
     * @param diasValidez validez deliberadamente corta.
     */
    static Material generar(Path destino, int diasValidez) throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(BITS, new SecureRandom());
        KeyPair par = generador.generateKeyPair();

        Instant ahora = Instant.now();
        Date notBefore = Date.from(ahora.minus(1, ChronoUnit.HOURS));
        Date notAfter = Date.from(ahora.plus(diasValidez, ChronoUnit.DAYS));
        X500Name sujeto = new X500Name(SUBJECT);
        BigInteger serie = BigInteger.valueOf(ahora.toEpochMilli());

        X509v3CertificateBuilder constructor = new JcaX509v3CertificateBuilder(
                sujeto, serie, notBefore, notAfter, sujeto, par.getPublic());
        constructor.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        // nonRepudiation + digitalSignature: xades4j comprueba KeyUsage cuando
        // checkKeyUsage esta activo, igual que exigiria un certificado real de
        // firma electronica.
        constructor.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

        ContentSigner firmante = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(par.getPrivate());
        X509Certificate certificado = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(constructor.build(firmante));

        KeyStore almacen = KeyStore.getInstance("PKCS12");
        almacen.load(null, null);
        almacen.setKeyEntry(ALIAS, par.getPrivate(), PASSWORD, new Certificate[]{certificado});

        Files.createDirectories(destino.getParent());
        try (OutputStream salida = Files.newOutputStream(destino)) {
            almacen.store(salida, PASSWORD);
        }

        return cargar(destino);
    }

    /**
     * Carga el PKCS#12 desde disco tal y como lo haria produccion (Secret File
     * montado en Render). La contrasena viaja como {@code char[]}, no como
     * String, para poder limpiarla y no dejarla en el pool de literales.
     */
    static Material cargar(Path archivo) throws Exception {
        KeyStore almacen = KeyStore.getInstance("PKCS12");
        try (InputStream entrada = Files.newInputStream(archivo)) {
            almacen.load(entrada, PASSWORD);
        }
        PrivateKey clave = (PrivateKey) almacen.getKey(ALIAS, PASSWORD);
        X509Certificate certificado = (X509Certificate) almacen.getCertificate(ALIAS);
        return new Material(clave, certificado, archivo);
    }

    /** Borra el .p12 temporal. Se invoca siempre desde @AfterAll. */
    static void limpiar(Path archivo) throws Exception {
        Files.deleteIfExists(archivo);
    }
}
