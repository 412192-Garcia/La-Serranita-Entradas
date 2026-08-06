package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AbrirCajaRequestDTO {
    private BigDecimal montoInicial;
    /** Con cuántas entradas físicas (talonario) arranca el boletero el turno. */
    private Integer entradasFisicasInicial;
}
