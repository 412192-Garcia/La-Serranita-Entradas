package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.DestinoFactura;

/** Lo que eligió el boletero. MAIL con email vacío = no facturar. */
@Data
public class FacturacionPosDTO {
    DestinoFactura destino;
    String email;
    /** Ticketera elegida en la tablet (sólo IMPRIMIR). Null = la única conectada. */
    String impresora;
}
