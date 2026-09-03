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
    /**
     * Descuento con el que se cobró la venta, para que el modo revisión agrupe las ventas
     * normales aparte de las que tuvieron descuento (y cada descuento por separado). Uno de los
     * dos, o ninguno: `descuentoPorcentaje` si fue una promo de %, `descuentoMonto` si fue una
     * promo de monto fijo o un descuento manual (ahí sólo se conoce el $ que salió).
     */
    private BigDecimal descuentoPorcentaje;
    private BigDecimal descuentoMonto;
}
