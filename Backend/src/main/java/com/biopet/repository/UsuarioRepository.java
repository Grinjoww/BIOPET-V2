package com.biopet.repository;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailAndActivoTrue(String email);
    boolean existsByEmail(String email);
    Page<Usuario> findAllByActivoTrue(Pageable pageable);
    Optional<Usuario> findByIdAndActivoTrue(Long id);

    /**
     * Para selectores de solo lectura (ver UsuarioService.listarDuenios() /
     * listarVeterinarios()). El {@code Rol} lo fija siempre el Service con una
     * constante; nunca debe derivarse de un parámetro controlado por el cliente.
     */
    List<Usuario> findAllByRolAndActivoTrueOrderByNombreAsc(Rol rol);
}
