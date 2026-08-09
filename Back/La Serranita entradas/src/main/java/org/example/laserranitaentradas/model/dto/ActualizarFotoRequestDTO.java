package org.example.laserranitaentradas.model.dto;

import lombok.Data;

/** Null = sacar la foto de perfil y volver al ícono por defecto. */
@Data
public class ActualizarFotoRequestDTO {
    private String fotoPerfil;
}
