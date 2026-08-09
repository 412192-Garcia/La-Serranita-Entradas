package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cambiar la contraseña propia es self-service (cualquier usuario logueado, no sólo ADMIN),
 * así que a diferencia de actualizarUsuario tiene que verificar la contraseña actual antes
 * de pisarla: si no, cualquiera con una sesión activa podría cambiarle la contraseña a
 * cualquier otro id sin saber la suya.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UsuarioServiceImpl service;

    @Test
    void cambiarPassword_conPasswordActualIncorrecta_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        Usuario usuario = usuarioConPassword("hash-viejo");
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mal", "hash-viejo")).thenReturn(false);

        assertThatThrownBy(() -> service.cambiarPassword(3L, "mal", "nuevaPassword123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarPassword_conNuevaMuyCorta_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.cambiarPassword(3L, "actual", "123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarPassword_conNuevaVacia_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.cambiarPassword(3L, "actual", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarPassword_deUsuarioInexistente_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarPassword(99L, "actual", "nuevaPassword123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarPassword_casoNormal_encriptaLaNuevaYLaGuarda() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        Usuario usuario = usuarioConPassword("hash-viejo");
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("actual", "hash-viejo")).thenReturn(true);
        when(passwordEncoder.encode("nuevaPassword123")).thenReturn("hash-nuevo");

        service.cambiarPassword(3L, "actual", "nuevaPassword123");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hash-nuevo");
    }

    // ---------- Color de tema: self-service, valida el formato hex ----------

    @Test
    void actualizarColorTema_conHexInvalido_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.actualizarColorTema(3L, "verde", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarColorTema_sinElNumeral_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.actualizarColorTema(3L, "39a935", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarColorTema_conColorFondoInvalido_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.actualizarColorTema(3L, null, "no-es-hex", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarColorTema_conColorTarjetaInvalido_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.actualizarColorTema(3L, null, null, "no-es-hex", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarColorTema_conColorBordeInvalido_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.actualizarColorTema(3L, null, null, null, "no-es-hex"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarColorTema_deUsuarioInexistente_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarColorTema(99L, "#39a935", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarColorTema_conHexValido_loGuarda() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        Usuario usuario = usuarioConPassword("hash");
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.actualizarColorTema(3L, "#2563eb", "#1f2430", "#23272f", "#3a3f4b");

        assertThat(resultado.getColorTema()).isEqualTo("#2563eb");
        assertThat(resultado.getColorFondo()).isEqualTo("#1f2430");
        assertThat(resultado.getColorTarjeta()).isEqualTo("#23272f");
        assertThat(resultado.getColorBorde()).isEqualTo("#3a3f4b");
    }

    @Test
    void actualizarColorTema_conNull_vuelveAlTemaPorDefecto() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        Usuario usuario = usuarioConPassword("hash");
        usuario.setColorTema("#2563eb");
        usuario.setColorFondo("#1f2430");
        usuario.setColorTarjeta("#23272f");
        usuario.setColorBorde("#3a3f4b");
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.actualizarColorTema(3L, null, null, null, null);

        assertThat(resultado.getColorTema()).isNull();
        assertThat(resultado.getColorFondo()).isNull();
        assertThat(resultado.getColorTarjeta()).isNull();
        assertThat(resultado.getColorBorde()).isNull();
    }

    // ---------- Foto de perfil: self-service, valida formato y tamaño ----------

    @Test
    void actualizarFotoPerfil_conFormatoInvalido_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.actualizarFotoPerfil(3L, "no-es-una-data-uri"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarFotoPerfil_conMimeNoImagen_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);

        assertThatThrownBy(() -> service.actualizarFotoPerfil(3L, "data:text/plain;base64,aGVsbG8="))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarFotoPerfil_demasiadoGrande_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        String enorme = "data:image/png;base64," + "A".repeat(400_001);

        assertThatThrownBy(() -> service.actualizarFotoPerfil(3L, enorme))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarFotoPerfil_deUsuarioInexistente_rechaza() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarFotoPerfil(99L, "data:image/png;base64,aGVsbG8="))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarFotoPerfil_conDataUriValida_laGuarda() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        Usuario usuario = usuarioConPassword("hash");
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.actualizarFotoPerfil(3L, "data:image/png;base64,aGVsbG8=");

        assertThat(resultado.getFotoPerfil()).isEqualTo("data:image/png;base64,aGVsbG8=");
    }

    @Test
    void actualizarFotoPerfil_conNull_laSaca() {
        service = new UsuarioServiceImpl(usuarioRepository, passwordEncoder);
        Usuario usuario = usuarioConPassword("hash");
        usuario.setFotoPerfil("data:image/png;base64,aGVsbG8=");
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = service.actualizarFotoPerfil(3L, null);

        assertThat(resultado.getFotoPerfil()).isNull();
    }

    private Usuario usuarioConPassword(String passwordHasheada) {
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setPassword(passwordHasheada);
        return usuario;
    }
}
