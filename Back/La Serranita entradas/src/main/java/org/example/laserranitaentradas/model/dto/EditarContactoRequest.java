package org.example.laserranitaentradas.model.dto;

import lombok.Data;

/** Contacto editable desde boletería: titular, o DNI del receptor de un regalo. */
@Data
public class EditarContactoRequest {
    String nombre;
    String apellido;
    /** DNI del titular (el de Cliente). Null/vacío = no tocar. */
    String dni;
    String email;
    String telefono;
    /** Sólo aplica si la compra es un regalo (fechaVisita null): DNI de quien lo recibe. */
    String receptorDni;
}
