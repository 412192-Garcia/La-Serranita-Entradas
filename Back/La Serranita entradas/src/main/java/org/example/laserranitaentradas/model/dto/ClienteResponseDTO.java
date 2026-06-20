package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class ClienteResponseDTO {
    private Long id;
    private String dni;
    private String nombre;
    private String apellido;
}
