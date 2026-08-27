package com.biopet.facturacion.spike;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPIKE FASE 3 - Manejo de PKCS#12, tal como se hara en produccion cuando el
 * .p12 llegue montado como Secret File de Render.
 *
 * <p>Nada de lo que aqui se comprueba imprime la contrasena, la clave privada ni
 * el contenido del almacen.
 */
class Pkcs12SpikeTest {

    private static Path p12;
    private static CertificadoPruebaP12.Material material;

    @BeforeAll
    static void generar() throws Exception {
        p12 = Path.of("target", "tmp", "spike-p12-" + System.nanoTime() + ".p12");
        material = CertificadoPruebaP12.generar(p12, 2);
    }

    @AfterAll
    static void limpiar() throws Exception {
        if (p12 != null) {
            CertificadoPruebaP12.limpiar(p12);
            assertFalse(Files.exists(p12), "El .p12 temporal debe quedar borrado");
        }
    }

    @Test
    @DisplayName("El PKCS#12 se carga desde disco y expone alias, clave y certificado")
    void cargaDesdeDisco() throws Exception {
        assertTrue(Files.exists(p12));

        KeyStore almacen = KeyStore.getInstance("PKCS12");
        try (var entrada = Files.newInputStream(p12)) {
            almacen.load(entrada, CertificadoPruebaP12.PASSWORD);
        }

        var alias = Collections.list(almacen.aliases());
        assertEquals(1, alias.size(), "El almacen de prueba tiene una unica entrada");
        assertEquals(CertificadoPruebaP12.ALIAS, alias.get(0));
        assertTrue(almacen.isKeyEntry(CertificadoPruebaP12.ALIAS));
    }

    @Test
    @DisplayName("La contrasena incorrecta falla y no revela nada del almacen")
    void contrasenaIncorrecta() {
        assertThrows(Exception.class, () -> {
            KeyStore almacen = KeyStore.getInstance("PKCS12");
            try (var entrada = Files.newInputStream(p12)) {
                almacen.load(entrada, "contrasena-equivocada".toCharArray());
            }
        });
    }

    @Test
    @DisplayName("La clave es RSA de 2048 bits y el certificado tambien")
    void algoritmoYTamano() {
        RSAPrivateKey privada = (RSAPrivateKey) material.privateKey();
        RSAPublicKey publica = (RSAPublicKey) material.certificate().getPublicKey();

        assertEquals("RSA", privada.getAlgorithm());
        assertEquals("RSA", publica.getAlgorithm());
        assertEquals(CertificadoPruebaP12.BITS, privada.getModulus().bitLength());
        assertEquals(CertificadoPruebaP12.BITS, publica.getModulus().bitLength());
    }

    @Test
    @DisplayName("La clave privada se corresponde con el certificado")
    void clavePrivadaCorrespondeAlCertificado() throws Exception {
        byte[] datos = "prueba de correspondencia".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Signature firmador = Signature.getInstance("SHA256withRSA");
        firmador.initSign(material.privateKey());
        firmador.update(datos);
        byte[] firma = firmador.sign();

        Signature verificador = Signature.getInstance("SHA256withRSA");
        verificador.initVerify(material.certificate().getPublicKey());
        verificador.update(datos);
        assertTrue(verificador.verify(firma), "La publica del certificado debe validar lo que firmo la privada");

        RSAPrivateKey privada = (RSAPrivateKey) material.privateKey();
        RSAPublicKey publica = (RSAPublicKey) material.certificate().getPublicKey();
        assertEquals(privada.getModulus(), publica.getModulus(), "Mismo modulo RSA");
    }

    @Test
    @DisplayName("El certificado esta dentro de su periodo de validez")
    void dentroDeValidez() throws Exception {
        material.certificate().checkValidity(new Date());
    }

    @Test
    @DisplayName("Metadatos publicables por GET /api/config-fiscal/estado (sin exponer secretos)")
    void metadatosSegurosParaExponer() {
        var certificado = material.certificate();

        String subject = certificado.getSubjectX500Principal().getName();
        String issuer = certificado.getIssuerX500Principal().getName();
        Instant notBefore = certificado.getNotBefore().toInstant();
        Instant notAfter = certificado.getNotAfter().toInstant();
        var serial = certificado.getSerialNumber();

        assertTrue(subject.contains("BIOPET XADES TEST"));
        assertEquals(subject, issuer, "Autofirmado: subject == issuer");
        assertTrue(notAfter.isAfter(notBefore));
        assertTrue(serial.signum() > 0);

        // Estos cuatro datos son los unicos que un endpoint de estado deberia
        // exponer. NO son secretos: van dentro del propio XML firmado, en
        // ds:X509Certificate, visible para cualquiera que reciba la factura.
        // Lo que jamas debe salir: la clave privada, la contrasena del .p12 y
        // los bytes del almacen.
        assertFalse(subject.isBlank());
        assertFalse(notAfter.toString().isBlank());
    }

    @Test
    @DisplayName("Cargar dos veces el mismo .p12 produce el mismo certificado")
    void cargaDeterminista() throws Exception {
        var otra = CertificadoPruebaP12.cargar(p12);
        assertEquals(material.certificate(), otra.certificate());
        assertArrayEquals(material.certificate().getEncoded(), otra.certificate().getEncoded());
    }
}
