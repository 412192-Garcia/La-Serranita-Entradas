package org.example.laserranitaentradas.config;

import org.example.laserranitaentradas.model.entity.RolUsuario;

/**
 * Datos del usuario que vienen firmados dentro del JWT.
 *
 * Se usa como principal de Spring Security para que los controladores puedan
 * inyectarlo con @AuthenticationPrincipal y sepan quién está operando sin
 * confiar en lo que mande el cliente ni pegarle a la base en cada request.
 */
public record UsuarioAutenticado(Long id, String username, RolUsuario rol) {
}
