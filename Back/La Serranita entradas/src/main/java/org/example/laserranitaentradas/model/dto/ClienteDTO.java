package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class ClienteDTO {
    Long dni;
    String nombre;
    String apellido;
    String email;
    String telefono;
    /** Opcional. */
    Integer edad;
    /** Opcional. */
    String localidad;
}

