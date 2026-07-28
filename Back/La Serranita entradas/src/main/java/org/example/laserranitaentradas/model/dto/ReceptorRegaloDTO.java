package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class ReceptorRegaloDTO {
    String nombre;
    String email;
    String dni;
    /** Opcional. */
    String telefono;
}
