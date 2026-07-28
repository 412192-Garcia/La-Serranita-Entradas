package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RecaudacionPorFormaPagoDTO {
    private FormaPago formaPago;
    private String etiqueta;
    private long cantidad;
    private BigDecimal monto;
}
