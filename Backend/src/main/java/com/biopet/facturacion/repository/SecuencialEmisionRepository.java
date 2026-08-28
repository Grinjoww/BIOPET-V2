package com.biopet.facturacion.repository;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.SecuencialEmision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso al contador fiscal.
 *
 * <p><b>Deliberadamente sin bloqueo.</b> Aqui NO hay
 * {@code @Lock(PESSIMISTIC_WRITE)} ni ninguna consulta que reserve el siguiente
 * numero. La reserva concurrente del secuencial es Fase 4B: anadirla ahora, a
 * medias y sin las pruebas de concurrencia que la respalden, seria peor que no
 * tenerla, porque daria la impresion de que el problema ya esta resuelto.
 *
 * <p>El unico metodo que se necesita en esta fase es la busqueda por la clave
 * de negocio (punto de emision + ambiente), que es la que soporta el indice
 * unico {@code uq_secuencial_emision_punto_ambiente}.
 */
public interface SecuencialEmisionRepository extends JpaRepository<SecuencialEmision, Long> {

    Optional<SecuencialEmision> findByPuntoEmision_IdAndAmbiente(Long puntoEmisionId, AmbienteSri ambiente);
}
