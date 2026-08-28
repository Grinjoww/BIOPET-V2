package com.biopet.facturacion.sri;

/**
 * Codigos del catalogo de mensajes del SRI que este modulo interpreta.
 *
 * <p>Solo estan aqui los que CAMBIAN el comportamiento. El resto se persiste en
 * la bitacora sin interpretarlos, que es lo correcto: entender un mensaje que
 * no se necesita entender solo crea ramas que envejecen mal.
 *
 * <p><b>Se decide por codigo, nunca por texto.</b> El campo {@code mensaje} del
 * SRI es descriptivo y ha cambiado de redaccion entre versiones de la ficha;
 * {@code identificador} es el contrato. Un {@code contains("REGISTRADA")}
 * funcionaria hoy y dejaria de funcionar en silencio manana, justo en el camino
 * que evita duplicar comprobantes.
 */
public final class CodigosMensajeSri {

    /**
     * "CLAVE DE ACCESO REGISTRADA": el SRI ya tiene ese comprobante.
     *
     * <p>Es la respuesta al reenvio de algo que si llego, tipicamente tras un
     * timeout en el que BIOPET no supo el desenlace. NO es un defecto del
     * comprobante y NO debe generar una clave nueva: la accion correcta es
     * consultar la autorizacion de esa misma clave.
     */
    public static final String CLAVE_REGISTRADA = "43";

    /**
     * Comprobante en procesamiento: el SRI lo tiene y aun no ha resuelto.
     *
     * <p>Tampoco es un rechazo. Reenviarlo solo generaria mas trabajo al SRI y
     * mas codigos 43; se trata como pendiente y se consulta autorizacion mas
     * tarde.
     */
    public static final String EN_PROCESAMIENTO = "70";

    private CodigosMensajeSri() {
    }
}
