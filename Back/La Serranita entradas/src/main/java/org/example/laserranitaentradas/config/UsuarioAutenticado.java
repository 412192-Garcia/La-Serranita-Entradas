package org.example.laserranitaentradas.config;

import org.example.laserranitaentradas.model.entity.RolUsuario;

/** Usuario firmado dentro del JWT, como principal de Spring Security (@AuthenticationPrincipal). */
public record UsuarioAutenticado(Long id, String username, RolUsuario rol) {
}
