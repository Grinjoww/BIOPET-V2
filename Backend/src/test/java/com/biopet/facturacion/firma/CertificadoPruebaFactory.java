package com.biopet.facturacion.firma;

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

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Genera almacenes PKCS#12 EXCLUSIVAMENTE PARA PRUEBAS.
 *
 * <p><b>Nunca</b> se usa ni se pide un certificado real. Cada llamada crea en
 * caliente un par de claves y un X.509 autofirmado con identidad ficticia, y el
 * .p12 se escribe bajo {@code target/}, que esta fuera del control de versiones.
 * La contrasena esta en claro aqui a proposito: no protege nada real.
 *
 * <p>Un certificado autofirmado sirve para probar la MECANICA de la firma
 * -estructura XAdES, correccion criptografica, integracion- y nada mas. No es
 * una firma electronica reconocida y el SRI la rechazaria; esa distincion es
 * importante y no debe perderse de vista al leer estos tests en verde.
 *
 * <p>Ademas del caso valido produce los almacenes defectuosos que el proveedor
 * debe rechazar: caducado, aun no valido, RSA corto, no RSA, clave que no
 * corresponde al certificado y KeyUsage que no permite firmar.
 */
public final class CertificadoPruebaFactory {

    public static final String ALIAS = "biopet-firma";
    public static final String PASSWORD = "password-de-prueba-ficticia";
    public static final String SUBJECT = "CN=BIOPET FIRMA TEST,O=BIOPET TEST,C=EC";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CertificadoPruebaFactory() {
    }

    /** RSA 2048, vigente, con digitalSignature + nonRepudiation. */
    public static Path valido(Path destino) throws Exception {
        return escribir(destino, generarRsa(2048), vigenciaNormal(), true, false);
    }

    /** Caduco ayer. */
    public static Path caducado(Path destino) throws Exception {
        Instant ahora = Instant.now();
        return escribir(destino, generarRsa(2048),
                new Date[]{
                        Date.from(ahora.minus(30, ChronoUnit.DAYS)),
                        Date.from(ahora.minus(1, ChronoUnit.DAYS))},
                true, false);
    }

    /** Todavia no es valido: empieza manana. */
    public static Path aunNoValido(Path destino) throws Exception {
        Instant ahora = Instant.now();
        return escribir(destino, generarRsa(2048),
                new Date[]{
                        Date.from(ahora.plus(1, ChronoUnit.DAYS)),
                        Date.from(ahora.plus(30, ChronoUnit.DAYS))},
                true, false);
    }

    /** RSA de 1024 bits: por debajo del minimo exigido. */
    public static Path rsaCorto(Path destino) throws Exception {
        return escribir(destino, generarRsa(1024), vigenciaNormal(), true, false);
    }

    /** Clave EC en lugar de RSA. */
    public static Path noRsa(Path destino) throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(256, new SecureRandom());
        return escribir(destino, generador.generateKeyPair(), vigenciaNormal(), true, true);
    }

    /** KeyUsage sin digitalSignature ni nonRepudiation. */
    public static Path sinUsoDeFirma(Path destino) throws Exception {
        return escribir(destino, generarRsa(2048), vigenciaNormal(), false, false);
    }

    /**
     * Almacen cuyo certificado pertenece a OTRO par de claves.
     *
     * <p>{@code KeyStore.setKeyEntry} no comprueba la correspondencia, asi que
     * este almacen se crea sin problema y solo se detecta comparando los modulos,
     * que es justo lo que hace el proveedor.
     */
    public static Path claveNoCorresponde(Path destino) throws Exception {
        KeyPair delCertificado = generarRsa(2048);
        KeyPair otro = generarRsa(2048);
        X509Certificate certificado = certificar(delCertificado, vigenciaNormal(), true, false);

        KeyStore almacen = KeyStore.getInstance("PKCS12");
        almacen.load(null, null);
        // La clave privada del OTRO par, junto al certificado del primero.
        almacen.setKeyEntry(ALIAS, otro.getPrivate(), PASSWORD.toCharArray(),
                new Certificate[]{certificado});
        return guardar(almacen, destino);
    }

    /** Dos entradas de clave: obliga a indicar el alias. */
    public static Path dosClaves(Path destino) throws Exception {
        KeyStore almacen = KeyStore.getInstance("PKCS12");
        almacen.load(null, null);
        for (String alias : new String[]{ALIAS, ALIAS + "-2"}) {
            KeyPair par = generarRsa(2048);
            almacen.setKeyEntry(alias, par.getPrivate(), PASSWORD.toCharArray(),
                    new Certificate[]{certificar(par, vigenciaNormal(), true, false)});
        }
        return guardar(almacen, destino);
    }

    // ------------------------------------------------------------------

    private static KeyPair generarRsa(int bits) throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(bits, new SecureRandom());
        return generador.generateKeyPair();
    }

    private static Date[] vigenciaNormal() {
        Instant ahora = Instant.now();
        return new Date[]{
                Date.from(ahora.minus(1, ChronoUnit.HOURS)),
                Date.from(ahora.plus(365, ChronoUnit.DAYS))};
    }

    private static Path escribir(Path destino, KeyPair par, Date[] vigencia,
                                 boolean permiteFirmar, boolean ec) throws Exception {
        X509Certificate certificado = certificar(par, vigencia, permiteFirmar, ec);
        KeyStore almacen = KeyStore.getInstance("PKCS12");
        almacen.load(null, null);
        almacen.setKeyEntry(ALIAS, par.getPrivate(), PASSWORD.toCharArray(),
                new Certificate[]{certificado});
        return guardar(almacen, destino);
    }

    private static X509Certificate certificar(KeyPair par, Date[] vigencia,
                                              boolean permiteFirmar, boolean ec) throws Exception {
        X500Name sujeto = new X500Name(SUBJECT);
        BigInteger serie = BigInteger.valueOf(System.nanoTime());

        X509v3CertificateBuilder constructor = new JcaX509v3CertificateBuilder(
                sujeto, serie, vigencia[0], vigencia[1], sujeto, par.getPublic());
        constructor.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        constructor.addExtension(Extension.keyUsage, true, permiteFirmar
                ? new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation)
                : new KeyUsage(KeyUsage.keyEncipherment | KeyUsage.dataEncipherment));

        ContentSigner firmante = new JcaContentSignerBuilder(ec ? "SHA256withECDSA" : "SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(par.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(constructor.build(firmante));
    }

    private static Path guardar(KeyStore almacen, Path destino) throws Exception {
        Files.createDirectories(destino.getParent());
        try (OutputStream salida = Files.newOutputStream(destino)) {
            almacen.store(salida, PASSWORD.toCharArray());
        }
        return destino;
    }
}
