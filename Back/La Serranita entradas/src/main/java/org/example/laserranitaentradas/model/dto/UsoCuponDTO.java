package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Cupones aplicados en el rango, agrupados por el valor del descuento del cupón ("10%", "$500"),
 * para ver qué descuentos se usan más. Sólo cuenta compras cobradas.
 */
@Data
@AllArgsConstructor
public class UsoCuponDTO {
    /** El valor del cupón: "15%" o "$1.000". */
    private String etiqueta;
    private long cantidad;
    /** Suma de descuentoAplicado de esas compras. */
    private BigDecimal montoDescontado;
}
