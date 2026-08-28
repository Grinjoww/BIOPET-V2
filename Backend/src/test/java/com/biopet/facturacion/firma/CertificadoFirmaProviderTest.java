package com.biopet.facturacion.firma;

import com.biopet.facturacion.exception.CertificadoFirmaInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Carga y validacion del material PKCS#12.
 *
 * <p>Todos los almacenes se generan en caliente en un directorio temporal. En
 * ningun momento se usa ni se pide un certificado real.
 */
class CertificadoFirmaProviderTest {

    @TempDir
    Path temporal;

    private CertificadoFirmaProvider proveedor(Path p12, String password, String alias) {
        FirmaProperties propiedades = new FirmaProperties();
        propiedades.getCertificado().setPath(p12 == null ? null : p12.toString());
        propiedades.getCertificado().setPassword(password);
        propiedades.getCertificado().setAlias(alias);
        return new CertificadoFirmaProvider(propiedades);
    }

    private CertificadoFirmaProvider proveedor(Path p12) {
        return proveedor(p12, CertificadoPruebaFactory.PASSWORD, null);
    }

    // ==================================================================
    // Caso valido
    // ==================================================================

    @Test
    void unPkcs12ValidoDevuelveMaterialDeFirma() throws Exception {
        Path p12 = CertificadoPruebaFactory.valido(temporal.resolve("valido.p12"));

        MaterialFirma material = proveedor(p12).material();

        assertThat(material.privateKey()).isNotNull();
        assertThat(material.certificate()).isNotNull();
        assertThat(material.certificate().getSubjectX500Principal().getName())
                .contains("BIOPET FIRMA TEST");
    }

    @Test
    void laInformacionDelCertificadoNoExponeMaterialSensible() throws Exception {
        Path p12 = CertificadoPruebaFactory.valido(temporal.resolve("valido.p12"));

        InformacionCertificado info = proveedor(p12).informacion();

        assertThat(info.subject()).contains("BIOPET FIRMA TEST");
        assertThat(info.issuer()).contains("BIOPET FIRMA TEST");
        assertThat(info.serial()).isNotNull();
        assertThat(info.notBefore()).isBefore(info.notAfter());
        // Ni la clave ni la contrasena aparecen en la representacion textual.
        assertThat(info.toString()).doesNotContain(CertificadoPruebaFactory.PASSWORD);
    }

    @Test
    void niElMaterialNiLasPropiedadesImprimenSecretosEnToString() throws Exception {
        Path p12 = CertificadoPruebaFactory.valido(temporal.resolve("valido.p12"));
        FirmaProperties propiedades = new FirmaProperties();
        propiedades.getCertificado().setPath(p12.toString());
        propiedades.getCertificado().setPassword(CertificadoPruebaFactory.PASSWORD);

        MaterialFirma material = new CertificadoFirmaProvider(propiedades).material();

        // Un log accidental no debe volcar clave privada ni contrasena.
        assertThat(material.toString())
                .doesNotContain(CertificadoPruebaFactory.PASSWORD)
                .doesNotContain("RSAPrivateKey")
                .contains("subject=");
        assertThat(propiedades.toString())
                .doesNotContain(CertificadoPruebaFactory.PASSWORD)
                .contains("passwordConfigurada=true");
    }

    // ==================================================================
    // Configuracion ausente
    // ==================================================================

    @Test
    void sinConfiguracionElErrorDiceQueFaltaConfigurar() {
        assertThatThrownBy(() -> proveedor(null, CertificadoPruebaFactory.PASSWORD, null).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("SRI_CERT_PATH");

        assertThatThrownBy(() -> proveedor(temporal.resolve("x.p12"), "  ", null).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("SRI_CERT_PASSWORD");
    }

    @Test
    void unPkcs12InexistenteSeDenunciaSinFiltrarLaContrasena() {
        Path inexistente = temporal.resolve("no-existe.p12");

        assertThatThrownBy(() -> proveedor(inexistente).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("No se puede leer")
                .hasMessageNotContaining(CertificadoPruebaFactory.PASSWORD);
    }

    @Test
    void unaContrasenaIncorrectaSeDenunciaSinFiltrarla() throws Exception {
        Path p12 = CertificadoPruebaFactory.valido(temporal.resolve("valido.p12"));

        assertThatThrownBy(() -> proveedor(p12, "contrasena-equivocada", null).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("contrasena incorrecta")
                .hasMessageNotContaining("contrasena-equivocada");
    }

    // ==================================================================
    // Certificado que no sirve para firmar
    // ==================================================================

    @Test
    void unCertificadoCaducadoSeRechaza() throws Exception {
        Path p12 = CertificadoPruebaFactory.caducado(temporal.resolve("caducado.p12"));

        assertThatThrownBy(() -> proveedor(p12).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("caduco");
    }

    @Test
    void unCertificadoQueAunNoEsValidoSeRechaza() throws Exception {
        Path p12 = CertificadoPruebaFactory.aunNoValido(temporal.resolve("futuro.p12"));

        assertThatThrownBy(() -> proveedor(p12).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("no es valido hasta");
    }

    @Test
    void unaClaveRsaDe1024BitsSeRechaza() throws Exception {
        Path p12 = CertificadoPruebaFactory.rsaCorto(temporal.resolve("corto.p12"));

        assertThatThrownBy(() -> proveedor(p12).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("1024")
                .hasMessageContaining("2048");
    }

    @Test
    void unaClaveQueNoEsRsaSeRechaza() throws Exception {
        Path p12 = CertificadoPruebaFactory.noRsa(temporal.resolve("ec.p12"));

        assertThatThrownBy(() -> proveedor(p12).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("RSA");
    }

    @Test
    void unaClaveQueNoCorrespondeAlCertificadoSeRechaza() throws Exception {
        // El PKCS#12 se crea sin protestar: KeyStore no comprueba la
        // correspondencia. Solo se detecta comparando los modulos.
        Path p12 = CertificadoPruebaFactory.claveNoCorresponde(temporal.resolve("cruzado.p12"));

        assertThatThrownBy(() -> proveedor(p12).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    void unKeyUsageQueNoPermiteFirmarSeRechaza() throws Exception {
        Path p12 = CertificadoPruebaFactory.sinUsoDeFirma(temporal.resolve("sinfirma.p12"));

        assertThatThrownBy(() -> proveedor(p12).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("KeyUsage");
    }

    // ==================================================================
    // Alias
    // ==================================================================

    @Test
    void conVariasClavesSeExigeElegirAlias() throws Exception {
        Path p12 = CertificadoPruebaFactory.dosClaves(temporal.resolve("dos.p12"));

        assertThatThrownBy(() -> proveedor(p12).material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("SRI_CERT_ALIAS");

        // Indicandolo, funciona.
        assertThatCode(() -> proveedor(p12, CertificadoPruebaFactory.PASSWORD,
                CertificadoPruebaFactory.ALIAS).material())
                .doesNotThrowAnyException();
    }

    @Test
    void unAliasInexistenteSeDenuncia() throws Exception {
        Path p12 = CertificadoPruebaFactory.valido(temporal.resolve("valido.p12"));

        assertThatThrownBy(() -> proveedor(p12, CertificadoPruebaFactory.PASSWORD, "no-existe").material())
                .isInstanceOf(CertificadoFirmaInvalidoException.class)
                .hasMessageContaining("no-existe");
    }
}
