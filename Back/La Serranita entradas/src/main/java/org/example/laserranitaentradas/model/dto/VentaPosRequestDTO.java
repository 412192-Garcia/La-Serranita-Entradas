package org.example.laserranitaentradas.model.dto;

import lombok.Data;
import org.example.laserranitaentradas.model.entity.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Venta en la boletería: paga y entra en el acto, sin datos de cliente. */
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

    /** Si viene, cierra la reserva RESERVADO_EFECTIVO de ese id en vez de crear una compra. */
    Long compraReservadaId;
    /** ARS por USD, sólo si se cobró en dólares. */
    BigDecimal cotizacionDolar;
    /** Dólares que entregó el cliente. Obligatorio si se manda cotizacionDolar. */
    BigDecimal dolaresRecibidos;

    /** Clave del POS para que un reintento no duplique la venta. */
    String idempotencyKey;

    /** Cuándo se cobró de verdad, si la venta estuvo encolada sin conexión. Null = ahora. */
    LocalDateTime fechaOriginal;

    /** True si viene de un reintento en segundo plano: sólo esos rechazos se registran para el admin. */
    Boolean esReintentoEncolado;

    /** Caja al momento de cobrar, para saber cuál reabrir si se rechaza. */
    Long cajaId;

    /** Pago mixto: parte con esta forma y el resto con `formaPago`. Sólo efectivo, tarjeta o QR. */
    FormaPago formaPagoSecundaria;
    /** Obligatorio con formaPagoSecundaria y menor al total. */
    BigDecimal montoFormaPagoSecundaria;

    /** Lo que eligió el boletero. Null o MAIL sin email = no se factura. */
    FacturacionPosDTO facturacion;

    /** Cobros del Point que pagaron la parte en tarjeta o QR. Vacío si se cobró en el posnet. */
    List<Long> cobrosPointIds;
}
