package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.UsuarioRepository;
import org.example.laserranitaentradas.service.UsuarioService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern FOTO_DATA_URI = Pattern.compile("^data:image/(png|jpe?g|webp);base64,[A-Za-z0-9+/]+=*$");
    /** ~300KB decodidos: de sobra para una foto ya redimensionada a un avatar chico del lado del cliente. */
    private static final int FOTO_MAX_CARACTERES = 400_000;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Usuario crearUsuario(Usuario usuario) {
        if (usuarioRepository.findByUsername(usuario.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con el nombre de usuario \"" + usuario.getUsername() + "\"");
        }
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    @Override
    public Optional<Usuario> obtenerUsuarioPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    @Override
    public Optional<Usuario> obtenerUsuarioPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    @Override
    public List<Usuario> obtenerTodosUsuarios() {
        return usuarioRepository.findAll();
    }

    @Override
    public Usuario actualizarUsuario(Usuario usuario) {
        Usuario existente = usuarioRepository.findById(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para id: " + usuario.getId()));

        // La contraseña es write-only: si el formulario de edición no manda una nueva,
        // llega en null/vacío acá, y guardarla tal cual pisaría el hash existente,
        // dejando al usuario sin poder iniciar sesión nunca más.
        if (usuario.getPassword() != null && !usuario.getPassword().isBlank()) {
            existente.setPassword(passwordEncoder.encode(usuario.getPassword()));
        }
        existente.setUsername(usuario.getUsername());
        existente.setNombre(usuario.getNombre());
        existente.setApellido(usuario.getApellido());
        existente.setRol(usuario.getRol());
        existente.setActivo(usuario.getActivo());
        return usuarioRepository.save(existente);
    }

    @Override
    public void eliminarUsuario(Long id) {
        usuarioRepository.deleteById(id);
    }

    @Override
    public Optional<Usuario> autenticar(String username, String password) {
        return usuarioRepository.findByUsername(username)
                .filter(Usuario::getActivo)
                .filter(u -> passwordEncoder.matches(password, u.getPassword()));
    }

    @Override
    public void cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva) {
        if (passwordNueva == null || passwordNueva.isBlank() || passwordNueva.length() < 6) {
            throw new IllegalArgumentException("La contraseña nueva tiene que tener al menos 6 caracteres");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para id: " + usuarioId));
        if (!passwordEncoder.matches(passwordActual, usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        usuario.setPassword(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(usuario);
    }

    @Override
    public Usuario actualizarColorTema(Long usuarioId, String colorTema, String colorFondo, String colorTarjeta, String colorBorde) {
        if (colorTema != null && !HEX_COLOR.matcher(colorTema).matches()) {
            throw new IllegalArgumentException("El color principal tiene que ser un hex válido, ej: #39a935");
        }
        if (colorFondo != null && !HEX_COLOR.matcher(colorFondo).matches()) {
            throw new IllegalArgumentException("El color de fondo tiene que ser un hex válido, ej: #f4f5f7");
        }
        if (colorTarjeta != null && !HEX_COLOR.matcher(colorTarjeta).matches()) {
            throw new IllegalArgumentException("El color de tarjeta tiene que ser un hex válido, ej: #ffffff");
        }
        if (colorBorde != null && !HEX_COLOR.matcher(colorBorde).matches()) {
            throw new IllegalArgumentException("El color de borde tiene que ser un hex válido, ej: #e5e7eb");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para id: " + usuarioId));
        usuario.setColorTema(colorTema);
        usuario.setColorFondo(colorFondo);
        usuario.setColorTarjeta(colorTarjeta);
        usuario.setColorBorde(colorBorde);
        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario actualizarFotoPerfil(Long usuarioId, String fotoPerfil) {
        if (fotoPerfil != null) {
            if (fotoPerfil.length() > FOTO_MAX_CARACTERES) {
                throw new IllegalArgumentException("La foto es demasiado grande.");
            }
            if (!FOTO_DATA_URI.matcher(fotoPerfil).matches()) {
                throw new IllegalArgumentException("La foto tiene que ser una imagen (PNG, JPG o WEBP).");
            }
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado para id: " + usuarioId));
        usuario.setFotoPerfil(fotoPerfil);
        return usuarioRepository.save(usuario);
    }
}
