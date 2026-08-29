package com.biopet.facturacion.service;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.exception.RecursoNoEncontradoException;
import com.biopet.facturacion.dto.TarifaImpuestoRequest;
import com.biopet.facturacion.dto.TarifaImpuestoResponse;
import com.biopet.facturacion.entity.TarifaImpuesto;
import com.biopet.facturacion.exception.ConfiguracionFiscalInvalidaException;
import com.biopet.facturacion.repository.TarifaImpuestoRepository;
import com.biopet.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Configuracion de tarifas de impuesto para la Fase 8B.
 *
 * <p>{@code tarifa_impuesto} es HISTORICA a proposito (ver el javadoc de la
 * entidad): esta fase NUNCA sobrescribe una fila ya insertada. Solo expone dos
 * escrituras:
 * <ul>
 *   <li>{@link #crear} abre una vigencia NUEVA y, si existe una vigencia
 *       ABIERTA anterior para el mismo par (codigo de impuesto, codigo de
 *       porcentaje), la cierra automaticamente en la fecha anterior a la nueva
 *       -nunca toca una fila que YA estuviese cerrada, es decir, ya
 *       historica-. Esto mantiene disjuntos los periodos de vigencia, la
 *       misma invariante de la que depende {@link TarifaImpuestoResolver} para
 *       no lanzar {@code TarifaImpuestoAmbiguaException} en produccion.</li>
 *   <li>{@link #cambiarEstado} es baja logica pura: apaga/enciende
 *       {@code activo} sin tocar tarifa, vigencia ni ningun otro campo.</li>
 * </ul>
 *
 * <p>No hay un {@code PUT} de edicion general: no existe una operacion segura
 * para "editar una tarifa" que no sea, en realidad, una de las dos de arriba.
 *
 * <p>Lectura: ADMIN ve todo (vigente e historico); AUXILIAR solo las activas.
 * Ningun otro rol tiene acceso -no hay una necesidad clara identificada para
 * VETERINARIO/DUENO, ver el informe de la Fase 8B-.
 */
@Service
public class TarifaImpuestoService {

    private final TarifaImpuestoRepository tarifaImpuestoRepository;
    private final UsuarioRepository usuarioRepository;

    public TarifaImpuestoService(TarifaImpuestoRepository tarifaImpuestoRepository,
                                 UsuarioRepository usuarioRepository) {
        this.tarifaImpuestoRepository = tarifaImpuestoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<TarifaImpuestoResponse> listar(String email) {
        Usuario usuario = usuarioActual(email);
        List<TarifaImpuesto> tarifas = usuario.getRol() == Rol.ROLE_ADMIN
                ? tarifaImpuestoRepository.findAll()
                : tarifaImpuestoRepository.findAllByActivoTrue();
        return tarifas.stream()
                .sorted(Comparator.comparing(TarifaImpuesto::getVigenteDesde).reversed())
                .map(TarifaImpuestoService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TarifaImpuestoResponse buscar(Long id) {
        return toResponse(tarifaPorId(id));
    }

    @Transactional
    public TarifaImpuestoResponse crear(TarifaImpuestoRequest request) {
        Optional<TarifaImpuesto> abierta = tarifaImpuestoRepository
                .findByCodigoImpuestoAndCodigoPorcentajeAndVigenteHastaIsNull(
                        request.codigoImpuesto(), request.codigoPorcentaje());

        if (abierta.isPresent()) {
            TarifaImpuesto anterior = abierta.get();
            if (!request.vigenteDesde().isAfter(anterior.getVigenteDesde())) {
                throw new ConfiguracionFiscalInvalidaException(
                        "Ya existe una vigencia abierta para el impuesto " + request.codigoImpuesto().codigo()
                                + " con codigo de porcentaje " + request.codigoPorcentaje() + " desde "
                                + anterior.getVigenteDesde() + ". La nueva vigencia debe iniciar despues de esa "
                                + "fecha.");
            }
            // Cierra la vigencia abierta anterior EXACTAMENTE donde empieza la
            // nueva, nunca solapando: la fila cerrada nunca fue tocada de otra
            // forma, solo se le fija el limite que faltaba.
            anterior.setVigenteHasta(request.vigenteDesde().minusDays(1));
            tarifaImpuestoRepository.save(anterior);
        }

        TarifaImpuesto nueva = TarifaImpuesto.builder()
                .codigoImpuesto(request.codigoImpuesto())
                .codigoPorcentaje(request.codigoPorcentaje())
                .descripcion(request.descripcion().trim())
                .tarifa(request.tarifa())
                .vigenteDesde(request.vigenteDesde())
                .vigenteHasta(null)
                .activo(true)
                .build();
        return toResponse(tarifaImpuestoRepository.save(nueva));
    }

    /** Alta/baja logica exclusivamente: nunca toca tarifa, codigo ni vigencia. */
    @Transactional
    public TarifaImpuestoResponse cambiarEstado(Long id, boolean activo) {
        TarifaImpuesto tarifa = tarifaPorId(id);
        tarifa.setActivo(activo);
        return toResponse(tarifaImpuestoRepository.save(tarifa));
    }

    private TarifaImpuesto tarifaPorId(Long id) {
        return tarifaImpuestoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la tarifa de impuesto con id " + id + "."));
    }

    private Usuario usuarioActual(String email) {
        return usuarioRepository.findByEmailAndActivoTrue(email)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + email));
    }

    private static TarifaImpuestoResponse toResponse(TarifaImpuesto t) {
        return new TarifaImpuestoResponse(
                t.getId(), t.getCodigoImpuesto(), t.getCodigoPorcentaje(), t.getDescripcion(), t.getTarifa(),
                t.getVigenteDesde(), t.getVigenteHasta(), t.isActivo());
    }
}
