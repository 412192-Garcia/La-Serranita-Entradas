package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ArticuloVarioResponseDTO {
    private Long id;
    private String nombre;
    private BigDecimal precioSugerido;
    private Boolean activo;
}
