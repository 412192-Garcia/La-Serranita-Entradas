package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una fila del "detalle de caja": una venta o un retiro, para listarlos juntos en orden cronológico. */
@Data
@Builder
public class OperacionCajaDTO {
    /** "VENTA", "RETIRO" o "INGRESO_ENTRADAS". */
    private String tipo;
    private LocalDateTime fecha;
    /** Null en los ingresos de entradas físicas: no mueven plata. */
    private BigDecimal monto;
    /** Sólo para ventas. */
    private FormaPago formaPago;
    /** Venta: "2x Pase General, 1x Cuadrito recuerdo". Retiro: el motivo. Ingreso de entradas: "+50 entradas — motivo". */
    private String detalle;
    /** Sólo para ventas: id de la Compra, para poder cancelarla o editarla desde el detalle de caja (ADMIN). Null en retiros/ingresos. */
    private Long compraId;
}
