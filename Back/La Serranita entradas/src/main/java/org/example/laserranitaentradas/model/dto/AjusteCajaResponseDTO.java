package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class AjusteCajaResponseDTO {
    private Long id;
    private FormaPago formaOrigen;
    private FormaPago formaDestino;
    private BigDecimal monto;
    private int cantidadVentas;
    private String detalle;
    private String nota;
    private LocalDateTime fecha;
    /** Username del admin que cargó el ajuste (campo de auditoría de BaseEntity). */
    private String usuario;
    /** Composición de una venta del ajuste: id de tipo de entrada → cantidad de pases. */
    private Map<Long, Integer> lineas;
}
