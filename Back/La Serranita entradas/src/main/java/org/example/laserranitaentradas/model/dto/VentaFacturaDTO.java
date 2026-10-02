package org.example.laserranitaentradas.model.dto;

import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una venta de un turno de caja con su factura (o sin ella), para la ventana "Ventas y facturas":
 * desde ahí se reimprime o se manda por mail una factura, o se factura una venta que se cobró sin
 * factura (típico: efectivo con el mail vacío, y el cliente después la pide).
 *
 * @param detalle  resumen de lo vendido ("2x General, 1x Souvenir")
 * @param factura  la factura vigente; null si la venta no se facturó
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
