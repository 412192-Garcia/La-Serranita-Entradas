package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Cuánto se cobró en la puerta (POS) contra cuánto se cobró de reservas anticipadas. */
@Data
@AllArgsConstructor
public class VentasPorOrigenDTO {
    private TipoListadoCompra origen;
    private String etiqueta;
    private long cantidad;
    private BigDecimal monto;
}
