package org.example.laserranitaentradas.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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

    // Separada por comas. En producción la pisa CORS_ALLOWED_ORIGINS.
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
            // setStatus y no sendError: el forward a /error pierde los headers de CORS y el navegador no ve
            // el 401/403.
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
                .requestMatchers(HttpMethod.GET, "/api/dias-apertura/compra-de-hoy-cerrada").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/configuracion/horario").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/tipos-entrada/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cupones/codigo/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/compras", "/api/compras/iniciar-pago", "/api/compras/cotizar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/compras/opciones-pago").permitAll()
                // {id:\d+} y no {id}: si no matchea /api/compras/buscar y la deja abierta sin login.
                .requestMatchers(HttpMethod.GET, "/api/compras/{id:\\d+}", "/api/compras/{id:\\d+}/estado", "/api/compras/codigo/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/compras/{id:\\d+}/verificar-pago").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pagos/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/ping").permitAll()
                // Para el monitor externo y el healthcheck de Docker; no expone datos.
                .requestMatchers(HttpMethod.GET, "/api/salud").permitAll()
                // Actuator va en su propio puerto, sólo accesible desde la red de Docker.
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()
                // Errores de JavaScript del navegador, también sin login; el controller limita por IP.
                .requestMatchers(HttpMethod.POST, "/api/errores-cliente").permitAll()
                // El agente de impresión no tiene JWT: se autentica con su propio token en el controller.
                .requestMatchers("/api/impresion/agente/**").permitAll()

                // ---------- Sólo ADMIN, dentro del módulo interno ----------
                // Va antes que /api/interno/**: gana la primera regla que matchea.
                .requestMatchers(HttpMethod.POST, "/api/interno/compras/generar-reserva").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/caja/*/detalle").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/caja/*/operaciones").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/caja/abiertas").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/caja/cerradas").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/caja/cerradas/boleteros").hasRole("ADMIN")
                // Lo cobrado con el Point en una caja: es un total "esperado", como los del cierre.
                .requestMatchers(HttpMethod.GET, "/api/interno/point/caja/*/resumen").hasRole("ADMIN")
                // Configuración > Cobros.
                .requestMatchers(HttpMethod.GET, "/api/interno/point/terminales/actualizadas").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/interno/point/terminales/*/nombre", "/api/interno/point/terminales/*/habilitado",
                        "/api/interno/point/terminales/*/modo").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/interno/compras/*/cancelar-venta").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/interno/compras/*/editar-venta").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/compras/caja/*/venta-pos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/facturas/*/reintentar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/facturas/manual", "/api/interno/facturas/manual/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/facturas/padron/*").hasRole("ADMIN")
                .requestMatchers("/api/interno/facturas/control/**", "/api/interno/facturas/control").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/facturas/totales", "/api/interno/facturas/exportar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/interno/facturas/manuales").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/facturas/compra/*/online").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/facturas/*/anular").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/facturas/*/reenviar-mail").hasRole("ADMIN")
                // Devuelve plata real por la API de Mercado Pago: la UI ya lo muestra sólo al admin.
                .requestMatchers(HttpMethod.POST, "/api/interno/compras/*/reembolsar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/compras/*/cancelar-reserva").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/interno/compras/*/fecha").hasRole("ADMIN")
                // Cerrar y corregir caja: sólo ADMIN.
                .requestMatchers(HttpMethod.POST, "/api/interno/caja/*/cerrar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/caja/*/cerrar-sin-control").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/caja/*/retiros").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/caja/*/ingresos-entradas").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/interno/caja/*/correccion").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/interno/caja/*/deshabilitar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/interno/caja/*/ajustes/*").hasRole("ADMIN")
                // Vender sin la apertura formal de caja: sólo para cuando el propio ADMIN vende.
                .requestMatchers(HttpMethod.POST, "/api/interno/caja/abrir-sin-control").hasRole("ADMIN")
                // Operaciones rechazadas: se resuelven desde Sistema, junto con el resto del monitoreo.
                .requestMatchers("/api/interno/rechazos/**").hasRole("SUPERADMIN")
                .requestMatchers("/api/interno/notificaciones/**").hasRole("ADMIN")
                // Monitoreo: sólo el que mantiene la app, no el admin del parque.
                .requestMatchers("/api/interno/sistema/**").hasRole("SUPERADMIN")

                // ---------- Boletería (BOLETERO o ADMIN) ----------
                // Antes que /api/usuarios/**: cualquier usuario cambia su contraseña o su tema.
                .requestMatchers(HttpMethod.POST, "/api/usuarios/renovar-sesion").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/usuarios/me/password").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/usuarios/me/tema").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/usuarios/me/foto").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/compras/buscar").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/compras/*/validar").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/compras/*/deshacer-validacion").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers("/api/interno/**").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/promociones/**").hasAnyRole("BOLETERO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/articulos-varios/**").hasAnyRole("BOLETERO", "ADMIN")
                // El POS los cachea para el precio de grupo sin conexión. Editarlos sigue siendo ADMIN.
                .requestMatchers(HttpMethod.GET, "/api/descuentos-efectivo/**").hasAnyRole("BOLETERO", "ADMIN")

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

    /** SUPERADMIN incluye ADMIN y ADMIN incluye BOLETERO: las reglas nombran el rol mínimo. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPERADMIN > ROLE_ADMIN
                ROLE_ADMIN > ROLE_BOLETERO
                """);
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
