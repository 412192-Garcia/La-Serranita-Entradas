package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DescuentoEfectivoResponseDTO {
    private Long id;
    private Long tipoEntradaId;
    private String tipoEntradaNombre;
    private Integer cantidadPases;
    private BigDecimal precioPromocionalTotal;
}
