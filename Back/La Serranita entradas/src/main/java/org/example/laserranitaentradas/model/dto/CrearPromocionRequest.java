package org.example.laserranitaentradas.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CrearPromocionRequest {
    private String nombre;
    /** Exactamente uno de los dos. */
    private BigDecimal porcentajeDescuento;
    private BigDecimal montoDescuento;
    /** Sólo se usa en actualizaciones (ej: reactivar); al crear siempre nace activa. */
    private Boolean activo;
}
