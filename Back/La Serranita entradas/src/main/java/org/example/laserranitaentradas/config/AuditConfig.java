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
    public AuditorAware<Long> auditorAware() {
        return new AuditorAware<Long>() {
            @Override
            public Optional<Long> getCurrentAuditor() {
                try {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                    if (authentication != null && authentication.isAuthenticated()) {
                        // TODO: Obtener el ID de usuario del principal autenticado
                        // Por ahora retorna 1 por defecto (Usuario SISTEMA)
                        Object principal = authentication.getPrincipal();
                        if (principal instanceof String) {
                            return Optional.of(1L);
                        }
                    }
                } catch (Exception e) {
                    // Si hay error en seguridad, usa usuario por defecto
                }

                return Optional.of(1L);
            }
        };
    }

}

