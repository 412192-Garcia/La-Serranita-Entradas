package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;

import java.math.BigDecimal;

@Data
public class RetiroCajaRequestDTO {
    private BigDecimal monto;
    private String motivo;
    /** Null = RETIRO (compatibilidad con quien no lo mande). */
    private TipoMovimientoCaja tipo;
}
