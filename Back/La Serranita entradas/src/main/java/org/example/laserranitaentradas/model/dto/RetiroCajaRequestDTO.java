package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RetiroCajaRequestDTO {
    private BigDecimal monto;
    private String motivo;
}
