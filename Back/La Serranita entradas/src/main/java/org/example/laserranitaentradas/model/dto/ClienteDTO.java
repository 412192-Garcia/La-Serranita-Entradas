package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class ClienteDTO {
    Long DNI;
    String nombre;
    String apellido;
    String email;
    String telefono;
}

