package com.biopet.facturacion.firma;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion de la firma electronica ({@code sri.firma.*}).
 *
 * <p>Todas las claves son OPCIONALES en el arranque, a proposito: BIOPET debe
 * poder levantar (y sus pruebas correr) sin certificado, porque firmar es una
 * funcion mas y no un requisito para servir el resto de la aplicacion. La
 * ausencia de configuracion se convierte en un error claro en el momento de
 * firmar, no en un fallo de arranque que dejaria la clinica entera sin sistema
 * por no tener aun el .p12.
 *
 * <p>No hay ningun valor por defecto para la ruta ni para la contrasena: un
 * secreto con fallback en el repositorio no es un secreto. Llegan por entorno
 * ({@code SRI_CERT_PATH}, {@code SRI_CERT_PASSWORD}, {@code SRI_CERT_ALIAS}) y
 * el .p12 se monta como fichero secreto en el despliegue. Nunca se guarda en
 * PostgreSQL ni en Git.
 *
 * <p>{@link Certificado} es una clase anidada y no tres campos planos porque el
 * YAML declara {@code sri.firma.certificado.path}: con campos planos esa clave
 * se ligaria a {@code sri.firma.certificado-path} y la configuracion quedaria
 * sin aplicar en silencio.
 */
@Component
@ConfigurationProperties(prefix = "sri.firma")
public class FirmaProperties {

    private final Certificado certificado = new Certificado();

    /** RSA_SHA1 por defecto: es el perfil que describe la ficha del SRI. */
    private AlgoritmoFirmaSri algoritmo = AlgoritmoFirmaSri.RSA_SHA1;

    public Certificado getCertificado() {
        return certificado;
    }

    public AlgoritmoFirmaSri getAlgoritmo() {
        return algoritmo;
    }

    public void setAlgoritmo(AlgoritmoFirmaSri algoritmo) {
        this.algoritmo = algoritmo;
    }

    /** Nunca imprime la contrasena, solo si esta puesta. */
    @Override
    public String toString() {
        return "FirmaProperties[" + certificado + ", algoritmo=" + algoritmo + "]";
    }

    /** Datos del almacen PKCS#12. */
    public static class Certificado {

        /** Ruta del almacen PKCS#12 en el sistema de ficheros. */
        private String path;

        /** Contrasena del almacen y de la entrada de clave. */
        private String password;

        /**
         * Alias de la entrada a usar. Si se deja vacio se toma la unica entrada
         * de clave del almacen, y si hay varias se exige elegir explicitamente.
         */
        private String alias;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        @Override
        public String toString() {
            return "certificado[path=" + path + ", alias=" + alias
                    + ", passwordConfigurada=" + (password != null && !password.isBlank()) + "]";
        }
    }
}
