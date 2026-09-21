package org.example.laserranitaentradas.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtService {

    private static final String CLAIM_SESION_LARGA = "mantener";

    private final SecretKey clave;
    private final long expiracionMs;
    private final long expiracionMantenerSesionMs;

    /**
     * @param expiracionMs                duración de un token común (por defecto 12 h: un turno).
     * @param expiracionMantenerSesionMs  duración cuando el usuario marca "Mantener sesión iniciada"
     *                                    (por defecto 14 días). Ojo: el token es sin estado, no se
     *                                    puede revocar antes de que venza — dar de baja a un usuario o
     *                                    cambiarle el rol/contraseña no lo saca hasta ese momento.
     */
    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiration-ms}") long expiracionMs,
                      @Value("${jwt.remember-expiration-ms:1209600000}") long expiracionMantenerSesionMs) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMs = expiracionMs;
        this.expiracionMantenerSesionMs = expiracionMantenerSesionMs;
    }

    public String generarToken(Usuario usuario) {
        return generarToken(usuario, false);
    }

    public String generarToken(Usuario usuario, boolean mantenerSesion) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + (mantenerSesion ? expiracionMantenerSesionMs : expiracionMs));

        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("id", usuario.getId())
                .claim("rol", usuario.getRol().name())
                // Marca los tokens de "Mantener sesión iniciada": son los únicos que se pueden renovar.
                .claim(CLAIM_SESION_LARGA, mantenerSesion)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(clave)
                .compact();
    }

    /** El token se emitió con "Mantener sesión iniciada" (duración larga, renovable). */
    public boolean esSesionLarga(Claims claims) {
        return Boolean.TRUE.equals(claims.get(CLAIM_SESION_LARGA, Boolean.class));
    }

    public Optional<Claims> validarYExtraerClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
