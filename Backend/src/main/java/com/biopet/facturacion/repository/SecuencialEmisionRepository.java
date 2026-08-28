package com.biopet.facturacion.repository;

import com.biopet.facturacion.domain.AmbienteSri;
import com.biopet.facturacion.entity.SecuencialEmision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso al contador fiscal.
 */
public interface SecuencialEmisionRepository extends JpaRepository<SecuencialEmision, Long> {

    /** Lectura normal, sin bloqueo. Para consultar el estado del contador. */
    Optional<SecuencialEmision> findByPuntoEmision_IdAndAmbiente(Long puntoEmisionId, AmbienteSri ambiente);

    /**
     * Carga el contador de {@code (punto, ambiente)} tomando un bloqueo
     * pesimista de escritura sobre ESA fila, y solo esa.
     *
     * <p>{@code PESSIMISTIC_WRITE} se traduce en el dialecto de PostgreSQL a un
     * {@code SELECT ... FOR UPDATE}: la segunda transaccion que pida la misma
     * fila se queda esperando en la base de datos hasta que la primera confirme
     * o deshaga. El arbitro es PostgreSQL, no la JVM, que es justo lo que hace
     * falta: {@code synchronized} o un {@code AtomicLong} solo ordenarian los
     * hilos de UNA instancia y dejarian de servir en cuanto haya dos replicas
     * del backend, un despliegue solapado o un job aparte.
     *
     * <p>La consulta se escribe a mano en lugar de derivarla del nombre por dos
     * motivos concretos:
     *
     * <ul>
     *   <li>{@code s.puntoEmision.id} navega la clave ajena SIN generar un JOIN
     *       contra {@code punto_emision}. Importa: con un JOIN, el
     *       {@code FOR UPDATE} alcanzaria tambien a la fila del punto de
     *       emision y dos ambientes del mismo punto se serializarian entre si
     *       sin necesidad. Hay un test que comprueba en {@code pg_locks} que
     *       solo se bloquea {@code secuencial_emision}.</li>
     *   <li>el nombre {@code bloquear...} deja claro en cada uso que esa llamada
     *       tiene efectos de bloqueo y exige una transaccion activa.</li>
     * </ul>
     *
     * <p>Devuelve {@code Optional} vacio si el par no esta configurado; quien
     * llama decide que hacer, y {@code SecuencialService} lo trata como error de
     * configuracion, nunca creando la fila al vuelo.
     *
     * <p>Debe invocarse siempre dentro de una transaccion. Sin ella, el bloqueo
     * se liberaria de inmediato y no serviria de nada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SecuencialEmision s "
            + "where s.puntoEmision.id = :puntoEmisionId and s.ambiente = :ambiente")
    Optional<SecuencialEmision> bloquearPorPuntoEmisionYAmbiente(
            @Param("puntoEmisionId") Long puntoEmisionId,
            @Param("ambiente") AmbienteSri ambiente);
}
