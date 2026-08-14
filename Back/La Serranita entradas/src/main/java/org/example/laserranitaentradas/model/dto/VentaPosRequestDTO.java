package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /**
     * Clave que genera el POS antes de mandar la venta. Si se reintenta la misma venta con la
     * misma clave (porque la respuesta original se perdió en un corte), se devuelve la compra
     * ya registrada en vez de duplicarla. Null desde clientes que no usan la cola offline.
     */
    String idempotencyKey;

    /**
     * Cuándo se cobró de verdad en la puerta. Lo manda el POS cuando la venta estuvo encolada
     * sin conexión, así una venta de las 14:00 sincronizada a las 18:00 no queda registrada a
     * las 18:00 en el detalle de caja ni en los reportes por hora. Null = ahora.
     */
    LocalDateTime fechaOriginal;
}
