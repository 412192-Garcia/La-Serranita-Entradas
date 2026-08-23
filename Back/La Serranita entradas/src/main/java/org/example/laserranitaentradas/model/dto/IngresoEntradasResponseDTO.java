package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.TipoMovimientoEntradas;

import java.time.LocalDateTime;

@Data
@Builder
public class IngresoEntradasResponseDTO {
    private Long id;
    private Integer cantidad;
    private String motivo;
    private TipoMovimientoEntradas tipo;
    private LocalDateTime fecha;
}
