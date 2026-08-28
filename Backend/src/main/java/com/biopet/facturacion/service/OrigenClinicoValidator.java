package com.biopet.facturacion.service;

import com.biopet.entity.Cita;
import com.biopet.entity.Consulta;
import com.biopet.entity.EstadoCita;
import com.biopet.entity.Mascota;
import com.biopet.entity.Vacuna;
import com.biopet.facturacion.entity.OrigenDetalleFactura;
import com.biopet.facturacion.exception.OrigenClinicoInvalidoException;
import com.biopet.repository.CitaRepository;
import com.biopet.repository.ConsultaRepository;
import com.biopet.repository.VacunaRepository;
import org.springframework.stereotype.Component;

/**
 * Comprueba que la trazabilidad clinica de una linea es coherente con la
 * factura.
 *
 * <p>El origen no aporta ni precio ni impuesto -eso sale siempre del
 * {@code ConceptoFacturable}- pero si aporta la respuesta a "que atencion
 * respalda este cargo", que es exactamente lo que alguien mira cuando reclama
 * una factura. Por eso se valida en lugar de aceptarlo a ciegas.
 *
 * <p>No toca las entidades clinicas ni las marca como facturadas: solo lee. Que
 * un mismo origen aparezca en varias facturas sigue permitido a proposito (ver
 * el indice no unico {@code idx_factura_detalles_origen} de V8), porque una
 * refacturacion o una futura nota de credito deben poder referirse a la misma
 * consulta.
 */
@Component
public class OrigenClinicoValidator {

    private final ConsultaRepository consultaRepository;
    private final VacunaRepository vacunaRepository;
    private final CitaRepository citaRepository;

    public OrigenClinicoValidator(ConsultaRepository consultaRepository,
                                  VacunaRepository vacunaRepository,
                                  CitaRepository citaRepository) {
        this.consultaRepository = consultaRepository;
        this.vacunaRepository = vacunaRepository;
        this.citaRepository = citaRepository;
    }

    /**
     * @param mascota    la mascota de la factura; {@code null} si la factura no
     *                   tiene contexto clinico.
     * @param origenTipo tipo de registro clinico, o {@code null} si la linea no
     *                   declara origen.
     * @param origenId   id del registro clinico, o {@code null}.
     */
    public void validar(Mascota mascota, OrigenDetalleFactura origenTipo, Long origenId) {
        if (origenTipo == null && origenId == null) {
            return;
        }
        // El par va junto o no va: un tipo sin id (o al reves) no identifica
        // nada y la BD lo aceptaria en silencio, porque ambas columnas son
        // nullables por separado.
        if (origenTipo == null || origenId == null) {
            throw new OrigenClinicoInvalidoException(
                    "El origen clinico debe informar tipo e id a la vez; se recibio tipo=" + origenTipo
                            + " e id=" + origenId + ".");
        }
        if (mascota == null) {
            throw new OrigenClinicoInvalidoException(
                    "La factura no tiene mascota, asi que ninguna linea puede declarar origen clinico "
                            + origenTipo + " " + origenId + ".");
        }

        switch (origenTipo) {
            case CONSULTA -> validarConsulta(mascota, origenId);
            case VACUNA -> validarVacuna(mascota, origenId);
            case CITA -> validarCita(mascota, origenId);
        }
    }

    private void validarConsulta(Mascota mascota, Long consultaId) {
        Consulta consulta = consultaRepository.findById(consultaId)
                .orElseThrow(() -> noExiste(OrigenDetalleFactura.CONSULTA, consultaId));
        exigirMismaMascota(mascota, consulta.getMascota().getId(), OrigenDetalleFactura.CONSULTA, consultaId);
    }

    private void validarVacuna(Mascota mascota, Long vacunaId) {
        Vacuna vacuna = vacunaRepository.findById(vacunaId)
                .orElseThrow(() -> noExiste(OrigenDetalleFactura.VACUNA, vacunaId));
        exigirMismaMascota(mascota, vacuna.getMascota().getId(), OrigenDetalleFactura.VACUNA, vacunaId);
        // El precio NO se lee de la vacuna: sale del ConceptoFacturable. La
        // vacuna solo dice que se aplico, no cuanto se cobra por ella.
    }

    private void validarCita(Mascota mascota, Long citaId) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> noExiste(OrigenDetalleFactura.CITA, citaId));
        exigirMismaMascota(mascota, cita.getMascota().getId(), OrigenDetalleFactura.CITA, citaId);

        // Una cita PROGRAMADA es una intencion, no una atencion prestada;
        // una CANCELADA no ocurrio. Cobrar por cualquiera de las dos seria
        // facturar algo que no se hizo.
        if (cita.getEstado() != EstadoCita.COMPLETADA) {
            throw new OrigenClinicoInvalidoException(
                    "La cita " + citaId + " esta " + cita.getEstado()
                            + " y solo puede facturarse una cita COMPLETADA.");
        }
    }

    private void exigirMismaMascota(Mascota mascota, Long mascotaDelOrigen,
                                    OrigenDetalleFactura tipo, Long origenId) {
        if (!mascota.getId().equals(mascotaDelOrigen)) {
            throw new OrigenClinicoInvalidoException(
                    "El origen " + tipo + " " + origenId + " pertenece a la mascota " + mascotaDelOrigen
                            + ", no a la mascota " + mascota.getId() + " de la factura.");
        }
    }

    private OrigenClinicoInvalidoException noExiste(OrigenDetalleFactura tipo, Long origenId) {
        return new OrigenClinicoInvalidoException(
                "El origen clinico " + tipo + " " + origenId + " no existe.");
    }
}
