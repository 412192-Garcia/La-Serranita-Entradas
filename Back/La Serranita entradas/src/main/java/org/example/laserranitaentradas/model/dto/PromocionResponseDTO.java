package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PromocionResponseDTO {
    private Long id;
    private String nombre;
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;
    private Boolean activo;
}
