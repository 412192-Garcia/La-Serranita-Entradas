package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Página de "Cajas cerradas" para la pantalla de Cajas (GET /api/interno/caja/cerradas): mismas
 * cinco propiedades que devuelve Spring Data para cualquier Page (content/totalElements/
 * totalPages/number/size, ver Pagina<T> del frontend), más los totales de retiros/faltantes/
 * sobrantes de TODO lo que matchea el filtro — no sólo la página actual, así las tarjetas KPI de
 * arriba de la tabla no quedan recortadas a lo que se ve en pantalla.
 */
@Data
@Builder
public class CajasCerradasResponseDTO {
    private List<CajaResumenReporteDTO> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
    private BigDecimal totalRetiros;
    private BigDecimal totalFaltantes;
    private BigDecimal totalSobrantes;
}
