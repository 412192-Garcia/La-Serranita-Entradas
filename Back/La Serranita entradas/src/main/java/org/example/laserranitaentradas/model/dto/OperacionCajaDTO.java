package org.example.laserranitaentradas.model.dto;

import lombok.Builder;
import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Una fila del "detalle de caja": una venta o un retiro, para listarlos juntos en orden cronológico. */
@Data
@Builder
public class OperacionCajaDTO {
    /** "VENTA", "RETIRO" o "INGRESO_ENTRADAS". */
    private String tipo;
    private LocalDateTime fecha;
    /** Null en los ingresos de entradas físicas: no mueven plata. */
    private BigDecimal monto;
    /** Sólo para ventas: parte del monto que corresponde a artículos varios (no entradas). 0 si la venta es sólo entradas. */
    private BigDecimal montoArticulos;
    /** Sólo para ventas: una entrada por cada línea de entrada paga (tipo + cantidad + su parte del monto). */
    private List<SegmentoEntradaDTO> segmentosEntrada;
    /** Sólo para ventas. */
    private FormaPago formaPago;
    /** Venta: "2x Pase General, 1x Cuadrito recuerdo". Retiro: el motivo. Ingreso de entradas: "+50 entradas — motivo". */
    private String detalle;
    /** Sólo para ventas: id de la Compra, para poder cancelarla o editarla desde el detalle de caja (ADMIN). Null en retiros/ingresos. */
    private Long compraId;
}
