package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.DestinoFactura;

/**
 * Lo que eligió el boletero en el POS: "Imprimir factura" o "Enviar por mail" (+ email).
 * MAIL con email vacío = no facturar, sin error: es la opción por defecto al cobrar en efectivo.
 */
@Data
public class FacturacionPosDTO {
    DestinoFactura destino;
    String email;
    /** Ticketera elegida en la tablet (sólo IMPRIMIR). Null = la única conectada. */
    String impresora;
}
