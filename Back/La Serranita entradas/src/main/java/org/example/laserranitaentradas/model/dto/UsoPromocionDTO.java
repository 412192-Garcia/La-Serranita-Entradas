package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Uso de una promo en el rango. Las ventas viejas no guardan qué promo usaron. */
@Data
@AllArgsConstructor
public class UsoPromocionDTO {
    private Long promocionId;
    private String nombre;
    private long cantidadVentas;
    private BigDecimal totalDescontado;
}
