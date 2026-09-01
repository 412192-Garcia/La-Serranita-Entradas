package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Una línea de entrada paga de una venta, para el modo revisión del cierre: qué tipo, cuántos
 * pases y qué parte del monto de la compra le corresponde (repartido proporcional al precio de
 * lista cuando la compra tiene varias líneas / descuento). Las líneas gratis y los artículos no
 * generan segmento.
 */
@Data
@Builder
public class SegmentoEntradaDTO {
    private Long tipoEntradaId;
    private String tipoNombre;
    private int cantidad;
    private BigDecimal monto;
}
