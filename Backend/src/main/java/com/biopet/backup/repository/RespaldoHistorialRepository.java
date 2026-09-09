package com.biopet.backup.repository;

import com.biopet.backup.entity.EstadoRespaldo;
import com.biopet.backup.entity.RespaldoHistorial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RespaldoHistorialRepository extends JpaRepository<RespaldoHistorial, Long> {

    Page<RespaldoHistorial> findAllByOrderByIniciadoEnDesc(Pageable pageable);

    /**
     * Guarda defensiva de concurrencia: ademas del AtomicBoolean en memoria
     * (que basta dentro del mismo proceso), esta consulta detecta una fila
     * EN_PROCESO que haya quedado huerfana por un reinicio/caida del
     * backend a mitad de un respaldo -el AtomicBoolean nace en false en
     * cada arranque, pero la fila en la base seguiria diciendo lo
     * contrario.
     */
    boolean existsByEstado(EstadoRespaldo estado);
}
