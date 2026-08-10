package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RetiroCajaResponseDTO {
    private Long id;
    private BigDecimal monto;
    private String motivo;
    private TipoMovimientoCaja tipo;
    private LocalDateTime fecha;
}
