package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.RolUsuario;

@Data
public class LoginResponseDTO {
    private Long id;
    private String username;
    private String nombre;
    private String apellido;
    private RolUsuario rol;
    private String token;
    private String colorTema;
    private String colorFondo;
    private String colorTarjeta;
    private String colorBorde;
    private String fotoPerfil;
}
