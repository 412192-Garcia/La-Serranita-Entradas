package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.RolUsuario;

/**
 * Vista pública de un usuario del staff.
 *
 * Existe para que la entidad Usuario no salga cruda por la API: así el hash de
 * la contraseña no depende de una anotación de Jackson para no filtrarse, y los
 * campos de auditoría heredados de BaseEntity quedan fuera de la respuesta.
 */
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
