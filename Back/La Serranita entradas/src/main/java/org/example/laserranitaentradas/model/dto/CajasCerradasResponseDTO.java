package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Página de cajas cerradas, con la forma de un Page de Spring Data. */
@Data
@Builder
public class CajasCerradasResponseDTO {
    private List<CajaResumenReporteDTO> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
}
