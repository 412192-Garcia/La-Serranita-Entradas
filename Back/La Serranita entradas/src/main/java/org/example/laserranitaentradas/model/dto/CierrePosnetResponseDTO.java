package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CierrePosnetResponseDTO {
    private Long id;
    private FormaPago formaPago;
    private BigDecimal monto;
    private String nota;
    private LocalDateTime fecha;
}
