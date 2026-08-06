package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompraDetalleResponseDTO {
    private Long id;
    private TipoEntradaResponseDTO tipoEntrada;
    private ArticuloVarioResponseDTO articuloVario;
    private String descripcionLibre;
    /** Siempre poblado: precio de lista para entradas (en vivo), el guardado para artículos. */
    private BigDecimal precioUnitario;
    private Integer cantidad;
}

