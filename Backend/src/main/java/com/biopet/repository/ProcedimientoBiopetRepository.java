package com.biopet.repository;

import com.biopet.entity.Mascota;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio dedicado a la invocacion formal (JPA) de las rutinas
 * almacenadas de BIOPET definidas en db/procs/ y replicadas en la migracion
 * V5. No expone metodos CRUD: solo las 6 invocaciones (F02).
 *
 * <p>PostgreSQL distingue entre funciones y procedimientos: las funciones
 * (RETURNS TABLE / OUT escalar) se invocan con SELECT y los procedimientos
 * con CALL. Por eso las 4 rutinas fn_* se invocan con @Query nativa y las
 * 2 rutinas sp_* con @Procedure (Spring Data genera {call ...}).
 */
public interface ProcedimientoBiopetRepository extends Repository<Mascota, Long> {

    @Query(value = "SELECT * FROM fn_resumen_mascotas_por_especie(:p_duenio_id)", nativeQuery = true)
    List<ResumenEspecie> resumenPorEspecie(@Param("p_duenio_id") Long duenioId);

    @Query(value = "SELECT * FROM fn_historial_clinico_mascota(:p_mascota_id)", nativeQuery = true)
    List<HistorialClinico> historialClinicoMascota(@Param("p_mascota_id") Long mascotaId);

    @Query(value = "SELECT * FROM fn_reporte_dashboard(:p_desde, :p_hasta)", nativeQuery = true)
    List<ReporteDashboard> reporteDashboard(@Param("p_desde") LocalDate desde,
                                            @Param("p_hasta") LocalDate hasta);

    @Query(value = "SELECT p_codigo FROM fn_siguiente_numero_ficha(:p_prefijo)", nativeQuery = true)
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
