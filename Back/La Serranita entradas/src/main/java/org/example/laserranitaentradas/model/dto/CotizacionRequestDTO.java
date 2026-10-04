package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CotizacionRequestDTO {
    FormaPago formaPago;
    List<DetalleCompraDTO> entradas;
    List<LineaArticuloPosDTO> articulos;
    Long promocionId;
    BigDecimal descuentoManualPorcentaje;
    BigDecimal descuentoManualMonto;
    /** Cupón online a aplicar sobre las entradas (opcional). */
    String cuponCodigo;
    /**
     * Reserva RESERVADO_EFECTIVO que se está cobrando en el POS (opcional): si viene, las
     * entradas se cotizan al precio con el que se reservó y no al actual.
     */
    Long compraReservadaId;
}
