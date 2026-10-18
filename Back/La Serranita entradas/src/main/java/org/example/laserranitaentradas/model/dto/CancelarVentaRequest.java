package org.example.laserranitaentradas.model.dto;

import lombok.Data;

@Data
public class CancelarVentaRequest {
    /** Por qué se anula la venta (opcional): si viene, queda en la compra y en la auditoría. */
    String motivo;
}
