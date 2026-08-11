package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Una fila del reporte de ventas de artículos varios. articuloVarioId null = líneas sin catálogo (descripción libre). */
@Data
@AllArgsConstructor
public class VentaArticuloVarioDTO {
    private Long articuloVarioId;
    private String nombre;
    private long cantidad;
    private BigDecimal monto;
}
