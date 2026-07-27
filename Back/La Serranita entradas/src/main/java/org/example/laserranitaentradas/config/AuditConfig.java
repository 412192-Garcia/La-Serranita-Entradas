package org.example.laserranitaentradas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class AuditConfig {

    /** Compras y consultas del sitio público se hacen sin sesión. */
    private static final String AUDITOR_ANONIMO = "SISTEMA";

    /**
     * Quién queda registrado en los campos de auditoría de las entidades.
     * Cuando la request trae un JWT válido, el filtro deja un UsuarioAutenticado
     * como principal y se usa su username.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null
                    && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof UsuarioAutenticado usuario) {
                return Optional.of(usuario.username());
            }

            return Optional.of(AUDITOR_ANONIMO);
        };
    }

}
