package org.example.laserranitaentradas.model.dto;

import lombok.Data;

/** Datos de contacto editables desde boletería: sólo nombre/apellido del titular y su contacto. */
@Data
public class EditarContactoRequest {
    String nombre;
    String apellido;
    String email;
    String telefono;
}
