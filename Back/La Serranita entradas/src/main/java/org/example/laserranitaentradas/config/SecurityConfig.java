package org.example.laserranitaentradas.config;

import jakarta.servlet.http.HttpServletResponse;
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
                .requestMatchers(HttpMethod.GET, "/api/tipos-entrada/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cupones/codigo/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/compras", "/api/compras/iniciar-pago", "/api/compras/cotizar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/compras/{id}", "/api/compras/{id}/estado").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/mercadopago/preferences").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pagos/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/ping").permitAll()

                // ---------- Boletería (BOLETERO o ADMIN) ----------
                .requestMatchers(HttpMethod.GET, "/api/compras/dni/**", "/api/compras/fecha/**").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/compras").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/compras/*/validar").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers("/api/interno/**").hasAnyRole("BOLETERO", "ADMIN")

                // ---------- Configuración (solo ADMIN) ----------
                .requestMatchers("/api/dias-apertura/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/tipos-entrada").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/tipos-entrada/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/tipos-entrada/**").hasRole("ADMIN")
                .requestMatchers("/api/cupones/**").hasRole("ADMIN")
                .requestMatchers("/api/descuentos-efectivo/**").hasRole("ADMIN")
                .requestMatchers("/api/configuracion/**").hasRole("ADMIN")
                .requestMatchers("/api/usuarios/**").hasRole("ADMIN")

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
        config.addAllowedOrigin("http://localhost:4200");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
