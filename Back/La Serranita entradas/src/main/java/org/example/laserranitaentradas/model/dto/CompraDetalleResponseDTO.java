package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompraDetalleResponseDTO {
    private Long id;
    private TipoEntradaResponseDTO tipoEntrada;
    private Integer cantidad;
}

