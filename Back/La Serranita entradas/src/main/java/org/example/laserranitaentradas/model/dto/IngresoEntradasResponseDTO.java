package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class IngresoEntradasResponseDTO {
    private Long id;
    private Integer cantidad;
    private LocalDateTime fecha;
}
