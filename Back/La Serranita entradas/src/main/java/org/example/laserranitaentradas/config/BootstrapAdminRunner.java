package org.example.laserranitaentradas.config;

import lombok.extern.slf4j.Slf4j;
import org.example.laserranitaentradas.model.entity.RolUsuario;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * En producción data.sql no corre (son credenciales de desarrollo), así que la base
 * arranca sin ningún usuario y nadie podría loguearse para crear al resto. Este runner
 * crea el primer ADMIN una única vez, sólo si todavía no existe ningún usuario y vienen
 * seteadas las env vars de bootstrap — así se autodesactiva solo después del primer
 * arranque real (usuarioRepository.count() > 0 en cualquier arranque posterior).
 *
 * Aparte, con BOOTSTRAP_SUPERADMIN_USERNAME/PASSWORD crea el usuario SUPERADMIN (soporte de la app,
 * el único que ve Sistema) si todavía no hay ningún usuario con ese nombre, aunque la base ya
 * tenga usuarios: es la forma de dar de alta el primero en una instalación que ya está andando
 * (desde Usuarios no se puede asignar ese rol).
 */
@Slf4j
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${BOOTSTRAP_ADMIN_USERNAME:}")
    private String bootstrapUsername;

    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}")
    private String bootstrapPassword;

    @Value("${BOOTSTRAP_SUPERADMIN_USERNAME:}")
    private String superadminUsername;

    @Value("${BOOTSTRAP_SUPERADMIN_PASSWORD:}")
    private String superadminPassword;

    public BootstrapAdminRunner(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Primero el admin: si fuera al revés, el superadmin ya contaría como "hay usuarios".
        crearAdminInicial();
        crearSuperadmin();
    }

    private void crearAdminInicial() {
        if (bootstrapUsername.isBlank() || bootstrapPassword.isBlank()) {
            return;
        }
        if (usuarioRepository.count() > 0) {
            return;
        }

        Usuario admin = Usuario.builder()
                .username(bootstrapUsername)
                .password(passwordEncoder.encode(bootstrapPassword))
                .nombre("Admin")
                .apellido("Inicial")
                .rol(RolUsuario.ADMIN)
                .activo(true)
                .build();
        usuarioRepository.save(admin);

        log.warn("Se creó el usuario ADMIN inicial '{}' a partir de BOOTSTRAP_ADMIN_USERNAME/PASSWORD. " +
                "Entrá, creá los usuarios reales desde Configuración > Usuarios, y sacá esas dos variables " +
                "de entorno del hosting (ya cumplieron su función; dejarlas no hace nada más porque este " +
                "runner no vuelve a correr, pero es buena práctica no dejarlas con la contraseña en texto plano).",
                bootstrapUsername);
    }

    private void crearSuperadmin() {
        if (superadminUsername.isBlank() || superadminPassword.isBlank()) return;
        if (usuarioRepository.findByUsername(superadminUsername).isPresent()) return;
        usuarioRepository.save(Usuario.builder()
                .username(superadminUsername)
                .password(passwordEncoder.encode(superadminPassword))
                .nombre("Soporte")
                .apellido("Técnico")
                .rol(RolUsuario.SUPERADMIN)
                .activo(true)
                .build());
        log.warn("Se creó el usuario SUPERADMIN '{}' a partir de BOOTSTRAP_SUPERADMIN_USERNAME/PASSWORD. "
                + "Entrá, cambiá la contraseña desde tu perfil y sacá esas variables del .env.", superadminUsername);
    }
}
