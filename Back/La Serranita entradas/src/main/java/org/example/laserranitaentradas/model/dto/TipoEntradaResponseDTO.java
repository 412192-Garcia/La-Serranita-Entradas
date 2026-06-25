package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.Tipo;

import java.math.BigDecimal;

@Data
public class TipoEntradaResponseDTO {
    private Long id;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Tipo tipo;
}
