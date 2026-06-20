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

    @Bean
    public AuditorAware<String> auditorAware() {
        return new AuditorAware<String>() {
            @Override
            public Optional<String> getCurrentAuditor() {
                try {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                    if (authentication != null && authentication.isAuthenticated()) {
                        // TODO: Obtener el nombre/ID del usuario autenticado
                        Object principal = authentication.getPrincipal();
                        if (principal instanceof String) {
                            return Optional.of((String) principal);
                        }
                    }
                } catch (Exception e) {
                    // Si hay error en seguridad, usa usuario por defecto
                }

                return Optional.of("SISTEMA");
            }
        };
    }

}
