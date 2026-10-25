package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Una línea de entrada paga de una venta para el modo revisión: tipo, pases y su parte del monto. Gratis y
 * artículos no generan segmento.
 */
@Data
@Builder
public class SegmentoEntradaDTO {
    private Long tipoEntradaId;
    private String tipoNombre;
    private int cantidad;
    private BigDecimal monto;
    /**
     * Para agrupar por descuento: `descuentoPorcentaje` si fue promo de %, `descuentoMonto` si fue de monto o
     * manual.
     */
    private BigDecimal descuentoPorcentaje;
    private BigDecimal descuentoMonto;
}
