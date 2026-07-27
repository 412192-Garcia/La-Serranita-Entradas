package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearDescuentoEfectivoRequest {
    private Long tipoEntradaId;
    private Integer cantidadPases;
    private BigDecimal precioPromocionalTotal;
}
