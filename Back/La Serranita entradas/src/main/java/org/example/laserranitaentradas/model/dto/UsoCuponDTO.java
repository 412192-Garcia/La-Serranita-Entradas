package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Cupones aplicados en el rango, agrupados por lote (o por código si es individual) y valor del
 * descuento, para ver qué promociones se usan más. Sólo cuenta compras cobradas.
 */
@Data
@AllArgsConstructor
public class UsoCuponDTO {
    /** Lote o código y valor del cupón: "La Voz · 20%", "NUEVAWEB · $17.500 por entrada". */
    private String etiqueta;
    private long cantidad;
    /** Suma de descuentoAplicado de esas compras. */
    private BigDecimal montoDescontado;
}
