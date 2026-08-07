package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EntradasPorTipoDTO {
    private String nombreTipo;
    private Integer cantidad;
}
