package com.biopet.facturacion.exception;

import com.biopet.facturacion.domain.AmbienteSri;

/**
 * No existe fila de contador para el par (punto de emision, ambiente) pedido.
 *
 * <p>Esto NO es un caso a resolver sobre la marcha: significa que la
 * configuracion fiscal esta incompleta. El servicio de reserva se niega
 * deliberadamente a crear el contador al vuelo, porque hacerlo enmascararia dos
 * errores graves y distintos:
 *
 * <ul>
 *   <li>que se este facturando desde un punto de emision que el SRI no tiene
 *       autorizado, en cuyo caso empezar a numerar desde 1 genera comprobantes
 *       que seran rechazados;</li>
 *   <li>que el punto SI este autorizado y ya tenga numeracion previa (por
 *       ejemplo de otro sistema o de una migracion), en cuyo caso arrancar en 1
 *       reutilizaria numeros ya emitidos, que es un problema tributario real.</li>
 * </ul>
 *
 * <p>Crear y configurar los contadores de ambos ambientes es responsabilidad
 * del futuro CRUD administrativo, con el valor inicial correcto y de forma
 * explicita.
 */
public class SecuencialNoConfiguradoException extends RuntimeException {

    private final Long puntoEmisionId;
    private final AmbienteSri ambiente;

    public SecuencialNoConfiguradoException(Long puntoEmisionId, AmbienteSri ambiente) {
        super("No hay contador de secuencial configurado para el punto de emision "
                + puntoEmisionId + " en el ambiente " + ambiente
                + ". La configuracion fiscal debe crearlo explicitamente antes de emitir.");
        this.puntoEmisionId = puntoEmisionId;
        this.ambiente = ambiente;
    }

    public Long getPuntoEmisionId() {
        return puntoEmisionId;
    }

    public AmbienteSri getAmbiente() {
        return ambiente;
    }
}
