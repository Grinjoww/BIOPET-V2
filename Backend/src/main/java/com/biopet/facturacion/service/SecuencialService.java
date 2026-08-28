package com.biopet.facturacion.service;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.domain.ClaveAccesoRequest;
import com.biopet.facturacion.entity.SecuencialEmision;
import com.biopet.facturacion.exception.SecuencialAgotadoException;
import com.biopet.facturacion.exception.SecuencialNoConfiguradoException;
import com.biopet.facturacion.repository.SecuencialEmisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva numeros secuenciales fiscales del SRI.
 *
 * <p>Responsabilidad unica: entregar el siguiente numero de la serie de un
 * {@code (punto de emision, ambiente)} garantizando que dos emisiones
 * simultaneas nunca reciben el mismo. No genera la clave de acceso, ni el
 * codigo numerico, ni construye facturas, ni firma, ni habla con el SRI.
 *
 * <h2>Como se garantiza la exclusion mutua</h2>
 *
 * <p>Con un bloqueo pesimista de fila en PostgreSQL
 * ({@code SELECT ... FOR UPDATE}), no con estado en memoria. La diferencia es
 * practica, no teorica: {@code synchronized} o un {@code AtomicLong} ordenarian
 * los hilos de una sola JVM y fallarian en silencio en cuanto existan dos
 * replicas del backend, un despliegue solapado en el que conviven la version
 * vieja y la nueva, o un proceso de mantenimiento aparte. La numeracion fiscal
 * no admite ese fallo: dos facturas con el mismo numero son un problema
 * tributario, no un bug cosmetico.
 *
 * <p>Tampoco se usa {@code MAX(secuencial) + 1} sobre {@code facturas}: sin
 * bloqueo, dos transacciones leerian el mismo maximo; y con bloqueo seria un
 * lock sobre un rango de filas que crece sin parar. La fila unica de contador
 * es exactamente lo que hay que serializar y nada mas.
 *
 * <h2>Por que REQUIRED y no REQUIRES_NEW</h2>
 *
 * <p>{@link Transactional} con la propagacion por defecto ({@code REQUIRED}), a
 * proposito. La reserva debe poder formar parte de la MISMA transaccion que
 * persiste la factura emitida, para que ambas cosas ocurran o no ocurra
 * ninguna. Con {@code REQUIRES_NEW} el incremento se confirmaria en su propia
 * transaccion y, si la insercion de la factura fallase despues, el numero
 * quedaria consumido sin comprobante: un hueco en una numeracion que la ley
 * exige contigua.
 *
 * <p>La contrapartida es que el bloqueo dura lo que dure la transaccion de
 * quien llama. Por eso la regla de uso es: la transaccion que reserva debe
 * cerrarse en cuanto la factura este persistida. La generacion del XML, la
 * firma XAdES y las llamadas SOAP al SRI van DESPUES y FUERA de ella; mantener
 * una transaccion de PostgreSQL abierta esperando a un servicio externo
 * bloquearia la numeracion entera durante segundos.
 */
@Service
public class SecuencialService {

    /**
     * 999999999. Se toma de {@link ClaveAccesoRequest} (nucleo fiscal de la
     * Fase 2) en lugar de reescribir la constante: el tope no es una decision
     * de este servicio sino una consecuencia de que la clave de acceso reserve
     * 9 digitos al secuencial.
     */
    public static final long SECUENCIAL_MAXIMO = ClaveAccesoRequest.SECUENCIAL_MAXIMO;

    private final SecuencialEmisionRepository secuencialEmisionRepository;

    public SecuencialService(SecuencialEmisionRepository secuencialEmisionRepository) {
        this.secuencialEmisionRepository = secuencialEmisionRepository;
    }

    /**
     * Reserva y devuelve el siguiente secuencial de la serie.
     *
     * <p>Flujo, todo dentro de la transaccion de quien llama:
     * <ol>
     *   <li>valida los argumentos;</li>
     *   <li>carga la fila {@code (punto, ambiente)} con {@code FOR UPDATE};</li>
     *   <li>si no existe, la configuracion fiscal esta incompleta y falla;</li>
     *   <li>si ya vale 999999999, falla sin tocar nada;</li>
     *   <li>incrementa exactamente en 1 y fuerza el UPDATE;</li>
     *   <li>devuelve el numero reservado.</li>
     * </ol>
     *
     * <p>El numero devuelto solo es definitivo si la transaccion confirma. Si se
     * deshace, el contador vuelve a su valor anterior y ese numero se volvera a
     * entregar en la siguiente reserva. Es el comportamiento buscado: un
     * secuencial no se consume si la factura que lo iba a usar nunca existio.
     *
     * @param puntoEmisionId punto de emision, obligatorio.
     * @param ambiente       PRUEBAS o PRODUCCION, obligatorio. Cada uno lleva su
     *                       propio contador sobre el mismo punto.
     * @return el secuencial reservado, entre 1 y 999999999.
     * @throws IllegalArgumentException          si algun argumento es nulo.
     * @throws SecuencialNoConfiguradoException  si no hay contador para ese par.
     * @throws SecuencialAgotadoException        si la serie llego a su tope.
     */
    @Transactional
    public long reservar(Long puntoEmisionId, AmbienteSri ambiente) {
        if (puntoEmisionId == null) {
            throw new IllegalArgumentException("El punto de emision es obligatorio para reservar un secuencial.");
        }
        if (ambiente == null) {
            throw new IllegalArgumentException("El ambiente SRI es obligatorio para reservar un secuencial.");
        }

        // Una sola consulta: no se comprueba antes si el PuntoEmision existe.
        // Un punto inexistente y un punto sin contador configurado son el mismo
        // problema desde aqui -no se puede numerar- y desdoblarlo en dos
        // consultas solo anadiria trabajo por cada emision para distinguir dos
        // casos que se corrigen igual: configurando la serie.
        SecuencialEmision contador = secuencialEmisionRepository
                .bloquearPorPuntoEmisionYAmbiente(puntoEmisionId, ambiente)
                .orElseThrow(() -> new SecuencialNoConfiguradoException(puntoEmisionId, ambiente));

        long ultimo = contador.getUltimoSecuencial();
        if (ultimo >= SECUENCIAL_MAXIMO) {
            throw new SecuencialAgotadoException(puntoEmisionId, ambiente, SECUENCIAL_MAXIMO);
        }

        long reservado = ultimo + 1;
        contador.setUltimoSecuencial(reservado);

        // Flush explicito: el UPDATE se emite aqui, mientras la fila sigue
        // bloqueada, y no en un punto indeterminado del commit. Ademas hace que
        // una violacion del CHECK de rango salte en esta linea y no al final,
        // donde seria mucho mas dificil de atribuir.
        secuencialEmisionRepository.saveAndFlush(contador);

        return reservado;
    }
}
