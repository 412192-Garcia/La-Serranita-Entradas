package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CotizacionResponseDTO {
    private BigDecimal subtotal;
    private BigDecimal ahorro;
}
