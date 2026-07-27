package org.example.laserranitaentradas.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.laserranitaentradas.model.entity.RolUsuario;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Optional<Claims> claims = jwtService.validarYExtraerClaims(token);

            if (claims.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
                autenticar(claims.get());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(Claims claims) {
        String rolClaim = claims.get("rol", String.class);
        RolUsuario rol;
        try {
            rol = RolUsuario.valueOf(rolClaim);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Token con un rol que ya no existe: se deja pasar sin autenticar y
            // las reglas de autorización lo rechazan como si no hubiera token.
            return;
        }

        UsuarioAutenticado usuario = new UsuarioAutenticado(
                claims.get("id", Long.class),
                claims.getSubject(),
                rol
        );

        var authentication = new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
