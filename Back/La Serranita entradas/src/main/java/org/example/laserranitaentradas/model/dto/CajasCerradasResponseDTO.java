package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Página de "Cajas cerradas" para la pantalla de Cajas (GET /api/interno/caja/cerradas): mismas
 * cinco propiedades que devuelve Spring Data para cualquier Page (content/totalElements/
 * totalPages/number/size, ver Pagina<T> del frontend).
 */
@Data
@Builder
public class CajasCerradasResponseDTO {
    private List<CajaResumenReporteDTO> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
}
