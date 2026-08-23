package com.biopet.config;

import com.biopet.entity.Rol;
import com.biopet.entity.Usuario;
import com.biopet.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * B-1: bootstrap opcional del primer usuario ADMIN. Ya NO crea
 * "admin@biopet.ec" con una contraseña hardcodeada -esa contraseña quedaba
 * públicamente conocida en el repositorio y en cualquier BD nueva quedaba con
 * una cuenta ADMIN adivinable desde el día uno.
 *
 * <p>Comportamiento (ver application.yml, app.admin-seed.*):
 * <ul>
 *   <li>{@code ADMIN_SEED_ENABLED} (default {@code false}): si es false, este
 *   runner no hace nada -ninguna cuenta se crea. Pensado para quedar
 *   deshabilitado en el dia a dia, tanto en desarrollo como en produccion
 *   una vez que el primer ADMIN ya existe.</li>
 *   <li>{@code ADMIN_SEED_EMAIL} / {@code ADMIN_SEED_PASSWORD}: obligatorias
 *   si el seed esta habilitado. Sin fallback -ningun valor conocido vive en
 *   el codigo (mismo criterio que spring.datasource.password /
 *   security.jwt.secret en application.yml). Si el seed esta habilitado y
 *   falta alguna, el arranque falla con un mensaje claro: no se crea un
 *   admin con contraseña vacia ni con un valor por defecto conocido.</li>
 *   <li>{@code ADMIN_SEED_NAME} (opcional): nombre de despliegue del admin
 *   semilla.</li>
 * </ul>
 *
 * <p>Idempotente: si ya existe un usuario con {@code ADMIN_SEED_EMAIL}, este
 * runner no hace nada -ni lo duplica ni le resetea la contraseña en cada
 * restart. El seed sirve para el bootstrap de una BD nueva, no para
 * sobrescribir una cuenta ya creada.
 */
@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedAdmin(
            UsuarioRepository repo,
            PasswordEncoder enc,
            @Value("${app.admin-seed.enabled:false}") boolean seedHabilitado,
            @Value("${app.admin-seed.email:}") String email,
            @Value("${app.admin-seed.password:}") String password,
            @Value("${app.admin-seed.name:Administrador BIOPET}") String nombre
    ) {
        return args -> {
            if (!seedHabilitado) {
                return;
            }
            if (email == null || email.isBlank()) {
                throw new IllegalStateException(
                        "ADMIN_SEED_ENABLED=true pero falta ADMIN_SEED_EMAIL. "
                                + "Definí ambas variables antes de arrancar, o deshabilitá el seed "
                                + "(ADMIN_SEED_ENABLED=false).");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException(
                        "ADMIN_SEED_ENABLED=true pero falta ADMIN_SEED_PASSWORD. "
                                + "No existe una contraseña por defecto: definila antes de arrancar, "
                                + "o deshabilitá el seed (ADMIN_SEED_ENABLED=false).");
            }
            if (repo.existsByEmail(email)) {
                return;
            }
            repo.save(Usuario.builder()
                    .nombre(nombre)
                    .email(email)
                    .passwordHash(enc.encode(password))
                    .rol(Rol.ROLE_ADMIN)
                    .activo(true)
                    .build());
        };
    }
}
