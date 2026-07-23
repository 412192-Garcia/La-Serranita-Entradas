package org.example.laserranitaentradas.service;

import org.example.laserranitaentradas.model.entity.Usuario;

import java.util.List;
import java.util.Optional;

public interface UsuarioService {
    Usuario crearUsuario(Usuario usuario);
    Optional<Usuario> obtenerUsuarioPorId(Long id);
    Optional<Usuario> obtenerUsuarioPorUsername(String username);
    List<Usuario> obtenerTodosUsuarios();
    Usuario actualizarUsuario(Usuario usuario);
    void eliminarUsuario(Long id);
}

