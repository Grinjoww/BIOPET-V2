package com.biopet.audit.repository;

import com.biopet.audit.entity.AuditoriaEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {

    /**
     * usuarioEmail/accion/modulo: opcionales, mismo patron que
     * FacturaRepository.buscar ("(:param is null or campo = :param)") -
     * PostgreSQL infiere sin problema el tipo de un parametro VARCHAR nulo
     * en ese patron.
     *
     * <p>desde/hasta NO usan el mismo patron a proposito: con una columna
     * TIMESTAMPTZ, PostgreSQL no logra inferir el tipo de un parametro
     * ligado dos veces (una en "{@code :desde is null}", otra en
     * "{@code fecha_hora >= :desde}") cuando el valor es NULL -falla en
     * runtime con "ERROR: no se pudo determinar el tipo del parametro $N",
     * un problema real de PgJDBC que NUNCA aparece contra H2 (usado en los
     * tests de este modulo), solo se detecto al verificar contra PostgreSQL
     * real. Por eso AuditoriaService.buscar nunca pasa null aqui: sustituye
     * "sin filtro" por limites amplios pero siempre no-nulos (ver sus
     * constantes DESDE_POR_DEFECTO/HASTA_POR_DEFECTO), y esta consulta
     * compara directamente sin ningun "is null".
     */
    @Query("""
            select e from AuditoriaEvento e
            where (:usuarioEmail is null or e.usuarioEmail = :usuarioEmail)
              and (:accion is null or e.accion = :accion)
              and (:modulo is null or e.modulo = :modulo)
              and e.fechaHora >= :desde
              and e.fechaHora <= :hasta
            order by e.fechaHora desc, e.id desc
            """)
    Page<AuditoriaEvento> buscar(@Param("usuarioEmail") String usuarioEmail,
                                  @Param("accion") String accion,
                                  @Param("modulo") String modulo,
                                  @Param("desde") Instant desde,
                                  @Param("hasta") Instant hasta,
                                  Pageable pageable);

    /**
     * Alimentan los selects "Usuario", "Acción" y "Módulo" del filtro con los
     * valores REALMENTE presentes en la auditoria, no una lista hardcodeada
     * ni el listado completo de usuarios registrados (que incluiria cuentas
     * sin ningun evento).
     */
    @Query("select distinct e.usuarioEmail from AuditoriaEvento e where e.usuarioEmail is not null order by e.usuarioEmail")
    List<String> usuariosDistintos();

    @Query("select distinct e.accion from AuditoriaEvento e order by e.accion")
    List<String> accionesDistintas();

    @Query("select distinct e.modulo from AuditoriaEvento e order by e.modulo")
    List<String> modulosDistintos();
}
