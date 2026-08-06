package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearArticuloVarioRequest {
    private String nombre;
    private BigDecimal precioSugerido;
    /** Sólo se usa en actualizaciones (ej: reactivar); al crear siempre nace activo. */
    private Boolean activo;
}
