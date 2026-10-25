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
    /** "VENTA", "ANTICIPADA_VALIDADA", "RETIRO", "APORTE", "INGRESO_ENTRADAS" o "RETIRO_ENTRADAS". */
    private String tipo;
    private LocalDateTime fecha;
    /** Null en ingresos de entradas. En ANTICIPADA_VALIDADA es informativo: no lo cobró esta caja. */
    private BigDecimal monto;
    /** Sólo ventas: parte del monto de artículos varios. */
    private BigDecimal montoArticulos;
    /** Sólo ventas: un segmento por línea de entrada paga. */
    private List<SegmentoEntradaDTO> segmentosEntrada;
    /** Ventas: forma en que se cobró. Anticipadas validadas: forma con la que se pagó originalmente. */
    private FormaPago formaPago;
    /** Sólo ventas: true si se cobró en dólares. No se puede reasignar entre formas de pago. */
    private Boolean pagoEnDolares;
    /** Venta: "2x Pase General, 1x Cuadrito". Retiro: el motivo. Entradas: "+50 entradas — motivo". */
    private String detalle;
    /** Sólo ventas, para cancelarla o editarla. Null en ANTICIPADA_VALIDADA: no pasó por esta caja. */
    private Long compraId;
    /**
     * Sólo VENTA: true si es una reserva cobrada acá. A diferencia de ANTICIPADA_VALIDADA, cuenta en los
     * totales.
     */
    private Boolean anticipada;
}
