package com.biopet.facturacion.sri;

import com.biopet.facturacion.domain.AmbienteSri;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ambiente fiscal EFECTIVO de este despliegue ({@code sri.ambiente}).
 *
 * <h2>Por que existe: el cliente HTTP no elige ambiente</h2>
 *
 * <p>Correccion pre-commit de la Fase 8A. Antes, {@code EmitirFacturaRequest}
 * dejaba que el cliente REST mandara {@code "ambiente": "PRODUCCION"} en el
 * cuerpo de la peticion. Eso es exactamente el tipo de decision que esta fase
 * prohibe delegar en el frontend: PRUEBAS/PRODUCCION no es un dato de negocio
 * que el usuario de la clinica deba -ni pueda- escoger por factura, es una
 * propiedad del DESPLIEGUE. Ahora {@code FacturaController} ya no lee ningun
 * ambiente del request: lo resuelve aqui, en el backend, una sola vez por
 * arranque.
 *
 * <p>Default {@code PRUEBAS}, igual que {@link SriSoapProperties}: un
 * despliegue mal configurado debe fallar hacia pruebas, nunca hacia produccion
 * por omision.
 *
 * <h2>Consistencia con los endpoints SOAP</h2>
 *
 * <p>Declarar {@code sri.ambiente=PRODUCCION} mientras
 * {@code sri.soap.recepcion-url}/{@code autorizacion-url} siguen apuntando a
 * CELCER (o viceversa) es una configuracion contradictoria que no debe
 * arrancar en silencio: emitiria comprobantes marcados PRODUCCION contra el
 * ambiente de pruebas del SRI, o -peor- marcados PRUEBAS contra produccion
 * real. Se comprueba aqui, en el arranque, reutilizando
 * {@link SriSoapProperties#apuntaAProduccion()} en vez de repetir la logica de
 * host: es la MISMA nocion de "a que ambiente apuntan los endpoints" que ya
 * usa esa clase para su propio aviso.
 */
@Component
@ConfigurationProperties(prefix = "sri")
public class SriAmbienteProperties {

    private static final Logger log = LoggerFactory.getLogger(SriAmbienteProperties.class);

    private final SriSoapProperties soapProperties;

    /** {@code sri.ambiente}. Nunca lo decide el cliente HTTP. */
    private AmbienteSri ambiente = AmbienteSri.PRUEBAS;

    public SriAmbienteProperties(SriSoapProperties soapProperties) {
        this.soapProperties = soapProperties;
    }

    @PostConstruct
    void validar() {
        boolean endpointsDeProduccion = soapProperties.apuntaAProduccion();

        if (ambiente == AmbienteSri.PRODUCCION && !endpointsDeProduccion) {
            throw new IllegalStateException(
                    "sri.ambiente=PRODUCCION pero los endpoints SOAP configurados (sri.soap.recepcion-url / "
                            + "sri.soap.autorizacion-url) no apuntan al ambiente de produccion del SRI. "
                            + "Configuracion inconsistente: no se arranca para evitar marcar comprobantes "
                            + "como PRODUCCION contra un servicio que no lo es.");
        }
        if (ambiente == AmbienteSri.PRUEBAS && endpointsDeProduccion) {
            throw new IllegalStateException(
                    "sri.ambiente=PRUEBAS pero los endpoints SOAP configurados (sri.soap.recepcion-url / "
                            + "sri.soap.autorizacion-url) apuntan al ambiente de produccion del SRI "
                            + "(cel.sri.gob.ec). Configuracion inconsistente: no se arranca para evitar enviar "
                            + "comprobantes reales marcados como PRUEBAS.");
        }

        log.info("SRI: ambiente fiscal efectivo = {} (endpoints de produccion: {})",
                ambiente, endpointsDeProduccion);
    }

    public AmbienteSri getAmbiente() {
        return ambiente;
    }

    public void setAmbiente(AmbienteSri ambiente) {
        this.ambiente = ambiente;
    }
}
