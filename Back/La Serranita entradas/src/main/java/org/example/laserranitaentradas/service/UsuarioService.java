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
    /** Login del módulo interno. Vacío si no matchea o el usuario está inactivo. */
    Optional<Usuario> autenticar(String username, String password);

    /** Cada usuario cambia la suya verificando la actual. */
    void cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva);

    /** Colores de la interfaz del usuario. Null vuelve al valor por defecto. */
    Usuario actualizarColorTema(Long usuarioId, String colorTema, String colorFondo, String colorTarjeta, String colorBorde);

    /** Foto como data URI base64, ya comprimida en el cliente. Null = sacarla. */
    Usuario actualizarFotoPerfil(Long usuarioId, String fotoPerfil);
}

