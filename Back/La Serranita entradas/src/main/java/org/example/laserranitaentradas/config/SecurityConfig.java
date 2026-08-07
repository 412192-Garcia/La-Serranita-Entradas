package org.example.laserranitaentradas.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // Lista separada por comas: en dev es sólo el ng serve local; en producción se
    // pisa con la env var CORS_ALLOWED_ORIGINS apuntando al dominio real del front.
    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String corsAllowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/**"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Se usa setStatus (no sendError) para no disparar el forward interno a /error:
            // ese forward vuelve a pasar por el filtro de CORS y, al perder los headers,
            // el navegador lo reporta como fallo de CORS en vez de exponer el 401/403 real.
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, authException) -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, accessDeniedException) -> response.setStatus(HttpServletResponse.SC_FORBIDDEN)))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ---------- Storefront público (compra de entradas online) ----------
                .requestMatchers(HttpMethod.POST, "/api/usuarios/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/dias-apertura/abiertos").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/dias-apertura/ultima-abierta").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tipos-entrada/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cupones/codigo/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/compras", "/api/compras/iniciar-pago", "/api/compras/cotizar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/compras/{id}", "/api/compras/{id}/estado").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/compras/{id}/verificar-pago").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/mercadopago/preferences").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pagos/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/ping").permitAll()

                // ---------- Sólo ADMIN, dentro del módulo interno ----------
                // Va antes que la regla general de /api/interno/** (más abajo): Spring Security
                // usa la primera regla que matchea, así que el orden acá importa.
                .requestMatchers(HttpMethod.POST, "/api/interno/compras/generar-reserva").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/caja/*/detalle").hasRole("ADMIN")

                // ---------- Boletería (BOLETERO o ADMIN) ----------
                .requestMatchers(HttpMethod.GET, "/api/compras/buscar").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/compras/*/validar").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/compras/*/deshacer-validacion").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers("/api/interno/**").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/promociones/**").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/articulos-varios/**").hasAnyRole("BOLETERO", "ADMIN")

                // ---------- Configuración (solo ADMIN) ----------
                .requestMatchers("/api/dias-apertura/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/tipos-entrada").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/tipos-entrada/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/tipos-entrada/**").hasRole("ADMIN")
                .requestMatchers("/api/cupones/**").hasRole("ADMIN")
                .requestMatchers("/api/descuentos-efectivo/**").hasRole("ADMIN")
                .requestMatchers("/api/configuracion/**").hasRole("ADMIN")
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                .requestMatchers("/api/reportes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/promociones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/promociones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/promociones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/articulos-varios/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/articulos-varios/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/articulos-varios/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : corsAllowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                config.addAllowedOrigin(trimmed);
            }
        }
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
