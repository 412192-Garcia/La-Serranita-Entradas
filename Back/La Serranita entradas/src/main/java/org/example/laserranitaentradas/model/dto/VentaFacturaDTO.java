package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una venta del turno con su factura, para "Ventas y facturas".
 *
 * @param detalle resumen ("2x General, 1x Souvenir")
 * @param factura la vigente; null si no se facturó
 */
public record VentaFacturaDTO(
        Long compraId,
        String codigoReserva,
        LocalDateTime fecha,
        BigDecimal montoTotal,
        FormaPago formaPago,
        String detalle,
        FacturaResponseDTO factura
) {}
