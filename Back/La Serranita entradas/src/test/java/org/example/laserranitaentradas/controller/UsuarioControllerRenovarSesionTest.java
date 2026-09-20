package org.example.laserranitaentradas.controller;

import org.example.laserranitaentradas.config.JwtService;
import org.example.laserranitaentradas.config.UsuarioAutenticado;
import org.example.laserranitaentradas.model.dto.LoginResponseDTO;
import org.example.laserranitaentradas.model.entity.RolUsuario;
import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Renovación de la sesión de "Mantener sesión iniciada": sólo renueva los tokens largos, y
 * vuelve a mirar al usuario en la base — es lo que evita que un token de días sea irrevocable.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioControllerRenovarSesionTest {

    private static final String SECRETO = "una-clave-de-prueba-de-al-menos-32-bytes-de-largo";

    @Mock private UsuarioService usuarioService;

    private final JwtService jwtService = new JwtService(SECRETO, 43_200_000L, 1_209_600_000L);
    private UsuarioController controller;
    private Usuario boletero;
    private final UsuarioAutenticado operador = new UsuarioAutenticado(7L, "boletero1", RolUsuario.BOLETERO);

    @BeforeEach
    void setUp() {
        controller = new UsuarioController(usuarioService, jwtService);
        boletero = Usuario.builder().id(7L).username("boletero1").nombre("Ana").apellido("Paz")
                .rol(RolUsuario.BOLETERO).activo(true).build();
    }

    private String bearer(boolean mantener) {
        return "Bearer " + jwtService.generarToken(boletero, mantener);
    }

    @Test
    void renuevaUnaSesionLargaConLosDatosActualesDelUsuario() {
        // Le cambiaron el rol después de emitir el token: la renovación trae el rol vigente.
        Usuario ascendido = Usuario.builder().id(7L).username("boletero1").nombre("Ana").apellido("Paz")
                .rol(RolUsuario.ADMIN).activo(true).build();
        when(usuarioService.obtenerUsuarioPorId(7L)).thenReturn(Optional.of(ascendido));

        ResponseEntity<?> respuesta = controller.renovarSesion(bearer(true), operador);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponseDTO dto = (LoginResponseDTO) respuesta.getBody();
        assertThat(dto.getRol()).isEqualTo(RolUsuario.ADMIN);
        assertThat(jwtService.esSesionLarga(jwtService.validarYExtraerClaims(dto.getToken()).orElseThrow())).isTrue();
    }

    @Test
    void noRenuevaUnTokenDeTurno() {
        ResponseEntity<?> respuesta = controller.renovarSesion(bearer(false), operador);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unUsuarioDadoDeBajaNoRenueva() {
        boletero.setActivo(false);
        when(usuarioService.obtenerUsuarioPorId(7L)).thenReturn(Optional.of(boletero));

        ResponseEntity<?> respuesta = controller.renovarSesion(bearer(true), operador);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unUsuarioEliminadoNoRenueva() {
        when(usuarioService.obtenerUsuarioPorId(7L)).thenReturn(Optional.empty());

        ResponseEntity<?> respuesta = controller.renovarSesion(bearer(true), operador);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
