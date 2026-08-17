package com.biopet.repository;

import com.biopet.entity.Mascota;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio dedicado a la invocacion formal (JPA StoredProcedure) de los
 * procedimientos/funciones almacenados de BIOPET definidos en db/procs/.
 * No expone metodos CRUD: solo las 6 invocaciones (F02).
 */
public interface ProcedimientoBiopetRepository extends Repository<Mascota, Long> {

    @Procedure(procedureName = "fn_resumen_mascotas_por_especie")
    @Transactional(readOnly = true)
    List<ResumenEspecie> resumenPorEspecie(@Param("p_duenio_id") Long duenioId);

    @Procedure(procedureName = "fn_historial_clinico_mascota")
    @Transactional(readOnly = true)
    List<HistorialClinico> historialClinicoMascota(@Param("p_mascota_id") Long mascotaId);

    @Procedure(procedureName = "fn_reporte_dashboard")
    @Transactional(readOnly = true)
    List<ReporteDashboard> reporteDashboard(@Param("p_desde") LocalDate desde,
                                            @Param("p_hasta") LocalDate hasta);

    @Procedure(procedureName = "fn_siguiente_numero_ficha", outputParameterName = "p_codigo")
    String siguienteNumeroFicha(@Param("p_prefijo") String prefijo);

    @Procedure(procedureName = "sp_actualizar_estado_citas_masivas", outputParameterName = "p_afectadas")
    Long actualizarEstadoCitasMasivas(@Param("p_veterinario_id") Long veterinarioId,
                                      @Param("p_estado_anterior") String estadoAnterior,
                                      @Param("p_estado_nuevo") String estadoNuevo,
                                      @Param("p_fecha_limite") Instant fechaLimite);

    @Procedure(procedureName = "sp_registrar_consulta_validada", outputParameterName = "p_consulta_id")
    Long registrarConsultaValidada(@Param("p_mascota_id") Long mascotaId,
                                   @Param("p_veterinario_id") Long veterinarioId,
                                   @Param("p_motivo") String motivo,
                                   @Param("p_diagnostico") String diagnostico,
                                   @Param("p_tratamiento") String tratamiento,
                                   @Param("p_observaciones") String observaciones);
}
