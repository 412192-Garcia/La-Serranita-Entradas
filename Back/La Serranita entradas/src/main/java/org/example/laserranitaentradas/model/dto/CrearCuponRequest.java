package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;

@Data
public class CrearCuponRequest {
    private String codigo;
    private Integer usosMaximos;
    private LocalDate fechaExpiracion;
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;
}
