package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.RolUsuario;

/** Usuario del staff para la API, sin el hash ni la auditoría. */
@Data
public class UsuarioResponseDTO {
    private Long id;
    private String username;
    private String nombre;
    private String apellido;
    private RolUsuario rol;
    private Boolean activo;
    private String colorTema;
    private String colorFondo;
    private String colorTarjeta;
    private String colorBorde;
    private String fotoPerfil;
}
