package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Una venta del turno en "Ventas y facturas", con su factura (null si no se facturó). */
public record VentaFacturaDTO(
        Long compraId,
        String codigoReserva,
        LocalDateTime fecha,
        BigDecimal montoTotal,
        FormaPago formaPago,
        /** "2x General, 1x Souvenir". */
        String detalle,
        FacturaResponseDTO factura,
        List<Linea> lineas,
        BigDecimal descuento,
        String promocion,
        FormaPago formaPagoSecundaria,
        BigDecimal montoFormaPagoSecundaria,
        BigDecimal dolaresRecibidos,
        BigDecimal cotizacionDolar,
        /** Reserva cobrada en esta caja (no una venta de puerta). */
        boolean anticipada,
        /** Nombre del titular; sólo en anticipadas. */
        String titular,
        /** Cobro con el Point: "visa", "credit_card", cuotas. */
        String pointMedioPago,
        String pointTipoMedio,
        Integer pointCuotas,
        /** Hasta cuándo el boletero la puede corregir; null si no puede. */
        LocalDateTime corregibleHasta,
        /** Lo que corrigió el boletero, de la más vieja a la más nueva. */
        List<CorreccionVentaDTO> correcciones
) {
    /**
     * Una entrada (tipoEntradaId) o un artículo (articuloVarioId, o ninguno si es libre).
     * precioUnitario null = venta vieja sin precio guardado.
     */
    public record Linea(int cantidad, String nombre, BigDecimal precioUnitario, boolean entrada,
                        Long tipoEntradaId, Long articuloVarioId) {}
}
