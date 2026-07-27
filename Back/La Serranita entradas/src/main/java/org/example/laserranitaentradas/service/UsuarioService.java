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
    /** Valida usuario/contraseña para el login del módulo interno. Vacío si no matchea o el usuario está inactivo. */
    Optional<Usuario> autenticar(String username, String password);
}

