package org.example.laserranitaentradas.model.dto;

import lombok.Data;

/** Datos de contacto editables desde boletería: nombre/apellido/DNI del titular y su contacto,
 * o el DNI de quien recibe un regalo. */
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
