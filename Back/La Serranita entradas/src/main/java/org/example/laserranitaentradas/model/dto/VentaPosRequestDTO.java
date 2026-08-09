package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.util.List;

/**
 * Venta presencial en la boletería: el visitante paga y entra en el acto, así que
 * no se cargan datos del cliente (no hay nada que validar después).
 */
@Data
public class VentaPosRequestDTO {
    /** Cómo cobró el boletero. Define además si corresponde el precio promocional por grupo. */
    FormaPago formaPago;
    List<DetalleCompraDTO> entradas;
    /** Artículos varios (souvenirs, etc.), de catálogo o libres. */
    List<LineaArticuloPosDTO> articulos;
    /** Descuento por promo con nombre. Excluyente con descuentoManualPorcentaje/Monto. */
    Long promocionId;
    /** Descuento manual ad-hoc que tipea el cajero. Excluyente con promocionId y con descuentoManualMonto. */
    BigDecimal descuentoManualPorcentaje;
    /** Excluyente con promocionId y con descuentoManualPorcentaje. */
    BigDecimal descuentoManualMonto;
    /** Cotización usada (ARS por USD). Presente sólo si el boletero cobró en dólares (sigue siendo EFECTIVO_BOLETERIA). */
    BigDecimal cotizacionDolar;
    /** Dólares que entregó el cliente. Obligatorio si se manda cotizacionDolar. */
    BigDecimal dolaresRecibidos;
}
