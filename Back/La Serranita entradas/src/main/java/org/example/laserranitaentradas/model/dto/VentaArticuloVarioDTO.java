package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Fila del reporte de artículos varios. articuloVarioId null = descripción libre. */
@Data
@AllArgsConstructor
public class VentaArticuloVarioDTO {
    private Long articuloVarioId;
    private String nombre;
    private long cantidad;
    private BigDecimal monto;
}
