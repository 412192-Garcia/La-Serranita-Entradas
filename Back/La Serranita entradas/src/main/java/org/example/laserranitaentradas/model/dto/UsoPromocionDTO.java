package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Uso de una promo de venta en puerta en el rango: sólo cuenta ventas hechas después de que
 *  Compra.promocion empezó a guardarse — las anteriores no tienen forma de saber qué promo usaron. */
@Data
@AllArgsConstructor
public class UsoPromocionDTO {
    private Long promocionId;
    private String nombre;
    private long cantidadVentas;
    private BigDecimal totalDescontado;
}
