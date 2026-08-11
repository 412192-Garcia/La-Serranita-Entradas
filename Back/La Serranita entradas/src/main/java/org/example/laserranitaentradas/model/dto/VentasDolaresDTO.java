package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Ventas cobradas en efectivo-dólares en el rango: cuánto físico entró y a qué cotización promedio. */
@Data
@AllArgsConstructor
public class VentasDolaresDTO {
    private long cantidadVentas;
    private BigDecimal totalDolaresRecibidos;
    /** Suma de montoTotal (en pesos) de esas ventas: lo que representaron en la recaudación. */
    private BigDecimal totalEquivalenteArs;
    /** Promedio ponderado por dolaresRecibidos. Null si no hubo ventas en dólares en el rango. */
    private BigDecimal cotizacionPromedio;
}
