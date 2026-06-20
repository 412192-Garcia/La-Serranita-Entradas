package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TipoEntradaResponseDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
}

