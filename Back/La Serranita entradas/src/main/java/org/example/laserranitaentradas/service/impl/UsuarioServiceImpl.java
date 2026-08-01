package org.example.laserranitaentradas.service.impl;

import org.example.laserranitaentradas.model.entity.Usuario;
import org.example.laserranitaentradas.repository.UsuarioRepository;
import org.example.laserranitaentradas.service.UsuarioService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

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
}
