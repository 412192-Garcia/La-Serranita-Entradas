package org.example.laserranitaentradas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class DesgloseTipoEntradaDTO {
    private Long tipoEntradaId;
    private String nombre;
    private long cantidad;
    private BigDecimal montoRecaudado;
}
