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

    /**
     * Si viene seteado, esta "venta" en realidad cierra la reserva RESERVADO_EFECTIVO de ese id
     * (el boletero la cargó en el POS desde el panel de anticipadas y la cobra como una venta
     * normal): no se crea una compra nueva, se reprecia y valida la existente. Null en una venta
     * de puerta común.
     */
    Long compraReservadaId;
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

    /**
     * True sólo cuando esta petición viene de un reintento en segundo plano de la cola offline
     * (no del primer intento en vivo). Se usa para decidir si un rechazo del servidor amerita
     * quedar registrado para que un admin lo revise (ver RechazoOperacionService): un rechazo en
     * vivo ya lo ve y lo corrige la persona que lo tipeó ahí mismo — anotarlo también sería puro
     * ruido. Un rechazo en un reintento en cambio pasa sin que nadie lo esté mirando.
     */
    Boolean esReintentoEncolado;

    /** Caja del boletero al momento de cobrar: si esto se rechaza porque esa caja ya no está
     * abierta, queda guardado en el rechazo para saber cuál reabrir y reintentar (ver
     * RetiroCajaRequestDTO, mismo criterio). */
    Long cajaId;
}
