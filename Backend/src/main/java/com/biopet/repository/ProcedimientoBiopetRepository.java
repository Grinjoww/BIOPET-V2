package com.biopet.repository;

import com.biopet.entity.Mascota;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio dedicado a la invocacion formal (JPA) de las rutinas
 * almacenadas de BIOPET definidas en db/procs/ y replicadas en la migracion
 * V5. No expone metodos CRUD: solo las 6 invocaciones (F02), las 6 con
 * {@code @Procedure}.
 *
 * <p>Spring Data JPA / Hibernate generan siempre una sentencia {@code CALL}
 * para {@code @Procedure}, y PostgreSQL solo acepta {@code CALL} sobre
 * objetos {@code PROCEDURE} (nunca sobre {@code FUNCTION}, ni siquiera con
 * parametros {@code OUT}: verificacion real F03, PostgreSQL responde
 * "... is not a procedure. Hint: To call a function, use SELECT"). Por eso
 * las 4 rutinas {@code fn_*}, que originalmente eran {@code FUNCTION}
 * ({@code RETURNS TABLE} / OUT escalar directo, invocadas con
 * {@code @Query(nativeQuery = true)}), se reclasificaron a {@code PROCEDURE}
 * conservando su nombre {@code fn_...}.
 *
 * <p>Las 3 rutinas que devuelven un conjunto de filas
 * ({@code resumenPorEspecie}, {@code historialClinicoMascota},
 * {@code reporteDashboard}) exponen un unico {@code OUT refcursor} (un
 * {@code PROCEDURE} no admite {@code RETURNS TABLE}) y se invocan con
 * {@code @Procedure(name = "...")} referenciando un
 * {@code @NamedStoredProcedureQuery} declarado en {@link Mascota} con
 * {@code ParameterMode.REF_CURSOR} explicito: probar {@code @Procedure} sin
 * un {@code @NamedStoredProcedureQuery} (parametros auto-derivados de los
 * metadatos JDBC) genera una sentencia {@code CALL} que omite el
 * placeholder del parametro {@code refcursor}, que PostgreSQL rechaza con
 * {@code procedure ... does not exist} (verificacion real F03) por
 * discordancia de aridad. El cursor que abre {@code OPEN p_cursor FOR
 * SELECT ...} solo es legible dentro de la misma transaccion en la que se
 * abre; por eso estos 3 metodos NO declaran su propio
 * {@code @Transactional} (una transaccion iniciada por el propio proxy del
 * repositorio no es reconocida como "transaccion circundante" por Spring
 * Data y falla con {@code InvalidDataAccessApiUsageException}) sino que
 * dependen de que el codigo llamador ya este dentro de una transaccion —
 * como ya lo esta {@code MascotaService.resumenPorEspecie}
 * (@Transactional(readOnly = true)), y como declaran explicitamente los
 * tests de integracion que los ejercitan.
 *
 * <p>{@code siguienteNumeroFicha} ya devolvia un escalar, por lo que su
 * {@code PROCEDURE} usa {@code OUT p_codigo VARCHAR} directo (sin
 * refcursor, sin @NamedStoredProcedureQuery, sin requisito de transaccion
 * circundante), igual que los 2 {@code sp_*} (que ya eran {@code PROCEDURE}
 * y no cambiaron).
 */
public interface ProcedimientoBiopetRepository extends Repository<Mascota, Long> {

    @Procedure(name = "fn_resumen_mascotas_por_especie")
    List<ResumenEspecie> resumenPorEspecie(Long duenioId);

    @Procedure(name = "fn_historial_clinico_mascota")
    List<HistorialClinico> historialClinicoMascota(Long mascotaId);

    @Procedure(name = "fn_reporte_dashboard")
    List<ReporteDashboard> reporteDashboard(LocalDate desde, LocalDate hasta);

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
