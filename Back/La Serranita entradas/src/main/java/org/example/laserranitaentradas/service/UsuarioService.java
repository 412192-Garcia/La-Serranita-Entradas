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

    /**
     * Cambio de contraseña self-service: cualquier usuario logueado puede cambiar la suya
     * propia, verificando la actual antes de pisarla (a diferencia de actualizarUsuario, que
     * es ADMIN-only y no pide la contraseña vieja).
     */
    void cambiarPassword(Long usuarioId, String passwordActual, String passwordNueva);

    /**
     * Colores elegidos por el usuario para personalizar la interfaz (principal, de fondo de
     * página, de tarjetas y de bordes). Null en cualquiera vuelve ese color al valor por
     * defecto. Se valida el formato acá (no en el controller) porque es una regla de negocio,
     * no de transporte HTTP.
     */
    Usuario actualizarColorTema(Long usuarioId, String colorTema, String colorFondo, String colorTarjeta, String colorBorde);

    /**
     * Foto de perfil self-service: se manda como data URI base64 (ya redimensionada y
     * comprimida del lado del cliente, ver mi-cuenta.ts) para no necesitar un servicio de
     * archivos aparte. Null = sacarla. Se valida acá el formato y un tamaño máximo razonable.
     */
    Usuario actualizarFotoPerfil(Long usuarioId, String fotoPerfil);
}

