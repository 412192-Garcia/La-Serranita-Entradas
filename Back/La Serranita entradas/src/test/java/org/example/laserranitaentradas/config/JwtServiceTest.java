package org.example.laserranitaentradas.config;

import io.jsonwebtoken.Claims;
import org.example.laserranitaentradas.model.entity.RolUsuario;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRETO = "una-clave-de-prueba-de-al-menos-32-bytes-de-largo";
    private static final long TURNO_MS = 12L * 60 * 60 * 1000;
    private static final long MANTENER_MS = 14L * 24 * 60 * 60 * 1000;

    private final JwtService jwtService = new JwtService(SECRETO, TURNO_MS, MANTENER_MS);

    private Usuario boletero() {
        return Usuario.builder().id(7L).username("boletero1").rol(RolUsuario.BOLETERO).build();
    }

    private long duracionMs(String token) {
        Claims claims = jwtService.validarYExtraerClaims(token).orElseThrow();
        return claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
    }

    @Test
    void sinMantenerSesionElTokenDuraUnTurno() {
        // Los Date de JWT van en segundos: se permite 1 s de redondeo.
        assertTrue(Math.abs(duracionMs(jwtService.generarToken(boletero(), false)) - TURNO_MS) <= 1000);
    }

    @Test
    void conMantenerSesionElTokenDuraDias() {
        assertTrue(Math.abs(duracionMs(jwtService.generarToken(boletero(), true)) - MANTENER_MS) <= 1000);
    }

    @Test
    void laFirmaSinFlagEsLaDeSiempre() {
        assertTrue(Math.abs(duracionMs(jwtService.generarToken(boletero())) - TURNO_MS) <= 1000);
    }

    @Test
    void elTokenLargoConservaLosClaimsDeRolEId() {
        Claims claims = jwtService.validarYExtraerClaims(jwtService.generarToken(boletero(), true)).orElseThrow();
        assertEquals("BOLETERO", claims.get("rol", String.class));
        assertEquals(7L, claims.get("id", Long.class));
    }
}
